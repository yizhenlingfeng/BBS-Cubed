package gbeic.bbsplusplus.client.ui.morph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MorphPreviewModelSelectionTest
{
    @Test
    void progressEndpointShowsTargetModel()
    {
        assertEquals("target", UICubeMappingEditorPanel.previewModelId(
            false, "source", "target", 1F, true, true));
    }

    @Test
    void interiorProgressKeepsSourceForMorphRendering()
    {
        assertEquals("source", UICubeMappingEditorPanel.previewModelId(
            false, "source", "target", 0.5F, true, true));
    }

    @Test
    void missingSourceFallsBackToTarget()
    {
        assertEquals("target", UICubeMappingEditorPanel.previewModelId(
            false, "source", "target", 0.5F, false, true));
    }

    @Test
    void missingTargetKeepsSourceAtEndpoint()
    {
        assertEquals("source", UICubeMappingEditorPanel.previewModelId(
            false, "source", "target", 1F, true, false));
    }

    @Test
    void endpointModelSwitchKeepsCameraButChangingTheModelPairRefitsIt()
    {
        assertFalse(UICubeMappingEditorPanel.shouldRefitCamera("source", "target", "source", "target"));
        assertTrue(UICubeMappingEditorPanel.shouldRefitCamera("source", "target", "new-source", "new-target"));
    }
}
