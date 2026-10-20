package baritone.qwenui.core;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;

/**
 * Vanilla Minecraft binds Fullscreen to F11 and handles it before any other key mapping, which would stop a
 * QwenUI default of F11 from ever firing. This one-time migration moves Fullscreen to F10 (reflection, best effort).
 */
public final class VanillaKeys {
    private VanillaKeys() { }

    /** @return true when the vanilla Fullscreen key was moved from F11 to F10. */
    public static boolean moveFullscreenOffF11(Minecraft client) {
        try {
            if (client == null || client.options == null) return false;
            for (KeyMapping mapping : client.options.keyMappings) {
                if (!"key.fullscreen".equals(mapping.getName())) continue;
                if (!"key.keyboard.f11".equals(call(mapping, "saveString"))) return false;
                Object f10 = com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F10);
                Method set = null;
                for (Method m : KeyMapping.class.getMethods()) {
                    if (m.getName().equals("setKey") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(f10)) set = m;
                }
                if (set == null) return false;
                set.invoke(mapping, f10);
                KeyMapping.resetMapping();
                Object options = client.options;
                for (Method m : options.getClass().getMethods()) {
                    if (m.getName().equals("save") && m.getParameterCount() == 0) { m.invoke(options); break; }
                }
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Best effort only: the user can still rebind keys in Controls.
        }
        return false;
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        return target.getClass().getMethod(name).invoke(target);
    }
}
