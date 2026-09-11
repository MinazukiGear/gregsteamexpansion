# 代码优化方向清单（工程重构路线图）

本文档登记 Greg Steam Expansion 当前代码库的**工程结构与运行时性能**优化方向，按优先级排序。重构默认保持既有行为；已经由用户明确裁定的行为调整列在下方，并同步回写 `docs/design/`。

> **状态（2026-09-11 更新）**：已分批实施，当前进度见下表。原始问题、行号与度量保留为历史基线（`main` @ `bcaf777`，113 个 Java 文件 / 27,781 行），不代表当前代码规模。
>
> **性质**：工程债清单，不是设计文档。凡涉及玩法语义的改动，仍须先回到 `docs/design/` 走设计流程。
>
> **度量口径**：文中所有数字均由只读扫描脚本实测得出，复现方式见[附录](#附录度量脚本)。

## 结论速览

| 优先级 | 项 | 主要收益 | 规模 | 风险 |
| --- | --- | --- | --- | --- |
| **P0-1** | 抽取统一蒸汽多方块引擎基类 | 消除 4 份引擎拷贝；把每台新机器成本从 ~3000 行降到数百行 | 大 | 中（需先补测试） |
| **P0-2** | 配方缓存改为静态共享 + 按输入索引 | 消除每实例全量拷贝与空闲机器全表线性扫描 | 小 | 低 |
| **P1-3** | 结构图案与预览 shape 单一数据源 | 结构微调从改 3 处变为改 1 处 | 中 | 中 |
| **P1-4** | `GSEMachines` tooltip 表驱动化 | 约 900 行样板降到 100 行量级 | 小 | 低 |
| **P1-5** | 建立 CI 并把 GameTest 接进构建门禁 | 45 个既有测试从"靠人记得跑"变为自动保护 | 小 | 低 |
| **P2-6** | 收敛侵入性 Mixin | 降低上游版本升级时的整体报废风险 | 小～中 | 中 |
| **P2-7** | 补齐引擎行为测试 | 为 P0-1 铺安全网 | 中 | 低 |
| **P2-8** | reload 路径消除全量 Map 深拷贝 | 数据包重载耗时 | 小 | 低 |
| **P2-9** | 仓库与工具链卫生 | 降低认知负担 | 小 | 极低 |

**后续排期**：P0-1（先部件收集，再抽取 `PendingOutputBuffer`、`SteamBudget`、`BatchStateMachine` 和展示层）→ P1-3 / P1-4 → P2-6 / P2-9 剩余项。

### 重构行为约束（2026-09-11 用户定案）

以下约束用于后续所有优化提交，不再逐项询问：

1. 所有使用实体蒸汽供给仓的蒸汽多方块，每个供给仓的机器侧输入上限统一为 `1,200 mB/t`；供给仓的结构要求统一为至少 `1` 个。结构不保证能够跑满最大并行，玩家可按实际需求增设供给仓。
2. 取消所有多方块机器对普通机械方块/外壳方块的最低数量限制，并取消仅为维持该最低数量而设置的仓室或接口总数上限。固定功能方块、允许的接口类型，以及输入、输出、排气、进气等功能接口自身的必需数量规则保持有效。
3. 四类蒸汽引擎的结构失效行为统一为：保留批次与待输出，并把正在运行的进度回退至 `1 tick`。
4. GUI 与 Jade 的“当前每刻蒸汽需求”使用当前状态语义：运行中、蒸汽不足或辅助输入不足时显示恢复运行所需需求；主动暂停、配置禁用、输出堵塞、排气堵塞、结构无效或等待原尺寸时显示 `0 mB/t`。
5. 禁用上游蒸汽输入仓继续采用严格、可读的启动失败策略。现有 `LegacySteamHatchCompat` 启动自检满足该约束：无法核验或禁用不完整时抛出包含具体违规项的 `IllegalStateException`。
6. Java 类、继承层级及内部方法不承诺第三方二进制兼容；必须保持存档数据、注册 ID、配置和网络协议兼容，任何必要迁移都需显式实现。

### 实施进度（2026-09-11）

| 项 | 当前状态与剩余工作 |
| --- | --- |
| P1-5 | CI 与 `tools/verify.sh` 已建立，包含编译、GameTest、datagen 新鲜度、语言键对齐和构建；README 已记录命令。远端运行结果需以 GitHub Actions 为准。 |
| P0-2 | 已实现输入内容索引与空输入短路；本轮将单槽共享缓存改为当前 revision 下按类型身份保存的缓存表，revision 变化时清空，避免交替访问不同类型反复重建。扩展回归覆盖交替类型访问、两种类型 reload 失效、旧条目保持不变和空输入候选为空。 |
| P2-7 | 行为基线已完成：新增 `GSESteamEngineTests` 的 14 个测试，全仓库 64 个 GameTest 全绿，编译与构建通过；覆盖矩阵及已发现差异见 P2-7 正文。 |
| P0-1 / P1-3 / P1-4 | 尚未完成。先补行为基线，再按组件逐步抽取，保留各机器家族的显式差异。 |
| P2-8 | 已引入 `RecipeManagerTables`，配方迁移与锅炉燃料同步使用共享的按需拷贝工具。 |
| P2-9 | 部分完成：MDK changelog 已改名为 `changelog-forge-mdk.txt`，语言键对齐已接入 CI；工具统一、资产生成校验和设计数字抽样断言仍待处理。 |

语言检查另有待补缺口：中英文键集合对齐无法发现两边同时遗漏的 Jade 配置名称，应增加已注册 Jade UID 到默认语言翻译的完整性检查。本轮保留此前补齐的 `structure_diagnostics` 翻译。

P0-2 轮验证：JDK 17 下 `compileJava runGameTestServer build` 成功，全部 50 个 GameTest 通过（扩展既有测试，未新增测试方法）。P2-7 验证见下文。

---

## 度量基线

| 指标 | 实测值 |
| --- | --- |
| `src/main/java` | 113 文件 / 27,781 行 |
| 四个多方块引擎类合计 | 5,129 行（1717 / 1289 / 1147 / 976） |
| 四个引擎类中的方法 | 269 个 / 方法体合计 3,378 行 |
| 其中存在跨类近同构孪生的方法 | 58 个 / 2,517 行（**74%**） |
| 全仓库跨文件**逐字节相同**的函数体 | 82 组 / 230 个重复实例 / 可删 840 行 |
| `GSEProcessorPatterns` | 1,645 行；74 处 `.aisle()`、42 行 shape 层、**182 处 `.where()`** |
| `GSEMachines` tooltip 区块 | L644–L1521，约 30 个同构方法，约 900 行 |
| GameTest | 45 个 `@GameTest`（44 个方法），其中 20 个结构成型断言、**3 个引擎行为测试** |
| `docs/` 设计文档 | 27 文件 / 10,403 行 |

### 已经发生的破坏（不是理论风险）

引擎拷贝之间**已经出现行为漂移**：

- `demandText()`：`AbstractSteamProcessorMachine.java:1502` 判据为 `!lastTickConsumedSteam`；`AbstractSteamVoidMachine.java:798` 被改成 `getStatusId().equals("working") && !lastTickConsumedSteam`。
- `pendingSummaryText()`：processor / crusher / void 三家实现互不相同。

git 历史同样印证该模式——同一个 bug 在不同引擎拷贝里被反复单独修复：

| commit | 说明 | 只改了 |
| --- | --- | --- |
| `0f79012` | 进度行改为纯 Java 拼接，根治 format error | crusher |
| `bcd6556` | 粉碎机/熔炉界面数值百分号转义 | crusher |
| `7fd8403` | 修复粉碎机/熔炉取汽被供给仓能力面拦截 | crusher |
| `d4c03b0` | 修复并行产物平方与熔炉供给仓收集 | crusher |
| `8778fd2` | 修复蒸汽流体仓入场均计数的两处潜在结构缺陷 | processor |

---

## P0-1　抽取统一蒸汽多方块引擎基类

### 问题

四个巨型基类实为**同一台引擎的四份拷贝**：

| 文件 | 行数 |
| --- | --- |
| `machine/multiblock/processor/AbstractSteamProcessorMachine.java` | 1,717 |
| `machine/multiblock/LargeHeatStorageSteamFurnaceMachine.java` | 1,289 |
| `machine/multiblock/crusher/AbstractSteamCrusherMachine.java` | 1,147 |
| `machine/multiblock/voidproducer/AbstractSteamVoidMachine.java` | 976 |

每个类都同时承担了**部件收集、接口数量校验、取汽预算、批次状态机、并行度计算、最坏情况输出预检、待输出暂存与投递、排气循环、鼓风辅助输入、工作状态渲染、状态文案、完整 ModularUI**这十余项职责。

### 证据

四个类共 269 个方法、3,378 行方法体，其中 **58 个方法（2,517 行，74%）在另一个引擎类里存在近同构孪生**（token 重合度 ≥0.60）：

| 方法 | 份数 | 合计行数 | 相似度 |
| --- | --- | --- | --- |
| `collectParts()` | 4 | 207 | 1.00 |
| `createUI()` | 4 | 174 | 1.00 |
| `drawSteam()` | 4 | 161 | 0.98 |
| `getStatusId()` | 4 | 102 | 0.90 |
| `completeBatch()` | 3 | 101 | 1.00 |
| `runBatchTick()` | 3 | 95 | 0.96 |
| `insertIntoBus()` | 4 | 91 | **1.00（逐字节相同，每份 23 行）** |
| `largestParallelThatFits()` | 2 | 76 | 0.97 |
| `deliverPendingFluids()` | 2 | 70 | 1.00 |
| `validateInterfaceCounts()` | 3 | 66 | 0.75 |
| `deliverPendingOutputs()` | 4 | 64 | 1.00 |
| `onStructureFormed()` | 4 | 63 | 0.94 |
| `updateWorkingSoundClient()` | 3 | 62 | 0.95 |
| `onStructureInvalid()` | 4 | 60 | 1.00 |
| `runExhaustCycles()` | 4 | 51 | 1.00 |
| `mergeStacks()` | 3 | 51 | 1.00 |
| `updateWorkingAppearance()` | 4 | 49 | 1.00 |
| … | | | |

全仓库层面，**逐字节完全相同**的跨文件函数体有 82 组、230 个重复实例，仅此一项即可删除约 840 行（不含上述近似重复，也不含只有常量不同的分支）。

### 影响

1. **路线图成本被乘法级放大**。`docs/design/next-machine-candidates.md` 第七节把蒸汽拉线机 / 切割机 / 折弯机 / 成型机 / 包装机列为"骨架现成，设计量最低"。实测每台机器的真实成本是：
   - `7daf3f4`（大型蒸汽组装机 + 电路组装机 + 锅炉房）：**53 文件 / +3,390 行**
   - `4ab9f73`（F1 采矿厂 + F2 流体钻井）：**30 文件 / +2,761 行**，其中 `AbstractSteamVoidMachine` 是 976 行**新增的引擎拷贝**，真正的业务逻辑不到 300 行。

2. **持续性损耗**。任何一个引擎级修复都要重复落地 4 次，且已经出现漂移（见上文）。这与项目既有原则直接冲突——`docs/design/steam-crushers.md:28` 明确写着"**共用行为的修复应优先落在基类**"；当前的 4 份引擎拷贝使这条原则在跨家族层面无法执行。

### 建议方案

先补测试（P2-7），再按以下粒度分 5 次提交，每一块都能独立用现有 GameTest 回归：

1. `SteamPartCollector` —— 部件收集 + `validateInterfaceCounts` 接口计数校验
2. `PendingOutputBuffer` —— 待输出暂存、稳定序投递、`insertIntoBus` 模拟/执行路径
3. `SteamBudget` —— 取汽、总额度、辅助输入（鼓风）
4. `BatchStateMachine` —— 批次状态与统一的结构失效回退、暂停/堵塞冻结语义
5. `SteamStatusText` + `SteamProcessorUI` —— 状态文案族与 ModularUI

### 验收方式

- 全部 GameTest 全绿（当前 50 个原有测试 + P2-7 新增 14 个）；
- 四个引擎类的行为差异被压缩为**显式覆写点**，且每个覆写点有注释说明差异原因。

### 风险

中。这是全项目最大的一次结构调整。**必须先有 P2-7 的测试网**，否则并行度、回退、投递这些没有测试覆盖的语义在搬移过程中极易静默改变。

---

## P0-2　配方缓存改为静态共享 + 按输入索引

### 问题

`AbstractSteamProcessorMachine.refreshRecipeCache()`（L211–L221）在**每个实例**上做全量拷贝：

```java
cachedRecipes = type == null ? List.of() : List.copyOf(type.getRecipesInCategory(type.getCategory())); // L217
for (GTRecipe recipe : cachedRecipes) recipesById.put(recipe.getId(), recipe);                          // L219
```

调用点：`processorServerTick()` L531（**每 tick**）、`findRecipeById()` L845。

### 证据与影响

1. **每实例一份全量拷贝**。revision 变化时，每台已成型机器各自复制该类型的全部配方并重建 `id → recipe` 的 HashMap。而 `difficulty` 系统会在**开服**（`GSEDifficultyEvents.java:97`）和**首次选择难度**（`GSEDifficultyEvents.java:169`）各触发一次**整包 datapack reload**，正好把这一刻放大成「机器数 × 配方数」的卡顿尖峰。

2. **空闲机器仍线性全表扫描**。`tryStartBatch()` L745–L761：

```java
for (GTRecipe recipe : cachedRecipes) {        // L754
    if (recipe != preferred && !isAirIntakeRecipe(recipe) && tryStartRecipe(recipe)) return;
}
```

无输入的机器必然扫完全表，且由 `IDLE_RECIPE_RETRY_TICKS = 20`（L186）保证每 20 tick 重来一次。每个候选走 `acceptsRecipe` → `passesVoltageGate` → `isAirIntakeRecipe`（L718，**每个候选分配一个 `FluidStack`**）→ `RecipeHelper.matchRecipe`。20 台空闲大型组装机 × assembler 类型数千条配方 ÷ 20 tick，量级已不可忽略。

### 建议方案

1. 把配方缓存提升为**进程级静态缓存**，键为 `(RecipeCacheLifecycle.revision(), GTRecipeType)`，所有实例共享同一份 `List` 与索引。
2. 在构建缓存时顺手建索引（`recipesById` 已经在建，加两张表成本极低）：`Map<Item, List<GTRecipe>>` / `Map<Fluid, List<GTRecipe>>`，让 `tryStartBatch` 按总线实际内容取候选，而不是全表遍历。
3. 廉价兜底（可先单独提交）：`tryStartBatch()` 入口加"所有输入总线为空则直接 return"的短路。
4. 顺带修掉 `isAirIntakeRecipe` 的重复调用与每候选 `FluidStack` 分配（两个循环各调一次，L755 / L759）。

### 验收方式

- `GSERecipeOptimizationTests` 的缓存失效与空闲唤醒测试继续通过；
- 新增：断言多台同类型机器共享同一份缓存实例；断言空输入机器不进入候选遍历。

### 风险

低。改动面小、可独立提交、现有测试即可验证。

---

## P1-3　结构图案与预览 shape 改为单一数据源

### 问题

`registry/GSEProcessorPatterns.java`（1,645 行）中，每台机器的几何写了**两遍**：

- 一遍 `FactoryBlockPattern` 的 `.aisle()`（**74 处**）
- 一遍 `MultiblockShapeInfo` 的 `String[][]` 层（**42 行**）+ 各自独立的 `.where()` 字符映射（**共 182 处**）

改一次结构要动 pattern、shape、以及设计文档三处。

### 现状缓解（重要）

`gametest/GSEStructureTestUtils.java:39 assertFirstShapeForms()` 会用机器上注册的 `MultiblockShapeInfo` 反铺方块，再反过来用 pattern 校验（L56），因此**"shape 与 pattern 打架"会被现有的 20 个 `*FormsFromShape` 测试抓到**。这是本项目做得很好的一点，也是本项排 P1 而非 P0 的原因。

### 残余风险

- 只校验 `shapes.get(0)`（L45），多形状机器的其余形状无保护（如熔炉的 7/11/15 三种宽度）；
- 每次结构微调仍需人工同步三处，且文档那一处完全没有校验。

### 建议方案

让 shape 从 pattern 的 aisle 数据派生：同一份 `String[]` 层数据，同时挂"谓词映射表"与"代表方块映射表"，只在确实需要特定代表性摆法时做局部覆写。

---

## P1-4　`GSEMachines` tooltip 表驱动化

### 问题

`registry/GSEMachines.java`（1,620 行）的 L644–L1521 是约 30 个形如 `xxxTooltips(ItemStack, List<Component>)` 的方法，每个约 40 行，全部是同一张信息卡片的骨架 + 不同的 lang key。合计约 **900 行纯样板**，占该文件一半以上。

### 建议方案

改为一张 `List<TooltipRow>` 数据表 + 一个渲染器，可降到 100 行量级。副作用是新机器加 tooltip 从"复制 40 行"变成"加 5 行数据"——与 P0-1 的方向一致。

### 验收方式

- 逐机器对比重构前后的 tooltip 输出（可用 GameTest 断言关键条目的 lang key 序列）。

### 风险

低。纯展示层，行为等价性易验证。

---

## P1-5　建立 CI 并把 GameTest 接进构建门禁

### 问题

- 仓库**无任何 CI 配置**（无 `.github/`、无 `.gitlab-ci.yml`）。
- `build.gradle` L50–L52 已定义 `gameTestServer` 运行配置，commit 记录也写着"all 43 GameTests passed"，但 **README 的「开始开发」段（L46–L51）没有任何 GameTest 命令**，`docs/` 全库 0 处提及。新贡献者无从得知这 45 个测试存在。
- `src/test/java`、`src/test/resources` 是**空目录**；`build.gradle` 中没有任何 `testImplementation` / JUnit 依赖；而 README L49 的构建命令是 `gradlew.bat build -x test`——测试任务实际处于被显式跳过的状态。

三者叠加的后果：**当前所有回归保护都依赖人工记得运行 GameTest**。

### 建议方案

1. 补 CI（push / PR 触发）：至少 `runGameTestServer` + `build`。
2. README「开始开发」补上 GameTest 运行命令。
3. 决定 `src/test` 的去留：要么接入 JUnit 并去掉 `-x test`，要么删除空目录并明确"本项目只用 GameTest"。

### 附带的构建优化

`build.gradle` L270–L272 主动禁用了 `compileJava` 的构建缓存（因为 Mixin AP 写的 refmap 是旁路产物，`outputs.cacheIf { false }`）。有 CI 之后这个代价才值得，同时应专门缓存 `extractGtceuEmbeddedDependencies`（L105–L125，每次都强制 `outputs.upToDateWhen { false }`）来补偿。

---

## P2-6　收敛侵入性 Mixin

现有 mixin 注释写得很清楚，但风险等级不同：

| Mixin | 现状 | 建议 |
| --- | --- | --- |
| `GTCEuMixin.java:34` | 每次 `GTCEu.isClientThread()` 调用都走 `Class.forName` + `getMethod`（**每次分配一个 Method 对象**）+ `invoke` | 改为一次性 lazy holder，近乎零风险 |
| `PartAbilityMixin.java:28` | 在 `PartAbility.register` 的 HEAD 处**取消上游注册**，让 `gtceu:steam_input_hatch` 永不入表 | 属"改变上游行为"而非扩展；配合 `defaultRequire: 1`，上游一改就硬崩。建议加启动自检并在行为不符时给出可读降级路径 |
| `RecipeManagerAccessor.java` + `OreCrushingMigration` / `BoilerRoomFuelSync` | 运行时把 vanilla `RecipeManager` 的 `recipes` / `byName` 整体取出、复制、改写、写回 | 功能上很严谨（先校验 5 个前置条件再动刀，失败只记日志不删配方），但同时依赖 Mixin accessor、vanilla 内部字段结构与 GTCEu staging API，是**最容易随版本升级整体报废**的一处，建议在 CI 中加针对性的加载断言 |
| `MinecraftMixin.java` / `WorldListEntryMixin.java` | 改写窗口标题与存档列表条目 | 纯客户端装饰，风险可接受 |

---

## P2-7　补齐引擎行为测试

原始基线中，45 个 GameTest 里有 20 个结构成型测试，引擎核心保护仅限少量缓存/唤醒场景。本轮新增 `gametest/GSESteamEngineTests.java`，用实际成型控制器、总线和仓室建立以下行为基线：

| 测试组 | 覆盖与预期 |
| --- | --- |
| `processor/crusher/furnace/voidProducerStateBoundaries`（4 个） | 总供汽不足时零扣取、成功时精确扣取；处理机、粉碎机、熔炉和虚空机每仓均有 1,200 mB/t 上限；主动暂停和排气阻塞冻结，缺汽回退 1 tick，恢复续跑；仅运行 tick 推进 20/200 tick 排气周期，第 200 tick 对目标造成 12 点伤害；结构失效保留批次与待输出。 |
| `processor/crusher/furnacePendingAndSettlement`（3 个） | 4 并行的保证产物为 3×4，不发生并行平方；机会产物数量合法，堵塞重试保持同一结果；释放空间后一次投递，不重复产出。 |
| `voidPendingRecovery` | 四工位周期结算、工作强度产量倍率、输出满时不再次生产或耗汽、暂停时仍可投递已完成产物。 |
| `processor/crusherParallelCapacity`（2 个） | 输出槽剩余 6 个位置时每次产 3 个的配方降为 2 并行；不足一份则不开工；机会副产物也必须预留空间；模拟不写入库存。 |
| `furnaceParallelCapacity` | 使用真实圆石烧石头配方验证满输出拒绝且不扣输入、释放 6 个位置后以 6 并行启动并只扣取 6 份输入。 |
| `processorConsumesInputsOnlyAtBatchStart` | 输出预检失败不扣输入；9 个输入、每份耗 2 个时启动 4 并行并剩 1 个；缺汽恢复不重新扣取或重算并行。 |
| `processorFluidCapacityAndRecovery` | 流体剩余容量限制并行，预检不改储量；容量不足时完整保留待输出，释放空间后精确投递。 |
| `blastAirAndSteamAreAtomic` | 鼓风不足不耗汽且回退；蒸汽不足不耗风；同时满足后按锁定耗汽量和每并行 4 mB 空气恢复。 |

夹具在成型后于同一服务端 tick 内设置已锁定批次并调用真实引擎方法，使故障边界不依赖调度时机。反射仅集中在测试适配器中，不增加生产代码测试开关，也不注册全局测试配方或改动共享随机种子。采矿厂预览仅有一个供给仓，结构可以正常成型；吞吐聚合测试额外安装九个合法供给仓以提供 12,000 mB/t。排气目标按绝对世界坐标放置。

原有 `airIntakeAloneStartsAirBatch` 同步修复了夹具时序：控制器成型不代表进气室的缓存状态已在其独立 tick 中刷新，测试现在在原有超时范围内等待 `COLLECTING`，然后供汽并验证自然采集能够启动批次，仍不手动注入空气。

**基线后的设计收口：**

- 四类引擎结构失效现已统一回退至 `1 tick`；熔炉不再保留原进度。
- 粉碎机已纳入每实体供给仓 `1,200 mB/t` 上限。所有蒸汽多方块均只要求至少 `1` 个供给仓；供汽不足时结构仍然有效，由运行状态反映缺汽。
- 所有家族的普通机械方块最低数量及其派生接口总数上限已经取消；P0-1 的部件收集组件只复用仍有效的功能接口规则。
- 当前需求展示语义已经统一并加入四家族状态边界测试。

**覆盖边界：**本轮是重构前的行为基线，不是穷举验证。未覆盖真实区块/世界 NBT 往返、全部工作强度和形状组合、第三方 ME 缓冲异常、多种产物竞争同一剩余槽位，以及处理机从配方完成到流体产物生成的完整链路。机会产出测试检查数量上界与重试稳定性，不作随机分布检验。后续抽取对应组件时应针对其改动补充这些场景。

**验证结果（2026-09-11）：**JDK 17 下执行 `gradlew.bat --no-daemon compileJava runGameTestServer build`；设计收口后全部 64 个 GameTest 通过，构建成功。

---

## P2-8　reload 路径消除全量 Map 深拷贝

`machine/multiblock/BoilerRoomFuelSync.java:139` 的 `recipes()` 只为了读 `STEAM_BOILER_RECIPES` 一张表，却把 `RecipeManager` 里**所有类型的全部配方表**深拷贝成新的 HashMap（L145–L147）；同一段代码在 `migration/OreCrushingMigration.java` 里又抄了一份（属 P0-1 扫出的 82 组重复之一）。服务端与客户端各执行一次。

**建议**：只复制目标类型（约 5 行改动）；提取为共享工具方法（与 P0-1 同批处理）。

---

## P2-9　仓库与工具链卫生

| 项 | 现状 | 建议 |
| --- | --- | --- |
| `changelog.txt` | 1,061 行 / 74 KB，经 `git log` 确认是 **Forge MDK 自带的 Forge changelog**，非本项目变更记录；`.gitignore` 的 `forge*changelog.txt` 规则未覆盖 | 删除 |
| `tools/` | 两代工具并存：Python 版 11 个 + PowerShell 版 3 个，其中 `generate_empty_gametest_structure.ps1` 与 `gen_empty_gametest_structure.py` 功能重复 | 统一到一种语言 |
| 资产生成 | 生成物（贴图、`.nbt` 模板）全部提交进仓库，但**脚本没有接到任何 Gradle 任务**，目前靠人工保证一致 | 增加 `genAssets` 校验任务 |
| 文案一致性 | 当前 `en_us` 655 / `en_ud` 657 / `zh_cn` 655 键，`en_us ↔ zh_cn` **零差异（做得很好）**；但 `en_us.json` 由 `GSELang.java` 生成、`zh_cn.json` 手工维护，**没有任何东西阻止下一次提交打破这个对齐** | CI 中加入键集合一致性检查 |
| 文档一致性 | 27 份设计文档共 10,403 行，其中的结构尺寸与并行数与代码完全靠人工同步 | 对可机检的数字（并行上限、结构边长）做抽样断言 |

---

## 与既有设计文档的关系

本清单**不修改**任何已定案设计。相关条目与既有文档的关系：

- P0-1 是 `docs/design/steam-crushers.md:28`「共用行为的修复应优先落在基类」这一既有原则在**跨家族层面**的推广；当前四个引擎拷贝使该原则无法执行。
- P0-2 / P2-8 只改缓存与拷贝策略，不改任何配方语义。
- P1-3 不再改变已收口的结构规则，只把同一份结构数据的书写位置从 3 处收敛到 1 处。
- P1-4 不改任何 tooltip 文案内容。
- P2-6 涉及上游行为的 Mixin 若需要调整策略，须先回到 `docs/design/machines-and-hatches.md` 确认"禁用上游蒸汽输入仓"的既定口径不变。

## 建议执行顺序

| 顺序 | 项 | 理由 |
| --- | --- | --- |
| 1 | **P0-1 引擎基类抽取** | 下一步先抽取部件收集，按 5 个粒度分批推进，每步运行 64 个回归测试；蒸汽预算统一采用“至少一个供给仓、每实体仓 1,200 mB/t”的已定口径 |
| 2 | **P1-3 / P1-4 数据驱动化** | 引擎边界稳定后推进 |
| 3 | **P2-6 / P2-9 剩余项** | 可穿插处理，优先补 Jade 配置翻译完整性检查 |

P1-5、P0-2、P2-7 行为基线与 P2-8 已完成本轮实施，后续继续由门禁保护，不再列为从零开工的任务。

## 附录：度量脚本

本文件中的所有数字可用下列脚本复现（写入任意 `.cjs` 文件后 `node <file> src/main/java`）：

```js
// 跨文件「逐字节相同」的函数体扫描
const fs = require('fs'), path = require('path'), crypto = require('crypto');
function walk(d, out = []) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p, out); else if (e.name.endsWith('.java')) out.push(p);
  }
  return out;
}
const sigRe = /^\s{4}(?:@\w+\s+)*(?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?(?:abstract\s+)?[\w<>\[\],.\s?]+\s+(\w+)\s*\([^;]*\)\s*\{?\s*$/;
const methods = [];
for (const f of walk(process.argv[2])) {
  const lines = fs.readFileSync(f, 'utf8').split(/\r?\n/);
  for (let i = 0; i < lines.length; i++) {
    const m = sigRe.exec(lines[i]);
    if (!m) continue;
    let depth = 0, started = false, end = -1;
    for (let j = i; j < lines.length && end < 0; j++) {
      for (const ch of lines[j]) {
        if (ch === '{') { depth++; started = true; }
        else if (ch === '}') { depth--; if (started && depth === 0) { end = j; break; } }
      }
    }
    if (end < 0) continue;
    methods.push({
      file: path.relative(process.argv[2], f), name: m[1], lines: end - i + 1,
      body: lines.slice(i, end + 1).join('\n')
        .replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '')
        .replace(/\s+/g, ' ').trim(),
    });
  }
}
const byHash = new Map();
for (const m of methods) {
  const h = crypto.createHash('sha1').update(m.body).digest('hex');
  if (!byHash.has(h)) byHash.set(h, []);
  byHash.get(h).push(m);
}
let groups = 0, instances = 0, removable = 0;
for (const [, g] of byHash) {
  if (g.length < 2 || new Set(g.map(x => x.file)).size < 2) continue;
  groups++; instances += g.length; removable += g[0].lines * (g.length - 1);
}
console.log({ methods: methods.length, groups, instances, removableLines: removable });
```

四个引擎类的**近同构**度量（token 重合度 ≥0.60）为同族脚本，只需把上面的"哈希完全相同"判据换成 `intersection / min(|A|,|B|)` 的 token 集合重合度，并把扫描范围限定为：

```
machine/multiblock/processor/AbstractSteamProcessorMachine.java
machine/multiblock/crusher/AbstractSteamCrusherMachine.java
machine/multiblock/voidproducer/AbstractSteamVoidMachine.java
machine/multiblock/LargeHeatStorageSteamFurnaceMachine.java
```
