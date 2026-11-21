# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.bansify.computer.**$$serializer { *; }
-keepclassmembers class com.bansify.computer.** {
    *** Companion;
}
-keepclasseswithmembers class com.bansify.computer.** {
    kotlinx.serialization.KSerializer serializer(...);
}
