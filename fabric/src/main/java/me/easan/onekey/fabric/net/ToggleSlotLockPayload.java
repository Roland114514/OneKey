package me.easan.onekey.fabric.net;

import io.netty.buffer.ByteBuf;
import me.easan.onekey.fabric.OneKeyFabric;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：切换指定槽位的锁定状态。
 */
public record ToggleSlotLockPayload(String containerKey, int slotIndex) implements CustomPacketPayload {

    public static final Type<ToggleSlotLockPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OneKeyFabric.MOD_ID, "toggle_slot_lock"));

    public static final StreamCodec<ByteBuf, ToggleSlotLockPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ToggleSlotLockPayload::containerKey,
            ByteBufCodecs.VAR_INT, ToggleSlotLockPayload::slotIndex,
            ToggleSlotLockPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
