package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.proto.ClassTemplate;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.GenericHandle;
import org.xvm.proto.ObjectHandle.JavaLong;
import org.xvm.proto.Op;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;

import java.util.Iterator;
import java.util.List;
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
    private static MethodConstant s_constTo; // TODO: should be MethodIdConst

    public xConst(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);

        if (fInstance)
            {
            INSTANCE = this;
            s_constTo = f_types.f_adapter.getMethod(
                    "Object", "to", VOID, STRING).getIdentityConstant();
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

        boolean fFirst = true;
        for (Iterator<Component> iter = struct.children().iterator(); iter.hasNext();)
            {
            Component comp = iter.next();

            if (comp instanceof PropertyStructure)
                {
                PropertyStructure property = (PropertyStructure) comp;
                if (isReadOnly(property))
                    {
                    continue;
                    }

                String sProp = property.getName();
                ObjectHandle hProp = hConst.getField(sProp);

                if (hProp == null)
                    {
                    // be tolerant here
                    continue;
                    }

                if (fFirst)
                    {
                    fFirst = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(sProp).append('=');

                TypeComposition clzProp = hProp.f_clazz;
                List<MethodStructure> callChain = clzProp.getMethodCallChain(s_constTo);
                MethodStructure method = callChain.isEmpty() ? null : callChain.get(0);

                int iResult;
                if (method == null || f_types.f_adapter.isNative(method))
                    {
                    iResult = clzProp.f_template.buildStringValue(frame, hProp, Frame.RET_LOCAL);
                    }
                else
                    {
                    ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(method)];
                    iResult = frame.call1(method, hProp, ahVar, Frame.RET_LOCAL);
                    }

                switch (iResult)
                    {
                    case Op.R_NEXT:
                        sb.append(((xString.StringHandle) frame.getFrameLocal()).getValue());
                        break;

                    case Op.R_CALL:
                        Frame frameNext = frame.m_frameNext;
                        frameNext.setContinuation(new Supplier<Frame>()
                            {
                            @Override
                            public Frame get()
                                {
                                sb.append(((xString.StringHandle) frame.getFrameLocal()).getValue());

                                while (iter.hasNext())
                                    {
                                    Component comp = iter.next();

                                    if (comp instanceof PropertyStructure)
                                        {
                                        PropertyStructure property = (PropertyStructure) comp;
                                        if (isReadOnly(property))
                                            {
                                            continue;
                                            }

                                        String sProp = property.getName();
                                        ObjectHandle hProp = hConst.getField(sProp);

                                        if (hProp == null)
                                            {
                                            // be tolerant here
                                            continue;
                                            }

                                        sb.append(", ");
                                        sb.append(sProp).append('=');

                                        TypeComposition clzProp = hProp.f_clazz;
                                        List<MethodStructure> callChain = clzProp.getMethodCallChain(s_constTo);
                                        MethodStructure method = callChain.isEmpty() ? null : callChain.get(0);

                                        int iResult;
                                        if (method == null || f_types.f_adapter.isNative(method))
                                            {
                                            iResult = clzProp.f_template.buildStringValue(frame, hProp, Frame.RET_LOCAL);
                                            }
                                        else
                                            {
                                            ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(method)];
                                            iResult = frame.call1(method, hProp, ahVar, Frame.RET_LOCAL);
                                            }

                                        switch (iResult)
                                            {
                                            case Op.R_NEXT:
                                                sb.append(((xString.StringHandle) frame.getFrameLocal()).getValue());
                                                break;

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

                                    }

                                sb.append(')');
                                frame.assignValue(iReturn, xString.makeHandle(sb.toString()));
                                return null;
                                }
                            });
                        // fall through
                    case Op.R_EXCEPTION:
                        return iResult;

                    default:
                        throw new IllegalStateException();
                    }
                }
            }
        sb.append('}');

        return frame.assignValue(iReturn, xString.makeHandle(sb.toString()));
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
