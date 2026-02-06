# Snirect Android

基于 Go (gVisor) + Kotlin 的 Android 流量拦截与 SNI 修改工具。

## 项目结构

*   `android/`: Android 宿主工程 (Kotlin + Jetpack Compose)。
*   `core/`: Go 核心逻辑 (gVisor 网络栈 + SNI 逻辑)。

## 开发环境准备

1.  **Go**: 1.20+
2.  **Android Studio**: Hedgehog 或更新版本
3.  **Android NDK**: 推荐 26.x 或更高
4.  **gomobile**: 用于编译 Go 核心库

## 构建步骤

### 1. 编译核心库 (AAR)

由于 `gomobile` 在某些 CLI 环境下的依赖解析问题，建议在本地完整的 Shell 环境中执行：

```bash
# 安装 gomobile 工具
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init

# 进入核心目录
cd core

# 编译 AAR (确保已设置 ANDROID_HOME 和 ANDROID_NDK_HOME)
# 示例：
# export ANDROID_HOME=$HOME/Android/Sdk
# export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.1.10909125
gomobile bind -v -target=android -androidapi 21 -o ../android/app/libs/core.aar .
```

成功后，你应该在 `android/app/libs/` 下看到 `core.aar` 文件。

### 2. 运行 Android 应用

1.  用 Android Studio 打开 `android/` 目录。
2.  同步 Gradle。
3.  连接 Android 设备或模拟器。
4.  点击运行。

**注意**: 必须先完成步骤 1 生成 AAR，否则 Android 项目会因为找不到 `core.Core` 类而报错。

## 功能特性

*   **VPNService**: 接管系统流量。
*   **Transparent Proxy**: 使用 gVisor netstack 在用户态还原 TCP 流。
*   **SNI Modification**: 解析并修改 TLS ClientHello 中的 SNI 扩展。

## 故障排除

*   如果 `gomobile bind` 报错 `no Go package in golang.org/x/mobile/bind`：
    *   尝试 `go get -d golang.org/x/mobile/bind`。
    *   确保你的 `GOPATH` 设置正确。
*   如果 Android 编译报错 `Unresolved reference: core`：
    *   检查 `android/app/libs/core.aar` 是否存在。
    *   检查 `android/app/build.gradle` 中是否包含 `implementation fileTree(dir: 'libs', include: ['*.aar'])`。
