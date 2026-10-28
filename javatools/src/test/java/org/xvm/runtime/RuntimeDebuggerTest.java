package org.xvm.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

public class RuntimeDebuggerTest {
    @Test
    public void hostDebuggerOverridesOffProperty() {
        String previous = System.setProperty("org.xvm.runtime.debugger", "off");
        Runtime runtime;
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

            Debugger hostDebugger = new Debugger() {
                @Override
                public int activate(Frame frame, int iPC) {
                    throw new AssertionError("not called by this test");
                }

                @Override
                public int checkBreakPoint(Frame frame, int iPC) {
                    throw new AssertionError("not called by this test");
                }

                @Override
                public void onReturn(Frame frame) {
                    throw new AssertionError("not called by this test");
                }

                @Override
                public int checkBreakPoint(Frame frame, ObjectHandle.ExceptionHandle hEx) {
                    throw new AssertionError("not called by this test");
                }
            };
            runtime.setDebugger(hostDebugger);
            assertSame(hostDebugger, runtime.getDebugger());
        } finally {
            runtime.shutdownXVM();
        }
    }
}
