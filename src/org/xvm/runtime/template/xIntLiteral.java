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
import org.xvm.runtime.TemplateRegistry;


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
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        ConstantPool    pool     = constant.getConstantPool();
        LiteralConstant constVal = (LiteralConstant) constant;
        ObjectHandle    hText    = xString.makeHandle(constVal.getValue());

        TypeConstant type = getCanonicalType();
        TypeInfo     info = type.ensureTypeInfo();

        MethodConstant  idConstruct = info.findConstructor(new TypeConstant[]{pool.typeString()}, null);
        MethodStructure constructor = info.getMethodById(idConstruct).getHead().getMethodStructure();

        return construct(frame, constructor, getCanonicalClass(), new ObjectHandle[]{hText}, Op.A_STACK);
        }
    }
