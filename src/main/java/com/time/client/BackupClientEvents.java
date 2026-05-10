package com.time.client;

import com.time.machine;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public final class BackupClientEvents {
    private static final String CATEGORY = "key.categories." + machine.MODID;
    private static final KeyMapping OPEN_BACKUP_MENU = new KeyMapping(
            "key." + machine.MODID + ".open_backup_menu",
            GLFW.GLFW_KEY_B,
            CATEGORY
    );

    private BackupClientEvents() {
    }

    @Mod.EventBusSubscriber(modid = machine.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBusEvents {
        private ModBusEvents() {
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_BACKUP_MENU);
        }
    }

    @Mod.EventBusSubscriber(modid = machine.MODID, value = Dist.CLIENT)
    public static final class ForgeBusEvents {
        private ForgeBusEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                return;
            }

            while (OPEN_BACKUP_MENU.consumeClick()) {
                Screen currentScreen = minecraft.screen;
                if (!(currentScreen instanceof BackupMenuScreen)) {
                    minecraft.setScreen(new BackupMenuScreen(currentScreen));
                }
            }
        }
    }
}
