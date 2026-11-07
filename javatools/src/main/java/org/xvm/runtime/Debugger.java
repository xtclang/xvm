package org.xvm.runtime;

import org.xvm.asm.Op;

/**
 * Debugger interface.
 */
public interface Debugger {
    /**
     * The debugger stub that performs no actions.
     */
    Debugger NONE = new Debugger() {
        @Override
        public int activate(Frame frame, int iPC) {
            return iPC + 1;
        }

        @Override
        public int checkBreakPoint(Frame frame, int iPC) {
            return iPC + 1;
        }

        @Override
        public void onReturn(Frame frame) {
        }

        @Override
        public int checkBreakPoint(Frame frame, ObjectHandle.ExceptionHandle hEx) {
            return Op.R_EXCEPTION;
        }
    };

    /**
     * Activate the debugger.
     *
     * @param frame  the current frame
     * @param iPC    the current PC
     *
     * @return the iPC for the next op or any of the Op.R_* values
     */
    int activate(Frame frame, int iPC);

    /**
     * Check for a breakpoint at the specified frame and program counter.
     *
     * @param frame  the current frame
     * @param iPC    the current PC
     *
     * @return the iPC for the next op or any of the Op.R_* values
     */
    int checkBreakPoint(Frame frame, int iPC);

    /**
     * Inform the debugger that a frame is being exited.
     *
     * @param frame  the frame that exits
     */
    void onReturn(Frame frame);

    /**
     * Check for a breakpoint for the specified exception.
     *
     * @param frame  the current frame
     * @param hEx    the exception handler
     *
     * @return Op.R_CALL if the debugger made a natural call, Op.R_EXCEPTION if the exception needs
     *         to be processed naturally ano longer has to be stopped at, or Op.R_NEXT if this
     *         exception needs to be continued to be traced (until caught)
     */
    int checkBreakPoint(Frame frame, ObjectHandle.ExceptionHandle hEx);

    /**
     * Inform the debugger that it has become the debugger of the specified runtime, which turns
     * the runtime's "debugger is active" flag off first.
     *
     * @param runtime  the runtime whose services this debugger now debugs
     */
    default void attach(Runtime runtime) {
    }

    /**
     * Inform the debugger that another debugger has replaced it. A debugger that holds fibers
     * releases them.
     */
    default void detach() {
    }

    /**
     * Inform the debugger of the container that holds its host's own code, such as the runner
     * that runs the program. That code is not the program being debugged: a debugger never stops
     * it, holds it or ends it.
     *
     * @param container  the host's container
     */
    default void setHost(Container container) {
    }

    /**
     * End the program's fibers that this debugger holds, or that reach a line from now on,
     * without running more of the program, as when the program is being stopped. Called before
     * the debugger is replaced.
     */
    default void terminate() {
    }

    /**
     * @return true if, while this debugger is active, a fiber must not yield to other fibers, as
     *         while it steps; the console debugger never lets a fiber yield while it is active
     */
    default boolean holdsFibers() {
        return true;
    }
}
