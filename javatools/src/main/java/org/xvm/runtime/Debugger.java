package org.xvm.runtime;

/**
 * Debugger interface.
 */
public interface Debugger
    {
    /**
     * Activate the debugger.
     *
     * @param frame  the current frame
     * @param iPC    the current PC
     *
     * @return the iPC for the next op or any of the Op.R_* values
     */
    int enter(Frame frame, int iPC);

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
     * Check for a breakpoint for the specified exception.
     *
     * @param frame  the current frame
     * @param hEx    the exception handler
     */
    void checkBreakPoint(Frame frame, ObjectHandle.ExceptionHandle hEx);
    }
