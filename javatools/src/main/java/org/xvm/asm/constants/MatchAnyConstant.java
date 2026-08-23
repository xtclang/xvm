package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Objects;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.util.Hash;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writeMagnitude;


/**
 * Represent the "_" used in a case statement to match any value of a particular type.
 */
public class MatchAnyConstant
        extends ValueConstant {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is any and all values of the specified type.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param type  the type of the any-match
     */
    public MatchAnyConstant(ConstantPool pool, TypeConstant type) {
        super(pool);

        if (type == null) {
            throw new IllegalArgumentException("type of the match required");
        }

        m_constType = type;
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
    public MatchAnyConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException {
        super(pool);

        m_iType = readMagnitude(in);
    }

    @Override
    protected void resolveConstants() {
        m_constType = getConstantPool().getConstant(m_iType, TypeConstant.class);
    }


    // ----- type-specific functionality -----------------------------------------------------------

    @Override
    public TypeConstant getType() {
        return m_constType;
    }

    @Override
    public Object getValue() {
        // there is no correct answer to this question, although null is tempting
        return "_";
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat() {
        return Format.Any;
    }

    @Override
    protected MatchAnyConstant copyForAdoption(AdoptionContext context) {
        // The wildcard's logical value is its type. Do not shallow-clone the value shell:
        // a foreign module-local type cannot be smuggled into the target owner through a value
        // locator, while a shared type is still adopted and interned by target registration.
        var pool = context.pool();
        var type = Objects.requireNonNull(m_constType, "match-any type");
        if (!type.isShared(pool)) {
            throw new IllegalStateException("cannot adopt match-any with foreign type: " + type);
        }
        return new MatchAnyConstant(pool, type);
    }

    @Override
    public boolean containsUnresolved() {
        return !isHashCached() && m_constType.containsUnresolved();
    }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor) {
        visitor.accept(m_constType);
    }

    @Override
    public MatchAnyConstant resolveTypedefs() {
        TypeConstant constOld = m_constType;
        TypeConstant constNew = constOld.resolveTypedefs();
        return constNew == constOld
                ? this
                : getConstantPool().ensureMatchAnyConstant(constNew);
    }

    @Override
    public Object getLocator() {
        return m_constType;
    }

    @Override
    protected int compareDetails(Constant that) {
        return this.m_constType.compareDetails(((MatchAnyConstant) that).m_constType);
    }

    @Override
    public String getValueString() {
        // use the partial binding syntax (why not?)
        return "<" + m_constType.getValueString() + "> _";
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool) {
        m_constType = pool.register(m_constType);
    }

    @Override
    protected void assemble(DataOutput out)
            throws IOException {
        out.writeByte(getFormat().ordinal());
        writeMagnitude(out, m_constType.getPosition());
    }

    @Override
    public String getDescription() {
        return "match-any=" + getType().getValueString();
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode() {
        return Hash.of(m_constType);
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Used during deserialization: holds the index of the type constant.
     */
    private transient int m_iType;

    /**
     * The TypeConstant for the type of the match value.
     */
    private TypeConstant m_constType;
}
