package cn.mythicland.framework.bukkit.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class YamlConfig extends AbstractYamlConfig {
    public YamlConfig(Plugin plugin, String fileName) {
        super((JavaPlugin) plugin, fileName);
    }

    @Override
    protected void checkConfig() {
    }

    @Override
    protected boolean synchronizeDefaults() {
        return false;
    }

    public final YamlConfiguration data() {
        return (YamlConfiguration) this.configuration();
    }

    public final void save() {
        this.markChanged();
        this.saveChanges();
    }
}
