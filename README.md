# Greg Steam Expansion

一个基于 Minecraft 1.20.1 的 Forge / GregTech CEu Modern 附属模组。目前正在实现低压与高压混合燃料蒸汽锅炉。

## 固定的开发环境

| 组件 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.10 |
| Java | 17 |
| GregTech CEu Modern | 7.5.3 |
| EMI（测试客户端工具） | 1.1.24+1.20.1 |
| Gradle | 8.8（项目 Wrapper） |

GTCEu 是客户端与服务端的必需前置。EMI 不是本模组的前置依赖，仅由 Gradle 作为开发运行时工具加载到测试客户端，方便查看物品、方块和配方；发布版客户端和专用服务端都不要求安装 EMI，本项目 JAR 也不会打包 EMI。

开发客户端额外通过 Gradle 运行时依赖加载 Jade 11.13.3。混合燃料锅炉具有专用 Jade 信息提供器，可显示工作模式、运行状态、温度、实时蒸汽产量、粉料剩余时间以及水、液体燃料和蒸汽储量。Jade 仅用于开发环境，不是发布版模组的必需前置，也不会被打包进本项目 JAR。

开发客户端还以同样的运行时依赖方式加载精妙背包（Sophisticated Backpacks）、精妙存储（Sophisticated Storage）及其前置 Sophisticated Core 与 Puzzles Lib，仅用于在游戏内验证合成站读取容器与可放置背包的行为。此外加载 Modern UI，用于在全局界面美化的环境下检查模组界面表现。以上均不是本模组的前置，不会打包进 JAR，也不会写入 mods.toml。

## 开始开发

1. 用 IntelliJ IDEA 导入根目录的 `build.gradle`，并将 Gradle JVM / Project SDK 设置为 JDK 17。
2. 生成 IDEA 运行配置：

   ```powershell
   .\gradlew.bat genIntellijRuns
   ```

3. 启动开发客户端：

   ```powershell
   .\gradlew.bat runClient
   ```

4. 构建可发布 JAR：

   ```powershell
   .\gradlew.bat build
   ```

构建产物位于 `build/libs/`。如果系统默认 Java 不是 17，请先让 `JAVA_HOME` 指向 JDK 17；PowerShell 示例：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-17'
```

## 项目信息

- Mod ID：`gregsteamexpansion`
- Java 包：`com.hoshino.gregsteamexpansion`
- 入口类：`GregSteamExpansion`
- 当前版本：`0.1.0`
- 许可：代码与功能性资源 LGPL-3.0（`LICENSE.txt`）；`textures/` 图像素材 CC BY-NC-SA 4.0，禁止商用（`LICENSE-ASSETS.txt`）

## 设计文档

- [物品与方块总体设计](docs/design/items-and-blocks.md)：汇总本模组需要自行注册的物品与方块、当前范围、命名规则与新增条目准入条件。
- [机器与机器部件总体设计](docs/design/machines-and-hatches.md)：汇总单方块机器、多方块机器、蒸汽供给仓、蒸汽流体输入/输出仓、蒸汽进气室及其他机器接口的范围与设计边界。
- [杂项设计](docs/design/miscellaneous.md)：记录 EMI 显示增强等不属于单一内容规格的跨系统规则。
- [合成站设计](docs/design/crafting-station.md)：记录工作台上位替代“合成站”的完整规格（已定案），含双形态注册、外部容器读取、合成结算与两份配方。
- [矿石粉碎配方类型设计](docs/design/ore-crushing.md)：定义“矿石或粗矿 → 粉碎矿”的独立配方类型、GTCEu 研磨机配方迁移范围与兼容边界。
- [蒸汽粉碎机与大型蒸汽粉碎机设计](docs/design/steam-crushers.md)：逐步记录两级矿石粉碎多方块已经确认的结构、并行、耗时与蒸汽规则。
- [混合燃料锅炉设计规格](docs/design/mixed-fuel-boiler.md)：记录低压与高压混合燃料锅炉的定位、工作模式、燃料白名单、能力方向、各面行为、堆叠方式与已定合成配方。
- [大型蓄热蒸汽熔炉开发指导](docs/design/large-heat-storage-steam-furnace.md)：记录可变尺寸多方块熔炉、蒸汽排气仓、预热、供汽、配方处理与安全规则。
- [焦炉与大型焦炉设计](docs/design/coke-ovens.md)：记录 GTCEu 普通焦炉与焦炉仓修改，以及固定三炉室大型焦炉的结构、批处理、接口、安全和表现规则。

## 当前实现进度

低压与高压混合燃料锅炉已经完成机器注册和首版逻辑实现，包括液体燃料/协同燃烧两种模式、粉料消耗、温度和干烧行为、多面能力、蒸汽自动输出、GUI、声音与粒子反馈。协同燃烧模式不会产生灰烬或其他物品副产物。工作台配方和中英文语言资源也已接入数据生成。

灰烬机制移除前曾通过以下验证命令；按当前开发约定，本次修改后尚未重新运行：

```powershell
.\gradlew.bat runData
.\gradlew.bat runGameTestServer
.\gradlew.bat build
```

`runGameTestServer` 当前保留九项测试：验证低压/高压方块实例和两份工作台配方、模式切换与三个输入槽过滤、仅液体模式无粉料运行、协同燃烧缺粉停机与补粉恢复、六个方向的水/燃料/蒸汽/粉料能力、两台锅炉直接并排时同时运行、满蒸汽槽泄压，以及高温断水后重新进水爆炸。安全测试让高压锅炉按正常速率运行 1,240 tick 后再触发对应条件；测试液体燃料使用 GTCEu 蒸汽锅炉配方中的杂酚油。开发客户端也曾完成一次主菜单冒烟测试，模组、动态机器模型和纹理图集均成功加载，日志错误计数为 0。

设计文档中列出的正面覆盖纹理和 GUI 图标已经制作并接入。机器仅在实际燃烧的 `working` 状态显示点火与自发光纹理，暂停和等待状态保持熄火；GUI 模式按钮和主要故障状态也使用对应图标。

纹理由可复现的像素矩阵生成脚本维护。修改脚本后可用以下命令重新生成全部 16×16 PNG：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\generate_mixed_fuel_boiler_textures.ps1
```

下一步是在开发客户端世界内进行交互验证：检查两级方块外观与自发光、模式按钮及状态图标、真实管道/传送带组合，以及爆炸和泄压的声音与粒子反馈；之后开展数值平衡测试。现有 GameTest 覆盖模式切换、输入过滤、缺粉恢复、六面能力、并排堆叠、干烧爆炸和蒸汽泄压逻辑，但灰烬机制移除后的当前修订尚未重新运行这些测试。

## 已知上游兼容风险

测试客户端同时加载 GTCEu 7.5.3 内嵌的 LDLib 1.0.40.b 与 EMI 1.1.24 时，可能遇到一条尚未关闭的 Mixin 初始化竞态。如果开发客户端出现 `MixinTargetAlreadyLoadedException` 或 `EmiPlugin was loaded too early`，请参考 [GregTechCEu/GregTech#2917](https://github.com/GregTechCEu/GregTech/issues/2917)；该问题不影响未安装 EMI 的发布环境。
