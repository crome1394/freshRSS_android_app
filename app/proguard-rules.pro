# FreshRSS Android — R8 rules for release minify.

# Kotlin / coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# kotlinx.serialization (models used with OfflineCache)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.crome.freshrss.data.model.**$$serializer { *; }
-keepclassmembers class com.crome.freshrss.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.crome.freshrss.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.crome.freshrss.data.offline.**$$serializer { *; }
-keepclassmembers class com.crome.freshrss.data.offline.** {
    *** Companion;
}
-keepclasseswithmembers class com.crome.freshrss.data.offline.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# AndroidX Security (Tink under the hood)
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }

# Keep Application entry
-keep class com.crome.freshrss.FreshRssApp { *; }
