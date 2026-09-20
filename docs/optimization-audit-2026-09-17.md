# 代码优化审计（2026-09-17）

本文记录上一轮工程重构完成后的新基线与后续优化项。默认保持玩法、存档字段、注册 ID、配置和网络数据兼容；涉及玩法语义的修改仍回到 `docs/design/` 立项。

## 当前基线

- `src/main/java`：134 个 Java 文件、33,211 行。
- 排除 `gametest` 与数据生成代码后：121 个文件、24,022 行。
- 当前自动回归：142 个 GameTest。
- 最大的生产类依次为 `AbstractSteamProcessorMachine`（1,491 行）、`GSEProcessorPatterns`（1,327 行）、`LargeHeatStorageSteamFurnaceMachine`（1,186 行）、`LargeCokeOvenMachine`（1,064 行）和 `GSEJadePlugin`（936 行）。行数只用于定位审查入口，不单独作为重构理由。

## 已实施：配方热路径与重复产出解析

### 1. 处理机缓存刷新只响应 revision 变化

此前 `processorServerTick()` 每 tick 调用 `refreshRecipeCache()`；该方法每次都进入 `SteamRecipeCache.get()` 的同步区并无条件执行 `requestRecipeSearch()`。因此已有的输入变更订阅和 20 tick 空闲兜底实际上被绕过，空闲机器仍每 tick 搜索一次。

现在每台处理机记录上次见到的 `RecipeCacheLifecycle.revision()`，仅在 datapack revision 变化时重新读取共享缓存并唤醒搜索。输入变化仍由现有订阅即时唤醒，未发通知的外部处理器仍由 20 tick 兜底覆盖。回归测试固定了“revision 未变化时刷新不得重新置脏”的行为。

### 2. 粉碎机与熔炉接入共享配方索引

- 粉碎机从“每个输入堆遍历全部矿石配方”改为按物品读取 `SteamRecipeCache` 内容桶，再执行原有的单输入、Ingredient 与电压校验。
- 粉碎机和蓄热熔炉的批次恢复从线性 ID 扫描改为共享 `byId` 索引。
- 熔炉按持久化的 `batchRecipeMode` 选择恢复表，避免同时扫描熔炉与合金炉两张表。

### 3. 产出物品解析统一

处理机和粉碎机原本各维护一份相同的 `Content -> ItemStack` 解析，熔炉还跨包调用粉碎机实现。解析已集中到 `PendingOutputBuffer`，启动前最坏情况预检与批次完成使用同一规则，删除两份重复实现和不必要的熔炉到粉碎机依赖。

### 4. 弃用 API 清理

项目代码中的 `ResourceLocation` 旧构造器已替换为 `fromNamespaceAndPath` 或 `tryParse`；配置与客户端扩展注册直接使用构造器传入的 `FMLJavaModLoadingContext`。权重配置中的非法资源 ID 现在会按原设计记录警告并跳过，不会先由旧构造器抛出异常。

本批代码与测试合计净减少 36 行。验证要求为 `compileJava`、137 个 GameTest、`check` 与 `build` 全部通过。

## 已实施：粉碎机空闲搜索事件化

粉碎机现在只订阅实际输入部件的物品能力。普通输入总线变化会即时唤醒配方搜索；不发送通知的外部/ME 处理器和输出空间变化由 20 tick 定期重试覆盖。结构失效和区块卸载会解除旧订阅，批次完成会显式请求下一次搜索。按槽位顺序选择第一条可运行配方的语义保持不变。

现有粉碎机结算 GameTest 同时固定了两条性能行为：无通知且兜底期限未到时不得扫描；输入总线发生变化时必须将搜索置为待执行。

## 已实施：收敛 Jade 蒸汽机器快照

`GSEJadePlugin` 中粉碎机与处理机原本分别写入同一组状态、批次、进度、并行、蒸汽和待输出字段，也分别渲染相同顺序的 tooltip 行。现在两者通过内部 `SteamMachineSnapshot` 写入 13 个公共字段，并复用统一的状态、输入名称、进度、并行、蒸汽需求与待输出行渲染器；处理机 provider 只追加专用状态键、流体待输出和进气室信息。玩家侧快照不携带内部配方资源 ID。

现有粉碎机结算与鼓风炉 GUI/Jade GameTest 已扩展为协议回归：分别校验公共 NBT 字段集合、字段值、两种翻译键前缀和 tooltip 行顺序。数据键、字段名和显示顺序保持不变，GameTest 总数仍为 137。

## 已实施：拆分蓄热熔炉的温度与输入调度状态

`LargeHeatStorageSteamFurnaceMachine` 原本同时实现温度公式、预热/冷却状态推进和 distinct bus 的独立输入范围与轮询。现在纯 `FurnaceThermalLogic` 负责温度上限、启动温度、预热成本、冷却间隔、速度/蒸汽倍率以及热状态转换；运行期 `FurnaceInputScheduler` 负责 scope 重建、隔离搜索和成功后轮询。控制器只协调蒸汽扣取、配方启动和结果回写。

`formedWidth`、`formedHeight`、`currentTemperature`、`preheatProgressUnits`、`heatTimer`、`coolTimer`、`inputBusCursor` 与 `batchInputSourcePos` 等持久化字段仍保留在控制器，字段名和 NBT 布局没有变化。现有存档往返与双输入总线公平性测试继续覆盖兼容性；熔炉状态边界测试新增代表性预热计划、升温计时和冷却转换断言。控制器主体减少约 110 行。

## 已实施：合并合成站容器视图

`CraftingStationMenu` 现在由 `GridView` 提供唯一的 `CraftingContainer` 基础实现，`AugmentedView` 只覆写虚拟工具格的 `getItem()`。基础视图的物品列表与堆叠统计统一经由虚方法读取，使工具槽补位仍能被配方完整观察，并删除了约 60 行重复代理代码。

新增真实方块实体与菜单 GameTest，同时验证格内工具匹配、工具槽虚拟补位，以及虚拟匹配不会写入真实合成格。

## 已实施：拆分合成站事务

新增包内 `CraftingStationCrafting`，集中负责配方匹配、虚拟工具格、合成提交、工具损耗、相邻来源补料和余料回流；`CraftingStationMenu` 只保留槽位布局、来源分页、同步和 Shift 连续合成循环。Shift 操作仍锁定首次匹配的配方，单次提交仍按“扣取—余料—补料”顺序执行。

新增事务级 GameTest，覆盖工具槽虚拟补位与损耗、相邻箱子连续补料、格内工具余料留槽，以及不完整配方不得暴露旧预览或修改输入。

## 已实施：按功能域拆分 Jade provider

`GSEJadePlugin` 现在只保留 Jade 入口和注册委托。蒸汽处理机、焦炉、公用设备 provider 分别迁入 `SteamMachineJadeProviders`、`CokeOvenJadeProviders` 与 `UtilityJadeProviders`，注册及 tooltip 去重留在 `GSEJadeProviders`，共享的蒸汽机器快照与条形渲染器收敛到 `SteamMachineJadeSupport`。

provider UID、NBT 字段名、注册目标和 tooltip 行顺序保持不变；语言键校验已扩展为识别按功能域拆分后的包内 provider holder，运行测试中的 Jade provider 查找也同步覆盖这些 holder。

## 已实施：拆分锅炉房热状态与水垢公式

新增纯逻辑类 `BoilerRoomThermalLogic`，集中负责温度计时推进、除垢倒计时与阶段扣减、报废冷却、水垢产汽损失及等效满载寿命增量。`BoilerRoomMachine` 继续持有原有持久化字段，并只负责采集机器输入、扣取空气、应用结果及触发警告/报废等世界副作用，因此 NBT 布局保持不变。

锅炉房 GameTest 新增纯状态边界断言，固定升温 cadence、除垢最后一 tick 和单阶段水垢扣减行为。

## 已实施：拆分生存获取测试

新增 `GSEAcquisitionTests`，从 `GSEGameTests` 整体迁移获取配方清单、内部依赖无环检查和大型蒸汽高炉精确升级配方 3 个测试，以及只被这些测试使用的 4 个辅助方法。测试方法名、模板和超时保持不变，`GSEGameTests` 从 2,262 行降至 1,991 行，GameTest 总数仍为 138。

## 已实施：拆分蒸汽仓室与迁移测试

新增 `GSESteamHatchTests`，从 `GSEGameTests` 整体迁移供汽、流体、进气仓和旧蒸汽仓迁移相关的 12 个测试，以及只被这些测试使用的 5 个辅助方法。通用的单机器放置夹具收敛到 `GSEStructureTestUtils.placeMachine()`；测试方法名、模板和超时保持不变，`GSEGameTests` 从 1,991 行降至 1,439 行，GameTest 总数仍为 138。

## 已实施：拆分结构成型测试

新增 `GSEStructureFormationTests`，从 `GSEGameTests` 整体迁移 30 个结构成型测试和 3 个形状坐标辅助方法，统一承载预览形状、逐坐标与方块数量、水平朝向、共用墙体、仓室位置/数量和非法接口边界。测试方法名、模板和超时保持不变，`GSEGameTests` 从 1,439 行降至 547 行，GameTest 总数仍为 138。

## 已实施：拆分蓄热蒸汽熔炉运行测试

新增 `GSEFurnaceTests`，从 `GSESteamEngineTests` 整体迁移热状态、控制器持久化、待输出结算、大型供汽仓超频锁定、输出并行容量和独立输入仓轮询 6 个测试，以及 4 个熔炉专用辅助方法。新增包内 `GSESteamEngineTestSupport`，统一承载真实结构成型、批次状态边界、蒸汽与输出操作、状态保存和反射适配等 27 个共享方法，两个测试类通过静态导入复用，未复制夹具实现。`GSESteamEngineTests` 从约 2,850 行降至 2,263 行，GameTest 总数仍为 138。

## 已实施：拆分大型蒸汽高炉运行测试

新增 `GSEBlastFurnaceTests`，从 `GSESteamEngineTests` 整体迁移风汽原子扣取、GUI/Jade 状态、排气与缺汽分界、真实待输出持久化、控制器拆除掉落、三重并行限制、配方偏好、锻铁—炼钢闭环、96 并行满载供汽/进气和能力输入兼容 11 个测试，以及 7 个高炉专用辅助方法。输入总线访问、处理机状态清理、库存统计和 GUI/Jade 代理等 13 个跨组件方法继续收敛到 `GSESteamEngineTestSupport`。`GSESteamEngineTests` 从 2,263 行降至 1,361 行，GameTest 总数仍为 138。

## 已实施：拆分自动化接口兼容测试

新增 `GSEAutomationInterfaceTests`，从 `GSESteamEngineTests` 整体迁移蒸汽破碎机、处理机、化学浸洗机、小型离心机、大型焦炉和普通焦炉的创造/ME/样板总成输入输出接口兼容 6 个测试，以及接口组合验证和注册方块解析 2 个专用辅助方法。仍被处理机超频夹具使用的结构内方块定位方法迁入 `GSESteamEngineTestSupport`；同时清理前几轮抽取留下的连续空行。`GSESteamEngineTests` 从 1,361 行降至 1,098 行，GameTest 总数仍为 138。

## 已实施：拆分蒸汽组装机运行测试

新增 `GSEAssemblerTests`，从 `GSESteamEngineTests` 整体迁移大型蒸汽组装机和电路组装机的状态边界、空槽 ULV 限制、槽位堆叠并行与经济阶梯，以及电路组装机物品—焊料原子扣取 7 个测试，并迁移 2 个组装机专用辅助方法。批次状态、输入总线、输出容量和反射访问继续复用 `GSESteamEngineTestSupport`。`GSESteamEngineTests` 从 1,098 行降至 893 行、从 27 个测试降至 20 个，GameTest 总数仍为 138。

## 已实施：拆分虚空生产机运行测试

新增 `GSEVoidProducerTests`，从 `GSESteamEngineTests` 整体迁移虚空生产机状态边界、控制器持久化、待输出恢复和大型供汽仓超频锁定 4 个测试，以及停用配置守卫与输出库存计数 2 个专用辅助方法。处理机仍使用的流体待输出统计迁入 `GSESteamEngineTestSupport`，由两个测试类共享。`GSESteamEngineTests` 从 893 行降至 750 行、从 20 个测试降至 16 个，GameTest 总数仍为 138。

## 已实施：拆分蒸汽破碎机运行测试

新增 `GSECrusherTests`，从 `GSESteamEngineTests` 整体迁移破碎机状态边界、控制器持久化、输入唤醒与 Jade 快照、批次结算、并行容量、多产物容量竞争和大型供汽仓超频锁定 6 个测试，以及 4 个破碎机专用辅助方法。处理机同样使用的并行容量与多产物容量竞争辅助方法迁入 `GSESteamEngineTestSupport`。`GSESteamEngineTests` 从 750 行降至 474 行、从 16 个测试降至 10 个，仅保留处理机运行契约；GameTest 总数仍为 138。

## 已实施：统一排气反馈与伤害周期推进

处理机、破碎机、虚空生产机和大型蓄热蒸汽熔炉此前各自维护相同的 20 tick 排气反馈与 200 tick 热伤害计时推进，其中前三类包含逐字相同的方法。现在 `SteamExhaustHatchMachine` 提供无状态的 `advanceFeedbackCycle` 和 `advanceDamageCycle`，控制器仅保存并回写原有计时字段。四类控制器的字段名、类型和 NBT 格式保持不变，只有成功完整扣取蒸汽的 tick 才推进周期；重复的周期阈值判断与效果触发实现已删除。

## 已实施：统一协同燃烧粉料规则

混合燃料锅炉与四档锅炉房原本各自维护一份相同的粉料标签校验和燃烧时间换算。现在 `CoFiringPowderFuel` 集中定义白名单判断、煤/木炭/焦炭/木粉基础热值、Forge 燃料回退，以及小撮粉 `1/4`、微小撮粉 `1/9` 的换算。两类机器仍独立负责取料、消耗速率和持久化，原有槽位、结构能力与 NBT 字段不变；现有混合燃料锅炉测试新增六组精确热值断言。

## 后续优化顺序

### P2：继续拆分运行态与结构测试

`GSEGameTests` 已降至约 550 行，`GSESteamEngineTests` 已降至 474 行并只保留处理机测试，运行态测试拆分已基本收敛。下一批可重新审计生产代码热点和剩余大类；通用运行夹具继续收敛在 `GSESteamEngineTestSupport`，保持 GameTest 名称、模板和总数不变。

## 暂不处理

- `GSEProcessorPatterns` 虽然行数较大，但结构 pattern 与预览 shape 已使用单一几何数据源，剩余内容以声明式结构层为主。
- `GSELang`、`GSERecipes` 和注册表的大部分行是数据声明；在没有生成一致性问题或重复语义前，单纯压缩行数收益有限。
- 三类蒸汽引擎保留少量相同 getter 与生命周期骨架。现有组合组件已经承载易漂移的业务逻辑，继续引入共同父类会增加耦合，当前没有足够收益。
