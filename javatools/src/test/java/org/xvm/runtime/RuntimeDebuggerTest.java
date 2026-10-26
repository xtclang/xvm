package org.xvm.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the choice of a runtime's debugger.
 */
public class RuntimeDebuggerTest {
    @Test
    public void theConsoleDebuggerIsTheDebuggerUntilAHostSetsAnother() {
        Runtime runtime = new Runtime();
        try {
            assertTrue(runtime.isDebuggerAllowed());
            assertSame(DebugConsole.INSTANCE, runtime.getDebugger());

            runtime.setDebugger(Debugger.NONE);
            assertSame(Debugger.NONE, runtime.getDebugger());
        } finally {
            runtime.shutdownXVM();
        }
    }

    @Test
    public void theSystemPropertyTurnsTheDebuggerOffWhateverAHostSets() {
        Runtime runtime;
        String  previous = System.setProperty("org.xvm.runtime.debugger", "off");
        try {
            runtime = new Runtime();
        } finally {
            if (previous == null) {
                System.clearProperty("org.xvm.runtime.debugger");
            } else {
                System.setProperty("org.xvm.runtime.debugger", previous);
            }
        }

        try {
            assertFalse(runtime.isDebuggerAllowed());
            assertSame(Debugger.NONE, runtime.getDebugger());

            runtime.setDebugger(DebugConsole.INSTANCE);
            assertSame(Debugger.NONE, runtime.getDebugger());
        } finally {
            runtime.shutdownXVM();
        }
    }
}
