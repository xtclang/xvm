package org.xvm.runtime;

import org.xvm.asm.Op;

/**
 * Debugger interface.
 */
public interface Debugger {
    /**
     * The debugger of a host that has none, such as an embedding host without a terminal for the
     * {@link DebugConsole}: {@code assert:debug} continues with the next op, and since this
     * debugger never becomes active, no breakpoint is checked.
     */
    Debugger NONE = new Debugger() {
        @Override
        public int activate(Frame ctx, int iPC) {
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
     * @param ctx the current frame
     * @param iPC the current PC
     *
     * @return the iPC for the next op or any of the Op.R_* values
     */
    int activate(Frame ctx, int iPC);

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
}
