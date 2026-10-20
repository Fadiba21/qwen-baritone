package baritone.qwenui.core;

import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class LitematicaBridgeTest {
    @Test
    public void featureRecordsExposeStableIdsAndDoNotAdvertiseUnsafeBuildActions() {
        List<LitematicaBridge.Feature> features = LitematicaBridge.features();
        Set<String> ids = features.stream().map(LitematicaBridge.Feature::id).collect(Collectors.toSet());

        assertTrue(ids.containsAll(Set.of(
                "gui.config", "gui.schematic.load", "gui.schematic.manager", "gui.placements",
                "gui.area.selection", "gui.material.list", "gui.verifier", "gui.placement.config")));
        assertFalse(ids.contains("placement.select"));
        assertFalse(ids.stream().anyMatch(id -> id.contains("build") || id.contains("paste")));
        assertTrue(features.stream().allMatch(feature -> feature.id() != null && feature.label() != null
                && feature.description() != null));
        assertThrows(UnsupportedOperationException.class, () -> features.add(
                new LitematicaBridge.Feature("bad", "bad", "bad", false, true)));
    }

    @Test
    public void unavailableOptionalModHasNoPlacementsAndDoesNotAcceptUnknownActions() {
        // The unit-test runtime intentionally does not bundle Litematica or MaLiLib.
        assertTrue(LitematicaBridge.placements().isEmpty());
        assertFalse(LitematicaBridge.isAvailable("unsupported.arbitrary.action"));
        assertFalse(LitematicaBridge.isAvailable("config.toggle.call_anything"));
        assertThrows(IllegalStateException.class, () -> LitematicaBridge.execute("gui.config"));
    }

    @Test
    public void catalogKeepsTheLegacyFiveArgumentConstructor() {
        BaritoneBridge.Catalog catalog = new BaritoneBridge.Catalog(
                List.of(), List.of(), false, false, "not installed");

        assertEquals("not installed", catalog.litematicaDescription());
        assertNotNull(catalog.litematicaFeatures());
        assertNotNull(catalog.litematicaPlacements());
        assertTrue(catalog.litematicaPlacements().isEmpty());
        assertTrue(catalog.litematicaFeatures().stream().allMatch(feature -> !feature.available()));
        assertThrows(UnsupportedOperationException.class, () -> catalog.litematicaFeatures().clear());
        assertThrows(UnsupportedOperationException.class, () -> catalog.litematicaPlacements().clear());
    }

    @Test
    public void placementSelectionIdsRequireAnExplicitOneBasedIndex() {
        assertFalse(LitematicaBridge.isAvailable("placement.select.0"));
        assertFalse(LitematicaBridge.isAvailable("placement.select.-1"));
        assertFalse(LitematicaBridge.isAvailable("placement.select.anything"));
        assertThrows(IllegalStateException.class, () -> LitematicaBridge.execute("placement.select.1"));
        assertThrows(IllegalArgumentException.class, () -> LitematicaBridge.execute("placement.select"));
    }
}
