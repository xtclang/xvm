package org.xvm.runtime.template._native.net;


import java.io.File;
import java.io.IOException;

import java.net.Socket;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Objects;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener;

import org.xvm.test.XdkOutputs;
import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.Runtime;

import org.xvm.runtime.template._native.net.xRTSocket.SocketHandle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards the shared socket state of {@code SocketHandle} views (must-audit graduation from the
 * clone study). The handle is created with a masked composition - {@code getCanonicalType()} is
 * {@code net.Socket}, not the native class - so {@code revealOrigin()} manufactures a fresh view
 * clone on native entries. On the old shape the native {@code Socket} lived in a per-view
 * {@code public volatile} field: the write after connect landed on one view while the registered
 * service handle kept {@code null} forever, and any additional access view silently dropped the
 * close-path write. The socket now lives in a holder shared by every view (the
 * {@code xRTServer.HttpServerHandle} idiom).
 */
public class SocketHandleStateSharingTest {
    /**
     * A socket installed through one revealed view must be visible through every other view,
     * and clearing it through one view must clear it for all. Red on the per-view field shape,
     * where each revealed clone carried its own socket reference.
     */
    @Test
    public void socketStateIsSharedAcrossViews() {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, XdkOutputs.systemRepository(), ErrorListener.RUNTIME);
            var template  = NativeTemplates.get(container).socket();
            var context   = container.createServiceContext("socket-view-test");
            var hMasked   = new SocketHandle(template.getCanonicalClass(), context);

            var hViewA = (SocketHandle) hMasked.revealOrigin();
            var hViewB = (SocketHandle) hMasked.revealOrigin();
            assertNotSame(hMasked, hViewA,
                    "the canonical composition is masked; revealOrigin must produce a view");
            assertNotSame(hViewA, hViewB, "each reveal produces a fresh view");

            var socket = new Socket();
            hViewA.setSocket(socket);

            assertSame(socket, hViewB.getSocket(),
                    "a socket installed through one view must be visible through every view;"
                            + " a per-view field left the sibling views with null");
            assertSame(socket, hMasked.getSocket(),
                    "the registered (masked) service handle must see the socket too;"
                            + " on the old shape it kept null forever");

            hViewB.setSocket(null);
            assertNull(hViewA.getSocket(),
                    "the close-path write must be visible through every view");
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * {@code finishConnect} must expose the masked net.Socket handle that construction produced,
     * not the revealed native inception view that native code uses internally to install the Java
     * socket.
     */
    @Test
    public void finishConnectReturnsMaskedApplicationHandle() throws IOException {
        var source = Files.readString(XdkOutputs.root().resolve(
                "javatools/src/main/java/org/xvm/runtime/template/_native/net/xRTSocket.java"));
        int ofMethod = source.indexOf("private static int finishConnect");
        assertTrue(ofMethod >= 0, "finishConnect must exist");

        int ofEnd = source.indexOf("\n    }\n\n\n    // ----- I/O", ofMethod);
        assertTrue(ofEnd > ofMethod, "finishConnect method boundary must be identifiable");

        var method = source.substring(ofMethod, ofEnd);
        assertTrue(method.contains("hSocket.setSocket(socket);"),
                "finishConnect still installs the Java socket through the revealed native view");
        assertTrue(method.contains("return frame.assignValues(aiReturn, xBoolean.trueHandle(frame), h);"),
                "finishConnect must return the original masked handle to application code");
        assertFalse(method.contains("return frame.assignValues(aiReturn, xBoolean.trueHandle(frame), hSocket);"),
                "returning the revealed native view defeats maskAs(net.Socket)");
    }

    // ----- helpers (same discovery as ArrayViewGuardTest) ---------------------------------------





}
