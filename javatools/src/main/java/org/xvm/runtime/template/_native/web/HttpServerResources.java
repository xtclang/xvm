package org.xvm.runtime.template._native.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import java.io.IOException;

import java.net.InetSocketAddress;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native state for one server binding, including partially initialized listeners and unfinished
 * exchanges. Startup and close are serialized; close never needs an Ecstasy service to run.
 */
final class HttpServerResources implements AutoCloseable {
    synchronized void bindHttp(InetSocketAddress address) throws IOException {
        checkOpen();
        http = HttpServer.create(address, 0);
    }

    synchronized void bindHttps(InetSocketAddress address) throws IOException {
        checkOpen();
        https = HttpsServer.create(address, 0);
    }

    synchronized void configureHttps(HttpsConfigurator configurator) {
        checkOpen();
        https.setHttpsConfigurator(configurator);
    }

    synchronized void start(HttpHandler handler, Runnable releaseKeepAlive) {
        checkOpen();
        executor = Executors.newCachedThreadPool(Thread.ofPlatform().daemon()
                .name("HttpHandler-", 0).factory());
        http.setExecutor(executor);
        http.createContext("/", handler);
        http.start();
        httpStarted = true;
        https.setExecutor(executor);
        https.createContext("/", handler);
        https.start();
        httpsStarted = true;
        this.releaseKeepAlive = releaseKeepAlive;
    }

    synchronized boolean track(HttpExchange exchange) {
        return !closed && exchanges.add(exchange);
    }

    void release(HttpExchange exchange) {
        synchronized (this) {
            exchanges.remove(exchange);
        }
        exchange.close();
    }

    synchronized HttpServer http() {
        return http;
    }

    synchronized HttpsServer https() {
        return https;
    }

    synchronized int exchangeCount() {
        return exchanges.size();
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("HTTP server is closed");
        }
    }

    @Override
    public void close() {
        HttpServer http;
        HttpsServer https;
        ExecutorService executor;
        Runnable release;
        List<HttpExchange> pending;
        boolean httpStarted;
        boolean httpsStarted;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            http = this.http;
            https = this.https;
            executor = this.executor;
            httpStarted = this.httpStarted;
            httpsStarted = this.httpsStarted;
            release = releaseKeepAlive;
            pending = List.copyOf(exchanges);
            exchanges.clear();
            this.http = null;
            this.https = null;
            this.executor = null;
            releaseKeepAlive = null;
        }
        try {
            try {
                stop(http, httpStarted);
            } finally {
                stop(https, httpsStarted);
            }
        } finally {
            try {
                pending.forEach(HttpExchange::close);
            } finally {
                try {
                    if (executor != null) {
                        executor.shutdownNow();
                        executor.close();
                    }
                } finally {
                    if (release != null) {
                        release.run();
                    }
                }
            }
        }
    }

    private static void stop(HttpServer server, boolean started) {
        if (server != null) {
            // An unstarted JDK server can retain its bound socket after stop(). Start it first.
            if (!started) {
                server.start();
            }
            server.stop(0);
        }
    }

    private HttpServer http;
    private HttpsServer https;
    private ExecutorService executor;
    private Runnable releaseKeepAlive;
    private final Set<HttpExchange> exchanges = new HashSet<>();
    private boolean httpStarted;
    private boolean httpsStarted;
    private boolean closed;
}
