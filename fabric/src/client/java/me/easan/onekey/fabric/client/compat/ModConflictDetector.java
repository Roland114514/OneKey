package me.easan.onekey.fabric.client.compat;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 客户端模组冲突检测器：
 * 检测是否存在同类库存操作模组（IPN、Inventory Sorter、Mouse Tweaks、Item Scroller 等），
 * 避免按键或鼠标手势冲突，给出友好提示。
 */
public final class ModConflictDetector {

    private static final List<String> KNOWN_MODS = List.of(
            "inventoryprofilesnext",
            "inventorysorter",
            "mousetweaks",
            "itemscroller"
    );

    private ModConflictDetector() {
    }

    public static List<String> detectConflicts() {
        List<String> found = new ArrayList<>();
        FabricLoader loader = FabricLoader.getInstance();
        for (String modId : KNOWN_MODS) {
            if (loader.isModLoaded(modId)) {
                found.add(modId);
            }
        }
        return found;
    }
}
