package com.openbubbles.wear.patch;

import android.app.Application;
import com.localob.messaging.backend.domain.ContentRun;
import com.localob.messaging.backend.domain.Message;
import com.localob.messaging.backend.domain.MessageContent;
import com.localob.messaging.backend.domain.RunFormat;
import java.lang.reflect.Proxy;
import java.util.*;
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
public class BridgeUnsendTest {
    @SuppressWarnings("unchecked")
    private static <T> StateFlow<T> flow(T value) {
        return (StateFlow<T>) Proxy.newProxyInstance(StateFlow.class.getClassLoader(), new Class[]{StateFlow.class},
                (self, method, args) -> { if (method.getName().equals("getValue")) return value;
                    throw new AssertionError(method.getName()); });
    }

    private static Message message(boolean own, long timestamp, Set<Integer> retracted, List<Integer> calls) {
        return message(own, timestamp, retracted, calls,
                Collections.singletonList(new ContentRun.Text("Test", RunFormat.Companion.getPlain())));
    }

    private static Message message(boolean own, long timestamp, Set<Integer> retracted, List<Integer> calls, List<ContentRun> runs) {
        MessageContent content = new MessageContent(runs,
                Collections.emptyList(), null, null, null, null, false);
        return (Message) Proxy.newProxyInstance(Message.class.getClassLoader(), new Class[]{Message.class}, (self, method, args) -> {
            switch (method.getName()) {
                case "isFromMe": return own;
                case "getTimestamp": return Instant.Companion.fromEpochMilliseconds(timestamp);
                case "getDateDeletedFlow": return flow(null);
                case "getRetractedPartsFlow": return flow(retracted);
                case "getContentFlow": return flow(content);
                case "canUnsend": return own && ((Instant) args[0]).toEpochMilliseconds() - timestamp < 120_000L;
                case "unsend": calls.add((Integer) args[0]); retracted.add((Integer) args[0]); return kotlin.Unit.INSTANCE;
                default: throw new AssertionError(method.getName());
            }
        });
    }

    @Test public void rejectsOtherPeoplesExpiredFutureAndAlreadyUnsentMessages() throws Exception {
        long now = System.currentTimeMillis();
        List<Integer> calls = new ArrayList<>();
        for (Message target : Arrays.asList(message(false, now, new HashSet<>(), calls),
                message(true, now - 120_000L, new HashSet<>(), calls),
                message(true, now + 60_000L, new HashSet<>(), calls),
                message(true, now, new HashSet<>(Collections.singleton(0)), calls))) {
            assertFalse(BridgeOps.canUnsend(target, now));
            try { BridgeOps.unsendMessage(target); fail("Must reject ineligible target"); }
            catch (IllegalArgumentException expected) { }
        }
        assertTrue(calls.isEmpty());
    }

    @Test public void invokesNativeUnsendAndRecognizesTombstone() throws Exception {
        List<Integer> calls = new ArrayList<>();
        Message target = message(true, System.currentTimeMillis() - 1000, new HashSet<>(), calls);
        assertTrue(BridgeOps.canUnsend(target, System.currentTimeMillis()));
        BridgeOps.unsendMessage(target);
        assertEquals(Collections.singletonList(0), calls);
        assertTrue(BridgeOps.fullyUnsent(target));
        assertFalse(BridgeOps.canUnsend(target, System.currentTimeMillis()));
    }

    @Test public void unsendsEveryRemainingPartWithoutRepeatingRetractedParts() throws Exception {
        com.localob.messaging.backend.domain.Attachment attachment =
                (com.localob.messaging.backend.domain.Attachment) Proxy.newProxyInstance(
                        Message.class.getClassLoader(), new Class[]{com.localob.messaging.backend.domain.Attachment.class},
                        (self, method, args) -> { throw new AssertionError(method.getName()); });
        List<ContentRun> parts = Arrays.asList(new ContentRun.Text("First", RunFormat.Companion.getPlain()),
                new ContentRun.AttachmentRef(attachment), new ContentRun.Text("Last", RunFormat.Companion.getPlain()));
        List<Integer> calls = new ArrayList<>();
        Message target = message(true, System.currentTimeMillis() - 1000,
                new HashSet<>(Collections.singleton(1)), calls, parts);
        BridgeOps.unsendMessage(target);
        assertEquals(Arrays.asList(0, 2), calls);
        assertTrue(BridgeOps.fullyUnsent(target));
    }
}
