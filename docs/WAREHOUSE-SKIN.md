# 寄存所玩家皮肤

nextgen 与 ThePitGay-High 的寄存所 NPC 均使用玩家模型，默认皮肤为《崩坏：星穹铁道》昔涟（Cyrene）的 Minecraft 同人皮肤，来源为 [MineSkin](https://mineskin.org/0961f7062f76400ba776649c38b37d40)。完整 Mojang 签名纹理随 JAR 内置，加载时无需查询皮肤接口。

`custom.yml` 自动补齐 `warehouse_npc-npc-skin` 与 `not-netease-skins.warehouse_npc-signature`，管理员可成对替换为自定义签名皮肤。普通版通过玩家 NPC 工厂创建寄存所，`/pit warehouseLoc` 同时刷新该工厂。

2026-11-15 核对的四份候选皮肤，按预览图从左到右排列：

| MineSkin | 签名记录的正版账号名 | UUID |
| --- | --- | --- |
| [0957e2d40937](https://mineskin.org/0957e2d409374c44902b0fbc596fe69a) | Yeleha | 3fc7fdf9-3963-4c41-9119-9ba3f7cc3fed |
| [0961f7062f76（内置）](https://mineskin.org/0961f7062f76400ba776649c38b37d40) | wMisieur | d2ca5e0f-2d36-4ba1-8d0f-0c277a1da6ef |
| [890e69227ef9](https://mineskin.org/890e69227ef942d6a34a93fd3a101d2b) | BradBot_2 | e8150f52-9fde-4c7d-b279-02f2c6e7579d |
| [e51a775cac56](https://mineskin.org/e51a775cac56478cb35c9ba9b30a6427) | BITNW | 173de31f-2381-4c65-b5fe-c2102bb1f115 |

以上账号名来自签名纹理的 `profileName`，并经 Mojang session server 核对；它们是签名时使用的账号，不代表皮肤作者。查询时四个账号当前的皮肤均已改变。按账号名重新抓取无法固定为预览图中的皮肤，应使用对应 MineSkin 条目的纹理与签名。

普通版构建通过：在继续排除此前确认缺失外部样本的 79 个测试方法后，408 项中 383 项通过、25 项跳过，0 项失败；其中皮肤签名、配置补全与覆盖回归 7 项全部通过。未启动 Minecraft 服务端。
