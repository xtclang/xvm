package org.xvm.runtime;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where the debugger ends a program's stack ({@link SessionDebugger#beforeHost}): at the first entry
 * of a fiber that runs in the host's container, as {@link Frame#getStackFrameArray} lists the
 * fibers, whatever the entry knows of its frame. The runner calls a program from its own fiber with
 * {@code Container.invoke}; when that fiber is not waiting for the call at the instant the program
 * stops, the stack's last entry has no frame, only the calling method, which is ecstasy's, not the
 * runner's, and the debugger showed it as the program's.
 */
public class ProgramStackTest {
    @Test
    public void theHostsCallerEntryWithoutAFrameEndsTheStackAsItsFramesDo() {
        Frame.StackFrame twice = entry(false);
        Frame.StackFrame run   = entry(true);
        // the runner's fiber moved on from its call: the entry knows only Container.invoke
        Frame.StackFrame invoke = entry(false);
        assertEquals(List.of(twice, run),
                SessionDebugger.beforeHost(new Frame.StackFrame[] {twice, run, invoke}, List.of(false, true)));

        // the runner's fiber waits for the call: its frames follow, and end the stack the same way
        Frame.StackFrame start    = entry(false);
        Frame.StackFrame register = entry(false);
        assertEquals(List.of(twice, run),
                SessionDebugger.beforeHost(new Frame.StackFrame[] {twice, run, start, register}, List.of(false, true)));
    }

    @Test
    public void theProgramsOwnCallersStayUpToTheHost() {
        // a service of the program, called from kick() by the program's fiber, which has returned
        Frame.StackFrame work = entry(true);
        Frame.StackFrame kick = entry(false);
        assertEquals(List.of(work, kick),
                SessionDebugger.beforeHost(new Frame.StackFrame[] {work, kick}, List.of(false, false)));

        // a service of the program, called by the program's fiber, which waits in run() and was
        // called by the runner's, which has moved on from its call
        Frame.StackFrame run    = entry(true);
        Frame.StackFrame invoke = entry(false);
        Frame.StackFrame[] stack = {work, run, invoke};
        assertEquals(List.of(work, run), SessionDebugger.beforeHost(stack, List.of(false, false, true)));

        // without a host, the whole stack is the program's
        assertEquals(List.of(work, run, invoke), SessionDebugger.beforeHost(stack, List.of(false, false, false)));
    }

    /**
     * @return an entry of a stack that knows neither its frame nor its method, which the decision
     *         does not read, and is the last of its fiber's if it is a boundary
     */
    private static Frame.StackFrame entry(boolean boundary) {
        var entry = new Frame.StackFrame(null, null, -1);
        entry.boundary = boundary;
        return entry;
    }
}
