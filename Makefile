# --- 配置变量 ---
# 目标架构: android/arm64 (默认), android/arm, android/x86, android/x86_64
ARCH ?= android/arm64
# 构建类型: Release (默认), Debug
BUILD_TYPE ?= Release
# Android API Level
ANDROID_API ?= 21

# 输出路径
CORE_AAR := android/app/libs/core.aar
CORE_DIR := core
ANDROID_DIR := android

# Go 编译参数
# -s -w: 移除调试符号
# -trimpath: 移除源码路径信息
LDFLAGS := -ldflags="-s -w"
GO_FLAGS := -v -trimpath $(LDFLAGS)

# --- 核心指令 ---

.PHONY: all core app release debug clean install help

help:
	@echo "Snirect 构建管理工具"
	@echo "用法:"
	@echo "  make core         - 编译 Go 核心库 (当前 ARCH=$(ARCH))"
	@echo "  make app          - 编译 Android APK (当前 BUILD_TYPE=$(BUILD_TYPE))"
	@echo "  make all          - 同时执行 make core 和 make app"
	@echo "  make release      - 一键构建 Release 版 (Arm64 优化)"
	@echo "  make debug        - 一键构建 Debug 版"
	@echo "  make install      - 构建并安装到连接的设备"
	@echo "  make clean        - 清理生成产物"
	@echo ""
	@echo "变量控制 (可选):"
	@echo "  make core ARCH=android/arm64,android/arm  - 编译多架构 AAR"
	@echo "  make app BUILD_TYPE=Debug                 - 编译 Debug APK"

all: core app

# 编译 Go 核心库
core:
	@echo ">>> 正在编译 Go 核心库 [$(ARCH)]..."
	@cd $(CORE_DIR) && gomobile bind $(GO_FLAGS) -target=$(ARCH) -androidapi $(ANDROID_API) -o ../$(CORE_AAR) .
	@ls -lh $(CORE_AAR)

# 编译 Android 应用
app:
	@echo ">>> 正在构建 Android 应用 [$(BUILD_TYPE)]..."
	@cd $(ANDROID_DIR) && ./gradlew assemble$(BUILD_TYPE)
	@echo ">>> APK 路径:"
	@find $(ANDROID_DIR)/app/build/outputs/apk -name "*.apk" | xargs ls -lh

# 快捷方式：Release 构建 (Arm64)
release:
	@$(MAKE) core ARCH=android/arm64
	@$(MAKE) app BUILD_TYPE=Release

# 快捷方式：Debug 构建
debug:
	@$(MAKE) core ARCH=android
	@$(MAKE) app BUILD_TYPE=Debug

# 安装到设备
install:
	@cd $(ANDROID_DIR) && ./gradlew install$(BUILD_TYPE)

# 清理
clean:
	@echo ">>> 正在清理..."
	@rm -f $(CORE_AAR)
	@cd $(ANDROID_DIR) && ./gradlew clean
	@echo "清理完成"
