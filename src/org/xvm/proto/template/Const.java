package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.proto.ClassTemplate;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.GenericHandle;
import org.xvm.proto.ObjectHandle.JavaLong;
import org.xvm.proto.Op;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;
import org.xvm.proto.Utils;

import org.xvm.proto.template.Enum.EnumHandle;
import org.xvm.proto.template.xString.StringHandle;

import java.util.Iterator;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class Const
        extends ClassTemplate
    {
    public static Const INSTANCE;

    // name of the synthetic property for cached hash value
    public static final String PROP_HASH = "@hash";

    public Const(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        markNativeGetter("hash");
        markNativeMethod("to", VOID, STRING);
        }

    @Override
    public boolean isStateful()
        {
        return true;
        }

    @Override
    public int invokeNativeGet(Frame frame, PropertyStructure property,
                               ObjectHandle hTarget, int iReturn)
        {
        switch (property.getName())
            {
            case "hash":
                return buildHashCode(frame, hTarget, iReturn);
            }

        return super.invokeNativeGet(frame, property, hTarget, iReturn);
        }

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        // if there is an "equals" function, we need to call it
        MethodStructure functionEquals = findCompareFunction("equals", xBoolean.TYPES);
        if (functionEquals != null)
            {
            return frame.call1(functionEquals, null,
                    new ObjectHandle[]{hValue1, hValue2}, iReturn);
            }

        assert (f_struct.getFormat() == Component.Format.CONST);

        return new Equals((GenericHandle) hValue1, (GenericHandle) hValue2,
                clazz, clazz.getFieldNames().iterator(), iReturn).doNext(frame);
        }

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        // if there is an "compare" function, we need to call it
        MethodStructure functionCompare = findCompareFunction("compare", xOrdered.TYPES);
        if (functionCompare != null)
            {
            return frame.call1(functionCompare, null,
                    new ObjectHandle[]{hValue1, hValue2}, iReturn);
            }

        assert (f_struct.getFormat() == Component.Format.CONST);

        return new Compare((GenericHandle) hValue1, (GenericHandle) hValue2,
                clazz, clazz.getFieldNames().iterator(), iReturn).doNext(frame);
        }

    @Override
    public int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        GenericHandle hConst = (GenericHandle) hTarget;

        StringBuilder sb = new StringBuilder()
          .append(hConst.f_clazz.toString())
          .append('{');

        return new ToString(hConst, sb, hConst.f_clazz.getFieldNames().iterator(), iReturn).doNext(frame);
        }

    // build the hashValue and assign it to the specified register
    // returns R_NEXT, R_CALL or R_EXCEPTION
    public int buildHashCode(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        GenericHandle hConst = (GenericHandle) hTarget;

        JavaLong hHash = (JavaLong) hConst.getField(PROP_HASH);
        if (hHash != null)
            {
            return frame.assignValue(iReturn, hHash);
            }

        return new HashGet(hConst, new long[1], hConst.f_clazz.getFieldNames().iterator(), iReturn).doNext(frame);
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
        final private TypeComposition clazz;
        final private Iterator<String> iterFields;
        final private int iReturn;

        public Equals(GenericHandle hValue1, GenericHandle hValue2, TypeComposition clazz,
                      Iterator<String> iterFields, int iReturn)
            {
            this.hValue1 = hValue1;
            this.hValue2 = hValue2;
            this.clazz = clazz;
            this.iterFields = iterFields;
            this.iReturn = iReturn;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            ObjectHandle hResult = frameCaller.getFrameLocal();
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

                TypeComposition classProp = clazz.resolveClass(getProperty(sProp).getType());

                switch (classProp.callEquals(frameCaller, h1, h2, Frame.RET_LOCAL))
                    {
                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    case Op.R_NEXT:
                        ObjectHandle hResult = frameCaller.getFrameLocal();
                        if (hResult == xBoolean.FALSE)
                            {
                            return frameCaller.assignValue(iReturn, hResult);
                            }
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.setContinuation(this);
                        return Op.R_CALL;

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
        final private TypeComposition clazz;
        final private Iterator<String> iterFields;
        final private int iReturn;

        public Compare(GenericHandle hValue1, GenericHandle hValue2, TypeComposition clazz,
                       Iterator<String> iterFields, int iReturn)
            {
            this.hValue1 = hValue1;
            this.hValue2 = hValue2;
            this.clazz = clazz;
            this.iterFields = iterFields;
            this.iReturn = iReturn;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            EnumHandle hResult = (EnumHandle) frameCaller.getFrameLocal();
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

                TypeComposition classProp = clazz.resolveClass(getProperty(sProp).getType());

                switch (classProp.callCompare(frameCaller, h1, h2, Frame.RET_LOCAL))
                    {
                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    case Op.R_NEXT:
                        EnumHandle hResult = (EnumHandle) frameCaller.getFrameLocal();
                        if (hResult != xOrdered.EQUAL)
                            {
                            return frameCaller.assignValue(iReturn, hResult);
                            }
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.setContinuation(this);
                        return Op.R_CALL;

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

        public ToString(GenericHandle hConst, StringBuilder sb, Iterator<String> iterFields, int iReturn)
            {
            this.hConst = hConst;
            this.sb = sb;
            this.iterFields = iterFields;
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
            sb.append(((StringHandle) frameCaller.getFrameLocal()).getValue())
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
    protected static class HashGet
            implements Frame.Continuation
        {
        final private GenericHandle hConst;
        final private long[] holder;
        final private Iterator<String> iterFields;
        final private int iReturn;

        public HashGet(GenericHandle hConst, long[] holder, Iterator<String> iterFields, int iReturn)
            {
            this.hConst = hConst;
            this.iterFields = iterFields;
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
            holder[0] = 37 * holder[0] + ((JavaLong) frameCaller.getFrameLocal()).getValue();
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

                switch (Utils.callHash(frameCaller, hProp))
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
            hConst.m_mapFields.put(PROP_HASH, hHash);

            return frameCaller.assignValue(iReturn, hHash);
            }
        }
    }
