package org.xvm.asm.constants;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.util.Hash;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writeMagnitude;

/**
 * An artificial identity that represents a "pure type", i.e. something that is not a class or other
 * persistent "structure" in the xvm sense of the term.
 */
public class PureIdentityConstant
    extends IdentityConstant {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public PureIdentityConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException {
        super(pool);
        m_iType = readMagnitude(in);
    }

    /**
     * Construct a constant whose value is a class identifier.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param type  the type represented by this IdentityConstant
     */
    public PureIdentityConstant(ConstantPool pool, TypeConstant type) {
        super(pool);
        m_type = type;
    }

    @Override
    protected void resolveConstants() {
        m_type = getConstantPool().getConstant(m_iType, TypeConstant.class);
        super.resolveConstants();
    }


    // ----- IdentityConstant methods --------------------------------------------------------------

    @Override
    public IdentityConstant replaceParentConstant(IdentityConstant idParent) {
        return this;
    }

    @Override
    public String getName() {
        return getValueString();
    }

    @Override
    public ModuleConstant getModuleConstant() {
        if (m_type instanceof UnionTypeConstant union) {
            ModuleConstant mc1 = union.getUnderlyingType().getFileStructure().getModuleId();
            ModuleConstant mc2 = union.getUnderlyingType2().getFileStructure().getModuleId();
            return mc1.equals(mc2) ? mc1 : null;
        } else {
            return super.getModuleConstant();
        }
    }

    @Override
    public boolean isShared(ConstantPool poolOther) {
        if (m_type instanceof UnionTypeConstant union) {
            return union.isShared(poolOther);
        } else {
            return super.isShared(poolOther);
        }
    }

    /**
     * @return true iff this class is a virtual child class
     */
    public boolean isVirtualChild() {
        return m_type.isVirtualChild();
    }

    @Override
    public Component getComponent() {
        // a type union represents at least two components, or maybe none, or anything in-between...
        return null;
    }

    @Override
    public IdentityConstant getParentConstant() {
        return null;
    }

    @Override
    public TypeConstant getFormalType() {
        return getType();
    }

    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat() {
        return Format.PureType;
    }

    @Override
    public boolean isClass() {
        // TODO review
        // all pure types are fundamentally a class (including interfaces) at some level
        return true;
    }

    @Override
    public TypeConstant getType() {
        return m_type;
    }

    @Override
    public IdentityConstant appendTrailingSegmentTo(IdentityConstant that) {
        // TODO review
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsUnresolved() {
        return !isHashCached() && m_type.containsUnresolved();
    }

    @Override
    protected Object getLocator() {
        return m_type;
    }

    @Override
    protected int compareDetails(Constant that) {
        if (!(that instanceof PureIdentityConstant)) {
            return -1;
        }

        return this.m_type.compareTo(((PureIdentityConstant) that).m_type);
    }

    @Override
    public String getValueString() {
        return getType().getValueString();
    }

    @Override
    public int computeHashCode() {
        return Hash.of(m_type);
    }

    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool) {
        m_type = pool.register(m_type);
    }

    @Override
    protected void assemble(DataOutput out)
            throws IOException {
        super.assemble(out);
        writeMagnitude(out, m_type.getPosition());
    }

    @Override
    public String getDescription() {
        return "union=" + getValueString();
    }

    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that specifies the type of the
     * class identified by this constant.
     */
    private int m_iType;

    /**
     * The Class type.
     */
    private TypeConstant m_type;
}
