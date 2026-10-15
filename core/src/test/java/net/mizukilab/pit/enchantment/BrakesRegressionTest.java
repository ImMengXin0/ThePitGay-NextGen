package net.mizukilab.pit.enchantment;

import com.google.common.util.concurrent.AtomicDouble;
import net.mizukilab.pit.enchantment.type.rage.Brakes;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BrakesRegressionTest {
    @Test
    public void highGroundReductionMatchesTheLevelInTheLore() {
        Brakes brakes = new Brakes();
        Player victim = mock(Player.class), attacker = mock(Player.class);
        when(victim.getLocation()).thenReturn(new Location(null, 0, 64, 0));
        when(attacker.getLocation()).thenReturn(new Location(null, 0, 65, 0));
        double[] expected = {1.0, 0.95, 0.90};
        for (int level = 1; level <= 3; level++) {
            AtomicDouble multiplier = new AtomicDouble(1);
            brakes.handlePlayerDamaged(level, victim, attacker, 10, new AtomicDouble(10), multiplier, new AtomicBoolean());
            assertEquals("level " + level, expected[level - 1], multiplier.get(), 1e-9);
        }
    }

    @Test
    public void sameHeightOrLowerAttacksDoNotReceiveHighGroundReduction() {
        Brakes brakes = new Brakes();
        Player victim = mock(Player.class), attacker = mock(Player.class);
        when(victim.getLocation()).thenReturn(new Location(null, 0, 64, 0));
        for (int y : new int[]{63, 64}) {
            when(attacker.getLocation()).thenReturn(new Location(null, 0, y, 0));
            AtomicDouble multiplier = new AtomicDouble(1);
            brakes.handlePlayerDamaged(3, victim, attacker, 10, new AtomicDouble(10), multiplier, new AtomicBoolean());
            assertEquals(1, multiplier.get(), 0);
        }
    }

    @Test
    public void levelOneStillReducesSpeedTwoToSpeedOneWithoutChangingItsDuration() {
        Brakes brakes = new Brakes();
        Player attacker = mock(Player.class), target = mock(Player.class);
        when(target.getActivePotionEffects()).thenReturn(List.of(new PotionEffect(PotionEffectType.SPEED, 240, 1)));
        brakes.handleAttackEntity(1, attacker, target, 5, new AtomicDouble(5), new AtomicDouble(1), new AtomicBoolean());
        verify(target).addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 240, 0), true);
    }
}
