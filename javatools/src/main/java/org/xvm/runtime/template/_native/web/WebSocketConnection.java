package org.xvm.runtime.template._native.web;

import java.io.IOException;

import java.nio.ByteBuffer;

import java.util.concurrent.CompletableFuture;

import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;

import org.eclipse.jetty.websocket.api.exceptions.CloseException;

import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xObject;

/**
 * Push Jetty events to the application's service; its request queue owns pending messages.
 */
public class WebSocketConnection
        implements Session.Listener {
    public WebSocketConnection(int cbMax) {
        f_cbMax = cbMax;
    }

    public CompletableFuture<WebSocketHandle> getOpenedFuture() {
        return f_cfOpened;
    }

    public void connect(WebSocketHandler handler) {
        assert m_handler == null;
        m_handler = handler;

        handler.onConnect().whenComplete((result, error) -> {
            m_fReady = true;
            if (error != null) {
                fail(StatusCode.SERVER_ERROR, "WebSocket onConnect failed");
            } else if (f_fClosed.get()) {
                notifyClosed();
            } else {
                demand();
            }
        });
    }

    @Override
    public void onWebSocketOpen(Session session) {
        m_session = session;
        session.setMaxTextMessageSize(f_cbMax);
        session.setMaxBinaryMessageSize(f_cbMax);
        f_cfOpened.complete(new WebSocketHandle(this));
    }

    @Override
    public void onWebSocketText(String sMessage) {
        m_handler.onMessage(sMessage);
    }

    @Override
    public void onWebSocketBinary(ByteBuffer buffer, Callback callback) {
        byte[] ab = new byte[buffer.remaining()];
        buffer.get(ab);
        m_handler.onMessage(ab);
        callback.succeed();
    }

    @Override
    public void onWebSocketClose(int nStatus, String sReason, Callback callback) {
        if (!m_fError) {
            m_nCloseCode   = nStatus;
            m_sCloseReason = sReason == null ? "" : sReason;
        }
        f_fClosed.set(true);
        notifyClosed();
        callback.succeed();
    }

    @Override
    public void onWebSocketError(Throwable e) {
        m_nCloseCode   = e instanceof CloseException exception
                ? exception.getStatusCode() : StatusCode.SERVER_ERROR;
        m_sCloseReason = e.getMessage() == null ? "" : e.getMessage();
        m_fError       = true;
        f_cfOpened.completeExceptionally(e);
        f_fClosed.set(true);
        notifyClosed();
    }

    public boolean isOpen() {
        Session session = m_session;
        return session != null && session.isOpen() && !f_fClosed.get();
    }

    public void demand() {
        if (isOpen()) {
            m_session.demand();
        }
    }

    public void fail(int nCode, String sReason) {
        onWebSocketError(new CloseException(nCode, sReason));
        m_session.close(nCode, sReason, Callback.NOOP);
    }

    private void notifyClosed() {
        if (m_fReady && f_fNotified.compareAndSet(false, true)) {
            m_handler.onClose(m_nCloseCode, m_sCloseReason,
                    m_fError || m_nCloseCode != StatusCode.NORMAL && m_nCloseCode != StatusCode.SHUTDOWN);
        }
    }

    public CompletableFuture<Void> send(Object message)
                throws IOException {
        if (!isOpen()) {
            throw new IOException("WebSocket is closed");
        }

        return Callback.Completable.with(callback -> {
            if (message instanceof String sText) {
                m_session.sendText(sText, callback);
            } else {
                m_session.sendBinary(ByteBuffer.wrap((byte[]) message), callback);
            }
        });
    }

    public CompletableFuture<Void> close(int nCode, String sReason) {
        // WebSocket.disconnect() supplies the application's callback and clears its delegate
        f_fNotified.set(true);

        if (f_fClosed.compareAndSet(false, true)) {
            m_nCloseCode   = nCode;
            m_sCloseReason = sReason;
            return Callback.Completable.with(callback -> m_session.close(nCode, sReason, callback));
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Native handle holding the WebSocketConnection.
     */
    static public class WebSocketHandle
            extends ObjectHandle {
        public WebSocketHandle(WebSocketConnection connection) {
            super(xObject.INSTANCE.getCanonicalClass());

            f_connection = connection;
        }

        final WebSocketConnection f_connection;
    }

    private final int                                f_cbMax;
    private final CompletableFuture<WebSocketHandle> f_cfOpened  = new CompletableFuture<>();
    private final AtomicBoolean                      f_fClosed   = new AtomicBoolean();
    private final AtomicBoolean                      f_fNotified = new AtomicBoolean();

    private WebSocketHandler m_handler;
    private Session          m_session;

    // close details are published by f_fClosed or m_fError
    private int    m_nCloseCode = StatusCode.NORMAL;
    private String m_sCloseReason = "";

    private volatile boolean m_fReady;
    private volatile boolean m_fError;
}
