# Greg Steam Expansion

基于 Minecraft 1.20.1 Forge 与 GregTech CEu Modern 的蒸汽时代扩展附属模组。

> **⚠ 测试版（Alpha）**：全部机器均已通过基础游戏内运行测试，但完整生存流程、存档重载、异常恢复与跨模组自动化仍在持续验证。内容、数值、资源 ID 与接口仍可能变动，不建议用于重要存档。当前测试版本见 [Releases](https://github.com/MinazukiGear/gregsteamexpansion/releases)。
>
> **⚠ 本项目大量使用 AI 辅助开发**：代码、设计文档、语言条目与部分贴图资源均在 AI 协助下完成，全部产出经人工审核后合入。使用的模型包括 **Hy4 preview**、**GLM-5.3-Flash**、**GPT-5.6-Sol** 与 **GPT-6-Astra**。AI 生成的数值、结构规则与接口约定一律以 `docs/design/` 中标注为"已定案"的章节为准，未标注的内容不代表最终设计。欢迎审阅源码与反馈问题。

## 内容一览

- **混合燃料蒸汽锅炉**（低压/高压）：液体燃料与协同燃烧双模式，支持 Jade 信息显示。
- **合成站与合成站竖半砖**：工作台上位替代，可外部读取附近容器。
- **青铜构件与蒸汽结构方块**：蒸汽碾磨/装配/电路装配/搅拌方块，为蒸汽多方块机器的标准组件。
- **蒸汽仓室体系**：蒸汽供给仓、仅大型机器可用且供汽量为四倍的 256,000 mB 大型蒸汽供给仓、蒸汽排气仓、蒸汽流体输入/输出仓、蒸汽空气进气仓，替代上游蒸汽输入仓；大型供给仓还会在控制器 UI 中解锁下一配方生效的 3 倍每刻耗汽 / 50% 耗时超频开关，排气仓带阻塞判定与排气灼伤机制。
- **大型蓄热蒸汽熔炉**：可变尺寸（7×7 / 11×11 / 15×15，高度 6–18）纯蒸汽多方块熔炉，含强制预热、温度体系、并行批次加工、配方模式切换与控制器运行信息页。
- **蒸汽粉碎机 / 大型蒸汽粉碎机 / 电力粉碎机**：独立于研磨机的第一段矿物处理多方块（蒸汽双机）与 MV–UV 分级单方块电力机器，共享矿石粉碎迁移配方（矿石主产物 4× 基线 × 难度倍率），支持 EMI 结构预览。
- **普通焦炉与焦炉仓**：接管 `gtceu:coke_oven` 注册身份并覆盖上游获取配方；可在 `0–5` 个接口配额内混用三模式焦炉仓、创造/标准/ME 物品接口与 ME 样板总成。
- **大型焦炉与大型焦炉仓**：独立注册为 `gregsteamexpansion:large_coke_oven` 与 `gregsteamexpansion:large_coke_oven_hatch`，不复用上游普通焦炉身份。`7×7×5` 完整包围范围，三炉室 + 顶部砖制进料斗，需 `3–5` 个合法接口并满足物品输入、物品输出和流体输出；物品方向可使用自有仓、创造/标准/ME 总线或 ME 样板总成。最大并行 `6`、固定 `0.5×` 原配方耗时、不消耗任何能源，满载吞吐等同 12 台普通焦炉。含一氧化碳环境危害、所有权互斥、批次结算与三炉门同步渲染。
- **轻量蒸汽多方块家族**：蒸汽压缩机（C1a）、蒸汽提取机（C1b）、蒸汽锻压机（B6）——紧凑结构（前两者 3×3×3，锻压机 3×3×5）、并行 8、LV 电压门、无排气仓。
- **大型蒸汽处理厂家族**：洗矿厂（S1，并行 64）、研磨厂（A4，球形 7×7×7）、化学浸洗厂（B4）、搅拌机（B3）、蒸汽离心机与大型蒸汽离心机（C0）、大型蒸汽热力离心机（C0b）——全配方运行对应 GTCEu 类型，越级以蒸汽消耗平衡，大型机配蒸汽排气仓。
- **大型蒸汽组装机 / 大型蒸汽电路组装机**（B1/B2）：运行 `assembler` / `circuit_assembler` 全类型；控制器组装机槽位（1–4 台同等级电力组装机，不可混装）解锁配方等级并提升并行至 2/4/8/16，次线性蒸汽/耗时阶梯，Easy 档产出 2×。电路组装机另有一个不消耗参照物的电路专精槽：匹配配方耗时与总耗汽增加 50%，并按启动档位获得额外目标电路，默认 Easy `100% / +7×`、Normal `50% / +3×`、Expert `25% / +1×`。
- **大型蒸汽高炉**（G1，旗舰）：运行 `primitive_blast_furnace` 全类型的原始高炉上位多方块。`13×13` 底、`15` 高的三段收分巨塔，主体以高炉砖（`gtceu:firebricks`，与原始高炉同款材质）砌成，辅以 121 格焦炭砖炉床与工业蒸汽机械方块骨架；并行 `96`（全模组最高），连续完成同一精确配方可把耗时从新手档逐级降至精通档（默认 Easy `75%→45%`、Normal `80%→50%`、Expert `90%→60%`）。满载需 `19,200 mB/t` 蒸汽（恰 16 个供给仓）与全部 8 个鼓风口的鼓风空气（4 mB/t/并行；蒸汽进气室必需化，家族首个必需进气室机型）——极高造价与极大的结构换取极高效率。
- **锻铁大宗配方注入**（随 G1 落地）：向 `primitive_blast_furnace` 类型 add-only 注入 3 条"铁粉 + 燃料粉 → 锻铁"配方，原始高炉同步可用，补足上游仅有铁粒零散路线（铁粒烧锻铁粒为上游既有 `gtceu:wrought_iron_nugget`，本模组不重复添加）的锻铁产能缺口。
- **锅炉房**（青铜/钢/钛/钨钢四档，S2）：仅协同燃烧的产汽终端，蒸汽进气室硬性前置（助燃空气 50/100/200/400 mB/t 逐档递增），产汽逐档上位于同档大型锅炉；液体燃料白名单由数据包加载时注入。带可配置水垢机制：舒适/压榨档按 24/8 小时等效满载寿命分档减产，第三档向所有者在线团队发聊天警告，满垢永久报废；可停机冷却后用稀盐酸酸洗。
- **终极终端**：在同时安装 AE2 与 GTM Things 时启用的高级结构工程终端。它直接识别运行时实现 GTCEu `IMultiController` 的任意控制器，不限制 `gtceu`、GSE 或第三方附属模组命名空间；可为最多 16 个同维度目标建立持久任务，按控制器公开的 `BlockPattern` 补建、修复，或安全拆除已成型及未成型结构中仍匹配蓝图的普通方块。内置结构尺寸信道支持可变宽度机器，线圈与配置选材信道在独立“信道”页使用方块下拉选择。终端固定不放置仓室；生存模式材料先从玩家物品栏、再从已绑定的 AE2 无线终端网络原子预留，创造模式则无视两处数量直接放置；服务器默认每刻最多处理 32 个方块。
- **大型蒸汽采矿厂 / 大型蒸汽流体钻井**（F1/F2 旗舰）：纯虚空生产机器——采矿厂 4 工位每 200 tick 按权重抽取粗矿 ×8/抽（满速 12,000 mB/t），流体钻井 2 泵位抽主世界油类流体 2,000 mB/抽（满速 6,000 mB/t）；产出倍率 Easy 4× / Normal 2× / Expert 1×；配置开关与可覆盖权重表见 `machines.large_steam_ore_plant.*` / `machines.large_steam_fluid_drill.*`（重启生效，禁用时机器不可运行）。
- **可选全局工作强度机制**：默认关闭；启用后可选 Easy / Normal / Expert，影响产量、预热成本、蒸汽消耗等数值。三档的全部运行时参数、5 个本模组配方加难开关和 19 个 GTCEu 配方开关均可由整合包作者分别配置；本模组非控制器方块产量与 `gtceuCasingsPerCraft` 统一。首次启动可在游戏内选择，保存并完整重启后生效；关闭时所有难度倍率为 `1×`，且不接管 GTCEu 难度配置。

上述机器的设计规格与验收清单见 [`docs/design/`](docs/design/) 对应文档；[`docs/design/next-machine-candidates.md`](docs/design/next-machine-candidates.md) 是**后续机器可实现列表**（含已立项/已实现状态与裁定留痕），剩余可实现方向为发酵厂（C4）、筛分厂（C5a）、磁选厂（C5b）及锯木厂等新方向，均未立项。

截至 2026-09-13，使用者已确认全部机器能够在游戏内正常运行；这代表基础运行冒烟测试通过，不等同于各设计文档中的存档、异常状态、兼容性和数值边界清单已经全部验收。对应清单位于各设计文档末尾，设计状态、代码入口与当前待办汇总见 [设计文档索引](docs/design/README.md)。

2026-09-15，使用者在 Expert 开发客户端中确认 B1 大型蒸汽组装机与 B2 大型蒸汽电路组装机的本轮验收成功；本轮覆盖生存链衔接以及两台机器的结构、槽位、界面和实际运行主路径。未逐项反馈的存档、异常状态与跨模组组合仍保留在各自清单中。

## 开发环境

| 组件 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.10 |
| Java | 17 |
| GregTech CEu Modern | 7.5.3（必需前置） |
| Gradle | 8.8（项目 Wrapper） |

EMI、Jade、精妙背包/存储、Modern UI，以及 ExtendedAE-Plus（连同 ExtendedAE、Glodium 和 GuideME）等仅作为开发客户端测试工具由 Gradle 运行时加载，不会打包进发布 JAR。AE2 与 GTM Things 仍是可选依赖：不安装时其余 GSE 内容正常加载，但终极终端不会出现在创造标签中、配方也不会加载；两者同时安装后该功能启用。

## 开始开发

```powershell
.\gradlew.bat genIntellijRuns       # 生成 IDEA 运行配置（JDK 17）
.\gradlew.bat runClient             # 启动开发客户端
.\gradlew.bat runGameTestServer     # 运行全部 GameTest（159 个，见下）
.\gradlew.bat build -x test         # 构建发布 JAR（build/libs/）
.\gradlew.bat runData               # 重新生成数据（资源/配方/语言）
```

若系统默认 Java 不是 17，先设置 `$env:JAVA_HOME` 指向 JDK 17。

### 测试与 CI

本项目的回归保护全部由 **GameTest** 承担，测试代码在
`src/main/java/com/hoshino/gregsteamexpansion/gametest/`：

| 文件 | 覆盖内容 |
| --- | --- |
| `GSEGameTests` | 混合燃料锅炉行为、配方重写一致性、焦炉仓与过期占用、组装机控制器槽、配方类型接线及合成站容器视图 |
| `GSECraftingStationTests` | 合成站工具补位与损耗、相邻来源补料、Shift 连续合成、余料留槽和失败回滚 |
| `GSEAcquisitionTests` | 已实现内容的获取配方清单、原料标签解析与内部依赖无环检查，以及大型蒸汽高炉精确升级配方 |
| `GSESteamHatchTests` | 蒸汽供汽/流体/进气仓能力、专用蒸汽源声明、大型供汽仓超频经济、流体仓互换回滚、覆盖板状态与旧蒸汽仓方块/物品迁移 |
| `GSEStructureFormationTests` | 30 个结构成型契约：预览形状逐坐标与数量、水平朝向、共用墙体、仓室位置/数量及非法接口边界 |
| `GSEBoilerRoomTests` | 四档进气条带位置/数量、非法仓室拒绝、多进气室汇总供气与不足不扣、仪表同步、状态提示、原生/GTM Things 物品输入、完整燃料启动链，以及水垢阈值/累计/酸洗/NBT/报废与稀盐酸配方 |
| `GSESteamTankTests` | 大型蒸汽储罐可变尺寸/容量、共享存量与标准蒸汽过滤、阀门主动输入/输出及 ME 接口类相邻流体库存兼容（3 个） |
| `GSEDifficultyGameTests` | 启动档位、可配置难度参数、GTCEu 配方映射、旗舰机器开关和权重表 |
| `GSERecipeOptimizationTests` | 配方缓存失效与空闲机器唤醒 |
| `GSESteamEngineTests` | 蒸汽处理机的状态边界、批次结算、输入与流体容量、持久化及大型供汽仓超频锁定（10 个） |
| `GSECrusherTests` | 蒸汽破碎机的状态边界、持久化、搜索唤醒、批次结算、并行容量、GUI/Jade 及超频锁定（6 个） |
| `GSEAssemblerTests` | 大型蒸汽组装机与电路组装机的槽位等级、并行/经济阶梯、状态边界、物品—焊料原子扣取及电路专精增产（8 个） |
| `GSEVoidProducerTests` | 蒸汽虚空生产机的状态边界、控制器持久化、待输出恢复及大型供汽仓超频锁定（4 个） |
| `GSEFurnaceTests` | 大型蓄热蒸汽熔炉的热状态、控制器持久化、待输出结算、大型供汽仓超频锁定、输出并行容量及独立输入仓轮询（6 个） |
| `GSEBlastFurnaceTests` | 大型蒸汽高炉的风汽原子扣取、GUI/Jade、排气与缺汽状态、待输出持久化、三重并行限制、配方偏好、炼钢闭环、96 并行满载边界，以及熟练度门槛/重置/迁移（15 个） |
| `GSEAutomationInterfaceTests` | 蒸汽破碎机、处理机、化学浸洗机、离心机及两类焦炉的创造/ME/样板总成输入输出接口兼容，以及 ME 输出总成的自动输出唤醒（7 个） |
| `GSESteamEngineTestSupport` | 运行态测试共享夹具：真实结构成型、蒸汽/输出操作、输入总线、状态保存、批次边界、GUI/Jade 和反射适配 |
| `GSEStructureDiagnosticsTests` | 缺块/数量/一致性诊断、候选去重截断、哨兵状态安全、NBT 传输及成型后清除（7 个） |
| `GSETerminalTests` | 终极终端配置 NBT 往返、全息蓝图状态分类、已成型与未成型结构拆除、结构尺寸/无仓室规划、创造模式无限材料，以及原生 GTCEu 控制器通用兼容（7 个） |
| `GSEStructureTestUtils` | 结构辅助：用机器注册的 `MultiblockShapeInfo` 反铺方块，再用图案校验 |

> `src/test` 为空目录，本项目**不使用 JUnit**；`build` 任务中的 `-x test` 是 Forge MDK 模板遗留，
> 没有任何测试任务会被它跳过（该选项保留只是为了避免 Gradle 报"无测试源"）。

提交前跑一遍完整门禁：

```powershell
.\gradlew.bat compileJava runGameTestServer build   # 最小集
bash tools/verify.sh                                # 与 CI 完全一致的完整门禁
```

处理机控制器的跨服务器进程持久化使用专用双阶段验证。脚本先启动一个 GameTest
服务器写入并落盘状态，再启动全新的服务器进程读取、核对并清理测试区块：

```powershell
python tools/verify_server_restart.py
python tools/verify_server_restart.py --offline     # 依赖已缓存时可离线运行
```

启动配置的跨进程重启验证会依次运行 Easy、Normal 与 Expert 三套临时配置，核对 GTCEu
难度预设、旗舰机器开关和权重表，并在结束或失败后恢复原配置：

```powershell
python tools/verify_config_restart.py
python tools/verify_config_restart.py --offline
```

`tools/verify.sh` 是 `.github/workflows/build.yml` 的本地镜像，依次执行编译 →
GameTest → datagen 新鲜度（`runData` 后不应产生 git diff）→ 构建。Gradle `check` 会在构建中
隔离生成并逐像素核对程序化 PNG、逐字节核对其他生成资产，同时检查 `en_us`/`zh_cn` 键集合及所有已注册 Jade UID 的
配置翻译，并抽样核对设计文档与代码中的结构尺寸、并行上限及获取配方科技阶段边界。CI 在 push 与 PR 上执行同一组
步骤，因此本地通过即可认为 CI 会通过。

仓库中的程序化贴图与 GameTest 空结构统一由 Python 3.10+ 生成。在仓库根目录安装固定版本
依赖并运行统一入口；脚本顺序、输出归属等约定见 [`tools/README.md`](tools/README.md)：

```powershell
python -m pip install -r tools/requirements.txt
python tools/generate_assets.py
python tools/generate_assets.py --check
```

构建缓存：`compileJava` 因 Mixin 注解处理器把 refmap 写成旁路产物而禁用了构建缓存
（`build.gradle` 的 `outputs.cacheIf { false }`），CI 上以缓存 `~/.gradle` 与
`extractGtceuEmbeddedDependencies` 的固定重跑作为补偿。

## 项目信息

- Mod ID：`gregsteamexpansion`
- 入口类：`com.hoshino.gregsteamexpansion.GregSteamExpansion`
- 当前开发版本：`0.1.0-alpha.6`（测试版；上一发布标签为 [`v0.1.0-alpha.5`](https://github.com/MinazukiGear/gregsteamexpansion/releases/tag/v0.1.0-alpha.5)）
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
