package cn.mythicland.framework.bukkit.diagnostics;

import java.lang.reflect.Array;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.DataWatcher;
import net.minecraft.server.v1_8_R3.GenericAttributes;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_8_R3.util.CraftMagicNumbers;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class DiagnosticSnapshot {
    private DiagnosticSnapshot() {
    }

    static int tick() {
        return MinecraftServer.currentTick;
    }

    static Map<String, Object> player(Player player, boolean equipment) {
        return player(player, equipment, new int[]{512, 8192});
    }

    static Map<String, Object> player(Player player, DiagnosticOptions options) {
        int[] remaining = {512, 8192, options.includes(DiagnosticOptions.Detail.NBT) ? 1 : 0};
        if (options.captures(DiagnosticOptions.PacketGroup.COMBAT)) return player(player, true, remaining);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uuid", player.getUniqueId().toString());
        out.put("name", player.getName());
        out.put("entityId", player.getEntityId());
        out.put("world", player.getWorld().getUID().toString());
        if (options.captures(DiagnosticOptions.PacketGroup.MOVEMENT)
                || options.captures(DiagnosticOptions.PacketGroup.VISIBILITY)) {
            out.put("position", location(player.getLocation()));
            out.put("velocity", vector(player.getVelocity()));
            out.put("onGround", player.isOnGround());
            out.put("sprint", player.isSprinting());
            out.put("sneak", player.isSneaking());
        }
        if (options.captures(DiagnosticOptions.PacketGroup.HEALTH)) {
            out.put("health", player.getHealth());
            out.put("maxHealth", player.getMaxHealth());
            out.put("absorption", ((CraftPlayer) player).getHandle().getAbsorptionHearts());
            out.put("dead", player.isDead());
        }
        if (options.captures(DiagnosticOptions.PacketGroup.EQUIPMENT)) {
            out.put("slot", player.getInventory().getHeldItemSlot());
            out.put("hand", value(player.getItemInHand(), 0, new IdentityHashMap<>(), remaining));
            out.put("armor", value(player.getInventory().getArmorContents(), 0, new IdentityHashMap<>(), remaining));
        }
        if (options.captures(DiagnosticOptions.PacketGroup.LATENCY)) out.put("ping", ((CraftPlayer) player).getHandle().ping);
        return out;
    }

    static Map<String, Object> viewer(Player viewer, Collection<? extends Player> targets, DiagnosticOptions options) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uuid", viewer.getUniqueId().toString());
        if (options.captures(DiagnosticOptions.PacketGroup.HEALTH)) {
            var objective = viewer.getScoreboard().getObjective(DisplaySlot.BELOW_NAME);
            if (objective == null) out.put("belowName", null);
            else {
                Map<String, Object> scores = new LinkedHashMap<>();
                for (Player target : targets) {
                    var score = objective.getScore(target.getName());
                    scores.put(target.getName(), score.isScoreSet() ? score.getScore() : null);
                }
                out.put("belowName", Map.of("objective", objective.getName(), "displayName", objective.getDisplayName(), "scores", scores));
            }
        }
        if (options.captures(DiagnosticOptions.PacketGroup.VISIBILITY)
                || options.captures(DiagnosticOptions.PacketGroup.EQUIPMENT)) {
            Map<String, Object> visibility = new LinkedHashMap<>();
            for (Player target : targets) visibility.put(target.getName(), viewer.canSee(target));
            out.put("canSee", visibility);
        }
        return out;
    }

    private static Map<String, Object> player(Player player, boolean equipment, int[] remaining) {
        EntityPlayer handle = ((CraftPlayer) player).getHandle();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uuid", player.getUniqueId().toString());
        out.put("name", player.getName());
        out.put("entityId", player.getEntityId());
        out.put("world", player.getWorld().getUID().toString());
        out.put("position", location(player.getLocation()));
        out.put("eye", location(player.getEyeLocation()));
        out.put("velocity", vector(player.getVelocity()));
        out.put("bounds", List.of(
                handle.getBoundingBox().a,
                handle.getBoundingBox().b,
                handle.getBoundingBox().c,
                handle.getBoundingBox().d,
                handle.getBoundingBox().e,
                handle.getBoundingBox().f
        ));
        out.put("health", player.getHealth());
        out.put("maxHealth", player.getMaxHealth());
        out.put("absorption", handle.getAbsorptionHearts());
        out.put("noDamageTicks", handle.noDamageTicks);
        out.put("maximumNoDamageTicks", handle.maxNoDamageTicks);
        out.put("lastDamage", handle.lastDamage);
        out.put("hurtTicks", handle.hurtTicks);
        out.put("fallDistance", player.getFallDistance());
        out.put("onGround", player.isOnGround());
        out.put("sprint", player.isSprinting());
        out.put("sneak", player.isSneaking());
        out.put("blocking", player.isBlocking());
        out.put("dead", player.isDead());
        out.put("gameMode", player.getGameMode().name());
        out.put("flying", player.isFlying());
        out.put("vehicle", player.getVehicle() == null ? null : player.getVehicle().getUniqueId().toString());
        out.put("water", handle.V());
        out.put("climbing", handle.k_());
        out.put("nativeAttack", handle.getAttributeInstance(GenericAttributes.ATTACK_DAMAGE).getValue());
        out.put("ping", handle.ping);
        out.put("slot", player.getInventory().getHeldItemSlot());
        ItemStack hand = player.getItemInHand();
        out.put("hand", equipment ? value(hand, 0, new IdentityHashMap<>(), remaining) : hand == null ? "AIR" : hand.getType().name());
        out.put("potions", player.getActivePotionEffects()
                .stream()
                .map(effect -> Map.of(
                        "type", effect.getType().getName(), "amplifier", effect.getAmplifier(), "duration", effect.getDuration()))
                .toList());
        Location feet = player.getLocation();
        List<Object> footing = new ArrayList<>();
        for (int dy = -1; dy <= 0; dy++) {
            var block = player.getWorld().getBlockAt(feet.getBlockX(), feet.getBlockY() + dy, feet.getBlockZ());
            footing.add(Map.of("y", block.getY(), "type", block.getType().name(), "data", block.getData()));
        }
        out.put("footing", footing);
        if (equipment)
            out.put("armor", value(player.getInventory().getArmorContents(), 0, new IdentityHashMap<>(), remaining));
        return out;
    }

    static Map<String, Object> event(Event event) {
        return event(event, true);
    }

    static Map<String, Object> event(Event event, boolean nbt) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("class", event.getClass().getName());
        out.put("asynchronous", event.isAsynchronous());
        if (event instanceof Cancellable cancellable) out.put("cancelled", cancellable.isCancelled());
        if (!event.isAsynchronous()) out.put("tick", tick());
        if (event instanceof EntityDamageEvent damage) {
            out.put("cause", damage.getCause().name());
            out.put("damage", damage.getDamage());
            out.put("finalDamage", damage.getFinalDamage());
            Map<String, Object> modifiers = new LinkedHashMap<>();
            for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
                if (damage.isApplicable(modifier)) modifiers.put(modifier.name(), damage.getDamage(modifier));
            }
            out.put("modifiers", modifiers);
        }
        out.put("fields", value(event, 0, new IdentityHashMap<>(), new int[]{512, 8192, nbt ? 1 : 0}));
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> packet(Object packet, boolean nbt) {
        return (Map<String, Object>) value(packet, 0, new IdentityHashMap<>(), new int[]{512, 8192, nbt ? 1 : 0});
    }

    static Map<String, Object> item(net.minecraft.server.v1_8_R3.ItemStack item, boolean nbt) {
        return item(item, new int[]{512, 8192, nbt ? 1 : 0});
    }

    private static Map<String, Object> item(net.minecraft.server.v1_8_R3.ItemStack item, int[] remaining) {
        if (item == null) return Map.of("type", "AIR");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", CraftMagicNumbers.getMaterial(item.getItem()).name());
        out.put("amount", item.count);
        out.put("damage", item.getData());
        if (remaining.length == 2 || remaining[2] != 0) {
            NBTTagCompound tag = new NBTTagCompound();
            item.save(tag);
            out.put("nbt", limited(tag.toString(), remaining));
        }
        return out;
    }

    static Object value(Object object) {
        return value(object, 0, new IdentityHashMap<>(), new int[]{512, 8192});
    }

    private static Object value(Object object, int depth, IdentityHashMap<Object, Boolean> seen, int[] remaining) {
        if (--remaining[0] < 0) return Map.of("omitted", "node_limit");
        if (object == null || object instanceof Number || object instanceof Boolean) return object;
        if (object instanceof String text) return limited(text, remaining);
        if (object instanceof UUID || object instanceof Enum<?>) return object.toString();
        if (depth > 6) return Map.of("class", object.getClass().getName(), "omitted", "depth_limit");
        if (seen.put(object, Boolean.TRUE) != null) return Map.of("reference", object.getClass().getName());
        try {
            if (object instanceof Location location) return location(location);
            if (object instanceof Vector vector) return vector(vector);
            if (object instanceof net.minecraft.server.v1_8_R3.ItemStack item) return item(item, remaining);
            if (object instanceof DataWatcher watcher) return value(watcher.c(), depth + 1, seen, remaining);
            if (object instanceof DataWatcher.WatchableObject watched) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("index", watched.a());
                out.put("type", watched.c());
                out.put("value", value(watched.b(), depth + 1, seen, remaining));
                return out;
            }
            if (object instanceof Entity entity) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("uuid", entity.getUniqueId().toString());
                out.put("name", entity.getName());
                out.put("entityId", entity.getEntityId());
                out.put("type", entity.getType().name());
                out.put("position", location(entity.getLocation()));
                out.put("velocity", vector(entity.getVelocity()));
                out.put("dead", entity.isDead());
                if (entity instanceof LivingEntity living) {
                    out.put("health", living.getHealth());
                    out.put("noDamageTicks", living.getNoDamageTicks());
                }
                if (entity instanceof Player player) {
                    remaining[0] -= 48;
                    out.put("state", player(player, true, remaining));
                }
                if (entity instanceof Projectile projectile)
                    out.put("shooter", value(projectile.getShooter(), depth + 1, seen, remaining));
                return out;
            }
            if (object instanceof ItemStack item) {
                return item(CraftItemStack.asNMSCopy(item), remaining);
            }
            if (object instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                int count = 0;
                for (var entry : map.entrySet()) {
                    if (count++ == 48) {
                        out.put("omitted", "entry_limit");
                        break;
                    }
                    out.put(limited(String.valueOf(entry.getKey()), remaining), value(entry.getValue(), depth + 1, seen, remaining));
                }
                return out;
            }
            if (object instanceof Iterable<?> iterable) {
                List<Object> out = new ArrayList<>();
                for (Object entry : iterable) {
                    if (out.size() == 48) {
                        out.add(Map.of("omitted", "entry_limit"));
                        break;
                    }
                    out.add(value(entry, depth + 1, seen, remaining));
                }
                return out;
            }
            if (object.getClass().isArray()) {
                List<Object> out = new ArrayList<>();
                int length = Array.getLength(object);
                for (int i = 0; i < Math.min(48, length); i++)
                    out.add(value(Array.get(object, i), depth + 1, seen, remaining));
                if (length > 48) out.add(Map.of("omitted", "entry_limit", "length", length));
                return out;
            }
            if (object.getClass().getName().startsWith("java.")) return Map.of("class", object.getClass().getName());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("class", object.getClass().getName());
            for (Class<?> type = object.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                    if (out.size() >= 48) {
                        out.put("omitted", "field_limit");
                        return out;
                    }
                    if (!field.trySetAccessible()) {
                        out.put(field.getName(), Map.of("omitted", "inaccessible"));
                        continue;
                    }
                    out.put(field.getName(), value(field.get(object), depth + 1, seen, remaining));
                }
            }
            return out;
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot read diagnostic snapshot: " + object.getClass().getName(), failure);
        } finally {
            seen.remove(object);
        }
    }

    static boolean containsPlayer(Object object, Set<UUID> watched) {
        return containsPlayer(object, watched, 0, new IdentityHashMap<>(), new int[]{512});
    }

    private static boolean containsPlayer(
            Object object,
            Set<UUID> watched,
            int depth,
            IdentityHashMap<Object, Boolean> seen,
            int[] remaining
    ) {
        if (object == null || --remaining[0] < 0) return false;
        if (object instanceof UUID uuid) return watched.contains(uuid);
        if (object instanceof Entity entity) {
            return watched.contains(entity.getUniqueId()) || entity instanceof Projectile projectile
                    && containsPlayer(projectile.getShooter(), watched, depth + 1, seen, remaining);
        }
        if (depth > 5 || seen.put(object, Boolean.TRUE) != null) return false;
        if (object instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .limit(48)
                    .anyMatch(entry -> containsPlayer(
                            entry.getKey(),
                            watched,
                            depth + 1,
                            seen,
                            remaining
                    )
                            || containsPlayer(entry.getValue(), watched, depth + 1, seen, remaining));
        }
        if (object instanceof Iterable<?> iterable) {
            int count = 0;
            for (Object entry : iterable) {
                if (count++ == 48) break;
                if (containsPlayer(entry, watched, depth + 1, seen, remaining)) return true;
            }
            return false;
        }
        if (object.getClass().isArray()) {
            int length = Math.min(48, Array.getLength(object));
            for (int i = 0; i < length; i++) {
                if (containsPlayer(Array.get(object, i), watched, depth + 1, seen, remaining))
                    return true;
            }
            return false;
        }
        if (object instanceof Number || object instanceof String || object instanceof Boolean || object instanceof Enum<?>
                || object.getClass().getName().startsWith("java.")) return false;
        try {
            for (Class<?> type = object.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic() || !field.trySetAccessible())
                        continue;
                    if (containsPlayer(field.get(object), watched, depth + 1, seen, remaining)) return true;
                }
            }
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot identify diagnostic event actors", failure);
        }
        return false;
    }

    static Map<String, Object> location(Location location) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("world", location.getWorld() == null ? null : location.getWorld().getUID().toString());
        out.put("x", location.getX());
        out.put("y", location.getY());
        out.put("z", location.getZ());
        out.put("yaw", location.getYaw());
        out.put("pitch", location.getPitch());
        return out;
    }

    private static List<Double> vector(Vector vector) {
        return List.of(vector.getX(), vector.getY(), vector.getZ());
    }

    private static String limited(String text, int[] remaining) {
        int length = Math.min(text.length(), Math.max(0, remaining[1]));
        remaining[1] -= length;
        return length == text.length() ? text : text.substring(0, length) + " [omitted:string_budget,originalLength=" + text.length() + "]";
    }
}
