package baritone.qwenui.core;

import org.junit.Test;
import java.util.Locale;
import static org.junit.Assert.*;

public class SettingsKeyTest {
    @Test public void camelCaseUsesNativeLowercaseKey() { assertEquals("allowbreak",BaritoneBridge.settingKey("allowBreak")); }
    @Test public void namesAlreadyLowercaseRemainUnchanged() { assertEquals("allowbreak",BaritoneBridge.settingKey("allowbreak")); }
    @Test public void normalizationIsLocaleIndependent() {
        Locale original=Locale.getDefault();
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR")); assertEquals("allowinventory",BaritoneBridge.settingKey("allowInventory")); }
        finally { Locale.setDefault(original); }
    }
    @Test(expected=NullPointerException.class) public void nullNameIsRejected() { BaritoneBridge.settingKey(null); }
}
