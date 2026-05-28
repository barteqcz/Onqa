-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}
-keep,allowobfuscation,allowshrinking class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName *;
}
-keepclassmembers class * {
    public static final ** Companion;
}
-keepclassmembers class * {
    public static ** serializer();
}

-keep class * extends androidx.lifecycle.ViewModel { *; }

-keepattributes Signature, InnerClasses, AnnotationDefault
-keepattributes *Annotation*
