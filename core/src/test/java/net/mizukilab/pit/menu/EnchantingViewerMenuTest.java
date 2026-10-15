package net.mizukilab.pit.menu;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PlayerOption;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.menu.option.PlayerOptionMenu;
import net.mizukilab.pit.menu.option.button.EnchantingVisibilityButton;
import net.mizukilab.pit.menu.viewer.button.EnchantingViewerMenu;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.inventory.InventoryUtil;
import net.mizukilab.pit.util.menu.Button;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EnchantingViewerMenuTest extends BukkitDataTestSupport {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<PlayerProfile> profiles;
    private MockedStatic<PlayerUtil> players;
    private MockedStatic<InventoryUtil> inventory;
    private PlayerProfile profile;
    private PlayerOption option;
    private Player viewer;
    private Player target;
    private UUID targetId;
    private EnchantingViewerMenu menu;

    @Before
    public void setupViewer() {
        bukkit = mockStatic(Bukkit.class);
        profiles = mockStatic(PlayerProfile.class);
        players = mockStatic(PlayerUtil.class);
        inventory = mockStatic(InventoryUtil.class);
        profile = mock(PlayerProfile.class);
        option = new PlayerOption();
        targetId = UUID.randomUUID();
        when(profile.getPlayerUuid()).thenReturn(targetId);
        when(profile.getPlayerName()).thenReturn("Target");
        when(profile.getPlayerOption()).thenReturn(option);
        when(profile.isLoaded()).thenReturn(true);
        viewer = mock(Player.class);
        when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
        target = mock(Player.class);
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.isOnline()).thenReturn(true);
        bukkit.when(() -> Bukkit.getPlayer(targetId)).thenReturn(target);
        profiles.when(() -> PlayerProfile.getRawCache(targetId)).thenReturn(profile);
        menu = new EnchantingViewerMenu(profile);
    }

    @After
    public void teardownViewer() {
        inventory.close();
        players.close();
        profiles.close();
        bukkit.close();
    }

    @Test
    public void privacyDefaultsVisibleAndPersistsForOldAndNewProfiles() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertTrue(option.isEnchantingVisibility());
        assertTrue(mapper.readValue("{}", PlayerOption.class).isEnchantingVisibility());
        option.setEnchantingVisibility(true);
        assertTrue(mapper.readValue(mapper.writeValueAsString(option), PlayerOption.class).isEnchantingVisibility());
        option.setEnchantingVisibility(false);
        assertFalse(mapper.readValue(mapper.writeValueAsString(option), PlayerOption.class).isEnchantingVisibility());
    }

    @Test
    public void ordinaryPlayerCannotTogglePrivacyAndLegacyHiddenSettingBecomesVisible() {
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(viewer.getUniqueId())).thenReturn(profile);
        Button setting = new PlayerOptionMenu().getButtons(viewer).get(22);
        assertTrue(setting instanceof EnchantingVisibilityButton);
        option.setEnchantingVisibility(false);
        setting.getButtonItem(viewer);
        assertTrue(option.isEnchantingVisibility());
        setting.clicked(viewer, 22, ClickType.LEFT, -1, null);
        assertTrue(option.isEnchantingVisibility());
        setting.clicked(viewer, 22, ClickType.RIGHT, -1, null);
        assertTrue(option.isEnchantingVisibility());
    }

    @Test
    public void supporterCanTogglePrivacyAndRenderingPreservesHiddenChoice() {
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(viewer.getUniqueId())).thenReturn(profile);
        when(profile.isSupporter()).thenReturn(true);
        Button setting = new EnchantingVisibilityButton();
        setting.clicked(viewer, 22, ClickType.LEFT, -1, null);
        setting.getButtonItem(viewer);
        assertFalse(option.isEnchantingVisibility());
        setting.clicked(viewer, 22, ClickType.RIGHT, -1, null);
        assertTrue(option.isEnchantingVisibility());
    }

    @Test
    public void staffCanTogglePrivacyWithoutMembership() {
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(viewer.getUniqueId())).thenReturn(profile);
        players.when(() -> PlayerUtil.isStaff(viewer)).thenReturn(true);
        Button setting = new EnchantingVisibilityButton();
        setting.clicked(viewer, 22, ClickType.LEFT, -1, null);
        setting.getButtonItem(viewer);
        assertFalse(option.isEnchantingVisibility());
        setting.clicked(viewer, 22, ClickType.RIGHT, -1, null);
        assertTrue(option.isEnchantingVisibility());
    }

    @Test
    public void membershipExpiryRestoresVisibilityDuringLiveViewing() {
        when(profile.isSupporter()).thenReturn(true);
        option.setEnchantingVisibility(false);
        assertNotNull(menu.getUnavailableReason(viewer));
        assertFalse(menu.getButtons(viewer).containsKey(11));
        assertFalse(option.isEnchantingVisibility());
        when(profile.isSupporter()).thenReturn(false);
        assertNull(menu.getUnavailableReason(viewer));
        assertTrue(menu.getButtons(viewer).containsKey(11));
        assertTrue(option.isEnchantingVisibility());
        option.setEnchantingVisibility(false);
        assertTrue(menu.getButtons(viewer).containsKey(11));
        assertTrue(option.isEnchantingVisibility());
    }

    @Test
    public void staffOwnerCanKeepItemsHiddenWithoutMembership() {
        players.when(() -> PlayerUtil.isStaff(target)).thenReturn(true);
        option.setEnchantingVisibility(false);
        assertNotNull(menu.getUnavailableReason(viewer));
        assertFalse(menu.getButtons(viewer).containsKey(11));
        assertFalse(option.isEnchantingVisibility());
    }

    @Test
    public void privacyBlocksDecodingAndAllowsOwnerOrStaff() {
        when(profile.isSupporter()).thenReturn(true);
        option.setEnchantingVisibility(false);
        when(profile.getEnchantingItem()).thenReturn("private");
        assertNotNull(menu.getUnavailableReason(viewer));
        assertFalse(menu.getButtons(viewer).containsKey(11));
        inventory.verifyNoInteractions();
        assertNull(menu.getUnavailableReason(target));
        when(viewer.hasPermission("pit.admin")).thenReturn(true);
        assertNull(menu.getUnavailableReason(viewer));
        when(viewer.hasPermission("pit.admin")).thenReturn(false);
        players.when(() -> PlayerUtil.isStaff(viewer)).thenReturn(true);
        assertNull(menu.getUnavailableReason(viewer));
    }

    @Test
    public void refreshReadsAllSlotsAndDecodesOnlyChangedItems() {
        option.setEnchantingVisibility(true);
        ItemStack first = decoded("first");
        ItemStack book = decoded("book");
        ItemStack material = decoded("material");
        when(profile.getEnchantingItem()).thenReturn("first");
        when(profile.getEnchantingBook()).thenReturn("book");
        when(profile.getEnchantingScience()).thenReturn("material");
        Map<Integer, Button> buttons = menu.getButtons(viewer);
        assertSame(first.clone(), buttons.get(11).getButtonItem(viewer));
        assertSame(book.clone(), buttons.get(13).getButtonItem(viewer));
        assertSame(material.clone(), buttons.get(15).getButtonItem(viewer));
        assertNotSame(first, buttons.get(11).getButtonItem(viewer));
        menu.getButtons(viewer);
        inventory.verify(() -> InventoryUtil.deserializeItemStack("first"), times(1));
        ItemStack second = decoded("second");
        when(profile.getEnchantingItem()).thenReturn("second");
        when(profile.getEnchantingScience()).thenReturn(null);
        buttons = menu.getButtons(viewer);
        assertSame(second.clone(), buttons.get(11).getButtonItem(viewer));
        assertNotSame(material.clone(), buttons.get(15).getButtonItem(viewer));
        inventory.verify(() -> InventoryUtil.deserializeItemStack("book"), times(1));
        buttons.get(11).clicked(viewer, 11, ClickType.LEFT, -1, first);
        verify(profile, never()).save(any());
        verify(profile, never()).setEnchantingItem(any());
        verify(viewer, never()).getInventory();
        assertTrue(menu.isAutoUpdate());
        assertTrue(menu.isReadOnly());
    }

    @Test
    public void privacySurvivesLogoutAndLatestSessionSurvivesCacheEviction() {
        when(profile.isSupporter()).thenReturn(true);
        option.setEnchantingVisibility(true);
        decoded("first");
        when(profile.getEnchantingItem()).thenReturn("first");
        assertTrue(menu.getButtons(viewer).containsKey(11));
        option.setEnchantingVisibility(false);
        assertFalse(menu.getButtons(viewer).containsKey(11));
        when(target.isOnline()).thenReturn(false);
        assertFalse(menu.getButtons(viewer).containsKey(11));
        profiles.when(() -> PlayerProfile.getRawCache(targetId)).thenReturn(null);
        assertFalse(menu.getButtons(viewer).containsKey(11));
        PlayerProfile reconnected = mock(PlayerProfile.class);
        PlayerOption newOption = new PlayerOption();
        newOption.setEnchantingVisibility(true);
        when(reconnected.isLoaded()).thenReturn(true);
        when(reconnected.getPlayerOption()).thenReturn(newOption);
        when(reconnected.getEnchantingItem()).thenReturn("second");
        ItemStack second = decoded("second");
        when(target.isOnline()).thenReturn(true);
        profiles.when(() -> PlayerProfile.getRawCache(targetId)).thenReturn(reconnected);
        assertSame(second.clone(), menu.getButtons(viewer).get(11).getButtonItem(viewer));
        when(target.isOnline()).thenReturn(false);
        // Quit/save cleanup may change the last profile after its final online refresh.
        ItemStack finalItem = decoded("final");
        when(reconnected.getEnchantingItem()).thenReturn("final");
        assertSame(finalItem.clone(), menu.getButtons(viewer).get(11).getButtonItem(viewer));
        bukkit.when(() -> Bukkit.getPlayer(targetId)).thenReturn(null);
        profiles.when(() -> PlayerProfile.getRawCache(targetId)).thenReturn(null);
        assertSame(finalItem.clone(), menu.getButtons(viewer).get(11).getButtonItem(viewer));
    }

    @Test
    public void offlineSavedProfileDisplaysAllSlotsWithoutLoadingOrWriting() throws Exception {
        PlayerProfile saved = new ObjectMapper().readValue("{\"uuid\":\"" + targetId
                + "\",\"playerName\":\"Target\",\"enchantingItem\":\"saved-item\","
                + "\"enchantingBook\":\"saved-book\",\"enchantingScience\":\"saved-material\"}", PlayerProfile.class);
        assertFalse(saved.isLoaded());
        bukkit.when(() -> Bukkit.getPlayer(targetId)).thenReturn(null);
        profiles.when(() -> PlayerProfile.getRawCache(targetId)).thenReturn(null);
        menu = new EnchantingViewerMenu(saved);
        ItemStack item = decoded("saved-item"), book = decoded("saved-book"), material = decoded("saved-material");
        assertNull(menu.getUnavailableReason(viewer));
        Map<Integer, Button> buttons = menu.getButtons(viewer);
        assertSame(item.clone(), buttons.get(11).getButtonItem(viewer));
        assertSame(book.clone(), buttons.get(13).getButtonItem(viewer));
        assertSame(material.clone(), buttons.get(15).getButtonItem(viewer));
        menu.getButtons(viewer);
        inventory.verify(() -> InventoryUtil.deserializeItemStack("saved-item"), times(1));
        inventory.verify(() -> InventoryUtil.deserializeItemStack("saved-book"), times(1));
        inventory.verify(() -> InventoryUtil.deserializeItemStack("saved-material"), times(1));
        buttons.get(11).clicked(viewer, 11, ClickType.LEFT, -1, item);
        assertEquals("saved-item", saved.getEnchantingItem());
        assertTrue(menu.isReadOnly());
        verify(viewer, never()).getInventory();
        profiles.verify(() -> PlayerProfile.getRawCache(targetId), atLeastOnce());
        profiles.verifyNoMoreInteractions();
        bukkit.verify(() -> Bukkit.getScheduler(), never());
    }

    @Test
    public void offlineHiddenProfilePreservesPrivacyAndOwnerAndStaffAccess() {
        when(profile.isLoaded()).thenReturn(false);
        option.setEnchantingVisibility(false);
        bukkit.when(() -> Bukkit.getPlayer(targetId)).thenReturn(null);
        profiles.when(() -> PlayerProfile.getRawCache(targetId)).thenReturn(null);
        assertNotNull(menu.getUnavailableReason(viewer));
        assertFalse(menu.getButtons(viewer).containsKey(11));
        inventory.verifyNoInteractions();
        assertFalse(option.isEnchantingVisibility());
        verify(profile, never()).isSupporter();
        assertNull(menu.getUnavailableReason(target));
        when(viewer.hasPermission("pit.admin")).thenReturn(true);
        assertNull(menu.getUnavailableReason(viewer));
        when(viewer.hasPermission("pit.admin")).thenReturn(false);
        players.when(() -> PlayerUtil.isStaff(viewer)).thenReturn(true);
        assertNull(menu.getUnavailableReason(viewer));
    }

    @Test
    public void onlineLoadingNeverDisplaysStaleOfflineItems() {
        bukkit.when(() -> Bukkit.getPlayer(targetId)).thenReturn(null);
        profiles.when(() -> PlayerProfile.getRawCache(targetId)).thenReturn(null);
        when(profile.getEnchantingItem()).thenReturn("old");
        ItemStack old = decoded("old");
        assertSame(old.clone(), menu.getButtons(viewer).get(11).getButtonItem(viewer));
        bukkit.when(() -> Bukkit.getPlayer(targetId)).thenReturn(target);
        assertNotNull(menu.getUnavailableReason(viewer));
        assertFalse(menu.getButtons(viewer).containsKey(11));
        PlayerProfile loading = mock(PlayerProfile.class);
        profiles.when(() -> PlayerProfile.getRawCache(targetId)).thenReturn(loading);
        assertFalse(menu.getButtons(viewer).containsKey(11));
        when(loading.isLoaded()).thenReturn(true);
        when(loading.getPlayerOption()).thenReturn(new PlayerOption());
        when(loading.getEnchantingItem()).thenReturn("new");
        ItemStack current = decoded("new");
        assertSame(current.clone(), menu.getButtons(viewer).get(11).getButtonItem(viewer));
    }

    @Test
    public void logoutBeforeFirstRefreshUsesLatestCachedSession() {
        when(profile.getEnchantingItem()).thenReturn("old");
        bukkit.when(() -> Bukkit.getPlayer(targetId)).thenReturn(null);
        PlayerProfile latest = mock(PlayerProfile.class);
        when(latest.isLoaded()).thenReturn(true);
        when(latest.getPlayerOption()).thenReturn(new PlayerOption());
        when(latest.getEnchantingItem()).thenReturn("latest");
        ItemStack item = decoded("latest");
        profiles.when(() -> PlayerProfile.getRawCache(targetId)).thenReturn(latest);
        assertSame(item.clone(), menu.getButtons(viewer).get(11).getButtonItem(viewer));
        profiles.when(() -> PlayerProfile.getRawCache(targetId)).thenReturn(null);
        assertSame(item.clone(), menu.getButtons(viewer).get(11).getButtonItem(viewer));
        inventory.verify(() -> InventoryUtil.deserializeItemStack("old"), never());
    }

    @Test
    public void invalidSlotDoesNotHideOtherItemsOrRetryDecodeEveryRefresh() {
        option.setEnchantingVisibility(true);
        when(profile.getEnchantingItem()).thenReturn("invalid");
        inventory.when(() -> InventoryUtil.deserializeItemStack("invalid")).thenThrow(new IllegalArgumentException("bad data"));
        when(profile.getEnchantingBook()).thenReturn("book");
        ItemStack book = decoded("book");
        assertSame(book.clone(), menu.getButtons(viewer).get(13).getButtonItem(viewer));
        menu.getButtons(viewer);
        inventory.verify(() -> InventoryUtil.deserializeItemStack("invalid"), times(1));
    }

    private ItemStack decoded(String encoded) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(item.clone()).thenReturn(mock(ItemStack.class));
        inventory.when(() -> InventoryUtil.deserializeItemStack(encoded)).thenReturn(item);
        return item;
    }
}
