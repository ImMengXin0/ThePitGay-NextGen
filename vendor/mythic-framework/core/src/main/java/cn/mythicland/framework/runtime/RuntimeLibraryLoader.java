package cn.mythicland.framework.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

public final class RuntimeLibraryLoader {
    private static final int CONNECT_TIMEOUT_MILLIS = 30_000;
    private static final int READ_TIMEOUT_MILLIS = 60_000;
    private static final String STATE_FILE = ".managed-libraries";

    private final Path libraryDirectory;
    private final ClassLoader resourceLoader;
    private final String manifestResource;
    private final Consumer<String> infoLogger;
    private final Consumer<String> warningLogger;

    public RuntimeLibraryLoader(
            Path dataDirectory,
            ClassLoader resourceLoader,
            String manifestResource,
            Consumer<String> infoLogger,
            Consumer<String> warningLogger
    ) {
        this.libraryDirectory = dataDirectory.resolve("libs");
        this.resourceLoader = resourceLoader;
        this.manifestResource = manifestResource;
        this.infoLogger = infoLogger;
        this.warningLogger = warningLogger;
    }

    public List<URL> resolveLibraries(Set<String> activePlatforms) {
        try {
            Files.createDirectories(this.libraryDirectory);
            List<RuntimeLibrary> manifest = this.readManifest();
            Set<String> managedNames = new LinkedHashSet<>();
            Set<String> requiredNames = new LinkedHashSet<>();
            List<URL> urls = new ArrayList<>();
            List<PendingLibrary> missingLibraries = new ArrayList<>();
            for (RuntimeLibrary library : manifest) {
                managedNames.add(library.fileName());
                if (library.platforms().stream().noneMatch(activePlatforms::contains)) continue;
                requiredNames.add(library.fileName());
                Path target = this.resolveTarget(library.fileName());
                if (this.hasExpectedHash(target, library.sha256())) {
                    this.infoLogger.accept("Loading dependency jar: " + target.getFileName());
                    urls.add(target.toUri().toURL());
                    continue;
                }
                missingLibraries.add(new PendingLibrary(library, target));
            }
            for (PendingLibrary missingLibrary : missingLibraries) {
                this.download(missingLibrary.library(), missingLibrary.target());
            }
            for (PendingLibrary missingLibrary : missingLibraries) {
                RuntimeLibrary library = missingLibrary.library();
                Path target = missingLibrary.target();
                if (!this.hasExpectedHash(target, library.sha256())) {
                    throw new IOException("Invalid runtime library file: " + target);
                }
                this.infoLogger.accept("Loading dependency jar: " + target.getFileName());
                urls.add(target.toUri().toURL());
            }
            this.cleanupManagedLibraries(managedNames, requiredNames);
            Files.write(this.libraryDirectory.resolve(STATE_FILE), requiredNames, StandardCharsets.UTF_8);
            return List.copyOf(urls);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to prepare runtime libraries from " + this.manifestResource,
                    exception
            );
        }
    }

    private List<RuntimeLibrary> readManifest() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = this.resourceLoader.getResourceAsStream(this.manifestResource)) {
            if (input == null) {
                throw new IllegalStateException("Missing runtime dependency manifest: " + this.manifestResource);
            }
            properties.load(input);
        }
        int count = Integer.parseInt(properties.getProperty("library.count"));
        List<RuntimeLibrary> libraries = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String prefix = "library." + index + ".";
            libraries.add(new RuntimeLibrary(
                    required(properties, prefix + "coordinate"),
                    required(properties, prefix + "fileName"),
                    split(required(properties, prefix + "downloadUrls")),
                    required(properties, prefix + "sha256"),
                    Set.copyOf(split(required(properties, prefix + "platform")))
            ));
        }
        return List.copyOf(libraries);
    }

    private Path resolveTarget(String fileName) {
        Path target = this.libraryDirectory.resolve(fileName).normalize();
        if (!target.getParent().equals(this.libraryDirectory.normalize())) {
            throw new IllegalStateException("Invalid runtime library file name: " + fileName);
        }
        return target;
    }

    private void download(RuntimeLibrary library, Path target) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.deleteIfExists(temporary);
        IOException lastFailure = null;
        this.infoLogger.accept("Downloading dependency jar: " + target.getFileName());
        for (String downloadUrl : library.downloadUrls()) {
            try {
                URLConnection connection = URI.create(downloadUrl).toURL().openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
                connection.setReadTimeout(READ_TIMEOUT_MILLIS);
                connection.setRequestProperty("User-Agent", "MythicFramework-LibraryLoader");
                try (InputStream input = connection.getInputStream();
                     OutputStream output = Files.newOutputStream(temporary)) {
                    input.transferTo(output);
                }
                if (!this.hasExpectedHash(temporary, library.sha256())) {
                    throw new IOException("SHA-256 mismatch for " + library.coordinate());
                }
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
                return;
            } catch (IOException exception) {
                lastFailure = exception;
                Files.deleteIfExists(temporary);
                this.warningLogger.accept("Download failed, trying next source: " + downloadUrl);
            }
        }
        throw new IOException("Failed to download required library " + library.coordinate(), lastFailure);
    }

    private boolean hasExpectedHash(Path file, String expectedHash) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) == 0L) return false;
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            return expectedHash.equalsIgnoreCase(toHex(digest.digest()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void cleanupManagedLibraries(
            Set<String> managedNames,
            Set<String> requiredNames
    ) throws IOException {
        for (String managedName : managedNames) {
            if (!requiredNames.contains(managedName)) Files.deleteIfExists(this.resolveTarget(managedName));
        }
        Path state = this.libraryDirectory.resolve(STATE_FILE);
        if (!Files.isRegularFile(state)) return;
        for (String previousName : Files.readAllLines(state, StandardCharsets.UTF_8)) {
            if (previousName.isBlank() || requiredNames.contains(previousName)) continue;
            Files.deleteIfExists(this.resolveTarget(previousName));
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing manifest value: " + key);
        }
        return value.trim();
    }

    private static List<String> split(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value));
        return result.toString();
    }

    private record RuntimeLibrary(
            String coordinate,
            String fileName,
            List<String> downloadUrls,
            String sha256,
            Set<String> platforms
    ) {
    }

    private record PendingLibrary(RuntimeLibrary library, Path target) {
    }
}
