package me.easan.onekey.fabric.client.gui;

import me.easan.onekey.common.config.OneKeyConfig;
import me.easan.onekey.fabric.OneKeyFabric;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * OneKey 图形设置界面：采用原版控件，零第三方依赖。
 * 支持调整排序规则开关、冷却、扫描半径、存入模式，并提供原版热键重绑入口与槽位锁定操作提示。
 */
public final class OneKeySettingsScreen extends Screen {

    private final Screen parent;

    public OneKeySettingsScreen(Screen parent) {
        super(Component.translatable("onekey.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        OneKeyConfig config = OneKeyFabric.config();

        int leftX = this.width / 2 - 155;
        int rightX = this.width / 2 + 5;
        int btnWidth = 150;
        int btnHeight = 20;

        // 第一行：整理总开关 / 按分类分组
        this.addRenderableWidget(CycleButton.onOffBuilder(config.sort.enabled)
                .create(leftX, 36, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.enabled"),
                        (button, value) -> config.sort.enabled = value));

        this.addRenderableWidget(CycleButton.onOffBuilder(config.sort.groupByCategory)
                .create(rightX, 36, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.groupByCategory"),
                        (button, value) -> config.sort.groupByCategory = value));

        // 第二行：按名称排序 / 按数量降序
        this.addRenderableWidget(CycleButton.onOffBuilder(config.sort.sortByName)
                .create(leftX, 60, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.sortByName"),
                        (button, value) -> config.sort.sortByName = value));

        this.addRenderableWidget(CycleButton.onOffBuilder(config.sort.sortByCountDesc)
                .create(rightX, 60, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.sortByCountDesc"),
                        (button, value) -> config.sort.sortByCountDesc = value));

        // 第三行：合并同类堆叠 / 整理包含快捷栏（默认开，玩家可自行关闭）
        this.addRenderableWidget(CycleButton.onOffBuilder(config.sort.mergeStacks)
                .create(leftX, 84, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.mergeStacks"),
                        (button, value) -> config.sort.mergeStacks = value));

        this.addRenderableWidget(CycleButton.onOffBuilder(config.sort.includeHotbar)
                .create(rightX, 84, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.includeHotbar"),
                        (button, value) -> config.sort.includeHotbar = value));

        // 第四行：冷却时间 / 堆叠扫描半径
        this.addRenderableWidget(CycleButton.<Integer>builder(val -> Component.literal(val + " ms"), () -> config.sort.cooldownMs)
                .withValues(100, 150, 200, 250, 300, 500)
                .create(leftX, 108, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.cooldown"),
                        (button, value) -> config.sort.cooldownMs = value));

        this.addRenderableWidget(CycleButton.<Integer>builder(val -> Component.literal(val + " 格"), () -> config.quickStack.radius)
                .withValues(2, 4, 6, 8)
                .create(rightX, 108, btnWidth, btnHeight, Component.translatable("onekey.settings.quickStack.radius"),
                        (button, value) -> config.quickStack.radius = value));

        // 第五行：存入模式 / 手势影响快捷栏（滚轮与 Shift 双击是否作用于 0..8）
        this.addRenderableWidget(CycleButton.<Boolean>builder(
                        val -> Component.translatable(val ? "onekey.settings.stashMode.all" : "onekey.settings.stashMode.matching"),
                        () -> config.quickStack.stashMode)
                .withValues(false, true)
                .create(leftX, 132, btnWidth, btnHeight, Component.translatable("onekey.settings.quickStack.stashMode"),
                        (button, value) -> config.quickStack.stashMode = value));

        this.addRenderableWidget(CycleButton.onOffBuilder(config.gestures.includeHotbar)
                .create(rightX, 132, btnWidth, btnHeight, Component.translatable("onekey.settings.gestures.includeHotbar"),
                        (button, value) -> config.gestures.includeHotbar = value));

        // 第六行：准星瞄准优先 / 智能分类防污染
        this.addRenderableWidget(CycleButton.onOffBuilder(config.quickStack.crosshairPriority)
                .create(leftX, 156, btnWidth, btnHeight, Component.translatable("onekey.settings.quickStack.crosshairPriority"),
                        (button, value) -> config.quickStack.crosshairPriority = value));

        this.addRenderableWidget(CycleButton.onOffBuilder(config.quickStack.smartMatching)
                .create(rightX, 156, btnWidth, btnHeight, Component.translatable("onekey.settings.quickStack.smartMatching"),
                        (button, value) -> config.quickStack.smartMatching = value));

        // 第七行：Shift+左键扫掠转移 / 右键重复放置
        this.addRenderableWidget(CycleButton.onOffBuilder(config.gestures.lmbDragSweep)
                .create(leftX, 180, btnWidth, btnHeight, Component.translatable("onekey.settings.gestures.lmbDragSweep"),
                        (button, value) -> config.gestures.lmbDragSweep = value));

        this.addRenderableWidget(CycleButton.onOffBuilder(config.gestures.rmbDragRepeat)
                .create(rightX, 180, btnWidth, btnHeight, Component.translatable("onekey.settings.gestures.rmbDragRepeat"),
                        (button, value) -> config.gestures.rmbDragRepeat = value));

        // 第八行：滚轮方向反转 / 光标持物滚轮微调
        this.addRenderableWidget(CycleButton.onOffBuilder(config.gestures.invertWheel)
                .create(leftX, 204, btnWidth, btnHeight, Component.translatable("onekey.settings.gestures.invertWheel"),
                        (button, value) -> config.gestures.invertWheel = value));

        this.addRenderableWidget(CycleButton.onOffBuilder(config.gestures.wheelCarriedHandling)
                .create(rightX, 204, btnWidth, btnHeight, Component.translatable("onekey.settings.gestures.wheelCarriedHandling"),
                        (button, value) -> config.gestures.wheelCarriedHandling = value));

        // 底部一行：原版按键绑定入口 / 完成
        this.addRenderableWidget(Button.builder(Component.translatable("onekey.settings.openKeybinds"), btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new KeyBindsScreen(this, this.minecraft.options));
            }
        }).bounds(leftX, this.height - 28, btnWidth, btnHeight).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> onClose())
                .bounds(rightX, this.height - 28, btnWidth, btnHeight).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);

        extractor.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);

        // 提示信息
        extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.locking"), this.width / 2, this.height - 56, 0xFFFFB300);
        extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.hotbar"), this.width / 2, this.height - 44, 0xFFAAAAAA);
    }

    @Override
    public void removed() {
        // 窗口关闭 / 直接切换界面不一定走 onClose，这里再落一次盘避免设置丢失
        OneKeyFabric.saveConfig();
        super.removed();
    }

    @Override
    public void onClose() {
        OneKeyFabric.saveConfig();
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
