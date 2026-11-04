package me.easan.onekey.neoforge.client.gui;

import me.easan.onekey.common.config.OneKeyConfig;
import me.easan.onekey.neoforge.OneKeyNeoForge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * OneKey 图形设置界面（NeoForge）：采用原版控件，零第三方依赖。
 */
public final class OneKeySettingsScreen extends Screen {

    private final Screen parent;

    public OneKeySettingsScreen(Screen parent) {
        super(Component.translatable("onekey.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        OneKeyConfig config = OneKeyNeoForge.config();

        int leftX = this.width / 2 - 155;
        int rightX = this.width / 2 + 5;
        int btnWidth = 150;
        int btnHeight = 20;

        // 第一行：整理总开关 / 按分类分组
        this.addRenderableWidget(CycleButton.onOffBuilder(config.sort.enabled)
                .create(leftX, 40, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.enabled"),
                        (button, value) -> config.sort.enabled = value));

        this.addRenderableWidget(CycleButton.onOffBuilder(config.sort.groupByCategory)
                .create(rightX, 40, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.groupByCategory"),
                        (button, value) -> config.sort.groupByCategory = value));

        // 第二行：按名称排序 / 按数量降序
        this.addRenderableWidget(CycleButton.onOffBuilder(config.sort.sortByName)
                .create(leftX, 64, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.sortByName"),
                        (button, value) -> config.sort.sortByName = value));

        this.addRenderableWidget(CycleButton.onOffBuilder(config.sort.sortByCountDesc)
                .create(rightX, 64, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.sortByCountDesc"),
                        (button, value) -> config.sort.sortByCountDesc = value));

        // 第三行：冷却时间 / 堆叠扫描半径
        this.addRenderableWidget(CycleButton.<Integer>builder(val -> Component.literal(val + " ms"), () -> config.sort.cooldownMs)
                .withValues(100, 150, 200, 250, 300, 500)
                .create(leftX, 88, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.cooldown"),
                        (button, value) -> config.sort.cooldownMs = value));

        this.addRenderableWidget(CycleButton.<Integer>builder(val -> Component.literal(val + " 格"), () -> config.quickStack.radius)
                .withValues(2, 4, 6, 8)
                .create(rightX, 88, btnWidth, btnHeight, Component.translatable("onekey.settings.quickStack.radius"),
                        (button, value) -> config.quickStack.radius = value));

        // 第四行：存入模式 / 兼容点击延迟
        this.addRenderableWidget(CycleButton.<Boolean>builder(
                        val -> Component.translatable(val ? "onekey.settings.stashMode.all" : "onekey.settings.stashMode.matching"),
                        () -> config.quickStack.stashMode)
                .withValues(false, true)
                .create(leftX, 112, btnWidth, btnHeight, Component.translatable("onekey.settings.quickStack.stashMode"),
                        (button, value) -> config.quickStack.stashMode = value));

        this.addRenderableWidget(CycleButton.onOffBuilder(config.compat.enabled)
                .create(rightX, 112, btnWidth, btnHeight, Component.translatable("onekey.settings.compat.enabled"),
                        (button, value) -> config.compat.enabled = value));


        // 第五行：准星瞄准优先 / 智能分类防污染
        this.addRenderableWidget(CycleButton.onOffBuilder(config.quickStack.crosshairPriority)
                .create(leftX, 136, btnWidth, btnHeight, Component.translatable("onekey.settings.quickStack.crosshairPriority"),
                        (button, value) -> config.quickStack.crosshairPriority = value));

        this.addRenderableWidget(CycleButton.onOffBuilder(config.quickStack.smartMatching)
                .create(rightX, 136, btnWidth, btnHeight, Component.translatable("onekey.settings.quickStack.smartMatching"),
                        (button, value) -> config.quickStack.smartMatching = value));

        // 第六行：原版按键绑定设置入口
        this.addRenderableWidget(Button.builder(Component.translatable("onekey.settings.openKeybinds"), btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new KeyBindsScreen(this, this.minecraft.options));
            }
        }).bounds(this.width / 2 - 100, 162, 200, btnHeight).build());

        // 底部：完成按钮
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, btnHeight).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);

        extractor.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);

        // 提示信息
        extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.locking"), this.width / 2, 186, 0xFFFFB300);
        extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.hotbar"), this.width / 2, 200, 0xFFAAAAAA);
    }

    @Override
    public void removed() {
        // 窗口关闭 / 直接切换界面不一定走 onClose，这里再落一次盘避免设置丢失
        OneKeyNeoForge.saveConfig();
        super.removed();
    }

    @Override
    public void onClose() {
        OneKeyNeoForge.saveConfig();
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
