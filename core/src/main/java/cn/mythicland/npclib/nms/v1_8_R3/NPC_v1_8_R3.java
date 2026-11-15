package cn.mythicland.npclib.nms.v1_8_R3;

import cn.mythicland.npclib.NPCLib;
import cn.mythicland.npclib.internal.NPCBase;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import java.util.List;

/** Historical entry point retained for extensions; implementation is FancyNpcs. */
public class NPC_v1_8_R3 extends NPCBase {
    public NPC_v1_8_R3(NPCLib owner, List<String> lines) { super(owner, lines); }
    public static NPC_v1_8_R3 villager(NPCLib owner, List<String> lines, Villager.Profession profession) {
        NPC_v1_8_R3 npc = new NPC_v1_8_R3(owner, lines);
        npc.type = EntityType.VILLAGER;
        npc.profession = java.util.Objects.requireNonNull(profession);
        return npc;
    }
}
