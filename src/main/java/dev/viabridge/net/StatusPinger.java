package dev.viabridge.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import dev.viabridge.config.BridgeSettings;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A Server List Ping client, written directly against Netty.
 *
 * <p>Deliberately built from raw bytes rather than from Minecraft's own networking
 * classes: the status handshake has been byte-for-byte stable since 1.7, so this one
 * implementation talks to every server version we care about, and — more usefully — it
 * compiles against no Minecraft class at all, so it cannot break when mappings shift.
 */
final class StatusPinger {

    /**
     * A dedicated event loop. Callers block on the returned future from inside the game's
     * own Netty threads, so the ping must never share a thread with them.
     */
    private static final EventLoopGroup GROUP =
        new NioEventLoopGroup(1, new DefaultThreadFactory("ViaBridge-Ping", true));

    private static final int CONNECT_TIMEOUT_MILLIS = 5000;
    private static final int READ_TIMEOUT_SECONDS = 10;

    private StatusPinger() {
    }

    static CompletableFuture<ProtocolVersion> ping(final InetSocketAddress address) {
        final CompletableFuture<ProtocolVersion> result = new CompletableFuture<>();

        new Bootstrap()
            .group(GROUP)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
            .option(ChannelOption.TCP_NODELAY, true)
            .handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(final Channel channel) {
                    channel.pipeline()
                        .addLast("timeout", new ReadTimeoutHandler(READ_TIMEOUT_SECONDS))
                        .addLast("splitter", new VarIntFrameDecoder())
                        .addLast("status", new StatusHandler(address, result));
                }
            })
            .connect(address)
            .addListener(future -> {
                if (!future.isSuccess()) {
                    result.completeExceptionally(future.cause());
                }
            });

        return result;
    }

    /** Splits the stream into packets using the VarInt length prefix. */
    private static final class VarIntFrameDecoder extends ByteToMessageDecoder {
        @Override
        protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) {
            in.markReaderIndex();

            // A VarInt is at most 5 bytes; bail out and wait for more if it is truncated.
            for (int i = 0; i < 5; i++) {
                if (!in.isReadable()) {
                    in.resetReaderIndex();
                    return;
                }
                if ((in.readByte() & 0x80) == 0) {
                    in.resetReaderIndex();
                    final int length = readVarInt(in);
                    if (in.readableBytes() < length) {
                        in.resetReaderIndex();
                        return;
                    }
                    out.add(in.readBytes(length));
                    return;
                }
            }
            throw new IllegalStateException("VarInt length prefix longer than 5 bytes");
        }
    }

    private static final class StatusHandler extends SimpleChannelInboundHandler<ByteBuf> {

        private final InetSocketAddress address;
        private final CompletableFuture<ProtocolVersion> result;

        private StatusHandler(final InetSocketAddress address,
                              final CompletableFuture<ProtocolVersion> result) {
            this.address = address;
            this.result = result;
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) {
            // Handshake (id 0x00) with next-state = 1 (status), then an empty status
            // request (also id 0x00).
            final ByteBuf handshake = ctx.alloc().buffer();
            writeVarInt(handshake, 0x00);
            writeVarInt(handshake, BridgeSettings.NATIVE_PROTOCOL);
            writeString(handshake, hostname());
            handshake.writeShort(address.getPort());
            writeVarInt(handshake, 1);
            ctx.write(framed(ctx, handshake));

            final ByteBuf request = ctx.alloc().buffer();
            writeVarInt(request, 0x00);
            ctx.writeAndFlush(framed(ctx, request));
        }

        /**
         * Prefer the unresolved hostname — servers behind virtual hosts key off it, and
         * sending a bare IP can get a different (or no) status response.
         */
        private String hostname() {
            if (address.isUnresolved() || address.getHostString() != null) {
                return address.getHostString();
            }
            return address.getAddress().getHostAddress();
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, final ByteBuf packet) {
            final int packetId = readVarInt(packet);
            if (packetId != 0x00) {
                return;
            }

            try {
                final JsonElement parsed = JsonParser.parseString(readString(packet));
                final int protocol = parsed.getAsJsonObject()
                    .getAsJsonObject("version")
                    .get("protocol")
                    .getAsInt();
                result.complete(ProtocolVersion.getProtocol(protocol));
            } catch (final RuntimeException e) {
                result.completeExceptionally(
                    new IllegalStateException("Malformed status response", e));
            } finally {
                ctx.close();
            }
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) {
            result.completeExceptionally(new IllegalStateException("Connection closed before a status response"));
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
            result.completeExceptionally(cause);
            ctx.close();
        }

        private ByteBuf framed(final ChannelHandlerContext ctx, final ByteBuf payload) {
            final ByteBuf out = ctx.alloc().buffer();
            writeVarInt(out, payload.readableBytes());
            out.writeBytes(payload);
            payload.release();
            return out;
        }
    }

    // -- VarInt / String primitives -------------------------------------------------

    private static void writeVarInt(final ByteBuf buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    private static int readVarInt(final ByteBuf buf) {
        int result = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            final byte read = buf.readByte();
            result |= (read & 0x7F) << shift;
            if ((read & 0x80) == 0) {
                return result;
            }
        }
        throw new IllegalStateException("VarInt longer than 5 bytes");
    }

    private static void writeString(final ByteBuf buf, final String value) {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(final ByteBuf buf) {
        final int length = readVarInt(buf);
        final byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
