package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.proto.ClassTemplate;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.GenericHandle;
import org.xvm.proto.Op;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;

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
        markNativeMethod("to", VOID, STRING);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }


    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        // if there is an "equals" function, we need to call it
        MethodStructure functionEquals = getMethod("equals", new String[] {f_sName, f_sName}, BOOLEAN);
        if (functionEquals != null && functionEquals.isStatic())
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
        MethodStructure functionCompare = getMethod("compare",
                new String[] {f_sName, f_sName}, new String[] {"Ordered"});
        if (functionCompare != null && functionCompare.isStatic())
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
    public ObjectHandle.ExceptionHandle buildStringValue(ObjectHandle hTarget, StringBuilder sb)
        {
        ClassStructure struct = f_struct;

        GenericHandle hConst = (GenericHandle) hTarget;

        sb.append(hConst.f_clazz.toString())
          .append('{');

        boolean fFirst = true;
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

                hProp.f_clazz.f_template.buildStringValue(hProp, sb);
                }
            }
        sb.append('}');

        return null;
        }
    }
