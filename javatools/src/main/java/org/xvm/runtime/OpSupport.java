package org.xvm.runtime;


import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;


/**
 * {@link OpSupport} represents a run-time facet of a type.
 */
public interface OpSupport
    {
    /**
     * Obtain an underlying ClassTemplate for this {@link OpSupport} and the specified type.
     *
     * @param type  the type
     *
     * @return the corresponding ClassTemplate
     */
    ClassTemplate getTemplate(TypeConstant type);


    // ----- built-in binary operations ------------------------------------------------------------

    /**
     * Perform an "add" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);

    /**
     * Perform a "subtract" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);

    /**
     * Perform a "multiply" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);

    /**
     * Perform a "divide" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);

    /**
     * Perform a "modulo" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);

    /**
     * Perform an "shl" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokeShl(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);

    /**
     * Perform an "shr" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokeShr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);

    /**
     * Perform an "ushr" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokeShrAll(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);

    /**
     * Perform an "and" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);

    /**
     * Perform an "or" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);

    /**
     * Perform an "xor" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokeXor(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);

    /**
     * Perform a "divrem" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param aiReturn the two register ids to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokeDivRem(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int[] aiReturn);

    /**
     * Perform a "range" / "interval" operation (inclusive start and end).
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokeIRangeI(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);

    /**
     * Perform a "range" / "interval" operation (exclusive start).
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokeERangeI(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);

    /**
     * Perform a "range" / "interval" operation (exclusive end).
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokeIRangeE(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);

    /**
     * Perform a "range" / "interval" operation (exclusive start and end).
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param hArg     the argument handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokeERangeE(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);


    // ----- built-in unary operations -------------------------------------------------------------

    /**
     * Perform a "negate" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn);

    /**
     * Perform a "complement" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokeCompl(Frame frame, ObjectHandle hTarget, int iReturn);

    /**
     * Perform a "sequential next" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn);

    /**
     * Perform a "sequential previous" operation.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn);
    }
