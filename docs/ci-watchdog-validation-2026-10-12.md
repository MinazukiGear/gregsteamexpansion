# 完整门禁的超时、停滞检测与诊断

日期：2026-10-12；后续 CI 记录更新至 2026-10-15。基于 `main` @ `48c1ed85ff40965021907706c8a4859168dc19ad` 开始验证工具、CI 日志上传和文档工作；原生 CI 验证中另修正一项既有 GameTest 的环境隔离，机器与终端逻辑不变。

## 触发问题与历史 CI 收口

终端计算成本优化和进气夹具修正后的[四平台 CI](https://github.com/MinazukiGear/gregsteamexpansion/actions/runs/38206731773)最终全部通过，各平台六轮 `193/193` 必需 GameTest、三种依赖冒烟和完整构建通过。这是提交 `48c1ed8` 的结果；性能对照和进气夹具来源见[计算成本记录](terminal-cost-performance-2026-10-12.md)。

其中 Windows ARM64 的首次尝试（job `114671754787`）完成普通回归后，在服务器重启 seed 阶段发生 `Worker-Main-2` / `ThreadedLevelLightEngine` 的 `MissingPaletteEntryException`。最后服务器输出为 `2026-10-12T05:18:53Z`，直到 `06:39:41Z` 因作业 90 分钟上限取消。相同提交仅重跑该平台后通过（job `114693732495`）。尚未确定异常由哪项夹具触发；本轮解决长时间失去进度时的检测、诊断和退出，未宣称修复该异常根因。

历史证据保留在 `.tmp/terminal-cost-final-ci-receipt.json` 和 `.tmp/terminal-cost-arm-timeout-receipt.json`。首次 ARM64 日志 SHA-256 为 `2ee43d67b8e87c16765bf683301cdc847f7006e92fa4cacfb99929265d1ff1a4`。旧“其余配置验证继续运行 / 修正提交待四平台验证”记录已按完成结果更新。

## 阶段控制与证据

`tools/verification_watchdog.py` 监控实际外部命令：工具夹具、编译、普通 GameTest、服务器重启 seed/verify、配置模板 bootstrap 与 Easy/Normal/Expert、core/AE2/full 依赖组合、datagen 和构建。单命令默认 1,200 秒上限、连续 600 秒无 stdout/stderr 字节即失败。无换行的输出也刷新活跃时间；持续输出不会绕过绝对上限。`GSE_VERIFY_TIMEOUT_SECONDS` 和 `GSE_VERIFY_IDLE_SECONDS` 可指定有限正数秒，非法值在启动前拒绝。

每阶段在 `build/verification/diagnostics/` 建立唯一证据目录。元数据记录阶段、命令、PID、预期档位/重启阶段、时间和失败原因；不转储全部环境变量。输出保留首 64 MiB 和末 64 KiB，失败时复制当前 GSE 夹具配置及 Minecraft 日志尾部。在清理前尝试对该阶段拥有的 Java 进程运行 `jcmd Thread.print -l`：单次最长 5 秒，总 attach 预算 20 秒；无法获取线程栈时记录原因并保留原失败。

Windows 使用挂起启动、私有 Job Object 和恢复线程，避免在归属建立前派生子进程；关闭 Job Object 清理其成员。POSIX 使用私有会话/进程组，只清理该组，主动脱离该组的进程不在覆盖范围。普通退出允许子进程最多 10 秒完成 Gradle 缓存关闭；仍存活的受管子进程按失败处理。实现依据 [Windows Job Objects](https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects)、[ResumeThread](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-resumethread) 和 [Python 3.10 subprocess](https://docs.python.org/3.10/library/subprocess.html)；工具保持 Python 3.10+ 语法兼容，不引入新依赖。

复核当前缓存的 Gradle 8.8 源码及 [native-platform 的 detach 实现](https://github.com/gradle/native-platform/blob/master/native-platform/src/main/cpp/posix.cpp)确认，单次 daemon 也会调用 `setsid()`。受监控的 POSIX Gradle 命令因此只在其子进程环境的 `JAVA_TOOL_OPTIONS` 末尾追加 `-Dorg.gradle.native=false`，保留原有选项、不修改调用者环境或项目配置。该 JVM 启动属性覆盖 launcher 和 daemon，使验证树保留在私有组；单独的 Gradle CLI `-D` 无法保证 daemon 启动时的 native 初始化关闭。Windows 和非 Gradle 命令不加入此属性。Linux x64 与 macOS ARM64 的原生完整门禁已通过，结果见下文最终候选记录。

服务器重启、配置重启和依赖组合的 Python 循环由完整门禁直接调用，受监控和清理的是每次实际 Gradle/JVM 命令。超时失败能够返回配置脚本的 `finally`，逐字节恢复原文件或原来的不存在状态，并阻止后续阶段。非零退出码继续作为失败，不自动重试或放宽 GameTest 断言。

CI 对失败或取消尝试上传 `diagnostics/**`、Minecraft 日志和崩溃报告。四平台矩阵与 90 分钟作业兜底保留。强制终止宿主进程或 runner 可能打断最终诊断、配置恢复或制品上传，取消场景不能保证取得完整证据。

## 本轮验证状态

受监控的真实 Gradle 离线编译通过（`.tmp/ci-watchdog-gradle-probe.log`），确认 Windows batch wrapper、私有 Job Object 和正常关闭宽限期可协同工作。

Windows 11 AMD64、Zulu JDK 17.0.13、Python 3.12.14 下，最终 15 项诊断夹具全部通过，无跳过（`.tmp/ci-watchdog-fixtures-final.log`）：

- 真正挂起的进程按无输出上限失败；持续输出且无换行的进程按绝对上限失败。
- 非零退出码 `7` 保留，失败配置和日志尾部完整收集；元数据未包含夹具注入的无关环境变量。
- 受管子进程和启动器退出后残留的普通子进程被清理，同时独立启动的无关进程继续存活。
- 原配置存在/不存在两种状态在真实超时后均恢复；seed 失败不启动 verify，core 失败不启动后续依赖组合，完整门禁遇到配置失败停止。
- 实际启动 Java 17 夹具，并在终止前用 `jcmd` 取得含 `gse-watchdog-fixture` 线程名的线程栈。
- 模拟 POSIX Gradle 平台选择，核对原 JVM 选项保留、追加属性和调用者环境不被修改；Windows 与非 Gradle 命令不改变其选项。该项不冒充 POSIX 原生执行。
- 输出上限与末尾保留、非法上限启动前拒绝、隔离证据路径边界通过；Windows 无权限创建符号链接时，该夹具只验证非链接越界拒绝。

完整离线门禁 `python tools/verify.py --offline` 全部通过（`.tmp/ci-watchdog-full-gate.log`）：

- 普通回归、独立 JVM seed/verify 和 Easy/Normal/Expert 六轮各 `193/193` 项必需 GameTest 通过。
- core、AE2、full 三种隔离依赖运行各 `1/1` 冒烟通过。
- 9 项可移植性工具夹具、datagen 新鲜度、92 份生成资产、929 个语言键、设计契约、获取路线、经济与完整构建审计通过。
- JAR 审计核对 429 个 Java 17 类和 429 个源资源。14 个外部阶段的元数据均为 `passed`，无输出采集错误或遗留子进程失败；最长阶段约 376 秒。
- 配置重启前后核对临时配置的不存在状态一致；15 项诊断夹具另行保护原文件存在时的逐字节恢复。

完整门禁启动时包含 13 项诊断夹具；后续补充的依赖超时报告和 POSIX 启动属性分支由最终 15 项独立夹具复核，Windows 正常 Gradle 调用行为相同。额外的真实 Gradle 编译探针确认 daemon 启动时 `org.gradle.native=false` 且使用 `org.gradle.internal.nativeintegration.jna.UnsupportedEnvironment`，编译成功（`.tmp/ci-watchdog-native-probe.log`）。该探针在本机 Windows 执行，不代替 POSIX 进程组清理的原生运行验证。

| 保留日志 | SHA-256 |
| --- | --- |
| `.tmp/ci-watchdog-full-gate.log` | `7bb0fd51119273ea5eefbde7475b925e39175b8af47e8700056184f04949a8d0` |
| `.tmp/ci-watchdog-fixtures-final.log` | `72849ef13e4ea62b1d3c66deef98628a73618134d4d91b261c428f04e1754cce` |

7 个变更 Python 文件通过 Python 3.10 语法解析，文档本地链接与差异格式检查通过。最终来源指纹、14 阶段元数据/日志哈希及保护文件复核汇总为 `.tmp/ci-watchdog-final-receipt.json`；日志和原始诊断位于忽略目录，执行 clean 前应保留这些证据。

以上为 2026-10-12 的本机结果；候选源码已于 2026-10-15 提交推送，四平台进展见下节。本机 Windows x64 验证不代替其他平台的原生运行。

## 2026-10-15 候选 CI 与 Java 夹具路径修正

提交 `f5dff08b79a3afb3780ad9edf216832396895dbd` 的[首次四平台 CI](https://github.com/MinazukiGear/gregsteamexpansion/actions/runs/38677627296)中，Windows x64 与 ARM64 均在新 Java 诊断夹具的编译步骤返回 `javac` 退出码 `2`，此前 9 项可移植性夹具通过，机器 GameTest 尚未启动。两平台均成功上传失败诊断。原夹具捕获的编译器输出未展示在异常中，因此首轮日志不能确定具体编译器报错文本。

失败命令把中文临时目录作为绝对参数交给 `javac`；[OpenJDK 17 Windows launcher](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/native/launcher/main.c)在启动时把 UTF-16 参数转换为系统 ANSI 代码页，存在本机中文环境与 CI 环境的路径兼容差异。夹具现保留真实的中文/空格工作目录，在该目录中以 ASCII 相对文件名、显式 UTF-8 源码编码编译，并用相对类路径 `.` 启动 Java；编译失败时展示编译器原始输出。实际 JVM 附加、线程名检查、进程清理、配置恢复和超时断言均保留。

修正后本机 15 项诊断夹具和 9 项可移植性夹具通过，日志分别为 `.tmp/ci-watchdog-fixtures-relative-java-2026-10-15.log`、`.tmp/ci-watchdog-portability-2026-10-15.log`。此项只改测试夹具的 Java 启动参数，生产 Java、资源和机器回归断言未改；该修正随最终候选完成下文的四平台 CI。

提交 `c7b3bb3dee64d14a9a03b3998eee7e21d05ed93e` 的[下一轮 CI](https://github.com/MinazukiGear/gregsteamexpansion/actions/runs/38678233425)在 macOS ARM64 完成 15 项工具夹具、编译及普通 `193/193` GameTest 后，清理步骤报告 `EPERM`；诊断元数据确认实际 Gradle 退出码为 `0`、失败原因为 `cleanup_error`。监控的正常退出分支已确认没有活跃组成员，但再次发送组信号时仍收到权限错误。记录位于 `.tmp/ci-watchdog-ci-macos-cleanup-artifact/`。

POSIX 清理现只在 `PermissionError` 后重新查询私有组：确认没有活跃成员才按已结束处理；存在活跃成员或查询失败仍报清理失败。清理本身失败时也补采诊断上下文。新增回归分别保护这三种情况，修正后的本机 16 项诊断夹具通过（`.tmp/ci-watchdog-fixtures-posix-cleanup-2026-10-15.log`）；真实超时、无关进程保护和 JVM 线程栈断言未放宽。该项没有修改生产 Java 或资源，最终候选的 macOS 原生完整门禁亦通过。

同一提交的 Windows x64 在重启 seed 阶段独立报告 `steam_air_intake_collects_while_unformed` 失败：状态为 `BLOCKED`、剩余周期为 `40`。该日志未记录入口方块，不能确定具体阻塞物或其来源（`.tmp/ci-watchdog-ci-38678233425-windows-x64-initial.log`）。原 `3×1×1` 夹具无法拥有入口周围全部环境；现使用已有 `32³` 空模板，把单个进气室与入口五面隔离移至自身区域中央，仅初始化时清空入口，继续由真实 40-tick 周期采气。100 tick 上限、精确批量、NBT 往返与 Jade 去重检查均保留，并补充失败时的朝向与入口方块信息。Java 改动仅在 `GSESteamHatchTests`，未修改机器采气逻辑或模板资产。

隔离修正的本机双 JVM 写入/恢复两轮各 `193/193` 通过（`.tmp/ci-watchdog-intake-fixture-restart-2026-10-15.log`）；完整构建、设计契约、92 份生成资产、929 个语言键及 JAR 的 429 个类/429 份源资源审计通过（`.tmp/ci-watchdog-intake-fixture-build-2026-10-15.log`）。

## 2026-10-15 最终候选四平台验收

最终源码 `75860e957d8d0a323c77a2b5aea7b66747651e0a` 的[完整 CI](https://github.com/MinazukiGear/gregsteamexpansion/actions/runs/38681423446)在首次运行中四平台全部通过，最终状态更新时间为 `2026-10-15T04:56:56Z`。四个结果来自同一源码和同一轮运行，无失败作业重跑；此前被新候选取代的取消结果不计入本表。各平台使用原生 Zulu Java `17.0.20.1`，Python 为 `3.14.7` 或 `3.14.8`。

各平台均核对完整日志，结果一致：

- 16 项诊断夹具和 9 项可移植性夹具通过，无跳过；真实 JDK 线程栈附加、超时清理及配置恢复检查均执行。
- 普通回归、独立 JVM seed/verify、Easy/Normal/Expert 六轮各 `193/193` 项必需 GameTest 通过。
- core、AE2、full 三轮依赖冒烟各 `1/1` 通过；datagen 新鲜度、92 份生成资产、929 个语言键及完整构建通过。
- 发布 JAR 审计通过，包含 429 个 Java 17 类和 429 份源资源；完整门禁输出 `All gates passed.`。

| 原生平台 | CI 作业 | 完整日志 SHA-256 |
| --- | --- | --- |
| Linux x64 | [116246974568](https://github.com/MinazukiGear/gregsteamexpansion/actions/runs/38681423446/job/116246974568) | `aaab5558f039eb7e5e4bf6a62bdd2a200cf137309a5706b7e3d68851d58dd6bf` |
| Windows x64 | [116246974791](https://github.com/MinazukiGear/gregsteamexpansion/actions/runs/38681423446/job/116246974791) | `655d1fd91490bbea737fb00d0b37ba638a063d4ab0b4e973b539778aa03164a1` |
| Windows ARM64 | [116246974698](https://github.com/MinazukiGear/gregsteamexpansion/actions/runs/38681423446/job/116246974698) | `c6e439f34633efb779ccfaebfc0ea3382cdad39a0d1dc1c579db4d9198496c80` |
| macOS ARM64 | [116246974710](https://github.com/MinazukiGear/gregsteamexpansion/actions/runs/38681423446/job/116246974710) | `cd3a922280edcd8cd65641e5b38858ffc24912c2510222b66b7b85d3f607c572` |

同一运行上传四份 `gregsteamexpansion-<平台>-jar` 制品。下表哈希取自各平台构建审计的 JAR 本身，区别于 GitHub 制品 ZIP 的摘要；公开 beta.2 仍对应原发布标签与附件。

| 平台 | CI 制品 ID | JAR SHA-256 |
| --- | --- | --- |
| Linux x64 | `11948019905` | `4842cc8628c4ac5b9eb11009fbed1700a51a0e4d516dac1c1ef63fd7489f0330` |
| Windows x64 | `11949806376` | `05b04a2046595c1a9a0068ec39411404101703b39aac47ca7154c46e10e51180` |
| Windows ARM64 | `11949171944` | `28f4bbc3d59477b7dcc48a0e58c2109e01d72656564a0f9b432d4ac69f01e072` |
| macOS ARM64 | `11949003307` | `7e5450df79f57b68565b22143ab133bfe08322094d9c4fd8dceb8ab4f472c146` |

日志保存为 `.tmp/ci-watchdog-ci-38681423446-<平台>.log`，来源、作业、日志与 JAR 指纹及制品元数据汇总在 `.tmp/ci-watchdog-ci-38681423446-receipt.json`。该凭据同时复核开发存档、配置、客户端选项和 `dist/0.1.0-beta.2/` 的 97 份文件：文件集合和逐文件 SHA-256 均与本轮起始清单一致；beta.2 标签仍指向 `297d7824f5376a88f31ef9970f989f4cd57a53a1`。

失败与取消证据也已单独核对：首次 Windows x64 的诊断制品 `11947400045` 保留了工具夹具的 `nonzero_exit` 元数据、退出码 `1` 和原始日志。中间提交 `eb9352f` 的[运行](https://github.com/MinazukiGear/gregsteamexpansion/actions/runs/38679609441)中，Windows x64 已通过，其余平台因最终候选推送而取消；Linux 取消制品 `11948855490` 保留阶段输出，但受宿主强制终止影响，活动阶段元数据仍为 `running`，未取得最终线程栈。这确认了前述强制取消的证据边界；受控超时的完整诊断由真实 JVM 夹具验证。

后续仅补写本节与验收索引，源码、工作流和测试夹具沿用已通过四平台的 `75860e9`。结果记录提交只执行文档链接、设计契约与差异格式校验，不重复运行同一份代码的完整门禁。

## 范围与后续

本轮机器与终端逻辑、协议和刷新调度不变；Java 改动仅用于隔离上述既有 GameTest 的入口环境。每位玩家仍只渲染自己的选中预览，预览与材料数据按 UUID 隔离，每 10 tick 刷新、主动操作立即响应。开发存档、配置、客户端选项和公开 beta.2 文件按本轮起始清单复核，97 份文件哈希及文件集合均未变化；公开发布标签和附件保持原样。

真实 AE 网络材料统计与客户端全息 FPS 仍是后续性能测量范围；服务端模拟计时不能代替这些数据。新机器设计继续冻结，直到使用者再次主动提出。
