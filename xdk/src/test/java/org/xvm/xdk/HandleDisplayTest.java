package org.xvm.xdk;

import java.io.PrintWriter;
import java.io.StringWriter;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;

import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.Runtime;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.xvm.util.Handy.quotedString;

/**
 * Exercises the actual Java display entry points with handles from the installed XDK.
 */
class HandleDisplayTest {
    private static final String TEXT = "quote\" and \\ backslash\nnewline";
    private static Runtime runtime;

    @BeforeAll
    static void initializeRuntime() {
        var xdk = Path.of("build/install/xdk");
        var repository = new LinkedRepository(
                new DirRepository(xdk.resolve("lib").toFile(), true),
                new DirRepository(xdk.resolve("javatools").toFile(), true));
        runtime = new Runtime();
        new NativeContainer(runtime, repository);
    }

    @AfterAll
    static void shutdownRuntime() {
        if (runtime != null) {
            runtime.shutdownXVM();
        }
    }

    @Test
    void debuggerStringDisplayLeavesTheValueCacheUnchanged() throws ReflectiveOperationException {
        var handle = xString.makeHandle(TEXT);
        var expected = "(" + handle.getComposition() + ") " + quotedString(TEXT);
        assertNull(cachedString(handle));

        // A Java debugger's toString renderer invokes this entry point.
        assertEquals(expected, handle.toString());
        assertNull(cachedString(handle));

        var cached = handle.getStringValue();
        assertEquals(TEXT, cached);
        assertEquals(expected, handle.toString());
        assertSame(cached, cachedString(handle));
    }

    @Test
    void exceptionAndJavaStackTraceDisplayLeaveTheTextCacheUnchanged() throws ReflectiveOperationException {
        var handle = xException.makeHandle(null, TEXT);
        var text = (StringHandle) handle.getField(null, "text");
        var expected = "(" + handle.getComposition() + ") " + quotedString(TEXT);
        assertNotNull(handle.getComposition().getFieldInfo("text"));
        assertNull(cachedString(text));

        assertEquals(expected, handle.toString());
        assertNull(cachedString(text));
        var wrapper = handle.getException();
        assertEquals(expected, wrapper.toString());
        assertNull(cachedString(text));

        var output = new StringWriter();
        wrapper.printStackTrace(new PrintWriter(output));
        assertTrue(output.toString().startsWith(expected + System.lineSeparator()));
        assertNull(cachedString(text));

        var cached = text.getStringValue();
        assertEquals(TEXT, cached);
        assertEquals(expected, wrapper.toString());
        assertSame(cached, cachedString(text));
    }

    private static String cachedString(StringHandle handle) throws ReflectiveOperationException {
        // Inspect the existing cache without calling the accessor that populates it.
        var field = StringHandle.class.getDeclaredField("m_sValue");
        field.setAccessible(true);
        return (String) field.get(handle);
    }
}
