package cn.mythicland.framework.bukkit.bossbar;

import org.bukkit.entity.Player;

public interface BossBarService {
    void show(Player player, String title, double progress);

    void remove(Player player);

    void clear();
}
