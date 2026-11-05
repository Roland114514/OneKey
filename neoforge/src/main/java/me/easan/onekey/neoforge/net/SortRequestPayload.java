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

    public static final StreamCodec<ByteBuf, SortRequestPayload> CODEC = new StreamCodec<>() {
        @Override
        public SortRequestPayload decode(ByteBuf buffer) {
            int syncId = ByteBufCodecs.VAR_INT.decode(buffer);
            int mode = ByteBufCodecs.VAR_INT.decode(buffer);
            int options = buffer.isReadable() ? ByteBufCodecs.VAR_INT.decode(buffer) : SortRequest.NO_CUSTOM_OPTIONS;
            return new SortRequestPayload(new SortRequest(syncId, mode, options));
        }

        @Override
        public void encode(ByteBuf buffer, SortRequestPayload value) {
            ByteBufCodecs.VAR_INT.encode(buffer, value.request().syncId());
            ByteBufCodecs.VAR_INT.encode(buffer, value.request().mode());
            ByteBufCodecs.VAR_INT.encode(buffer, value.request().optionsBitmask());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
