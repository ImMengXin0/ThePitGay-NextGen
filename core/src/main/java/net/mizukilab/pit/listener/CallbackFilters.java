package net.mizukilab.pit.listener;

import com.google.common.util.concurrent.AtomicDouble;
import net.mizukilab.pit.enchantment.param.event.NotPlayerOnly;
import net.mizukilab.pit.enchantment.param.event.PlayerOnly;
import net.mizukilab.pit.parm.listener.*;
import net.mizukilab.pit.parm.type.BowOnly;
import net.mizukilab.pit.parm.type.ThrowOnly;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** Resolves callback annotations once per class, without retaining unloaded extension class loaders. */
enum CallbackFilters {
    KILLED(IPlayerKilledEntity.class, "handlePlayerKilled", false),
    BE_KILLED(IPlayerBeKilledByEntity.class, "handlePlayerBeKilledByEntity", false),
    ATTACK(IAttackEntity.class, "handleAttackEntity", true),
    DAMAGED(IPlayerDamaged.class, "handlePlayerDamaged", true),
    SHOOT(IPlayerShootEntity.class, "handleShootEntity", true);

    private static final int PLAYER = 1, NOT_PLAYER = 2, BOW = 4, THROW = 8;
    private final Class<?> callbackType;
    private final String methodName;
    private final boolean damageCallback;
    private final ClassValue<Integer> flags = new ClassValue<>() {
        @Override protected Integer computeValue(Class<?> type) { return readFlags(type); }
    };

    CallbackFilters(Class<?> callbackType, String methodName, boolean damageCallback) {
        this.callbackType = callbackType;
        this.methodName = methodName;
        this.damageCallback = damageCallback;
    }

    private int readFlags(Class<?> type) {
        if (!callbackType.isAssignableFrom(type)) throw new IllegalArgumentException(type.getName());
        try {
            Method method = damageCallback
                    ? type.getMethod(methodName, int.class, Player.class, Entity.class, double.class,
                                     AtomicDouble.class, AtomicDouble.class, AtomicBoolean.class)
                    : type.getMethod(methodName, int.class, Player.class, Entity.class, AtomicDouble.class, AtomicDouble.class);
            int result = 0;
            // These legacy Addon callbacks unconditionally cast their victim to Player.
            // Cache the exact implementation match, preserving other plugins' same-NBT behavior.
            if (this == ATTACK || this == SHOOT) {
                switch (type.getName()) {
                    case "cn.apnf.mistral.enchantments.normal.ComboChaosEnchant",
                         "cn.apnf.mistral.enchantments.rare.ComboParasiteEnchant",
                         "cn.apnf.mistral.enchantments.rare.SevereInjuryEnchant",
                         "cn.apnf.mistral.enchantments.rare.NightMare",
                         "cn.apnf.mistral.enchantments.addon.rare.VitalEchoEnchant",
                         "cn.apnf.mistral.enchantments.rare.TearEnchant" -> result |= PLAYER;
                }
            }
            if (method.isAnnotationPresent(PlayerOnly.class) || type.isAnnotationPresent(PlayerOnly.class)) result |= PLAYER;
            else if (method.isAnnotationPresent(NotPlayerOnly.class) || type.isAnnotationPresent(NotPlayerOnly.class)) result |= NOT_PLAYER;
            if (this == SHOOT) {
                if (method.isAnnotationPresent(BowOnly.class)) result |= BOW;
                if (method.isAnnotationPresent(ThrowOnly.class)) result |= THROW;
            }
            return result;
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("Missing callback " + type.getName() + "." + methodName, exception);
        }
    }

    boolean allows(Object callback, Entity target) { return allows(callback, target, null); }

    boolean allows(Object callback, Entity target, Entity damageSource) {
        int value = flags.get(callback.getClass());
        if ((value & PLAYER) != 0 && !(target instanceof Player)) return false;
        if ((value & NOT_PLAYER) != 0 && target instanceof Player) return false;
        if ((value & BOW) != 0 && !(damageSource instanceof Arrow)) return false;
        return (value & THROW) == 0 || !(damageSource instanceof Arrow);
    }
}
