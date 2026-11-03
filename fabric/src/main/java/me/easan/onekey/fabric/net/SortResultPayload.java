package me.easan.onekey.fabric.net;

import io.netty.buffer.ByteBuf;
import me.easan.onekey.common.net.SortResult;
import me.easan.onekey.fabric.OneKeyFabric;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端到客户端的整理结果网络包。
 */
public record SortResultPayload(SortResult result) implements CustomPacketPayload {

    public static final Type<SortResultPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OneKeyFabric.MOD_ID, "sort_result"));

    public static final StreamCodec<ByteBuf, SortResultPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, p -> p.result().success(),
            ByteBufCodecs.VAR_INT, p -> p.result().moved(),
            (success, moved) -> new SortResultPayload(new SortResult(success, moved))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
