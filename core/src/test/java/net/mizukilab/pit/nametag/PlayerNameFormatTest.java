package net.mizukilab.pit.nametag;

import cn.charlotte.pit.data.PlayerProfile;
import me.clip.placeholderapi.PlaceholderAPI;
import net.mizukilab.pit.PaperTestBootstrap;
import net.mizukilab.pit.config.TabConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.*;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PlayerNameFormatTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<PlaceholderAPI> papi;
    private Player player;
    private PlayerProfile profile;
    private PluginManager plugins;
    private final TabConfiguration config = TabConfiguration.INSTANCE;
    private String oldList, oldPrefix, oldSuffix;
    private boolean oldListEnabled, oldNameEnabled;
    @Before public void setup() {
        oldList = config.getPlayerListFormat(); oldPrefix = config.getNametagPrefix(); oldSuffix = config.getNametagSuffix();
        oldListEnabled = config.getPlayerListEnabled(); oldNameEnabled = config.getNametagEnabled();
        config.setPlayerListEnabled(true); config.setNametagEnabled(true);
        config.setPlayerListFormat("{prefix}{name}{suffix}");
        config.setNametagPrefix("{prefix}"); config.setNametagSuffix("{suffix}");
        bukkit = PaperTestBootstrap.mockBukkit();
        plugins = mock(PluginManager.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        papi = mockStatic(PlaceholderAPI.class);
        try (var warehouses = mockConstruction(cn.charlotte.pit.data.sub.PlayerWarehouse.class);
             var ender = mockConstruction(cn.charlotte.pit.data.sub.PlayerEnderChest.class)) {
            profile = mock(PlayerProfile.class);
        }
        player = mock(Player.class);
        when(player.getName()).thenReturn("Tester");
        var listName = new AtomicReference<>("Tester");
        when(player.getPlayerListName()).thenAnswer(call -> listName.get());
        doAnswer(call -> { String text = call.getArgument(0); listName.set(text == null ? "Tester" : text); return null; })
                .when(player).setPlayerListName(nullable(String.class));
    }
    @After public void cleanup() {
        if (player != null) PlayerNameFormat.reset(player);
        if (papi != null) papi.close();
        if (bukkit != null) bukkit.close();
        config.setPlayerListFormat(oldList); config.setNametagPrefix(oldPrefix); config.setNametagSuffix(oldSuffix);
        config.setPlayerListEnabled(oldListEnabled); config.setNametagEnabled(oldNameEnabled);
    }
    @Test public void defaultsRetainEventAndBountyTextWithoutOldSixteenCharacterTruncation() {
        String prefix = "§d§l登峰造极 400 §6[120] ";
        var plate = PlayerNameFormat.apply("200", player, profile, prefix, " §6§l5000g");
        assertEquals(prefix + "Tester §6§l5000g", player.getPlayerListName());
        assertEquals(prefix, plate.getPrefix());
        PlayerNameFormat.apply("200", player, profile, prefix, " §6§l5000g");
        verify(player, times(1)).setPlayerListName(anyString());
        papi.verifyNoInteractions();
    }
    @Test public void tabAndNametagUseSeparateFormatsAndResolvePapiWithSubjectPlayerAndColors() {
        config.setPlayerListFormat("{name} &7[%server_rank%]");
        config.setNametagPrefix("&a%server_rank% "); config.setNametagSuffix(" &eVIP");
        when(plugins.isPluginEnabled("PlaceholderAPI")).thenReturn(true);
        papi.when(() -> PlaceholderAPI.setPlaceholders(player, "Tester &7[%server_rank%]")).thenReturn("Tester &7[&6Gold]");
        papi.when(() -> PlaceholderAPI.setPlaceholders(player, "&a%server_rank% ")).thenReturn("&aGold ");
        var plate = PlayerNameFormat.apply("200", player, profile, "legacy", "legacy");
        assertEquals("Tester §7[§6Gold]", player.getPlayerListName());
        assertEquals("§aGold ", plate.getPrefix()); assertEquals(" §eVIP", plate.getSuffix());
    }
    @Test public void localNameAndLevelRespectNickAndMissingPapiDoesNotPreventFormatting() {
        when(profile.isNicked()).thenReturn(true); when(profile.getNickName()).thenReturn("Alias");
        when(profile.getFormattedLevelTagTabSpec()).thenReturn("§7[4]");
        assertEquals("§7[4] Alias %player_ping%", PlayerNameFormat.render("{level} {name} %player_ping%", player, profile, "", ""));
        papi.verifyNoInteractions();
    }
    @Test public void disablingRestoresOwnedTabNamesAndClearsNametagDecoration() {
        PlayerNameFormat.apply("200", player, profile, "prefix ", " suffix");
        config.setPlayerListEnabled(false); config.setNametagEnabled(false);
        var plate = PlayerNameFormat.apply("200", player, profile, "prefix ", " suffix");
        assertEquals("Tester", player.getPlayerListName());
        assertEquals("", plate.getPrefix()); assertEquals("", plate.getSuffix());
        player.setPlayerListName("OtherPlugin");
        PlayerNameFormat.apply("200", player, profile, "", "");
        assertEquals("OtherPlugin", player.getPlayerListName());
    }
}
