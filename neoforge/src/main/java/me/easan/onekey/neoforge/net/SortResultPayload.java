package me.easan.onekey.neoforge.net;

import io.netty.buffer.ByteBuf;
import me.easan.onekey.common.net.SortResult;
import me.easan.onekey.neoforge.OneKeyNeoForge;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端：一键整理执行结果回执。
 */
public record SortResultPayload(SortResult result) implements CustomPacketPayload {

    public static final Type<SortResultPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OneKeyNeoForge.MOD_ID, "sort_result"));

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
