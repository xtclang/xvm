package org.xvm.runtime.template;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.RangeConstant;
import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UInt8Constant;

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

import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xEnum.EnumHandle;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.GenericArrayHandle;
import org.xvm.runtime.template.collections.xBitArray;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;


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
    public void initNative()
        {
        if (this == INSTANCE)
            {
            ConstantPool pool = pool();

            // equals and Comparable support
            getStructure().findMethod("equals",   3).markNative();
            getStructure().findMethod("compare",  3).markNative();
            getStructure().findMethod("hashCode", 2).markNative();

            // Stringable support
            ClassStructure clzHelper = f_templates.getClassStructure("_native.ConstHelper");

            FN_ESTIMATE_LENGTH = clzHelper.findMethod("estimateStringLength", 2);
            FN_APPEND_TO       = clzHelper.findMethod("appendTo", 3);
            FN_FREEZE          = clzHelper.findMethod("freeze", 1);

            // Range support
            RANGE_CONSTRUCT = f_templates.getClassStructure("Range").
                findMethod("construct", 4);

            // Nibble support
            TypeConstant typeBitArray = pool.ensureParameterizedTypeConstant(
                pool.typeArray(), pool.typeBit());
            NIBBLE_CONSTRUCT = f_templates.getClassStructure("numbers.Nibble").
                findMethod("construct", 1, typeBitArray);

            // DateTime support
            DATETIME_CONSTRUCT = f_templates.getClassStructure("temporal.DateTime").
                findMethod("construct", 1, pool.typeString());
            DATE_CONSTRUCT     = f_templates.getClassStructure("temporal.Date").
                findMethod("construct", 1, pool.typeString());
            TIME_CONSTRUCT     = f_templates.getClassStructure("temporal.Time").
                findMethod("construct", 1, pool.typeString());
            DURATION_CONSTRUCT = f_templates.getClassStructure("temporal.Duration").
                findMethod("construct", 1, pool.typeString());
            VERSION_CONSTRUCT = f_templates.getClassStructure("reflect.Version").
                findMethod("construct", 1, pool.typeString());

            PATH_CONSTRUCT = f_templates.getClassStructure("fs.Path").
                findMethod("construct", 1, pool.typeString());

            HASH_SIG = f_templates.getClassStructure("collections.Hashable").
                findMethod("hashCode", 2).getIdentityConstant().getSignature();

            TYPE_FREEZABLE = pool.ensureEcstasyTypeConstant("collections.Freezable");
            }
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof RangeConstant)
            {
            RangeConstant constRange = (RangeConstant) constant;

            ObjectHandle  h1 = frame.getConstHandle(constRange.getFirst());
            ObjectHandle  h2 = frame.getConstHandle(constRange.getLast());
            BooleanHandle f1 = xBoolean.makeHandle(constRange.isFirstExcluded());
            BooleanHandle f2 = xBoolean.makeHandle(constRange.isLastExcluded());

            TypeConstant     typeRange   = constRange.getType();
            ClassComposition clzRange    = f_templates.resolveClass(typeRange);
            MethodStructure  constructor = RANGE_CONSTRUCT;

            ObjectHandle[] ahArg = new ObjectHandle[constructor.getMaxVars()];
            ahArg[0] = h1;
            ahArg[1] = h2;
            ahArg[2] = f1;
            ahArg[3] = f2;

            if (Op.anyDeferred(ahArg))
                {
                Frame.Continuation stepNext = frameCaller ->
                    clzRange.getTemplate().construct(
                        frameCaller, constructor, clzRange, null, ahArg, Op.A_STACK);
                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
                }
            return clzRange.getTemplate().construct(
                frame, constructor, clzRange, null, ahArg, Op.A_STACK);
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

        if (constant.getFormat() == Format.Nibble)
            {
            byte[] abValue = new byte[] {((UInt8Constant) constant).getValue().byteValue()};

            ObjectHandle[] ahArg = new ObjectHandle[NIBBLE_CONSTRUCT.getMaxVars()];
            ahArg[0] = xBitArray.makeHandle(abValue, 4, xArray.Mutability.Constant);

            return construct(frame, NIBBLE_CONSTRUCT, ensureClass(constant.getType()), null, ahArg, Op.A_STACK);
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    protected int postValidate(Frame frame, ObjectHandle hStruct)
        {
        if (hStruct.isMutable())
            {
            GenericHandle hConst = (GenericHandle) hStruct;
            if (hConst.containsMutableFields())
                {
                ClassComposition clz      = (ClassComposition) hStruct.getComposition();
                ObjectHandle[]   ahFields = clz.getFieldValueArray(hConst);
                int              cFields  = ahFields.length;
                if (cFields > 0)
                    {
                    List<String> listFieldNames = clz.getFieldNames();
                    assert listFieldNames.size() == cFields;

                    // remove all immutable and proxied (services and services' children);
                    // collect all freezable into a "freezable" list along with their indexes
                    List<ObjectHandle> listFreezable = null;
                    List<String>       listName      = null;

                    for (int i = 0; i < cFields; i++)
                        {
                        ObjectHandle hField = ahFields[i];
                        if (!hField.isMutable() || hField.isService())
                            {
                            continue;
                            }

                        String sField = listFieldNames.get(i);
                        if (hField.getType().isA(TYPE_FREEZABLE))
                            {
                            if (listFreezable == null)
                                {
                                listFreezable = new ArrayList<>();
                                listName      = new ArrayList<>();
                                }
                            listFreezable.add(hField);
                            listName.add(sField);
                            }
                        else
                            {
                            return frame.raiseException("Field \"" + sField + "\" is not freezable");
                            }
                        }

                    if (listFreezable != null)
                        {
                        ObjectHandle[]     ahFreezable = listFreezable.toArray(Utils.OBJECTS_NONE);
                        String[]           asName      = listName.toArray(new String[0]);
                        GenericArrayHandle hValues     = (GenericArrayHandle)
                            xArray.makeObjectArrayHandle(ahFreezable, xArray.Mutability.Fixed);

                        ObjectHandle[] ahVars = new ObjectHandle[FN_FREEZE.getMaxVars()];
                        ahVars[0] = hValues;

                        Frame frameFreeze = frame.createFrame1(FN_FREEZE, null, ahVars, Op.A_IGNORE);
                        frameFreeze.addContinuation(frameCaller ->
                            {
                            for (int i = 0, c = asName.length; i < c; i++)
                                {
                                // verify that "freeze" didn't widen the type
                                String       sField  = asName[i];
                                ObjectHandle hNew    = hValues.m_ahValue[i];
                                TypeConstant typeOld = hConst.getField(sField).getType();
                                TypeConstant typeNew = hNew.getType();
                                if (typeNew.isA(typeOld))
                                    {
                                    hConst.setField(sField, hNew);
                                    }
                                else
                                    {
                                    TypeConstant typeExpected = frameCaller.poolContext().
                                        ensureImmutableTypeConstant(typeOld);
                                    return frameCaller.raiseException(
                                        "The freeze() result type for the \"" + sField +
                                        "\" field was illegally changed; \"" +
                                        typeExpected.getValueString() + "\" expected, \"" +
                                        typeNew.getValueString() + "\" returned");
                                    }
                                }

                            hConst.makeImmutable();
                            return Op.R_NEXT;
                            });

                        return frame.callInitialized(frameFreeze);
                        }
                    }
                }
            hConst.makeImmutable();
            }
        return Op.R_NEXT;
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
    protected int callEqualsImpl(Frame frame, ClassComposition clazz,
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
            ArrayHandle hValues = xArray.makeObjectArrayHandle(ahFields, xArray.Mutability.Constant);

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

        ArrayHandle hNames  = xArray.makeStringArrayHandle(ahNames);
        ArrayHandle hValues = xArray.makeObjectArrayHandle(ahFields, xArray.Mutability.Constant);

        // appendTo(Appender<Char> appender, String[] names, Object[] fields)
        ObjectHandle[] ahVars = new ObjectHandle[FN_APPEND_TO.getMaxVars()];
        ahVars[0] = hAppender; // appender
        ahVars[1] = hNames;
        ahVars[2] = hValues;

        return frame.call1(FN_APPEND_TO, null, ahVars, iReturn);
        }


    // ----- helper classes -----

    /**
     * Helper class for equals() implementation.
     */
    protected static class Equals
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
                    return frameCaller.raiseException("Unassigned property \"" + sProp +'"');
                    }

                TypeConstant typeProp = clz.getFieldType(sProp).
                    resolveGenerics(pool, frameCaller.getGenericsResolver());

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
    protected static class Compare
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
                    return frameCaller.raiseException("Unassigned property \"" + sProp +'"');
                    }

                TypeConstant typeProp = clz.getFieldType(sProp).
                    resolveGenerics(pool, frameCaller.getGenericsResolver());

                // this check is only to provide a better exception description
                if (typeProp.findCallable(pool.sigCompare()) == null)
                    {
                    return frameCaller.raiseException("Property \"" + sProp + "\" is not Orderable");
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
            ConstantPool     pool = frameCaller.poolContext();
            ClassComposition clz  = (ClassComposition) hConst.getComposition();
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
                    return frameCaller.raiseException("Unassigned property: \"" + sProp + '"');
                    }

                TypeConstant typeProp = clz.getFieldType(sProp).
                    resolveGenerics(pool, frameCaller.getGenericsResolver());

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
                        new ObjectHandle[] {typeProp.ensureTypeHandle(pool), hProp}, Op.A_STACK);
                    }
                else
                    {
                    ObjectHandle[] ahVar = new ObjectHandle[methodHash.getMaxVars()];
                    ahVar[0] = typeProp.ensureTypeHandle(pool);
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
    private static MethodStructure FN_FREEZE;
    private static MethodStructure RANGE_CONSTRUCT;
    private static MethodStructure NIBBLE_CONSTRUCT;
    private static MethodStructure DATETIME_CONSTRUCT;
    private static MethodStructure DATE_CONSTRUCT;
    private static MethodStructure TIME_CONSTRUCT;
    private static MethodStructure DURATION_CONSTRUCT;
    private static MethodStructure VERSION_CONSTRUCT;
    private static MethodStructure PATH_CONSTRUCT;

    private static SignatureConstant HASH_SIG;

    private static TypeConstant TYPE_FREEZABLE;
    }
