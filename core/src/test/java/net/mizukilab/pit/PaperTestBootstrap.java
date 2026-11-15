package net.mizukilab.pit;

import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.resources.*;
import net.minecraft.server.*;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.server.packs.resources.*;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.*;
import org.bukkit.craftbukkit.*;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.junit.platform.launcher.*;
import java.util.*;
import java.util.stream.Stream;
import static org.mockito.Mockito.*;

/** Vanilla registry setup, without starting a server, opening sockets or loading worlds.
 * Follows Paper's org.bukkit.support.RegistryHelper for the target server version.
 */
public final class PaperTestBootstrap implements LauncherSessionListener {
    private static boolean initialized;
    @Override public void launcherSessionOpened(LauncherSession session) { initialize(); }
    public static org.mockito.MockedStatic<org.bukkit.Bukkit> mockBukkit() {
        var mock = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class, CALLS_REAL_METHODS);
        mock.when(org.bukkit.Bukkit::getUnsafe).thenReturn(CraftMagicNumbers.INSTANCE);
        mock.when(org.bukkit.Bukkit::getItemFactory).thenReturn(org.bukkit.craftbukkit.inventory.CraftItemFactory.instance());
        return mock;
    }
    public static synchronized void initialize() {
        if (initialized) return;
        net.minecraft.SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var features = FeatureFlags.DEFAULT_FLAGS;
        {
            var repository = ServerPacksSource.createVanillaTrustedRepository();
            MinecraftServer.configurePackRepository(repository,
                    new WorldDataConfiguration(DataPackConfig.DEFAULT, features), true, false);
            var resources = new MultiPackResourceManager(PackType.SERVER_DATA, repository.openAllSelected());
            var layers = RegistryLayer.createRegistryAccess();
            var tags = TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC));
            var lookups = TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.WORLDGEN), tags);
            var worldgen = RegistryDataLoader.load(resources, lookups, RegistryDataLoader.WORLDGEN_REGISTRIES);
            layers = layers.replaceFrom(RegistryLayer.WORLDGEN, worldgen);
            var dimensions = RegistryDataLoader.load(resources,
                    Stream.concat(lookups.stream(), worldgen.listRegistries()).toList(), RegistryDataLoader.DIMENSION_REGISTRIES);
            layers = layers.replaceFrom(RegistryLayer.DIMENSIONS, dimensions);
            var data = ReloadableServerResources.loadResources(resources, layers, tags, features,
                    Commands.CommandSelection.DEDICATED, LevelBasedPermissionSet.ALL_PERMISSIONS,
                    net.minecraft.util.Util.backgroundExecutor(), Runnable::run).join();
            data.updateStaticRegistryTags();
            RegistryAccess.Frozen registries = layers.compositeAccess().freeze();
            CraftRegistry.setMinecraftRegistry(registries);
            var minecraft = mock(net.minecraft.server.dedicated.DedicatedServer.class);
            when(minecraft.registryAccess()).thenReturn(registries);
            try {
                var singleton = MinecraftServer.class.getDeclaredField("SERVER");
                singleton.setAccessible(true); singleton.set(null, minecraft);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            CraftServer bukkit = mock(CraftServer.class);
            when(bukkit.getLogger()).thenReturn(java.util.logging.Logger.getLogger("paper-tests"));
            when(bukkit.getName()).thenReturn("Paper-test");
            when(bukkit.getVersion()).thenReturn("Paper-test (MC: 1.21.11)");
            when(bukkit.getMinecraftVersion()).thenReturn("1.21.11");
            when(bukkit.getBukkitVersion()).thenReturn("1.21.11-R0.1-SNAPSHOT");
            when(bukkit.getUnsafe()).thenReturn(CraftMagicNumbers.INSTANCE);
            when(bukkit.getServer()).thenReturn(minecraft);
            when(bukkit.getItemFactory()).thenReturn(org.bukkit.craftbukkit.inventory.CraftItemFactory.instance());
            org.bukkit.Bukkit.setServer(bukkit);
        }
        initialized = true;
    }
}
