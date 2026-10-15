package net.mizukilab.pit.command;

import cn.charlotte.pit.ThePit;
import dev.rollczi.litecommands.LiteCommands;
import dev.rollczi.litecommands.bukkit.BukkitCommandsRegistry;
import dev.rollczi.litecommands.bukkit.LiteBukkitFactory;
import dev.rollczi.litecommands.bukkit.LiteBukkitSettings;
import dev.rollczi.litecommands.bukkit.PitTabComplete;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.config.ConfigManager;
import net.mizukilab.pit.config.PitWorldConfig;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.enchantment.UnknownEnchantment;
import net.mizukilab.pit.item.ItemFactor;
import net.mizukilab.pit.map.MapSelector;
import net.mizukilab.pit.util.sound.SoundFactory;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Modifier;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Runs actual core command annotations through LiteCommands and Bukkit Command.tabComplete. */
public class CommandCompletionIntegrationTest extends BukkitDataTestSupport {
    private final Map<String, Command> commands = new HashMap<>();
    private final List<Player> online = new ArrayList<>();
    private MockedStatic<ThePit> pit;
    private MockedStatic<Bukkit> bukkit;
    private ThePit plugin;
    private LiteCommands<CommandSender> lite;
    private Player admin, alice, bob;
    private EnchantmentFactor enchants;
    private ItemFactor items;
    private ConfigManager config;

    @Before
    public void registerActualCoreCommands() throws Exception {
        pit = mockStatic(ThePit.class);
        bukkit = mockStatic(Bukkit.class);
        plugin = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        Server delegate = mock(Server.class);
        Server server = serverWithOnlinePlayers(delegate, online);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getName()).thenReturn("ThePitUltimate");
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        PluginManager plugins = mock(PluginManager.class);
        when(delegate.getPluginManager()).thenReturn(plugins);
        when(plugins.getPlugins()).thenReturn(new Plugin[]{plugin});
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(delegate.getScheduler()).thenReturn(scheduler);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(delegate.getPlayer(anyString())).thenAnswer(call -> online.stream()
                .filter(player -> player.getName().equalsIgnoreCase(call.getArgument(0))).findFirst().orElse(null));
        admin = player("Admin", true);
        alice = player("Alice", false);
        bob = player("Bob", false);
        online.addAll(List.of(admin, alice, bob));
        // Delegate without copying the factor: its revision callback captures the owning instance.
        enchants = mock(EnchantmentFactor.class, org.mockito.AdditionalAnswers.delegatesTo(new EnchantmentFactor()));
        when(plugin.getEnchantmentFactor()).thenReturn(enchants);
        enchants.getEnchantmentMap().put("custom_enchant", new TieredEnchant("custom_enchant", 3));
        items = new ItemFactor();
        when(plugin.getItemFactor()).thenReturn(items);
        items.getItemMap().put("example_item", null); // Completion must use names without constructing items.
        config = mock(ConfigManager.class);
        when(plugin.getConfigManager()).thenReturn(config);
        PitWorldConfig world = mock(PitWorldConfig.class);
        when(world.getId()).thenReturn(2);
        when(world.getWorldName()).thenReturn("castle");
        when(config.getPitConfigs()).thenReturn(new HashMap<>(Map.of(2, world)));
        SoundFactory sounds = mock(SoundFactory.class);
        when(plugin.getSoundFactory()).thenReturn(sounds);
        when(sounds.getSoundNames()).thenReturn(Set.of("pit_music"));
        BukkitCommandsRegistry registry = new BukkitCommandsRegistry() {
            public boolean register(String name, String prefix, Command command) {
                commands.put(name.toLowerCase(Locale.ROOT), command);
                for (String alias : command.getAliases()) commands.put(alias.toLowerCase(Locale.ROOT), command);
                return true;
            }
            public void unregister(String name, String prefix) { commands.remove(name.toLowerCase(Locale.ROOT)); }
        };
        LiteBukkitSettings settings = new LiteBukkitSettings().nativePermissions(true)
                .commandsRegistry(registry);
        var builder = LiteBukkitFactory.builder(plugin, server, settings);
        builder.settings(PitTabComplete::install);
        PitCommandSuggestions.register(builder, plugin);
        lite = builder.commands(new PitCommands(), new PitAdminCommands(), new PitAdminSimpleCommand(),
                new PitItemCommands(), new PitAdminDupeFixCommands()).build();
    }

    @After
    public void closeCommands() {
        if (lite != null) lite.unregister();
        if (bukkit != null) bukkit.close();
        if (pit != null) pit.close();
    }

    private Player player(String name, boolean privileged) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.canSee(any(Player.class))).thenReturn(true);
        when(player.hasPermission(anyString())).thenReturn(privileged);
        return player;
    }

    private List<String> complete(CommandSender sender, String label, String... args) {
        Command command = commands.get(label.toLowerCase(Locale.ROOT));
        assertNotNull("Actual core command not registered: " + label, command);
        List<String> result = command.tabComplete(sender, label, args);
        assertEquals("No duplicate results", new HashSet<>(result).size(), result.size());
        return result;
    }

    @Test
    public void unregisteringCommandsDisablesNativeCompletion() {
        Command command = commands.get("view");
        assertEquals(List.of("Alice"), command.tabComplete(admin, "view", new String[]{"ali"}));
        lite.unregister();
        lite = null;
        assertTrue(command.tabComplete(admin, "view", new String[]{"ali"}).isEmpty());
    }

    @Test
    public void playerNamesRespectPrefixVisibilityAndChangesWithoutProfileQueries() {
        assertEquals(List.of("Alice"), complete(admin, "view", "aL"));
        assertEquals(List.of("Alice"), complete(admin, "trade", "AL"));
        when(admin.canSee(alice)).thenReturn(false);
        assertTrue(complete(admin, "view", "Ali").isEmpty());
        assertTrue(complete(admin, "trade", "Ali").isEmpty());
        online.remove(bob);
        assertTrue(complete(admin, "view", "B").isEmpty());
        online.add(player("Bobby", false));
        assertEquals(List.of("Bobby"), complete(admin, "offer", "b"));
        verify(plugin, never()).getMongoDB();
        verify(plugin, never()).getProfileOperator();
    }

    @Test
    public void freeTextHasNoPlaceholderOrPlayerLeakAndPermissionsStillApply() {
        assertEquals(List.of(), complete(admin, "rename", ""));
        assertTrue(complete(admin, "cdk", "").isEmpty());
        assertTrue(complete(admin, "searchItem", "").isEmpty());
        assertFalse(commands.get("pit").testPermissionSilent(alice));
        assertTrue(complete(alice, "pit", "changeItemInHand", "ench", "").isEmpty());
        assertTrue(complete(alice, "event", "").isEmpty());
        assertTrue(complete(alice, "sis", "").isEmpty());
    }

    @Test
    public void nestedSubcommandsShortcutsAndArgumentKeysStayDistinct() {
        assertTrue(complete(admin, "pit", "changeItemInHand", "").containsAll(List.of("ench", "color", "tier")));
        assertEquals(List.of("a"), complete(admin, "pit", "pitLoc", "a"));
        assertEquals(List.of("abounty"), complete(admin, "pit", "change", "Alice", "a"));
        assertEquals(List.of("infNpc"), complete(admin, "pit", "debug", "in"));
        assertEquals(List.of("example_item"), complete(admin, "pit", "internalName", "ex"));
        assertEquals(List.of("Alice"), complete(admin, "rollback", "ali"));
        assertEquals(List.of("Alice"), complete(admin, "sis", "ali"));
        assertTrue(complete(admin, "pit", "saveAll", "").containsAll(List.of("true", "false")));
    }

    @Test
    public void enchantRegistryCacheUpdatesOnReplacementAndStableTypingReusesIt() {
        assertEquals(List.of("custom_enchant"), complete(admin, "pit", "changeItemInHand", "ench", "CUSTOM"));
        clearInvocations(enchants);
        for (int i = 0; i < 20; i++) complete(admin, "pit", "changeItemInHand", "ench", "cu");
        verify(enchants, never()).getEnchantmentMap();
        assertEquals(Set.of("0", "1", "2", "3"), new HashSet<>(complete(admin, "pit", "changeItemInHand", "ench", "custom_enchant", "")));
        enchants.getEnchantmentMap().put("custom_enchant", new TieredEnchant("custom_enchant", 1));
        assertEquals(Set.of("0", "1"), new HashSet<>(complete(admin, "pit", "changeItemInHand", "ench", "custom_enchant", "")));
        enchants.getEnchantmentMap().remove("custom_enchant");
        assertTrue(complete(admin, "pit", "changeItemInHand", "ench", "cu").isEmpty());
        assertTrue(complete(admin, "pit", "changeItemInHand", "ench", "custom_enchant", "").isEmpty());
    }

    @Test
    public void itemAndConfigurationSuggestionsComeFromCurrentRegistries() {
        assertEquals(List.of("example_item"), complete(admin, "pit", "give", "ex"));
        items.getItemMap().put("extra_item", null);
        assertEquals(Set.of("example_item", "extra_item"), new HashSet<>(complete(admin, "pit", "give", "ex")));
        assertEquals(List.of("mythic_leggings:red"), complete(admin, "giveitems", "Alice", "mythic_leggings:re"));
        assertTrue(complete(admin, "giveitems", "Alice", "").containsAll(List.of("coins", "exp", "extra_item")));
        assertEquals(List.of("RED"), complete(admin, "pit", "changeItemInHand", "color", "re"));
        assertEquals(List.of("2"), complete(admin, "swm", ""));
        assertEquals(List.of("castle"), complete(admin, "lookupSWM", "ca"));
        assertEquals(List.of("pit_music"), complete(admin, "pit", "testSound", "pit"));
        verify(config, never()).getSelectedWorldConfig();
        verify(config, never()).getGlobal();
    }

    @Test
    public void menuEventAndPluginSuggestionsMatchCoreEntrypoints() {
        assertEquals(Set.of("admin_enchant", "admin_item"), new HashSet<>(complete(admin, "openMenu", "admin_")));
        assertEquals(List.of("ender_chest"), complete(admin, "openMenu", "end"));
        assertEquals(List.of("dragon_egg"), complete(admin, "event", "dra"));
        assertEquals(List.of("dragon_egg"), complete(admin, "pit", "event", "dra"));
        assertEquals(List.of("ThePitUltimate"), complete(admin, "disablePlugin", "thepit"));
        assertEquals(Set.of("0", "1", "2", "3"), new HashSet<>(complete(admin, "pit", "changeItemInHand", "tier", "")));
    }

    @Test
    public void completedMapNameAndOptionalMapIdCanActuallyBeUsed() {
        MapSelector selector = mock(MapSelector.class);
        when(plugin.getMapSelector()).thenReturn(selector);
        PitAdminCommands command = new PitAdminCommands();
        command.lookupMap(admin, complete(admin, "lookupSWM", "ca").get(0));
        verify(selector).switchMapIndexed(2);
        command.switchMap(admin, null);
        verify(selector).switchMap();
    }

    public static class TieredEnchant extends UnknownEnchantment {
        private final int max;
        TieredEnchant(String name, int max) { super(name); this.max = max; }
        @Override public int getMaxEnchantLevel() { return max; }
    }

    private static Server serverWithOnlinePlayers(Server delegate, List<Player> players) throws Exception {
        // Bukkit 1.8 has return-type-only Collection/Player[] overloads; Mockito cannot stub them separately.
        String name = "net/mizukilab/pit/command/CompletionServerStub";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", new String[]{"org/bukkit/Server"});
        writer.visitField(Opcodes.ACC_PUBLIC, "delegate", "Lorg/bukkit/Server;", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC, "players", "Ljava/util/Collection;", null, null).visitEnd();
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode(); constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(0, 0); constructor.visitEnd();
        for (var method : Server.class.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) continue;
            String descriptor = Type.getMethodDescriptor(method);
            var code = writer.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), descriptor, null, null);
            code.visitCode(); code.visitVarInsn(Opcodes.ALOAD, 0);
            if (method.getName().equals("getOnlinePlayers")) {
                code.visitFieldInsn(Opcodes.GETFIELD, name, "players", "Ljava/util/Collection;");
                if (method.getReturnType().isArray()) {
                    code.visitInsn(Opcodes.ICONST_0); code.visitTypeInsn(Opcodes.ANEWARRAY, "org/bukkit/entity/Player");
                    code.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Collection", "toArray", "([Ljava/lang/Object;)[Ljava/lang/Object;", true);
                    code.visitTypeInsn(Opcodes.CHECKCAST, "[Lorg/bukkit/entity/Player;");
                }
            } else {
                code.visitFieldInsn(Opcodes.GETFIELD, name, "delegate", "Lorg/bukkit/Server;");
                int slot = 1;
                for (Type type : Type.getArgumentTypes(descriptor)) {
                    code.visitVarInsn(type.getOpcode(Opcodes.ILOAD), slot); slot += type.getSize();
                }
                code.visitMethodInsn(Opcodes.INVOKEINTERFACE, "org/bukkit/Server", method.getName(), descriptor, true);
            }
            code.visitInsn(Type.getReturnType(descriptor).getOpcode(Opcodes.IRETURN));
            code.visitMaxs(0, 0); code.visitEnd();
        }
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        Class<?> type = new ClassLoader(Server.class.getClassLoader()) {
            Class<?> define() { return defineClass(null, bytes, 0, bytes.length); }
        }.define();
        Server server = (Server) type.getConstructor().newInstance();
        type.getField("delegate").set(server, delegate);
        type.getField("players").set(server, players);
        return server;
    }
}
