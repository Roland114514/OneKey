package me.easan.onekey.common.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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

    /**
     * 从 JSON 文本解析；null、空文本或非法 JSON 一律返回默认配置。
     *
     * <p>配置文件是玩家可编辑的外部输入：手改出错、磁盘写满导致截断、旧版本字段类型变化都会产生非法 JSON。
     * 此时必须降级为默认值让游戏照常启动，绝不能把解析异常抛进模组入口导致启动崩溃。</p>
     */
    public static OneKeyConfig fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new OneKeyConfig();
        }
        try {
            OneKeyConfig parsed = GSON.fromJson(json, OneKeyConfig.class);
            return parsed == null ? new OneKeyConfig() : parsed.normalized();
        } catch (RuntimeException exception) {
            // JsonSyntaxException / IllegalStateException 等：保留默认值继续运行
            return new OneKeyConfig();
        }
    }

    /** 判断一段文本能否被安全解析（供上层决定是否备份损坏文件）。 */
    public static boolean isValidJson(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            GSON.fromJson(json, OneKeyConfig.class);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 配置文件内容损坏时，把原文件重命名成 {@code <名字>.corrupt} 留档，避免被默认值静默覆盖。
     *
     * @return 备份后的路径；文件不存在或内容合法时返回 {@code null}
     */
    public static Path backupIfCorrupted(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        if (isValidJson(raw)) {
            return null;
        }
        Path backup = path.resolveSibling(path.getFileName() + ".corrupt");
        Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
        return backup;
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

    /**
     * 写入配置文件，自动创建父目录。
     *
     * <p>采用「临时文件 + 原子替换」落盘：直接覆盖写在进程崩溃或断电时会留下截断的半个 JSON，
     * 下次启动即变成损坏配置。原子替换保证磁盘上永远是完整的旧版或完整的新版。</p>
     */
    public static void save(Path path, OneKeyConfig config) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path 不能为 null");
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, toJson(config), StandardCharsets.UTF_8);
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException notSupported) {
            // 部分文件系统（如某些网络盘）不支持原子移动时平滑降级
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
