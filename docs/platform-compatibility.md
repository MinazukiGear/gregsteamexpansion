# 平台兼容性与验证边界

本文保留 beta.1 的平台验证记录；beta.2 更新内容见[版本说明](releases/v0.1.0-beta.2.md)，最终本地门禁与候选附件见[发布准备记录](releases/beta2-release-preparation.md)。

本轮目标为 Minecraft **1.20.1**、Forge **47.4.10**、GTCEu Modern **7.5.3**、GSE
**0.1.0-beta.1**。Gradle Wrapper **8.8**，ForgeGradle 固定为本机原先实际解析的
**6.0.54**；编译、Gradle 和游戏使用 **64 位 JDK 17**。没有升级游戏或加载器，未发布或推送。

## 模组与上游的边界

GSE 是 Java 模组，同一份 JAR 用于目标系统和架构。自身没有 JNI、系统进程调用或平台专用
二进制。门禁检查 Java 17 字节码、资源大小写、UTF-8、模型/贴图引用及 JAR 完整性，不能
证明 Minecraft、Forge、GTCEu 内嵌 LDLib、启动器和可选模组在全部平台都能运行。

本机 Mojang 1.20.1 元数据中的七个 LWJGL **3.3.1** 模块（core、GLFW、jemalloc、OpenAL、
OpenGL、stb、tinyfd）均列有 `natives-windows`、`natives-windows-arm64`、
`natives-linux`、`natives-macos-arm64`。LWJGL [上游说明](https://raw.githubusercontent.com/LWJGL/lwjgl3/3.3.1/doc/notes/3.3.0.md)
也确认了 Windows/macOS ARM 原生包。包存在仍需核对启动器实际选取、图形驱动、JNA、
语音库及其他模组原生依赖，不能据此声称整套客户端通过 ARM 验收。

本机进一步检查：准备的客户端类路径包含全部七个 Windows ARM64 模块，缓存中每个 JAR
包含一份 ARM64 DLL，PE machine 均为 `0xAA64`。GTCEu 提取出的 LDLib、Registrate、
MixinExtras 编译库未含原生二进制。这仍属于缓存与依赖静态检查，不是 ARM 进程实测。

Windows arm64 优先验证原生 arm64 JDK 17。若上游组件确实阻塞原生运行，可另行验证
**Windows 11 ARM64 经 x64 仿真**运行 x64 Java/启动器；本轮未实测该回退。
Windows 10 ARM 的 x86 仿真不能直接替代此方案。普通 ARM64 JVM 不能加载 x64 JNI DLL，
JVM 与 JNI 必须匹配，参见 [Microsoft 仿真说明](https://learn.microsoft.com/en-ca/windows/arm/apps-on-arm-x86-emulation)
及 [ARM64EC 说明](https://learn.microsoft.com/en-us/windows/arm/arm64ec)。macOS 使用原生 arm64
JDK 17 和原生库；Rosetta 运行单独记录，不能冒充原生 ARM 实测。

终极终端额外需要 AE2 与 GTM Things。本机开发固定 AE2 **15.4.10**、GuideME **20.1.7**、
GTM Things **1.6.0**。EMI、Modern UI、精妙系列等开发辅助模组不打包进 GSE，其启动或
原生依赖问题需分别归因。独立依赖验证覆盖 core / AE2 / full 三种服务器组合。

2026-10-10 的首次开发客户端验收发现 GuideME 在配置加载前读取翻译开关。GSE 的可选
客户端 Mixin 对这一处开发环境早期读取使用声明的默认值，配置加载后恢复原始读取；
正式环境沿用上游行为。详见 [本轮客户端验收记录](releases/beta2-progression-acceptance.md)。

## 开发环境与命令

[Forge 1.20.1 官方要求](https://docs.minecraftforge.net/en/1.20.1/gettingstarted/)为 JDK 17 与 64 位 JVM。
[Gradle 官方兼容表](https://docs.gradle.org/current/userguide/compatibility.html)将 Java 25 的运行支持列在
Gradle 9.1 起。虽然本机 Java 25 的原有缓存基线构建成功，后续验证固定 JDK 17；toolchain
只保证编译器，不能替换启动 Gradle 的 JVM。

Windows PowerShell，使用已有 JDK 与 Python（安装依赖建议在虚拟环境中执行）：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-17' # 按本机实际路径调整
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
python -m pip install -r tools/requirements.txt
python tools/verify.py
# Gradle 依赖已缓存时：python tools/verify.py --offline
```

Linux/macOS，使用对应架构的现有 JDK 17 和 Python 3.10+：

```bash
export JAVA_HOME=/path/to/jdk17
export PATH="$JAVA_HOME/bin:$PATH"
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r tools/requirements.txt
python tools/verify.py
# 单独构建：sh gradlew --no-daemon build
```

完整入口不依赖 Bash，保留的 `bash tools/verify.sh` 调用同一入口。Unix 验证脚本通过 `sh`
执行 Wrapper，源码 ZIP 无可执行位也能使用；重启和依赖脚本沿用当前 Python。Gradle 独立调用
会寻找合适的 Python，或接受 `GSE_ASSET_PYTHON` / `-PassetPythonExecutable`，路径作为独立
参数传递。门禁不会自动安装 JDK、系统库或更改安全设置；`--offline` 只控制 Gradle 依赖解析。

## Linux 发行版与 NixOS

Debian/Ubuntu、RedHat/RHEL/Fedora、Arch 使用同一 JAR 和上述命令。服务器需要 JDK 17
以及可写实例/临时目录；客户端还需要上游 LWJGL/JNA 的 glibc、图形、窗口和声音依赖。
包名与可用 JDK 随发行版变化，由目标机环境提供，不将发行版检测写入模组。

NixOS 不采用传统 FHS 库布局。提供 **尚未在 NixOS 执行** 的 `shell.nix`，供已有 Nix 环境使用：

```bash
nix-shell
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r tools/requirements.txt
python tools/verify.py
# 客户端：sh gradlew runClient，须另验图形/声音
```

shell 提供 JDK 17、Python、Git、Bash 及原生库的搜索路径，包含 `/run/opengl-driver/lib`。
GPU 驱动由宿主提供；Pillow 在虚拟环境中固定为 **12.3.0**。使用用户 `<nixpkgs>`，没有锁定
系统包，不承诺完整环境可复现。正式实例可参考 [NixOS Prism Launcher 文档](https://wiki.nixos.org/wiki/Prism_Launcher)
选择 JDK 17。必须核对实际 JVM 与包装环境的 JNI 查找路径；启用 `nix-ld` 不会自动向
Nix 打包的解释器提供 JNI 库，参见 [nix-ld 上游说明](https://github.com/nix-community/nix-ld#my-pythonnodejsrubyinterpreter-libraries-do-not-find-the-libraries-configured-by-nix-ld)。
本轮没有安装系统软件、启用 `nix-ld` 或修改宿主图形设置。

## 本轮证据与平台状态（2026-09-30）

初始工作区干净，未找到仓库/祖先 `AGENTS.md` 或 `.agents/skills`。读取工程说明与本地项目
摘要，原“分析项目发展方向”线程的最近任务已完成；初查无同工程 Java/Gradle 构建进程。
没有终止未知进程或改动本地记忆。

| 平台 | 本轮状态 | 待实际验证 |
| --- | --- | --- |
| Windows x64 | Windows 11 AMD64 实际构建、静态门禁和开发 GameTest 专用服务器回归 | 交付 JAR 的正式客户端/专服、图形/声音/输入与联机 |
| Windows arm64 | 静态审查与原生 ARM CI 配置；未在目标机运行 | 原生 JDK/启动器、服务器、客户端和可选模组；仿真另记 |
| Linux x64 | 静态审查与 Ubuntu CI 配置；本轮未在目标机运行 | Debian、RedHat、Arch、NixOS 实际构建、服务器与客户端 |
| macOS arm64 | 静态审查与原生 ARM CI 配置；未在目标机运行 | 原生 Java/库、服务器与客户端 |

CI 配置采用 `ubuntu-24.04`、`windows-2025`、`windows-11-arm`、`macos-15`，原生架构的
Zulu JDK 17 与 Python 3.14，缓存和产物按 OS/架构区分，失败保存日志。标签来自
[GitHub 官方列表](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)。
本轮没有触发远端 CI；配置不算实测，CI 服务端检查也不能替代客户端和发行版验证。

基线命令 `gradlew.bat --offline --no-daemon build --console=plain` 在 Java 25 下 46 秒成功，
`compileJava` 为 UP-TO-DATE。修改后设置现有 Zulu **17.0.13**，执行
`gradlew.bat --offline --no-daemon --console=plain compileJava build`，实际重新编译，
1 分 17 秒成功。Python **3.14.7**、Pillow **12.3.0**；90 个生成资产新鲜，923 个语言键一致，
15 个 Jade UID 有翻译。静态审查包含 427 个资源、322 个 JSON/元数据文件、410 个类文件。
六个 Python 故障夹具通过（平台分支模拟不算平台实测）。进一步排除实际发现的 datagen
`.cache` 打包内容；未发现需新增的配方、模型、贴图或翻译。

完整命令为 `python tools/verify.py --offline`，本机日志保存到
`build/verification/portability/full-gate.log`，**退出码 0，All gates passed**：187 项主套件、
双 JVM 写入/恢复各 187 项、Easy/Normal/Expert 各 187 项、三种依赖组合各 1 项均通过；
配置恢复成功。`runData` 1 分 1 秒通过，生成资源没有 git 改动；最终 `build` 48 秒通过。
JAR 为 `build/libs/gregsteamexpansion-0.1.0-beta.1.jar`，**1,594,381 字节**；SHA-256：

```text
8ad5ccbf212c7c9c3e1135f54554288bb04592006533ef09ee9fd5df4a31af86
```

本地开发包与既有发布包版本字符串相同但内容改变，联机双方应安装本次同一 JAR。
manifest 保留原有构建时间戳，不承诺不同构建哈希相同。

附加 Windows x64 实测：在 `build/verification/portability/path with 中文-0ecb2aa2` 的独立
源码副本，用 `build/verification/portability/python with 中文/Scripts/python.exe` 通过
`verification_common.gradle_command('compileJava', 'build', offline=True)` 调用 Wrapper，设置
`JAVA_TOOL_OPTIONS=-Duser.language=tr -Duser.country=TR -Dfile.encoding=UTF-8`。1 分 34 秒通过，
19 个任务实际执行；日志为 `build/verification/portability/unicode-locale-build.log`。
与正式交付 JAR 比较，仅 manifest 构建时间戳不同，全部类、refmap 和游戏资源逐字节一致。
此检查验证构建与静态资源，不等于终端 GUI 的土耳其区域游戏内验收。

该隔离构建的 ForgeGradle 配置期 `DownloadMCMeta` 探测出现一次
`Failed to retrieve asset index ID / SSLHandshakeException`；它随后使用现有依赖完成构建，
没有导致任务失败。保留本机既有代理/证书相关配置，没有关闭更多 TLS 校验。
首次联网开发客户端仍需核对 Mojang 元数据/资产访问；`--offline` 不保证上游插件完全不探测网络。

## 最小游戏内验收

在各目标系统/架构使用独立测试实例和备份存档，记录 OS、CPU、Java 架构、前置版本、
JAR SHA-256 与 `logs/latest.log`。自动门禁使用 `build/verification`，不复用开发 `run/` 存档。

1. 本次 JAR 加入 Minecraft 1.20.1 / Forge 47.4.10 / GTCEu 7.5.3 实例，启动主菜单并新建
   Normal 测试世界。日志无资源缺失、Mixin/refmap 失败或原生架构错误。分别记录核心实例
   与 AE2 + GuideME + GTM Things 完整实例的结果。
2. 切换中英文与 GUI 缩放 2/3，检查蒸汽破碎机、合成站、锅炉及一台大型处理机的贴图、
   动画、GUI、Jade 标签和获取配方；实际供汽加工、取出产物，保存退出并恢复。
3. 完整实例中检查终极终端全部页签、模式和任务状态文字、全息预览与输入；再以土耳其
   JVM 区域 `-Duser.language=tr -Duser.country=TR` 启动，确认中英文翻译键正常。
4. 使用同一 JAR 连接 Linux 测试服务器。生存终端从背包/AE 取料施工，制造阻挡验证回滚
   和退款；退出重连、停服重启，检查游标、方块、托管材料与退款不复制或丢失。
5. 停服后复制测试存档 Windows → Linux → Windows，检查机器库存、外挂归属和终端队列。
   客户端另验声音、窗口/全屏、输入及动画；NixOS 专门核对 JNI 与 GPU 路径。

只有在对应目标机实际执行后才能标记通过，自动验证不能代替上述操作。
