# 完整门禁的超时、停滞检测与诊断

日期：2026-10-12；基于 `main` @ `48c1ed85ff40965021907706c8a4859168dc19ad` 的开发工作区。当前改动只涉及验证工具、CI 日志上传和文档。

## 触发问题与历史 CI 收口

终端计算成本优化和进气夹具修正后的[四平台 CI](https://github.com/MinazukiGear/gregsteamexpansion/actions/runs/38206731773)最终全部通过，各平台六轮 `193/193` 必需 GameTest、三种依赖冒烟和完整构建通过。这是提交 `48c1ed8` 的结果；性能对照和进气夹具来源见[计算成本记录](terminal-cost-performance-2026-10-12.md)。

其中 Windows ARM64 的首次尝试（job `114671754787`）完成普通回归后，在服务器重启 seed 阶段发生 `Worker-Main-2` / `ThreadedLevelLightEngine` 的 `MissingPaletteEntryException`。最后服务器输出为 `2026-10-12T05:18:53Z`，直到 `06:39:41Z` 因作业 90 分钟上限取消。相同提交仅重跑该平台后通过（job `114693732495`）。尚未确定异常由哪项夹具触发；本轮解决长时间失去进度时的检测、诊断和退出，未宣称修复该异常根因。

历史证据保留在 `.tmp/terminal-cost-final-ci-receipt.json` 和 `.tmp/terminal-cost-arm-timeout-receipt.json`。首次 ARM64 日志 SHA-256 为 `2ee43d67b8e87c16765bf683301cdc847f7006e92fa4cacfb99929265d1ff1a4`。旧“其余配置验证继续运行 / 修正提交待四平台验证”记录已按完成结果更新。

## 阶段控制与证据

`tools/verification_watchdog.py` 监控实际外部命令：工具夹具、编译、普通 GameTest、服务器重启 seed/verify、配置模板 bootstrap 与 Easy/Normal/Expert、core/AE2/full 依赖组合、datagen 和构建。单命令默认 1,200 秒上限、连续 600 秒无 stdout/stderr 字节即失败。无换行的输出也刷新活跃时间；持续输出不会绕过绝对上限。`GSE_VERIFY_TIMEOUT_SECONDS` 和 `GSE_VERIFY_IDLE_SECONDS` 可指定有限正数秒，非法值在启动前拒绝。

每阶段在 `build/verification/diagnostics/` 建立唯一证据目录。元数据记录阶段、命令、PID、预期档位/重启阶段、时间和失败原因；不转储全部环境变量。输出保留首 64 MiB 和末 64 KiB，失败时复制当前 GSE 夹具配置及 Minecraft 日志尾部。在清理前尝试对该阶段拥有的 Java 进程运行 `jcmd Thread.print -l`：单次最长 5 秒，总 attach 预算 20 秒；无法获取线程栈时记录原因并保留原失败。

Windows 使用挂起启动、私有 Job Object 和恢复线程，避免在归属建立前派生子进程；关闭 Job Object 清理其成员。POSIX 使用私有会话/进程组，只清理该组，主动脱离该组的进程不在覆盖范围。普通退出允许子进程最多 10 秒完成 Gradle 缓存关闭；仍存活的受管子进程按失败处理。实现依据 [Windows Job Objects](https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects)、[ResumeThread](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-resumethread) 和 [Python 3.10 subprocess](https://docs.python.org/3.10/library/subprocess.html)；工具保持 Python 3.10+ 语法兼容，不引入新依赖。

复核当前缓存的 Gradle 8.8 源码及 [native-platform 的 detach 实现](https://github.com/gradle/native-platform/blob/master/native-platform/src/main/cpp/posix.cpp)确认，单次 daemon 也会调用 `setsid()`。受监控的 POSIX Gradle 命令因此只在其子进程环境的 `JAVA_TOOL_OPTIONS` 末尾追加 `-Dorg.gradle.native=false`，保留原有选项、不修改调用者环境或项目配置。该 JVM 启动属性覆盖 launcher 和 daemon，使验证树保留在私有组；单独的 Gradle CLI `-D` 无法保证 daemon 启动时的 native 初始化关闭。Windows 和非 Gradle 命令不加入此属性。POSIX 原生运行仍待四平台 CI 核验。

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

本轮源码尚未提交或推送。本机 Windows x64 验证不代表新增监控实现已通过 Linux x64、Windows ARM64 或 macOS ARM64；新实现的四平台结果须以其实际提交对应的后续 CI 为准。

## 范围与后续

本轮不改动 Java 生产逻辑、测试模板、终端协议或刷新调度。每位玩家仍只渲染自己的选中预览，预览与材料数据按 UUID 隔离，每 10 tick 刷新、主动操作立即响应。开发存档、配置、客户端选项和公开 beta.2 文件按本轮起始清单复核，97 份文件哈希及文件集合均未变化；公开发布标签和附件保持原样。

真实 AE 网络材料统计与客户端全息 FPS 仍是后续性能测量范围；服务端模拟计时不能代替这些数据。新机器设计继续冻结，直到使用者再次主动提出。
