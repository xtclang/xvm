package org.xvm.runtime;


import java.sql.Timestamp;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.template.Const;

import org.xvm.runtime.template.Function;
import org.xvm.runtime.template.types.xProperty.PropertyHandle;


/**
 * Various helpers.
 *
 * @author gg 2017.03.10
 */
public abstract class Utils
    {
    public final static int[] ARGS_NONE = new int[0];
    public final static ObjectHandle[] OBJECTS_NONE = new ObjectHandle[0];

    public static SignatureConstant SIG_DEFAULT;
    public static SignatureConstant SIG_TO_STRING;

    public static String formatArray(Object[] ao, String sOpen, String sClose, String sDelim)
        {
        if (ao == null || ao.length == 0)
            {
            return "";
            }

        StringBuilder sb = new StringBuilder();
        sb.append(sOpen);

        boolean fFirst = true;
        for (Object o : ao)
            {
            if (fFirst)
                {
                fFirst = false;
                }
            else
                {
                sb.append(sDelim);
                }
            sb.append(o);
            }
        sb.append(sClose);
        return sb.toString();
        }

    public static void log(String sMsg)
        {
        if (sMsg.charAt(0) == '\n')
            {
            System.out.println();
            sMsg = sMsg.substring(1);
            }
        System.out.println(new Timestamp(System.currentTimeMillis())
            + " " + ServiceContext.getCurrentContext() + ": " + sMsg);
        }

    public static void registerGlobalSignatures(ConstantPool pool)
        {
        TypeConstant tString = pool.getImplicitlyImportedComponent("String").getIdentityConstant().asTypeConstant();

        TypeConstant[] atVoid = new TypeConstant[0];
        TypeConstant[] atString = new TypeConstant[] {tString};
        SIG_DEFAULT = pool.ensureSignatureConstant("default", atVoid, atVoid);
        SIG_TO_STRING = pool.ensureSignatureConstant("to", atVoid, atString);
        }

    /**
     * Ensure that the specified array of arguments is of the specified size.
     *
     * @param ahArg  the array of arguments
     * @param cVars  the desired array size
     *
     * @return the array of the desired size containing all the arguments
     */
    public static ObjectHandle[] ensureSize(ObjectHandle[] ahArg, int cVars)
        {
        int cArgs = ahArg.length;
        if (cArgs == cVars)
            {
            return ahArg;
            }

        if (cArgs < cVars)
            {
            ObjectHandle[] ahVar = new ObjectHandle[cVars];
            System.arraycopy(ahArg, 0, ahVar, 0, cArgs);
            return ahVar;
            }

        throw new IllegalArgumentException("Requested size " + cVars +
            " is less than the array size " + cArgs);
        }

    /**
     * Create a FullyBoundHandle representing a finalizer of the specified constructor.
     *
     * @param constructor  the constructor
     * @param hStruct      the struct handle
     * @param ahArg        the arguments
     *
     * @return a FullyBoundHandle representing the finalizer
     */
    public static Function.FullyBoundHandle makeFinalizer(MethodStructure constructor,
                                          ObjectHandle hStruct, ObjectHandle[] ahArg)
        {
        MethodStructure methodFinally = constructor.getConstructFinally();

        return methodFinally == null ? Function.FullyBoundHandle.NO_OP :
                Function.makeHandle(methodFinally).bindAll(hStruct, ahArg);
        }


    // ----- hash.get() support -----

    // call "hash.get" method for the given const value, placing the result into the frame local
    // return R_EXCEPTION, R_NEXT or R_CALL
    public static int callHash(Frame frame, ObjectHandle hConst)
        {
        TypeComposition clzConst = hConst.f_clazz;
        CallChain chain = clzConst.getPropertyGetterChain("hash");

        if (chain.isNative())
            {
            Const template = (Const) clzConst.f_template; // should we get it from method?
            return template.buildHashCode(frame, hConst, Frame.RET_LOCAL);
            }

        ObjectHandle[] ahVar = new ObjectHandle[chain.getTop().getMaxVars()];
        return clzConst.f_template.invoke1(frame, chain, hConst, ahVar, Frame.RET_LOCAL);
        }

    // ----- to<String> support -----

    // call "to<String>" method for the given value, placing the result into the frame local
    // return R_EXCEPTION, R_NEXT or R_CALL
    public static int callToString(Frame frame, ObjectHandle hValue)
        {
        TypeComposition clzValue = hValue.f_clazz;
        CallChain chain = clzValue.getMethodCallChain(Utils.SIG_TO_STRING, Constants.Access.PUBLIC);

        if (chain.isNative())
            {
            return clzValue.f_template.buildStringValue(frame, hValue, Frame.RET_LOCAL);
            }

        ObjectHandle[] ahVar = new ObjectHandle[chain.getTop().getMaxVars()];
        return clzValue.f_template.invoke1(frame, chain, hValue, ahVar, Frame.RET_LOCAL);
        }


    // ----- Pre/PostInc support -----

    public static class PreInc
            implements Frame.Continuation
        {
        private final ObjectHandle hTarget;
        private final String sPropName;
        private final int iReturn;

        public PreInc(ObjectHandle hTarget, String sPropName, int iReturn)
            {
            this.hTarget = hTarget;
            this.sPropName = sPropName;
            this.iReturn = iReturn;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            ObjectHandle hValueOld = frameCaller.getFrameLocal();

            int iRes = hValueOld.f_clazz.f_template.invokeNext(frameCaller, hValueOld, Frame.RET_LOCAL);
            if (iRes == Op.R_EXCEPTION)
                {
                return Op.R_EXCEPTION;
                }

            ObjectHandle hValueNew = frameCaller.getFrameLocal();
            iRes = frameCaller.assignValue(iReturn, hValueNew);
            if (iRes == Op.R_EXCEPTION)
                {
                return Op.R_EXCEPTION;
                }
            return hTarget.f_clazz.f_template.
                    setPropertyValue(frameCaller, hTarget, sPropName, hValueNew);
            }
        }

    public static class PostInc
            implements Frame.Continuation
        {
        private final ObjectHandle hTarget;
        private final String sPropName;
        private final int iReturn;

        public PostInc(ObjectHandle hTarget, String sPropName, int iReturn)
            {
            this.hTarget = hTarget;
            this.sPropName = sPropName;
            this.iReturn = iReturn;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            ObjectHandle hValueOld = frameCaller.getFrameLocal();

            int iRes = frameCaller.assignValue(iReturn, hValueOld);
            if (iRes == Op.R_EXCEPTION)
                {
                return Op.R_EXCEPTION;
                }
            iRes = hValueOld.f_clazz.f_template.invokeNext(frameCaller, hValueOld, Frame.RET_LOCAL);
            if (iRes == Op.R_EXCEPTION)
                {
                return Op.R_EXCEPTION;
                }
            ObjectHandle hValueNew = frameCaller.getFrameLocal();
            return hTarget.f_clazz.f_template.
                    setPropertyValue(frameCaller, hTarget, sPropName, hValueNew);
            }
        }

    // ----- "local property as an argument" support -----

    static public class GetArgument
                implements Frame.Continuation
        {
        public GetArgument(ObjectHandle[] ahTarget, Frame.Continuation continuation)
            {
            this.ahTarget = ahTarget;
            this.continuation = continuation;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            ahTarget[0] = frameCaller.getFrameLocal();

            return continuation.proceed(frameCaller);
            }

        public int doNext(Frame frameCaller)
            {
            ObjectHandle handle = ahTarget[0];
            if (handle instanceof PropertyHandle)
                {
                ObjectHandle hThis = frameCaller.getThis();
                String sProp = ((PropertyHandle) handle).m_constProperty.getName();

                switch (hThis.f_clazz.f_template.getPropertyValue(
                        frameCaller, hThis, sProp, Frame.RET_LOCAL))
                    {
                    case Op.R_NEXT:
                        // replace the property handle with the value
                        ahTarget[0] = frameCaller.getFrameLocal();
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.setContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }
            return continuation.proceed(frameCaller);
            }

        private final ObjectHandle[] ahTarget;
        private final Frame.Continuation continuation;
        }

    static public class GetArguments
                implements Frame.Continuation
        {
        public GetArguments(ObjectHandle[] ahVar, int[] holderIx, Frame.Continuation continuation)
            {
            this.ahVar = ahVar;
            this.holderIx = holderIx;
            this.continuation = continuation;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            updateResult(frameCaller);

            return doNext(frameCaller);
            }

        protected void updateResult(Frame frameCaller)
            {
            // replace a property handle with the value
            ahVar[holderIx[0]] = frameCaller.getFrameLocal();
            }

        public int doNext(Frame frameCaller)
            {
            for (int iStep = holderIx[0]++; iStep < ahVar.length; )
                {
                ObjectHandle handle = ahVar[iStep];
                if (handle == null)
                    {
                    // nulls can only be at the tail of the array
                    break;
                    }

                if (handle instanceof PropertyHandle)
                    {
                    ObjectHandle hThis = frameCaller.getThis();
                    String sProp = ((PropertyHandle) handle).m_constProperty.getName();

                    switch (hThis.f_clazz.f_template.getPropertyValue(
                            frameCaller, hThis, sProp, Frame.RET_LOCAL))
                        {
                        case Op.R_NEXT:
                            // replace the property handle with the value
                            updateResult(frameCaller);
                            break;

                        case Op.R_CALL:
                            frameCaller.m_frameNext.setContinuation(this);
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }
                    }
                }
            return continuation.proceed(frameCaller);
            }

        private final ObjectHandle[] ahVar;
        private final int[] holderIx;
        private final Frame.Continuation continuation;
        }
    }
