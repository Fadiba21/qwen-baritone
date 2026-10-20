package baritone.qwenui.ai;
import org.junit.Test;
import static org.junit.Assert.*;
public class RequestGenerationTest {
    @Test public void acceptsCurrentRequest(){var gate=new RequestGeneration();long token=gate.begin();assertTrue(gate.accepts(token));}
    @Test public void deletionRejectsLateReply(){var gate=new RequestGeneration();long token=gate.begin();gate.invalidate();assertFalse(gate.accepts(token));}
    @Test public void newRequestRejectsOldCallback(){var gate=new RequestGeneration();long old=gate.begin(),current=gate.begin();assertFalse(gate.accepts(old));assertTrue(gate.accepts(current));}
    @Test public void closeAndReopenNeverAcceptCancelledCallback(){var gate=new RequestGeneration();long before=gate.begin();gate.invalidate();gate.invalidate();long after=gate.begin();assertFalse(gate.accepts(before));assertTrue(gate.accepts(after));}
}
