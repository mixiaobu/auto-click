# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Gson reads and writes app configs by field name. Keep model field names stable
# so SharedPreferences data and exported JSON files stay compatible after R8.
-keepattributes Signature,*Annotation*
-keepclassmembers,allowoptimization class org.xiaobu.autoclick.data.** {
    <fields>;
}

# ML Kit bundled text recognition uses native code, generated proto classes,
# Dynamite descriptors, and reflection-heavy registrars. Keep the OCR runtime
# intact in release builds so R8 does not break Chinese text recognition.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_bundled_common.** { *; }
-keep class com.google.android.gms.dynamite.descriptors.com.google.mlkit.dynamite.text.** { *; }
-keep class com.google.android.libraries.vision.** { *; }
-keep class com.google.android.apps.common.proguard.** { *; }
