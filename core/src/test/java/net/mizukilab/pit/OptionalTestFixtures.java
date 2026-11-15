package net.mizukilab.pit;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assume;

/** Missing external plugin binaries are reported as skipped integration checks. */
public final class OptionalTestFixtures {
    private OptionalTestFixtures() { }
    public static void require(String... properties) {
        for (String property : properties) {
            String value = System.getProperty(property);
            Assume.assumeTrue("External fixture unavailable: " + property + "=" + value,
                    value != null && Files.isRegularFile(Path.of(value)));
        }
    }
}
