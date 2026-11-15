# 构建与测试（ThePitGay-High）

本分支目标为 Paper/Purpur **1.21.11、Java 21**，使用 Gradle Wrapper **8.14.3**、Kotlin **2.1.20** 和 Paperweight 开发包。构建无需 1.8.8 服务端 JAR。首次构建需要联网，依赖缓存完整后才能使用 `--offline`。

## 构建

安装 JDK 21，在仓库根目录执行：

```powershell
.\gradlew.bat build --continue --console=plain
```

Linux/macOS 使用 `bash gradlew build --continue --console=plain`。该命令编译全部模块、执行自动化测试并生成 `core/build/libs/ThePitGay-High.jar`。仅打包可执行 `:core:shadowJar`，但不会运行测试。

如果 Windows 的企业证书或系统代理证书只安装在系统证书库，可在当前 PowerShell 会话设置：

```powershell
$env:JAVA_TOOL_OPTIONS='-Djavax.net.ssl.trustStoreType=Windows-ROOT'
```

## 服务端插件依赖

| 插件 | 本次编译和验证所用版本 | 说明 |
| --- | --- | --- |
| FancyNpcs | 2.9.2 | 内置菜单 NPC 的显示和交互 |
| FancyHolograms | 2.10.1 | 全息图、个人文字、机器人名牌 |
| ProtocolLib | 5.5.0-SNAPSHOT，Spigot 发行包 | 使用仓库 `vendor/dependencies/` 固定的官方构建，不能替换为同一开发版本的 API 26.3 Paper 发行包 |
| LuckPerms | API 5.4 | 需要安装适用于目标服务端的 LuckPerms |
| WorldEdit | Bukkit 7.3.19 | 完整活动地图功能需要；Modrinth 精确版本 ID `2YDdVDmG` |
| MythicMobs | API 5.12.1 | 使用相应功能时安装，尚未实服验证 |
| PlaceholderAPI / PlayerPoints | 可选 | 按现有功能配置安装目标版本 |

FancyNpcs 2.10+ 和 FancyHolograms 2.11+ 的已检查构建要求 Java 25，因此这里固定使用支持 Java 21 的上述版本。ProtocolLib 正式版 5.4.0 只明确列出支持到 1.21.8；固定开发构建的下载来源和校验值见 [依赖说明](../vendor/dependencies/README.md)。这些服务端插件不会打包进核心 JAR。

## 自动化测试

测试会初始化目标 Paper 的注册表和数据转换器，使用真实物品数据、容器和协议对象；玩家连接、调度和数据库等边界使用模拟对象。测试不会启动 Minecraft 服务端、接受 EULA 或连接生产数据库。

HTML 报告分别位于各模块的 `build/reports/tests/test/`，XML 位于 `build/test-results/test/`。GitHub Actions 工作流也会构建本分支并保存 JAR 和报告。

部分历史测试依赖外部扩展二进制，缺失时会明确显示为跳过。核心弓连击测试独立运行，保留可运行的内部回归覆盖。

| 外部测试样本 | 默认位置 | 覆盖参数 |
| --- | --- | --- |
| ThePitRepairExTend 4.0.0 | `test-fixtures/ThePitRepairExTend-4.0.0.jar` | `-PrepairExtendJar=/path/to/repair.jar` |
| ThePitRepairExTend 5.3.0 | `test-fixtures/ThePitRepairExTend-5.3.0.jar` | `-PnativeRepairJar=/path/to/repair.jar` |
| ThePitAddon Server 6.7 | `test-fixtures/ThePitAddon-MistralNetWork-Server-6.7-SNAPSHOT.jar` | `-PpitAddonJar=/path/to/addon.jar` |
| ThePitAddon Loader 6.7 | `test-fixtures/ThePitAddon-MistralNetWork-Loader-6.7-SNAPSHOT.jar` | `-PpitAddonLoaderJar=/path/to/loader.jar` |
| NPCView 1.0 | `test-fixtures/NPCView-1.0-SNAPSHOT.jar` | `-PnpcViewJar=/path/to/npcview.jar` |

这些名称代表原有测试样本。1.8 扩展中引用旧 Bukkit/NMS 的部分需要迁移源码并重新编译；提供旧 JAR 不代表能够在 1.21.11 上加载。迁移范围、数据格式和实服待验内容见 [迁移说明](HIGH-MIGRATION.md)。
