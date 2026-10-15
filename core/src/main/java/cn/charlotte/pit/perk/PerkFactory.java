package cn.charlotte.pit.perk;

import cn.charlotte.pit.ThePit;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.SneakyThrows;
import net.mizukilab.pit.parm.listener.*;
import net.mizukilab.pit.util.PublicUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/1 22:26
 */
public class PerkFactory {

    private PerkFactory delegate;
    private final List<AbstractPerk> perks;
    private final List<IPlayerDamaged> playerDamageds;
    private final List<IAttackEntity> attackEntities;
    private final List<IItemDamage> iItemDamages;
    private final List<IPlayerBeKilledByEntity> playerBeKilledByEntities;
    private final List<IPlayerKilledEntity> playerKilledEntities;
    private final List<IPlayerRespawn> playerRespawns;
    private final List<IPlayerShootEntity> playerShootEntities;
    private final Map<AbstractPerk, ITickTask> tickTasks;
    private final List<IPlayerAssist> playerAssists;

    private final Map<String, AbstractPerk> perkMap = new Object2ObjectOpenHashMap<>();


    public PerkFactory() {
        this.perks = new ObjectArrayList<>();
        this.playerDamageds = new ObjectArrayList<>();
        this.iItemDamages = new ObjectArrayList<>();
        this.attackEntities = new ObjectArrayList<>();
        this.playerBeKilledByEntities = new ObjectArrayList<>();
        this.playerKilledEntities = new ObjectArrayList<>();
        this.playerRespawns = new ObjectArrayList<>();
        this.tickTasks = new Object2ObjectOpenHashMap<>();
        this.playerShootEntities = new ObjectArrayList<>();
        this.playerAssists = new ObjectArrayList<>();
    }

    @SneakyThrows
    public void init(Collection<Class<?>> classes) {
        // Older addons construct a temporary factory for an additional streak.
        // Once a live core registry exists, that facade must feed the registry used by menus/ticks.
        ThePit plugin = ThePit.getInstance();
        PerkFactory active = plugin == null ? null : plugin.getPerkFactory();
        if (active != null && active != this) {
            delegate = active;
            active.init(classes);
            return;
        }
        for (Class<?> clazz : classes) {
            if (AbstractPerk.class.isAssignableFrom(clazz)) {
                registerPerk((AbstractPerk) clazz.getConstructor().newInstance());
            }
        }
    }

    public void registerPerk(AbstractPerk perk) {
        if (delegate != null) { delegate.registerPerk(perk); return; }
        // The addon also registers through a temporary factory; reject before any listeners/callbacks.
        if (isRegistrationBlocked(perk.getInternalPerkName())) return;
        AbstractPerk previous = perkMap.get(perk.getInternalPerkName());
        if (previous == perk) return;
        if (previous != null) removePerk(previous);
        perks.add(perk);
        perkMap.put(perk.getInternalPerkName(), perk);
        PublicUtil.register(perk.getClass(), perk, playerDamageds, attackEntities, iItemDamages,
                playerBeKilledByEntities, playerKilledEntities, playerRespawns, playerShootEntities);
        if (perk instanceof ITickTask task) tickTasks.put(perk, task);
        if (perk instanceof IPlayerAssist assist) playerAssists.add(assist);
    }

    public static boolean isRegistrationBlocked(String internalName) {
        return "crazy_dog_mode_mega_streak".equals(internalName);
    }

    public void removePerk(AbstractPerk perk) {
        if (delegate != null) { delegate.removePerk(perk); return; }
        PublicUtil.unregister(perk.getClass(), perk, playerDamageds, attackEntities, iItemDamages,
                playerBeKilledByEntities, playerKilledEntities, playerRespawns, playerShootEntities);
        perks.removeIf(value -> value == perk);
        tickTasks.values().removeIf(value -> value == perk);
        playerAssists.removeIf(value -> value == perk);
        if (perkMap.get(perk.getInternalPerkName()) == perk) perkMap.remove(perk.getInternalPerkName());
    }

    public List<AbstractPerk> getPerks() {
        return delegate == null ? perks : delegate.getPerks();
    }

    public List<IPlayerDamaged> getPlayerDamageds() {
        return delegate == null ? playerDamageds : delegate.getPlayerDamageds();
    }

    public List<IAttackEntity> getAttackEntities() {
        return delegate == null ? attackEntities : delegate.getAttackEntities();
    }

    public List<IItemDamage> getiItemDamages() {
        return delegate == null ? iItemDamages : delegate.getiItemDamages();
    }

    public List<IPlayerBeKilledByEntity> getPlayerBeKilledByEntities() {
        return delegate == null ? playerBeKilledByEntities : delegate.getPlayerBeKilledByEntities();
    }

    public List<IPlayerKilledEntity> getPlayerKilledEntities() {
        return delegate == null ? playerKilledEntities : delegate.getPlayerKilledEntities();
    }

    public List<IPlayerRespawn> getPlayerRespawns() {
        return delegate == null ? playerRespawns : delegate.getPlayerRespawns();
    }

    public List<IPlayerShootEntity> getPlayerShootEntities() {
        return delegate == null ? playerShootEntities : delegate.getPlayerShootEntities();
    }

    public Map<AbstractPerk, ITickTask> getTickTasks() {
        return delegate == null ? tickTasks : delegate.getTickTasks();
    }

    public List<IPlayerAssist> getPlayerAssists() {
        return delegate == null ? playerAssists : delegate.getPlayerAssists();
    }

    public Map<String, AbstractPerk> getPerkMap() {
        return delegate == null ? perkMap : delegate.getPerkMap();
    }
}
