# gomobile 生成的 xray 绑定（通过反射调用）
-keep class libxray.** { *; }
-keep class go.** { *; }

# Rust JNI 入口
-keep class com.iceflow.core.RustBridge { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.iceflow.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
