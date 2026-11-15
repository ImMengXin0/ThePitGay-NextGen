# ThePitGay-High 迁移报告

本分支已完成面向 **Paper/Purpur 1.21.11、Java 25** 的代码迁移，全部模块构建成功。NPC 使用 **FancyNpcs 2.9.2**，全息图使用 **FancyHolograms 2.10.1**；后者的插件入口要求 Java 25。本次按用户要求完成代码和自动化测试，**未启动 Minecraft 服务端**；上线前仍需完成下述实服验收。

分支：`ThePitGay-High`，基于 `nextgen` 提交 `51a9055`。本报告记录 2026-11-15 的本地验证结果。

## 已完成的迁移

- 全部四个模块改用 JDK 21 编译工具链、Gradle 8.14.3、Kotlin 2.1.20 和 Paper 1.21.11 开发包；运行和自动化测试使用 Java 25。不再依赖 1.8.8 服务端 JAR。旧实现归档在 `core/src/legacy/java/`，不参与编译和打包。
- 新增 `paper-platform` 模块，以类型化 API 接入 FancyNpcs 和 FancyHolograms。内置菜单 NPC 已迁移，包括保留村民模型的仓库 NPC 和蠹虫模型的下水道 NPC；支持个人文字、可见范围、世界和区块检查、交互、重载及关闭清理。
- 全息图改用 FancyHolograms，支持公开或私人可见、多行、个人渲染、触摸、附件、临时寿命及父子行清理。渲染工作转至主线程，关闭时统一回收对象。
- 原生战斗机器人迁移为现代 `ServerPlayer`，保留真实战斗实体及其独立连接、可见性和名牌处理。实际移动、碰撞和战斗效果仍需实服确认。
- 内嵌 MythicFramework Bukkit 层、物品 API、材料、声音、药水、粒子、BossBar、聊天、标题及数据包调用已迁移到现代 API。WorldEdit 7 活动地图操作改为主线程有序队列，包含撤销、任务代次检查和关闭清理。
- 告示牌输入增加会话及位置检查、重复提交抑制、主线程回调、超时和退出清理；区块保护使用插件区块票；移除会删除世界内全部非玩家实体的旧 NPC 清理逻辑。
- Shadow 打包保留服务注册信息和需要重定位的框架库；Kotlin、MongoDB/MongoJack、Jackson、Jedis、Nashorn 等 21 个运行库使用产物 `plugin.yml` 的 Paper 原生 `libraries:` 下载机制，固定全部直接及传递依赖版本。首次启动联网下载到服务端根目录 `libraries/`，后续复用缓存。核心 JAR 不包含这些远程运行库、FancyNpcs、FancyHolograms、ProtocolLib 或 Minecraft 服务端类。
- 修复用户在 Leaf 1.21.11 上报告的名牌任务启动异常：使用 `BukkitRunnable.runTaskTimer` 注册任务，保留取消能力。MongoDB 客户端增加幂等关闭及初始化失败回收；停服时先停止调度、保存玩家数据并排空档案队列，再关闭数据库。其他资源清理失败会记录异常并继续，避免遗漏 MongoDB 监控线程。启动失败通过插件管理器禁用插件，并恢复原白名单状态。

## 物品与历史数据

现代自定义物品信息使用 `CUSTOM_DATA`，按复制、修改、提交的顺序写入。新背包写入采用带版本的 PITI v2 格式和 Paper 完整物品字节；读取保留 PITI v1、旧 Bukkit 对象流及旧 Base64 压缩 NBT 的转换路径，旧数字物品 ID 通过目标版本 DataFixer 转换。

自动化测试覆盖旧 1.8 金剑的 UUID、自定义附魔、寿命、名称、Lore、原版附魔、不可破坏标记和耐久保留，以及 v1 到 v2 的再保存。玩家背包接受历史 36、40、41 槽与目标版本 43 槽布局；目标布局包含护甲、副手、身体护甲和鞍。恢复顺序已修正，防止旧 36 槽物品覆盖已恢复的护甲。

读取会拒绝重复或越界槽位、错误区段、截断或超长负载及损坏 JSON，避免返回部分背包或把解析失败吞为 `null`。这些检查验证了代表性历史样本，完整历史玩家库仍需在独立数据库副本上验收。现代物品成功保存后，回退 1.8 核心需要恢复迁移前备份。

## 构建和自动化测试

验证命令：

```powershell
.\gradlew.bat build :core:paperLibrarySmoke --continue --console=plain
```

结果：**BUILD SUCCESSFUL**。各模块 XML 报告汇总如下：

| 模块 | 测试总数 | 通过 | 跳过 | 失败或错误 |
| --- | ---: | ---: | ---: | ---: |
| core | 491 | 386 | 105 | 0 |
| mythic-framework-core | 22 | 22 | 0 | 0 |
| mythic-framework-bukkit | 7 | 7 | 0 | 0 |
| paper-platform | 6 | 6 | 0 | 0 |
| **合计** | **526** | **421** | **105** | **0** |

105 项外部扩展检查按用户要求暂不纳入本次验收；默认测试样本路径未配置，测试明确记录为跳过。核心弓连击等可独立运行的检查仍执行。历史 1.8 扩展中依赖旧 Bukkit/NMS 的代码需要单独迁移源码并重编译；这些跳过项不构成扩展兼容性证明。

测试初始化真实 Paper 注册表、物品容器和数据转换器，并验证现代 ProtocolLib 数据包；玩家连接、调度、数据库等边界使用模拟对象。新增回归包含物品迁移 7 项、全息图生命周期 5 项、告示牌生命周期 4 项，以及本次名牌调度和数据库生命周期 5 项。测试未开启游戏端口、加载运行世界或连接生产数据库。

另执行独立 `paperLibrarySmoke` 检查，使用目标 Paper 真实 `LibraryLoader` 从空缓存下载 21 个运行库，逐一比对构建时 SHA-256，并验证薄 JAR 类加载、Jackson JSON 和 Nashorn 脚本。随后切换离线会话验证缓存复用、JAR 未被重写，以及空缓存离线启动被拒绝。结果为 `core/build/paper-library-smoke/result.txt`；生产代码使用 Paper 原生加载器，不包含这项验证所用的反射设置。

本地完整构建日志为 `audit/high-paper-libraries-build.log`，HTML 报告在各模块 `build/reports/tests/test/`。这些构建输出不入库。GitHub Actions 工作流 `.github/workflows/high-build.yml` 在本分支推送及 PR 时使用 JDK 21 构建、Java 25 测试并保存 JAR、测试报告和依赖缓存验证结果；本地结果不代表远程 CI 已通过。

## 交付产物与依赖

- 核心 JAR：`core/build/libs/ThePitGay-High.jar`
- 插件版本：`5.0.0-HIGH-SNAPSHOT`
- 文件大小：`7,764,450` 字节
- SHA-256：`be3113872c943ae1f9750872bd07548d9f324b2b0d66ba9de56cb31953e7b6bb`
- 映射命名空间：`mojang`

打包检查未发现对 `net/minecraft/server/v1_8_R3` 或 `org/bukkit/craftbukkit/v1_8_R3` 的类链接。历史 facade 的部分包名仍保留 `v1_8_R3`，其实现已迁移；这不表示旧 NMS 仍被打包。

运行需要 FancyNpcs、FancyHolograms、LuckPerms 和固定的 ProtocolLib **5.5.0-SNAPSHOT Spigot 发行包**。官方 ProtocolLib 5.4.0 的目标元数据处理未通过本次测试，因此仓库固定了支持目标版本的官方开发构建，并附来源、校验值及许可证。不能使用同一开发版中要求 API 26.3 的 Paper 发行包替换。

用户日志中的 MongoDB `zip file closed` 出现在名牌任务导致启用失败之后，符合插件类加载器关闭而监控线程仍存活的情况；该日志本身不能证明数据库地址有误或 JAR 损坏。本次修复已覆盖调度调用和资源回收回归，但尚未在用户的 Leaf 环境复测。更换核心时应完整停服后替换，再使用 Java 25 启动；不要热加载旧实例。

完整版本表、构建命令及扩展测试样本路径见 [构建与测试说明](BUILDING.md)，ProtocolLib 来源和安装要求见 [固定依赖说明](../vendor/dependencies/README.md)。服务端插件名称仍为 `ThePitUltimate`，以保留现有扩展的依赖名称。

## 尚待实服验收

本次没有接受 EULA，也没有启动测试服。以下范围尚未完成验证：

- Paper/Purpur 1.21.11 的启动、插件加载顺序和完整依赖组合。
- MongoDB、Redis 的实际读写，完整历史玩家库转换及恢复流程。
- 原生机器人的移动、碰撞、受击、攻击、跨世界和连接清理。
- 实际战斗平衡及现代原版机制。本次未新增完整的 1.8 战斗模拟，不能据此确认原有手感完全一致。
- WorldEdit 大地图、FAWE 使用场景及相关性能。
- Fancy 多人渲染、触摸、个人信息可见范围与重载行为。
- 已迁移 Addon、RepairExTend、NPCView 等外部扩展的完整集成。
- 多人 TPS、内存及 CPU 压力测试。

后续验收应使用独立目录、地图和配置副本、测试数据库及迁移前备份。本报告所确认的是代码迁移和自动化构建结果，实际运行验收完成后才能判断是否满足上线条件。
