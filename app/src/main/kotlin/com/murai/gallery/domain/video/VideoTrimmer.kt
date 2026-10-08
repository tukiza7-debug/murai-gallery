package com.murai.gallery.domain.video

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Lossless-ish trim: copies the selected track segments (video + audio) into a
 * new container via MediaMuxer. Runs entirely off the main thread.
 */
object VideoTrimmer {

    data class TrimResult(val ok: Boolean, val path: String?, val message: String?)

    suspend fun frames(context: Context, uri: android.net.Uri, timesSec: List<Long>): List<android.graphics.Bitmap> =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                timesSec.mapNotNull { t ->
                    retriever.getFrameAtTime(t * 1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            } catch (t: Throwable) {
                emptyList()
            } finally {
                runCatching { retriever.release() }
            }
        }

    suspend fun probeDurationMs(context: Context, uri: android.net.Uri): Long =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                d?.toLongOrNull() ?: 0L
            } catch (t: Throwable) {
                0L
            } finally {
                runCatching { retriever.release() }
            }
        }

    suspend fun trim(
        context: Context,
        sourceUri: android.net.Uri,
        startMs: Long,
        endMs: Long,
        outFile: File
    ): TrimResult = withContext(Dispatchers.IO) {
        if (endMs <= startMs) return@withContext TrimResult(false, null, "range")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, sourceUri, null)
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrack = -1
            var audioTrack = -1
            var muxVideo = -1
            var muxAudio = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoTrack < 0) {
                    videoTrack = i
                    muxVideo = muxer.addTrack(format)
                } else if (mime.startsWith("audio/") && audioTrack < 0) {
                    audioTrack = i
                    muxAudio = muxer.addTrack(format)
                }
            }
            if (videoTrack < 0) return@withContext TrimResult(false, null, "no-video-track")
            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val info = android.media.MediaCodec.BufferInfo()

            fun copyTrack(track: Int, muxTrack: Int) {
                extractor.selectTrack(track)
                extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                while (true) {
                    info.offset = 0
                    info.size = extractor.readSampleData(buffer, 0)
                    if (info.size < 0) break
                    val sampleTimeUs = extractor.sampleTime
                    if (sampleTimeUs > endMs * 1000) break
                    info.presentationTimeUs = sampleTimeUs - startMs * 1000
                    info.flags = when (extractor.sampleFlags) {
                        android.media.MediaExtractor.SAMPLE_FLAG_SYNC -> android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
                        else -> 0
                    }
                    muxer.writeSampleData(muxTrack, buffer, info)
                    extractor.advance()
                }
            }
            muxer.start()
            copyTrack(videoTrack, muxVideo)
            if (audioTrack >= 0) copyTrack(audioTrack, muxAudio)
            muxer.stop()
            TrimResult(true, outFile.absolutePath, null)
        } catch (t: Throwable) {
            TrimResult(false, null, t.message)
        } finally {
            runCatching { extractor.release() }
            runCatching { muxer?.release() }
        }
    }
}
