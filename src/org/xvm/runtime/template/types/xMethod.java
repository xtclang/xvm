package org.xvm.runtime.template.types;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.Enum;
import org.xvm.runtime.template.xString;


/**
 * TODO:
 */
public class xMethod
        extends ClassTemplate
    {
    public static xMethod INSTANCE;
    public static TypeConstant TYPE;

    public static Enum ACCESS;

    public xMethod(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            TYPE = f_clazzCanonical.getType();
            }
        }

    @Override
    public void initDeclared()
        {
        markNativeGetter("name");
        markNativeGetter("conditionalReturn");
        markNativeGetter("access");
        markNativeGetter("property");

        ACCESS = (Enum) f_templates.getTemplate(f_sName + ".Access");
        }

    @Override
    public ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof MethodConstant)
            {
            MethodStructure method = (MethodStructure) ((MethodConstant) constant).getComponent();

            // TODO: assert if a function

            return makeHandle(method, frame.getThis().getType());
            }
        return null;
        }

    @Override
    public int invokeNativeGet(Frame frame, PropertyStructure property, ObjectHandle hTarget, int iReturn)
        {
        MethodHandle hMethod = (MethodHandle) hTarget;

        switch (property.getName())
            {
            case "name":
                return frame.assignValue(iReturn, xString.makeHandle(hMethod.f_method.getName()));

            case "access":
                Constants.Access access = hMethod.f_method.getAccess();
                // Constant.Access starts with "Struct"
                return frame.assignValue(iReturn, ACCESS.getEnumByOrdinal(access.ordinal() + 1));
            }
        return super.invokeNativeGet(frame, property, hTarget, iReturn);
        }

    public static MethodHandle makeHandle(MethodStructure method, TypeConstant typeTarget)
        {
        ConstantPool pool = INSTANCE.f_templates.f_container.f_pool;

        TypeConstant typeRet = pool.ensureParameterizedTypeConstant(pool.typeTuple(), method.getReturnTypes());
        TypeConstant typeArg = pool.ensureParameterizedTypeConstant(pool.typeTuple(), method.getParamTypes());

        TypeComposition clzMethod = INSTANCE.ensureParameterizedClass(typeTarget, typeRet, typeArg);

        return new MethodHandle(clzMethod, method);
        }

    public static class MethodHandle
            extends ObjectHandle
        {
        public final MethodStructure f_method;
        public final PropertyStructure f_property;

        protected MethodHandle(TypeComposition clazz, MethodStructure method)
            {
            super(clazz);

            f_method = method;
            f_property = null;
            }

        protected MethodHandle(TypeComposition clazz, PropertyStructure property)
            {
            super(clazz);

            f_method = null;
            f_property = property;
            }

        public TypeConstant getTarget()
            {
            return getType().getActualParamType("TargetType");
            }

        @Override
        public String toString()
            {
            return super.toString() + f_method;
            }
        }
    }
