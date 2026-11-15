package net.mizukilab.pit.sound.impl

import net.mizukilab.pit.util.sound.AbstractPitSound
import nya.Skip
import org.bukkit.Sound
import org.bukkit.entity.Player

/**
 * 地狱爆炸音效 - 炽热的火焰爆发
 */
@Skip
object InfernoBlastSound : AbstractPitSound() {
    override fun getMusicInternalName(): String {
        return "inferno_blast"
    }

    override fun onSoundTick(player: Player, tick: Int) {
        when (tick) {
            0 -> {
                player.playSound(player.location, Sound.ITEM_FLINTANDSTEEL_USE, 2.0f, 0.5f)
                player.playSound(player.location, Sound.ENTITY_GHAST_SHOOT, 1.0f, 0.8f)
            }
            2 -> player.playSound(player.location, Sound.BLOCK_FIRE_AMBIENT, 1.5f, 1.2f)
            4 -> {
                player.playSound(player.location, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.7f)
                player.playSound(player.location, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 1.0f)
            }
            6 -> player.playSound(player.location, Sound.BLOCK_LAVA_POP, 1.8f, 1.5f)
            8 -> {
                player.playSound(player.location, Sound.ENTITY_BLAZE_HURT, 1.0f, 0.8f)
                player.playSound(player.location, Sound.BLOCK_FIRE_EXTINGUISH, 2.0f, 0.5f)
            }
            10 -> player.playSound(player.location, Sound.BLOCK_LAVA_AMBIENT, 1.5f, 1.0f)
            12 -> player.playSound(player.location, Sound.ITEM_FLINTANDSTEEL_USE, 1.0f, 1.8f)
        }

        if (tick > 16) {
            end(player)
        }
    }
}
