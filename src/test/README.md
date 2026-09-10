# 为什么这个目录是空的

本项目的全部回归测试是 **Forge GameTest**，源码位于
`src/main/java/com/hoshino/gregsteamexpansion/gametest/`，因为它们需要
真实的 Minecraft 服务端世界、GTCEu 的配方管理器与多方块结构成型流程。

`src/test/java` 与 `src/test/resources` 保留为空目录，仅用于让 Gradle 的
`test` 任务有合法的源集存在。本项目**不接入 JUnit**，也不打算接入：能用
GameTest 表达的东西不要在 JUnit 里重复一遍，两者会各自漂移。

运行方式见 `README.md` 的「测试与 CI」一节，或直接：

```powershell
.\gradlew.bat runGameTestServer
```
