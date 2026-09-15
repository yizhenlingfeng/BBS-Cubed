package gbeic.bbsplusplus.events;
import gbeic.bbsplusplus.BBSFSloveCMLClient;
import gbeic.bbsplusplus.ui.film.replays.overlays.UIQuickReplayOverlayPanel;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;


public class ClientEventHandler {
    private static KeyBinding keyOpenQuickReplays;

    public static void register() {
        // Register key bindings
        keyOpenQuickReplays = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.bbspp.open_quick_replays",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            "category.bbspp.keys"
        ));

        // Register client tick events
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                return;
            }

            // Handle quick replay key
            while (keyOpenQuickReplays.wasPressed()) {
                if (!UIQuickReplayOverlayPanel.isOpened()) {
                    keyOpenQuickReplays();
                }
            }
        });

        BBSFSloveCMLClient.LOGGER.info("Client event handler registered!");
    }

    private static void keyOpenQuickReplays() {
        // This will be called when the quick replay key is pressed
        // We need to get the current film and its replays
        BBSFSloveCMLClient.LOGGER.info("Quick replay key pressed!");
    }

    public static KeyBinding getKeyOpenQuickReplays() {
        return keyOpenQuickReplays;
    }
}
