# Murai Gallery R8 configuration.
-dontwarn org.osmdroid.**
-keep class org.osmdroid.** { *; }
-keep class com.murai.gallery.widget.** { *; }
-keepclassmembers class * extends android.appwidget.AppWidgetProvider { <init>(); }
# Keep ViewModel class names readable in crash reports (construction is direct
# via muraiFactory, no reflection; names only, shrinking still applies).
-keepnames class * extends androidx.lifecycle.ViewModel
# Keep Room entities' column metadata via consumer rules; nothing extra required.
