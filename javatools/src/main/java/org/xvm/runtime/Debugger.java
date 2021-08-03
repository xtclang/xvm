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
     * @param iPC    the current iPC
     *
     * @return the iPC for the next op or any of the Op.R_* values
     */
    int enter(Frame frame, int iPC);

    /**
     * Check for a breakpoint.
     *
     * @param frame  the current frame
     * @param iPC    the current iPC
     *
     * @return the iPC for the next op or any of the Op.R_* values
     */
    int checkBreakPoint(Frame frame, int iPC);
    }
