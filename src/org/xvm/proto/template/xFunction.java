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
public class xFunction
        extends xObject
    {
    public xFunction(TypeSet types)
        {
        super(types, "x:Function", "x:Object", Shape.Interface);
        }

    @Override
    public void initDeclared()
        {
        addImplement("x:Const");

        //    Tuple invoke(Tuple args)
        //
        //    Type[] ReturnType;
        //
        //    Type[] ParamType;

        addPropertyTemplate("ReturnType", "x:collections.Array<x:Type>");
        addPropertyTemplate("ParamType", "x:collections.Array<x:Type>");

        addMethodTemplate("invoke", new String[] {"x:Tuple"}, new String[] {"x:Tuple"});
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new FunctionHandle(clazz);
        }

    @Override
    public void assignConstValue(ObjectHandle handle, Constant constant)
        {
        FunctionHandle hThis = (FunctionHandle) handle;
        MethodConstant constFunction = (MethodConstant) constant; // TODO: replace with function when implemented
        ClassConstant constClass = (ClassConstant) constFunction.getNamespace();

        String sTargetClz = ConstantPoolAdapter.getClassName(constClass);
        TypeCompositionTemplate target = f_types.getTemplate(sTargetClz);

        hThis.m_invoke = target.getFunctionTemplate(constFunction.getName(), "");
        }

    public static class FunctionHandle
            extends ObjectHandle
        {
        public InvocationTemplate m_invoke;

        public FunctionHandle(TypeComposition clazz)
            {
            super(clazz);
            }

        @Override
        public String toString()
            {
            return super.toString() + m_invoke;
            }
        }
    }
