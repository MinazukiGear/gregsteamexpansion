# Greg Steam Expansion

基于 Minecraft 1.20.1 Forge 与 GregTech CEu Modern 的蒸汽时代扩展附属模组。

> **⚠ 本模组仍在开发中**，内容、数值、资源 ID 与接口随时可能变动，不建议用于正式存档。
>
> **⚠ 本项目大量使用 AI 辅助开发**：代码、设计文档与部分资源均在 AI 协助下完成并经人工审核，欢迎审阅源码与反馈问题。

## 内容一览

- **混合燃料蒸汽锅炉**（低压/高压）：液体燃料与协同燃烧双模式，支持 Jade 信息显示。
- **合成站与合成站竖半砖**：工作台上位替代，可外部读取附近容器。
- **青铜构件与蒸汽结构方块**：蒸汽碾磨/装配/电路装配/搅拌方块，为后续蒸汽多方块机器的标准组件。
- **蒸汽排气仓**：兼容多方块蒸汽机器的通用排气接口，带阻塞判定与排气灼伤机制。
- **大型蓄热蒸汽熔炉**：可变尺寸（7×7 / 11×11 / 15×15，高度 6–18）纯蒸汽多方块熔炉，含强制预热、温度体系、并行批次加工、配方模式切换与控制器运行信息页。
- **矿石粉碎配方类型**：独立于研磨机的第一段矿物处理（消费者机器开发中）。
- **全局工作强度机制**：Easy / Normal / Expert 三档存档级难度，影响产量、预热成本、蒸汽消耗等数值。

详细设计规格见 [`docs/design/`](docs/design/)。

## 开发环境

| 组件 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.10 |
| Java | 17 |
| GregTech CEu Modern | 7.5.3（必需前置） |
| Gradle | 8.8（项目 Wrapper） |

EMI、Jade、精妙背包/存储、Modern UI 等仅作为开发客户端测试工具由 Gradle 运行时加载，不是本模组前置，也不会打包进发布 JAR。

## 开始开发

```powershell
.\gradlew.bat genIntellijRuns   # 生成 IDEA 运行配置（JDK 17）
.\gradlew.bat runClient         # 启动开发客户端
.\gradlew.bat build -x test     # 构建发布 JAR（build/libs/）
.\gradlew.bat runData           # 重新生成数据（资源/配方/语言）
```

若系统默认 Java 不是 17，先设置 `$env:JAVA_HOME` 指向 JDK 17。

## 项目信息

- Mod ID：`gregsteamexpansion`
- 入口类：`com.hoshino.gregsteamexpansion.GregSteamExpansion`
- 当前版本：`0.1.0`
- 许可：代码与功能性资源 LGPL-3.0（`LICENSE.txt`）；`textures/` 图像素材 CC BY-NC-SA 4.0，禁止商用（`LICENSE-ASSETS.txt`）

## 已知上游问题

开发客户端同时加载 GTCEu 内嵌 LDLib 与 EMI 时可能遇到 Mixin 初始化竞态（`MixinTargetAlreadyLoadedException` / `EmiPlugin was loaded too early`），参见 [GregTechCEu/GregTech#2917](https://github.com/GregTechCEu/GregTech/issues/2917)；不影响未安装 EMI 的环境。
