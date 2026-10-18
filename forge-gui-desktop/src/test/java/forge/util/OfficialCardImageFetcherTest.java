package forge.util;

import com.sun.net.httpserver.HttpServer;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

public class OfficialCardImageFetcherTest {
    @Test
    public void jpegScansArePreservedByteForByte() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64, 90, BufferedImage.TYPE_INT_RGB), "jpg", bytes);
        withServer(200, bytes.toByteArray(), (url, destination) -> {
            OfficialCardImageFetcher.save(url, destination);
            Assert.assertEquals(Files.readAllBytes(destination), bytes.toByteArray());
        });

    }

    @Test
    public void transparentImageIsSavedAsACompleteJpeg() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64, 90, BufferedImage.TYPE_INT_ARGB), "png", bytes);
        withServer(200, bytes.toByteArray(), (url, destination) -> {
            OfficialCardImageFetcher.save(url, destination);
            BufferedImage actual = ImageIO.read(destination.toFile());
            Assert.assertEquals(actual.getWidth(), 64);
            Assert.assertEquals(actual.getHeight(), 90);
            Assert.assertFalse(actual.getColorModel().hasAlpha());
            Assert.assertEquals(actual.getRGB(20, 20) & 0xffffff, 0xffffff);
            try (var files = Files.list(destination.getParent())) {
                Assert.assertEquals(files.count(), 1L);
            }
        });
    }

    @Test
    public void invalidImageCannotReplaceAnExistingCacheFile() throws Exception {
        withServer(200, "<html>not an image</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                (url, destination) -> {
                    Files.writeString(destination, "previous");
                    Assert.expectThrows(IOException.class, () -> OfficialCardImageFetcher.save(url, destination));
                    Assert.assertEquals(Files.readString(destination), "previous");
                });
    }

    @Test
    public void serverFailureCannotProduceACacheFile() throws Exception {
        withServer(503, new byte[0], (url, destination) -> {
            Assert.expectThrows(IOException.class, () -> OfficialCardImageFetcher.save(url, destination));
            Assert.assertFalse(Files.exists(destination));
        });
    }

    @Test
    public void faceKeysStaySeparate() {
        Assert.assertEquals(OfficialCardImageFetcher.face("c:Card|SET|1"), "");
        Assert.assertEquals(OfficialCardImageFetcher.face("c:Card|SET|1" + forge.ImageKeys.BACKFACE_POSTFIX), "back");
        Assert.assertEquals(OfficialCardImageFetcher.face("c:Card|SET|1" + forge.ImageKeys.SPECFACE_U), "blue");
    }

    @FunctionalInterface
    interface Check {
        void run(String url, Path destination) throws Exception;
    }

    private void withServer(int status, byte[] bytes, Check check) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/image", exchange -> {
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) { exchange.getResponseBody().write(bytes); }
            exchange.close();
        });
        Path directory = Files.createTempDirectory("forge-image-test-");
        server.start();
        try {
            check.run("http://127.0.0.1:" + server.getAddress().getPort() + "/image", directory.resolve("card.jpg"));
        } finally {
            server.stop(0);
            try (var files = Files.list(directory)) {
                for (Path file : files.toList()) { Files.deleteIfExists(file); }
            }
            Files.deleteIfExists(directory);
        }
    }
}
