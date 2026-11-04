package me.easan.onekey.neoforge.net;

import io.netty.buffer.ByteBuf;
import me.easan.onekey.common.net.QuickStackResult;
import me.easan.onekey.neoforge.OneKeyNeoForge;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端：快速堆叠 / 一键存入执行结果回执。
 */
public record QuickStackResultPayload(QuickStackResult result) implements CustomPacketPayload {

    public static final Type<QuickStackResultPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OneKeyNeoForge.MOD_ID, "quick_stack_result"));

    public static final StreamCodec<ByteBuf, QuickStackResultPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, p -> p.result().moved(),
            moved -> new QuickStackResultPayload(new QuickStackResult(moved))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
