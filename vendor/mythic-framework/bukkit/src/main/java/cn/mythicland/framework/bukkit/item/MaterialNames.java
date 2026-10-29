package cn.mythicland.framework.bukkit.item;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.Locale;

public final class MaterialNames {
    private MaterialNames() {
    }

    public static Material firstAvailable(String preferredName, Material fallback) {
        return firstAvailable(fallback, preferredName);
    }

    public static Material firstAvailable(
            String preferredName,
            String alternateName,
            Material fallback
    ) {
        Material material = find(preferredName);
        if (material != null) return material;
        material = find(alternateName);
        if (material != null) return material;
        return fallback;
    }

    public static Material firstAvailable(Material fallback, String... preferredNames) {
        if (preferredNames == null) return fallback;
        for (String preferredName : preferredNames) {
            Material material = find(preferredName);
            if (material != null) return material;
        }
        return fallback;
    }

    public static Material requireAny(String usage, String... exactMaterialNames) {
        if (exactMaterialNames != null) {
            for (String exactMaterialName : exactMaterialNames) {
                Material material = find(exactMaterialName);
                if (material != null) return material;
            }
        }
        throw new IllegalStateException(
                "Required material for " + usage + " is unavailable. Expected one of: "
                        + Arrays.toString(exactMaterialNames)
        );
    }

    public static Material match(String rawName) {
        String materialName = rawName == null ? "" : rawName.trim();
        if (materialName.isEmpty()) {
            return null;
        }

        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.matchMaterial(materialName.toUpperCase(Locale.ROOT));
        }
        return material;
    }

    private static Material find(String preferredName) {
        if (preferredName == null || preferredName.isBlank()) return null;
        try {
            return Material.valueOf(preferredName);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
