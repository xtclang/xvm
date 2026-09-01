package org.xvm.runtime.template._native.web;


import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.RuntimeTestSupport;
import org.xvm.runtime.ServiceContext;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;

import org.xvm.runtime.template._native.web.xRTServer.HttpServerHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * Behavioral test for the server bind rollback path. Boots a real container, binds real HTTP and
 * HTTPS servers on ephemeral ports, and then fails the very last step of the bind - after both
 * servers are running and after the container keep-alive callback has been registered.
 */
public class HttpServerBindRollbackTest {
    /**
     * The bind sequence registers the container keep-alive callback before its final steps, so a
     * failure in "createContext(...)" used to terminate the service context while leaving the
     * callback count elevated, both started Java servers running, and the thread pool alive. The
     * container could then never go idle, so a "once and done" run could not terminate.
     * <p/>
     * The fault is injected by clearing the router, which makes the real bind implementation reach
     * "httpServer.createContext(&quot;/&quot;, null)" and raise NullPointerException at exactly the
     * post-registration point the rollback exists for. Everything up to that point - socket
     * binding, executor creation, starting both servers, registering the callback - is real.
     */
    @Test
    public void failedServerBindDoesNotPinTheContainerAlive() {
        assumeTrue(RuntimeTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        NativeContainer container = RuntimeTestSupport.newContainer();
        ServiceContext  context   = container.createServiceContext("HttpServer");
        Frame           frame     = RuntimeTestSupport.entryFrame(context);

        HttpServerHandle hServer = new HttpServerHandle(
                xRTServer.INSTANCE.getCanonicalClass(), context);

        // drop the router so that the last step of the bind fails, after the servers have started
        // and after the keep-alive registration
        hServer.setRouter(null);

        // a null binding is required to match the cleared router; ports are 0 so the OS picks
        // ephemeral ones and the test can never collide with anything else on the machine
        ObjectHandle[] ahArg = new ObjectHandle[]{
                null,
                xString.makeHandle("localhost"),
                xInt64.makeHandle(0),
                xInt64.makeHandle(0)};

        assertTrue(container.isIdle(), "a container with no server must start out idle");

        try {
            int nResult = xRTServer.INSTANCE.invokeNativeN(frame,
                    xRTServer.INSTANCE.getStructure().findMethod("bindImpl", 4),
                    hServer, ahArg, Op.A_IGNORE);

            assertEquals(Op.R_EXCEPTION, nResult, "a failed bind must be reported to natural code");
        } finally {
            // belt and braces: on the unfixed code the rollback does not happen, so the started
            // servers would otherwise keep non-daemon threads alive for the rest of the JVM
            stopQuietly(hServer.getHttpServer());
            stopQuietly(hServer.getHttpsServer());
        }

        assertTrue(container.isIdle(),
                "a failed server bind must not leave the container pinned alive");
        assertNull(hServer.getHttpServer(),
                "a failed server bind must leave the handle unconfigured, so a retry is possible");
    }

    private static void stopQuietly(HttpServer server) {
        if (server != null) {
            try {
                server.stop(0);
            } catch (RuntimeException _) {
                // nothing useful to do in test cleanup
            }
        }
    }
}
