package net.mizukilab.pit.npc;

import cn.charlotte.pit.ThePit;
import net.jitse.npclib.api.NPC;
import net.jitse.npclib.api.skin.Skin;
import net.mizukilab.pit.config.NewConfiguration;
import net.mizukilab.pit.npc.type.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BuiltinNpcSkinsTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private YamlConfiguration bundle;
    private Field configField;
    private Object previousConfig;

    @Before public void setup() throws Exception {
        bundle = resource("/npc-starrail-skins.yml");
        configField = NewConfiguration.class.getDeclaredField("config");
        configField.setAccessible(true);
        previousConfig = configField.get(null);
    }

    @After public void restore() throws Exception { configField.set(null, previousConfig); }

    private YamlConfiguration resource(String name) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(name)) {
            assertNotNull(name, stream);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    private Set<String> ids() { return bundle.getConfigurationSection("skins").getKeys(false); }
    private String valueKey(String id) { return id + "-npc-skin"; }
    private String signatureKey(String id) { return "not-netease-skins." + id + "-signature"; }

    private void assertPair(String id, YamlConfiguration config) {
        assertEquals(id, BuiltinNpcSkins.get(id).getValue(), config.getString(valueKey(id)));
        assertEquals(id, BuiltinNpcSkins.get(id).getSignature(), config.getString(signatureKey(id)));
    }

    @Test public void allTenDistinctBundledSkinsHaveValidMojangSignatures() throws Exception {
        assertEquals(10, ids().size());
        Set<String> unique = new HashSet<>();
        PublicKey key;
        try (InputStream stream = getClass().getResourceAsStream("/yggdrasil_session_pubkey.der")) {
            assertNotNull(stream);
            key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(stream.readAllBytes()));
        }
        for (String id : ids()) {
            Skin skin = BuiltinNpcSkins.get(id);
            assertTrue(id, unique.add(skin.getValue()));
            Signature verifier = Signature.getInstance("SHA1withRSA");
            verifier.initVerify(key);
            verifier.update(skin.getValue().getBytes(StandardCharsets.UTF_8));
            assertTrue(id, verifier.verify(Base64.getDecoder().decode(skin.getSignature())));
            String payload = new String(Base64.getDecoder().decode(skin.getValue()), StandardCharsets.UTF_8);
            assertTrue(id, payload.contains("textures.minecraft.net/texture/"));
        }
    }

    @Test public void shippedConfigContainsMatchingSignedPairs() throws Exception {
        YamlConfiguration config = resource("/custom.yml");
        for (String id : ids()) assertPair(id, config);
        assertFalse(BuiltinNpcSkins.migrateDefaults(config));
    }

    @Test public void missingAndBlankDefaultsMigrateOnceAndSurviveSerialization() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set(valueKey("keeper"), "  ");
        config.set("unrelated-option", 37);
        assertTrue(BuiltinNpcSkins.migrateDefaults(config));
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(config.saveToString());
        for (String id : ids()) assertPair(id, reloaded);
        assertEquals(37, reloaded.getInt("unrelated-option"));
        assertFalse(BuiltinNpcSkins.migrateDefaults(reloaded));
    }

    @Test public void allRecognizedLegacyValuesMigrateTogetherWithTheirSignatures() {
        for (String id : ids()) {
            for (String oldValue : bundle.getStringList("skins." + id + ".legacy-values")) {
                YamlConfiguration config = new YamlConfiguration();
                BuiltinNpcSkins.migrateDefaults(config);
                config.set(valueKey(id), oldValue);
                config.set(signatureKey(id), "legacy-signature");
                assertTrue(id, BuiltinNpcSkins.migrateDefaults(config));
                assertPair(id, config);
                assertFalse(BuiltinNpcSkins.migrateDefaults(config));
            }
        }
    }

    @Test public void bundledValuesRepairMissingOrMismatchedSignature() {
        for (String wrongSignature : Arrays.asList(null, "", "old-signature")) {
            YamlConfiguration config = new YamlConfiguration();
            BuiltinNpcSkins.migrateDefaults(config);
            for (String id : ids()) config.set(signatureKey(id), wrongSignature);
            assertTrue(BuiltinNpcSkins.migrateDefaults(config));
            for (String id : ids()) assertPair(id, config);
        }
    }

    @Test public void realLoadAndCleanupKeepCustomPairsAndDoNotInventCustomSignatures() throws Exception {
        File file = new File(folder.getRoot(), "custom.yml");
        YamlConfiguration original = new YamlConfiguration();
        original.set(valueKey("shop"), "custom-shop-texture");
        original.set(signatureKey("shop"), "custom-shop-signature");
        original.set(valueKey("mail"), "custom-unsigned-texture");
        original.save(file);
        try (var pit = mockStatic(ThePit.class)) {
            ThePit plugin = mock(ThePit.class);
            when(plugin.getDataFolder()).thenReturn(folder.getRoot());
            pit.when(ThePit::getInstance).thenReturn(plugin);
            NewConfiguration.INSTANCE.loadFile();
            NewConfiguration.INSTANCE.cleanupConfig();
            YamlConfiguration reloaded = YamlConfiguration.loadConfiguration(file);
            assertEquals("custom-shop-texture", reloaded.getString(valueKey("shop")));
            assertEquals("custom-shop-signature", reloaded.getString(signatureKey("shop")));
            assertEquals("custom-unsigned-texture", reloaded.getString(valueKey("mail")));
            assertEquals("", reloaded.getString(signatureKey("mail")));
            for (String id : ids()) if (!id.equals("shop") && !id.equals("mail")) assertPair(id, reloaded);
            assertFalse(BuiltinNpcSkins.migrateDefaults(reloaded));
        }
    }

    @Test public void everyHumanNpcAppliesNewDefaultAndFallsBackForIncompleteOverrides() {
        YamlConfiguration config = new YamlConfiguration();
        NewConfiguration.INSTANCE.setConfig(config);
        List<SkinNPC> npcs = List.of(new GenesisAngelNpc(), new GenesisDemonNpc(), new KeeperNPC(),
                new MailNpc(), new PerkNPC(), new PrestigeNPC(), new QuestNpc(), new ShopNPC(),
                new StatusNPC(), new InfinityItemNPC());
        Set<String> actualIds = new HashSet<>();
        for (SkinNPC npc : npcs) {
            String id = npc.getNpcInternalName();
            actualIds.add(id);
            assertSame(BuiltinNpcSkins.get(id), npc.getNpcSkin());
            NPC target = mock(NPC.class);
            npc.initSkin(target);
            verify(target).setSkin(BuiltinNpcSkins.get(id));
            reset(target);
            config.set(valueKey(id), "custom-without-signature");
            config.set(signatureKey(id), "");
            npc.initSkin(target);
            verify(target).setSkin(BuiltinNpcSkins.get(id));
            reset(target);
            config.set(signatureKey(id), "custom-signature");
            npc.initSkin(target);
            ArgumentCaptor<Skin> applied = ArgumentCaptor.forClass(Skin.class);
            verify(target).setSkin(applied.capture());
            assertEquals("custom-without-signature", applied.getValue().getValue());
            assertEquals("custom-signature", applied.getValue().getSignature());
        }
        assertEquals(ids(), actualIds);
    }
}
