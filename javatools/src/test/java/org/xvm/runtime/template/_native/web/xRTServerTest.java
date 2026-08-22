package org.xvm.runtime.template._native.web;


import java.lang.reflect.Field;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import org.xvm.runtime.template._native.web.xRTServer.RouteInfo;
import org.xvm.runtime.template._native.web.xRTServer.Router;
import org.xvm.runtime.template._native.web.xRTServer.SimpleKeyManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;


public class xRTServerTest {
    /**
     * TLS route key-store handles must not be stored in ambient thread state. Request handling can
     * reuse Java workers, so a thread-local key store can leak between routes.
     */
    @Test
    public void keyManagerDoesNotStoreRouteKeyStoreInThreadLocal() {
        assertDoesNotThrow(() -> SimpleKeyManager.class.getDeclaredField("f_hServer"));

        assertFalse(Arrays.stream(SimpleKeyManager.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(ThreadLocal.class::isAssignableFrom),
                "key manager must resolve TLS state from routes, not ThreadLocal");
    }

    /**
     * TLS alias lookup must resolve against route data rather than ambient thread state. This keeps
     * server behavior deterministic for nested or reused request threads.
     */
    @Test
    public void tlsAliasesResolveRoutesWithoutAmbientThreadState() {
        Router router = new Router();

        RouteInfo direct = RouteInfo.create(null, 80, 443, null, "server", "localhost");
        RouteInfo named  = RouteInfo.create(null, 80, 443, null, "server", "example.com");
        RouteInfo other  = RouteInfo.create(null, 80, 443, null, "server", "other.example.com");

        router.setDirectRoute(direct);
        router.mapRoutes.put("example.com", named);
        router.mapRoutes.put("other.example.com", other);

        assertNotEquals(named.tlsAlias(), other.tlsAlias());
        assertSame(direct, SimpleKeyManager.findRouteForTlsAlias(router, direct.tlsAlias()));
        assertSame(named, SimpleKeyManager.findRouteForTlsAlias(router, named.tlsAlias()));
        assertSame(other, SimpleKeyManager.findRouteForTlsAlias(router, other.tlsAlias()));
        assertNull(SimpleKeyManager.findRouteForTlsAlias(router, "missing"));
    }
}
