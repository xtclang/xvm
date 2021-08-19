package org.xvm.runtime;

/**
 * Debugger interface.
 */
public interface Debugger
    {
    /**
     * Activate the debugger.
     *
     * @param ctx  the current service context
     */
    void activate(ServiceContext ctx);

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
     * @return Op.R_EXCEPTION if the exception needs to be processed naturally or the iPC for the
     *         next op, Op.R_NEXT or Op.R_CALL values if the exception was caused by the debugger
     *         itself and has been handled
     */
    int checkBreakPoint(Frame frame, ObjectHandle.ExceptionHandle hEx);
    }
