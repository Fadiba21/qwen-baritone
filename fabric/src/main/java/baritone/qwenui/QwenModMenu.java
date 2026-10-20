package baritone.qwenui;

import baritone.qwenui.ui.QwenScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Optional integration, only loaded when Mod Menu requests its entrypoint. */
public final class QwenModMenu implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new QwenScreen(parent, false);
    }
}
