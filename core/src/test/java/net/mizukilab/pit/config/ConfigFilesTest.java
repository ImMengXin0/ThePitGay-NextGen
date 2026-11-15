package net.mizukilab.pit.config;

import net.mizukilab.pit.util.configuration.ConfigFiles;
import net.mizukilab.pit.util.configuration.Configuration;
import net.mizukilab.pit.util.configuration.annotations.ConfigData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ConfigFilesTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private JavaPlugin plugin;
    @Before public void setup() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder.getRoot());
    }
    private void resource(String path, String yaml) {
        when(plugin.getResource(path)).thenAnswer(call -> new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
    @Test public void upgradesNestedKeysAndPreservesFalseZeroListsAndExtensionGroups() throws Exception {
        var file = folder.newFile("custom.yml");
        Files.writeString(file.toPath(), "enabled: false\namount: 0\nlines: []\nmm-listener:\n  MyBoss:\n    xp: 25\nservice:\n  host: custom\n");
        resource("custom.yml", "enabled: true\namount: 8\nlines: [default]\nservice:\n  host: localhost\n  port: 27017\n");
        var loaded = ConfigFiles.load(plugin, file, "custom.yml");
        assertFalse(loaded.getBoolean("enabled"));
        assertEquals(0, loaded.getInt("amount"));
        assertTrue(loaded.getStringList("lines").isEmpty());
        assertEquals(25, loaded.getInt("mm-listener.MyBoss.xp"));
        assertEquals("custom", loaded.getString("service.host"));
        assertEquals(27017, YamlConfiguration.loadConfiguration(file).getInt("service.port"));
        String once = Files.readString(file.toPath());
        ConfigFiles.load(plugin, file, "custom.yml");
        assertEquals(once, Files.readString(file.toPath()));
    }
    @Test public void malformedYamlAndSectionConflictsNeverOverwriteFiles() throws Exception {
        resource("config.yml", "service:\n  host: localhost\n");
        var file = folder.newFile("config.yml");
        for (String invalid : new String[]{"service: [broken", "service: wrong-type\n"}) {
            Files.writeString(file.toPath(), invalid);
            assertThrows(IllegalStateException.class, () -> ConfigFiles.load(plugin, file, "config.yml"));
            assertEquals(invalid, Files.readString(file.toPath()));
        }
    }
    @Test public void mapFilesUseTheWorldTemplateAndRetainMapCoordinates() throws Exception {
        var worlds = folder.newFolder("worlds");
        var file = new File(worlds, "arena-two.yml");
        Files.writeString(file.toPath(), "worldName: arena-two\nid: 2\nloc:\n  spawn: [custom-coordinates]\n");
        resource("worlds/world.yml", "worldName: world\nid: 1\nloc:\n  spawn: [default]\n  npc:\n    warehouse: example\n");
        var loaded = new ExampleConfig(plugin, file);
        loaded.load();
        var yaml = YamlConfiguration.loadConfiguration(file);
        assertEquals("arena-two", yaml.getString("worldName"));
        assertEquals("custom-coordinates", yaml.getStringList("loc.spawn").get(0));
        assertEquals("example", yaml.getString("loc.npc.warehouse"));
        assertEquals(20, yaml.getInt("new-option"));
    }
    @Test public void reloadReadsDiskAndRestoresOriginalFieldDefaultWhenKeyIsRemoved() throws Exception {
        var file = folder.newFile("extra.yml");
        var config = new ExampleConfig(plugin, file);
        Files.writeString(file.toPath(), "new-option: 9\n");
        config.load();
        assertEquals(9, config.option);
        Files.writeString(file.toPath(), "custom-entry: keep\n");
        config.load();
        assertEquals(20, config.option);
        assertEquals("keep", YamlConfiguration.loadConfiguration(file).getString("custom-entry"));
    }
    public static class ExampleConfig extends Configuration {
        @ConfigData(path = "new-option") int option = 20;
        ExampleConfig(JavaPlugin plugin, File file) { super(plugin, file.getName(), file.getParent()); }
    }
}
