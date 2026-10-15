package dev.rollczi.litecommands.bukkit;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PitTabCompleteTest {
    @Test
    public void replacementClosesDefaultListenersAndRepeatedInstallationKeepsCurrentAdapter() {
        TabComplete previous = mock(TabComplete.class);
        LiteBukkitSettings settings = new LiteBukkitSettings().tabCompleter(previous);
        assertSame(settings, PitTabComplete.install(settings));
        verify(previous).close();
        TabComplete installed = settings.tabCompleter();
        assertTrue(installed instanceof PitTabComplete);
        assertSame(settings, PitTabComplete.install(settings));
        assertSame(installed, settings.tabCompleter());
        verifyNoMoreInteractions(previous);
    }
}
