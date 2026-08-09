package dev.viabridge.mixin;

import dev.viabridge.net.PipelineInjector;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The single hook into the game.
 *
 * <p>{@code channelActive} is inherited from Netty's {@code ChannelInboundHandlerAdapter},
 * so its name and descriptor are library-owned and survive Minecraft remapping and version
 * bumps untouched. Minecraft's own networking methods were the obvious alternative, but
 * their signatures churn across the 1.21.x line; anchoring to Netty keeps this mixin from
 * being the fragile part of the mod.
 *
 * <p>By the time this fires, the pipeline is fully built and the connection is live, but
 * the handshake has not been written yet — the client only sends it once the connect
 * future resolves. That is exactly the window the translation handlers need to be in
 * place.
 */
@Mixin(Connection.class)
public class MixinConnection {

    @Inject(method = "channelActive", at = @At("TAIL"))
    private void viabridge$installTranslation(final ChannelHandlerContext ctx, final CallbackInfo ci) {
        PipelineInjector.install(ctx.pipeline());
    }
}
