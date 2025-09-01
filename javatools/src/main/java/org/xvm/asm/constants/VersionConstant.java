package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Version;


/**
 * Represent a version number.
 */
public class VersionConstant
        extends LiteralConstant {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a PackedInteger.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param ver   the version
     */
    public VersionConstant(ConstantPool pool, Version ver) {
        super(pool, Format.Version, ver.toString(), null);

        m_ver = ver;
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
    public VersionConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException {
        super(pool, format, in);
    }

    @Override
    protected void resolveConstants() {
        super.resolveConstants();

        m_ver = new Version(getValue());
    }


    // ----- ValueConstant methods -----------------------------------------------------------------

    /**
     * @return the fully qualified version number
     */
    public Version getVersion() {
        return m_ver;
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public TypeConstant getType() {
        return getConstantPool().typeVersion();
    }

    @Override
    protected int compareDetails(Constant that) {
        if (!(that instanceof VersionConstant)) {
            return -1;
        }
        return this.m_ver.compareTo(((VersionConstant) that).m_ver);
    }

    @Override
    public String getValueString() {
        return "v:" + getValue();
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public String getDescription() {
        return "version=" + getValue();
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The version indicator for this version.
     */
    private Version m_ver;
}
