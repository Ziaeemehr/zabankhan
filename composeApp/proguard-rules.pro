# Serialization's generated serializer/Companion rules come with the runtime.
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault
-keep,includedescriptorclasses @kotlinx.serialization.Serializable class com.ziaee.zabankhan.** { *; }

# Room resolves generated implementations by name.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.ziaee.zabankhan.data.**_Impl { *; }

# Providers and model loading use reflection/native entry points.
-keep class com.google.mlkit.** { *; }
-keep class com.tom_roush.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# Ktor, OkHttp and Media3 ship their own consumer rules.

# PDFBox's optional JPEG2000 codec is not bundled; ordinary PDF/OCR works without it.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder

# Vosk loads its native entry points through JNA reflection (both Android flavors).
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
