package org.xvm.asm.constants;


import java.io.DataOutput;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.javajit.TypeSystem;

import org.xvm.util.Hash;


/**
 * Represent a constant that represents an Ecstasy type that can never be optimized by the JIT.
 *
 * Note: this TypeConstant is transient in nature and used only during the Java byte code production;
 * it is never held by any ConstantPool.
 */
public class JitRefOnlyTypeConstant
        extends TypeConstant {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a {@link JitRefOnlyTypeConstant} constant.
     *
     * @param pool       the ConstantPool that will contain this Constant
     * @param constType  an underlying TypeConstant that this constant represents
     */
    public JitRefOnlyTypeConstant(ConstantPool pool, TypeConstant constType) {
        super(pool);

        if (constType == null) {
            throw new IllegalArgumentException("type required");
        }

        m_constType = constType;
    }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isModifyingType() {
        return true;
    }

    @Override
    public TypeConstant getUnderlyingType() {
        return m_constType;
    }

    @Override
    public TypeConstant ensureAccess(Access access) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isNullable() {
        return m_constType.isNullable();
    }

    @Override
    public TypeConstant removeNullable() {
        return isNullable()
                ? cloneSingle(getConstantPool(), m_constType.removeNullable())
                : this;
    }

    @Override
    protected TypeConstant cloneSingle(ConstantPool pool, TypeConstant type) {
        return new JitRefOnlyTypeConstant(pool, type);
    }

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft) {
        return m_constType.calculateRelationToLeft(typeLeft);
    }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight) {
        return m_constType.calculateRelationToRight(typeRight);
    }


    // ----- TypeInfo support ----------------------------------------------------------------------

    @Override
    protected TypeInfo buildTypeInfo(ErrorListener errs) {
        return m_constType.ensureTypeInfoInternal(errs);
    }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected boolean isDuckTypeAbleFrom(TypeConstant typeRight) {
        return m_constType.isDuckTypeAbleFrom(typeRight);
    }


    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public String ensureJitClassName(TypeSystem ts) {
        return m_constType.ensureJitClassName(ts);
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat() {
        return Format.JitRefOnlyType;
    }

    @Override
    public boolean containsUnresolved() {
        return true;
    }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor) {
        visitor.accept(m_constType);
    }

    @Override
    protected int compareDetails(Constant obj) {
        return obj instanceof JitRefOnlyTypeConstant that
                ? this.m_constType.compareTo(that.m_constType)
                : -1;
    }

    @Override
    public String getValueString() {
        return "JitRefOnly " + m_constType.getValueString();
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool) {
        throw new IllegalStateException();
    }

    @Override
    protected void assemble(DataOutput out) {
        throw new IllegalStateException();
    }

    @Override
    public boolean validate(ErrorListener errs) {
        return m_constType.validate(errs);
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode() {
        return Hash.of(m_constType);
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The type referred to.
     */
    private final TypeConstant m_constType;
}