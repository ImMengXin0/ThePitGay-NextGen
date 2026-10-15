package net.mizukilab.pit.command

import cn.charlotte.pit.ThePit
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import dev.rollczi.litecommands.annotations.argument.Arg
import dev.rollczi.litecommands.annotations.argument.Key
import dev.rollczi.litecommands.annotations.command.RootCommand
import dev.rollczi.litecommands.annotations.context.Context
import dev.rollczi.litecommands.annotations.execute.Execute
import dev.rollczi.litecommands.annotations.optional.OptionalArg
import dev.rollczi.litecommands.annotations.permission.Permission
import net.md_5.bungee.api.chat.ClickEvent
import net.mizukilab.pit.command.handler.HandHasItem
import net.mizukilab.pit.menu.item.AllSavedMythicItemsMenu
import net.mizukilab.pit.menu.item.SavedMythicItemMenu
import net.mizukilab.pit.util.chat.CC
import net.mizukilab.pit.util.DatabaseTasks
import net.mizukilab.pit.util.chat.ChatComponentBuilder
import net.mizukilab.pit.util.inventory.InventoryUtil
import net.mizukilab.pit.util.item.ItemBuilder
import net.mizukilab.pit.util.item.ItemUtil
import org.bson.Document
import org.bukkit.entity.Player

@RootCommand
class PitItemCommands {

    /**
     * @Author ShanguanLinG
     * @Created 2025/09/30 3:53
     */

    @Execute(name = "saveItem")
    @HandHasItem(mythic = true)
    fun saveItem(@Context player: Player) {
        val item = player.itemInHand.clone()
        val uuid = ItemUtil.getUUID(item)
        if (uuid == null) {
            player.sendMessage(CC.translate("&c&l错误! &c该物品缺少唯一UUID."))
            return
        }
        val internal = ItemUtil.getInternalName(item)
        val savedItem = ItemBuilder(item.clone()).saved(true).build()
        val mmItem = net.mizukilab.pit.util.Utils.getMythicItem0(savedItem) ?: return
        val updatedItem = mmItem.toItemStack()
        val heldSlot = player.inventory.heldItemSlot
        val encoded = InventoryUtil.serializeItemStack(updatedItem)
        if (encoded == null) {
            player.sendMessage(CC.translate("&c&l错误! &c序列化物品失败."))
            return
        }
        val collection = ThePit.getInstance().mongoDB.database.getCollection("saved_mythic_items")
        val doc = Document()
            .append("uuid", uuid)
            .append("internal", internal)
            .append("item", encoded)
            .append("createdBy", player.uniqueId.toString())
            .append("createdByName", player.name)
            .append("createdAt", System.currentTimeMillis())
        DatabaseTasks.submit(player, {
            collection.replaceOne(Filters.eq("uuid", uuid), doc, ReplaceOptions().upsert(true))
        }, {
            // Do not replace an item the player moved or changed while MongoDB was working.
            if (player.inventory.heldItemSlot == heldSlot && player.itemInHand == item) {
                player.itemInHand = updatedItem
                player.updateInventory()
            }
            val chat = ChatComponentBuilder(CC.translate("&a&l保存成功! &7已保存UUID为 &f$uuid &7的神话物品. &e[复制UUID]"))
                .setCurrentClickEvent(ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, uuid))
                .create()
            player.spigot().sendMessage(*chat)
        })
    }

    @Execute(name = "si")
    @HandHasItem(mythic = true)
    fun saveItemAlias(@Context player: Player) {
        saveItem(player)
    }

    @Execute(name = "searchItem")
    fun searchItem(@Context player: Player, @Arg("uuid") uuid: String) {
        val collection = ThePit.getInstance().mongoDB.database.getCollection("saved_mythic_items")
        DatabaseTasks.submit(player, { collection.find(Filters.eq("uuid", uuid)).first() }, { found ->
            val encoded = found?.getString("item")
            if (encoded == null) {
                player.sendMessage(CC.translate("&c未找到有效的物品记录。"))
            } else {
                SavedMythicItemMenu(uuid, encoded).openMenu(player)
            }
        })
    }

    @Execute(name = "sei")
    fun searchItemAlias(@Context player: Player, @Arg("uuid") uuid: String) {
        searchItem(player, uuid)
    }

    @Execute(name = "savedItems")
    @Permission("pit.admin")
    fun showSavedItems(@Context player: Player, @OptionalArg("playerId") @Key("pit.players") playerId: String?) {
        AllSavedMythicItemsMenu(1, playerId).openMenu(player)
    }

    @Execute(name = "sis")
    @Permission("pit.admin")
    fun showSavedItemsAlias(@Context player: Player, @OptionalArg("playerId") @Key("pit.players") playerId: String?) {
        showSavedItems(player, playerId)
    }

    @Execute(name = "removeItem")
    @Permission("pit.admin")
    fun removeItem(@Context player: Player, @Arg("uuid") uuid: String) {
        val collection = ThePit.getInstance().mongoDB.database.getCollection("saved_mythic_items")
        DatabaseTasks.submit(player, { collection.deleteOne(Filters.eq("uuid", uuid)) }, { result ->
            if (result.deletedCount > 0) {
                player.sendMessage(CC.translate("&a已删除物品记录: &f$uuid"))
            } else {
                player.sendMessage(CC.translate("&c未找到可删除的记录: &e$uuid"))
            }
        })
    }

    @Execute(name = "ri")
    @Permission("pit.admin")
    fun removeItemAlias(@Context player: Player, @Arg("uuid") uuid: String) {
        removeItem(player, uuid)
    }
}
