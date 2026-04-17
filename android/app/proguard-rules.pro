# Kept for future use. isMinifyEnabled=false in release right now, so these are inert,
# but already correct for when you want to enable shrinking.

-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.jsoup.** { *; }
-keep class com.arthenica.ffmpegkit.** { *; }

-dontwarn org.mozilla.javascript.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
