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
    default int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

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
    default int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

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
    default int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

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
    default int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

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
    default int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

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
    default int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    /**
     * Perform a "sequential next" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param iReturn  the register id to place the results of operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    default int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    /**
     * Perform a "sequential previous" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param iReturn  the register id to place the results of operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    default int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }
    }
