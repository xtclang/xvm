package org.xvm.runtime.template.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xEnumeration;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template._native.reflect.xRTFunction;


/**
 * Native Class implementation.
 */
public class xClass
        extends xConst
    {
    public static xClass INSTANCE;

    public xClass(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

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
            ClassConstant    idClz    = (ClassConstant) constant;
            ClassStructure   struct   = (ClassStructure) idClz.getComponent();
            TypeConstant     typeClz  = idClz.getValueType(null);
            ClassComposition clz      = ensureClass(typeClz);
            ClassTemplate    template = clz.getTemplate();

            MethodStructure constructor = f_struct.findMethod("construct", 3);
            ObjectHandle[]  ahVar       = new ObjectHandle[constructor.getMaxVars()];

            // constructor parameters:
            //   required: Composition
            //   required: Map<String, Type>
            //   optional: function PublicType()?
            // TODO
            ahVar[0] = xNullable.NULL;
            ahVar[1] = xNullable.NULL;

            if (struct.isSingleton())
                {
                ConstantPool pool          = frame.poolContext();
                Constant     constInstance = pool.ensureSingletonConstConstant(idClz);
                ObjectHandle hInstance     = frame.getConstHandle(constInstance);

//                TypeConstant typePublic = idClz.getType();
//                TypeConstant typeFn     = frame.poolContext().
//                    buildFunctionType(TypeConstant.NO_TYPES, typePublic);

                xRTFunction.FunctionHandle hFn = new xRTFunction.NativeFunctionHandle(
                    (frameCaller, ahArg, iReturn) -> frameCaller.assignValue(iReturn, hInstance));
                ahVar[2] = hFn;
                }
            else
                {
                ahVar[2] = xNullable.NULL;
                }

            return template.construct(frame, constructor, clz, null, ahVar, Op.A_STACK);
            }

        return super.createConstHandle(frame, constant);
        }
    }
