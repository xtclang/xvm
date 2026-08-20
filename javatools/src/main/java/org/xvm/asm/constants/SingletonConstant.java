package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.runtime.Fiber;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.InitializingHandle;
import org.xvm.util.Hash;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writeMagnitude;


/**
 * Represent a singleton instance of a const (including enum, package, module) or service class as a
 * constant value.
 */
public class SingletonConstant
        extends ValueConstant {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a literal.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param format      the format
     * @param constClass  the class constant for the singleton value
     */
    public SingletonConstant(ConstantPool pool, Format format, IdentityConstant constClass) {
        super(pool);

        switch (format) {
        case SingletonConst:
        case EnumValueConst:
        case SingletonService:
            break;

        default:
            throw new IllegalArgumentException("invalid format " + format);
        }

        if (constClass == null) {
            throw new IllegalArgumentException("class of the singleton value required");
        }

        f_fmt        = format;
        m_constClass = constClass;
    }

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public SingletonConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException {
        super(pool);

        f_fmt    = format;
        m_iClass = readMagnitude(in);
    }

    @Override
    protected void resolveConstants() {
        m_constClass = getConstantPool().getConstant(m_iClass, IdentityConstant.class);
    }


    // ----- type-specific functionality -----------------------------------------------------------

    @Override
    public TypeConstant getType() {
        return m_constClass.getType();
    }

    /**
     * @return  the class constant for the singleton value
     */
    public IdentityConstant getClassConstant() {
        return m_constClass;
    }

    /**
     * {@inheritDoc}
     * @return  the class constant for the singleton value
     */
    @Override
    public Constant getValue() {
        return m_constClass;
    }


    // ----- run-time support  ---------------------------------------------------------------------

    /**
     * @return an ObjectHandle representing this singleton value
     */
    public ObjectHandle getHandle() {
        return f_state.get().handle();
    }

    /**
     * Set the handle for this singleton's value.
     *
     * @param handle  the corresponding handle
     */
    public void setHandle(ObjectHandle handle) {
        // the only scenarios when the singleton value can be reset are when it turns from
        // INITIALIZING to anything or from a struct to an immutable value
        assert handle != null;

        while (true) {
            InitState oldState = f_state.get();
            InitState newState = InitState.completed(handle);
            if (f_state.compareAndSet(oldState, newState)) {
                CompletableFuture<ObjectHandle> cfInitialized = oldState.waiter();
                if (cfInitialized != null) {
                    cfInitialized.complete(handle);
                }
                return;
            }
        }
    }

    /**
     * Mark this ObjectHandle as being initialized.
     *
     * @param fiber  the current fiber
     *
     * @return false iff the ObjectHandle has already been marked as "initializing"
     */
    public boolean markInitializing(Fiber fiber) {
        assert fiber != null;

        // initialization is entered from the main context; record which fiber owns the attempt, so
        // other fibers would wait without being mistaken for recursion
        while (true) {
            InitState oldState = f_state.get();
            if (oldState.handle() != null || oldState.owner() != null) {
                return false;
            }

            if (f_state.compareAndSet(oldState, InitState.initializing(fiber))) {
                return true;
            }
        }
    }

    /**
     * Obtain a future to wait on if this singleton is being initialized by another fiber.
     *
     * @param fiber  the current fiber
     *
     * @return a future for the initialized handle, or null for recursive initialization
     */
    public CompletableFuture<ObjectHandle> getInitializationWaiter(Fiber fiber) {
        assert fiber != null;
        while (true) {
            InitState    oldState = f_state.get();
            ObjectHandle hHandle  = oldState.handle();
            if (hHandle != null && !(hHandle instanceof InitializingHandle)) {
                return CompletableFuture.completedFuture(hHandle);
            }

            Fiber fiberInitializing = oldState.owner();
            if (fiberInitializing == null) {
                return CompletableFuture.completedFuture(null);
            }

            if (fiber == fiberInitializing) {
                // only the initializing fiber represents true recursive initialization; all others
                // must wait for completion
                if (hHandle instanceof InitializingHandle) {
                    return null;
                }

                InitState newState = oldState.withHandle(new InitializingHandle(this));
                if (f_state.compareAndSet(oldState, newState)) {
                    return null;
                }
                continue;
            }

            CompletableFuture<ObjectHandle> cfInitialized = oldState.waiter();
            if (cfInitialized == null) {
                cfInitialized = new CompletableFuture<>();
                InitState newState = oldState.withWaiter(cfInitialized);
                if (!f_state.compareAndSet(oldState, newState)) {
                    continue;
                }
            }
            return cfInitialized;
        }
    }

    /**
     * Abort the current singleton initialization.
     *
     * @param e  the exception that prevented initialization
     */
    public void abortInitialization(Throwable e) {
        while (true) {
            InitState oldState = f_state.get();
            if (f_state.compareAndSet(oldState, InitState.EMPTY)) {
                CompletableFuture<ObjectHandle> cfInitialized = oldState.waiter();
                if (cfInitialized != null) {
                    cfInitialized.completeExceptionally(e);
                }
                return;
            }
        }
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat() {
        return f_fmt;
    }

    @Override
    public boolean containsUnresolved() {
        return !isHashCached() && m_constClass.containsUnresolved();
    }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor) {
        visitor.accept(m_constClass);
    }

    @Override
    public SingletonConstant resolveTypedefs() {
        IdentityConstant constOld = m_constClass;
        IdentityConstant constNew = (IdentityConstant) constOld.resolveTypedefs();
        return constNew == constOld
                ? this
                : getConstantPool().register(new SingletonConstant(getConstantPool(), f_fmt, constNew));
    }

    @Override
    public Object getLocator() {
        return getClassConstant();
    }

    @Override
    protected int compareDetails(Constant that) {
        if (!(that instanceof SingletonConstant)) {
            return -1;
        }
        return this.m_constClass.compareTo(((SingletonConstant) that).m_constClass);
    }

    @Override
    public String getValueString() {
        return m_constClass.getName();
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool) {
        m_constClass = pool.register(m_constClass);
    }

    @Override
    protected void assemble(DataOutput out)
            throws IOException {
        out.writeByte(getFormat().ordinal());
        writeMagnitude(out, m_constClass.getPosition());
    }

    @Override
    public String getDescription() {
        return "singleton-" + (f_fmt == Format.SingletonConst ? "const=" : "service=") +
                m_constClass.getName();
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode() {
        return Hash.of(m_constClass);
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The format of the constant; either SingletonConst or SingletonService.
     */
    private final Format f_fmt;

    /**
     * Used during deserialization: holds the index of the class constant.
     */
    private transient int m_iClass;

    /**
     * The IdentityConstant for the class of the singleton value.
     */
    private IdentityConstant m_constClass;

    /**
     * Runtime initialization state. The field is final; transitions replace immutable snapshots.
     */
    private final transient AtomicReference<InitState> f_state =
            new AtomicReference<>(InitState.EMPTY);

    /**
     * Immutable runtime initialization state.
     *
     * @param handle  the current handle, optionally an InitializingHandle
     * @param owner   the fiber currently initializing the singleton
     * @param waiter  a shared waiter for non-owner fibers
     */
    private record InitState(ObjectHandle handle, Fiber owner,
                             CompletableFuture<ObjectHandle> waiter) {
        static final InitState EMPTY = new InitState(null, null, null);

        static InitState initializing(Fiber owner) {
            return new InitState(null, owner, null);
        }

        static InitState completed(ObjectHandle handle) {
            return new InitState(handle, null, null);
        }

        InitState withHandle(ObjectHandle handle) {
            return new InitState(handle, owner, waiter);
        }

        InitState withWaiter(CompletableFuture<ObjectHandle> waiter) {
            return new InitState(handle, owner, waiter);
        }
    }
}
