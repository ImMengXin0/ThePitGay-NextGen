package cn.mythicland.framework.bukkit.diagnostics;

import cn.mythicland.framework.diagnostics.DiagnosticIds;
import cn.mythicland.framework.diagnostics.DiagnosticRecorder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

final class DiagnosticCoreBridge implements AutoCloseable {
    private final Consumer<Map<String, Object>> sink;
    private Method uninstall;
    private Method currentPacket;
    private Method currentDamage;

    DiagnosticCoreBridge(DiagnosticRecorder recorder, DiagnosticIds ids, Set<UUID> watched) throws ReflectiveOperationException {
        this.sink = fields -> {
            if (recorder.isRecording()) recorder.record("core.trace", fields);
        };
        Class<?> type;
        try {
            type = Class.forName("com.hpfxd.pandaspigot.diagnostics.CombatTrace");
        } catch (ClassNotFoundException unavailable) {
            recorder.record("capability", Map.of("coreBranches", false, "reason", "core_hook_not_installed"));
            return;
        }
        this.uninstall = type.getMethod("uninstall", Consumer.class);
        this.currentPacket = type.getMethod("currentPacketId");
        this.currentDamage = type.getMethod("currentDamageId");
        try {
            type.getMethod("install", Set.class, Consumer.class, ToLongFunction.class).invoke(
                    null,
                    watched,
                    this.sink,
                    (ToLongFunction<Object>) ids::id
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            try {
                this.close();
            } catch (ReflectiveOperationException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        recorder.record("capability", Map.of("coreBranches", true, "hookClass", type.getName()));
    }

    long packetId() {
        if (this.currentPacket == null) return 0;
        try {
            return ((Number) this.currentPacket.invoke(null)).longValue();
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Cannot read core diagnostic packet context", failure);
        }
    }

    long damageId() {
        if (this.currentDamage == null) return 0;
        try {
            return ((Number) this.currentDamage.invoke(null)).longValue();
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Cannot read core diagnostic damage context", failure);
        }
    }

    @Override
    public void close() throws ReflectiveOperationException {
        if (this.uninstall != null) this.uninstall.invoke(null, this.sink);
    }
}
