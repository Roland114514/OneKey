package me.easan.onekey.common.net;

/**
 * 网络协议版本。客户端与服务端不一致时拒绝协同功能（避免静默错位）。
 */
public final class ProtocolVersion {

    public static final int CURRENT = 1;

    private ProtocolVersion() {
    }
}
