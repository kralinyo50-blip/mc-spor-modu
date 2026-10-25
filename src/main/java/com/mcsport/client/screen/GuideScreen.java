package com.mcsport.client.screen;

import com.mcsport.McSport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Illustrated tutorial book opened by the guide-book item. Pages mix a
 * 256x256 voxel illustration with localised title and wrapped body text;
 * all copy lives in the lang files so the same screen is fully Turkish and
 * English without any hard-coded strings.
 */
public class GuideScreen extends Screen {

    private static final int IMAGE_SIZE = 104;

    private record Page(String titleKey, String bodyKey, ResourceLocation image) {
    }

    private final Page[] pages = new Page[]{
            new Page("guide.mcsport.p1.title", "guide.mcsport.p1.body",
                    new ResourceLocation(McSport.MOD_ID, "textures/gui/guide/guide_stadium.png")),
            new Page("guide.mcsport.p2.title", "guide.mcsport.p2.body",
                    new ResourceLocation(McSport.MOD_ID, "textures/gui/guide/guide_stadium.png")),
            new Page("guide.mcsport.p3.title", "guide.mcsport.p3.body",
                    new ResourceLocation(McSport.MOD_ID, "textures/gui/guide/guide_items.png")),
            new Page("guide.mcsport.p4.title", "guide.mcsport.p4.body",
                    new ResourceLocation(McSport.MOD_ID, "textures/gui/guide/guide_items.png")),
            new Page("guide.mcsport.p5.title", "guide.mcsport.p5.body",
                    new ResourceLocation(McSport.MOD_ID, "textures/gui/guide/guide_referee.png")),
            new Page("guide.mcsport.p6.title", "guide.mcsport.p6.body",
                    new ResourceLocation(McSport.MOD_ID, "textures/gui/guide/guide_referee.png")),
            new Page("guide.mcsport.p7.title", "guide.mcsport.p7.body",
                    new ResourceLocation(McSport.MOD_ID, "textures/gui/guide/guide_stadium.png")),
            new Page("guide.mcsport.p8.title", "guide.mcsport.p8.body",
                    new ResourceLocation(McSport.MOD_ID, "textures/gui/guide/guide_items.png")),
            new Page("guide.mcsport.p9.title", "guide.mcsport.p9.body",
                    new ResourceLocation(McSport.MOD_ID, "textures/gui/guide/guide_stadium.png")),
            new Page("guide.mcsport.p10.title", "guide.mcsport.p10.body",
                    new ResourceLocation(McSport.MOD_ID, "textures/gui/guide/guide_referee.png")),
    };

    private int pageIndex;
    private Button previousButton;
    private Button nextButton;

    public GuideScreen() {
        super(Component.translatable("guide.mcsport.title"));
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int bottomY = height - 34;
        previousButton = addRenderableWidget(Button.builder(
                        Component.translatable("guide.mcsport.previous"), b -> {
                            if (pageIndex > 0) {
                                pageIndex--;
                            }
                            updateButtons();
                        })
                .bounds(centerX - 155, bottomY, 96, 20).build());
        nextButton = addRenderableWidget(Button.builder(
                        Component.translatable("guide.mcsport.next"), b -> {
                            if (pageIndex < pages.length - 1) {
                                pageIndex++;
                            }
                            updateButtons();
                        })
                .bounds(centerX + 59, bottomY, 96, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("guide.mcsport.close"), b -> onClose())
                .bounds(centerX - 48, bottomY, 96, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        previousButton.active = pageIndex > 0;
        nextButton.active = pageIndex < pages.length - 1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        Page page = pages[pageIndex];
        int centerX = width / 2;

        graphics.drawCenteredString(font, title, centerX, 16, 0xFFFFFF55);
        graphics.drawCenteredString(font, Component.translatable(page.titleKey()),
                centerX, 34, 0xFFFFFFFF);
        graphics.drawString(font,
                (pageIndex + 1) + " / " + pages.length, centerX + 140, 18, 0xFFB0B0B0);

        int imageX = centerX - IMAGE_SIZE / 2;
        int imageY = 46;
        graphics.fill(imageX - 3, imageY - 3, imageX + IMAGE_SIZE + 3,
                imageY + IMAGE_SIZE + 3, 0xCC202020);
        graphics.blit(page.image(), imageX, imageY, 0.0F, 0.0F,
                IMAGE_SIZE, IMAGE_SIZE, 256, 256);

        int textY = imageY + IMAGE_SIZE + 12;
        // Lang copy is pre-wrapped with \n so no client-side line splitting
        // (and no FormattedCharSequence dependency) is needed.
        String body = String.valueOf(Component.translatable(page.bodyKey()).getString());
        for (String line : body.split("\n")) {
            graphics.drawCenteredString(font, Component.literal(line),
                    centerX, textY, 0xFFE8E8E8);
            textY += 11;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
