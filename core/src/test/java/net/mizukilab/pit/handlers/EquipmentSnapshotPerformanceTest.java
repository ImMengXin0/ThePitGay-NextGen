package net.mizukilab.pit.handlers;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.perk.PerkFactory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import cn.mythicland.framework.bukkit.item.PaperItemData;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.Bootstrap;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.enchantment.UnknownEnchantment;
import net.mizukilab.pit.item.IMythicItem;
import net.mizukilab.pit.item.type.mythic.MythicSwordItem;
import net.mizukilab.pit.item.type.mythic.MythicLeggingsItem;
import net.mizukilab.pit.util.PublicUtil;
import net.mizukilab.pit.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.inventory.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.*;
import java.io.PrintStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class EquipmentSnapshotPerformanceTest extends net.mizukilab.pit.BukkitDataTestSupport {
    @BeforeClass public static void items() {
        PrintStream out=System.out,err=System.err;
        try(var attributes=mockConstruction(AttributeModifier.class)) { net.mizukilab.pit.PaperTestBootstrap.initialize(); }
        finally { System.setOut(out); System.setErr(err); }
    }
    @Test public void freshSwordWrappersReuseSnapshotAndInPlaceEditsInvalidate() throws Exception { check(false); }
    @Test public void freshLeggingsWrappersReuseSnapshotAndInPlaceEditsInvalidate() throws Exception { check(true); }
    private void check(boolean pants) throws Exception {
        String previous=PublicUtil.itemVersion; PublicUtil.itemVersion="snapshot-test";
        try(var pit=mockStatic(ThePit.class); var bukkit=net.mizukilab.pit.PaperTestBootstrap.mockBukkit(); var utils=mockStatic(Utils.class)) {
            ThePit plugin=mock(ThePit.class); pit.when(ThePit::getInstance).thenReturn(plugin);
            when(plugin.getEnchantmentFactor()).thenReturn(new EnchantmentFactor());
            when(plugin.getPerkFactory()).thenReturn(new PerkFactory());
            bukkit.when(Bukkit::getItemFactory).thenReturn(CraftItemFactory.instance());
            Player player=mock(Player.class); PlayerInventory inventory=mock(PlayerInventory.class);
            when(player.getInventory()).thenReturn(inventory);
            PlayerProfile profile=mock(PlayerProfile.class);
            ItemStack handle=new ItemStack(pants?Items.LEATHER_LEGGINGS:Items.GOLDEN_SWORD);
            CompoundTag root=new CompoundTag(),extra=new CompoundTag(); root.put("extra",extra);
            UUID id=UUID.randomUUID(); extra.putString("uuid",id.toString());
            extra.putString("internal",pants?"mythic_leggings":"mythic_sword"); extra.putString("version",PublicUtil.itemVersion);
            ListTag ench=new ListTag(); ench.add(StringTag.valueOf("test:1")); extra.put("ench",ench); PaperItemData.custom(handle, root);
            AtomicInteger decodes=new AtomicInteger();
            utils.when(() -> Utils.getMythicItem(any(org.bukkit.inventory.ItemStack.class))).thenAnswer(call -> {
                decodes.incrementAndGet(); IMythicItem parsed=pants?new MythicLeggingsItem():new MythicSwordItem(); parsed.uuid=id;
                if(extra.getListOrEmpty("ench").size()>0) parsed.getEnchantments().put(new UnknownEnchantment("test"),1);
                return parsed;
            });
            when(inventory.getItemInHand()).thenAnswer(call -> CraftItemStack.asCraftMirror(handle));
            when(inventory.getLeggings()).thenAnswer(call -> CraftItemStack.asCraftMirror(handle));
            TickHandler ticker=new TickHandler();
            var method=pants?TickHandler.class.getDeclaredMethod("tickLeggings",Player.class,PlayerProfile.class,long.class)
                    :TickHandler.class.getDeclaredMethod("tickItemInHand",Player.class,PlayerInventory.class,PlayerProfile.class,long.class);
            method.setAccessible(true);
            Runnable tick=() -> {
                try {
                    if(pants)method.invoke(ticker,player,profile,0L); else method.invoke(ticker,player,inventory,profile,0L);
                } catch(Exception e) { throw new AssertionError(e); }
            };
            for(int i=0;i<100;i++)tick.run(); assertEquals(1,decodes.get());
            // A scroll or workshop can rewrite enchant NBT without replacing the underlying handle.
            extra.put("ench",new ListTag()); PaperItemData.custom(handle, root); tick.run(); assertEquals(2,decodes.get());
            assertTrue((pants?profile.leggings:profile.heldItem).getEnchantments().isEmpty());
            for(int i=0;i<20;i++)tick.run(); assertEquals(2,decodes.get());
            extra.putInt("lives",7); PaperItemData.custom(handle, root); tick.run(); assertEquals(3,decodes.get());
            root.putString("custom_external_name","renamed"); PaperItemData.custom(handle, root); tick.run(); assertEquals(4,decodes.get());
            // Repair replaces the public field with a live wrapper: it must be revalidated.
            if(pants)profile.leggingItemStack=CraftItemStack.asCraftMirror(handle);
            else profile.heldItemStack=CraftItemStack.asCraftMirror(handle);
            tick.run(); assertEquals(5,decodes.get());
            extra.putInt("lives",6); PaperItemData.custom(handle, root); tick.run(); assertEquals(6,decodes.get());
            PublicUtil.itemVersion="new-version"; tick.run(); assertEquals(7,decodes.get());
        } finally { PublicUtil.itemVersion=previous; }
    }
}
