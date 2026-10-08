package com.murai.gallery.domain.gif

import java.io.OutputStream

/**
 * Compact, original GIF89a encoder (LZW, 256-color median-cut palette).
 * Takes ARGB frames and a per-frame delay in centiseconds.
 */
class GifEncoder {

    private data class Palette(val colors: IntArray, val indices: IntArray)

    fun encode(frames: List<IntArray>, width: Int, height: Int, delayCs: Int, out: OutputStream) {
        require(frames.isNotEmpty()) { "no frames" }
        val palette = buildMedianCutPalette(frames)
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(out, width)
        writeShort(out, height)
        out.write(0xF0 or 7) // global color table, 256 entries
        out.write(0)         // background color index
        out.write(0)         // aspect
        // global color table padded to 256 entries
        for (i in 0 until 256) {
            val c = if (i < palette.colors.size) palette.colors[i] else 0
            out.write((c shr 16) and 0xFF)
            out.write((c shr 8) and 0xFF)
            out.write(c and 0xFF)
        }
        // Netscape loop extension
        out.write(0x21); out.write(0xFF); out.write(11)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3); out.write(1); writeShort(out, 0); out.write(0)

        for ((frameIndex, frame) in frames.withIndex()) {
            out.write(0x21); out.write(0xF9); out.write(4)
            out.write(0x04) // disposal = do not dispose
            writeShort(out, delayCs.coerceIn(2, 600))
            writeShort(out, 0) // transparent index (none)
            out.write(0)
            out.write(0x2C)
            writeShort(out, 0); writeShort(out, 0)
            writeShort(out, width); writeShort(out, height)
            out.write(0)
            encodeLzw(palette.indices, frameIndex * width * height, width * height, out)
        }
        out.write(0x3B)
        out.flush()
    }

    private fun buildMedianCutPalette(frames: List<IntArray>): Palette {
        val sample = ArrayList<Int>(256 * 256)
        for (frame in frames) {
            val step = (frame.size / 65536).coerceAtLeast(1)
            var i = 0
            while (i < frame.size) {
                sample.add(frame[i])
                i += step
            }
            if (sample.size > 400_000) break
        }
        var boxes: List<List<Int>> = listOf(sample)
        while (boxes.size < 256) {
            var bestBox: List<Int>? = null
            var bestScore = -1L
            var bestChannel = 0
            for (box in boxes) {
                if (box.size < 2) continue
                var minR = 255; var maxR = 0; var minG = 255; var maxG = 0; var minB = 255; var maxB = 0
                for (c in box) {
                    val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
                    if (r < minR) minR = r; if (r > maxR) maxR = r
                    if (g < minG) minG = g; if (g > maxG) maxG = g
                    if (b < minB) minB = b; if (b > maxB) maxB = b
                }
                val scoreR = (maxR - minR).toLong()
                val scoreG = (maxG - minG).toLong() shl 1
                val scoreB = (maxB - minB).toLong()
                val score = maxOf(scoreR, scoreG, scoreB) * box.size
                if (score > bestScore) {
                    bestScore = score
                    bestBox = box
                    bestChannel = when (maxOf(scoreR, scoreG, scoreB)) {
                        scoreR -> 0; scoreG -> 1; else -> 2
                    }
                }
            }
            if (bestBox == null) break
            val sorted = bestBox.sortedBy { channel(it, bestChannel) }
            val mid = sorted.size / 2
            boxes = boxes.flatMap { if (it === bestBox) listOf(sorted.subList(0, mid), sorted.subList(mid, sorted.size)) else listOf(it) }
        }
        val colors = IntArray(boxes.size)
        for ((idx, box) in boxes.withIndex()) {
            if (box.isEmpty()) { colors[idx] = 0; continue }
            var r = 0L; var g = 0L; var b = 0L
            for (c in box) {
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
            }
            val n = box.size
            colors[idx] = (0xFF000000.toInt()) or
                ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
        }
        // map every sampled pixel to nearest palette color
        val indexCache = HashMap<Int, Int>(4096)
        val indices = IntArray(frames.size * frames[0].size)
        var out = 0
        for (frame in frames) {
            for (pixel in frame) {
                val cached = indexCache[pixel]
                if (cached != null) {
                    indices[out++] = cached
                } else {
                    var best = 0
                    var bestDist = Int.MAX_VALUE
                    val pr = (pixel shr 16) and 0xFF
                    val pg = (pixel shr 8) and 0xFF
                    val pb = pixel and 0xFF
                    for ((i, c) in colors.withIndex()) {
                        val cr = (c shr 16) and 0xFF; val cg = (c shr 8) and 0xFF; val cb = c and 0xFF
                        val d = (pr - cr) * (pr - cr) + (pg - cg) * (pg - cg) + (pb - cb) * (pb - cb)
                        if (d < bestDist) { bestDist = d; best = i }
                        if (d == 0) break
                    }
                    indexCache[pixel] = best
                    indices[out++] = best
                }
            }
        }
        return Palette(colors, indices)
    }

    private fun channel(color: Int, channel: Int): Int = when (channel) {
        0 -> (color shr 16) and 0xFF
        1 -> (color shr 8) and 0xFF
        else -> color and 0xFF
    }

    private fun encodeLzw(indices: IntArray, offset: Int, length: Int, out: OutputStream) {
        out.write(8) // LZW min code size
        val block = ByteStream()
        var codeSize = 9
        var dictSize = 258
        val dict = HashMap<String, Int>(1 shl 14)
        fun resetDict() {
            dict.clear()
            dictSize = 258
            codeSize = 9
        }
        resetDict()
        var cur = ""
        val bitWriter = BitWriter(block)
        bitWriter.write(256, codeSize)
        for (p in offset until offset + length) {
            val c = indices[p].toString()
            val next = cur + "," + c
            if (cur.isEmpty()) { cur = c; continue }
            if (dict.containsKey(next)) {
                cur = next
            } else {
                bitWriter.write(resolve(dict, cur), codeSize)
                dict[next] = dictSize++
                if (dictSize == (1 shl codeSize) && codeSize < 12) codeSize++
                if (dictSize >= 4094) {
                    bitWriter.write(256, codeSize)
                    resetDict()
                }
                cur = c
            }
        }
        if (cur.isNotEmpty()) bitWriter.write(resolve(dict, cur), codeSize)
        bitWriter.write(257, codeSize)
        bitWriter.flush()
        block.writeToBlocks(out)
        out.write(0) // block terminator
    }

    private fun resolve(dict: Map<String, Int>, key: String): Int {
        if (!key.contains(',')) return key.toInt()
        return dict[key] ?: 256
    }

    private fun writeShort(out: OutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    private class BitWriter(private val sink: ByteStream) {
        private var buffer = 0
        private var bits = 0

        fun write(value: Int, count: Int) {
            var v = value
            for (i in 0 until count) {
                buffer = buffer or ((v and 1) shl bits)
                bits++
                v = v shr 1
                if (bits == 8) {
                    sink.put(buffer)
                    buffer = 0
                    bits = 0
                }
            }
        }

        fun flush() {
            if (bits > 0) sink.put(buffer)
            buffer = 0
            bits = 0
        }
    }

    private class ByteStream {
        private val blocks = ArrayList<ByteArray>()
        private var current = ByteArray(255)
        private var pos = 0

        fun put(b: Int) {
            if (pos == 255) flushChunk()
            current[pos++] = b.toByte()
        }

        private fun flushChunk() {
            if (pos == 0) return
            blocks.add(current.copyOf(pos))
            pos = 0
        }

        fun writeToBlocks(out: OutputStream) {
            flushChunk()
            for (block in blocks) {
                out.write(block.size)
                out.write(block)
            }
        }
    }
}
