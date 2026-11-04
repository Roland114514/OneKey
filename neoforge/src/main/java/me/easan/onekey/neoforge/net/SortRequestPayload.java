package me.easan.onekey.neoforge.net;

import io.netty.buffer.ByteBuf;
import me.easan.onekey.common.net.SortRequest;
import me.easan.onekey.neoforge.OneKeyNeoForge;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：一键整理请求。
 */
public record SortRequestPayload(SortRequest request) implements CustomPacketPayload {

    public static final Type<SortRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OneKeyNeoForge.MOD_ID, "sort_request"));

    public static final StreamCodec<ByteBuf, SortRequestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, p -> p.request().syncId(),
            ByteBufCodecs.VAR_INT, p -> p.request().mode(),
            (syncId, mode) -> new SortRequestPayload(new SortRequest(syncId, mode))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
