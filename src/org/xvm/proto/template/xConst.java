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
import java.util.function.Supplier;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xConst
        extends ClassTemplate
    {
    public static xConst INSTANCE;

    public xConst(TypeSet types, ClassStructure structure, boolean fInstance)
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
        ensurePropertyTemplate("hash").m_fReadOnly = true;
        ensureGetter("hash").m_fNative = true;
        markNativeMethod("to", VOID, STRING);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (ahArg.length)
            {
            case 0:
                if (method.getName().equals("get")) // hash.get()
                    {
                    GenericHandle hConst = (GenericHandle) hTarget;

                    assert method.getParent().getParent().getName().equals("hash");

                    JavaLong hHash = (JavaLong) hConst.getField("@hash");
                    if (hHash == null)
                        {
                        hHash = xInt64.makeHandle(buildHashCode(hTarget));
                        hConst.m_mapFields.put("@hash", hHash);
                        }
                    return frame.assignValue(iReturn, hHash);
                    }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
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
                if (isReadOnly(property))
                    {
                    continue;
                    }

                String sProp = property.getName();
                ObjectHandle h1 = hV1.getField(sProp);
                ObjectHandle h2 = hV2.getField(sProp);

                if (h1 == null || h2 == null)
                    {
                    frame.m_hException = xException.makeHandle("Unassigned property " + sProp);
                    return Op.R_EXCEPTION;
                    }

                TypeComposition classProp = clazz.resolve(property.getType());

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
                if (isReadOnly(property))
                    {
                    continue;
                    }

                String sProp = property.getName();
                ObjectHandle h1 = hV1.getField(sProp);
                ObjectHandle h2 = hV2.getField(sProp);

                if (h1 == null || h2 == null)
                    {
                    frame.m_hException = xException.makeHandle("Unassigned property " + sProp);
                    return Op.R_EXCEPTION;
                    }

                TypeComposition classProp = clazz.resolve(property.getType());

                int iRet = classProp.callCompare(frame, h1, h2, Frame.RET_LOCAL);
                if (iRet == Op.R_EXCEPTION)
                    {
                    return Op.R_EXCEPTION;
                    }

                xEnum.EnumHandle hResult = (xEnum.EnumHandle) frame.getFrameLocal();
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
        ClassStructure struct = f_struct;

        GenericHandle hConst = (GenericHandle) hTarget;

        StringBuilder sb = new StringBuilder()
          .append(hConst.f_clazz.toString())
          .append('{');

        for (Iterator<Component> iter = struct.children().iterator(); iter.hasNext();)
            {
            switch (addToString(frame, hConst, iter.next(), sb))
                {
                case 0:
                    continue;

                case Op.R_NEXT:
                    sb.append(((StringHandle) frame.getFrameLocal()).getValue())
                      .append(", ");
                    continue;

                case Op.R_CALL:
                    Frame frameNext = frame.m_frameNext;
                    frameNext.setContinuation(new Supplier<Frame>()
                        {
                        public Frame get()
                            {
                            sb.append(((StringHandle) frame.getFrameLocal()).getValue())
                              .append(", ");

                            while (iter.hasNext())
                                {
                                switch (addToString(frame, hConst, iter.next(), sb))
                                    {
                                    case 0:
                                        continue;

                                    case Op.R_NEXT:
                                        sb.append(((StringHandle) frame.getFrameLocal()).getValue())
                                          .append(", ");
                                        continue;

                                    case Op.R_CALL:
                                        Frame frameNext = frame.m_frameNext;
                                        frameNext.setContinuation(this);
                                        return frameNext;

                                    case Op.R_EXCEPTION:
                                        return null;

                                    default:
                                        throw new IllegalStateException();
                                    }
                                }

                            sb.setLength(sb.length() - 2); // remove the trailing ", "
                            sb.append('}');

                            frame.assignValue(iReturn, xString.makeHandle(sb.toString()));
                            return null;
                            }
                        });
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }

        sb.setLength(sb.length() - 2); // remove the trailing ", "
        sb.append('}');

        return frame.assignValue(iReturn, xString.makeHandle(sb.toString()));
        }

    // append a string value for the next child component of the constant to the StringBuilder
    // return zero if the child is not to be reflected in the string;
    // R_NEXT if the value has been added; R_CALL if a call has to be made to provide
    // the string value
    protected int addToString(Frame frame, GenericHandle hConst, Component comp, StringBuilder sb)
        {
        if (!(comp instanceof PropertyStructure))
            {
            return 0;
            }

        PropertyStructure property = (PropertyStructure) comp;
        if (isReadOnly(property))
            {
            return 0;
            }

        String sProp = property.getName();
        ObjectHandle hProp = hConst.getField(sProp);

        sb.append(sProp).append('=');

        if (hProp == null)
            {
            // be tolerant here
            sb.append("<unassigned>, ");
            return 0;
            }

        return Utils.callToString(frame, hProp);
        }

    protected long buildHashCode(ObjectHandle hTarget)
        {
        ClassStructure struct = f_struct;

        GenericHandle hConst = (GenericHandle) hTarget;

        long lHash = 0;
        for (Component comp : struct.children())
            {
            if (comp instanceof PropertyStructure)
                {
                PropertyStructure property = (PropertyStructure) comp;
                if (isReadOnly(property))
                    {
                    continue;
                    }

                String sProp = property.getName();
                ObjectHandle hProp = hConst.getField(sProp);

                assert (hProp != null);

                xConst template = (xConst) hProp.f_clazz.f_template;
                lHash = 37 * lHash + template.buildHashCode(hProp);
                }
            }

        return lHash;
        }
    }
