# Greg Steam Expansion

基于 Minecraft 1.20.1 Forge 与 GregTech CEu Modern 的蒸汽时代扩展附属模组。

> **⚠ 前瞻版（Alpha）**：模组尚不处于可游玩状态——玩法闭环未完成，已实现部分未经完整游戏内验收，内容、数值、资源 ID 与接口随时可能变动，不建议用于正式存档。当前进度预览见 [Releases](https://github.com/MinazukiGear/gregsteamexpansion/releases)。
>
> **⚠ 本项目大量使用 AI 辅助开发**：代码、设计文档、语言条目与部分贴图资源均在 AI 协助下完成，全部产出经人工审核后合入。使用的模型包括 **Hy4 preview**、**GLM-5.3-Flash**、**GPT-5.6-Sol** 与 **GPT-6-Astra**。AI 生成的数值、结构规则与接口约定一律以 `docs/design/` 中标注为"已定案"的章节为准，未标注的内容不代表最终设计。欢迎审阅源码与反馈问题。

## 内容一览

- **混合燃料蒸汽锅炉**（低压/高压）：液体燃料与协同燃烧双模式，支持 Jade 信息显示。
- **合成站与合成站竖半砖**：工作台上位替代，可外部读取附近容器。
- **青铜构件与蒸汽结构方块**：蒸汽碾磨/装配/电路装配/搅拌方块，为蒸汽多方块机器的标准组件。
- **蒸汽仓室体系**：蒸汽供给仓、蒸汽排气仓、蒸汽流体输入/输出仓、蒸汽空气进气仓，替代上游蒸汽输入仓；排气仓带阻塞判定与排气灼伤机制。
- **大型蓄热蒸汽熔炉**：可变尺寸（7×7 / 11×11 / 15×15，高度 6–18）纯蒸汽多方块熔炉，含强制预热、温度体系、并行批次加工、配方模式切换与控制器运行信息页。
- **蒸汽粉碎机 / 大型蒸汽粉碎机**：独立于研磨机的第一段矿物处理多方块，支持 EMI 结构预览，并作为矿石处理迁移配方的消费机器接入。
- **普通焦炉与焦炉仓**：接管 `gtceu:coke_oven` 注册身份并覆盖上游获取配方，焦炉仓支持物品输入/物品输出/流体输出三种模式（尚未经游戏内验收）。
- **大型焦炉与大型焦炉仓**：独立注册为 `gregsteamexpansion:large_coke_oven` 与 `gregsteamexpansion:large_coke_oven_hatch`，不复用上游普通焦炉身份。`7×7×5` 完整包围范围，三炉室 + 顶部砖制进料斗，需 `3–5` 个仓且三种模式各至少一个；最大并行 `6`、固定 `0.5×` 原配方耗时、不消耗任何能源，满载吞吐等同 12 台普通焦炉。含一氧化碳环境危害、所有权互斥、批次结算与三炉门同步渲染（已实现，尚未经游戏内验收）。
- **全局工作强度机制**：Easy / Normal / Expert 三档存档级难度，影响产量、预热成本、蒸汽消耗等数值。

### 已定案设计（待实现）

以下机器已完成设计定案（规格见 [`docs/design/`](docs/design/) 对应文档），尚未进入代码实现：

- **锅炉房**（青铜/钢/钛/钨钢四档）：协同燃烧产汽终端，蒸汽进气室前置，产能逐档上位于同档大型锅炉（`boiler-room.md`）。
- **大型蒸汽洗矿厂**：执行 `ore_washer` 全类型，`11×11×6` 三区外壳 + 蒸汽搅拌十字，并行 64，越级以蒸汽消耗平衡（`large-steam-ore-washer.md`）。
- **大型蒸汽研磨厂**：执行 `macerator` 全类型，外接 `7×7×7` 球形结构 + 碾磨方块十字，并行 64（`large-steam-macerator.md`）。
- **蒸汽化学浸洗厂**：执行 `chemical_bath` 全类型，`3×4×3` 紧凑机身（`large-steam-chemical-bath.md`）。
- **大型蒸汽组装机**：执行 `assembler` 全类型，`9×9×9` 装配大厅；控制器槽位装入 LV–EV 电力组装机解锁配方等级并提升并行（ULV–EV / 并行 1–16），Easy 档产出 2×（`large-steam-assembler.md`）。
- **大型蒸汽电路组装机**：执行 `circuit_assembler` 全类型，宽 `5` × 深 `11` × 高 `6` 中轴工艺塔；槽位机制与能耗阶梯与组装机一致（`large-steam-circuit-assembler.md`）。

详细设计规格见 [`docs/design/`](docs/design/)；其中 [`docs/design/next-machine-candidates.md`](docs/design/next-machine-candidates.md) 是 2026-09-07 重制的**后续机器可实现列表**（含已立项状态与裁定留痕），不代表已确认内容。

## 开发环境

| 组件 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.10 |
| Java | 17 |
| GregTech CEu Modern | 7.5.3（必需前置） |
| Gradle | 8.8（项目 Wrapper） |

EMI、Jade、精妙背包/存储、Modern UI、GTM Things（连同其必需的 AE2 和 AE2 的前置 GuideME）等仅作为开发客户端测试工具由 Gradle 运行时加载，不是本模组前置，也不会打包进发布 JAR。

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
- 当前版本：`0.1.0`（发布标签 [`v0.1.0-alpha.1`](https://github.com/MinazukiGear/gregsteamexpansion/releases)，前瞻版）
- 许可：代码与功能性资源 LGPL-3.0（`LICENSE.txt`）；`textures/` 图像素材 CC BY-NC-SA 4.0，禁止商用（`LICENSE-ASSETS.txt`）

## 致谢

本模组的部分设计参考了以下作品，感谢原作者：

- 整合包：
  - [GregTech: New Horizons (GTNH)](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack)
  - [GregTech: Odyssey (GTO)](https://github.com/GregTech-Odyssey/GregTech-Odyssey)
- 模组：
  - [Immersive Technology](https://github.com/tgstyle/MCT-Immersive-Technology)

## 已知上游问题

开发客户端同时加载 GTCEu 内嵌 LDLib 与 EMI 时可能遇到 Mixin 初始化竞态（`MixinTargetAlreadyLoadedException` / `EmiPlugin was loaded too early`），参见 [GregTechCEu/GregTech#2917](https://github.com/GregTechCEu/GregTech/issues/2917)；不影响未安装 EMI 的环境。
