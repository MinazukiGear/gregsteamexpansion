# 结构诊断（多方块未成型原因显示）设计文档

> 状态：**已定案（P0–P6 按 A 档建议全部锁定），A 档已实现**。本文档是本要素的唯一设计文件。
>
> 要素：**所有多方块机器在结构未成型时，必须向玩家说明"为什么没成型"**，而不是只显示一句"结构未成型"。
>
> 标注约定：✅ = 已锁定；💡 = 建议默认值（未拍板，实现可先用）；🚫 = 硬性约束；❓ = 待用户拍板。

---

## 一、依据（已核对源码）

### 1.1 GTCEu 7.5.3 上游事实

来源：`.tmp/gtceu-7.5.3-sources`

| 事实 | 位置 | 对本要素的意义 |
| --- | --- | --- |
| `MultiblockState` 持有 `public PatternError error`，另有 `hasError()` / `setError()` / `getPos()` | `api/pattern/MultiblockState.java` | 失败原因是**引擎已经算好的**，本要素不需要自己重新匹配结构 |
| `BlockPattern.checkPatternAt(...)` 匹配失败时 `state.setError(...)`，成功时 `setError(null)` | `api/pattern/BlockPattern.java` | 未成型时 `error` 恒为"最近一次失败原因"；成型后被清空 |
| `PatternError` 三实现见下 | `api/pattern/error/` | 错误分类与可提取字段的**唯一来源** |
| **上游从未在任何界面消费 `error` / `getErrorInfo()`** | 全源码检索 | 🚫 **本要素没有任何上游 UI 可复用，纯新增** |
| Jade `MultiblockStructureProvider` 只传布尔 `hasError`，渲染 `gtceu.top.invalid_structure`（"结构无效"） | `integration/jade/provider/MultiblockStructureProvider.java` | 现状：玩家只知道"没成型"，不知道原因 |
| 世界内预览 `MultiblockInWorldPreviewRenderer` 只渲染 pattern 幽灵方块，不读 `error` | `client/renderer/` | 世界里也不能靠上游看到原因 |
| `asyncCheckPattern` 条件 `(hasError() \|\| !isFormed) && (offset+periodID)%4==0`，**只在服务端 async 线程执行** | `MultiblockControllerMachine:155` | 🚫 **客户端 `MultiblockState` 从不被检查**，`error` 在客户端恒为初始态 ⇒ 客户端只能通过同步拿到原因 |
| `onPartUnload()` 会 `setError(UNLOAD_ERROR)` | `MultiblockControllerMachine:224` | "区块未加载"是一个独立错误类型，不是"缺方块" |
| `UNINIT_ERROR` 是构造时的直接赋值，**未经过 `setError()` ⇒ `worldState == null`** | `MultiblockState:36/50` | 🚫 对 `UNINIT_ERROR` 调 `getPos()` / `getCandidates()` 会 **NPE**，必须先按类型分派 |
| `isFormed` 是 `@DescSynced` | `MultiblockControllerMachine:63` | 客户端可自行判断"未成型"，无需额外同步这一步 |
| `SimplePredicate.getCandidates()` 每次调用**重建** `List<ItemStack>` | `api/pattern/predicates/SimplePredicate.java:172` | 有开销（tag 谓词可能几十上百项）⇒ 只能按需调用，不可逐 tick |

**`PatternError` 三个子类与可提取字段：**

| 类 | 语义 | `getPos()` | `getCandidates()` | `getErrorInfo()` |
| --- | --- | --- | --- | --- |
| `PatternError` | 位置不匹配（缺方块/方块错） | ✅ 失败方块坐标 | ✅ 期望方块候选（分组） | `gtceu.multiblock.pattern.error`（含候选名串 + 坐标） |
| `SinglePredicateError` | 数量类（max / min / maxLayer / minLayer） | ✅ | ✅ 该谓词候选 | `gtceu.multiblock.pattern.error.limited.<0..3>` |
| `PatternStringError` | 线圈不一致 / 过滤器不一致 / 区块未加载 / 未初始化 | ⚠️ 视来源而定（见 1.1 表） | ❌ 无 | 直接就是语言键 |

相关语言键（上游已有，可复用）：`gtceu.multiblock.pattern.error`、`.limited.0..3`、`.limited_exact`、`.limited_within`、`.coils`、`.filters`、`.batteries`。

> ⚠️ `UNLOAD_ERROR` / `UNINIT_ERROR` 的键是 **`multiblocked.pattern.error.chunk` / `.init`**（LDLib 命名空间），上游未提供中文，本模组需要自带兜底键。

### 1.2 本模组现状

- **20 台多方块（含锅炉房四档则为 23 台），全部直接或间接继承 GT 的 `MultiblockControllerMachine`**：

| 继承链 | 覆盖的机器 |
| --- | --- |
| `AbstractSteamProcessorMachine` | 洗矿厂 / 研磨厂 / 化学浸洗厂 / 离心机 / 大型离心机 / 搅拌机 / 锻压机 / 压缩机 / 提取机 / 大型组装机 / 电路组装机 / 热力离心机 等 12 台 |
| `AbstractSteamCrusherMachine` | 蒸汽粉碎机 / 大型蒸汽粉碎机 |
| `AbstractSteamVoidMachine` | 大型蒸汽矿脉处理厂 / 流体钻机 |
| `LargeHeatStorageSteamFurnaceMachine` | 大型蓄热蒸汽熔炉 |
| `BoilerRoomMachine` | 锅炉房 ×4 档（已实现） |
| `WorkableMultiblockMachine` | 大型焦炉 |
| `CokeOvenMachine` → `PrimitiveWorkableMachine` → `WorkableMultiblockMachine` | 普通焦炉 |

**⇒ 存在一个天然的统一注入点：`MultiblockControllerMachine`。不需要逐台改。**

- 展示层现状：8 个 Jade provider（每台机器一个私有枚举）+ 5 处自建 `createUI`。
- **0 处显示失败原因。** 现有 `getStatusId()` 家族口径里，未成型只映射到一个字符串 `"invalid_structure"`（`AbstractSteamProcessorMachine:1551`、`AbstractSteamCrusherMachine:907`、`LargeCokeOvenMachine:779`、`LargeHeatStorageSteamFurnaceMachine:1041`、`BoilerRoomMachine:490`）。

---

## 二、要素定义（三个可选层次）

| 层次 | 内容 | 说明 |
| --- | --- | --- |
| ★ 底线 | 玩家能区分"缺方块 / 数量不对 / 类型不一致 / 区块没加载"，并且**知道去哪找** | 没有坐标的原因几乎无用 |
| ★★ 完整 | 底线 + **期望方块候选**（缺的是哪种 casing / 仓室） | 候选来自 `getCandidates()`，需截断 |
| ★★★ 引导 | ★★ + 世界内定位（高亮 / 聊天坐标 / 一键传送建议） | 涉及渲染或指令，成本最高 |

🚫 硬约束（不随拍板改变）：
- 原因必须由 `MultiblockState.error` 派生，**不重新设计一套结构校验**。重复实现必然会与 pattern 演进脱节。
- 原因必须标注为"**首个问题**"：`checkPatternAt` 只记录第一个失败点，修好后会冒出下一个。UI 上不能暗示"这是全部问题"。

---

## 三、诊断信息分级（L1–L4）

抽取器输出一个 DTO（建议 `GSEStructureProblem`），字段按级别裁剪：

| 级别 | 字段 | 来源 | 备注 |
| --- | --- | --- | --- |
| L1 | `kind`：`MISSING_OR_WRONG` / `COUNT_LIMIT` / `INCONSISTENT` / `CHUNK_UNLOADED` / `UNINITIALIZED` | `error` 的运行时类型 + 常量比对 | 决定文案分支 |
| L2 | `pos`（绝对坐标）+ `distance` / 方向 | `PatternError.getPos()` | ⚠️ `UNINITIALIZED` 无坐标 |
| L3 | `expected`：`List<ItemStack>`（去重、截断，建议上限 4 + "等 N 种"） | `PatternError.getCandidates()` | ⚠️ 必须传 **ItemStack**，不能传字符串（见 R6） |
| L4 | `limitInfo`：`type`(0..3) + `number` | `SinglePredicateError` 的 public 字段 | 数量类错误专用 |

💡 建议默认：实现 **L1 + L2 + L3**，L4 作为 L1 文案的补充参数一并给出。

---

## 四、显示通道（T1–T5）

### T1. Jade 通用 provider　【推荐主通道】

- **做法**：新增一个 provider（UID `gregsteamexpansion.structure_diagnostics`），注册在 `MetaMachineBlockEntity` / `MetaMachineBlock` 上。`appendServerData` 判 `instanceof IMultiController && !isFormed()` → 抽 DTO 写 NBT；`appendTooltip` 渲染原因行。
- **覆盖**：一处改动，20 台全部生效（Jade 是类型级注册）。
- **取舍**：✅ 零 GUI 改动；✅ 无 mixin；✅ 服务端按需执行，正好绕开"客户端拿不到 `error`"的死结；✅ 不引入逐 tick 开销。❌ 依赖 Jade（本模组 Jade 已是硬依赖，不算成本）；❌ 与现有 8 个 provider 的 status 行有信息重叠，需要约定"新 provider 只说原因，不重复'结构未成型'"。
- **风险**：中低。主要是线程可见性（R1）。

### T2. 控制器 GUI 原因行　【推荐，但成本高】

- **做法**：在 5 处自建 `createUI` 的 status 行下方增加一行/一个 hover 项显示原因。
- **取舍**：✅ 不依赖 Jade；✅ 与现有 `getStatusId()` 家族口径一致。❌ **客户端读不到 `error`**，必须新增同步字段（见 W2）；❌ 要改 5 个 `createUI`；❌ 涉及跨线程写同步字段（R2）。
- **风险**：中高，是全部选项里工程复杂度最高的一项。

### T3. 右键控制器时聊天 / 动作栏提示

- **做法**：`IMultiController#onUse` 已占用「潜行 + 空手 → 世界预览」。未成型时右键控制器可在发提示后照常开 GUI（或复用世界预览的时机附带一条 actionbar）。
- **取舍**：✅ 服务端直接读 `error`，**零同步**；✅ 可给坐标，玩家可直接记下。❌ 与 GT 现有右键开 GUI 的行为有交互冲突风险（需要决定"提示 + 继续"还是"提示 + 拦截"）；❌ 只在交互瞬间可见，不常驻。
- **风险**：低（实现），中（交互语义）。

### T4. 细化 `getStatusId()`

- **做法**：把 `"invalid_structure"` 拆成 `structure_missing` / `structure_limited` / `structure_chunk_unloaded` 等，让现有 Jade / GUI 的 status 行本身带信息量。
- **取舍**：✅ 最贴合现有状态体系，改动最小。❌ 表达力弱（无坐标、无候选）；❌ 要同步改 5 处 `switch` + 语言键；❌ 优先表口径（结构未成型 > 输出堵塞 > …）需要重新核对。
- **风险**：低，但**收益也低**——单独做不足以支撑"显示原因"。

### T5. 世界内定位增强　【可选增强】

- **做法**：a) 聊天里给可点击坐标；b) 在 shift+空手的世界预览中把出错位置描边/变色；c) 提供 `/gse structure` 类调试指令打印完整诊断。
- **取舍**：✅ 对"大结构找不到哪块错了"的痛点最有效。❌ b 需要碰渲染（mixin 或自建 renderer），成本最高；❌ 上游 `MultiblockInWorldPreviewRenderer` 不暴露 error，扩展点不明确。
- **风险**：中高（渲染），低（指令）。

### T6. 开发者指令（可选）

- `/gse structure <pos>` 打印完整候选列表（不截断）+ 原始 `PatternError` 类型。
- 取舍：✅ 极低成本；✅ 对调试和 GameTest 断言都有用（可以作为测试的观测点）。❌ 玩家侧价值低。

---

## 五、接线方式（W1 / W2）

### W1. 纯工具类，无 mixin　【推荐】

- 新增 `GSEStructureDiagnostics.describe(IMultiController) → Optional<GSEStructureProblem>`。
- 依据：`IMultiController#getMultiblockState()` 与 `PatternError#getPos()` / `getCandidates()` **都是 public**（见 1.1/1.2 表），跨包直接可读。
- 适用范围：**T1、T3、T6** 全部可用。
- 取舍：✅ 零 mixin 风险；✅ 与既有 8 个 mixin 解耦；❌ 无法覆盖 T2（GUI 需要把数据推到客户端）。

### W2. Mixin `MultiblockControllerMachine` + `@DescSynced` 诊断快照

- 在 `MultiblockControllerMachine` 注入 `@DescSynced` 诊断字段 + getter，在 `onStructureFormed()` / `onStructureInvalid()` 与 async 检查后维护。
- 适用范围：T2（以及 T1/T3 的"客户端可读"版本）。
- 取舍：✅ 一次注入，20 台零改动拿到客户端可读的诊断；✅ 未来任何客户端 UI（Jade 客户端侧、世界预览、HUD）都能直接用。❌ 需要在 async 线程与主线程之间传递（R2）；❌ 动上游核心类，兼容风险。
- 💡 建议：**先做 W1；W2 仅在 T2 被拍板采用时才引入。**

---

## 六、风险清单

| 编号 | 风险 | 影响 | 缓解 |
| --- | --- | --- | --- |
| R1 | `error` 由服务端 async 线程写、主线程（Jade server data）读，无 `volatile` | 极少数情况读到上一轮的值（陈旧 ≤ 4 tick） | 可接受；若要求严格，在 W2 里做快照字段 |
| R2 | `@DescSynced` 字段**必须主线程写**，而结构检查在 async 线程 | 直接写会出同步异常/行为不定 | 检查结果先落到普通缓存，再由主线程回调转写；这是 T2 的主要工程成本 |
| R3 | `UNINIT_ERROR.worldState == null` | 直接 `getPos()` / `getCandidates()` → **NPE** | 先按 `PatternStringError` / 常量分派，再决定是否取坐标 |
| R4 | `error` 记录的是**第一个**失败点（且是最后一个尝试朝向的失败点） | 玩家可能误以为"只有一个问题"；定位可能不是最近的明显错误 | UI 文案明写"首个问题"；定位给绝对坐标让玩家自证 |
| R5 | 刚放置/刚拆改的机器，async 检查周期为 4 tick | 短暂没有原因可显示 | 该状态下文案退化为"正在校验结构…" |
| R6 | `getErrorInfo()` 在服务端构造，`ItemStack` 名会按**服务端语言**解析 | 中英混排 | 🚫 不要同步 `getErrorInfo()` 的字符串；只同步结构化的 ItemStack / 坐标，由客户端渲染 |
| R7 | `getCandidates()` 每次重建列表，tag 类谓词可能上百项 | 服务器开销 / 网络包过大 | 截断（默认 4 + "等 N 种"）、去重、按需调用（不逐 tick） |
| R8 | 语言键一致性 CI（`en_us` datagen 与手写 `zh_cn` 键集必须完全一致） | 新增键漏一边则门禁失败 | 新增键必须两侧同步，跑 `tools/verify.sh` |
| R9 | 与现有 8 个 provider / 5 处 status 行重叠，出现双份"结构未成型" | 玩家困惑 | 约定分工：既有 provider 保留状态行，新 provider 只说原因 |
| R10 | `multiblocked.pattern.error.chunk` / `.init` 无中文 | 中文玩家看到英文/键名 | 本模组自带兜底语言键 |
| R11 | 每台机器的 `createUI` 都是独立实现 | T2 改动分散，容易只改一半 | 若采纳 T2，先抽公共 helper 再逐台接 |

---

## 七、推荐组合（💡 待拍板）

| 档位 | 内容 | 覆盖通道 | 工程成本 |
| --- | --- | --- | --- |
| **A 档（建议默认）** | W1 工具类 + T1 Jade 原因行 + T6 调试指令 | Jade 全覆盖 + 调试 | 低（1 个新文件 + 1 个 provider + 语言键） |
| **B 档（A + 世界内引导）** | A + T3 右键提示（含可点坐标） | 不装 Jade 也能看 | 中 |
| **C 档（B + GUI 常驻）** | B + W2 mixin 同步字段 + T2 全部 `createUI` | 全通道 | 高（跨线程同步 + 5 处 UI） |
| **D 档（C + 视觉）** | C + T5 世界内出错位置高亮 | 全通道 + 视觉定位 | 最高（渲染） |

💡 建议从 **A 档**起步：它已经满足"★ 底线 + ★★ 完整"两档要素定义，且是唯一"一处改动覆盖 20 台"的方案。T3/T2 都可以在 A 之上单独追加，不返工。

---

## 八、实现清单（A 档，已落地）

| 文件 | 动作 | 说明 |
| --- | --- | --- |
| `structure/StructureProblem.java` | 新建 | DTO：L1 `kind` / L2 `pos` / L3 `expected` + `expectedTotal` / L4 `limitType` + `limitNumber`；含 NBT 序列化供 Jade 服务端数据通道使用 |
| `structure/StructureDiagnostics.java` | 新建 | 抽取器 `describe(IMultiController[, maxExpected])`：类型分派（先分流 `UNINIT_ERROR`）+ 候选展平去重截断 |
| `structure/StructureText.java` | 新建 | kind → 文案的共用装配，避免 Jade 与指令两处 switch 漂移 |
| `integration/jade/GSEJadePlugin.java` | 修改 | 新增 `StructureDiagnosticsProvider`，注册在 `MetaMachineBlockEntity` / `MetaMachineBlock` 上（一处覆盖全部多方块） |
| `command/GSECommands.java` | 新建 | `/gse structure`（权限 2）：准星 12 格内控制器，打印全量候选 + 引擎原始键 |
| `GregSteamExpansion.java` | 修改 | 注册 `GSECommands::onRegisterCommands` |
| `data/GSELang.java` | 修改 | en_us 键（datagen） |
| `resources/.../lang/zh_cn.json` | 修改 | 对应中文键，与 en_us 键集严格一致 |
| `gametest/GSEStructureDiagnosticsTests.java` | 新建 | 缺方块 → `MISSING_OR_WRONG`；仓室换成外壳 → `COUNT_LIMIT`（type 1） |

设计文档里原本的临时类名 `GSEStructureProblem` 落地为 `StructureProblem`（放在了 `structure` 包内，前缀冗余）。


---

## 九、验收清单（含验证状态）

**已在 `runGameTestServer` 中自动验证**（`GSEStructureDiagnosticsTests`，共 2 条）：

- [x] 缺方块 → `MISSING_OR_WRONG`，带坐标且候选非空。
- [x] 把蒸汽供给仓换成外壳（位置仍匹配）→ `COUNT_LIMIT`，`limitType == 1`（最少数量未满足），候选非空。
- [x] **破坏性验证**：把抽取器的候选展平改成返回空、并把数量类错误降级成位置类错误后，上述两条断言精确失败（其余 48 条不受影响）⇒ 测试确实在守行为。
- [x] `en_us` / `zh_cn` 键集一致（679 = 679）。
- [x] `./gradlew build` 通过；GameTest 总数 41 + 7 + 2 = 50，与 "All 50 required tests passed" 吻合。

**逻辑成立但未纳入自动化**（需要在真机或额外场景中确认）：

- [ ] Jade 面板的实际渲染效果 —— GameTest 验证的是抽取器输出与 DTO 序列化，不是 Jade 的 tooltip 实体。
- [ ] 候选超过 4 个时的截断与"等 N 种"文案 —— 蒸汽粉碎机的失败场景候选均 ≤ 4，未触达截断分支。
- [ ] 客户端按本地语言显示方块名 —— 依赖客户端解析 `ItemStack#getHoverName`，未目视验证。
- [ ] 结构成型后原因行消失 —— `describe()` 早退 + provider 前置判断，逻辑成立但无测试。
- [ ] 区块未加载 / 尚未初始化不崩溃 —— `UNINIT_ERROR` 的 NPE 分流已有代码，构造区块卸载场景成本高，未覆盖。
- [ ] 其余 19 台多方块 —— Jade 按方块类型注册、抽取器只依赖 `IMultiController`，逻辑上全覆盖；实测只做了蒸汽粉碎机。
- [ ] `/gse structure` 指令的实际输出 —— 未在 GameTest 中执行指令。
- [ ] 无新增逐 tick 开销 —— 诊断只在 Jade 的服务端数据请求时计算（玩家看向方块），没有 tick 钩子。

**提交提醒**：`runData` 已重写 `src/generated/.../lang/en_us.json`（及 `en_ud.json`），这两个文件需要随改动一起提交，否则 CI 的 datagen 新鲜度门禁会失败。


---

## 十、决策记录（已锁定）

用户 2026-09-11 拍板「都按建议来」，P0–P6 全部按建议值锁定，A 档已实现。

| 编号 | 议题 | 决策 |
| --- | --- | --- |
| P0 | 通道组合档位 | ✅ **A 档**：W1 工具类 + T1 Jade 原因行 + T6 `/gse structure` 调试指令 |
| P1 | 信息分级 | ✅ **L1 + L2 + L3**（类别 + 坐标 + 期望候选） |
| P2 | 候选截断阈值 | ✅ **4 个 + "等 N 种"**（`StructureProblem#MAX_EXPECTED`） |
| P3 | 是否引入 mixin（W2） | ✅ **否**（B/C 档才需要，本次不引入） |
| P4 | `/gse structure` 调试指令 | ✅ **是**（`GSECommands`） |
| P5 | 与既有 status 行的分工 | ✅ **新 provider 只说原因**，既有 8 个 provider 保留状态行 |
| P6 | 文案口径 | ✅ **明写"首个问题"**，并附"修好后可能还有下一个问题" |

B/C/D 档（右键提示 / GUI 常驻 / 世界内高亮）本次不做，可在 A 档之上单独追加，不返工。
