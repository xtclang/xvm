package org.xvm.proto.template;

import org.xvm.proto.*;
import org.xvm.proto.template.xService.ServiceHandle;

import java.util.concurrent.CompletableFuture;

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

    // a reference
    public static class FutureHandle
            extends ObjectHandle
        {
        protected ServiceHandle m_hService;
        protected CompletableFuture<ObjectHandle> m_future;

        public FutureHandle(TypeComposition clazz)
            {
            super(clazz);
            }

        public FutureHandle(TypeComposition clazz, ServiceHandle hService, CompletableFuture<ObjectHandle> future)
            {
            super(clazz);

            m_hService = hService;
            m_future = future;
            }

        protected ObjectHandle get()
            {
            try
                {
                // TODO: use the timeout defined on the service
                while (!m_future.isDone())
                    {
                    m_hService.m_context.yield();
                    }
                return m_future.get();
                }
            catch (Exception e )
                {
                // pass it onto the service handle
                throw new UnsupportedOperationException();
                }
            }

        @Override
        public String toString()
            {
            return super.toString() + m_future;
            }
        }

    public FutureHandle makeHandle(ServiceHandle hService, CompletableFuture<ObjectHandle> future)
        {
        return new FutureHandle(INSTANCE.f_clazzCanonical, hService, future);
        }

    }
