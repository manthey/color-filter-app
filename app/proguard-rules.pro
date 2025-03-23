# Keep the app's core functionality
-keep class com.orbitals.colorfilter.** { *; }

# OpenCV-specific rules
-keep class org.opencv.** { *; }
-keep class org.opencv.core.** { *; }
# Only keep the specific OpenCV features used, for example:
-keep class org.opencv.imgproc.** { *; }

# Android-specific rules
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}
-keepclassmembers class **.R$* {
    public static <fields>;
}

# General rules for Android
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions

