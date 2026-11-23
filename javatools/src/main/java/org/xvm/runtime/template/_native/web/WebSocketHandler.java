package org.xvm.runtime.template._native.web;

import java.io.IOException;

import java.util.concurrent.CompletableFuture;

import org.xvm.asm.ConstantPool;

import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xArray.Mutability;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.numbers.xUInt16;

import org.xvm.runtime.template.text.xString;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;

/**
 * Dispatches native WebSocket events to the connection implementation's service context.
 */
public class WebSocketHandler {
    WebSocketHandler(WebSocketConnection connection, ServiceContext context,
                     FunctionHandle onConnect, FunctionHandle onMessage,
                     FunctionHandle onError, FunctionHandle onDisconnect) {
        f_connection   = connection;
        f_context      = context;
        f_onConnect    = onConnect;
        f_onMessage    = onMessage;
        f_onError      = onError;
        f_onDisconnect = onDisconnect;
    }

    public CompletableFuture<?> onConnect() {
        CompletableFuture<ObjectHandle> cf = f_context.postRequest(null, f_onConnect, Utils.OBJECTS_NONE, 0);
        return cf == null
                ? CompletableFuture.failedFuture(new IOException("WebSocket endpoint terminated"))
                : cf;
    }

    public void onMessage(Object message) {
        try (var _ = ConstantPool.withPool(f_context.f_pool)) {
            ObjectHandle hMsg = message instanceof String sText
                    ? xString.makeHandle(sText)
                    : xByteArray.makeByteArrayHandle((byte[]) message, Mutability.Constant);

            CompletableFuture<ObjectHandle> cf =
                    f_context.postRequest(null, f_onMessage, new ObjectHandle[] {hMsg}, 0);
            if (cf == null) {
                f_connection.fail(1011, "WebSocket endpoint terminated");
            } else {
                f_connection.demand();
            }
        }
    }

    public void onClose(int nStatus, String sReason, boolean fError) {
        try (var _ = ConstantPool.withPool(f_context.f_pool)) {
            ObjectHandle hStatus = xUInt16.INSTANCE.makeJavaLong(nStatus);
            ObjectHandle hReason = xString.makeHandle(sReason);

            CompletableFuture<?> cf = fError
                    ? f_context.postRequest(null, f_onError, new ObjectHandle[] {hStatus}, 0)
                    : CompletableFuture.completedFuture(null);
            if (cf != null) {
                cf.whenComplete((result, failure) ->
                        f_context.callLater(f_onDisconnect, new ObjectHandle[] {hStatus, hReason}));
            }
        }
    }

    private final WebSocketConnection f_connection;
    private final ServiceContext      f_context;
    private final FunctionHandle      f_onConnect;
    private final FunctionHandle      f_onMessage;
    private final FunctionHandle      f_onError;
    private final FunctionHandle      f_onDisconnect;
}
