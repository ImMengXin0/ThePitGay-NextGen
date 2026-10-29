package cn.mythicland.npclib.listeners;

import cn.mythicland.npclib.internal.NPCBase;
import cn.mythicland.npclib.internal.NPCManager;
import cn.mythicland.npclib.NPCLib;
import org.bukkit.entity.Player;

public class HandleMoveBase {
    protected final NPCLib instance;

    protected HandleMoveBase(NPCLib instance) { this.instance = instance; }

    void handleMove(Player player) {
        if (instance.isShutdown() || !player.isOnline() || player.isDead()) return;
        for (NPCBase npc : instance.getNpcs()) {
            if (!npc.isCreated()) continue;
            if (!npc.getShown().contains(player.getUniqueId())) {
                continue; // NPC was never supposed to be shown to the player.
            }

            if (!npc.isShown(player)
                    && npc.isLocationChunkLoaded()
                    && npc.inRangeOf(player)
                    && npc.inViewOf(player)) {
                // The player is in range and can see the NPC, auto-show it.
                npc.show(player, true);
            } else if (npc.isShown(player) && !npc.inRangeOf(player)) {
                // The player is not in range of the NPC anymore, auto-hide it.
                npc.hide(player, true);
            }
        }
    }

}
