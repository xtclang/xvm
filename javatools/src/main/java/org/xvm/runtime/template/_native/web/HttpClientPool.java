package org.xvm.runtime.template._native.web;

import java.net.CookieHandler;

import java.net.http.HttpClient;

import java.time.Duration;

import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;

/**
 * One connector's clients and cookie context. Closing rejects new clients before stopping every
 * existing client; the owning container awaits close on a cleanup worker.
 */
final class HttpClientPool implements AutoCloseable {
    HttpClientPool(CookieHandler cookies, SSLContext sslContext) {
        this.cookies = cookies;
        this.sslContext = sslContext;
    }

    synchronized HttpClient selectClient(long timeoutMillis) {
        if (closed) {
            throw new IllegalStateException("HTTP connector is closed");
        }
        int slot = timeoutMillis == 0 ? 0 : Math.min(clients.length - 1,
                1 + Long.numberOfTrailingZeros(Long.highestOneBit(Math.max(1, timeoutMillis / 1000))));
        HttpClient client = clients[slot];
        if (client == null) {
            var builder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                    .cookieHandler(cookies).sslContext(sslContext);
            if (timeoutMillis > 0) {
                builder.connectTimeout(Duration.ofSeconds(Math.max(1,
                        Long.highestOneBit(timeoutMillis / 1000))));
            }
            clients[slot] = client = builder.build();
        }
        return client;
    }

    @Override
    public void close() {
        List<HttpClient> snapshot = new ArrayList<>();
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            for (int i = 0; i < clients.length; i++) {
                if (clients[i] != null) {
                    snapshot.add(clients[i]);
                    clients[i] = null;
                }
            }
        }
        snapshot.forEach(HttpClient::shutdownNow);
        snapshot.forEach(HttpClient::close);
    }

    private final CookieHandler cookies;
    private final SSLContext sslContext;
    private final HttpClient[] clients = new HttpClient[5];
    private boolean closed;
}
