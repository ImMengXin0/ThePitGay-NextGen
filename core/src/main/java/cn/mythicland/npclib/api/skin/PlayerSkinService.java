/*
 * Copyright (c) 2018 Jitse Boonstra
 */

package cn.mythicland.npclib.api.skin;

import com.mojang.authlib.properties.Property;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class PlayerSkinService {
    public Optional<Skin> resolve(Player player) {
        if (!(player instanceof CraftPlayer craftPlayer)) return Optional.empty();
        return craftPlayer.getProfile().properties().get("textures").stream()
                .filter(property -> property.value() != null && !property.value().isBlank())
                .filter(property -> property.signature() != null && !property.signature().isBlank())
                .findFirst()
                .map(property -> new Skin(property.value(), property.signature()));
    }
}
