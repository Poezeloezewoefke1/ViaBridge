package dev.viabridge.net;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.platform.ViaChannelInitializer;
import com.viaversion.viaversion.platform.ViaDecodeHandler;
import com.viaversion.viaversion.platform.ViaEncodeHandler;
import dev.viabridge.ViaBridge;
import dev.viabridge.config.BridgeSettings;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

/**
 * Splices Via's translating handlers into a connection's Netty pipeline.
 *
 * <p>Placement matters: the encoder must sit immediately upstream of Minecraft's packet
 * encoder so it rewrites packets after the game has serialised them, and the decoder
 * immediately upstream of the game's decoder so it rewrites inbound bytes before the game
 * tries to parse them as 1.21.4.
 *
 * <p>Anchor handlers are looked up by name against a candidate list rather than a single
 * hard-coded name. Those names are plain strings inside Minecraft's networking setup and
 * are not remapped, but they have been renamed across versions; probing keeps this working
 * across the 1.21.x line instead of only the exact build it was compiled on.
 */
public final class PipelineInjector {

    /** Anchors for the outbound (encoding) side, most specific first. */
    private static final String[] ENCODER_ANCHORS = {"encoder", "outbound_config"};

    /** Anchors for the inbound (decoding) side. */
    private static final String[] DECODER_ANCHORS = {"inbound_config", "decoder"};

    private PipelineInjector() {
    }

    /**
     * Installs the handlers if this pipeline is one we should translate.
     *
     * <p>Safe to call more than once per channel; the second call is a no-op. That matters
     * because the injection is driven from two different hooks so that a signature change
     * in either one is not fatal.
     */
    public static void install(final ChannelPipeline pipeline) {
        final BridgeSettings settings = ViaBridge.settings();
        if (settings == null || !settings.enabled()) {
            return;
        }

        final Channel channel = pipeline.channel();

        // A LocalChannel means singleplayer / the integrated server: same process, same
        // version, nothing to translate.
        if (!(channel instanceof SocketChannel)) {
            return;
        }

        // An accepted connection has the listening socket as its parent. That is the
        // open-to-LAN case where we are the server; only connections we initiated get
        // translated.
        if (channel.parent() != null) {
            return;
        }

        if (pipeline.get(ViaEncodeHandler.NAME) != null) {
            return;
        }

        final String encoderAnchor = firstPresent(pipeline, ENCODER_ANCHORS);
        final String decoderAnchor = firstPresent(pipeline, DECODER_ANCHORS);
        if (encoderAnchor == null || decoderAnchor == null) {
            ViaBridge.LOGGER.warning("Could not find the packet codec handlers in the "
                + "connection pipeline " + pipeline.names() + " — leaving this connection "
                + "untranslated. This usually means the mod is running on a Minecraft "
                + "version it was not built for.");
            return;
        }

        // Builds the UserConnection and its protocol pipeline; Via fills in the actual
        // protocol path once it sees the handshake.
        final UserConnection connection = ViaChannelInitializer.createUserConnection(channel, true);

        pipeline.addBefore(encoderAnchor, ViaEncodeHandler.NAME, new ViaEncodeHandler(connection));
        pipeline.addBefore(decoderAnchor, ViaDecodeHandler.NAME, new BridgeDecodeHandler(connection));
    }

    private static String firstPresent(final ChannelPipeline pipeline, final String[] candidates) {
        for (final String name : candidates) {
            if (pipeline.get(name) != null) {
                return name;
            }
        }
        return null;
    }
}
