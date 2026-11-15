package net.mizukilab.pit.platform.paper;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ViewerPolicyTest {
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final ViewerPolicy policy = new ViewerPolicy();

    @Test void aNewDisplayDoesNotLeakToOtherPlayers() {
        assertFalse(policy.accepts(alice));
        policy.showOnly(List.of(alice));
        assertTrue(policy.accepts(alice));
        assertFalse(policy.accepts(bob));
    }

    @Test void hidingAPublicDisplaySurvivesVisibilityRefresh() {
        policy.showAll();
        policy.hide(alice);
        assertFalse(policy.accepts(alice));
        assertTrue(policy.accepts(bob));
        policy.show(alice);
        assertTrue(policy.accepts(alice));
    }

    @Test void replacingViewersRevokesPreviousAccess() {
        policy.showOnly(List.of(alice));
        policy.showOnly(List.of(bob));
        assertFalse(policy.accepts(alice));
        assertTrue(policy.accepts(bob));
        policy.showOnly(List.of());
        assertFalse(policy.accepts(bob));
    }

    @Test void publicVisibilityCanBeChangedBackToPrivate() {
        policy.showAll();
        policy.showOnly(List.of(alice));
        assertTrue(policy.accepts(alice));
        assertFalse(policy.accepts(bob));
    }

    @Test void quitClearsPrivateAccessAndShutdownClearsPublicAccess() {
        policy.show(alice);
        policy.forget(alice);
        assertFalse(policy.accepts(alice));
        policy.showAll();
        policy.clear();
        assertFalse(policy.accepts(bob));
    }

    @Test void privateRefreshOnlyVisitsCurrentAndPreviousViewers() {
        policy.showOnly(List.of(alice));
        assertEquals(java.util.Set.of(alice), policy.privateCandidates(List.of()));
        policy.showOnly(List.of(bob));
        assertEquals(java.util.Set.of(alice, bob), policy.privateCandidates(List.of(alice)));
    }
}
