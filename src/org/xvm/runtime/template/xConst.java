package org.xvm.runtime.template;


import java.util.Iterator;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IntervalConstant;
import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xString.StringHandle;


/**
 * While this template represents a native interface, it never serves as an inception type
 * by itself.
 */
public class xConst
        extends ClassTemplate
    {
    public static xConst INSTANCE;

    public xConst(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        if (this == INSTANCE)
            {
            // equals and Comparable support
            f_struct.findMethod("equals",   3).setNative(true);
            f_struct.findMethod("compare",  3).setNative(true);
            f_struct.findMethod("hashCode", 2).setNative(true);

            // Stringable support
            ClassStructure clzHelper = f_templates.getClassStructure("_native.ConstHelper");

            FN_ESTIMATE_LENGTH = clzHelper.findMethod("estimateStringLength", 2);
            FN_APPEND_TO       = clzHelper.findMethod("appendTo", 3);

            // Interval support
            INTERVAL_CONSTRUCT = f_templates.getClassStructure("Interval").
                findMethod("construct", 2);

            // DateTime support
            DATETIME_CONSTRUCT = f_templates.getClassStructure("DateTime").
                findMethod("construct", 1, pool().typeString());
            DATE_CONSTRUCT     = f_templates.getClassStructure("Date").
                findMethod("construct", 1, pool().typeString());
            TIME_CONSTRUCT     = f_templates.getClassStructure("Time").
                findMethod("construct", 1, pool().typeString());
            DURATION_CONSTRUCT = f_templates.getClassStructure("Duration").
                findMethod("construct", 1, pool().typeString());
            VERSION_CONSTRUCT = f_templates.getClassStructure("rt.Version").
                findMethod("construct", 1, pool().typeString());

            PATH_CONSTRUCT = f_templates.getClassStructure("fs.Path").
                findMethod("construct", 1, pool().typeString());

            HASH_SIG = f_templates.getClassStructure("collections.Hashable").
                findMethod("hashCode", 2).getIdentityConstant().getSignature();
            }
        }

    @Override
    protected boolean isConstructImmutable()
        {
        return true;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof IntervalConstant)
            {
            IntervalConstant constInterval = (IntervalConstant) constant;

            ObjectHandle h1 = frame.getConstHandle(constInterval.getFirst());
            ObjectHandle h2 = frame.getConstHandle(constInterval.getLast());

            TypeConstant     typeInterval = constInterval.getType();
            ClassComposition clzInterval  = f_templates.resolveClass(typeInterval);
            MethodStructure  constructor  = INTERVAL_CONSTRUCT;

            ObjectHandle[] ahArg = new ObjectHandle[constructor.getMaxVars()];
            ahArg[0] = h1;
            ahArg[1] = h2;

            if (Op.anyDeferred(ahArg))
                {
                Frame.Continuation stepNext = frameCaller ->
                    clzInterval.getTemplate().construct(
                        frameCaller, constructor, clzInterval, null, ahArg, Op.A_STACK);
                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
                }
            return clzInterval.getTemplate().construct(
                frame, constructor, clzInterval, null, ahArg, Op.A_STACK);
            }

        Literal:
        if (constant instanceof LiteralConstant)
            {
            ClassComposition clz;
            MethodStructure  constructor;
            switch (constant.getFormat())
                {
                case DateTime:
                    clz         = ensureClass(pool().typeDateTime());
                    constructor = DATETIME_CONSTRUCT;
                    break;

                case Date:
                    clz         = ensureClass(pool().typeDate());
                    constructor = DATE_CONSTRUCT;
                    break;

                case Time:
                    clz         = ensureClass(pool().typeTime());
                    constructor = TIME_CONSTRUCT;
                    break;

                case Duration:
                    clz         = ensureClass(pool().typeDuration());
                    constructor = DURATION_CONSTRUCT;
                    break;

                case Version:
                    clz         = ensureClass(pool().typeVersion());
                    constructor = VERSION_CONSTRUCT;
                    break;

                case Path:
                    clz         = ensureClass(pool().typePath());
                    constructor = PATH_CONSTRUCT;
                    break;

                default:
                    break Literal;
                }

            ObjectHandle[] ahArg = new ObjectHandle[constructor.getMaxVars()];
            ahArg[0] = xString.makeHandle(((LiteralConstant) constant).getValue());

            return construct(frame, constructor, clz, null, ahArg, Op.A_STACK);
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "appendTo":
                {
                return callAppendTo(frame, hTarget, hArg, iReturn);
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "compare":
                return callCompare(frame, getCanonicalClass(), ahArg[1], ahArg[2], iReturn);

            case "estimateStringLength":
                return callEstimateLength(frame, hTarget, iReturn);

            case "equals":
                return callEquals(frame, getCanonicalClass(), ahArg[1], ahArg[2], iReturn);

            case "hashCode":
                return buildHashCode(frame, getCanonicalClass(), ahArg[1], iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    protected int callEqualsImpl(Frame frame,  ClassComposition clazz,
                                 ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        // Note: the actual types could be subclasses of the specified class
        return new Equals((GenericHandle) hValue1, (GenericHandle) hValue2,
            clazz.getFieldNames().iterator(), iReturn).doNext(frame);
        }

    @Override
    protected int callCompareImpl(Frame frame, ClassComposition clazz,
                                  ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        // Note: the actual types could be subclasses of the specified class
        return new Compare((GenericHandle) hValue1, (GenericHandle) hValue2,
            clazz.getFieldNames().iterator(), iReturn).doNext(frame);
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        GenericHandle hConst = (GenericHandle) hTarget;

        StringBuilder sb = new StringBuilder()
          .append(hConst.getComposition().toString())
          .append('{');

        return new ToString(hConst, sb, iReturn).doNext(frame);
        }

    // build the hashValue and assign it to the specified register
    // returns R_NEXT, R_CALL or R_EXCEPTION
    protected int buildHashCode(Frame frame, ClassComposition clazz, ObjectHandle hTarget, int iReturn)
        {
        GenericHandle hConst = (GenericHandle) hTarget;

        // allow caching the hash only if the targeting class is the actual object's class
        boolean fCache = hConst.getComposition().equals(clazz);
        if (fCache)
            {
            JavaLong hHash = (JavaLong) hConst.getField(PROP_HASH);
            if (hHash != null)
                {
                return frame.assignValue(iReturn, hHash);
                }
            }

        return new HashCode(hConst, clazz.getFieldNames().iterator(), fCache, iReturn).doNext(frame);
        }

    /**
     * Native implementation of the "estimateStringLength" method.
     *
     * @param frame    the frame
     * @param hTarget  the target Const value
     * @param iReturn  the register id to place the result of the call into
     *
     * @return one of R_NEXT, R_CALL or R_EXCEPTION
     */
    protected int callEstimateLength(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        GenericHandle   hConst = (GenericHandle) hTarget;
        TypeComposition clz    = hConst.getComposition();

        StringHandle[] ahNames  = clz.getFieldNameArray();
        ObjectHandle[] ahFields = clz.getFieldValueArray(hConst);
        if (ahNames.length > 0)
            {
            ArrayHandle hNames  = xArray.makeStringArrayHandle(ahNames);
            ArrayHandle hValues = xArray.makeObjectArrayHandle(ahFields);

            // estimateStringLength(String[] names, Object[] fields)
            ObjectHandle[] ahVars = new ObjectHandle[FN_ESTIMATE_LENGTH.getMaxVars()];
            ahVars[0] = hNames;
            ahVars[1] = hValues;

            return frame.call1(FN_ESTIMATE_LENGTH, null, ahVars, iReturn);
            }
        else
            {
            return frame.assignValue(iReturn, xInt64.makeHandle(0));
            }
        }

    /**
     * Native implementation of the "appendTo" method.
     *
     * @param frame      the frame
     * @param hTarget    the target Const value
     * @param hAppender  the appender
     * @param iReturn    the register id to place the result of the call into
     *
     * @return one of R_NEXT, R_CALL or R_EXCEPTION
     */
    protected int callAppendTo(Frame frame, ObjectHandle hTarget, ObjectHandle hAppender, int iReturn)
        {
        GenericHandle   hConst = (GenericHandle) hTarget;
        TypeComposition clz    = hConst.getComposition();

        StringHandle[] ahNames  = clz.getFieldNameArray();
        ObjectHandle[] ahFields = clz.getFieldValueArray(hConst);

        if (ahNames.length > 0)
            {
            ArrayHandle hNames  = xArray.makeStringArrayHandle(ahNames);
            ArrayHandle hValues = xArray.makeObjectArrayHandle(ahFields);

            // appendTo(Appender<Char> appender, String[] names, Object[] fields)
            ObjectHandle[] ahVars = new ObjectHandle[FN_APPEND_TO.getMaxVars()];
            ahVars[0] = hAppender; // appender
            ahVars[1] = hNames;
            ahVars[2] = hValues;

            return frame.call1(FN_APPEND_TO, null, ahVars, iReturn);
            }
        else
            {
            return Op.R_NEXT;
            }
        }


    // ----- helper classes -----

    /**
     * Helper class for equals() implementation.
     */
    protected class Equals
            implements Frame.Continuation
        {
        final private GenericHandle hValue1;
        final private GenericHandle hValue2;
        final private Iterator<String> iterFields;
        final private int iReturn;

        public Equals(GenericHandle hValue1, GenericHandle hValue2,
                      Iterator<String> iterFields, int iReturn)
            {
            this.hValue1 = hValue1;
            this.hValue2 = hValue2;
            this.iterFields = iterFields;
            this.iReturn = iReturn;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            ObjectHandle hResult = frameCaller.popStack();
            if (hResult == xBoolean.FALSE)
                {
                return frameCaller.assignValue(iReturn, hResult);
                }
            return doNext(frameCaller);
            }

        public int doNext(Frame frameCaller)
            {
            ClassComposition clz = (ClassComposition) hValue1.getComposition();
            while (iterFields.hasNext())
                {
                String sProp = iterFields.next();

                if (!clz.isRegular(sProp))
                    {
                    continue;
                    }

                ObjectHandle h1 = hValue1.getField(sProp);
                ObjectHandle h2 = hValue2.getField(sProp);

                if (h1 == null || h2 == null)
                    {
                    return frameCaller.raiseException(
                            xException.makeHandle("Unassigned property \"" + sProp +'"'));
                    }

                TypeConstant typeProp = clz.getFieldType(sProp).
                    resolveGenerics(frameCaller.poolContext(), frameCaller.getGenericsResolver());

                switch (typeProp.callEquals(frameCaller, h1, h2, Op.A_STACK))
                    {
                    case Op.R_NEXT:
                        ObjectHandle hResult = frameCaller.popStack();
                        if (hResult == xBoolean.FALSE)
                            {
                            return frameCaller.assignValue(iReturn, hResult);
                            }
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }
            return frameCaller.assignValue(iReturn, xBoolean.TRUE);
            }
        }

    /**
     * Helper class for compare() implementation.
     */
    protected class Compare
            implements Frame.Continuation
        {
        final private GenericHandle hValue1;
        final private GenericHandle hValue2;
        final private Iterator<String> iterFields;
        final private int iReturn;

        public Compare(GenericHandle hValue1, GenericHandle hValue2,
                       Iterator<String> iterFields, int iReturn)
            {
            this.hValue1 = hValue1;
            this.hValue2 = hValue2;
            this.iterFields = iterFields;
            this.iReturn = iReturn;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            EnumHandle hResult = (EnumHandle) frameCaller.popStack();
            if (hResult != xOrdered.EQUAL)
                {
                return frameCaller.assignValue(iReturn, hResult);
                }
            return doNext(frameCaller);
            }

        public int doNext(Frame frameCaller)
            {
            ConstantPool     pool = frameCaller.poolContext();
            ClassComposition clz  = (ClassComposition) hValue1.getComposition();
            while (iterFields.hasNext())
                {
                String sProp = iterFields.next();

                if (!clz.isRegular(sProp))
                    {
                    continue;
                    }

                ObjectHandle h1 = hValue1.getField(sProp);
                ObjectHandle h2 = hValue2.getField(sProp);

                if (h1 == null || h2 == null)
                    {
                    return frameCaller.raiseException(
                            xException.makeHandle("Unassigned property \"" + sProp +'"'));
                    }

                TypeConstant typeProp = clz.getFieldType(sProp).
                    resolveGenerics(pool, frameCaller.getGenericsResolver());

                // this check is only to provide a better exception description
                if (typeProp.findCallable(pool.sigCompare()) == null)
                    {
                    return frameCaller.raiseException(
                            xException.makeHandle("Property \"" + sProp + " is not Orderable"));
                    }

                switch (typeProp.callCompare(frameCaller, h1, h2, Op.A_STACK))
                    {
                    case Op.R_NEXT:
                        EnumHandle hResult = (EnumHandle) frameCaller.popStack();
                        if (hResult != xOrdered.EQUAL)
                            {
                            return frameCaller.assignValue(iReturn, hResult);
                            }
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }
            return frameCaller.assignValue(iReturn, xOrdered.EQUAL);
            }
        }

    /**
     * Helper class for buildStringValue() implementation.
     */
    protected static class ToString
            implements Frame.Continuation
        {
        final private GenericHandle hConst;
        final private StringBuilder sb;
        final private Iterator<String> iterFields;
        final private int iReturn;
        private int cProps;

        public ToString(GenericHandle hConst, StringBuilder sb, int iReturn)
            {
            this.hConst = hConst;
            this.sb = sb;
            this.iterFields = hConst.getComposition().getFieldNames().iterator();
            this.iReturn = iReturn;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            updateResult(frameCaller);

            return doNext(frameCaller);
            }

        protected void updateResult(Frame frameCaller)
            {
            sb.append(((StringHandle) frameCaller.popStack()).getValue())
              .append(", ");
            }

        protected int doNext(Frame frameCaller)
            {
            ClassComposition clz = (ClassComposition) hConst.getComposition();
            while (iterFields.hasNext())
                {
                String sProp = iterFields.next();

                if (!clz.isRegular(sProp))
                    {
                    continue;
                    }

                ObjectHandle hProp = hConst.getField(sProp);

                sb.append(sProp).append('=');
                cProps++;

                if (hProp == null)
                    {
                    // be tolerant here
                    sb.append("<unassigned>, ");
                    continue;
                    }

                switch (Utils.callToString(frameCaller, hProp))
                    {
                    case Op.R_NEXT:
                        updateResult(frameCaller);
                        continue;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }

            if (cProps > 0)
                {
                sb.setLength(sb.length() - 2); // remove the trailing ", "
                }
            sb.append('}');

            return frameCaller.assignValue(iReturn, xString.makeHandle(sb.toString()));
            }
        }

    /**
     * Helper class for buildHashCode() implementation.
     */
    protected static class HashCode
            implements Frame.Continuation
        {
        final private GenericHandle    hConst;
        final private Iterator<String> iterFields;
        final private boolean          fCache;
        final private int              iReturn;
        private       long             lResult;

        public HashCode(GenericHandle hConst, Iterator<String> iterFields, boolean fCache, int iReturn)
            {
            this.hConst     = hConst;
            this.iterFields = iterFields;
            this.fCache     = fCache;
            this.iReturn    = iReturn;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            updateResult(frameCaller);

            return doNext(frameCaller);
            }

        protected void updateResult(Frame frameCaller)
            {
            lResult = 37 * lResult + ((JavaLong) frameCaller.popStack()).getValue();
            }

        protected int doNext(Frame frameCaller)
            {
            ClassComposition clz = (ClassComposition) hConst.getComposition();
            while (iterFields.hasNext())
                {
                String sProp = iterFields.next();

                if (!clz.isRegular(sProp))
                    {
                    continue;
                    }

                ObjectHandle hProp = hConst.getField(sProp);
                if (hProp == null)
                    {
                    return frameCaller.raiseException(
                            xException.makeHandle("Unassigned property: \"" + sProp + '"'));
                    }

                TypeConstant typeProp = clz.getFieldType(sProp).
                    resolveGenerics(frameCaller.poolContext(), frameCaller.getGenericsResolver());

                MethodStructure methodHash = typeProp.findCallable(HASH_SIG);
                if (methodHash == null)
                    {
                    // ignore this field
                    continue;
                    }

                int iResult;
                if (methodHash.isNative())
                    {
                    iResult = hProp.getTemplate().invokeNativeN(frameCaller, methodHash, null,
                        new ObjectHandle[] {typeProp.getTypeHandle(), hProp}, Op.A_STACK);
                    }
                else
                    {
                    ObjectHandle[] ahVar = new ObjectHandle[methodHash.getMaxVars()];
                    ahVar[0] = typeProp.getTypeHandle();
                    ahVar[1] = hProp;
                    iResult = frameCaller.call1(methodHash, null, ahVar, Op.A_STACK);
                    }

                switch (iResult)
                    {
                    case Op.R_NEXT:
                        updateResult(frameCaller);
                        continue;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }

            JavaLong hHash = xInt64.makeHandle(lResult);
            if (fCache)
                {
                hConst.setField(PROP_HASH, hHash);
                }

            return frameCaller.assignValue(iReturn, hHash);
            }
        }

    // ----- constants -----------------------------------------------------------------------------

    // name of the synthetic property for cached hash value
    private static final String PROP_HASH = "@hash";

    private static MethodStructure FN_ESTIMATE_LENGTH;
    private static MethodStructure FN_APPEND_TO;
    private static MethodStructure INTERVAL_CONSTRUCT;
    private static MethodStructure DATETIME_CONSTRUCT;
    private static MethodStructure DATE_CONSTRUCT;
    private static MethodStructure TIME_CONSTRUCT;
    private static MethodStructure DURATION_CONSTRUCT;
    private static MethodStructure VERSION_CONSTRUCT;
    private static MethodStructure PATH_CONSTRUCT;

    private static SignatureConstant HASH_SIG;
    }
