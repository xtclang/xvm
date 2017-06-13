package org.xvm.proto.template;

import org.xvm.asm.Constant;
import org.xvm.asm.Constants;
import org.xvm.asm.constants.MethodConstant;

import org.xvm.proto.*;

import org.xvm.proto.template.xService.ServiceHandle;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

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
    public ObjectHandle createConstHandle(Constant constant, ObjectHeap heap)
        {
        if (constant instanceof MethodConstant)
            {
            MethodConstant constFunction = (MethodConstant) constant; // TODO: replace with function when implemented

            String sTargetClz = ConstantPoolAdapter.getClassName(constFunction);
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

        public InvocationTemplate getTemplate()
            {
            return m_invoke;
            }

        public int getReturnCount()
            {
            return m_invoke.m_cReturns;
            }

        public int getVarCount()
            {
            return m_invoke.m_cVars;
            }

        // ----- FunctionHandle interface -----

        // call with one return value to be placed into the specified slot
        // return either R_CALL or R_NEXT
        public int call1(Frame frame, ObjectHandle[] ahArg, int iReturn)
            {
            ObjectHandle[] ahVar = prepareVars(ahArg);

            addBoundArguments(ahVar);

            return call1Impl(frame, ahVar, iReturn);
            }

        // calls with multiple return values
        public int callN(Frame frame, ObjectHandle[] ahArg, int[] aiReturn)
            {
            ObjectHandle[] ahVar = prepareVars(ahArg);

            addBoundArguments(ahVar);

            return callNImpl(frame, ahVar, aiReturn);
            }

        // ----- internal implementation -----

        protected ObjectHandle[] prepareVars(ObjectHandle[] ahArg)
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

        // invoke with zero or one return to be placed into the specified register;
        protected int call1Impl(Frame frame, ObjectHandle[] ahVar, int iReturn)
            {
            ObjectHandle hTarget = m_invoke instanceof MethodTemplate ? ahVar[0] : null;

            return frame.call1(m_invoke, hTarget, ahVar, iReturn);
            }

        // invoke with multiple return values;
        protected int callNImpl(Frame frame, ObjectHandle[] ahVar, int[] aiReturn)
            {
            ObjectHandle hTarget = m_invoke instanceof MethodTemplate ? ahVar[0] : null;

            return frame.callN(m_invoke, hTarget, ahVar, aiReturn);
            }

        public FunctionHandle bind(int iArg, ObjectHandle hArg)
            {
            return new SingleBoundHandle(f_clazz, this, iArg, hArg);
            }

        public FullyBoundHandle bindAll(ObjectHandle[] ahArg)
            {
            return new FullyBoundHandle(f_clazz, this, ahArg);
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
        protected int call1Impl(Frame frame, ObjectHandle[] ahVar, int iReturn)
            {
            return m_hDelegate.call1Impl(frame, ahVar, iReturn);
            }

        @Override
        protected int callNImpl(Frame frame, ObjectHandle[] ahVar, int[] aiReturn)
            {
            return m_hDelegate.callNImpl(frame, ahVar, aiReturn);
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


    // one parameter bound function
    public static class SingleBoundHandle
            extends DelegatingHandle
        {
        protected int m_iArg; // the bound argument index
        protected ObjectHandle m_hArg;

        protected SingleBoundHandle(TypeComposition clazz, FunctionHandle hDelegate,
                                    int iArg, ObjectHandle hArg)
            {
            super(clazz, hDelegate);

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

    // all parameter bound function
    public static class FullyBoundHandle
            extends DelegatingHandle
        {
        protected ObjectHandle[] m_ahArg;
        protected FullyBoundHandle m_next;

        protected FullyBoundHandle(TypeComposition clazz, FunctionHandle hDelegate, ObjectHandle[] ahArg)
            {
            super(clazz, hDelegate);

            m_ahArg = ahArg;
            }

        @Override
        protected void addBoundArguments(ObjectHandle[] ahVar)
            {
            super.addBoundArguments(ahVar);

            // to avoid extra array creation, the argument array may contain unused null elements
            System.arraycopy(m_ahArg, 0, ahVar, 0, Math.min(ahVar.length, m_ahArg.length));
            }

        public FullyBoundHandle chain(FullyBoundHandle handle)
            {
            if (handle != NO_OP)
                {
                m_next = handle;
                }
            return this;
            }

        // @param access - if specified, apply to "this"
        // @return the very first frame to be called
        public Frame callChain(Frame frame, Constants.Access access, Supplier<Frame> continuation)
            {
            if (access != null)
                {
                ObjectHandle hThis = m_ahArg[0];
                m_ahArg[0] = hThis.f_clazz.ensureAccess(hThis, access);
                }

            Frame frameSave = frame.m_frameNext;

            call1(frame, Utils.OBJECTS_NONE, Frame.RET_UNUSED);

            // TODO: what if this function is async and frameThis is null
            Frame frameThis = frame.m_frameNext;

            frame.m_frameNext = frameSave;

            if (m_next == null)
                {
                frameThis.m_continuation = continuation;
                return frameThis;
                }

            Frame frameNext = m_next.callChain(frame, access, continuation);
            frameThis.m_continuation = () -> frameNext;
            return frameThis;
            }

        public static FullyBoundHandle NO_OP = new FullyBoundHandle(
                INSTANCE.f_clazzCanonical, null, null)
            {
            @Override
            public Frame callChain(Frame frame, Constants.Access access, Supplier<Frame> continuation)
                {
                return null;
                }
            };
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
        protected int call1Impl(Frame frame, ObjectHandle[] ahVar, int iReturn)
            {
            ServiceHandle hService = (ServiceHandle) ahVar[0];

            // native method on the service means "execute on the caller's thread"
            if (m_invoke.isNative() || frame.f_context == hService.m_context)
                {
                return super.call1Impl(frame, ahVar, iReturn);
                }

            // TODO: validate that all the arguments are immutable or ImmutableAble
            int cReturns = iReturn == Frame.RET_UNUSED ? 0 : 1;

            CompletableFuture<ObjectHandle> cfResult = hService.m_context.sendInvoke1Request(
                    frame, this, ahVar, cReturns);

            return cReturns == 0 ? Op.R_NEXT :
                frame.assignValue(iReturn, xFutureRef.makeSyntheticHandle(cfResult));
            }

        @Override
        protected int callNImpl(Frame frame, ObjectHandle[] ahVar, int[] aiReturn)
            {
            ServiceHandle hService = (ServiceHandle) ahVar[0];

            // native method on the service means "execute on the caller's thread"
            if (m_invoke.isNative() || frame.f_context == hService.m_context)
                {
                return super.callNImpl(frame, ahVar, aiReturn);
                }

            // TODO: validate that all the arguments are immutable or ImmutableAble

            int cReturns = aiReturn.length;

            CompletableFuture<ObjectHandle[]> cfResult = hService.m_context.sendInvokeNRequest(
                    frame, this, ahVar, cReturns);

            boolean fBlock = false;
            if (cReturns > 0)
                {
                for (int i = 0; i < cReturns; i++)
                    {
                    final int iRet = i;

                    CompletableFuture<ObjectHandle> cfReturn =
                            cfResult.thenApply(ahResult -> ahResult[iRet]);

                    int nR = frame.assignValue(aiReturn[i], xFutureRef.makeSyntheticHandle(cfReturn));
                    if (nR == Op.R_EXCEPTION)
                        {
                        return Op.R_EXCEPTION;
                        }

                    // if any of the assignments block, we need to block it all
                    fBlock |= nR == Op.R_BLOCK;
                    }
                }

            return fBlock ? Op.R_BLOCK : Op.R_NEXT;
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
