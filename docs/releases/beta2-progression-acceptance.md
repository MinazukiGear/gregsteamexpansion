# beta.2 生存路线核验与客户端验收

日期：2026-10-10。Minecraft 1.20.1 / Forge 47.4.10 / GTCEu Modern 7.5.3。继续核验已有内容，不新增机器，不改配方、产汽或消耗数值。本页接续 [beta.2 开发记录](beta2-development-2026-10-09.md)，当前更新说明见[版本说明](v0.1.0-beta.2.md)。

本轮人工验收已完成，结果来源为使用者确认“已验收完成”。下表按当前验收清单的整体确认更新；自动检查与客户端日志证据另列，未提供的实际耗时、库存及吞吐测量值不补填。当前开发包尚未发布，远端 CI 与其他平台实机验证不在这次确认范围内。

## 本轮发现与修正

1. 粉碎机属于首批钢之后的升级。加载路线为 GSE 粉碎机 → 高压蒸汽研磨机和钻石研磨头；前者需要钢板，后者需要双层钢板。首批钢先用上游原始高炉，钢前粉料与矿物处理使用青铜加工链。
2. 原经济报告仅列三档的获取材料，遗漏默认关闭难度。本轮补齐关闭难度的 34 项记录，包含工具、条件配方和直接产量；关闭时的外壳产量单独按 GTCEu 7.5.3 默认 `2` 计算，启用 Normal 的默认值为 `1`，不能直接复用其结构造价。若服务器改过 `gtceu.yaml`，需按实际值另算。
3. 四台 HP 协同锅炉的产汽能在理想条件下覆盖一并行粉碎，但一台普通焦炉的供油不能支持一台 HP 持续燃烧。只数锅炉和供汽仓会漏掉原料瓶颈。
4. 完整开发客户端首次资源重载时，GuideME 20.1.7 的翻译开关早于客户端配置加载被读取，触发 Forge 开发环境的配置断言。新增可选客户端 Mixin，仅在开发环境且 `guideme.toml` 的配置尚未加载时返回该选项声明的默认值；加载后与正式环境均继续使用原始读取。未修改 Forge 全局校验或依赖版本，也未增加 GuideME 必需依赖。上游 [20.1.7 源码](https://github.com/AppliedEnergistics/GuideME/blob/v20.1.7/src/main/java/guideme/internal/GuideReloadListener.java) 与 [20.1.15 源码](https://github.com/AppliedEnergistics/GuideME/blob/v20.1.15/src/main/java/guideme/internal/GuideReloadListener.java) 均保留这处提前读取。
5. 首次进入副本后，客户端将“服务端已经迁移矿石配方、研磨机保留共享分类的碎矿/粉料路线”误报为迁移失败。现按已同步的粉碎配方重建两类查询表，不重复迁移或再次乘算产量；增强已有配方一致性测试，核对同步后数量、对象身份、源配方移除与查询表一致性。
6. EMI 结构预览复用了机器 ID，与大型焦炉的制作配方发生冲突。GSE EMI 插件为本模组结构预览使用 `gregsteamexpansion:/multiblock_info/...` 虚拟 ID，保留原预览内容与制作配方 ID；过滤规则同时覆盖已有和后续插件注册。

## 配方与持续供给

以下是当前版本的配方/理论下限，不是完整生存采购时间或实测长期吞吐。假设 20 TPS、满温、无批次间隔、无阻塞且不分流燃料；冷启动、粉料加工、水、管道、输入/输出和存储另计。

| 环节 | 基线 |
| --- | --- |
| 原始高炉首批钢 | 1 铁锭 + 2 木炭 → 1 钢锭；1,800 tick，约 90 秒；不耗蒸汽或电力 |
| 普通焦炉煤制焦炭 | 1 煤 → 1 焦炭 + 500 mB 杂酚油；900 tick，约 45 秒 |
| LP 混合锅炉杂酚油批次 | 250 mB / 350 tick |
| HP 混合锅炉杂酚油批次 | 250 mB / 175 tick |
| 单并行粉碎 | 固定 600 tick、200 mB/t；单批 120,000 mB 蒸汽 |
| 默认关闭难度、4 HP 协同 | 理想产汽 240 mB/t；耗油约 114.29 mB/s；普通焦炉供油下限为 11 台 |
| Normal、1 HP 协同 | 理想产汽 300 mB/t；耗油约 28.57 mB/s；普通焦炉供油下限为 3 台 |

供油计算为 `ceil(锅炉台数 × 250 / 175 ÷ (500 / 900))`。上述焦炉数量只覆盖杂酚油；焦炭若同时用于炼钢或制粉，需单独记录分流。库存可用于首次短批次试机，但验收连续运行时须记录开始与结束库存，避免将库存消耗记作自给。

默认关闭难度的粉碎控制器直接输入为 4 青铜板、2 青铜转子、1 高压蒸汽研磨机和 1 钻石研磨头。HP 混合锅炉直接输入为 4 双层钢板、2 钢四合一流体管、各 1 台上游高压固体/液体锅炉及扳手工具。以上还未包含结构、仓室、管网和上游机器的造价，完整材料表需在实际蓝图确定后记录。

## 自动验证

默认关闭难度的最终 `runGameTestServer build --offline` 退出码 `0`，191 项必需测试与构建门禁通过。增强已有获取路线 GameTest，测试总数保持 191；原始高炉测试提供结构、铁和木炭，运行未经缩短的上游批次，它验证机器路线能工作，不验证采矿、工具制作或从零采购。

日志 `.tmp/beta2-progression-final.log` 已记录：

```text
difficultyEnabled=false
coalCokeTicks=900, creosoteOutputMb=500
boilerFuelMb=250, lpFuelTicks=350, hpFuelTicks=175
idealCokeOvensForFourHp=11
First steel running recipe=gtceu:primitive_blast_furnace/steel_from_charcoal_gem, durationTicks=1800
First steel completed using upstream iron + two charcoal, without steam or electricity.
All 191 required tests passed :)
BUILD SUCCESSFUL
```

8 项 Python 工具夹具通过。造价模型单独核对了关闭难度外壳每次 2 件、Normal 每次 1 件及四种模式各 34 项材料。构建检查保留 929 个语言键、15 项 Jade UID、90 个生成文件、427 项资源与 412 个包内 Java 17 类；经济报告新鲜度、设计契约和 JAR 静态可移植性检查通过。

Easy、Normal、Expert 三档独立启动回归各通过 191 项，均运行完整首批钢批次；采用 `verify_config_restart.py` 中的自定义配置样本，原验证配置已恢复。日志为 `.tmp/beta2-progression-profiles.log`，末行 `Configuration restart verification passed; original config restored.`。这组三档回归先于后续客户端修复；最终变更重跑默认关闭难度的 191 项及真实客户端，配方数值和档位参数未修改。

GuideME 兼容修复后，core / AE2 / full 三种独立服务器组合各通过 1 项运行检查，原始日志为 `.tmp/beta2-client-compat-dependencies.log`。可选客户端 Mixin 使用 `@Pseudo`，且注入允许内部方法不存在，避免未来 GuideME 内部 API 改名成为正式启动要求；实际固定版本的注入仍需由客户端启动验证。

配方同步与 EMI 修复后的最终 `runGameTestServer build --offline` 通过，日志为 `.tmp/beta2-client-sync-final.log`。191 项必需测试全部通过，增强的同步用例记录 `Retained 1760 server-migrated ore recipes after client sync.`；语言键、Jade UID、生成文件与资源检查数量不变，包内 Java 17 类增加至 414 个。设计契约、四种模式的经济报告和 JAR 静态可移植性检查通过。

本轮不修改配方生成源或语言数据，未重跑 `runData` 或双 JVM 世界续作；这些项目的 2026-10-09 完整门禁证据保留在原记录中。

本轮验收包已冻结为本地发布候选：[`gregsteamexpansion-0.1.0-beta.2.jar`](../../dist/0.1.0-beta.2/gregsteamexpansion-0.1.0-beta.2.jar)，`1,614,677` 字节，SHA-256：

```text
564fbb1aa00064dd9f7cd91266d3747c94880a96ba616111ebed8061db3b79be
```

版本仍为 `0.1.0-beta.2`，本轮 Java 变更包括获取路线/配方同步验收测试、GuideME 开发客户端兼容、客户端配方查询表与 EMI 预览 ID 修复，玩法与配方数值保持不变。2026-10-09 的原始门禁 JAR 已在 `build/verification/artifacts/2026-10-09/` 保留，哈希与旧记录一致；当前包尚未发布，后续门禁和附件核对见[发布准备记录](beta2-release-preparation.md)。

## 独立客户端环境

使用 JDK 17，在项目根目录运行：

```powershell
.\gradlew.bat -PgseAcceptanceClient=true runClient
```

存档、选项、配置和日志均在 `build/verification/client-acceptance/`；普通启动仍使用 `run/`。验收模式保留普通开发客户端的完整模组注册集合。首次创建新生存世界前确认 `config/gregsteamexpansion-common.toml` 中 `difficultyEnabled = false`，以及上游配方配置。不要将已反复修改过的验收目录直接认作默认配置。

已经核对 ForgeGradle 实际使用的 `RunConfig`：验收模式指向上述独立目录，普通模式指向 `run/`，两种配置均通过保护检查。ForgeGradle 在执行时才设置 `JavaExec.workingDir`，不能将执行前的任务默认目录作为运行目录判据。本轮将原 `options.txt` 复制到验收目录，使用中文、GUI 缩放 3 和窗口模式；原选项文件保留。

修复后首次启动使用全新配置；失败启动产生的独立配置已保存在同目录的 `config-before-compat-fix/`，便于核对。原 `run/config/` 未移动。

旧存档升级时，先备份原世界，将副本放入独立目录的 `saves/`，再打开副本。升级是否成功必须以实际加载、机器状态及重新保存恢复的结果为准，NBT/服务器重启自动测试不能替代这一步。

本轮已从 `run/saves/新的世界/` 准备旧世界副本：原世界 46 个文件，共 `12,587,223` 字节；复制前后逐文件 SHA-256 一致，ZIP 内文件与验收副本也逐项一致。原件没有被打开或升级。

- 备份：`build/verification/world-backups/2026-10-10-9bf6f8bb/world.zip`。
- 清单：同目录 `manifest.json`，保存全部文件哈希；ZIP SHA-256 为 `4aa8501b587e741ee7cd26333b7f73592732c84cfa3b7765a9f98075c250d650`。
- 验收副本：`build/verification/client-acceptance/saves/beta2-upgrade-20261010/`；在世界列表中按存档元数据确认副本。

这些本地验收文件不提交到 Git，位于 `build/`，执行 `gradlew clean` 前需另行保留。原世界继续留在 `run/saves/`。复制完整性与客户端载入有自动核对记录，机器状态及保存重开的人工验收由使用者确认完成。

只读扫描原世界的区块数据发现了锅炉房、大型粉碎机、高炉、蓄热熔炉、蒸汽仓和储罐等 GSE 标识，因此副本可用于检查这些已有内容；标识出现不证明对应机器已加载、成型或正常续作。

## 人工验收记录表

| 顺序 | 操作与检查 | 记录重点 | 当前状态 |
| --- | --- | --- | --- |
| 1 | 从新生存世界走完青铜加工、焦炉和首批钢 | 配方配置、实际工具/材料、第一块钢时间 | 使用者确认完成 |
| 2 | 制作 HP 锅炉与粉碎机，先运行一并行 | 完整蓝图材料、预热时间、单批耗汽、缺料原因 | 使用者确认完成 |
| 3 | 连续生产，核对供油、粉料、水与管网 | 运行时长、产物、燃料与蒸汽起止库存、停机时间 | 使用者确认完成 |
| 4 | 两名真实玩家同时施工并离线/恢复/取消 | 双方进度、退款数量、满背包领取、中英文与 GUI 缩放 | 使用者确认完成 |
| 5 | 备份旧世界副本升级、保存、重开 | 蒸汽仓迁移、旧批次、外挂归属、旧终端工程 | 客户端载入有日志；人工验收由使用者确认完成 |
| 6 | 高炉、锅炉房、焦炉已实现外挂组合 | 维护、资源竞争、跨区块恢复、状态提示 | 使用者确认完成 |

本轮客户端界面工具初始化两次均遇到自动审批超时，未获得可操作的窗口，未执行客户端输入。该结果不是安全拒绝。通过 Minecraft 内置 Quick Play 参数启动旧世界副本时，首次启动在资源重载阶段触发上述 GuideME 异常；原始日志为 `.tmp/beta2-acceptance-client-initial.log`，崩溃报告在验收目录内。

GuideME 修复后的全新配置启动越过资源重载，2026-10-10 16:08:41 启动集成服务器，16:08:46 确认玩家 `Dev` 登录旧世界副本；日志为 `.tmp/beta2-acceptance-client-fixed.log`。随后为修正配方同步与 EMI ID 问题终止了本次开发启动，并确认副本世界锁已释放。这次终止不计作人工保存退出。原世界 46 文件、原配置与选项再次逐项校验一致；记录为 `.tmp/beta2-original-state-final.json`。副本运行后可能变化，初始 ZIP 与原世界仍保留。

最终客户端复验日志为 `.tmp/beta2-acceptance-client-final.log`：16:20:41 集成服务器启动，16:20:46 玩家登录副本，16:20:48 客户端保留 1,760 条服务端迁移的粉碎配方，16:21:03 EMI 重载完成。GuideME 崩溃、矿石迁移误报与 GSE EMI 配方 ID 冲突均未再出现。原世界、原配置与选项以及 ZIP 备份再次校验一致，最终包哈希也已写入 `.tmp/beta2-original-state-final.json`。

完整开发依赖仍有第三方日志诊断，包括 Modern UI 字体资源路径、未安装的 `ae2wtlib` / `advanced_ae` 配方类型，以及 GTCEu/EMI 的虚拟配方和未翻译标签提示；本轮未修改这些模组。客户端能载入不代表所有日志诊断已消除。

使用者随后确认本轮人工验收完成。最终客户端日志补录：16:24:27 保存玩家与世界，16:24:28 全部维度区块保存完成，16:24:34 客户端停止，Gradle 以 `BUILD SUCCESSFUL` 结束。该日志支持正常保存退出；界面、真人联机、机器状态及保存重开的人工结果以使用者确认为依据。
