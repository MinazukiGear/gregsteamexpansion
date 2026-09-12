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
- **蒸汽粉碎机 / 大型蒸汽粉碎机 / 电力粉碎机**：独立于研磨机的第一段矿物处理多方块（蒸汽双机）与 MV–UV 分级单方块电力机器，共享矿石粉碎迁移配方（矿石主产物 4× 基线 × 难度倍率），支持 EMI 结构预览。
- **普通焦炉与焦炉仓**：接管 `gtceu:coke_oven` 注册身份并覆盖上游获取配方，焦炉仓支持物品输入/物品输出/流体输出三种模式（尚未经游戏内验收）。
- **大型焦炉与大型焦炉仓**：独立注册为 `gregsteamexpansion:large_coke_oven` 与 `gregsteamexpansion:large_coke_oven_hatch`，不复用上游普通焦炉身份。`7×7×5` 完整包围范围，三炉室 + 顶部砖制进料斗，需 `3–5` 个仓且三种模式各至少一个；最大并行 `6`、固定 `0.5×` 原配方耗时、不消耗任何能源，满载吞吐等同 12 台普通焦炉。含一氧化碳环境危害、所有权互斥、批次结算与三炉门同步渲染（已实现，尚未经游戏内验收）。
- **轻量蒸汽多方块家族**：蒸汽压缩机（C1a）、蒸汽提取机（C1b）、蒸汽锻压机（B6）——紧凑结构（前两者 3×3×3，锻压机 3×3×5）、并行 8、LV 电压门、无排气仓。
- **大型蒸汽处理厂家族**：洗矿厂（S1，并行 64）、研磨厂（A4，球形 7×7×7）、化学浸洗厂（B4）、搅拌机（B3）、蒸汽离心机与大型蒸汽离心机（C0）、大型蒸汽热力离心机（C0b）——全配方运行对应 GTCEu 类型，越级以蒸汽消耗平衡，大型机配蒸汽排气仓。
- **大型蒸汽组装机 / 大型蒸汽电路组装机**（B1/B2）：运行 `assembler` / `circuit_assembler` 全类型；控制器组装机槽位（1–4 台同等级电力组装机，不可混装）解锁配方等级并提升并行至 2/4/8/16，次线性蒸汽/耗时阶梯，Easy 档产出 2×。
- **大型蒸汽高炉**（G1，旗舰）：运行 `primitive_blast_furnace` 全类型的原始高炉上位多方块。`13×13` 底、`15` 高的三段收分巨塔，主体以高炉砖（`gtceu:firebricks`，与原始高炉同款材质）砌成，辅以 121 格焦炭砖炉床与工业蒸汽机械方块骨架；并行 `96`（全模组最高）、`0.4×` 耗时，满载吞吐为原始高炉的 `240×`。满载需 `19,200 mB/t` 蒸汽（恰 16 个供给仓）与全部 8 个鼓风口的鼓风空气（4 mB/t/并行；蒸汽进气室必需化，家族首个必需进气室机型）——极高造价与极大的结构换取极高效率。
- **锻铁大宗配方注入**（随 G1 落地）：向 `primitive_blast_furnace` 类型 add-only 注入 3 条"铁粉 + 燃料粉 → 锻铁"配方，原始高炉同步可用，补足上游仅有铁粒零散路线（铁粒烧锻铁粒为上游既有 `gtceu:wrought_iron_nugget`，本模组不重复添加）的锻铁产能缺口。
- **锅炉房**（青铜/钢/钛/钨钢四档，S2）：仅协同燃烧的产汽终端，蒸汽进气室硬性前置（助燃空气 50/100/200/400 mB/t 逐档递增），产汽逐档上位于同档大型锅炉；液体燃料白名单由数据包加载时注入，Easy 档产汽 2×。
- **大型蒸汽采矿厂 / 大型蒸汽流体钻井**（F1/F2 旗舰）：纯虚空生产机器——采矿厂 4 工位每 200 tick 按权重抽取粗矿 ×8/抽（满速 12,000 mB/t），流体钻井 2 泵位抽主世界油类流体 2,000 mB/抽（满速 6,000 mB/t）；产出倍率 Easy 4× / Normal 2× / Expert 1×；配置开关与可覆盖权重表见 `machines.large_steam_ore_plant.*` / `machines.large_steam_fluid_drill.*`（重启生效，禁用时机器不可运行）。
- **全局工作强度机制**：Easy / Normal / Expert 三档存档级难度，影响产量、预热成本、蒸汽消耗等数值。

上述机器的设计规格与验收清单见 [`docs/design/`](docs/design/) 对应文档；[`docs/design/next-machine-candidates.md`](docs/design/next-machine-candidates.md) 是**后续机器可实现列表**（含已立项/已实现状态与裁定留痕），剩余可实现方向为发酵厂（C4）、筛分厂（C5a）、磁选厂（C5b）及锯木厂等新方向，均未立项。

各机器的实际游戏测试与最终验收由使用者执行；对应验收清单位于各设计文档末尾。设计状态、代码入口与当前待办汇总见 [设计文档索引](docs/design/README.md)。

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
.\gradlew.bat genIntellijRuns       # 生成 IDEA 运行配置（JDK 17）
.\gradlew.bat runClient             # 启动开发客户端
.\gradlew.bat runGameTestServer     # 运行全部 GameTest（77 个，见下）
.\gradlew.bat build -x test         # 构建发布 JAR（build/libs/）
.\gradlew.bat runData               # 重新生成数据（资源/配方/语言）
```

若系统默认 Java 不是 17，先设置 `$env:JAVA_HOME` 指向 JDK 17。

### 测试与 CI

本项目的回归保护全部由 **GameTest** 承担，测试代码在
`src/main/java/com/hoshino/gregsteamexpansion/gametest/`：

| 文件 | 覆盖内容 |
| --- | --- |
| `GSEGameTests` | 结构成型、仓室行为、注册一致性、难度与配方注入 |
| `GSEBoilerRoomTests` | 四档进气条带位置/数量、非法仓室拒绝、多进气室汇总供气与不足不扣、仪表同步、状态提示、原生/GTM Things 物品输入及完整燃料启动链 |
| `GSERecipeOptimizationTests` | 配方缓存失效与空闲机器唤醒 |
| `GSESteamEngineTests` | 四类引擎状态边界、原子取汽、并行/输出容量、批次结算、排气和鼓风（14 个） |
| `GSEStructureDiagnosticsTests` | 缺失方块与接口数量不足的结构诊断 |
| `GSEStructureTestUtils` | 结构辅助：用机器注册的 `MultiblockShapeInfo` 反铺方块，再用图案校验 |

> `src/test` 为空目录，本项目**不使用 JUnit**；`build` 任务中的 `-x test` 是 Forge MDK 模板遗留，
> 没有任何测试任务会被它跳过（该选项保留只是为了避免 Gradle 报"无测试源"）。

提交前跑一遍完整门禁：

```powershell
.\gradlew.bat compileJava runGameTestServer build   # 最小集
bash tools/verify.sh                                # 与 CI 完全一致的完整门禁
```

`tools/verify.sh` 是 `.github/workflows/build.yml` 的本地镜像，依次执行编译 →
GameTest → datagen 新鲜度（`runData` 后不应产生 git diff）→ 构建。Gradle `check` 会在构建中
隔离生成并逐字节核对程序化资产，同时检查 `en_us`/`zh_cn` 键集合及所有已注册 Jade UID 的
配置翻译，并抽样核对设计文档与代码中的结构尺寸、并行上限。CI 在 push 与 PR 上执行同一组
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
