package org.xvm.proto.template;

import org.xvm.proto.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xFutureRef
        extends TypeCompositionTemplate
    {
    public static xFutureRef INSTANCE;

    public xFutureRef(TypeSet types)
        {
        super(types, "x:FutureRef<RefType>", "x:Ref<RefType>", Shape.Mixin);

        INSTANCE = this;
        }

    @Override
    public void initDeclared()
        {
        //    enum Completion {Pending, Result, Error};
        //    public/private Completion completion = Pending;
        //    private Boolean assignable = false;
        //    private Exception? failure = null;
        //    typedef function Void (Completion, RefType?, Exception?) NotifyDependent;
        //    private NotifyDependent? notify = null;

        }

    // TODO: should it extend xRef.RefHandle?
    public static class FutureHandle
            extends ObjectHandle
            implements xRef.Ref
        {
        protected Type m_typeReferent;
        protected CompletableFuture<ObjectHandle> m_future;

        public FutureHandle(TypeComposition clazz, Type typeReferent, CompletableFuture<ObjectHandle> future)
            {
            super(clazz);

            m_typeReferent = typeReferent;
            m_future = future;
            }

        @Override
        public ObjectHandle get()
                throws ExceptionHandle.WrapperException
            {
            try
                {
                // TODO: use the timeout defined on the service
                while (!m_future.isDone())
                    {
                    ServiceContext.getCurrentContext().yield();
                    }
                return m_future.get();
                }
            catch (InterruptedException e)
                {
                throw new UnsupportedOperationException("TODO");
                }
            catch (ExecutionException e)
                {
                Throwable eOrig = e.getCause();
                if (eOrig instanceof ExceptionHandle.WrapperException)
                    {
                    throw (ExceptionHandle.WrapperException) eOrig;
                    }
                throw new UnsupportedOperationException(e);
                }
            }

        @Override
        public ExceptionHandle set(ObjectHandle handle)
            {
            if (handle instanceof FutureHandle)
                {
                assert m_future == null;
                m_future = ((FutureHandle) handle).m_future;
                }
            else if (!m_future.isDone())
                {
                m_future.complete(handle);
                }
            return null;
            }

        @Override
        public String toString()
            {
            return super.toString() + m_future;
            }
        }

    public static FutureHandle makeHandle(Type type, CompletableFuture<ObjectHandle> future)
        {
        return new FutureHandle(INSTANCE.f_clazzCanonical, type, future);
        }
    }
