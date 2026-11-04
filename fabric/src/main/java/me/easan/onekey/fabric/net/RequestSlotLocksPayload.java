package me.easan.onekey.fabric.net;

import io.netty.buffer.ByteBuf;
import me.easan.onekey.fabric.OneKeyFabric;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：请求指定容器类型的锁定槽位列表。
 */
public record RequestSlotLocksPayload(String containerKey) implements CustomPacketPayload {

    public static final Type<RequestSlotLocksPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OneKeyFabric.MOD_ID, "request_slot_locks"));

    public static final StreamCodec<ByteBuf, RequestSlotLocksPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestSlotLocksPayload::containerKey,
            RequestSlotLocksPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
