package org.xvm.runtime.template;


import java.util.Iterator;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IntervalConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

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

    // name of the synthetic property for cached hash value
    public static final String PROP_HASH = "@hash";

    public static MethodStructure FN_ESTIMATE_LENGTH;
    public static MethodStructure FN_APPEND_TO;
    public static MethodStructure INTERVAL_CONSTRUCT;
    public static ClassComposition CLZ_STRINGS;
    public static ClassComposition CLZ_OBJECTS;

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
            TypeInfo infoConst = getCanonicalType().ensureTypeInfo();

            infoConst.findEqualsFunction().setNative(true);
            infoConst.findCompareFunction().setNative(true);

            // Stringable support
            ClassStructure clzHelper = f_templates.getClassStructure("_native.ConstHelper");

            for (MethodStructure method :
                    ((MultiMethodStructure) clzHelper.getChild("estimateStringLength")).methods())
                {
                FN_ESTIMATE_LENGTH = method;
                }
            for (MethodStructure method :
                    ((MultiMethodStructure) clzHelper.getChild("appendTo")).methods())
                {
                FN_APPEND_TO = method;
                }

            CLZ_STRINGS = f_templates.resolveClass(f_templates.f_adapter.getClassType("collections.Array<String>", null));
            CLZ_OBJECTS = f_templates.resolveClass(f_templates.f_adapter.getClassType("collections.Array<Object>", null));

            // Interval support
            ClassStructure clzInterval = f_templates.getClassStructure("Interval");
            for (MethodStructure idConstruct :
                    ((MultiMethodStructure) clzInterval.getChild("construct")).methods())
                {
                INTERVAL_CONSTRUCT = idConstruct;
                }
            }
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof IntervalConstant)
            {
            IntervalConstant constInterval = (IntervalConstant) constant;

            Constant     const1 = constInterval.getFirst();
            Constant     const2 = constInterval.getLast();
            ObjectHandle h1     = frame.getConstHandle(const1);
            ObjectHandle h2     = frame.getConstHandle(const2);

            TypeConstant     typeInterval = frame.f_context.f_heapGlobal.getConstType(constInterval);
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

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName,
                               ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "hash":
                return buildHashCode(frame, hTarget, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
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
            case "estimateStringLength":
                {
                return callEstimateLength(frame, hTarget, iReturn);
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int callEquals(Frame frame, ClassComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        // if there is an "equals" function that is not native (on the Const itself),
        // we need to call it
        MethodStructure functionEquals = clazz.getType().ensureTypeInfo().findEqualsFunction();
        if (functionEquals != null && !functionEquals.isNative())
            {
            return frame.call1(functionEquals, null,
                    new ObjectHandle[]{hValue1, hValue2}, iReturn);
            }

        assert (f_struct.getFormat() == Component.Format.CONST);

        // default "equals" implementation takes the actual type into the account
        if (!hValue1.getType().equals(hValue2.getType()))
            {
            return frame.assignValue(iReturn, xBoolean.FALSE);
            }

        return new Equals((GenericHandle) hValue1, (GenericHandle) hValue2,
            clazz.getFieldNames().iterator(), iReturn).doNext(frame);
        }

    @Override
    public int callCompare(Frame frame, ClassComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        // if there is an "compare" function, we need to call it
        MethodStructure functionCompare = clazz.getType().ensureTypeInfo().findCompareFunction();
        if (functionCompare != null && !functionCompare.isNative())
            {
            return frame.call1(functionCompare, null,
                    new ObjectHandle[]{hValue1, hValue2}, iReturn);
            }

        assert (f_struct.getFormat() == Component.Format.CONST);

        // default "equals" implementation takes the actual type into the account
        if (!hValue1.getType().equals(hValue2.getType()))
            {
            // use the string comparison for consistency
            return frame.assignValue(iReturn, xOrdered.makeHandle(
                hValue1.getType().getValueString().compareTo(hValue2.getType().getValueString())));
            }

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
    protected int buildHashCode(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        GenericHandle hConst = (GenericHandle) hTarget;

        JavaLong hHash = (JavaLong) hConst.getField(PROP_HASH);
        if (hHash != null)
            {
            return frame.assignValue(iReturn, hHash);
            }

        return new HashCode(hConst, new long[1], iReturn).doNext(frame);
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
            ArrayHandle hNames  = xArray.INSTANCE.createArrayHandle(CLZ_STRINGS, ahNames);
            ArrayHandle hValues = xArray.INSTANCE.createArrayHandle(CLZ_OBJECTS, ahFields);

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
            ArrayHandle hNames  = xArray.INSTANCE.createArrayHandle(CLZ_STRINGS, ahNames);
            ArrayHandle hValues = xArray.INSTANCE.createArrayHandle(CLZ_OBJECTS, ahFields);

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
            while (iterFields.hasNext())
                {
                String sProp = iterFields.next();

                ObjectHandle h1 = hValue1.getField(sProp);
                ObjectHandle h2 = hValue2.getField(sProp);

                if (h1 == null || h2 == null)
                    {
                    return frameCaller.raiseException(
                            xException.makeHandle("Unassigned property \"" + sProp +'"'));
                    }

                TypeConstant typeProp = findProperty(sProp).getType().
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
                        frameCaller.m_frameNext.setContinuation(this);
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
            while (iterFields.hasNext())
                {
                String sProp = iterFields.next();

                ObjectHandle h1 = hValue1.getField(sProp);
                ObjectHandle h2 = hValue2.getField(sProp);

                if (h1 == null || h2 == null)
                    {
                    return frameCaller.raiseException(
                            xException.makeHandle("Unassigned property \"" + sProp +'"'));
                    }

                TypeConstant typeProp = findProperty(sProp).getType().
                    resolveGenerics(frameCaller.poolContext(), frameCaller.getGenericsResolver());

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
                        frameCaller.m_frameNext.setContinuation(this);
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
            while (iterFields.hasNext())
                {
                String sProp = iterFields.next();

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
                        frameCaller.m_frameNext.setContinuation(this);
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
        final private GenericHandle hConst;
        final private long[] holder;
        final private Iterator<String> iterFields;
        final private int iReturn;

        public HashCode(GenericHandle hConst, long[] holder, int iReturn)
            {
            this.hConst = hConst;
            this.iterFields = hConst.getComposition().getFieldNames().iterator();
            this.holder = holder;
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
            holder[0] = 37 * holder[0] + ((JavaLong) frameCaller.popStack()).getValue();
            }

        protected int doNext(Frame frameCaller)
            {
            while (iterFields.hasNext())
                {
                String sProp = iterFields.next();

                ObjectHandle hProp = hConst.getField(sProp);
                if (hProp == null)
                    {
                    return frameCaller.raiseException(
                            xException.makeHandle("Unassigned property: \"" + sProp + '"'));
                    }

                switch (Utils.callGetProperty(frameCaller, hProp, "hash"))
                    {
                    case Op.R_NEXT:
                        updateResult(frameCaller);
                        continue;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.setContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }

            JavaLong hHash = xInt64.makeHandle(holder[0]);
            hConst.setField(PROP_HASH, hHash);

            return frameCaller.assignValue(iReturn, hHash);
            }
        }
    }
