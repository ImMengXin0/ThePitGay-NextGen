# 内置 NPC 点击排查

用户报告人物 NPC 已显示“天赋 / 右键查看”，右键没有打开菜单，后台也没有异常；这里的 MythicLib 指核心内嵌的 MythicNPCLib。

## 已确认的范围

点击链路为：Paper `PlayerUseUnknownEntityEvent` → FancyNpcs 原生点击监听器或核心的 `INTERACT_AT` 补充路径 → Fancy `NpcInteractEvent` → 内嵌 `NPCInteractEvent` → `NpcFactory` 根据 NPC UUID 匹配模型 → `PerkNPC.handlePlayerInteract()` → `PerkChooseMenu.openMenu()`。

内嵌库随核心重定位到 `net.mizukilab.pit.libs.npclib`，内置 NPC 的模型、显示和点击都使用同一套包装对象。当前没有证据说明内嵌库与 FancyNpcs 本身冲突。

新增集成回归使用真实 FancyNpcs `PlayerUseUnknownEntityListener`、`Npc.interact()`、Bukkit HandlerList 注册与事件分发、核心 NPCLib、NpcFactory、天赋 NPC 处理方法和天赋菜单构建。普通右键、点身体的 `INTERACT_AT` 均打开菜单；Fancy 点击事件被取消时不会打开菜单。额外使用用户的 `2.10.1-java21+2` JAR 运行这些检查。

测试模拟玩家连接、NMS 实体创建、调度和物品构建等服务端边界；没有启动 Leaf 或接入用户实际插件组合。因此这些结果不能证明用户实服的问题已经修复，也不能排除取消事件、监听器缺失、旧产物或可见状态等运行差异。

## 实服诊断

完整停服后替换最新核心，启动后由有 `pit.admin` 权限的玩家在游戏中执行：

```text
/pit npcdebug
```

随后右键一次出问题的 NPC，提供后台从 `stage=start` 开始的 `[NPC-CLICK]` 日志。诊断仅记录发起玩家，持续 60 秒，最多 120 条。到期、退出或核心关闭后移除数据包观察器。诊断不修改数据包和事件的取消状态，也不会强制打开菜单。

开始时记录补丁标识、NPC 实体 ID、注册状态、可见状态、世界、距离，以及 Paper、Fancy、内嵌游戏事件、背包打开事件的监听器清单。Fancy 2.10 新增的前置点击事件通过可选反射读取监听器清单，兼容 2.9 基线。

| 日志阶段 | 含义 |
| --- | --- |
| `packet` | ProtocolLib 收到使用实体的数据包，包含目标 ID 和最终取消状态 |
| `paper` | Paper 已发布未知实体交互事件，包含主副手、攻击及相对落点 |
| `fancy` | Fancy 已发布交互事件，包含触发类型和取消状态 |
| `rejected` | 核心未匹配到自身 NPC，或在线、死亡、可见性、世界、距离等检查未通过 |
| `debounced` | 核心抑制同次点击的重复分发 |
| `dispatch` | 核心已分发内嵌 NPC 点击事件 |
| `model` / `model-missing` | 事件匹配或未匹配到内置 NPC 模型 |
| `menu` | 进入具体菜单构建流程 |
| `menu-cancelled` | Bukkit 的打开背包调用返回空结果，菜单未打开 |
| `menu-open` / `menu-error` | 菜单打开流程完成或抛出异常 |

同一事件存在多个监听器，日志行可能交错；例如 Fancy 在 NORMAL 优先级处理 Paper 事件，可能在核心 MONITOR 阶段记录 `paper` 之前已出现 `menu-open`。判断时应按目标实体 ID 和阶段是否出现核对，不能只依赖行顺序。

只有 `packet` 而缺少 `paper` 时，应检查协议层取消和服务端实体处理；出现 `paper` 却没有 `fancy` 时，应结合 Fancy 原生监听器和前置事件清单核对。监听器清单只能说明谁参与处理，不能仅凭清单认定某插件取消了事件。

完整构建 **563 项，458 通过、105 跳过、0 失败或错误**；用户版 Fancy 兼容任务 **19 项全部通过**，单独统计。验证日志：`audit/high-npc-menu-debug-build.log`。自动化检查不启动测试服、不接受 EULA、不连接生产数据库。原有 105 项外部扩展检查仍按用户要求跳过。
