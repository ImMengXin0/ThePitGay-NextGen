package net.mizukilab.pit.platform.paper;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Converts data values from game templates into the flattened material names. */
public final class LegacyItemData {
    private static final String[] COLORS = {"WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME",
            "PINK", "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"};
    private static final String[] DYES = {"INK_SAC", "RED_DYE", "GREEN_DYE", "COCOA_BEANS", "LAPIS_LAZULI",
            "PURPLE_DYE", "CYAN_DYE", "LIGHT_GRAY_DYE", "GRAY_DYE", "PINK_DYE", "LIME_DYE", "YELLOW_DYE",
            "LIGHT_BLUE_DYE", "MAGENTA_DYE", "ORANGE_DYE", "BONE_MEAL"};
    private LegacyItemData() {}

    public static void apply(ItemStack item, int data) {
        String type = item.getType().name();
        if (type.startsWith("WHITE_") && data >= 0 && data < COLORS.length) {
            Material colored = Material.matchMaterial(COLORS[data] + type.substring("WHITE".length()));
            if (colored != null) { item.setType(colored); return; }
        }
        if (item.getType() == Material.INK_SAC && data >= 0 && data < DYES.length) {
            item.setType(Material.valueOf(DYES[data])); return;
        }
        if (item.getType() == Material.COD && data > 0 && data < 4) {
            item.setType(new Material[]{Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH}[data]); return;
        }
        if (item.getType() == Material.PIG_SPAWN_EGG) {
            Material egg = switch (data) {
                case 50 -> Material.CREEPER_SPAWN_EGG;
                case 51 -> Material.SKELETON_SPAWN_EGG;
                case 52 -> Material.SPIDER_SPAWN_EGG;
                case 54 -> Material.ZOMBIE_SPAWN_EGG;
                case 55 -> Material.SLIME_SPAWN_EGG;
                case 56 -> Material.GHAST_SPAWN_EGG;
                case 57 -> Material.ZOMBIFIED_PIGLIN_SPAWN_EGG;
                case 58 -> Material.ENDERMAN_SPAWN_EGG;
                case 59 -> Material.CAVE_SPIDER_SPAWN_EGG;
                case 60 -> Material.SILVERFISH_SPAWN_EGG;
                case 61 -> Material.BLAZE_SPAWN_EGG;
                case 62 -> Material.MAGMA_CUBE_SPAWN_EGG;
                case 65 -> Material.BAT_SPAWN_EGG;
                case 66 -> Material.WITCH_SPAWN_EGG;
                case 67 -> Material.ENDERMITE_SPAWN_EGG;
                case 68 -> Material.GUARDIAN_SPAWN_EGG;
                case 90 -> Material.PIG_SPAWN_EGG;
                case 91 -> Material.SHEEP_SPAWN_EGG;
                case 92 -> Material.COW_SPAWN_EGG;
                case 93 -> Material.CHICKEN_SPAWN_EGG;
                case 94 -> Material.SQUID_SPAWN_EGG;
                case 95 -> Material.WOLF_SPAWN_EGG;
                case 96 -> Material.MOOSHROOM_SPAWN_EGG;
                case 98 -> Material.OCELOT_SPAWN_EGG;
                case 100 -> Material.HORSE_SPAWN_EGG;
                case 101 -> Material.RABBIT_SPAWN_EGG;
                case 120 -> Material.VILLAGER_SPAWN_EGG;
                default -> null;
            };
            if (egg != null) { item.setType(egg); return; }
        }
        if (item.getType().getMaxDurability() > 0) item.setDurability((short) data);
    }
}
