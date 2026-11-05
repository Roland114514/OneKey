package me.easan.onekey.neoforge.net;

import io.netty.buffer.ByteBuf;
import me.easan.onekey.neoforge.OneKeyNeoForge;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端向服务端同步连锁按键按压状态与选定挖掘模式的数据包。
 */
public record ExcavatorStatePayload(boolean isPressed, int shapeId) implements CustomPacketPayload {

    public static final Type<ExcavatorStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OneKeyNeoForge.MOD_ID, "excavator_state"));

    public static final StreamCodec<ByteBuf, ExcavatorStatePayload> CODEC = new StreamCodec<>() {
        @Override
        public ExcavatorStatePayload decode(ByteBuf buffer) {
            boolean isPressed = buffer.readBoolean();
            int shapeId = ByteBufCodecs.VAR_INT.decode(buffer);
            return new ExcavatorStatePayload(isPressed, shapeId);
        }

        @Override
        public void encode(ByteBuf buffer, ExcavatorStatePayload value) {
            buffer.writeBoolean(value.isPressed());
            ByteBufCodecs.VAR_INT.encode(buffer, value.shapeId());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
