package me.easan.onekey.fabric.net;

import io.netty.buffer.ByteBuf;
import me.easan.onekey.common.net.QuickStackRequest;
import me.easan.onekey.fabric.OneKeyFabric;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：请求快速堆叠 / 存入附近容器。
 */
public record QuickStackRequestPayload(QuickStackRequest request) implements CustomPacketPayload {

    public static final Type<QuickStackRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OneKeyFabric.MOD_ID, "quick_stack_request"));

    public static final StreamCodec<ByteBuf, QuickStackRequestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, r -> r.request().syncId(),
            ByteBufCodecs.VAR_INT, r -> r.request().radius(),
            ByteBufCodecs.BOOL, r -> r.request().stash(),
            (syncId, radius, stash) -> new QuickStackRequestPayload(new QuickStackRequest(syncId, radius, stash))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
