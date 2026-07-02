package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.concurrent.CompletableFuture;

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
        return m_handle;
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

        CompletableFuture<ObjectHandle> cfInitialized = m_cfInitialized;
        m_handle            = handle;
        m_fiberInitializing = null;
        m_cfInitialized     = null;

        if (cfInitialized != null) {
            cfInitialized.complete(handle);
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
        if (m_fiberInitializing != null) {
            return false;
        }

        m_fiberInitializing = fiber;
        return true;
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
        Fiber fiberInitializing = m_fiberInitializing;

        assert fiberInitializing != null;
        if (fiber == fiberInitializing) {
            // only the initializing fiber represents true recursive initialization; all others
            // must wait for completion
            m_handle = new InitializingHandle(this);
            return null;
        }

        CompletableFuture<ObjectHandle> cfInitialized = m_cfInitialized;
        if (cfInitialized == null) {
            m_cfInitialized = cfInitialized = new CompletableFuture<>();
        }
        return cfInitialized;
    }

    /**
     * Abort the current singleton initialization.
     *
     * @param e  the exception that prevented initialization
     */
    public void abortInitialization(Throwable e) {
        CompletableFuture<ObjectHandle> cfInitialized = m_cfInitialized;
        m_handle            = null;
        m_fiberInitializing = null;
        m_cfInitialized     = null;

        if (cfInitialized != null) {
            cfInitialized.completeExceptionally(e);
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
     * The ObjectHandle representing this singleton's value. Can be observed outside the
     * main-context fiber.
     */
    private transient volatile ObjectHandle m_handle;

    /**
     * The fiber that is initializing the handle (optional).
     */
    private transient Fiber m_fiberInitializing;

    /**
     * Future completed when a concurrent initialization finishes.
     */
    private transient CompletableFuture<ObjectHandle> m_cfInitialized;
}
