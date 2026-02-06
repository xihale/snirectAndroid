# Android Snirect 总体设计报告

## 1. 项目概览 (Overview)

本项目旨在将 `snirect` 核心功能移植至 Android 平台，实现一个基于透明代理（Transparent Proxy）的流量整形与 SNI 修改工具。

*   **核心目标**: 利用 Android `VpnService` 拦截系统流量，通过 Go 语言编写的核心模块进行 SNI 解析、修改与转发，以实现特定的网络访问需求。
*   **开发模式**: 混合开发 (Hybrid)。
    *   **控制层 (Android/Kotlin)**: 负责 UI 交互、系统服务生命周期、权限管理、VPN 隧道建立。
    *   **核心层 (Go)**: 负责网络协议栈处理 (TCP/IP)、TLS 握手解析、策略匹配、流量转发。

## 2. 技术栈 (Tech Stack)

| 组件 | 技术选型 | 说明 |
| :--- | :--- | :--- |
| **UI 框架** | **Kotlin + Jetpack Compose** | 采用 Material Design 3 规范，构建现代化、响应式的用户界面。 |
| **核心逻辑** | **Go (Golang)** | 复用 `snirect` 现有逻辑，高性能并发处理网络 IO。 |
| **跨语言桥接** | **Gomobile** | 将 Go 代码编译为 Android AAR 库，实现 Kotlin 与 Go 的双向调用。 |
| **网络拦截** | **Android VpnService** | 创建 TUN 虚拟网卡，拦截 IP 数据包。 |
| **用户态协议栈** | **gVisor / tun2socks** | (在 Go 侧) 解析 TUN 设备传来的 IP 包，还原为 TCP/UDP 流以便应用层处理。 |
| **配置存储** | **DataStore / Room** | 管理用户配置、规则列表。 |

## 3. 系统架构 (Architecture)

### 3.1 模块划分

```mermaid
graph TD
    subgraph "Android Application Layer (Kotlin)"
        UI[UI / Activity] -->|Config/Control| VM[ViewModel]
        VM -->|Start/Stop| Svc[VPN Service]
        Svc -->|TUN FD / Config| GoLib[Go Shared Library (Gomobile)]
        CertMgr[Certificate Manager] -->|Install Intent| SysCert[Android System KeyChain]
    end

    subgraph "Native Core Layer (Go)"
        GoLib -->|Read/Write| TunIO[TUN Interface Handler]
        TunIO -->|IP Packets| NetStack[Userspace Network Stack]
        NetStack -->|TCP/UDP Stream| ProxyCore[Snirect Proxy Engine]
        ProxyCore -->|Match Rules| RuleEngine[Rule Engine]
        ProxyCore -->|Modify SNI| Upstream[Remote Server]
    end
    
    Svc -.->|Notifications| Notif[Notification Bar]
```

### 3.2 关键流程

1.  **启动流程**:
    *   用户点击 "Connect"。
    *   Kotlin 请求 `VpnService` 权限。
    *   建立 VPN 接口 (TUN device)，配置路由 (如 `0.0.0.0/0`) 和 DNS。
    *   获取 TUN 文件的 File Descriptor (FD)。
    *   通过 Gomobile 将 FD 传递给 Go 核心层。
    *   Go 核心层启动 `tun2socks` 风格的处理器，开始读取 FD 数据。

2.  **流量处理**:
    *   IP 包进入 Go 内存空间。
    *   协议栈解析出 TCP 连接。
    *   `snirect` 逻辑介入：解析 ClientHello，提取 SNI。
    *   根据规则修改 SNI 或目标地址。
    *   建立到真实服务器的连接并转发数据。

## 4. 详细模块设计

### 4.1 VPNService (Kotlin)
*   **功能**:
    *   构建 `VpnService.Builder`。
    *   设置 MTU (通常 1500)。
    *   设置 DNS (如 1.1.1.1 或本地伪装 DNS)。
    *   应用分流 (Per-app proxy): 使用 `addAllowedApplication` / `addDisallowedApplication`。
*   **交互**: 监听 Go 层的状态回调 (Connecting, Connected, Disconnected, Error) 并更新通知栏。

### 4.2 核心引擎 (Go)
需要适配 `snirect` 的现有代码以支持库模式运行，而非独立 CLI。

*   **入口函数**: `Start(fd int, configJson string)`
*   **SNI 修改器**: 针对 TLS 连接，解析首个数据包，根据配置表替换 Server Name 扩展字段。
*   **证书管理 (Certificate)**:
    *   如果需要解密 HTTPS (MITM)，Go 层需动态签发证书。
    *   提供 `GetCACertificate()` 接口供 Kotlin 层获取并保存为 `.crt` 文件供用户安装。

### 4.3 配置管理
*   **规则文件**: 支持 JSON/YAML 格式导入。
*   **UI 设置**:
    *   **路由模式**: 全局 / 仅代理列表 / 仅直连列表。
    *   **SNI 策略**: 静态映射 / 动态生成。
    *   **DNS 设置**: 远程 DNS 解析策略 (避免 DNS 污染)。

### 4.4 通知栏 (Notification)
*   显示当前上传/下载速度 (由 Go 层定期回调统计数据)。
*   提供 "停止" 按钮快捷操作。

## 5. 开发路线图 (Roadmap)

1.  **Phase 1: 骨架搭建**
    *   初始化 Android 项目 (Kotlin)。
    *   初始化 Go 模块，配置 Gomobile 编译环境。
    *   实现基本的 `VpnService` 启动与停止，验证 TUN FD 传递。

2.  **Phase 2: 核心移植**
    *   引入用户态协议栈 (如 `gvisor/pkg/tcpip` 或轻量级替代)。
    *   将 `snirect` 的连接处理逻辑接入协议栈。
    *   实现基础的 TCP 直连测试。

3.  **Phase 3: 功能实现**
    *   实现 SNI 解析与修改逻辑。
    *   实现规则配置的加载与热重载。
    *   UI 开发: 仪表盘、日志视图。

4.  **Phase 4: 完善与优化**
    *   证书安装引导流程。
    *   分应用代理 (Bypass mode)。
    *   耗电量优化与稳定性测试。
