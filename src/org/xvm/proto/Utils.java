package org.xvm.proto;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.Op;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.proto.template.Const;

import java.sql.Timestamp;


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
        SIG_TO_STRING = pool.ensureSignatureConstant("to", atString, atVoid);
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
        CallChain chain = clzValue.getMethodCallChain(Utils.SIG_TO_STRING);

        if (chain.isNative())
            {
            return clzValue.f_template.buildStringValue(frame, hValue, Frame.RET_LOCAL);
            }

        ObjectHandle[] ahVar = new ObjectHandle[chain.getTop().getMaxVars()];
        return clzValue.f_template.invoke1(frame, chain, hValue, ahVar, Frame.RET_LOCAL);
        }

    // ----- PostInc support -----

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
    }
