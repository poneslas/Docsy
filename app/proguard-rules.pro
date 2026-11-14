# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Optimize for performance
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# Compose already ships its own ProGuard rules via consumer-rules.
# Only keep what R8 can't infer automatically.
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ML Kit Document Scanner
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_document_scanner.** { *; }
-dontwarn com.google.mlkit.**

# ── .docx viewer (DocxWebRenderer) ──
# These two live in `android.print` on purpose: the print framework's result callbacks have
# package-private constructors, and being in that package is the only way to subclass them and
# drive a PrintDocumentAdapter without the system print dialog. R8 renaming or repackaging them
# would move them out of `android.print` and the access check would fail at runtime — on release
# builds only, which is the worst way to find out. `-keep` pins both the name and the package.
-keep class android.print.OpenLayoutResultCallback { *; }
-keep class android.print.OpenWriteResultCallback { *; }

# The WebView bridge is only ever called from JavaScript, so nothing in the app references these
# methods and R8 would otherwise consider them unused.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ── Optional Office engine (LibreOfficeKit JNI) ──
# liblo-native-code.so binds to these by name: Java_org_libreoffice_kit_* natives, the
# "handle" ByteBuffer fields and the messageRetrieved* callbacks. Nothing may be renamed.
-keep class org.libreoffice.kit.** { *; }

# WorkManager (foss Office-engine downloader) instantiates its Room database reflectively at
# startup; R8 full mode strips the generated no-arg constructor → launch crash. Keep it.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# ── Legacy Office formats (.xls / .doc / .ppt via Apache POI) ──
# POI finds its record classes reflectively: RecordFactory (HSSF) and the HSLF/HWPF record tables
# look up each record's `sid` field, its (RecordInputStream) constructor or static `create` method
# by name. R8 renames, merges and makes those classes abstract, and the lookup then dies in a static
# initialiser ("Invalid record class ... must not be abstract") — on release builds only, where the
# viewer swallows it and just says "Couldn't read this spreadsheet." Keep the library as it is.
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
