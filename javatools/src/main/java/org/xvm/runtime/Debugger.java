package org.xvm.runtime;

/**
 * Debugger interface.
 */
public interface Debugger
    {
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