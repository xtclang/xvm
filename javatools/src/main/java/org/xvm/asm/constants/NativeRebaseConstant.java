package org.xvm.asm.constants;


import java.io.DataOutput;

import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;
import org.xvm.util.Hash;


/**
 * NativeRebaseConstant is a transient, pseudo constant that represents a native type that does
 * not exist outside of (previous to) the runtime, and could not have been naturally created.
 * Its purpose is to provide a native class representation where there is only an interface known.
 *
 * This TypeConstant is *never* registered with the ConstantPool and is intended to be used only
 * by the runtime.
 */
public final class NativeRebaseConstant
        extends ClassConstant {
    /**
     * Construct a {@link NativeRebaseConstant} representing the specified interface.
     */
    public NativeRebaseConstant(ClassConstant constIface) {
        super(constIface.getConstantPool(), constIface.getParentConstant(), constIface.getName());

        assert constIface.getComponent().getFormat() == Component.Format.INTERFACE;

        m_constIface = constIface;
    }


    // ----- type specific methods  ----------------------------------------------------------------

    /**
     * @return the underlying ClassConstant
     */
    public ClassConstant getClassConstant() {
        return m_constIface;
    }


    // ----- IdentityConstant methods --------------------------------------------------------------

    @Override
    public IdentityConstant replaceParentConstant(IdentityConstant idParent) {
        return new NativeRebaseConstant((ClassConstant)
                getClassConstant().replaceParentConstant(idParent));
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public boolean containsUnresolved() {
        return !isHashCached() && (super.containsUnresolved() || m_constIface.containsUnresolved());
    }

    @Override
    public boolean validate(ErrorListener errs) {
        return true;
    }

    @Override
    public Format getFormat() {
        return Format.NativeClass;
    }

    @Override
    protected NativeRebaseConstant copyForAdoption(AdoptionContext context) {
        // Despite the "runtime-only" class comment, native rebase identities flow through
        // compile-time TypeInfo and variance computation (for example isContravariantParameter ->
        // resolveAutoNarrowing -> ensureParameterizedTypeConstant), which re-registers a containing
        // type graph in the compiling module's pool. Failing closed here broke every downstream
        // module compile. Adoption reconstructs against the interface identity adopted into the
        // destination pool, so no source-owner state is carried over; assemble() still rejects any
        // attempt to persist this pseudo constant.
        return new NativeRebaseConstant((ClassConstant) context.pool().register(m_constIface));
    }

    @Override
    protected int compareDetails(Constant that) {
        if (!(that instanceof NativeRebaseConstant)) {
            return -1;
        }
        return m_constIface.compareDetails(((NativeRebaseConstant) that).m_constIface);
    }

    @Override
    protected void assemble(DataOutput out) {
        throw new IllegalStateException();
    }

    @Override
    public int computeHashCode() {
        return Hash.of(m_constIface);
    }

    @Override
    public String toString() {
        return getValueString();
    }

    @Override
    public String getValueString() {
        return "Native(" + m_constIface.getValueString() + ')';
    }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The underlying type.
     */
    private final ClassConstant m_constIface;
}
