# Minification is disabled for now (see app/build.gradle.kts).
# If you enable R8, kotlinx.serialization ships its own consumer rules;
# Retrofit 2.11 and OkHttp 4.12 also ship consumer rules.
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class com.nokat.chat.**$$serializer { *; }
-keepclassmembers class com.nokat.chat.** {
    *** Companion;
}
