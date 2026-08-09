package dev.viabridge.net;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.platform.ViaChannelInitializer;
import com.viaversion.viaversion.platform.ViaDecodeHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

/**
 * Via's decoder, plus the bookkeeping needed when compression is switched on mid-session.
 *
 * <p>The server enables compression during login, which inserts {@code compress} /
 * {@code decompress} handlers into the pipeline. They land <em>after</em> our handlers,
 * which would leave Via translating still-compressed bytes. Via ships the fix
 * ({@link ViaChannelInitializer#reorderPipeline}); the only job here is noticing that the
 * moment has arrived.
 *
 * <p>Rather than relying on an event fired from a mixin, this watches the pipeline itself:
 * once a decompressor shows up, reorder once and stop looking. That keeps the behaviour
 * independent of Minecraft's internals, and it still honours the explicit events other
 * implementations (and Krypton) emit.
 */
@ChannelHandler.Sharable
public final class BridgeDecodeHandler extends ViaDecodeHandler {

    private static final String COMPRESS = "compress";
    private static final String DECOMPRESS = "decompress";

    private boolean reordered;

    public BridgeDecodeHandler(final UserConnection connection) {
        super(connection);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (!reordered && ctx.pipeline().get(DECOMPRESS) != null) {
            reorder(ctx);
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object event) throws Exception {
        final String name = String.valueOf(event);
        if ("COMPRESSION_THRESHOLD_UPDATED".equals(name) || "COMPRESSION_ENABLED".equals(name)) {
            reorder(ctx);
        }
        super.userEventTriggered(ctx, event);
    }

    private void reorder(final ChannelHandlerContext ctx) {
        ViaChannelInitializer.reorderPipeline(ctx.pipeline(), COMPRESS, DECOMPRESS);
        reordered = true;
    }
}
