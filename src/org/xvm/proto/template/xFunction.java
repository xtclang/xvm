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
        ensureImplement("x:Const");

        //    Tuple invoke(Tuple args)
        //
        //    Type[] ReturnType;
        //
        //    Type[] ParamType;

        addPropertyTemplate("ReturnType", "x:collections.Array<x:Type>");
        addPropertyTemplate("ParamType", "x:collections.Array<x:Type>");

        addMethodTemplate("invoke", new String[]{"x:Tuple"}, new String[]{"x:Tuple"});
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new FunctionHandle(clazz);
        }

    @Override
    public ObjectHandle createConstHandle(Constant constant)
        {
        if (constant instanceof MethodConstant)
            {
            MethodConstant constFunction = (MethodConstant) constant; // TODO: replace with function when implemented
            ClassConstant constClass = (ClassConstant) constFunction.getNamespace();

            String sTargetClz = ConstantPoolAdapter.getClassName(constClass);
            TypeCompositionTemplate target = f_types.getTemplate(sTargetClz);

            return new FunctionHandle(f_clazzCanonical,
                    target.getFunctionTemplate(constFunction));
            }
        return null;
        }


    public static class FunctionHandle
            extends ObjectHandle
        {
        public InvocationTemplate m_invoke;

        protected FunctionHandle(TypeComposition clazz)
            {
            super(clazz);
            }
        protected FunctionHandle(TypeComposition clazz, InvocationTemplate function)
            {
            super(clazz);

            m_invoke = function;
            }
        public FunctionHandle bind(int iArg, ObjectHandle hArg)
            {
            throw new UnsupportedOperationException();
            }

        @Override
        public String toString()
            {
            return super.toString() + m_invoke;
            }
        }

    public static xFunction INSTANCE;
    public static FunctionHandle makeHandle(InvocationTemplate function)
        {
        return new FunctionHandle(INSTANCE.f_clazzCanonical, function);
        }
    }
