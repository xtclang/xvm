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
        extends TypeCompositionTemplate
    {
    public static xFunction INSTANCE;

    public xFunction(TypeSet types)
        {
        super(types, "x:Function", "x:Object", Shape.Interface);

        addImplement("x:Const");

        INSTANCE = this;
        }

    @Override
    public void initDeclared()
        {
        //    Tuple invoke(Tuple args)
        //
        //    Type[] ReturnType;
        //
        //    Type[] ParamType;

        ensurePropertyTemplate("ReturnType", "x:collections.Array<x:Type>");
        ensurePropertyTemplate("ParamType", "x:collections.Array<x:Type>");

        ensureMethodTemplate("invoke", new String[]{"x:Tuple"}, new String[]{"x:Tuple"}).markNative();
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

    @Override
    public ObjectHandle invokeNative01(Frame frame, ObjectHandle hTarget,
                                       MethodTemplate method, ObjectHandle[] ahReturn)
        {
        FunctionHandle function = (FunctionHandle) hTarget;

        return function.invoke(frame, Utils.OBJECTS_NONE, ahReturn);
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

        public ObjectHandle invoke(Frame frame, ObjectHandle[] ahVar, int[] anArg, ObjectHandle[] ahReturn)
            {
            return invoke(frame, Utils.resolveArguments(frame, m_invoke, ahVar, anArg), ahReturn);
            }

        public ObjectHandle invoke(Frame frame, ObjectHandle[] ahArg, ObjectHandle[] ahReturn)
            {
            int cArgs = ahArg.length;
            int cVars = m_invoke.m_cVars;

            assert cArgs <= cVars;

            ObjectHandle[] ahVar;
            if (cVars > cArgs)
                {
                ahVar = new ObjectHandle[cVars];

                if (cArgs > 0)
                    {
                    if (cArgs == 1)
                        {
                        ahVar[0] = ahArg[0];
                        }
                    else
                        {
                        System.arraycopy(ahArg, 0, ahVar, 0, cArgs);
                        }
                    }
                }
            else
                {
                ahVar = ahArg;
                }

            Frame frameNew = frame.f_context.createFrame(frame, m_invoke, null, ahVar);

            ObjectHandle hException = frameNew.execute();

            int cReturns = ahReturn.length;
            assert cReturns <= m_invoke.m_cReturns;

            if (cReturns > 0)
                {
                if (cReturns == 1)
                    {
                    ahReturn[0] = frameNew.f_ahReturn[0];
                    }
                else
                    {
                    System.arraycopy(frameNew.f_ahReturn, 0, ahReturn, 0, cReturns);
                    }
                }

            return hException;
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

    public static class BoundHandle
            extends FunctionHandle
        {
        protected BoundHandle(FunctionHandle handleBase, int iArg, ObjectHandle hArg)
            {
            super(handleBase.f_clazz);
            }
        }

    public static FunctionHandle makeHandle(InvocationTemplate function)
        {
        return new FunctionHandle(INSTANCE.f_clazzCanonical, function);
        }
    }
