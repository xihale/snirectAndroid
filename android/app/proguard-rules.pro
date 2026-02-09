# 保留 Compose 相关元数据
-keepclassmembers class androidx.compose.runtime.Recomposer { *; }

# 保留 Go 核心库 (Gomobile 自动生成的代码)
-keep class core.** { *; }
-keep class go.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ktoml (TOML parsing)
-keep class com.akuleshov7.ktoml.** { *; }

# 忽略常见警告
-dontwarn androidx.compose.**
-dontwarn core.**
-dontwarn kotlinx.serialization.**
