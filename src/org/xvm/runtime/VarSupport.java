package org.xvm.runtime;


import org.xvm.asm.Op;

import org.xvm.runtime.template.xRef.RefHandle;


/**
 * {@link VarSupport} represents a run-time facet of a Var.
 */
public interface VarSupport
    {
    /**
     * Obtain an underlying ClassTemplate for this {@link VarSupport}
     */
    ClassTemplate getTemplate();


    // ----- built-in unary operations -------------------------------------------------------------

    /**
     * Get the Var's value.
     *
     * @param frame    the current frame
     * @param hTarget  the target Var or Ref handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int get(Frame frame, RefHandle hTarget, int iReturn);

    /**
     * Increment the Var value and retrieve the new value.
     *
     * @param frame    the current frame
     * @param hTarget  the target Var handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int invokeVarPreInc(Frame frame, RefHandle hTarget, int iReturn);

    /**
     * Retrieve the Var value and then increment it.
     *
     * @param frame    the current frame
     * @param hTarget  the target Var handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int invokeVarPostInc(Frame frame, RefHandle hTarget, int iReturn);

    /**
     * Decrement the Var value and retrieve the new value.
     *
     * @param frame    the current frame
     * @param hTarget  the target Var handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int invokeVarPreDec(Frame frame, RefHandle hTarget, int iReturn);

    /**
     * Retrieve the Var value and then decrement it.
     *
     * @param frame    the current frame
     * @param hTarget  the target Var handle
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int invokeVarPostDec(Frame frame, RefHandle hTarget, int iReturn);


    // ----- built-in binary operations ------------------------------------------------------------

    /**
     * Set the Var's value.
     *
     * @param frame    the current frame
     * @param hTarget  the target Var handle
     * @param hValue   the new value
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int set(Frame frame, RefHandle hTarget, ObjectHandle hValue);

    /**
     * Perform an "add" operation on the Var.
     *
     * @param frame    the current frame
     * @param hTarget  the target Var handle
     * @param hArg     the argument handle
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int invokeVarAdd(Frame frame, RefHandle hTarget, ObjectHandle hArg);

    /**
     * Perform a "subtract" operation on the Var.
     *
     * @param frame    the current frame
     * @param hTarget  the target Var handle
     * @param hArg     the argument handle
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int invokeVarSub(Frame frame, RefHandle hTarget, ObjectHandle hArg);

    /**
     * Perform a "multiply" operation on the Var.
     *
     * @param frame    the current frame
     * @param hTarget  the target Var handle
     * @param hArg     the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int invokeVarMul(Frame frame, RefHandle hTarget, ObjectHandle hArg);

    /**
     * Perform a "divide" operation on the Var.
     *
     * @param frame    the current frame
     * @param hTarget  the target Var handle
     * @param hArg     the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int invokeVarDiv(Frame frame, RefHandle hTarget, ObjectHandle hArg);

    /**
     * Perform a "modulo" operation on the Var.
     *
     * @param frame    the current frame
     * @param hTarget  the target Var handle
     * @param hArg     the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int invokeVarMod(Frame frame, RefHandle hTarget, ObjectHandle hArg);
    }
