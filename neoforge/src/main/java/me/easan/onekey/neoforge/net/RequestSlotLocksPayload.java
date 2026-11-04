package me.easan.onekey.neoforge.net;

import io.netty.buffer.ByteBuf;
import me.easan.onekey.neoforge.OneKeyNeoForge;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：请求同步特定容器的槽位锁定状态。
 */
public record RequestSlotLocksPayload(String containerKey) implements CustomPacketPayload {

    public static final Type<RequestSlotLocksPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OneKeyNeoForge.MOD_ID, "request_slot_locks"));

    public static final StreamCodec<ByteBuf, RequestSlotLocksPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestSlotLocksPayload::containerKey,
            RequestSlotLocksPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
