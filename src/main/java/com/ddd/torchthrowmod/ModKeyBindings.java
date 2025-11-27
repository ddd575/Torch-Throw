package com.ddd.torchthrowmod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {
    public static final String CATEGORY = "key.categories." + TorchThrowMod.MODID;

    public static final KeyMapping THROW_TORCH_KEY = new KeyMapping(
            "key." + TorchThrowMod.MODID + ".throw_torch",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V, // 默认V键
            CATEGORY
    );
}