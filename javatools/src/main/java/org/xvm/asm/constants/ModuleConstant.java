package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import java.util.function.Consumer;

import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Version;

import org.xvm.javajit.Builder;
import org.xvm.javajit.TypeSystem;

import org.xvm.util.Hash;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a Module constant. A Module constant is composed of a qualified module name, which
 * itself is composed of a domain name and an unqualified (simple) module name. For example, the
 * domain name "xtclang.org" can be combined with the simple module name "ecstasy" to create a
 * qualified module name of "ecstasy.xtclang.org".
 */
public class ModuleConstant
        extends IdentityConstant {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a module identifier.
     *
     * @param pool     the ConstantPool that will contain this Constant
     * @param sName    the qualified module name
     */
    public ModuleConstant(ConstantPool pool, String sName) {
        this(pool, sName, null);
    }

    /**
     * Construct a constant whose value is a module identifier.
     *
     * @param pool     the ConstantPool that will contain this Constant
     * @param sName    the qualified module name
     * @param version  the module version
     */
    public ModuleConstant(ConstantPool pool, String sName, Version version) {
        super(pool);

        m_constName    = pool.ensureStringConstant(sName);
        m_constVersion = version == null ? null : pool.ensureVersionConstant(version);
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
    public ModuleConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException {
        super(pool);

        m_iName    = readMagnitude(in);
        m_iVersion = readIndex(in);
    }

    @Override
    protected void resolveConstants() {
        ConstantPool pool = getConstantPool();

        m_constName    = (StringConstant)  pool.getConstant(m_iName);
        m_constVersion = (VersionConstant) pool.getConstant(m_iVersion);
    }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Extract the unqualified name of the module.
     *
     * @return the unqualified module name
     */
    public String getUnqualifiedName() {
        String sName = getName();
        int ofDot = sName.indexOf('.');
        return ofDot < 0 ? sName : sName.substring(0, ofDot);
    }

    /**
     * Get the domain name for the Module constant.
     *
     * @return the constant's domain information as a {@code String}, or {@code null} if the module
     *         name is not qualified (i.e. does not contain a domain name)
     */
    public String getDomainName() {
        String sName = getName();
        int ofDot = sName.indexOf('.');
        return ofDot < 0 ? null : sName.substring(ofDot + 1);
    }

    /**
     * Extract the module version.
     *
     * @return the unqualified module name
     */
    public Version getVersion() {
        VersionConstant constVersion = m_constVersion;
        return constVersion == null ? null : constVersion.getVersion();
    }

    /**
     * Determine if this ModuleConstant is the Ecstasy core module.
     *
     * @return true iff this ModuleConstant represents the module containing the Ecstasy class
     *         library
     */
    public boolean isEcstasyModule() {
        return getName().equals(ECSTASY_MODULE);
    }

    /**
     * @return true iff this ModuleConstant represents the module containing the Ecstasy class
     *         library or a native (prototype) module
     */
    public boolean isCoreModule() {
        String sName = getName();
        return sName.equals(ECSTASY_MODULE)
            || sName.equals(TURTLE_MODULE)
            || sName.equals(NATIVE_MODULE);
    }


    // ----- IdentityConstant methods --------------------------------------------------------------

    @Override
    public IdentityConstant getParentConstant() {
        return null;
    }

    @Override
    public IdentityConstant replaceParentConstant(IdentityConstant idParent) {
        return this;
    }

    /**
     * Get the qualified name of the Module.
     * <p/>
     * The qualified name for the module is constructed by combining the unqualified module name, a
     * separating '.', and the domain name.
     *
     * @return the qualified Module name
     */
    @Override
    public String getName() {
        return m_constName.getValue();
    }

    @Override
    public ModuleConstant getModuleConstant() {
        return this;
    }

    @Override
    public List<IdentityConstant> getPath() {
        List<IdentityConstant> list = new ArrayList<>();
        list.add(this);
        return list;
    }

    @Override
    public Component getComponent() {
        ModuleStructure struct = getFileStructure().getModule(this);
        if (struct == null) {
            return null;
        }

        ModuleStructure structOrigin = struct.getFingerprintOrigin();
        return structOrigin == null
                ? struct
                : structOrigin;
    }

    @Override
    public String getPathString() {
        return "";
    }

    @Override
    protected StringBuilder buildPath() {
        return new StringBuilder();
    }

    @Override
    public boolean trailingPathEquals(IdentityConstant that, int cSegments) {
        return trailingSegmentEquals(that);
    }

    @Override
    public IdentityConstant appendTrailingSegmentTo(IdentityConstant that) {
        throw new IllegalStateException(this.toString());
    }

    @Override
    public String getNestedName() {
        return null;
    }


    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public String getJitName(TypeSystem ts) {
        return Builder.MODULE;
    }

    @Override
    protected StringBuilder buildJitName(TypeSystem ts) {
        return new StringBuilder();
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat() {
        return Format.Module;
    }

    @Override
    public boolean isClass() {
        return true;
    }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor) {
        visitor.accept(m_constName);
    }

    @Override
    protected int compareDetails(Constant that) {
        if (!(that instanceof ModuleConstant idThat)) {
            return -1;
        }

        int n = this.m_constName.compareTo(idThat.m_constName);
        if (n != 0) {
            return n;
        }

        return this.m_constVersion == null
            ? idThat.m_constVersion == null
                ? 0
                : -1
            : idThat.m_constVersion == null
                ? 1
                : this.m_constVersion.compareDetails(idThat.m_constVersion);
    }

    @Override
    public String getValueString() {
        StringBuilder sb = new StringBuilder();
        sb.append(m_constName.getValue());
        if (m_constVersion != null) {
            sb.append(" v:")
              .append(m_constVersion.getVersion());
        }
        return sb.toString();
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool) {
        m_constName    = (StringConstant)  pool.register(m_constName);
        m_constVersion = (VersionConstant) pool.register(m_constVersion);
    }

    @Override
    protected void assemble(DataOutput out)
            throws IOException {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constName.getPosition());
        writePackedLong(out, Constant.indexOf(m_constVersion));
    }

    @Override
    public String getDescription() {
        return "module=" + getValueString();
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode() {
        return Hash.of(m_constName,
               Hash.of(m_constVersion));
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that specifies the name.
     */
    private int m_iName;

    /**
     * During disassembly, this holds the index of the constant that specifies the version.
     */
    private int m_iVersion;

    /**
     * The constant that holds the qualified name of the module.
     */
    private StringConstant m_constName;

    /**
     * The constant that holds the version of the module (optional).
     */
    private VersionConstant m_constVersion;
}
