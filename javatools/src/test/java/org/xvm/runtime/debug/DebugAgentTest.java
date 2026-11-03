package org.xvm.runtime.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The debugger agent's options. The agent itself, under the launcher's {@code --debug}, is tested with the language
 * server's side of the protocol, in the language server's tests.
 */
public class DebugAgentTest {
    @Test
    public void theOptionsNameALoopbackAddressToListenAtOrConnectTo() {
        var options = DebugAgent.Options.of("listen:localhost:5005,suspend,token=t");
        assertTrue(options.listen());
        assertTrue(options.address().isLoopbackAddress());
        assertEquals(5005, options.port());
        assertTrue(options.suspend());
        assertEquals("t", options.token());
        assertFalse(DebugAgent.Options.of("connect:127.0.0.1:6000").listen());

        assertTrue(refused("listen:192.0.2.1:5005").contains("is not a loopback address"));
        assertTrue(refused("connect:127.0.0.1:0").contains("not 0"));
        assertEquals(DebugAgent.Options.USAGE, refused("attach:127.0.0.1:5005"));
        assertEquals(DebugAgent.Options.USAGE, refused("listen:127.0.0.1"));
        assertTrue(refused("listen:127.0.0.1:5005,quiet").contains("Unknown --debug option 'quiet'"));
    }

    private static String refused(String options) {
        return assertThrows(IllegalArgumentException.class, () -> DebugAgent.Options.of(options)).getMessage();
    }
}
