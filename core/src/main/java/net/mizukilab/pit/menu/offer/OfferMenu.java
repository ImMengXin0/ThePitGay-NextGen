package net.mizukilab.pit.menu.offer;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.OfferData;
import net.mizukilab.pit.menu.offer.button.OfferButton;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.menu.Button;
import net.mizukilab.pit.util.menu.Menu;
import net.mizukilab.pit.util.menu.buttons.DisplayButton;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.*;

public class OfferMenu extends Menu {
    private final Player target;
    private final OfferData offer;
    public OfferMenu(Player target) {
        this.target = target;
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(target.getUniqueId());
        this.offer = profile.isLoaded() ? profile.getOfferData() : null;
    }
    @Override public String getTitle(Player player) { return "来自 " + target.getName() + " 的交易报价"; }
    @Override public boolean isReadOnly() { return true; }
    @Override public boolean isAutoUpdate() { return true; }
    @Override public int getSize() { return 27; }
    @Override public Map<Integer, Button> getButtons(Player player) {
        Map<Integer, Button> buttons = new HashMap<>();
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(target.getUniqueId());
        if (!target.isOnline() || !profile.isLoaded() || offer == null || profile.getOfferData() != offer
                || !offer.hasActiveOffer() || !player.getUniqueId().equals(offer.getBuyer())) {
            buttons.put(13, new DisplayButton(new ItemBuilder(Material.BARRIER).name("&c报价已失效").build(), true));
            return buttons;
        }
        buttons.put(11, new DisplayButton(offer.getItemStack(), true));
        buttons.put(15, new OfferButton(target, offer));
        return buttons;
    }
}
