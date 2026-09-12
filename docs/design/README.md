# 设计文档索引与当前状态

状态核对日期：2026-09-13；代码基线：`86ac9b5`。当前处于 Alpha，主任务是验证生存获取与生产闭环，并完成游戏内验收。工程重构 P0～P2 已完成，历史实现和门禁记录见[工程路线图](../refactor-roadmap.md)。

“已定案”表示设计规则已确认；“已实现”表示已有代码与资源，不保证每条规格均已验证；“已验收”只记录用户实际执行并确认的结果。本次同步记录了 2026-09-12 启动期难度系统的游戏内验收，不将其他代码与定案的差异自动改写为新规则。

## 已实现内容与规格入口

代码入口均相对于 `src/main/java/com/hoshino/gregsteamexpansion/`。机器注册集中在 `registry/GSEMachines.java`，获取配方集中在 `data/GSERecipes.java`。

| 内容 | 规格 | 主要实现入口 |
| --- | --- | --- |
| 启动期工作强度与 GTCEu 配方难度预设 | [全局难度](difficulty.md) | `difficulty/` |
| 青铜构件、四种结构方块、上游工业蒸汽机械方块补充配方 | [物品与方块](items-and-blocks.md) | `registry/GSEBlocks.java`、`data/GSERecipes.java` |
| 蒸汽供给、流体输入/输出、空气进气仓 | [机器与部件](machines-and-hatches.md) | `machine/multiblock/part/` |
| 混合燃料锅炉、四档锅炉房 | [混合燃料锅炉](mixed-fuel-boiler.md)、[锅炉房](boiler-room.md) | `machine/steam/MixedFuelBoilerMachine.java`、`machine/multiblock/BoilerRoomMachine.java` |
| 合成站与竖半砖 | [合成站](crafting-station.md) | `blockentity/CraftingStationBlockEntity.java`、`menu/CraftingStationMenu.java`、`client/CraftingStationScreen.java` |
| 蓄热熔炉、蒸汽排气仓 | [大型蓄热蒸汽熔炉](large-heat-storage-steam-furnace.md) | `machine/multiblock/LargeHeatStorageSteamFurnaceMachine.java`、`registry/GSEFurnacePatterns.java` |
| 矿石粉碎迁移、蒸汽双机、MV–UV 电力粉碎机 | [矿石粉碎](ore-crushing.md)、[蒸汽粉碎机](steam-crushers.md) | `recipe/`、`machine/multiblock/crusher/` |
| 普通焦炉与焦炉仓、大型焦炉与大型焦炉仓 | [焦炉体系](coke-ovens.md) | `machine/multiblock/cokeoven/`、`machine/multiblock/largecokeoven/`、`cokeoven/` |
| 蒸汽压缩机、提取机、锻压机 | [压缩机](steam-compressor.md)、[提取机](steam-extractor.md)、[锻压机](steam-forge.md) | `machine/multiblock/processor/` |
| 洗矿厂、研磨厂、化学浸洗厂、搅拌机 | [洗矿](large-steam-ore-washer.md)、[研磨](large-steam-macerator.md)、[浸洗](large-steam-chemical-bath.md)、[搅拌](large-steam-mixer.md) | `machine/multiblock/processor/` |
| 离心机双机、热力离心机 | [离心机](steam-centrifuges.md)、[热力离心机](large-steam-thermal-centrifuge.md) | `machine/multiblock/processor/` |
| 组装机、电路组装机 | [组装机](large-steam-assembler.md)、[电路组装机](large-steam-circuit-assembler.md) | `machine/multiblock/processor/AbstractSteamAssemblerMachine.java` |
| 大型蒸汽高炉、三条铁粉锻铁注入配方 | [大型蒸汽高炉](large-steam-blast-furnace.md) | `machine/multiblock/processor/LargeSteamBlastFurnaceMachine.java`、`data/GSERecipes.java` |
| 虚空采矿厂、流体钻井 | [采矿厂](large-steam-ore-plant.md)、[流体钻井](large-steam-fluid-drill.md) | `machine/multiblock/voidproducer/` |
| 结构诊断、跨内容展示规则 | [结构诊断](structure-diagnostics.md)、[杂项](miscellaneous.md) | `structure/`、`integration/`、`mixins/` |

以上内容仍待完整游戏内验收。`large-steam-forge.md` 为 B6 的[旧路径入口](large-steam-forge.md)，不代表另有一台待实现的大型锻压厂。

## 当前待办

1. 走通“基础材料 → 产汽与供汽 → 矿物处理 → 锻铁/钢 → 零件装配”的生存获取与生产链，记录配方、材料前置和实际供汽瓶颈。
2. 按机器规格核验断汽恢复、输出堵塞、结构拆建、存档重载、档位重启后的机器状态迁移，以及 GUI/Jade、EMI 和外部容器交互；启动期全局难度配置本身已验收。
3. 补充存档/NBT 往返、完整流体加工链、多产物竞争剩余容量等自动化覆盖；自动化结果与手动验收分别记录。
4. 核对以下静态发现的设计/实现差异，按原定案要求修复；若需改变玩法规则，则独立讨论。

| 待核对项 | 已有证据与处理边界 |
| --- | --- |
| 熔炉总线隔离的轮询持久化 | 规格要求持久化轮询游标，当前熔炉控制器未见对应持久化字段；需核对调度行为，详见[熔炉实现对照](large-heat-storage-steam-furnace.md#实现对照与待核验事项)。 |
| 熔炉专用蒸汽仓扩展接口预留 | 现有标准蒸汽路径已实现，尚需单独核对主体要求的显式扩展接口与能力标识；不由此启用任何未定案的混合配方行为。 |

按 Markdown `- [ ]` 统计，当前 16 份规格共有 193 项未勾选验收项。焦炉、合成站、熔炉等还使用表格、编号或普通列表记录核验范围，因此 193 不是全项目待办总数。

## 候选与验证边界

[后续机器候选清单](next-machine-candidates.md)中的 C4 发酵厂、C5a 筛分厂、C5b 磁选厂及其他新方向均未立项；先核验配方生态与闭环需求，再决定是否扩展。

锅炉房按用户更新采用“顶面中央 11 格仅外壳或进气室、进气室 1–11 个”，保留正确的预览朝向和温度/粉料仪表。旧预览第二层火室两侧的部件需要移除，进气条带上的其他仓室需要移至普通外壳位。截至 2026-09-13，debug 客户端已验收兼容总线粉料识别与配方启动、持续供气、空气缓存耗尽暂停、恢复供气、持续产汽、四档满温满节流目标产量、仅协同燃烧的模式边界、结构接口规则、无进气停产提示、四档空气消耗序列、缺粉暂停和加速冷却恢复、运行期缺水单次爆炸、蒸汽接口禁用与标准接口可用性、消音器泄压、节流、粉料仪表和状态同步等 GUI 行为、四档控制器的工作台与组装机获取配方，以及中英文文本与纹理叠加层；其他锅炉房项目仍按规格清单逐项验收。

全仓库现有 77 个 GameTest（锅炉房共 11 个，启动难度预设 1 个）。难度测试逐项核对当前启动档位写入 GTCEu 的 19 个配方难度布尔项与 `casingsPerCraft`。锅炉房完整燃料启动链覆盖：标准流体仓注入水与杂酚油、原生或 GTM Things 创造物品输入总线注入煤粉、进气室供气后，配方必须开始推进并升温；同时锁定背面消音器朝外且出口无阻挡。2026-09-12 的 65 项历史复验记录见[工程路线图](../refactor-roadmap.md#完成态基线2026-09-12)，当前验证命令见[项目 README](../../README.md#测试与-ci)。历史通过记录不代表每次文档更新都重新运行了构建，也不替代用户验收。
