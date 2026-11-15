package net.mizukilab.pit.sound.impl

import net.mizukilab.pit.util.sound.AbstractPitSound
import nya.Skip
import org.bukkit.Sound
import org.bukkit.entity.Player

/**
 * 雷击音效 - 模拟雷电轰鸣声
 */
@Skip
object ThunderStrikeSound : AbstractPitSound() {
    override fun getMusicInternalName(): String {
        return "thunder_strike"
    }

    override fun onSoundTick(player: Player, tick: Int) {
        when (tick) {
            0 -> {
                player.playSound(player.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.8f)
                player.playSound(player.location, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.5f)
            }
            3 -> {
                player.playSound(player.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 1.2f)
            }
            6 -> {
                player.playSound(player.location, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 2.0f)
            }
            8 -> {
                player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 1.8f)
            }
        }

        if (tick > 12) {
            end(player)
        }
    }
}
