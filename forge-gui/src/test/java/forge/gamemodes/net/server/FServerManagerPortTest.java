package forge.gamemodes.net.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FServerManagerPortTest {
    @TempDir
    Path tempDirectory;

    @Test
    void reportsTheActualPortChosenByTheOperatingSystem() throws InterruptedException {
        final EventLoopGroup group = new NioEventLoopGroup(1);
        Channel channel = null;
        try {
            channel = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(final SocketChannel socketChannel) {
                        }
                    })
                    .bind(0)
                    .sync()
                    .channel();

            final int actualPort = ((InetSocketAddress) channel.localAddress()).getPort();
            assertTrue(actualPort > 0 && actualPort <= 65535);
            assertEquals(actualPort, FServerManager.boundPort(channel));
        } finally {
            if (channel != null) {
                channel.close().sync();
            }
            group.shutdownGracefully().sync();
        }
    }

    @Test
    void publishesAndClearsOnlyTheCurrentBoundPort() throws Exception {
        final Path marker = tempDirectory.resolve("state").resolve("active-server-port");

        FServerManager.writeBoundPortMarker(marker, 54321);
        assertEquals("54321", Files.readString(marker, StandardCharsets.UTF_8).trim());

        FServerManager.clearBoundPortMarker(marker, 12345);
        assertTrue(Files.isRegularFile(marker), "a stale server shutdown must not remove a newer marker");

        FServerManager.clearBoundPortMarker(marker, 54321);
        assertFalse(Files.exists(marker));
    }
}
