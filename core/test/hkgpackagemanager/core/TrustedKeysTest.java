package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;

public class TrustedKeysTest {
    private static final String FP = Hex.sha256(new byte[] {1});
    private static final String FP2 = Hex.sha256(new byte[] {2});

    @Test public void roundTrip() {
        TrustedKeys k = TrustedKeys.NONE.with(FP, "Sample catalog").with(FP2, "Other");
        TrustedKeys back = TrustedKeys.decode(k.encode());
        assertEquals(k.labels(), back.labels());
        assertEquals("Sample catalog", back.labels().get(FP));
        assertEquals(Arrays.asList(FP, FP2), new ArrayList<String>(back.labels().keySet()));
        assertTrue(back.trusts(FP));
    }

    @Test public void badLinesSkipped() {
        TrustedKeys k = TrustedKeys.decode("zz notHex\n\n" + FP + "\n");
        assertEquals(1, k.labels().size());
        assertTrue(k.trusts(FP));
        assertEquals("", k.labels().get(FP));
    }

    @Test public void labelLosesNewlinesAndIsCappedAt80() {
        StringBuilder longLabel = new StringBuilder("a\nb\r");
        for (int i = 0; i < 200; i++) longLabel.append('x');
        TrustedKeys k = TrustedKeys.NONE.with(FP, longLabel.toString());
        String label = k.labels().get(FP);
        assertFalse(label.contains("\n"));
        assertFalse(label.contains("\r"));
        assertEquals(80, label.length());
        assertEquals(1, TrustedKeys.decode(k.encode()).labels().size());
    }

    @Test public void fingerprintsAreLowercased() {
        TrustedKeys k = TrustedKeys.decode(FP.toUpperCase() + " X\n");
        assertTrue(k.trusts(FP));
        assertTrue(k.trusts(FP.toUpperCase()));
        assertTrue(TrustedKeys.NONE.with(FP.toUpperCase(), "").trusts(FP));
    }

    @Test public void withoutRemoves() {
        TrustedKeys k = TrustedKeys.NONE.with(FP, "a").with(FP2, "b").without(FP);
        assertFalse(k.trusts(FP));
        assertTrue(k.trusts(FP2));
        assertFalse(TrustedKeys.NONE.trusts(FP));
    }
}
