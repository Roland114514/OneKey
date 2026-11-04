package me.easan.onekey.neoforge.net;

import io.netty.buffer.ByteBuf;
import me.easan.onekey.common.net.QuickStackRequest;
import me.easan.onekey.neoforge.OneKeyNeoForge;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：快速堆叠 / 一键存入请求。
 */
public record QuickStackRequestPayload(QuickStackRequest request) implements CustomPacketPayload {

    public static final Type<QuickStackRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OneKeyNeoForge.MOD_ID, "quick_stack_request"));

    public static final StreamCodec<ByteBuf, QuickStackRequestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, p -> p.request().syncId(),
            ByteBufCodecs.VAR_INT, p -> p.request().radius(),
            ByteBufCodecs.BOOL, p -> p.request().stash(),
            (syncId, radius, stash) -> new QuickStackRequestPayload(new QuickStackRequest(syncId, radius, stash))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
