package net.mizukilab.pit.npc;

import org.junit.Test;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Loads the shipped, relocated API against real extension binaries. */
public class NpcBinaryCompatibilityTest {
    @Test public void realAddonAndRepairNpcsStillOverrideTheShippedNpcApi() throws Exception {
        Path addon = Path.of(System.getProperty("pitAddonJar"));
        Path loader = Path.of(System.getProperty("pitAddonLoaderJar"));
        Path repair = Path.of(System.getProperty("repairExtendJar"));
        assumeTrue(Files.isRegularFile(addon) && Files.isRegularFile(loader) && Files.isRegularFile(repair));
        try (URLClassLoader classes = new URLClassLoader(new URL[]{Path.of("build/libs/ThePitGay-V.jar").toUri().toURL(),
                addon.toUri().toURL(), loader.toUri().toURL(), repair.toUri().toURL()}, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null && (name.startsWith("net.mizukilab.pit.npc.")
                            || name.startsWith("net.mizukilab.pit.libs.npclib.")
                            || name.startsWith("net.mizukilab.pit.libs.mythicframework."))) {
                        try { loaded = findClass(name); } catch (ClassNotFoundException ignored) { }
                    }
                    if (loaded == null) loaded = super.loadClass(name, false);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        }) {
            Class<?> base = classes.loadClass("net.mizukilab.pit.npc.AbstractPitNPC");
            Class<?> npc = classes.loadClass("net.mizukilab.pit.libs.npclib.api.NPC");
            Class<?> skin = classes.loadClass("net.mizukilab.pit.libs.npclib.api.skin.Skin");
            Class<?> lib = classes.loadClass("net.mizukilab.pit.libs.npclib.NPCLib");
            assertEquals(npc, lib.getMethod("createNPC").getReturnType());
            assertEquals(npc, lib.getMethod("createNPC", List.class).getReturnType());
            Object pair = skin.getConstructor(String.class, String.class).newInstance("value", "signature");
            assertEquals("value", skin.getMethod("getValue").invoke(pair));
            assertEquals("signature", skin.getMethod("getSignature").invoke(pair));
            int checked = 0;
            for (Path jar : List.of(addon, repair)) {
                try (JarFile archive = new JarFile(jar.toFile())) {
                    for (var entry : Collections.list(archive.entries())) {
                        String name = entry.getName();
                        if (!name.endsWith(".class") || name.contains("$") || !name.contains("/npc/")) continue;
                        Class<?> type = classes.loadClass(name.substring(0, name.length() - 6).replace('/', '.'));
                        if (!base.isAssignableFrom(type) || Modifier.isAbstract(type.getModifiers())) continue;
                        for (Method required : base.getMethods()) {
                            if (!Modifier.isAbstract(required.getModifiers())) continue;
                            Method implemented = type.getMethod(required.getName(), required.getParameterTypes());
                            assertFalse(type + " must implement " + required, Modifier.isAbstract(implemented.getModifiers()));
                            assertTrue(required.getReturnType().isAssignableFrom(implemented.getReturnType()));
                        }
                        checked++;
                    }
                }
            }
            assertTrue("Real extension NPC classes were checked", checked >= 5);
        }
    }

    @Test public void shippedCoreContainsFrameworkAndNoCitizensLinkage() throws Exception {
        try (JarFile jar = new JarFile("build/libs/ThePitGay-V.jar")) {
            assertNotNull(jar.getEntry("net/mizukilab/pit/libs/npclib/NPCLib.class"));
            assertNotNull(jar.getEntry("net/mizukilab/pit/libs/npclib/bot/BotPatrol.class"));
            assertNotNull(jar.getEntry("net/mizukilab/pit/libs/mythicframework/bukkit/reflect/Reflection.class"));
            assertNotNull(jar.getEntry("net/mizukilab/pit/libs/mythicframework/path/CyclicPathCursor.class"));
            for (var entry : Collections.list(jar.entries())) {
                if (entry.isDirectory()) continue;
                assertFalse(entry.getName(), entry.getName().startsWith("net/jitse/npclib/"));
                assertFalse(entry.getName(), entry.getName().startsWith("cn/mythicland/"));
                if (!entry.getName().endsWith(".class")) continue;
                String bytes = new String(jar.getInputStream(entry).readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
                assertFalse(entry.getName(), bytes.contains("net/citizensnpcs") || bytes.contains("net.citizensnpcs"));
            }
        }
    }
}
