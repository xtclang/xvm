package org.xvm.proto.template;

import org.xvm.proto.ObjectHandle;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

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
        protected CompletableFuture<ObjectHandle> m_future;

        public FutureHandle(TypeComposition clazz)
            {
            super(clazz);
            }

        public FutureHandle(TypeComposition clazz, CompletableFuture<ObjectHandle> future)
            {
            super(clazz);

            m_future = future;
            }

        protected ObjectHandle get()
            {
            try
                {
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

    public FutureHandle makeHandle(CompletableFuture<ObjectHandle> future)
        {
        return new FutureHandle(INSTANCE.f_clazzCanonical, future);
        }

    }
