package com.openbubbles.wear.patch;

import android.util.Log;
import com.localob.messaging.backend.db.Database;
import com.localob.messaging.backend.db.Message;
import com.localob.messaging.backend.db.Message_;
import com.localob.messaging.backend.rust.MessageInst;
import com.localob.messaging.backend.rust.MessageType;
import com.localob.messaging.backend.rust.NormalMessage;
import com.localob.messaging.backend.rust.PushChatMessage;
import com.localob.messaging.backend.rust.RustBridgeException;
import com.localob.messaging.backend.rust.Rust_bridge_androidKt;
import com.localob.messaging.backend.rust.SharedPushStateHandle;
import com.openbubbles.wear.bridge.NativeSendTimeoutKt;
import io.objectbox.query.Query;
import io.objectbox.query.QueryBuilder;
import kotlin.coroutines.Continuation;

public final class NativeSendRetry {
    private NativeSendRetry() {}

    public static Object sendMessage(SharedPushStateHandle session, MessageInst message,
                                     Continuation<? super Boolean> continuation) throws RustBridgeException {
        if (!(message.getMessage() instanceof PushChatMessage.Message)) {
            return Rust_bridge_androidKt.sendMessage(session, message, continuation);
        }
        NormalMessage content = ((PushChatMessage.Message) message.getMessage()).getV1();
        if (!(content.getService() instanceof MessageType.IMessage) || content.getScheduled() != null) {
            return Rust_bridge_androidKt.sendMessage(session, message, continuation);
        }
        // Reuse the native payload and GUID used by OpenBubbles' own retry.
        return NativeSendTimeoutKt.retryNativeSendTimeout(
                next -> Rust_bridge_androidKt.sendMessage(session, message, next),
                NativeSendRetry::isNativeTimeout, () -> stillPending(message.getId()), continuation);
    }

    static boolean isNativeTimeout(Exception error) {
        return error instanceof RustBridgeException.Other
                && "Send timeout; try again".equals(((RustBridgeException.Other) error).getV1());
    }

    private static boolean stillPending(String guid) {
        try (Query<Message> query = Database.INSTANCE.getMessages().query()
                .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE).build()) {
            Message message = query.findUnique();
            return message != null && message.getDateDeleted() == null
                    && message.getDateDelivered() == null && message.getDateRead() == null
                    && message.getSendingServiceId() != null && message.getErrorMessage() == null;
        } catch (RuntimeException error) {
            Log.w("OpenBubblesWear", "Could not verify pending message for timeout retry");
            return false;
        }
    }
}
