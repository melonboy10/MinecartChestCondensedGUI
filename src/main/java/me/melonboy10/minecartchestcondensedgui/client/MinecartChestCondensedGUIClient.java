package me.melonboy10.minecartchestcondensedgui.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class MinecartChestCondensedGUIClient implements ClientModInitializer {

    private static KeyBinding keyBinding;
    private static final String displayName = "12345";

    @Override
    public void onInitializeClient() {
        // Register new keybind for Y
        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.minecartchestcondensedgui.openmenu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            "minecartcondensedgui"
        ));

        // Check for key bind press and tick the minecart searcher
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keyBinding.wasPressed()) {
                client.player.sendMessage(new LiteralText("Key Y was pressed!"), false);

                if (!SearchTask.running) {
                    // If the key was presses search the area for minecarts and add them to the list

                    SearchTask.minecartEntities =
                        (ArrayList<ChestMinecartEntity>) client.player.getWorld().getNonSpectatingEntities(ChestMinecartEntity.class, client.player.getBoundingBox().expand(3))
                            .stream().filter(chestMinecartEntity -> chestMinecartEntity.getDisplayName().getString().equals(displayName)).collect(Collectors.toList());
                    
                    System.out.println("Found carts");
                    SearchTask.start();
                }
            }
        });
    }
}
