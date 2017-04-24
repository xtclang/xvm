package org.xvm.proto.template;

import org.xvm.asm.Constant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.MethodConstant;

import org.xvm.proto.template.xService.ServiceHandle;

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
            FunctionTemplate function = target.getFunctionTemplate(constFunction);

            if (function != null)
                {
                return new FunctionHandle(f_clazzCanonical, function);
                }
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

        // call with one return value to be placed into the specified slot
        // this method doesn't have to be overridden
        // it simply collects ths specified arguments off the frame's vars
        public ExceptionHandle call1(Frame frame, int[] anArg, int iReturn)
            {
            try
                {
                return call1(frame, frame.getArguments(anArg, m_invoke.m_cVars, 0), iReturn);
                }
            catch (ExceptionHandle.WrapperException e)
                {
                return e.getExceptionHandle();
                }
            }

        public ExceptionHandle call1(Frame frame, ObjectHandle[] ahArg, int iReturn)
            {
            return invoke1Impl(frame.f_context, frame, null, prepareVars(ahArg), iReturn);
            }

        // this method doesn't have to be overridden
        // it simply collects ths specified arguments off the frame's vars
        public ExceptionHandle call(Frame frame, int[] anArg, ObjectHandle[] ahReturn)
            {
            try
                {
                return call(frame, frame.getArguments(anArg, m_invoke.m_cVars, 0), ahReturn);
                }
            catch (ExceptionHandle.WrapperException e)
                {
                return e.getExceptionHandle();
                }
            }

        public ExceptionHandle call(Frame frame, ObjectHandle[] ahArg, ObjectHandle[] ahReturn)
            {
            return invokeImpl(frame.f_context, frame, null, prepareVars(ahArg), ahReturn);
            }

        public ObjectHandle[] prepareVars(ObjectHandle[] ahArg)
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

            return ahVar;
            }

        public ExceptionHandle invoke1(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
            {
            return invoke1Impl(frame.f_context, frame, hTarget, prepareVars(ahArg), iReturn);
            }

        // invoke with zero or one return to be placed into the specified register
        protected ExceptionHandle invoke1Impl(ServiceContext context, Frame frame, ObjectHandle hTarget,
                                           ObjectHandle[] ahVar, int iReturn)
            {
            if (hTarget == null && m_invoke instanceof MethodTemplate)
                {
                hTarget = ahVar[0];
                }

            Frame frameNew = context.createFrame(frame, m_invoke, hTarget, ahVar);

            ExceptionHandle hException = frameNew.execute();

            if (hException == null && iReturn >= 0)
                {
                hException = frame.assignValue(iReturn, frameNew.f_ahReturn[0]);
                }
            return hException;
            }

        public ExceptionHandle invoke(Frame frame, ObjectHandle hTarget,
                                      ObjectHandle[] ahArg, ObjectHandle[] ahReturn)
            {
            return invokeImpl(frame.f_context, frame, hTarget, prepareVars(ahArg), ahReturn);
            }

        // this method is only used by the ServiceContext
        public ExceptionHandle invokeFrameless(ServiceContext context, ObjectHandle hTarget,
                                      ObjectHandle[] ahArg, ObjectHandle[] ahReturn)
            {
            return invokeImpl(context, null, hTarget, prepareVars(ahArg), ahReturn);
            }

        // frame could be null
        protected ExceptionHandle invokeImpl(ServiceContext context, Frame frame, ObjectHandle hTarget,
                                             ObjectHandle[] ahVar, ObjectHandle[] ahReturn)
            {
            if (hTarget == null && m_invoke instanceof MethodTemplate)
                {
                hTarget = ahVar[0];
                }

            Frame frameNew = context.createFrame(frame, m_invoke, hTarget, ahVar);

            ExceptionHandle hException = frameNew.execute();

            if (hException == null)
                {
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
        public ExceptionHandle call(Frame frame, ObjectHandle[] ahArg, ObjectHandle[] ahReturn)
            {
            return m_hDelegate.call(frame, ahArg, ahReturn);
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

        protected BoundHandle(TypeComposition clazz, FunctionHandle hDelegate,
                              int iArg, ObjectHandle hArg)
            {
            super(clazz, hDelegate);

            m_iArg = iArg;
            m_hArg = hArg;
            }

        @Override
        public ExceptionHandle call1(Frame frame, ObjectHandle[] ahArg, int iReturn)
            {
            ObjectHandle[] ahVar = prepareVars(ahArg);

            addBoundArguments(ahVar);

            return invoke1Impl(frame.f_context, frame, null, ahVar, iReturn);
            }

        @Override
        public ExceptionHandle call(Frame frame, ObjectHandle[] ahArg, ObjectHandle[] ahReturn)
            {
            ObjectHandle[] ahVar = prepareVars(ahArg);

            addBoundArguments(ahVar);

            return invokeImpl(frame.f_context, frame, null, ahVar, ahReturn);
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

        @Override
        public ExceptionHandle call1(Frame frame, ObjectHandle[] ahArg, int iReturn)
            {
            ServiceHandle hService = (ServiceHandle) ahArg[0];

            // native method on the service means "execute on the caller's thread"
            if (m_invoke.isNative() || frame.f_context == hService.m_context)
                {
                return invoke1Impl(frame.f_context, frame, hService, prepareVars(ahArg), iReturn);
                }

            xService service = (xService) m_invoke.getClazzTemplate();
            return service.asyncInvoke1(frame, hService, this, ahArg, iReturn);
            }

        @Override
        public ExceptionHandle call(Frame frame, ObjectHandle[] ahArg, ObjectHandle[] ahReturn)
            {
            ServiceHandle hService = (ServiceHandle) ahArg[0];

            // native method on the service means "execute on the caller's thread"
            if (m_invoke.isNative() || frame.f_context == hService.m_context)
                {
                return invokeImpl(frame.f_context, frame, hService, prepareVars(ahArg), ahReturn);
                }

            xService service = (xService) m_invoke.getClazzTemplate();
            return service.asyncInvoke(frame, hService, this, ahArg, ahReturn);
            }
        }

    public static AsyncHandle makeAsyncHandle(InvocationTemplate function)
        {
        assert function.getClazzTemplate().isService();

        return new AsyncHandle(INSTANCE.f_clazzCanonical, function);
        }

    public static FunctionHandle makeHandle(InvocationTemplate function)
        {
        return new FunctionHandle(INSTANCE.f_clazzCanonical, function);
        }
    }
