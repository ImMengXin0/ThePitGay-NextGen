package net.mizukilab.pit.listener;

import com.google.common.util.concurrent.AtomicDouble;
import net.mizukilab.pit.enchantment.param.event.NotPlayerOnly;
import net.mizukilab.pit.enchantment.param.event.PlayerOnly;
import net.mizukilab.pit.parm.listener.*;
import net.mizukilab.pit.parm.type.BowOnly;
import net.mizukilab.pit.parm.type.ThrowOnly;
import org.bukkit.entity.*;
import org.junit.Test;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CallbackFiltersTest {
    public static class BowCallback implements IPlayerShootEntity {
        int hits;
        @BowOnly @PlayerOnly
        public void handleShootEntity(int level, Player player, Entity target, double damage,
                                      AtomicDouble finalDamage, AtomicDouble boost, AtomicBoolean cancel) { hits++; }
    }
    public static class InheritedBowCallback extends BowCallback { }
    public static class ThrowCallback implements IPlayerShootEntity {
        int hits;
        @ThrowOnly @NotPlayerOnly
        public void handleShootEntity(int level, Player player, Entity target, double damage,
                                      AtomicDouble finalDamage, AtomicDouble boost, AtomicBoolean cancel) { hits++; }
    }
    public static class EntityOnlyAttack implements IAttackEntity {
        int hits;
        @NotPlayerOnly
        public void handleAttackEntity(int level, Player player, Entity target, double damage,
                                       AtomicDouble finalDamage, AtomicDouble boost, AtomicBoolean cancel) { hits++; }
    }
    public static class PlayerKills implements IPlayerKilledEntity {
        int hits;
        @PlayerOnly
        public void handlePlayerKilled(int level, Player player, Entity target, AtomicDouble coin, AtomicDouble exp) { hits++; }
    }

    private void shot(IPlayerShootEntity callback, Player owner, Entity target, Entity source) throws Exception {
        Method method = GameEffectListener.class.getDeclaredMethod("processShootEntity", IPlayerShootEntity.class,
                int.class, Player.class, Entity.class, Entity.class, double.class,
                AtomicDouble.class, AtomicDouble.class, AtomicBoolean.class);
        method.setAccessible(true);
        method.invoke(new GameEffectListener(), callback, 3, owner, target, source, 4D,
                new AtomicDouble(4), new AtomicDouble(1), new AtomicBoolean());
    }

    @Test
    public void inheritedBowCallbackKeepsPlayerAndArrowRestrictionsAcrossRepeatedHits() throws Exception {
        InheritedBowCallback callback = new InheritedBowCallback();
        Player owner = mock(Player.class), target = mock(Player.class);
        Arrow arrow = mock(Arrow.class);
        Snowball snowball = mock(Snowball.class);
        shot(callback, owner, target, arrow);
        shot(callback, owner, mock(Zombie.class), arrow);
        shot(callback, owner, target, snowball);
        assertEquals(1, callback.hits);
        for (int i = 0; i < 1000; i++) {
            assertTrue(CallbackFilters.SHOOT.allows(callback, target, arrow));
            assertFalse(CallbackFilters.SHOOT.allows(callback, target, snowball));
        }
    }

    @Test
    public void thrownOnlyUsesTheProjectileSourceAndMethodTargetRestriction() throws Exception {
        ThrowCallback callback = new ThrowCallback();
        Player owner = mock(Player.class);
        Zombie target = mock(Zombie.class);
        shot(callback, owner, target, mock(Arrow.class));
        shot(callback, owner, mock(Player.class), mock(Snowball.class));
        assertEquals(0, callback.hits);
        shot(callback, owner, target, mock(Snowball.class));
        assertEquals(1, callback.hits);
    }

    @Test
    public void entityOnlyMeleeAllowsAPlayerAttackingANonPlayer() throws Exception {
        EntityOnlyAttack callback = new EntityOnlyAttack();
        Player owner = mock(Player.class);
        Method method = GameEffectListener.class.getDeclaredMethod("processAttackEntity", IAttackEntity.class,
                int.class, Player.class, Entity.class, double.class, AtomicDouble.class, AtomicDouble.class, AtomicBoolean.class);
        method.setAccessible(true);
        for (Entity target : new Entity[]{mock(Player.class), mock(Zombie.class)}) {
            method.invoke(new GameEffectListener(), callback, 3, owner, target, 4D,
                    new AtomicDouble(4), new AtomicDouble(1), new AtomicBoolean());
        }
        assertEquals(1, callback.hits);
    }

    @Test
    public void cachedKillFilterStillHonorsTargetAndMissingEnchantLevel() {
        PlayerKills callback = new PlayerKills();
        Player owner = mock(Player.class), target = mock(Player.class);
        GameEffectListener.processKilled(callback, 3, owner, mock(Zombie.class), new AtomicDouble(), new AtomicDouble());
        GameEffectListener.processKilled(callback, -1, owner, target, new AtomicDouble(), new AtomicDouble());
        assertEquals(0, callback.hits);
        GameEffectListener.processKilled(callback, 3, owner, target, new AtomicDouble(), new AtomicDouble());
        assertEquals(1, callback.hits);
    }
}
