package forge.download;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Tails the durable log without connecting the updater's lifetime to the game. */
final class DiyUpdateLog {
    private DiyUpdateLog() { }

    static void follow(final Process process, final Path log, final Consumer<String> output)
            throws IOException, InterruptedException {
        follow(process, log, output, () -> { });
    }

    static void follow(final Process process, final Path log, final Consumer<String> output, final Runnable poll)
            throws IOException, InterruptedException {
        final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
        final ByteBuffer bytes = ByteBuffer.allocate(8192);
        final CharBuffer chars = CharBuffer.allocate(8192);
        try (FileChannel channel = FileChannel.open(log, StandardOpenOption.READ)) {
            boolean ended = false;
            while (true) {
                poll.run();
                final int count = channel.read(bytes);
                bytes.flip();
                decoder.decode(bytes, chars, ended && count <= 0);
                chars.flip();
                if (chars.hasRemaining()) {
                    output.accept(chars.toString());
                }
                chars.clear();
                bytes.compact(); // Retain incomplete UTF-8 characters across file writes.
                if (count > 0) {
                    continue;
                }
                if (ended) {
                    break;
                }
                ended = process.waitFor(200, TimeUnit.MILLISECONDS);
            }
        }
    }
}
