# beta.2 发布准备

[v0.1.0-beta.2 预发布](https://github.com/MinazukiGear/gregsteamexpansion/releases/tag/v0.1.0-beta.2)已发布。标签指向四平台门禁全部通过的提交 `297d7824f5376a88f31ef9970f989f4cd57a53a1`，公开附件下载后校验通过。本轮人工验收已由使用者确认完成，详细结果见[验收记录](beta2-progression-acceptance.md)。

## 候选附件

发布目录为 `dist/0.1.0-beta.2/`，已加入 Git 忽略；与世界、配置和验证日志分开。目录中提供：

- `gregsteamexpansion-0.1.0-beta.2.jar`：修正 CI 夹具后重建的发布候选，`1,615,055` 字节。
- `SHA256SUMS.txt`：对上述 JAR 的 SHA-256 校验。
- `release-notes.md`：可粘贴到 GitHub Release 的更新说明。
- `verification-summary.json`：本地验证与候选来源摘要；保留在发布准备目录，不作为玩家安装所需文件。
- `build-inputs.sha256`：首次完整本地门禁的 674 项输入哈希；最终提交输入及后续修正另存 `committed-inputs.json` 和验证摘要，同样保留在本地。

候选 JAR SHA-256：

```text
cc849f8f57bbad0825ad2bc0d9409e4b8150c03f5e85f63910252f8cabbb6263
```

附件命名沿用 beta.1 的 JAR 与 `SHA256SUMS.txt`。构建目录还有旧版本 JAR，上传时只选择发布目录中的本次候选，勿使用 `build/libs/*.jar` 通配符。

## 最终代码门禁

使用已有 Zulu JDK 17、Python 3.12.14 与 Pillow 12.3.0，在当前工作区执行：

```powershell
python tools/verify.py --offline
```

2026-10-10 首次完整本地门禁通过，退出码 `0`，末尾为 `All gates passed.`。日志保存在 `.tmp/beta2-release-full-gate.log`，结果与来源写入 `dist/0.1.0-beta.2/verification-summary.json`。以下表格保留当时结果；后续 CI 夹具修正、候选重建与最终四平台结果另列。

| 检查 | 最终结果 |
|---|---|
| Python 工具负向夹具 | 8/8 |
| 默认 GameTest | 191/191 |
| 独立 JVM 服务器重启 | 写入 191/191，恢复 191/191 |
| Easy、Normal、Expert 启动配置 | 每档 191/191 |
| 仅核心、AE2、完整可选依赖 | 每组加载检查 1/1 |
| 数据生成 | 成功，既有生成资源内容未变（忽略本机 `.cache`） |
| 构建与静态门禁 | 设计、Mixin 边界、语言、资源、造价和 Java 17 包检查通过 |

重建候选通过 JAR 静态检查：414 个 Java 17 类、427 项源码资源、322 项 UTF-8 JSON/元数据。构建检查还确认 929 个语言键、15 个 Jade UID 翻译、14 个资产脚本的 90 项产物，以及 4 档造价报告、每档 34 项获取材料。

验证前后的 674 项源码、构建配置和工具输入逐项一致。原开发配置、选项未改动；配置重启夹具恢复到验证前不存在配置文件的状态。原世界 46 个文件与备份 SHA-256 均未改变；人工验收使用的副本不计入此项原世界核对。

首次完整本地门禁重建包与原验收包的 954 个 ZIP 条目仅有 manifest 的 `Implementation-Timestamp` 差异，该次重建包 SHA-256 为：

```text
db9694e47b620ac7520bcd32b18eefae0a255a12372de4684e9adf6152d7b098
```

两次构建时间分别为 `2026-10-10T16:15:52+0800` 和 `2026-10-10T17:22:02+0800`。原验收包 SHA-256 为 `564fbb1aa00064dd9f7cd91266d3747c94880a96ba616111ebed8061db3b79be`，与校验文件、当时摘要一同保留在 `dist/0.1.0-beta.2/history/pre-ci-fixture-fix/`。

Windows ARM64 随后暴露自动化 GameTest 的结构检查竞争。修正后重新运行本机 191 项 GameTest 与构建，退出码 `0`，日志为 `.tmp/beta2-ci-pattern-fixture-fix.log`。当前发布候选与原验收包逐项比较，只有 `GSEAutomationInterfaceTests.class` 和构建时间戳不同，全部运行时代码及资源字节一致；配置与原世界也保持不变。当前 `SHA256SUMS.txt` 已对应重建候选 `cc849f…`，不再使用原验收包哈希。

## GitHub 状态

首次候选源码已提交并推送为 `5062726b68bebcf4dcbf58e957e68bf1d38297b4`，[首次四平台运行](https://github.com/MinazukiGear/gregsteamexpansion/actions/runs/38041574230)已触发。标签应指向最终通过四平台门禁的候选提交，不使用此前 beta.1 基础提交 `7316d3c50d8192423208d6de4beb834198043052`。最终 beta.2 标签与 Release 已创建，目标为 `297d7824f5376a88f31ef9970f989f4cd57a53a1`。

跟进时在本地复现了干净 Windows 检出的动画元数据误报：生成文件使用 CRLF，Git 检出使用 LF，旧的字节比较会认为资源过期。修正只让 `.mcmeta` 使用 UTF-8 文本换行规范化比较，动画参数仍须一致，NBT 等其他产物仍按字节比较。工具夹具由 8 项增至 9 项，标准 Git 检出的 90 项资产比对通过，该项不改变安装包。

`0c7548a3c56d0c27440fe40d431031c78dd1ab34` 的[第二次运行](https://github.com/MinazukiGear/gregsteamexpansion/actions/runs/38042027063)中，Windows ARM64 默认套件的 191 项里有 1 项小型离心机自动化检查失败。已核对 GTCEu 7.5.3 的异步检查与锁实现，将自动化夹具的替换、强制检查及成型序列放在同一控制器锁内，保留全部接口组合和断言。最终提交 `297d7824f5376a88f31ef9970f989f4cd57a53a1` 的[四平台运行](https://github.com/MinazukiGear/gregsteamexpansion/actions/runs/38043331790)全部通过。

## 发布顺序

1. 复核更新说明、候选附件及本地完整门禁结果。
2. 将本次候选源码提交并推送，运行当前四平台 CI，记录候选提交 SHA 与每项结果；失败时先处理并重新验证。
3. 在通过门禁的候选提交上创建 `v0.1.0-beta.2` 标签与 GitHub Release，勾选预发布，使用 `release-notes.md`，仅附本次 JAR 和 `SHA256SUMS.txt`。
4. 下载已上传附件核对哈希，记录发布 URL，再将 README 和版本说明中的“尚未发布”改为实际发布状态。

Windows x64 开发客户端的人工结果已确认；其他平台的实机客户端与大型整合环境继续单独验证。CI 服务端检查不扩展为客户端实测结论。

## 发布结果

发布于 `2026-10-10T10:35:48Z`（UTC），预发布标记为 true。

| CI 平台 | 完整门禁 |
|---|---|
| [linux-x64](https://github.com/MinazukiGear/gregsteamexpansion/actions/runs/38043331790/job/114187850079) | success |
| [windows-arm64](https://github.com/MinazukiGear/gregsteamexpansion/actions/runs/38043331790/job/114187850167) | success |
| [macos-arm64](https://github.com/MinazukiGear/gregsteamexpansion/actions/runs/38043331790/job/114187850220) | success |
| [windows-x64](https://github.com/MinazukiGear/gregsteamexpansion/actions/runs/38043331790/job/114187850224) | success |

公开附件：`gregsteamexpansion-0.1.0-beta.2.jar`（1,615,055 字节）与 `SHA256SUMS.txt`。草稿及公开发布两次下载核对均通过，JAR SHA-256 为 `cc849f8f57bbad0825ad2bc0d9409e4b8150c03f5e85f63910252f8cabbb6263`。原验收包继续保留于本地历史目录。发布记录提交仅修改文档；标签保持指向上述经 CI 验证的源码提交。
