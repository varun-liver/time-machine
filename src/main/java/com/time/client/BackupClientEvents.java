package com.time.client;

import com.time.machine;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

public final class BackupClientEvents implements ClientModInitializer {
    private static final KeyMapping OPEN_BACKUP_MENU = new KeyMapping(
            "key." + machine.MODID + ".open_backup_menu",
            GLFW.GLFW_KEY_B,
            "key.categories." + machine.MODID
    );

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(OPEN_BACKUP_MENU);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_BACKUP_MENU.consumeClick()) {
                Screen currentScreen = client.screen;
                if (!(currentScreen instanceof BackupMenuScreen)) {
                    client.setScreen(new BackupMenuScreen(currentScreen));
                }
            }
        });
    }
}
