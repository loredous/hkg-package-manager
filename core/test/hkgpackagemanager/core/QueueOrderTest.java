package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class QueueOrderTest {
    private static QueueItem withId(String id) {
        return new QueueItem(id + ".zip", 1, "x", id, "1", false, ItemState.WAITING, "");
    }

    private static List<String> ids(List<QueueItem> l) {
        List<String> r = new ArrayList<String>();
        for (QueueItem i : l) r.add(i.id);
        return r;
    }

    @Test public void selfUpdateLast() {
        List<QueueItem> o = QueueOrder.order(Arrays.asList(withId("hkg.package.manager"), withId("a"), withId("b")));
        assertEquals(Arrays.asList("a", "b", "hkg.package.manager"), ids(o));
    }

    @Test public void keepsUserOrderOtherwise() {
        List<QueueItem> o = QueueOrder.order(Arrays.asList(withId("c"), withId("a"), withId("b")));
        assertEquals(Arrays.asList("c", "a", "b"), ids(o));
    }
}
