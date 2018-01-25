package org.xvm.runtime;


import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.template.xRef.RefHandle;


/**
 * {@link OpSupport} represents a run-time facet of a type.
 */
public interface OpSupport
    {
    /**
     * Obtain an underlying ClassTemplate for this {@link OpSupport}
     */
    ClassTemplate getTemplate();


    // ----- invocations ---------------------------------------------------------------------------

    /**
     * Invoke a method with zero or one return value on the specified target.
     *
     * @param frame    the current frame
     * @param chain    the CallChain representing the target method
     * @param hTarget  the target handle
     * @param ahVar    the invocation parameters
     * @param iReturn  the register id to place the result of invocation into
     *
     * @return one of the {@link Op#R_CALL}, {@link Op#R_EXCEPTION}, or {@link Op#R_BLOCK} values
     */
    default int invoke1(Frame frame, CallChain chain, ObjectHandle hTarget,
                        ObjectHandle[] ahVar, int iReturn)
        {
        return frame.invoke1(chain, 0, hTarget, ahVar, iReturn);
        }

    /**
     * Invoke a method with a return value of Tuple.
     *
     * @param frame    the current frame
     * @param chain    the CallChain representing the target method
     * @param hTarget  the target handle
     * @param ahVar    the invocation parameters
     * @param iReturn  the register id to place the result of invocation into
     *
     * @return one of the {@link Op#R_CALL}, {@link Op#R_EXCEPTION}, or {@link Op#R_BLOCK} values
     */
    default int invokeT(Frame frame, CallChain chain, ObjectHandle hTarget,
                        ObjectHandle[] ahVar, int iReturn)
        {
        return frame.invokeT(chain, 0, hTarget, ahVar, iReturn);
        }

    /**
     * Invoke a method with more than one return value.
     *
     * @param frame     the current frame
     * @param chain     the CallChain representing the target method
     * @param hTarget   the target handle
     * @param ahVar     the invocation parameters
     * @param aiReturn  the array of register ids to place the results of invocation into
     *
     * @return one of the {@link Op#R_CALL}, {@link Op#R_EXCEPTION}, or {@link Op#R_BLOCK} values
     */
    default int invokeN(Frame frame, CallChain chain, ObjectHandle hTarget,
                        ObjectHandle[] ahVar, int[] aiReturn)
        {
        return frame.invokeN(chain, 0, hTarget, ahVar, aiReturn);
        }


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


    // ----- property operations -------------------------------------------------------------------

    /**
     * Retrieve a property value.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param iReturn    the register id to place the results of operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    default int getPropertyValue(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    /**
     * Retrieve a field value.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param property   the PropertyStructure representing the property
     * @param iReturn    the register id to place the results of operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    default int getFieldValue(Frame frame, ObjectHandle hTarget,
                              PropertyStructure property, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    /**
     * Set a property value.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param hValue     the new value
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    default int setPropertyValue(Frame frame, ObjectHandle hTarget,
                                 String sPropName, ObjectHandle hValue)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    /**
     * Set a field value.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param property   the PropertyStructure representing the property
     * @param hValue     the new value
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    default int setFieldValue(Frame frame, ObjectHandle hTarget,
                              PropertyStructure property, ObjectHandle hValue)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    /**
     * Increment the property value and retrieve the new value.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param iReturn    the register id to place the results of operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    default int invokePreInc(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    /**
     * Retrieve the property value and then increment it.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param iReturn    the register id to place the results of operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    default int invokePostInc(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }


    // ----- native operations ---------------------------------------------------------------------

    /**
     * Invoke a native property "get" operation.
     *
     * @param frame     the current frame
     * @param property  the PropertyStructure representing the property
     * @param hTarget   the target handle
     * @param iReturn   the register id to place the result of invocation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    default int invokeNativeGet(Frame frame, PropertyStructure property, ObjectHandle hTarget, int iReturn)
        {
        throw new IllegalStateException("Unknown property: " + property.getName() + " on " + this);
        }

    /**
     * Invoke a native property "set" operation.
     *
     * @param frame     the current frame
     * @param property  the PropertyStructure representing the property
     * @param hTarget   the target handle
     * @param hValue    the new property value
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    default int invokeNativeSet(Frame frame, ObjectHandle hTarget, PropertyStructure property, ObjectHandle hValue)
        {
        throw new IllegalStateException("Unknown property: " + property.getName() + " on " + this);
        }

    /**
     * Invoke a native method with exactly one argument and zero or one return value.
     *
     * @param frame    the current frame
     * @param method   the target method
     * @param hTarget  the target handle
     * @param hArg     the invocation arguments
     * @param iReturn  the register id to place the result of invocation into
     *
     * @return one of the {@link Op#R_CALL}, {@link Op#R_EXCEPTION}, or {@link Op#R_BLOCK} values
     */
    default int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Unknown method: " + method + " on " + this);
        }

    /**
     * Invoke a native method with zero or more than one argument and zero or one return value.
     *
     * @param frame    the current frame
     * @param method   the target method
     * @param hTarget  the target handle
     * @param ahArg    the invocation arguments
     * @param iReturn  the register id to place the result of invocation into
     *
     * @return one of the {@link Op#R_CALL}, {@link Op#R_EXCEPTION}, or {@link Op#R_BLOCK} values
     */
    default int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        throw new IllegalStateException("Unknown method: " + method + " on " + this);
        }

    /**
     * Invoke a native method with any number of argument and return value of a Tuple.
     *
     * @param frame    the current frame
     * @param method   the target method
     * @param hTarget  the target handle
     * @param ahArg    the invocation arguments
     * @param iReturn  the register id to place the resulting Tuple into
     *
     * @return one of the {@link Op#R_CALL}, {@link Op#R_EXCEPTION}, or {@link Op#R_BLOCK} values
     */
    default int invokeNativeT(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        throw new IllegalStateException("Unknown method: " + method + " on " + this);
        }

    /**
     * Invoke a native method with any number of arguments and more than one return value.
     *
     * @param frame     the current frame
     * @param method    the target method
     * @param hTarget   the target handle
     * @param ahArg     the invocation arguments
     * @param aiReturn  the array of register ids to place the results of invocation into
     *
     * @return one of the {@link Op#R_CALL}, {@link Op#R_EXCEPTION}, or {@link Op#R_BLOCK} values
     */
    default int invokeNativeNN(Frame frame, MethodStructure method,
                              ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
        {
        throw new IllegalStateException("Unknown method: " + method + " on " + this);
        }
    }
