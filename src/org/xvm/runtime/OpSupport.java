package org.xvm.runtime;


import org.xvm.asm.Op;


/**
 * {@link OpSupport} represents a run-time facet of a type.
 */
public interface OpSupport
    {
    /**
     * Obtain an underlying ClassTemplate for this {@link OpSupport}
     */
    ClassTemplate getTemplate();


    // ----- various built-in operations -----------------------------------------------------------

    /**
     * Perform an "add" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place the results of operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);

    /**
     * Perform a "subtract" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place the results of operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);

    /**
     * Perform a "multiply" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place the results of operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);

    /**
     * Perform a "divide" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place the results of operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);

    /**
     * Perform a "modulo" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place the results of operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);

    /**
     * Perform a "negate" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param iReturn  the register id to place the results of operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn);

    /**
     * Perform a "sequential next" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param fPost    if true, the the operation is performed after the current value is returned
     *                 (e.g. i++); otherwise - before that (e.g. ++i)
     * @param iReturn  the register id to place the results of operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    default int invokeNext(Frame frame, ObjectHandle hTarget, boolean fPost, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    /**
     * Perform a "sequential previous" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param fPost    if true, the the operation is performed after the current value is returned
     *                 (e.g. i--); otherwise - before that (e.g. --i)
     * @param iReturn  the register id to place the results of operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    default int invokePrev(Frame frame, ObjectHandle hTarget, boolean fPost, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }
    }
