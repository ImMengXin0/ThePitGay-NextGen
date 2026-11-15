# 构建与测试（ThePitGay-High）

本分支目标为 Paper/Purpur **1.21.11、Java 25**，使用 Gradle Wrapper **8.14.3**、Kotlin **2.1.20** 和 Paperweight 开发包。Gradle 和编译工具链使用 JDK 21，测试和服务端运行使用 JDK 25。构建无需 1.8.8 服务端 JAR。首次构建需要联网，依赖缓存完整后才能使用 `--offline`。

## 构建

安装 JDK 21 和 JDK 25，设置 `JAVA_HOME` 为 JDK 21，在仓库根目录执行：

```powershell
.\gradlew.bat build :core:paperLibrarySmoke --continue --console=plain
```

Linux/macOS 使用 `bash gradlew build :core:paperLibrarySmoke --continue --console=plain`。该命令编译全部模块、执行自动化测试和 Paper 原生依赖加载验证，生成 `core/build/libs/ThePitGay-High.jar`。仅打包可执行 `:core:shadowJar`，但不会运行测试。

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

FancyHolograms **2.10.1 的插件入口类已经要求 Java 25**，因此服务端必须使用 Java 25；不能只凭其 API 类的字节码版本判断运行要求。ProtocolLib 正式版 5.4.0 只明确列出支持到 1.21.8；固定开发构建的下载来源和校验值见 [依赖说明](../vendor/dependencies/README.md)。这些服务端插件不会打包进核心 JAR。

## Paper 运行库下载与缓存

构建会将解析后的 21 个运行库及其精确版本写入产物 `plugin.yml` 的原生 `libraries:` 字段。Kotlin、MongoDB/MongoJack、Jackson、Jedis、Nashorn 及其依赖由 Paper 在创建插件类加载器前下载到**服务端根目录的 `libraries/`**，之后使用缓存。核心 JAR 继续内嵌需要重定位的框架库。

首次启动需要可访问 Maven Central，下载失败会阻止插件加载。保留完整的 JAR 和 POM 缓存后可以复用；更换依赖版本会下载新文件。Paper 使用自身的仓库配置和传输校验，无需放置旧版本的 `libs` 文件夹或自定义下载器。

`:core:paperLibrarySmoke` 使用目标 Paper 的真实 `LibraryLoader`，在独立空缓存中下载并核对全部 21 个 JAR 的 SHA-256，验证薄 JAR、JSON 和脚本运行，然后切换为离线会话检查缓存复用及空缓存拒绝启动。结果位于 `core/build/paper-library-smoke/result.txt`。该任务首次需联网，故纯离线构建可只执行 `build --offline`。

## FancyNpcs 兼容回归

核心适配层兼容 FancyNpcs 缺少可选属性时的查询。用户使用的 `2.10.1-java21+2` 在 `update()` 内无条件查询 `custom_model`，属性未注册时会用空键访问原始 `ConcurrentHashMap`；本项目仅为自身 NPC 提供属性视图，将这类查询视为属性不存在，保留原有并发存储、普通属性及清理行为。显示失败时会回收部分状态，下一次刷新可重试。

默认 `build` 会执行人物、村民、蠹虫、属性修改清理和显示失败重试共 5 项回归。测试调用真实的目标版本 Fancy `update()`，实体、移动、元数据传输及玩家连接使用模拟边界，未启动服务端。可额外指定本地 FancyNpcs JAR 运行同组回归，任务会核对实际加载的 JAR 路径：

```powershell
.\gradlew.bat :core:fancyNpcCompatibilityTest '-PfancyNpcsTestJar=D:/path/FancyNpcs.jar'
```

该选项仅替换此验证任务的运行依赖，不修改生产依赖版本或打包 FancyNpcs。

## Pit 世界规则与金锭

配置在 `plugins/ThePitUltimate/worlds/*.yml` 的 `worldName` 列表中的世界会禁用原版生物生成与自然回血，包括非当前地图和之后加载的地图。世界规则在初始化、配置重载和切图时设置；事件监听补充拦截原版生物生成，以及玩家的饱食和和平回血。`CUSTOM` 来源的插件实体继续生成，供 NPC、宠物、技能和 MythicMobs 使用；药水、金苹果和核心自定义治疗保留原有逻辑。不会批量删除已有生物。

金锭每秒尝试生成两个，每个独立保留 20 秒。生成使用当前地图的 `worldName`，默认以 `loc.region.pitA/pitB` 的水平中心为中心，X/Z 各延伸 90 格。注意 `pitA/pitB` 定义的是出生安全区，落点会避开该区域。需要调整范围时，在对应地图 YAML 中设置：

```yaml
gold-drop:
  center-x: 1000.0
  center-z: -2000.0
  radius: 90
  min-y: 30
```

`center-x/center-z` 应一起设置；不填写时使用安全区中心。`radius` 是水平正方形的半边长；`min-y` 是允许的最低地面方块 Y，不填写时使用世界最低高度。落点始终低于 `arenaHighestY`，仅检查已加载区块，校验安全地面、空气空间和世界边界；不会为了生成金锭加载或生成新地形。坐标落在方块水平中心，半砖等地面的碰撞高度会参与计算。无可用落点时跳过并限频记录提示。

金锭保留每枚 3～5 金币的原有基础奖励；阻止带奖励的物品合并，避免元数据和独立寿命丢失。切换地图和关闭核心时清理本任务跟踪的金锭。修改地图配置后使用核心的配置重载命令，或完整重启服务端。

出生点使用 Paper `AsyncPlayerSpawnLocationEvent`。主线程在地图选择、配置重载和添加出生点时发布位置快照，登录线程只读取副本，不操作地图配置、世界或玩家连接。

## 运行日志排查

本次用户日志的逐项结论见 [2026-11-15 日志审查](HIGH-LOG-2026-11-15.md)。如果异常只有 `java.lang.NullPointerException` 而没有堆栈，可在 Java 启动参数中加入 `-XX:-OmitStackTraceInFastThrow` 后复现，例如 `java -XX:-OmitStackTraceInFastThrow -jar paper.jar --nogui`。参数需放在 `-jar` 前；保留服务端原有内存和其他启动参数。

核心同时监听 Paper 的任务异常事件，在异常缺少堆栈时补充任务编号及执行类，同一类每 30 秒最多补充一次。该诊断用于识别剩余错误，不会把异常标记为已修复。

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
