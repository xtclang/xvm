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
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xEnumeration;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


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
    public void initDeclared()
        {
        markNativeMethod("allocate", VOID, null);

        getCanonicalType().invalidateTypeInfo();
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
                TypeConstant typePublic    = idClz.getType();
                TypeConstant typeFn        = pool.ensureImmutableTypeConstant(
                        pool.buildFunctionType(TypeConstant.NO_TYPES, typePublic));

                FunctionHandle hFn = new FunctionHandle(typeFn, null)
                    {
                    public int call1(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
                        {
                        return frame.assignValue(iReturn, hInstance);
                        }
                    };
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

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method,
                              ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "allocate":
                {
                TypeConstant typeClz    = hTarget.getType();
                TypeConstant typePublic = typeClz.getParamType(0);

                if (typePublic.isImmutabilitySpecified())
                    {
                    typePublic = typePublic.getUnderlyingType();
                    }
                if (typePublic.isAccessSpecified())
                    {
                    typePublic = typePublic.getUnderlyingType();
                    }

                ClassTemplate template = f_templates.getTemplate(typePublic);

                switch (template.f_struct.getFormat())
                    {
                    case CLASS:
                    case CONST:
                    case MIXIN:
                    case ENUMVALUE:
                        break;

                    default:
                        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                    }

                ClassComposition clz      = f_templates.resolveClass(typePublic);
                ObjectHandle     hStruct  = template.createStruct(frame, clz);
                MethodStructure  methodAI = clz.ensureAutoInitializer();
                if (methodAI != null)
                    {
                    switch (frame.call1(methodAI, hStruct, Utils.OBJECTS_NONE, Op.A_IGNORE))
                        {
                        case Op.R_NEXT:
                            break;

                        case Op.R_CALL:
                            frame.m_frameNext.addContinuation(frameCaller ->
                                frameCaller.assignValues(aiReturn, xBoolean.TRUE, hStruct));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }
                    break;
                    }
                return frame.assignValues(aiReturn, xBoolean.TRUE, hStruct);
                }
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }
    }
