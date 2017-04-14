package org.xvm.proto.template;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool.ClassConstant;
import org.xvm.asm.ConstantPool.MethodConstant;
import org.xvm.proto.*;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xMethod
        extends TypeCompositionTemplate
    {
    public static xMethod INSTANCE;

    public xMethod(TypeSet types)
        {
        // TODO:ParamType extends Tuple, ReturnType extends Tuple
        super(types, "x:Method<TargetType,ParamType,ReturnType>", "x:Object", Shape.Const);

        INSTANCE = this;
        }

    @Override
    public void initDeclared()
        {
        // todo
        ensurePropertyTemplate("name", "x:String");
        }

    @Override
    public ObjectHandle createConstHandle(Constant constant)
        {
        if (constant instanceof MethodConstant)
            {
            MethodConstant constMethod = (MethodConstant) constant;
            ClassConstant constClass = (ClassConstant) constMethod.getNamespace();

            String sTargetClz = ConstantPoolAdapter.getClassName(constClass);
            TypeCompositionTemplate target = f_types.getTemplate(sTargetClz);

            MethodTemplate method = target.getMethodTemplate(constMethod.getName(), "");
            if (method != null)
                {
                return new MethodHandle(f_clazzCanonical, method);
                }
            }
        return null;
        }

    public static class MethodHandle
            extends ObjectHandle
        {
        public MethodTemplate m_method;

        protected MethodHandle(TypeComposition clazz)
            {
            super(clazz);
            }

        protected MethodHandle(TypeComposition clazz, MethodTemplate method)
            {
            super(clazz);

            m_method = method;
            }

        @Override
        public String toString()
            {
            return super.toString() + m_method;
            }
        }

    }
