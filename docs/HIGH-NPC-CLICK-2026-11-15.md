# 内置 NPC 点击排查

用户报告人物 NPC 已显示“天赋 / 右键查看”，右键没有打开菜单，后台也没有异常；这里的 MythicLib 指核心内嵌的 MythicNPCLib。

## 本次日志与根因

20:05 的实服诊断显示，玩家点击商店实体 8 时有 `packet` 和 `paper` 阶段，核心的 `registered=true`，但始终没有 `fancy`、`dispatch`、`model` 或 `menu`。这把失败范围缩小到 Fancy 查询实体的环节；原来的 `registered` 只代表核心内嵌库的注册表，不能证明 Fancy 的注册表仍有这个实体。

检查 FancyNpcs 实际代码后发现：其启动后延迟 100 tick 调用 `NpcManagerImpl.loadNpcs()`，该方法先清空注册表，再加载 YAML 并发布 `NpcsLoadedEvent`。核心提前创建的 NPC 不写入 YAML，因此会从 Fancy 注册表消失；核心仍保留并显示这些对象。Fancy 原生监听器与核心的身体右键补充路径都查询 Fancy 注册表，找不到实体后直接返回，不产生报错。

补丁监听 `NpcsLoadedEvent`，重新注册核心仍持有的 NPC，并按已有玩家可见策略刷新显示。保留原对象、实体 ID、皮肤、个人文字和点击回调；注册身份一致时不重复注册。异步通知转回主线程处理，已删除 NPC、已关闭服务不会恢复。原生 Fancy 的点击取消行为继续生效。`/pit npcdebug` 的状态行新增独立的 `fancyRegistered` 字段，以区分两个注册表。

## 点击链路与验证

点击链路为：Paper `PlayerUseUnknownEntityEvent` → FancyNpcs 原生点击监听器或核心的 `INTERACT_AT` 补充路径 → Fancy `NpcInteractEvent` → 内嵌 `NPCInteractEvent` → `NpcFactory` 根据 NPC UUID 匹配模型 → `PerkNPC.handlePlayerInteract()` → `PerkChooseMenu.openMenu()`。

内嵌库随核心重定位到 `net.mizukilab.pit.libs.npclib`，内置 NPC 的模型、显示和点击都使用同一套包装对象。当前没有证据说明内嵌库与 FancyNpcs 本身冲突。

集成回归使用真实 FancyNpcs `NpcManagerImpl`、`loadNpcs()`、`reloadNpcs()`、`PlayerUseUnknownEntityListener`、`Npc.interact()`、Bukkit HandlerList 注册与事件分发、核心 NPCLib、NpcFactory、天赋 NPC 处理方法和天赋菜单构建。普通右键、点身体的 `INTERACT_AT` 在延迟清空注册表后仍打开菜单；Fancy 点击事件被取消时不会打开菜单。临时 YAML 文件位于测试目录。

本轮增加四项回归：延迟加载后的两种右键菜单流程，重复加载与重载时保持 ID、可见策略、个人文字且不恢复已删除对象，以及异步加载回到主线程并跳过已经关闭的服务。此前菜单测试把管理器查询模拟为固定返回 NPC，遗漏了延迟清空注册表的生命周期；现在改用真实管理器。默认 FancyNpcs 2.9.2 与用户的 `2.10.1-java21+2` 均运行这些检查。

测试模拟玩家连接、NMS 实体创建、调度和物品构建等服务端边界；没有启动 Leaf 或接入用户实际插件组合。已确认代码中的注册丢失问题及补丁回归，实服仍需更换产物重启复测。

后续收到 `You're currently on cooldown. ... remaining.` 提示报告：核心 NPC 已关闭 Fancy 的 0.25 秒交互冷却，继续使用桥接已有的 100 毫秒静默去重。连续原生点击回归在 Fancy 2.9.2 和用户的 2.10.1 变体上均通过；详见 [后续玩法补丁](HIGH-GAMEPLAY-2026-11-15.md)。

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
