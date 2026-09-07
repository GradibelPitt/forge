package forge.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildInfoTest {
    private static final String RUNTIME_VERSION_PROPERTY = "forge.runtime.version";

    private final String previousRuntimeVersion = System.getProperty(RUNTIME_VERSION_PROPERTY);

    @AfterEach
    void restoreRuntimeVersion() {
        if (previousRuntimeVersion == null) {
            System.clearProperty(RUNTIME_VERSION_PROPERTY);
        } else {
            System.setProperty(RUNTIME_VERSION_PROPERTY, previousRuntimeVersion);
        }
    }

    @Test
    void runtimeVersionOverridesMissingOverlayManifestVersion() {
        System.setProperty(RUNTIME_VERSION_PROPERTY, "20260907-network-autodetect-v1");

        assertEquals("20260907-network-autodetect-v1", BuildInfo.getVersionString());
    }

    @Test
    void blankRuntimeVersionIsIgnored() {
        System.setProperty(RUNTIME_VERSION_PROPERTY, "   ");

        final String packageVersion = BuildInfo.class.getPackage().getImplementationVersion();
        assertEquals(packageVersion == null ? "GIT" : packageVersion, BuildInfo.getVersionString());
    }
}
