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
        markCalculated("hash");
        markNativeGetter("hash");
        markNativeMethod("to", VOID, STRING);
        }

    @Override
    public int invokeNativeGet(Frame frame, ObjectHandle hTarget, PropertyStructure property, int iReturn)
        {
        switch (property.getName())
            {
            case "hash":
                return buildHashCode(frame, hTarget, iReturn);
            }

        return super.invokeNativeGet(frame, hTarget, property, iReturn);
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

        GenericHandle hV1 = (GenericHandle) hValue1;
        GenericHandle hV2 = (GenericHandle) hValue2;

        for (Component comp : f_struct.children())
            {
            if (comp instanceof PropertyStructure)
                {
                PropertyStructure property = (PropertyStructure) comp;
                if (isCalculated(property))
                    {
                    continue;
                    }

                String sProp = property.getName();
                ObjectHandle h1 = hV1.getField(sProp);
                ObjectHandle h2 = hV2.getField(sProp);

                if (h1 == null || h2 == null)
                    {
                    frame.m_hException = xException.makeHandle("Unassigned property \"" + sProp +'"');
                    return Op.R_EXCEPTION;
                    }

                TypeComposition classProp = clazz.resolveClass(property.getType());

                int iRet = classProp.callEquals(frame, h1, h2, Frame.RET_LOCAL);
                if (iRet == Op.R_EXCEPTION)
                    {
                    return Op.R_EXCEPTION;
                    }

                ObjectHandle hResult = frame.getFrameLocal();
                if (hResult == xBoolean.FALSE)
                    {
                    return frame.assignValue(iReturn, xBoolean.FALSE);
                    }
                }
            }
        return frame.assignValue(iReturn, xBoolean.TRUE);
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

        ClassStructure struct = f_struct;

        assert (struct.getFormat() == Component.Format.CONST);

        GenericHandle hV1 = (GenericHandle) hValue1;
        GenericHandle hV2 = (GenericHandle) hValue2;

        for (Component comp : struct.children())
            {
            if (comp instanceof PropertyStructure)
                {
                PropertyStructure property = (PropertyStructure) comp;
                if (isCalculated(property))
                    {
                    continue;
                    }

                String sProp = property.getName();
                ObjectHandle h1 = hV1.getField(sProp);
                ObjectHandle h2 = hV2.getField(sProp);

                if (h1 == null || h2 == null)
                    {
                    frame.m_hException = xException.makeHandle("Unassigned property \"" + sProp + '"');
                    return Op.R_EXCEPTION;
                    }

                TypeComposition classProp = clazz.resolveClass(property.getType());

                int iRet = classProp.callCompare(frame, h1, h2, Frame.RET_LOCAL);
                if (iRet == Op.R_EXCEPTION)
                    {
                    return Op.R_EXCEPTION;
                    }

                Enum.EnumHandle hResult = (Enum.EnumHandle) frame.getFrameLocal();
                if (hResult != xOrdered.EQUAL)
                    {
                    return frame.assignValue(iReturn, hResult);
                    }
                }
            }
        return frame.assignValue(iReturn, xOrdered.EQUAL);
        }

    @Override
    public int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        GenericHandle hConst = (GenericHandle) hTarget;

        StringBuilder sb = new StringBuilder()
          .append(hConst.f_clazz.toString())
          .append('{');

        return new ToString(hConst, sb, f_struct.children().iterator(), iReturn).doNext(frame);
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

        return new HashGet(hConst, new long[1], f_struct.children().iterator(), iReturn).doNext(frame);
        }

    // ----- helper classes -----

    /**
     * Helper class for buildStringValue() implementation.
     */
    protected class ToString
            implements Frame.Continuation
        {
        final private GenericHandle hConst;
        final private StringBuilder sb;
        final private Iterator<Component> iter;
        final private int iReturn;

        public ToString(GenericHandle hConst, StringBuilder sb, Iterator<Component> iter, int iReturn)
            {
            this.hConst = hConst;
            this.sb = sb;
            this.iter = iter;
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
            while (iter.hasNext())
                {
                Component comp = iter.next();

                if (!(comp instanceof PropertyStructure))
                    {
                    continue;
                    }

                PropertyStructure property = (PropertyStructure) comp;
                if (isCalculated(property))
                    {
                    continue;
                    }

                String sProp = property.getName();
                ObjectHandle hProp = hConst.getField(sProp);

                sb.append(sProp).append('=');

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

            sb.setLength(sb.length() - 2); // remove the trailing ", "
            sb.append('}');

            return frameCaller.assignValue(iReturn, xString.makeHandle(sb.toString()));
            }
        }

    /**
     * Helper class for buildHashCode() implementation.
     */
    protected class HashGet
            implements Frame.Continuation
        {
        final private GenericHandle hConst;
        final private long[] holder;
        final private Iterator<Component> iter;
        final private int iReturn;

        public HashGet(GenericHandle hConst, long[] holder, Iterator<Component> iter, int iReturn)
            {
            this.hConst = hConst;
            this.iter = iter;
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
            while (iter.hasNext())
                {
                Component comp = iter.next();

                if (!(comp instanceof PropertyStructure))
                    {
                    continue;
                    }

                PropertyStructure property = (PropertyStructure) comp;
                if (isCalculated(property))
                    {
                    continue;
                    }

                String sProp = property.getName();
                ObjectHandle hProp = hConst.getField(sProp);

                if (hProp == null)
                    {
                    frameCaller.m_hException = xException.makeHandle("Unassigned property: \"" + sProp + '"');
                    return Op.R_NEXT;
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
