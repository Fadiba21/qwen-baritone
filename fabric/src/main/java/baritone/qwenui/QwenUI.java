package baritone.qwenui;

import baritone.Baritone;
import baritone.qwenui.core.BaritoneBridge;
import baritone.qwenui.core.CommandHotkeys;
import baritone.qwenui.core.MacroRunner;
import baritone.qwenui.core.QwenConfig;
import baritone.qwenui.ui.QwenScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Fabric client entrypoint. No requests, commands or macros run automatically on startup. */
public final class QwenUI implements ClientModInitializer {
    private static QwenConfig config;
    private static final BaritoneBridge BRIDGE = new BaritoneBridge();
    private static final MacroRunner MACROS = new MacroRunner(BRIDGE);
    private static final CommandHotkeys.State COMMAND_HOTKEYS = new CommandHotkeys.State();
    private static KeyMapping open;
    private static KeyMapping stop;
    private boolean wasInWorld;
    public static QwenConfig config() {
        if (config == null) config = QwenConfig.load();
        return config;
    }
    public static BaritoneBridge bridge() { return BRIDGE; }
    public static MacroRunner macros() { return MACROS; }

    /** Human readable name of the current open key (as bound in Minecraft Controls). */
    public static String openKeyLabel() { return open == null ? "F11" : open.getTranslatedKeyMessage().getString(); }
    public static boolean isStopKey(net.minecraft.client.input.KeyEvent event) { return stop != null && stop.matches(event); }
    public static boolean isOpenKey(net.minecraft.client.input.KeyEvent event) { return open != null && open.matches(event); }
    /** True when a GLFW keycode would overlap the QwenUI opener or emergency-stop mapping. */
    public static boolean isReservedCommandKey(int key) {
        if (key < GLFW.GLFW_KEY_SPACE || key > GLFW.GLFW_KEY_LAST) return false;
        if (key == GLFW.GLFW_KEY_F11 || key == GLFW.GLFW_KEY_END) return true;
        InputConstants.Key candidate = InputConstants.Type.KEYSYM.getOrCreate(key);
        return (open != null && open.matches(candidate)) || (stop != null && stop.matches(candidate));
    }

    @Override public void onInitializeClient() {
        config();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            MACROS.cancel();
            Baritone.shutdown();
        });
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("qwenui", "controls"));
        open = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.qwenui.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F11, category));
        stop = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.qwenui.stop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_END, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!config().fullscreenKeyMoved) {
                config().fullscreenKeyMoved = true;
                boolean moved = baritone.qwenui.core.VanillaKeys.moveFullscreenOffF11(client);
                try { config().save(); } catch (java.io.IOException | RuntimeException ignored) { }
                if (moved && client.player != null) client.gui.hud.getChat().addClientSystemMessage(Component.literal(config().language.startsWith("id")
                        ? "QwenUI memakai F11 untuk membuka panel; tombol Fullscreen Minecraft dipindah ke F10 (ubah di Controls)."
                        : "QwenUI opens with F11; Minecraft's Fullscreen key was moved to F10 (change it in Controls)."));
            }
            boolean inWorld = client.level != null && client.player != null;
            if (!inWorld && wasInWorld) MACROS.cancel();
            wasInWorld = inWorld;
            while (open.consumeClick()) {
                if (inWorld && client.gui.screen() == null) client.gui.setScreen(new QwenScreen(null, false));
            }
            while (stop.consumeClick()) {
                MACROS.cancel();
                if (inWorld) BRIDGE.execute("stop");
            }
            boolean screenOpen = client.gui.screen() != null;
            long window = client.getWindow().handle();
            var eligibleHotkeys = config().commandHotkeys == null ? java.util.List.<CommandHotkeys.Binding>of()
                    : config().commandHotkeys.stream().filter(binding -> binding != null && !isReservedCommandKey(binding.key)).toList();
            for (String command : COMMAND_HOTKEYS.poll(eligibleHotkeys, inWorld, screenOpen,
                    key -> GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS)) {
                try {
                    if (!BRIDGE.execute(command)) {
                        client.gui.hud.getChat().addClientSystemMessage(Component.literal(config().language.startsWith("id")
                                ? "Perintah hotkey tidak dikenal oleh Baritone: " + command
                                : "Baritone does not recognize hotkey command: " + command));
                    }
                } catch (RuntimeException failure) {
                    client.gui.hud.getChat().addClientSystemMessage(Component.literal(config().language.startsWith("id")
                            ? "Perintah hotkey Baritone gagal: " + command
                            : "Baritone hotkey command failed: " + command));
                }
            }
            if (inWorld) {
                try { MACROS.tick(); }
                catch (RuntimeException failure) {
                    MACROS.cancel();
                    client.gui.hud.getChat().addClientSystemMessage(Component.literal(config().language.startsWith("id")
                        ? "Macro dihentikan karena gagal. Periksa perintah dan log Baritone."
                        : "Macro stopped after an error. Check its commands and Baritone log."));
                }
            }
        });
    }
}
