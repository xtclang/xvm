package org.xvm.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for the choice of a runtime's debugger.
 */
public class RuntimeDebuggerTest {
    @Test
    public void theConsoleDebuggerIsTheDebuggerUntilAHostSetsAnother() {
        Runtime runtime = new Runtime();
        try {
            assertSame(DebugConsole.INSTANCE, runtime.getDebugger());

            runtime.setDebugger(Debugger.NONE);
            assertSame(Debugger.NONE, runtime.getDebugger());
        } finally {
            runtime.shutdownXVM();
        }
    }

    @Test
    public void theSystemPropertyTurnsTheDebuggerOffUntilAHostSetsOne() {
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
            assertSame(Debugger.NONE, runtime.getDebugger());

            runtime.setDebugger(DebugConsole.INSTANCE);
            assertSame(DebugConsole.INSTANCE, runtime.getDebugger());
        } finally {
            runtime.shutdownXVM();
        }
    }
}
