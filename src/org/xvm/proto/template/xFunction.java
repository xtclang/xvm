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

        ensureMethodTemplate("invoke", new String[]{"x:Tuple"}, new String[]{"x:Tuple"});
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new DelegatingHandle(clazz, null);
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
        protected InvocationTemplate m_invoke;

        protected FunctionHandle(TypeComposition clazz, InvocationTemplate function)
            {
            super(clazz);

            m_invoke = function;
            }

        // ----- FunctionHandle interface -----

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

            addBoundArguments(ahVar);

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
            return new BoundHandle(f_clazz, this, iArg, hArg);
            }

        protected void addBoundArguments(ObjectHandle[] ahVar)
            {
            }

        @Override
        public String toString()
            {
            return super.toString() + m_invoke;
            }
        }

    public static class DelegatingHandle
            extends FunctionHandle
        {
        protected FunctionHandle m_hDelegate;

        protected DelegatingHandle(TypeComposition clazz, FunctionHandle hDelegate)
            {
            super(clazz, hDelegate == null ? null : hDelegate.m_invoke);

            m_hDelegate = hDelegate;
            }

        @Override
        public ObjectHandle invoke(Frame frame, ObjectHandle[] ahVar, int[] anArg, ObjectHandle[] ahReturn)
            {
            return m_hDelegate.invoke(frame, ahVar, anArg, ahReturn);
            }

        @Override
        public ObjectHandle invoke(Frame frame, ObjectHandle[] ahArg, ObjectHandle[] ahReturn)
            {
            return m_hDelegate.invoke(frame, ahArg, ahReturn);
            }

        @Override
        protected void addBoundArguments(ObjectHandle[] ahVar)
            {
            m_hDelegate.addBoundArguments(ahVar);
            }

        @Override
        public String toString()
            {
            return getClass().getSimpleName() + " -> " + m_hDelegate;
            }
        }


    // partially bound function
    public static class BoundHandle
            extends DelegatingHandle
        {
        protected int m_iArg; // the bound argument index
        protected ObjectHandle m_hArg;

        protected BoundHandle(TypeComposition clazz, FunctionHandle handleBase,
                              int iArg, ObjectHandle hArg)
            {
            super(clazz, handleBase);

            m_iArg = iArg;
            m_hArg = hArg;
            }

        @Override
        protected void addBoundArguments(ObjectHandle[] ahVar)
            {
            super.addBoundArguments(ahVar);

            int cMove = m_invoke.m_cArgs - (m_iArg + 1); // number of args to move to the right
            if (cMove > 0)
                {
                System.arraycopy(ahVar, m_iArg, ahVar, m_iArg + 1, cMove);
                }
            ahVar[m_iArg] = m_hArg;
            }
        }

    public static class AsyncHandle
            extends FunctionHandle
        {
        protected AsyncHandle(TypeComposition clazz, InvocationTemplate function)
            {
            super(clazz, function);
            }

        // ----- FunctionHandle interface -----

        public ObjectHandle invoke(Frame frame, ObjectHandle[] ahVar, int[] anArg, ObjectHandle[] ahReturn)
            {
            throw new UnsupportedOperationException();
            }

        @Override
        public ObjectHandle invoke(Frame frame, ObjectHandle[] ahArg, ObjectHandle[] ahReturn)
            {
            throw new UnsupportedOperationException();
            }
        }

    public static FunctionHandle makeHandle(InvocationTemplate function)
        {
        TypeCompositionTemplate template = function.getClazzTemplate();
        return template.isService() ?
            new AsyncHandle(INSTANCE.f_clazzCanonical, function) :
            new FunctionHandle(INSTANCE.f_clazzCanonical, function);
        }
    }
