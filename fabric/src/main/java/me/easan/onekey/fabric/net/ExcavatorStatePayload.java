package me.easan.onekey.fabric.net;

import io.netty.buffer.ByteBuf;
import me.easan.onekey.common.excavator.ExcavatorOptions;
import me.easan.onekey.fabric.OneKeyFabric;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端向服务端同步连锁按键按压状态、选定挖掘模式与玩家个人偏好。
 *
 * <p>1.1.2 起携带 {@code optionsMask}/{@code maxBlocks}：服务端把它与服主配置取交集，
 * 解决「多人服里玩家关掉连锁破坏却仍然触发」的问题。旧版客户端只发前两个字段，
 * 解码时按「无个人偏好」回退，保持单向兼容。</p>
 */
public record ExcavatorStatePayload(boolean isPressed, int shapeId, int optionsMask, int maxBlocks)
        implements CustomPacketPayload {

    public static final Type<ExcavatorStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OneKeyFabric.MOD_ID, "excavator_state"));

    public static final StreamCodec<ByteBuf, ExcavatorStatePayload> CODEC = new StreamCodec<>() {
        @Override
        public ExcavatorStatePayload decode(ByteBuf buffer) {
            boolean isPressed = buffer.readBoolean();
            int shapeId = ByteBufCodecs.VAR_INT.decode(buffer);
            int optionsMask = ExcavatorOptions.NO_CUSTOM_OPTIONS;
            int maxBlocks = ExcavatorOptions.DEFAULT.maxBlocks();
            if (buffer.isReadable()) {
                try {
                    optionsMask = ByteBufCodecs.VAR_INT.decode(buffer);
                    if (buffer.isReadable()) {
                        maxBlocks = ByteBufCodecs.VAR_INT.decode(buffer);
                    }
                } catch (RuntimeException ignored) {
                    optionsMask = ExcavatorOptions.NO_CUSTOM_OPTIONS;
                    maxBlocks = ExcavatorOptions.DEFAULT.maxBlocks();
                }
            }
            return new ExcavatorStatePayload(isPressed, shapeId, optionsMask, maxBlocks);
        }

        @Override
        public void encode(ByteBuf buffer, ExcavatorStatePayload value) {
            buffer.writeBoolean(value.isPressed());
            ByteBufCodecs.VAR_INT.encode(buffer, value.shapeId());
            ByteBufCodecs.VAR_INT.encode(buffer, value.optionsMask());
            ByteBufCodecs.VAR_INT.encode(buffer, value.maxBlocks());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
