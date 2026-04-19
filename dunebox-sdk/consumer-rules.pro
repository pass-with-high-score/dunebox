# Keep DuneBox SDK public API
-keep class app.pwhs.dunebox.sdk.DuneBox { *; }
-keep class app.pwhs.dunebox.sdk.config.** { *; }
-keep class app.pwhs.dunebox.sdk.rule.** { *; }
-keep class app.pwhs.dunebox.sdk.model.** { *; }
-keep class app.pwhs.dunebox.sdk.event.** { *; }
-keep class app.pwhs.dunebox.sdk.callback.** { *; }

# Keep Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
