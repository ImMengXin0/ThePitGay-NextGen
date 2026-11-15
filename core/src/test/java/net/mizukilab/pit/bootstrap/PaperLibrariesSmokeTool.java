package net.mizukilab.pit.bootstrap;

import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.LibraryLoader;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.LocalRepository;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.jar.JarFile;

/** Uses the target Paper loader without starting Minecraft or connecting to a database. */
public final class PaperLibrariesSmokeTool {
    private record Library(String coordinate, String sha256) {
        Path path(Path cache) {
            String[] parts = coordinate.split(":");
            return cache.resolve(parts[0].replace('.', '/')).resolve(parts[1]).resolve(parts[2])
                    .resolve(parts[1] + "-" + parts[2] + ".jar");
        }
    }

    public static void main(String[] args) throws Exception {
        require(Runtime.version().feature() == 25, "Run the library smoke check on Java 25");
        Path plugin = Path.of(args[0]);
        Path output = Path.of(args[1]);
        Files.createDirectories(output);
        Path cache = Files.createTempDirectory(output, "cache-");
        PluginDescriptionFile description;
        List<Library> manifest = new ArrayList<>();
        try (JarFile jar = new JarFile(plugin.toFile()); InputStream yaml = jar.getInputStream(jar.getJarEntry("plugin.yml"))) {
            description = new PluginDescriptionFile(yaml);
            String text = new String(jar.getInputStream(jar.getJarEntry("paper-libraries.txt")).readAllBytes(), StandardCharsets.UTF_8);
            for (String line : text.lines().filter(value -> !value.isBlank()).toList()) {
                String[] fields = line.split(" ");
                manifest.add(new Library(fields[0], fields[1]));
            }
            require(description.getLibraries().equals(manifest.stream().map(Library::coordinate).toList()),
                    "plugin.yml and the resolved library manifest differ");
            for (String prefix : List.of("kotlin/", "com/mongodb/", "org/bson/", "org/mongojack/",
                    "com/fasterxml/", "redis/clients/", "org/openjdk/nashorn/", "org/objectweb/asm/")) {
                require(jar.stream().noneMatch(entry -> entry.getName().startsWith(prefix) && entry.getName().endsWith(".class")),
                        "Remote library still bundled: " + prefix);
            }
        }

        Logger logger = Logger.getLogger("paper-library-smoke");
        logger.setLevel(Level.WARNING);
        var factory = LibraryLoader.LIBRARY_LOADER_FACTORY;
        var remapper = LibraryLoader.REMAPPER;
        // Hide Gradle's runtime libraries to prove that the downloaded JARs supply them.
        ClassLoader apiParent = new ClassLoader(ClassLoader.getPlatformClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("org.bukkit.") || name.startsWith("io.papermc.") || name.startsWith("net.minecraft.")
                        || name.startsWith("org.slf4j.") || name.startsWith("com.google.common.")
                        || name.startsWith("net.kyori.") || name.startsWith("it.unimi.dsi.fastutil.")) {
                    return PaperLibrariesSmokeTool.class.getClassLoader().loadClass(name);
                }
                return super.loadClass(name, resolve);
            }
        };
        LibraryLoader.LIBRARY_LOADER_FACTORY = (urls, parent) -> new URLClassLoader(urls, apiParent);
        LibraryLoader.REMAPPER = null;
        try {
            System.out.println("Paper native loader: first download into " + cache);
            try (URLClassLoader libraries = (URLClassLoader) loader(logger, cache, false).createLoader(description)) {
                verifyRuntime(libraries, plugin);
            }
            Map<Path, Long> timestamps = verifyCache(cache, manifest);
            System.out.println("First download and isolated runtime checks passed: " + manifest.size() + " libraries");

            try (URLClassLoader libraries = (URLClassLoader) loader(logger, cache, true).createLoader(description)) {
                verifyRuntime(libraries, plugin);
            }
            require(timestamps.equals(verifyCache(cache, manifest)), "Offline startup rewrote cached library JARs");
            System.out.println("Offline cache reuse passed");

            Path emptyCache = Files.createTempDirectory(output, "empty-cache-");
            boolean rejected = false;
            try {
                ClassLoader unexpected = loader(logger, emptyCache, true).createLoader(description);
                if (unexpected instanceof URLClassLoader closeable) closeable.close();
            } catch (RuntimeException expected) {
                rejected = expected.getCause() instanceof org.eclipse.aether.resolution.DependencyResolutionException;
            }
            require(rejected, "An offline first startup with no cached dependencies must fail");
            String report = "Java: " + Runtime.version() + "\nPlugin: " + plugin + "\nCache: " + cache
                    + "\nResolved libraries: " + manifest.size()
                    + "\nPASS: fresh download, SHA-256, thin JAR and isolated runtime"
                    + "\nPASS: offline cache reuse without rewriting JARs"
                    + "\nPASS: offline first startup rejects missing dependencies\n";
            Files.writeString(output.resolve("result.txt"), report);
            System.out.print(report);
        } finally {
            LibraryLoader.LIBRARY_LOADER_FACTORY = factory;
            LibraryLoader.REMAPPER = remapper;
        }
    }

    private static LibraryLoader loader(Logger logger, Path cache, boolean offline) throws Exception {
        LibraryLoader loader = new LibraryLoader(logger);
        // Only the smoke check overrides the cache directory and offline flag.
        // Production uses Paper's untouched native loader and its libraries/ directory.
        Field repositoryField = LibraryLoader.class.getDeclaredField("repository");
        repositoryField.setAccessible(true);
        RepositorySystem repository = (RepositorySystem) repositoryField.get(loader);
        Field sessionField = LibraryLoader.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(
                (DefaultRepositorySystemSession) sessionField.get(loader));
        session.setLocalRepositoryManager(repository.newLocalRepositoryManager(session, new LocalRepository(cache.toFile())));
        session.setOffline(offline);
        session.setReadOnly();
        sessionField.set(loader, session);
        return loader;
    }

    private static Map<Path, Long> verifyCache(Path cache, List<Library> manifest) throws Exception {
        Map<Path, Long> timestamps = new LinkedHashMap<>();
        for (Library library : manifest) {
            Path file = library.path(cache);
            require(Files.isRegularFile(file), "Missing cached library: " + library.coordinate());
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
            require(actual.equals(library.sha256()), "Downloaded library differs from build: " + library.coordinate());
            timestamps.put(file, Files.getLastModifiedTime(file).toMillis());
        }
        return timestamps;
    }

    private static void verifyRuntime(URLClassLoader libraries, Path plugin) throws Exception {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(libraries);
        try (URLClassLoader core = new URLClassLoader(new URL[]{plugin.toUri().toURL()}, libraries)) {
            for (String name : List.of("kotlin.jvm.internal.Intrinsics", "kotlin.reflect.jvm.internal.ReflectionFactoryImpl",
                    "com.mongodb.client.MongoClients", "org.mongojack.JacksonMongoCollection", "redis.clients.jedis.Jedis",
                    "com.fasterxml.jackson.databind.ObjectMapper", "org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory")) {
                require(Class.forName(name, true, libraries).getClassLoader() == libraries, "Library leaked from build classpath: " + name);
            }
            Class<?> mapperClass = libraries.loadClass("com.fasterxml.jackson.databind.ObjectMapper");
            Object mapper = mapperClass.getConstructor().newInstance();
            mapperClass.getMethod("findAndRegisterModules").invoke(mapper);
            String json = (String) mapperClass.getMethod("writeValueAsString", Object.class).invoke(mapper, Map.of("value", 42));
            require(json.equals("{\"value\":42}"), "Jackson runtime failed");
            ScriptEngine engine = new ScriptEngineManager(libraries).getEngineByName("nashorn");
            require(engine != null && ((Number) engine.eval("40 + 2")).intValue() == 42, "Nashorn service loading failed");
            Class<?> gson = core.loadClass("net.mizukilab.pit.libs.mythicframework.gson.Gson");
            gson.getConstructor().newInstance();
            require(gson.getClassLoader() == core, "Relocated framework library was not retained in the core");
            require(Class.forName("cn.charlotte.pit.ThePit", false, core).getClassLoader() == core,
                    "Thin plugin entrypoint failed to load");
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
