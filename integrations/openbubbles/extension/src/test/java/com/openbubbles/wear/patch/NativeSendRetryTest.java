package com.openbubbles.wear.patch;

import com.localob.messaging.backend.rust.RustBridgeException;
import java.util.concurrent.TimeoutException;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeSendRetryTest {
    @Test public void matchesOnlyTheNativeSendTimeout() {
        assertTrue(NativeSendRetry.isNativeTimeout(new RustBridgeException.Other("Send timeout; try again")));
        assertFalse(NativeSendRetry.isNativeTimeout(new RustBridgeException.Other("Network is offline")));
        assertFalse(NativeSendRetry.isNativeTimeout(new TimeoutException("Send timeout; try again")));
    }
}
