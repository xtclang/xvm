package org.xvm.runtime;


import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.template.Ref.RefHandle;


/**
 * {@link OpSupport} represents a run-time facet of a type.
 */
public interface OpSupport
    {
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

    // assign (Int i = 5;)
    // @return an immutable handle or null if this type doesn't take that constant
    default ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    // invoke the default constructors, then the specified constructor,
    // then finalizers; change this:struct handle to this:public
    // return one of the Op.R_ values
    default int construct(Frame frame, MethodStructure constructor,
                         TypeComposition clazz, ObjectHandle[] ahVar, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    // invoke with a zero or one return value
    // return R_CALL or R_BLOCK
    default int invoke1(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return frame.invoke1(chain, 0, hTarget, ahVar, iReturn);
        }

    // invoke with return value of Tuple
    default int invokeT(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return frame.invokeT(chain, 0, hTarget, ahVar, iReturn);
        }

    // invoke with more than one return value
    default int invokeN(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        return frame.invokeN(chain, 0, hTarget, ahVar, aiReturn);
        }

    // invokeNative property "get" operation
    default int invokeNativeGet(Frame frame, PropertyStructure property, ObjectHandle hTarget, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    // invokeNative property "set" operation
    default int invokeNativeSet(Frame frame, ObjectHandle hTarget, PropertyStructure property, ObjectHandle hValue)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }


    // invokeNative with exactly one argument and zero or one return value
    // place the result into the specified frame register
    // return R_NEXT or R_CALL
    default int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }


    // invokeNative with zero or more than one arguments and zero or one return values
    // return one of the Op.R_ values
    default int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }


    // invokeNative with zero or more arguments and a Tuple return value
    // return one of the Op.R_ values
    default int invokeNativeT(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    // invokeNative with zero or more arguments and more than one return values
    // return one of the Op.R_ values
    default int invokeNativeNN(Frame frame, MethodStructure method,
                              ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
        {
        throw new IllegalStateException("Unknown method: " + method);
        }

    // Add operation; place the result into the specified frame register
    // return one of the Op.R_ values
    default int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    // Sub operation; place the result into the specified frame register
    // return one of the Op.R_ values
    default int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    // Mul operation; place the result into the specified frame register
    // return one of the Op.R_ values
    default int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    // Div operation; place the result into the specified frame register
    // return one of the Op.R_ values
    default int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    // Mod operation; place the result into the specified frame register
    // return one of the Op.R_ values
    default int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    // Neg operation
    // return one of the Op.R_ values
    default int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    // Next operation (Sequential)
    // return either R_NEXT or R_EXCEPTION
    default int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    // Prev operation (Sequential)
    // return either R_NEXT or R_EXCEPTION
    default int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    // ---- OpCode support: register or property operations -----


    // increment the property value and place the result into the specified frame register
    // return R_NEXT, R_CALL or R_EXCEPTION
    default int invokePreInc(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    // place the property value into the specified frame register and increment it
    // return R_NEXT, R_CALL or R_EXCEPTION
    default int invokePostInc(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    // ----- property operations -----

    // get a property value into the specified register
    // return R_NEXT, R_CALL or R_EXCEPTION
    default int getPropertyValue(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    default int getFieldValue(Frame frame, ObjectHandle hTarget,
                              PropertyStructure property, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    // set a property value
    default int setPropertyValue(Frame frame, ObjectHandle hTarget,
                                 String sPropName, ObjectHandle hValue)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    default int setFieldValue(Frame frame, ObjectHandle hTarget,
                              PropertyStructure property, ObjectHandle hValue)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    // ----- Ref operations -----

    /**
     * Create an unassigned RefHandle for the specified class.
     *
     * @param  sName an optional Ref name
     */
    default RefHandle createRefHandle(TypeComposition clazz, String sName)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    // create a property ref for the specified target and property
    default RefHandle createPropertyRef(ObjectHandle hTarget, String sPropName, boolean fRO)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }

    // ----- support for equality and comparison ------

    // compare for equality two object handles that both belong to the specified class
    // return R_NEXT, R_CALL or R_EXCEPTION
    int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn);

    // compare for order two object handles that both belong to the specified class
    // return R_NEXT, R_CALL or R_EXCEPTION
    int callCompare(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn);


    // ----- array operations -----

    // get a handle to an array for the specified class
    // returns R_NEXT or R_EXCEPTION
    default int createArrayStruct(Frame frame, TypeConstant typeEl, long cCapacity, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + this);
        }
    }
