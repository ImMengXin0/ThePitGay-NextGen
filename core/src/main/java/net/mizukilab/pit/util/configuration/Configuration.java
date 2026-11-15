package net.mizukilab.pit.util.configuration;

import net.mizukilab.pit.util.configuration.annotations.ConfigData;
import net.mizukilab.pit.util.configuration.annotations.ConfigSerializer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Configuration {

    private YamlConfiguration config;
    private final JavaPlugin plugin;
    private final File file;
    private final File directory;
    private YamlConfiguration fieldDefaults;

    public Configuration(JavaPlugin plugin) {
        this(plugin, "config.yml");
    }

    public Configuration(JavaPlugin plugin, String filename) {
        this(plugin, filename, plugin.getDataFolder().getPath());
    }
    public Configuration(JavaPlugin plugin, String filename, String directory,boolean pathTo) {
        this(plugin,filename,plugin.getDataFolder().getPath() + "/" + directory);
    }
    public Configuration(JavaPlugin plugin, String filename, String directory) {
        this.plugin = plugin;
        this.directory = new File(directory);
        this.file = new File(directory, filename);
        this.config = new YamlConfiguration();
        this.createFile();
    }

    public void createFile() {
        if (!this.directory.exists()) {
            this.directory.mkdirs();
        }

        if (!this.file.exists()) {
            try {
                this.file.createNewFile();
            } catch (IOException var3) {
                var3.printStackTrace();
            }
        }

        try {
            this.config.load(this.file);
        } catch (InvalidConfigurationException | IOException var2) {
            var2.printStackTrace();
        }

    }

    public void save() {
        Field[] toSave = this.getClass().getDeclaredFields();

        for (Field f : toSave) {
            if (f.isAnnotationPresent(ConfigData.class)) {
                ConfigData configData = f.getAnnotation(ConfigData.class);

                try {
                    f.setAccessible(true);
                    Object saveValue = f.get(this);
                    Object configValue = null;
                    if (f.isAnnotationPresent(ConfigSerializer.class)) {
                        ConfigSerializer serializer = f.getAnnotation(ConfigSerializer.class);
                        if (saveValue instanceof List) {
                            configValue = new ArrayList<>();

                            for (Object o : (List) saveValue) {
                                AbstractSerializer as = (AbstractSerializer) serializer.serializer().newInstance();
                                ((List) configValue).add(as.toString(o));
                            }
                        } else {
                            AbstractSerializer as = (AbstractSerializer) serializer.serializer().newInstance();
                            configValue = as.toString(saveValue);
                        }
                    } else if (saveValue instanceof List) {
                        configValue = new ArrayList<>();
                        Iterator var15 = ((List) saveValue).iterator();

                        while (var15.hasNext()) {
                            Object o = var15.next();
                            ((List) configValue).add(o.toString());
                        }
                    }

                    if (configValue == null) {
                        configValue = saveValue;
                    }

                    this.config.addDefault(configData.path(), configValue);
                    this.config.set(configData.path(), configValue);
                    System.out.println("Setting: " + configData.path() + " to " + saveValue);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            this.config.save(this.file);
        } catch (IOException var13) {
            var13.printStackTrace();
        }

    }

    public void load() {
        String resource = this.file.getName();
        if (this.directory.getName().equals("worlds")) resource = "worlds/world.yml";
        this.config = ConfigFiles.load(this.plugin, this.file, resource);
        if (this.fieldDefaults == null) this.fieldDefaults = captureFieldDefaults();
        if (cn.mythicland.framework.bukkit.config.YamlConfigSynchronizer.backfill(this.config, this.fieldDefaults)) {
            try {
                this.config.save(this.file);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot update configuration: " + this.file, exception);
            }
        }
        Field[] toLoad = this.getClass().getDeclaredFields();
        int var3 = toLoad.length;

        for (Field f : toLoad) {
            try {
                f.setAccessible(true);
                if (f.isAnnotationPresent(ConfigData.class)) {
                    ConfigData configData = f.getAnnotation(ConfigData.class);
                    f.setAccessible(true);
                    if (this.config.contains(configData.path())) {
                        if (!f.isAnnotationPresent(ConfigSerializer.class)) {
                            try {
                                if (this.config.isList(configData.path())) {
                                    f.set(this, this.config.getList(configData.path(), new ArrayList<>()));
                                } else {
                                    f.set(this, this.config.get(configData.path()));
                                }
                            } catch (IllegalAccessException var11) {
                                var11.printStackTrace();
                            }
                        } else if (this.config.isList(configData.path())) {
                            try {
                                List<String> list = this.config.getStringList(configData.path());
                                List<Object> deserializedList = new ArrayList<>();

                                for (String s : list) {
                                    deserializedList.add(this.deserializeValue(f, s));
                                }

                                f.set(this, deserializedList);
                            } catch (InstantiationException | IllegalAccessException var13) {
                                System.out.println("Error reading list in configuration file: " + this.config.getName() + " path: " + configData.path());
                                var13.printStackTrace();
                            }
                        } else {
                            if (List.class.isAssignableFrom(f.getType()) && this.config.get(configData.path()) == null) {
                                f.set(this, new ArrayList<>());
                            } else {
                                try {
                                    Object object = this.config.get(configData.path());
                                    f.set(this, this.deserializeValue(f, object.toString()));
                                } catch (InstantiationException | IllegalAccessException var12) {
                                    System.out.println("Error reading value in configuration file: " + this.config.getName() + " path: " + configData.path());
                                    var12.printStackTrace();
                                }
                            }
                        }
                    } else {
                        if (f.isAnnotationPresent(ConfigSerializer.class)) {
                            try {
//                              Object object = this.config.get(configData.path());
                                f.set(this, this.deserializeValue(f, null));
                            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException var12) {
                                var12.printStackTrace();
                                System.out.println("Error reading value in configuration file: " + this.config.getName() + " path: " + configData.path());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public File getFile() {
        return this.file;
    }

    private YamlConfiguration captureFieldDefaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        for (Field field : getClass().getDeclaredFields()) {
            ConfigData data = field.getAnnotation(ConfigData.class);
            if (data == null) continue;
            try {
                field.setAccessible(true);
                Object value = field.get(this);
                // Null and sentinel coordinates have no canonical default on disk.
                if (value == null || value instanceof Double number && !Double.isFinite(number)
                        || value instanceof Integer intNumber && intNumber == Integer.MIN_VALUE) continue;
                if (field.isAnnotationPresent(ConfigSerializer.class)) {
                    AbstractSerializer serializer = (AbstractSerializer) field.getAnnotation(ConfigSerializer.class).serializer().newInstance();
                    if (value instanceof List<?> values) {
                        List<String> serialized = new ArrayList<>();
                        for (Object entry : values) serialized.add(serializer.toString(entry));
                        value = serialized;
                    } else value = serializer.toString(value);
                }
                defaults.set(data.path(), value);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot read default for " + data.path(), exception);
            }
        }
        return defaults;
    }

    public Object deserializeValue(Field f, Object value) throws IllegalAccessException, InstantiationException {
        AbstractSerializer serializer = (AbstractSerializer) f.getAnnotation(ConfigSerializer.class).serializer().newInstance();
        if (value == null) {
            return serializer.fromString(null);
        } else {
            return serializer.fromString(value.toString());
        }
    }
}
