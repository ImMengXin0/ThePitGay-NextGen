package net.mizukilab.pit.npc;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import org.junit.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import static org.junit.Assert.*;

public class MythicBotIdentityTest {
    @Test public void recognizesTheRealRepairLibraryWithoutSharingItsClassloader() throws Exception {
        Path jar = Path.of(System.getProperty("nativeRepairJar"));
        Assume.assumeTrue("Native RepairExTend fixture unavailable", Files.isRegularFile(jar));
        try (var loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> foreign = loader.loadClass("cn.mx.thepitaddon.npc.lib.bot.v1_8_R3.BotEntity");
            assertTrue(EntityPlayer.class.isAssignableFrom(foreign));
            assertTrue(NpcIdentity.isMythicBotType(foreign));
            assertFalse(NpcIdentity.isMythicBotType(EntityPlayer.class));
        }
    }
}
