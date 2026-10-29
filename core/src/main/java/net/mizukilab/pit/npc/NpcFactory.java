package net.mizukilab.pit.npc;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.event.PitProfileLoadedEvent;
import cn.mythicland.npclib.NPCLib;
import cn.mythicland.npclib.NPCLibOptions;
import cn.mythicland.npclib.api.NPC;
import cn.mythicland.npclib.api.events.NPCInteractEvent;
import cn.mythicland.npclib.api.state.NPCSlot;
import cn.mythicland.npclib.internal.NPCBase;
import cn.mythicland.npclib.internal.NPCManager;
import lombok.Getter;
import net.mizukilab.pit.npc.runnable.NpcRunnable;
import net.mizukilab.pit.parm.AutoRegister;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

@AutoRegister
public class NpcFactory implements Listener {
    private final NPCLib npcLib = new NPCLib(ThePit.getInstance(), new NPCLibOptions()
            .setMovementHandling(NPCLibOptions.MovementHandling.repeatingTask(5)));
    @Getter private final List<AbstractPitNPC> pitNpc = new ArrayList<>();
    private BukkitTask refreshTask;

    public boolean hasNPC(Entity entity) {
        return entity != null && NPCManager.getByEntityId(entity.getEntityId()) != null;
    }

    public void reload() {
        for (AbstractPitNPC model : pitNpc) {
            if (model.getNpc() != null) model.getNpc().destroy();
            model.setNpc(null);
            createNpc(model);
        }
        for (Player player : Bukkit.getOnlinePlayers()) show(player);
    }

    public void show(Player player) {
        if (!player.isOnline()) return;
        for (AbstractPitNPC model : pitNpc) {
            NPC npc = model.getNpc();
            if (npc == null) continue;
            npc.setText(player, model.getNpcTextLine(player));
            // show() is idempotent, including viewers waiting for a chunk/range.
            npc.show(player);
        }
    }

    public void init(Collection<Class<? extends AbstractPitNPC>> classes) {
        for (Class<? extends AbstractPitNPC> type : classes) {
            if (!ThePit.isDEBUG_SERVER() && type.getName().contains("InfinityItemNPC")) continue;
            if (pitNpc.stream().anyMatch(npc -> npc.getClass() == type)) continue;
            try {
                AbstractPitNPC model = type.getConstructor().newInstance();
                if (createNpc(model)) pitNpc.add(model);
            } catch (ReflectiveOperationException failure) {
                ThePit.getInstance().getLogger().log(java.util.logging.Level.WARNING,
                        "Cannot register NPC " + type.getName(), failure);
            }
        }
        if (refreshTask == null && !pitNpc.isEmpty()) {
            refreshTask = new NpcRunnable().runTaskTimer(ThePit.getInstance(), 20, 20);
        }
        for (Player player : Bukkit.getOnlinePlayers()) show(player);
    }

    private boolean createNpc(AbstractPitNPC model) {
        Location location = model.getNpcSpawnLocation();
        if (location == null || location.getWorld() == null) {
            ThePit.getInstance().getLogger().warning("NPC location is unavailable: " + model.getNpcInternalName());
            return false;
        }
        NPC npc = npcLib.createNPC();
        try {
            npc.setLocation(location);
            ((NPCBase) npc).disableFOV();
            if (model.getNpcHeldItem() != null) npc.setItem(NPCSlot.MAINHAND, model.getNpcHeldItem());
            model.setNpc(npc);
            model.initSkin(npc);
            npc.create();
            if (model.getAnimation() != null) npc.playAnimation(model.getAnimation());
            return true;
        } catch (RuntimeException failure) {
            npc.destroy();
            model.setNpc(null);
            throw failure;
        }
    }

    public void shutdown() {
        if (refreshTask != null) refreshTask.cancel();
        refreshTask = null;
        npcLib.shutdown();
        pitNpc.clear();
        HandlerList.unregisterAll(this);
    }

    @EventHandler public void onPlayerJoin(PitProfileLoadedEvent event) {
        Player player = Bukkit.getPlayer(event.getPlayerProfile().getPlayerUuid());
        if (player != null && player.isOnline()) show(player);
    }

    @EventHandler public void onInteract(NPCInteractEvent event) {
        for (AbstractPitNPC model : pitNpc) {
            if (model.getNpc() != null && model.getNpc().getUniqueId().equals(event.getNPC().getUniqueId())) {
                model.handlePlayerInteract(event.getWhoClicked());
                return;
            }
        }
    }
}
