package com.mcsport.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mcsport.McSport;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {

    public static final String CATEGORY = "key.categories." + McSport.MOD_ID;

    public static final KeyMapping OPEN_MATCH_MENU = new KeyMapping(
            "key." + McSport.MOD_ID + ".match_menu",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY);

    public static final KeyMapping SLIDE_TACKLE = new KeyMapping(
            "key." + McSport.MOD_ID + ".slide_tackle",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            CATEGORY);

    public static final KeyMapping REQUEST_PASS = new KeyMapping(
            "key." + McSport.MOD_ID + ".pass_request",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY);

    private KeyBindings() {
    }
}
