package forge;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNull;

public class CardStorageReaderTest {
    @Test
    public void missingNonLatinCardNameDoesNotCrashLazyLoading() throws Exception {
        final Path emptyCardsDirectory = Files.createTempDirectory("forge-empty-cards");
        try {
            final CardStorageReader reader = new CardStorageReader(
                    emptyCardsDirectory.toString(), null, true);

            assertNull(reader.attemptToLoadCard("\u7089\u77f3\u4f20\u8bf4"));
        } finally {
            Files.deleteIfExists(emptyCardsDirectory);
        }
    }
}
