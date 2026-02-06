# 保留 Compose 相关元数据
-keepclassmembers class androidx.compose.runtime.Recomposer { *; }

# 保留 Go 核心库 (Gomobile 自动生成的代码)
-keep class core.** { *; }
-keep class go.** { *; }

# 忽略常见警告
-dontwarn androidx.compose.**
-dontwarn core.**
