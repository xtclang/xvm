package org.xvm.runtime.template.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xEnumeration;


/**
 * Native Class implementation.
 */
public class xClass
        extends ClassTemplate
    {
    public static xClass INSTANCE;

    public xClass(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public ClassTemplate getTemplate(TypeConstant type)
        {
        TypeConstant typeDate = type.getParamType(0);
        return typeDate.isA(pool().typeEnum())
            ? xEnumeration.INSTANCE
            : this;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof ClassConstant)
            {
            ClassConstant    idClz   = (ClassConstant) constant;
            TypeConstant     typeClz = idClz.getValueType(null);
            ClassComposition clz     = ensureClass(typeClz);

            MethodStructure constructor = f_struct.findMethod("construct", 3);
            ObjectHandle[]  ahVar       = new ObjectHandle[constructor.getMaxVars()];
            // constructor parameters:
            //   required: Composition
            //   required: Map<String, Type>
            //   optional: function PublicType()?
            // TODO
            return clz.getTemplate().construct(frame, constructor, clz, null, ahVar, Op.A_STACK);
            }

        return super.createConstHandle(frame, constant);
        }
    }
