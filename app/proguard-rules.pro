-keep class com.pulsedeck.app.audio.NativeAudioEngineBridge { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.common.MediaItem { *; }
-keep class androidx.media3.common.MediaMetadata { *; }

-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.jsoup.**
-dontwarn javax.annotation.**
-dontwarn javax.script.**
