package org.xvm.proto.template;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.proto.*;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xMethod
        extends xObject
    {
    public xMethod(TypeSet types)
        {
        // TODO:ParamType extends Tuple, ReturnType extends Tuple
        super(types, "x:Method<TargetType,ParamType,ReturnType>", "x:Object", Shape.Const);
        }

    @Override
    public void initDeclared()
        {
        // todo
        addPropertyTemplate("name", "x:String");
        }

    @Override
    public void assignConstValue(ObjectHandle handle, Constant constant)
        {
        MethodHandle hThis = (MethodHandle) handle;
        ConstantPool.MethodConstant constMethod = (ConstantPool.MethodConstant) constant;
        ConstantPool.ClassConstant constClass = (ConstantPool.ClassConstant) constMethod.getNamespace();

        String sTargetClz = ConstantPoolAdapter.getClassName(constClass);
        TypeCompositionTemplate target = f_types.getTemplate(sTargetClz);

        hThis.m_method = target.getMethodTemplate(constMethod.getName(), "");
        }

    public static class MethodHandle
            extends ObjectHandle
        {
        public MethodTemplate m_method;

        protected MethodHandle(TypeComposition clazz)
            {
            super(clazz);
            }

        @Override
        public String toString()
            {
            return super.toString() + m_method;
            }
        }

    }
