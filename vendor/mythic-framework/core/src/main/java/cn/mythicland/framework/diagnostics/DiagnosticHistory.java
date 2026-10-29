package cn.mythicland.framework.diagnostics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

final class DiagnosticHistory {
    record Entry(long id, long nanos, byte[] encoded) {
    }

    private final DiagnosticRecorder.History limits;
    private final ArrayDeque<Entry> records = new ArrayDeque<>();
    private final Map<String, Entry> metadata = new LinkedHashMap<>();
    private long metadataBytes;
    private volatile long bytes;
    private volatile int size;
    private long expired;
    private long evicted;

    DiagnosticHistory(DiagnosticRecorder.History limits) {
        this.limits = limits;
    }

    void add(Map<String, Object> record, byte[] encoded) {
        Entry entry = new Entry((Long) record.get("id"), (Long) record.get("nanoTime"), encoded);
        String key = metadataKey(record);
        if (key != null) {
            Entry previous = this.metadata.put(key, entry);
            if (previous != null) {
                this.bytes -= previous.encoded.length;
                this.metadataBytes -= previous.encoded.length;
            }
            this.metadataBytes += encoded.length;
            if (this.metadataBytes > this.limits.maxBytes() / 2)
                throw new IllegalStateException("Diagnostic history metadata exceeds its byte budget");
        } else this.records.addLast(entry);
        this.bytes += encoded.length;
        this.trim(entry.nanos);
    }

    void trim(long now) {
        while (!this.records.isEmpty() && now - this.records.getFirst().nanos > this.limits.window().toNanos()) {
            this.bytes -= this.records.removeFirst().encoded.length;
            this.expired++;
        }
        while (!this.records.isEmpty() && this.bytes > this.limits.maxBytes()) {
            this.bytes -= this.records.removeFirst().encoded.length;
            this.evicted++;
        }
        this.size = this.metadata.size() + this.records.size();
    }

    ArrayDeque<byte[]> drain() {
        var entries = new ArrayList<>(this.metadata.values());
        entries.addAll(this.records);
        entries.sort(Comparator.comparingLong(Entry::id));
        ArrayDeque<byte[]> result = new ArrayDeque<>();
        for (Entry entry : entries) result.addLast(entry.encoded);
        this.metadata.clear();
        this.records.clear();
        this.bytes = 0;
        this.size = 0;
        return result;
    }

    Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("windowSeconds", this.limits.window().toSeconds());
        out.put("maxBytes", this.limits.maxBytes());
        out.put("expiredRecords", this.expired);
        out.put("capacityEvictedRecords", this.evicted);
        out.put("retainedRecords", this.size);
        out.put("retainedBytes", this.bytes);
        out.put("firstRetainedNanos", this.records.isEmpty() ? null : this.records.getFirst().nanos);
        return out;
    }

    long bytes() {
        return this.bytes;
    }

    int size() {
        return this.size;
    }

    private static String metadataKey(Map<String, Object> record) {
        String stage = (String) record.get("stage");
        Map<?, ?> fields = (Map<?, ?>) record.get("fields");
        return switch (stage) {
            case "session.start", "capability" -> stage;
            case "artifact" -> stage + ":" + fields.get("source");
            case "pipeline.installed" -> stage + ":" + fields.get("player");
            default -> null;
        };
    }
}
