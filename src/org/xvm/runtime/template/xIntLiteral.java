package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xString.StringHandle;


/**
 * TODO:
 */
public class xIntLiteral
        extends ClassTemplate
    {
    public xIntLiteral(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);
        }

    @Override
    public void initDeclared()
        {
        markNativeMethod("to", VOID, INT);
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        MethodStructure constructor = m_constructor;
        if (constructor == null)
            {
            ConstantPool pool = f_struct.getConstantPool();
            TypeConstant type = getCanonicalType();
            TypeInfo     info = type.ensureTypeInfo();

            MethodConstant idConstruct = info.findConstructor(
                new TypeConstant[]{pool.typeString()}, null);

            m_constructor = constructor =
                info.getMethodById(idConstruct).getHead().getMethodStructure();
            }

        LiteralConstant constVal = (LiteralConstant) constant;
        ObjectHandle    hText    = xString.makeHandle(constVal.getValue());

        return construct(frame, constructor, getCanonicalClass(), new ObjectHandle[]{hText}, Op.A_STACK);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        GenericHandle hLiteral = (GenericHandle) hTarget;
        switch (method.getName())
            {
            case "to":
                if (method.getReturnTypes()[0].equals(method.getConstantPool().typeInt()))
                    {
                    StringHandle hText = (StringHandle) hLiteral.getField("text");
                    String       sText = hText.getValue();
                    try
                        {
                        return frame.assignValue(iReturn, xInt64.makeHandle(Long.valueOf(sText)));
                        }
                    catch (NumberFormatException e)
                        {
                        return frame.raiseException(xException.makeHandle("Invalid literal " + sText));
                        }
                    }
                break;
            }
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        GenericHandle hLiteral = (GenericHandle) hTarget;
        return frame.assignValue(iReturn, (StringHandle) hLiteral.getField("text"));
        }

    // cached constructor
    private MethodStructure m_constructor;
    }
