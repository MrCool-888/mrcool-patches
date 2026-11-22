package com.openbubbles.wear.patch;

import android.app.Application;
import com.localob.messaging.backend.domain.ContentRun;
import com.localob.messaging.backend.domain.Message;
import com.localob.messaging.backend.domain.MessageContent;
import com.localob.messaging.backend.domain.RunFormat;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.datetime.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 32, manifest = Config.NONE, application = Application.class,
        instrumentedPackages = {"com.localob.messaging", "kotlinx.datetime"})
public class BridgeEditTest {
    private final List<String> edits = new ArrayList<>();

    private static StateFlow<?> flow(Object value) {
        return (StateFlow<?>) Proxy.newProxyInstance(StateFlow.class.getClassLoader(), new Class[]{StateFlow.class},
                (self, method, args) -> { if (method.getName().equals("getValue")) return value;
                    throw new AssertionError(method.getName()); });
    }

    private Message message(boolean own, long timestamp, boolean retracted, String subject) {
        MessageContent content = new MessageContent(
                Collections.singletonList(new ContentRun.Text("Original", RunFormat.Companion.getPlain())),
                Collections.emptyList(), null, null, null, subject, false);
        return (Message) Proxy.newProxyInstance(Message.class.getClassLoader(), new Class[]{Message.class},
                (self, method, args) -> {
                    switch (method.getName()) {
                        case "getTimestamp": return Instant.Companion.fromEpochMilliseconds(timestamp);
                        case "canEdit": return own && ((Instant) args[0]).toEpochMilliseconds() - timestamp < 900_000L;
                        case "getDateDeletedFlow":
                        case "getDateScheduledFlow": return flow(null);
                        case "getRetractedPartsFlow": return flow(retracted ? Collections.singleton(0) : Collections.emptySet());
                        case "getContentFlow": return flow(content);
                        case "edit":
                            assertEquals(0, args[0]);
                            List<?> replacement = (List<?>) args[1];
                            assertEquals(1, replacement.size());
                            edits.add(((ContentRun.Text) replacement.get(0)).getText());
                            return kotlin.Unit.INSTANCE;
                        default: throw new AssertionError(method.getName());
                    }
                });
    }

    @Test public void editsOnlyTheMatchingOriginalText() throws Exception {
        Message target = message(true, System.currentTimeMillis() - 1_000, false, null);
        assertThrows(IllegalArgumentException.class, () -> BridgeOps.editMessage(target, "Stale", "New text"));
        assertThrows(IllegalArgumentException.class, () -> BridgeOps.editMessage(target, "Original", "   "));
        BridgeOps.editMessage(target, "Original", "Original");
        assertTrue(edits.isEmpty());
        BridgeOps.editMessage(target, "Original", "New text\nwith a second line");
        assertEquals(Collections.singletonList("New text\nwith a second line"), edits);
    }

    @Test public void rejectsIneligibleMessagesBeforeCallingNativeEdit() {
        long now = System.currentTimeMillis();
        assertNull(BridgeOps.editableText(message(false, now, false, null), now));
        assertNull(BridgeOps.editableText(message(true, now - 900_000L, false, null), now));
        assertNull(BridgeOps.editableText(message(true, now + 1, false, null), now));
        assertNull(BridgeOps.editableText(message(true, now, true, null), now));
        assertNull(BridgeOps.editableText(message(true, now, false, "Subject"), now));
        assertTrue(edits.isEmpty());
    }
}
