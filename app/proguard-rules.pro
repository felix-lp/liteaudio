# --- NewPipeExtractor ---
# The extractor reflects into its own service/handler classes and runs YouTube's
# player JS through Rhino; keep both trees intact.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.tools.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn org.jetbrains.annotations.**

# jsoup (used by the extractor for HTML parsing)
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# OkHttp platform warnings
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Coroutines debug metadata
-dontwarn kotlinx.coroutines.debug.**
