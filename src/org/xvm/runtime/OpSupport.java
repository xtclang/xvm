package org.xvm.runtime;


import org.xvm.asm.Constant;
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

    /**
     * Obtain a canonical type that is represented by this {@link OpSupport} object
     *
     * Note: the following should always hold true: getCanonicalType().getOpSupport() == this;
     */
    TypeConstant getCanonicalType();

    /**
     * Produce a TypeComposition for this type using the specified actual (inception) type
     * and the revealed (mask) type.
     *
     * Note: the passed actual type should be fully resolved (no formal parameters)
     * Note2: the following should always hold true: typeActual.getOpSupport() == this;
     */
    TypeComposition ensureClass(TypeConstant typeActual, TypeConstant typeMask);

    /**
     * Create an object handle for the specified constant.
     *
     * @param frame     the current frame
     * @param constant  the constant
     *
     * @return the corresponding {@link ObjectHandle}
     */
    default ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }


    // ----- invocations ---------------------------------------------------------------------------

    /**
     * Construct an {@link ObjectHandle} of the specified class with the specified constructor.
     *
     * The following steps are to be performed:
     * <ul>
     *   <li>Invoke the default constructors for the inheritance chain starting at the base;
     *   <li>Invoke the specified constructor, potentially calling some super constructors
     *       passing "this:struct" as a target
     *   <li>Invoke all finalizers in the inheritance chain starting at the base passing
     *       "this:private" as a target
     * </ul>
     *
     * @param frame        the current frame
     * @param constructor  the MethodStructure for the constructor
     * @param clazz        the target class
     * @param ahVar        the construction parameters
     * @param iReturn      the register id to place the created handle into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    default int construct(Frame frame, MethodStructure constructor,
                         TypeComposition clazz, ObjectHandle[] ahVar, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

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


    // ----- Ref operations ------------------------------------------------------------------------

    /**
     * Create a Ref or Var for the specified referent class.
     *
     * Most commonly, the returned handle is an uninitialized Var, but
     * in the case of InjectedRef, it's an initialized [read-only] Ref.
     *
     * @param clazz  the referent class
     * @param sName  an optional Ref name
     *
     * @return the corresponding {@link RefHandle}
     */
    default RefHandle createRefHandle(TypeComposition clazz, String sName)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    /**
     * Create a property Ref or Var for the specified target and property.
     *
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param fRO        true if the
     *
     * @return the corresponding {@link RefHandle}
     */
    default RefHandle createPropertyRef(ObjectHandle hTarget, String sPropName, boolean fRO)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }


    // ----- support for equality and comparison ---------------------------------------------------

    /**
     * Compare for equality two object handles that both belong to the specified class.
     *
     * @param frame      the current frame
     * @param hValue1    the first value
     * @param hValue2    the second value
     * @param iReturn    the register id to place a Boolean result into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn);

    /**
     * Compare for order two object handles that both belong to the specified class.
     *
     * @param frame      the current frame
     * @param hValue1    the first value
     * @param hValue2    the second value
     * @param iReturn    the register id to place an Ordered result into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int callCompare(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn);


    // ----- to<String>() support ------------------------------------------------------------------

    /**
     * Build a String handle for a human readable representation of the target handle.
     *
     * @param frame      the current frame
     * @param hTarget    the target
     * @param iReturn    the register id to place a String result into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn);


    // ----- array operations ----------------------------------------------------------------------

    /**
     * Create a one dimensional array for a specified type and arity.
     *
     * @param frame      the current frame
     * @param typeEl     the array type
     * @param cCapacity  the array size
     * @param iReturn    the register id to place the array handle into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    default int createArrayStruct(Frame frame, TypeConstant typeEl, long cCapacity, int iReturn)
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
