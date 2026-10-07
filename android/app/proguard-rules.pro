# cf https://developer.android.com/topic/performance/app-optimization/add-keep-rules

# e.g. com.drew.metadata.exif.ExifSubIFDDirectory
-keep class com.drew.metadata.**{ *; }

-keep class org.beyka.tiffbitmapfactory.**{ *; }

-keep class org.mp4parser.**{ *; }

# referenced from: com.google.crypto.tink
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# ---------------------------------------------------------------------------
# Murai Gallery
# ---------------------------------------------------------------------------

# google_mlkit_text_recognition references optional script recognizer artifacts
# (chinese/devanagari/japanese/korean) that are not bundled; only latin is used.
-dontwarn com.google.mlkit.vision.text.chinese.**
-dontwarn com.google.mlkit.vision.text.devanagari.**
-dontwarn com.google.mlkit.vision.text.japanese.**
-dontwarn com.google.mlkit.vision.text.korean.**
