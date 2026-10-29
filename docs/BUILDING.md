# 构建与测试

项目使用 Gradle Wrapper 8.5、Java/Kotlin 17 编译工具链。建议使用 JDK 17 或 21 运行 Gradle，并安装 JDK 17 供工具链使用。首次构建需要访问依赖仓库；`--offline` 仅适用于依赖已缓存的环境。

MythicNPCLib 和 MythicFramework Core/Bukkit 已随源码内置，并由构建打包进核心。无需下载额外插件、准备 Downloads 路径或安装 Citizens。详见 [NPC 迁移说明](MYTHIC-NPC-MIGRATION.md)。

## 本地依赖

除仓库已有的 `libs/`、`packLib/` 依赖外，需要自行准备：

| 文件 | 默认位置 | 可覆盖参数 |
| --- | --- | --- |
| PandaSpigot 1.8.8（验证版本 221、226） | `libs/pandaspigot-221.jar` | `-PpandaSpigotJar=/path/to/pandaspigot.jar` |
| Spigot/CraftBukkit 1.8.8 R3 | `core/libs/spigot-1.8.8-R0.1-SNAPSHOT.jar` | 放入该目录；`core/libs/*.jar` 会加入编译类路径 |

本核心依赖 1.8.8 R3 的 NMS 与 PandaSpigot 扩展事件。服务器 JAR 是编译依赖，不会打包进核心，也不作为本次 Release 附件分发。

## 构建核心

Windows：

```powershell
.\gradlew.bat :core:shadowJar --no-daemon --console=plain
```

Linux/macOS 使用 `./gradlew`。输出为 `core/build/libs/ThePitGay-V.jar`。此命令仅构建核心，不执行测试。

## 完整回归测试

完整测试会加载扩展真实二进制，需要额外提供以下测试依赖；扩展不随测试源码分发：

| 扩展 | 默认路径 | 可覆盖参数 |
| --- | --- | --- |
| ThePitRepairExTend 4.0.0 | `test-fixtures/ThePitRepairExTend-4.0.0.jar` | `-PrepairExtendJar=/path/to/repair.jar` |
| ThePitRepairExTend 5.3.0（原生 Bot 类型检查） | `test-fixtures/ThePitRepairExTend-5.3.0.jar` | `-PnativeRepairJar=/path/to/repair-5.3.0.jar` |
| ThePitAddon 6.7 Server | `test-fixtures/ThePitAddon-MistralNetWork-Server-6.7-SNAPSHOT.jar` | `-PpitAddonJar=/path/to/addon.jar` |
| ThePitAddon 6.7 Loader | `test-fixtures/ThePitAddon-MistralNetWork-Loader-6.7-SNAPSHOT.jar` | `-PpitAddonLoaderJar=/path/to/loader.jar` |
| NPCView 1.0 | `test-fixtures/NPCView-1.0-SNAPSHOT.jar` | `-PnpcViewJar=/path/to/npcview.jar` |

```powershell
.\gradlew.bat :core:build --no-daemon --console=plain
```

相对路径均以仓库根目录解析，也可传入绝对路径。HTML 测试报告位于 `core/build/reports/tests/test/index.html`，XML 位于 `core/build/test-results/test/`。测试中的 Bukkit 调度、数据库等边界部分使用模拟对象，测试通过不能替代实服多人压测。
