package me.easan.onekey.common.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 配置读写（Gson JSON）。纯 Java 实现，平台层只需提供文件路径。
 */
public final class ConfigCodec {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private ConfigCodec() {
    }

    /** 从 JSON 文本解析；null 或空文本返回默认配置。 */
    public static OneKeyConfig fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new OneKeyConfig();
        }
        OneKeyConfig parsed = GSON.fromJson(json, OneKeyConfig.class);
        return parsed == null ? new OneKeyConfig() : parsed.normalized();
    }

    /** 序列化为带缩进的 JSON。 */
    public static String toJson(OneKeyConfig config) {
        return GSON.toJson(config == null ? new OneKeyConfig() : config.normalized());
    }

    /** 读取配置文件；文件不存在时返回默认配置（不创建文件）。 */
    public static OneKeyConfig load(Path path) throws IOException {
        if (path == null || Files.notExists(path)) {
            return new OneKeyConfig();
        }
        return fromJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    /** 写入配置文件，自动创建父目录。 */
    public static void save(Path path, OneKeyConfig config) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path 不能为 null");
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, toJson(config), StandardCharsets.UTF_8);
    }
}
