# Murai Gallery R8 configuration.
-dontwarn org.osmdroid.**
-keep class org.osmdroid.** { *; }
-keep class com.murai.gallery.widget.** { *; }
-keepclassmembers class * extends android.appwidget.AppWidgetProvider { <init>(); }
# Keep Room entities' column metadata via consumer rules; nothing extra required.
