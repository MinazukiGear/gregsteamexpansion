# Greg Steam Expansion

一个基于 Minecraft 1.20.1 的 Forge / GregTech CEu Modern 附属模组项目骨架。

## 固定的开发环境

| 组件 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.10 |
| Java | 17 |
| GregTech CEu Modern | 7.5.3 |
| EMI | 1.1.24+1.20.1 |
| Gradle | 8.8（项目 Wrapper） |

GTCEu 是客户端与服务端的必需前置。EMI 是客户端必需前置，因此专用服务端不需要安装 EMI。

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
- 默认许可：All Rights Reserved（发布前可在 `gradle.properties` 中修改）

## 已知上游兼容风险

GTCEu 7.5.3 内嵌的 LDLib 1.0.40.b 与 EMI 1.1.24 有一条尚未关闭的 Mixin 初始化竞态报告。项目仍严格使用所要求的版本；如果开发客户端出现 `MixinTargetAlreadyLoadedException` 或 `EmiPlugin was loaded too early`，请参考 [GregTechCEu/GregTech#2917](https://github.com/GregTechCEu/GregTech/issues/2917)。
