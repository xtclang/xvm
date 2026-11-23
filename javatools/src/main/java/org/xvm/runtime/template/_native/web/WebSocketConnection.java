package org.xvm.runtime.template._native.web;

import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;

import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;

import org.eclipse.jetty.websocket.core.exception.CloseException;

/**
 * Demand one complete message at a time so an application that stops receiving applies
 * backpressure instead of accumulating an unbounded queue in the runtime.
 */
public final class WebSocketConnection
        implements Session.Listener {
    public WebSocketConnection(int cbMax) {
        f_cbMax = cbMax;
    }

    public CompletableFuture<WebSocketConnection> getOpenedFuture() {
        return f_cfOpened;
    }

    @Override
    public void onWebSocketOpen(Session session) {
        m_session = session;
        session.setMaxTextMessageSize(f_cbMax);
        session.setMaxBinaryMessageSize(f_cbMax);
        f_cfOpened.complete(this);
        session.demand();
    }

    @Override
    public void onWebSocketText(String sMessage) {
        f_queueMessages.add(sMessage);
    }

    @Override
    public void onWebSocketBinary(ByteBuffer buffer, Callback callback) {
        byte[] ab = new byte[buffer.remaining()];
        buffer.get(ab);
        f_queueMessages.add(ab);
        callback.succeed();
    }

    @Override
    public void onWebSocketClose(int nStatus, String sReason, Callback callback) {
        m_nCloseCode   = nStatus;
        m_sCloseReason = sReason == null ? "" : sReason;
        f_fClosed.set(true);
        f_queueMessages.offer(CLOSED);
        callback.succeed();
    }

    @Override
    public void onWebSocketError(Throwable e) {
        m_exception    = e;
        m_nCloseCode   = e instanceof CloseException exception
                ? exception.getStatusCode() : StatusCode.SERVER_ERROR;
        m_sCloseReason = e.getMessage() == null ? "" : e.getMessage();
        f_cfOpened.completeExceptionally(e);
        f_fClosed.set(true);
        f_queueMessages.offer(CLOSED);
    }

    public boolean isOpen() {
        Session session = m_session;
        return session != null && session.isOpen() && !f_fClosed.get();
    }

    public int getCloseCode() {
        return m_nCloseCode;
    }

    public String getCloseReason() {
        return m_sCloseReason;
    }

    public Object receive() throws Exception {
        Object message = f_queueMessages.poll();
        if (message == null && !f_fClosed.get()) {
            message = f_queueMessages.take();
        }
        if (message == null || message == CLOSED) {
            if (m_exception != null) {
                throw new IOException("WebSocket receive failed", m_exception);
            }
            return null;
        }
        if (isOpen()) {
            m_session.demand();
        }
        return message;
    }

    public synchronized void send(Object message) throws Exception {
        if (!isOpen()) {
            throw new IOException("WebSocket is closed");
        }
        CompletableFuture<Void> cf       = new CompletableFuture<>();
        Callback                callback = Callback.from(() -> cf.complete(null),
                                                cf::completeExceptionally);
        if (message instanceof String sText) {
            m_session.sendText(sText, callback);
        } else {
            m_session.sendBinary(ByteBuffer.wrap((byte[]) message), callback);
        }
        cf.get();
    }

    public void close(int nCode, String sReason) throws Exception {
        if (!(StatusCode.isTransmittable(nCode) ||
                nCode >= APPLICATION_CODE_MIN && nCode <= APPLICATION_CODE_MAX) ||
                sReason.getBytes(StandardCharsets.UTF_8).length > 123) {
            throw new IllegalArgumentException("Invalid WebSocket close status or reason");
        }
        if (f_fClosed.compareAndSet(false, true)) {
            m_nCloseCode   = nCode;
            m_sCloseReason = sReason;
            f_queueMessages.offer(CLOSED);
            CompletableFuture<Void> cf = new CompletableFuture<>();
            m_session.close(nCode, sReason, Callback.from(() -> cf.complete(null),
                    cf::completeExceptionally));
            cf.get();
        }
    }

    private static final int APPLICATION_CODE_MIN = 3000;
    private static final int APPLICATION_CODE_MAX = 4999;

    private static final Object CLOSED = new Object();

    private final int                                    f_cbMax;
    private final CompletableFuture<WebSocketConnection> f_cfOpened = new CompletableFuture<>();
    private final ArrayBlockingQueue<Object>             f_queueMessages = new ArrayBlockingQueue<>(1);
    private final AtomicBoolean                          f_fClosed = new AtomicBoolean();

    private volatile Session   m_session;
    private volatile Throwable m_exception;
    private volatile int       m_nCloseCode = StatusCode.NORMAL;
    private volatile String    m_sCloseReason = "";
}
