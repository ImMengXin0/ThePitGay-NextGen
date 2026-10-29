package cn.mythicland.framework.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

public final class DiagnosticRecorder implements AutoCloseable {
    public record Limits(
            Duration duration,
            long segmentBytes,
            long sessionBytes,
            long directoryBytes,
            long minimumFreeBytes,
            int queueCapacity
    ) {
        public Limits {
            if (duration.isNegative() || duration.isZero() || duration.compareTo(Duration.ofMinutes(30)) > 0) {
                throw new IllegalArgumentException("Diagnostic duration must be within 1ns and 30 minutes");
            }
            if (segmentBytes < 4096 || sessionBytes < segmentBytes || directoryBytes < sessionBytes
                    || minimumFreeBytes < 0 || queueCapacity < 1) {
                throw new IllegalArgumentException("Invalid diagnostic storage or queue limits");
            }
        }
    }

    public record History(Duration window, long maxBytes) {
        public History {
            if (window.isZero() || window.isNegative() || window.compareTo(Duration.ofMinutes(30)) > 0)
                throw new IllegalArgumentException("History window must be within 1ns and 30 minutes");
            if (maxBytes < 65536) throw new IllegalArgumentException("History byte budget must be at least 64 KiB");
        }
    }

    public record Summary(
            String session,
            Path directory,
            String reason,
            long records,
            long dropped,
            long uncompressedBytes,
            String error
    ) {
    }

    private static final Gson JSON = new GsonBuilder()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();
    private static final int MAX_RECORD_BYTES = 65536;
    private final String session = UUID.randomUUID().toString();
    private final Path directory;
    private final Limits limits;
    private final ArrayBlockingQueue<Map<String, Object>> queue;
    private final AtomicReference<String> stopReason = new AtomicReference<>();
    private final AtomicLong identifiers = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final CompletableFuture<Summary> completion = new CompletableFuture<>();
    private final ScheduledExecutorService deadline;
    private final Thread writer;
    private volatile long deadlineNanos;
    private final DiagnosticHistory history;
    private volatile boolean buffering;
    private Map<String, Object> historySummary;
    private final long priorDirectoryBytes;
    private volatile long written;
    private volatile long bytes;
    private volatile String error = "";

    public DiagnosticRecorder(Path root, Limits limits, Map<String, Object> metadata) throws IOException {
        this(root, limits, metadata, null);
    }

    public DiagnosticRecorder(Path root, Limits limits, Map<String, Object> metadata, History history) throws IOException {
        if (history != null && history.maxBytes() > limits.sessionBytes() - 65536)
            throw new IllegalArgumentException("History byte budget must leave room within the session limit");
        this.limits = limits;
        this.history = history == null ? null : new DiagnosticHistory(history);
        this.buffering = history != null;
        Files.createDirectories(root);
        try (var files = Files.walk(root)) {
            this.priorDirectoryBytes = files.filter(Files::isRegularFile).mapToLong(path -> path.toFile().length()).sum();
        }
        if (this.priorDirectoryBytes + 65536 >= limits.directoryBytes()) {
            throw new IOException("Diagnostic directory quota reached: " + root);
        }
        if (Files.getFileStore(root).getUsableSpace() < limits.minimumFreeBytes() + 65536) {
            throw new IOException("Insufficient free disk space for diagnostics: " + root);
        }
        this.directory = Files.createDirectory(root.resolve(Instant.now().toString().replace(':', '-') + "-" + this.session));
        this.queue = new ArrayBlockingQueue<>(limits.queueCapacity());
        this.deadline = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "diagnostic-deadline");
            thread.setDaemon(true);
            return thread;
        });
        this.record("session.start", metadata);
        this.writer = new Thread(this::write, "diagnostic-writer");
        this.writer.setDaemon(true);
        if (history == null) this.startDeadline();
        this.writer.start();
    }

    public long nextId() {
        return this.identifiers.incrementAndGet();
    }

    public boolean isRecording() {
        if (this.deadlineNanos != 0 && System.nanoTime() - this.deadlineNanos >= 0) this.stop("duration_limit");
        return this.stopReason.get() == null;
    }

    public synchronized boolean record(String stage, Map<String, Object> fields) {
        if (!this.isRecording()) return false;
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("session", this.session);
        record.put("id", this.nextId());
        record.put("timeMillis", System.currentTimeMillis());
        record.put("nanoTime", System.nanoTime());
        record.put("thread", Thread.currentThread().getName());
        record.put("stage", stage);
        record.put("fields", fields);
        if (this.queue.offer(record)) return true;
        this.dropped.incrementAndGet();
        this.stop("queue_overflow");
        return false;
    }

    private void startDeadline() {
        this.deadlineNanos = System.nanoTime() + this.limits.duration().toNanos();
        this.deadline.schedule(() -> this.stop("duration_limit"), this.limits.duration().toNanos(), TimeUnit.NANOSECONDS);
    }

    public synchronized void trigger() {
        if (!this.buffering || !this.isRecording()) return;
        if (!this.record("history.trigger", Map.of())) return;
        this.buffering = false;
        this.startDeadline();
    }

    public boolean isBuffering() {
        return this.buffering && this.isRecording();
    }

    public void fail(String stage, Throwable failure) {
        this.error = stage + ": " + failure;
        this.stop("capture_error");
    }

    public synchronized void stop(String reason) {
        this.stopReason.compareAndSet(null, reason);
    }

    public CompletableFuture<Summary> completion() {
        return this.completion;
    }

    public Path directory() {
        return this.directory;
    }

    public long records() {
        return this.buffering ? this.history.size() : this.written;
    }

    public long bytes() {
        return this.buffering ? this.history.bytes() : this.bytes;
    }

    private byte[] encode(Map<String, Object> record) {
        byte[] encoded = (JSON.toJson(record) + "\n").getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_RECORD_BYTES) {
            Map<String, Object> bounded = new LinkedHashMap<>();
            bounded.put("omitted", "record_size_limit");
            bounded.put("originalBytes", encoded.length);
            Map<?, ?> fields = (Map<?, ?>) record.get("fields");
            for (var entry : fields.entrySet()) {
                if (bounded.size() >= 18) break;
                String key = String.valueOf(entry.getKey());
                if (key.length() > 64) continue;
                Object value = entry.getValue();
                if (value == null || value instanceof Number || value instanceof Boolean
                        || value instanceof String text && text.length() <= 128) {
                    bounded.put(key, value);
                }
            }
            String preview = JSON.toJson(fields);
            bounded.put("preview", preview.substring(0, Math.min(4096, preview.length())));
            record.put("fields", bounded);
            encoded = (JSON.toJson(record) + "\n").getBytes(StandardCharsets.UTF_8);
        }
        return encoded;
    }

    private void write() {
        OutputStream output = null;
        List<String> segments = new ArrayList<>();
        long segmentBytes = 0;
        boolean historyFlushed = this.history == null;
        ArrayDeque<byte[]> pending = new ArrayDeque<>();
        try {
            while (true) {
                byte[] encoded;
                if (!pending.isEmpty()) encoded = pending.removeFirst();
                else {
                    Map<String, Object> record = this.queue.poll(100, TimeUnit.MILLISECONDS);
                    if (!historyFlushed) {
                        if (record != null) this.history.add(record, this.encode(record));
                        else this.history.trim(System.nanoTime());
                        if (record != null && "history.trigger".equals(record.get("stage"))
                                || record == null && !this.isRecording() && this.queue.isEmpty()) {
                            this.historySummary = this.history.summary();
                            pending = this.history.drain();
                            historyFlushed = true;
                        }
                        continue;
                    }
                    if (record == null) {
                        if (!this.isRecording() && this.queue.isEmpty()) break;
                        if (output != null) output.flush();
                        continue;
                    }
                    encoded = this.encode(record);
                }
                if (this.bytes + encoded.length + 65536 > this.limits.sessionBytes()) {
                    this.dropped.incrementAndGet();
                    this.stop("session_size_limit");
                    break;
                }
                long storageReserve = 65536L + (segments.size() + 1L) * 512 + (this.written + 1) * 16
                        + (this.bytes + encoded.length) / 100;
                if (this.priorDirectoryBytes + this.bytes + encoded.length + storageReserve > this.limits.directoryBytes()) {
                    this.dropped.incrementAndGet();
                    this.stop("directory_size_limit");
                    break;
                }
                if (this.written % 128 == 0 && Files.getFileStore(this.directory).getUsableSpace() < this.limits.minimumFreeBytes() + 65536) {
                    this.dropped.incrementAndGet();
                    this.stop("free_space_limit");
                    break;
                }
                if (output == null || segmentBytes + encoded.length > this.limits.segmentBytes()) {
                    if (output != null) output.close();
                    String name = String.format("%04d.jsonl.gz", segments.size() + 1);
                    segments.add(name);
                    output = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(
                            this.directory.resolve(name), StandardOpenOption.CREATE_NEW)), 65536, true);
                    segmentBytes = 0;
                }
                output.write(encoded);
                this.bytes += encoded.length;
                segmentBytes += encoded.length;
                this.written++;
            }
        } catch (IOException | InterruptedException | RuntimeException failure) {
            this.error = failure.toString();
            this.stop("writer_error");
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            this.stop("writer_closed");
            this.deadline.shutdownNow();
            this.dropped.addAndGet(this.queue.size() + pending.size());
            this.queue.clear();
            Summary summary = new Summary(
                    this.session,
                    this.directory,
                    this.stopReason.get(),
                    this.written,
                    this.dropped.get(),
                    this.bytes,
                    this.error
            );
            try {
                if (output != null) {
                    Map<String, Object> end = new LinkedHashMap<>();
                    end.put("stage", "session.end");
                    end.put("timeMillis", System.currentTimeMillis());
                    end.put("session", this.session);
                    end.put("reason", summary.reason());
                    end.put("records", summary.records());
                    end.put("dropped", summary.dropped());
                    end.put("error", summary.error());
                    output.write((JSON.toJson(end) + "\n").getBytes(StandardCharsets.UTF_8));
                    output.close();
                    output = null;
                }
                Map<String, Object> manifest = new LinkedHashMap<>();
                manifest.put("session", this.session);
                manifest.put("reason", summary.reason());
                manifest.put("records", summary.records());
                manifest.put("dropped", summary.dropped());
                manifest.put("uncompressedBytes", this.bytes);
                manifest.put("error", summary.error());
                manifest.put("segments", segments);
                if (this.historySummary != null) manifest.put("history", this.historySummary);
                Files.writeString(
                        this.directory.resolve("session.json"),
                        JSON.toJson(manifest),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW
                );
            } catch (IOException | RuntimeException failure) {
                this.error = "Diagnostic finalization failed: " + failure;
                summary = new Summary(
                        this.session,
                        this.directory,
                        summary.reason(),
                        summary.records(),
                        summary.dropped(),
                        this.bytes,
                        this.error
                );
            } finally {
                if (output != null) {
                    try {
                        output.close();
                    } catch (IOException failure) {
                        this.error = "Diagnostic stream close failed: " + failure;
                        summary = new Summary(
                                this.session,
                                this.directory,
                                summary.reason(),
                                summary.records(),
                                summary.dropped(),
                                this.bytes,
                                this.error
                        );
                    }
                }
                this.completion.complete(summary);
            }
        }
    }

    @Override
    public void close() {
        this.stop("plugin_disable");
        try {
            this.writer.join(5000);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        }
        if (this.writer.isAlive()) throw new IllegalStateException("Diagnostic writer did not stop: " + this.directory);
    }
}
