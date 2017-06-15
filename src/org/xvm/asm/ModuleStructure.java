package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.VersionConstant;
import org.xvm.util.Handy;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An XVM Structure that represents an entire Module.
 *
 * @author cp 2016.04.14
 */
public class ModuleStructure
        extends ClassStructure
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ModuleStructure with the specified identity.
     *
     * @param xsParent  the XvmStructure (probably a FileStructure) that contains this structure
     * @param constId   the constant that specifies the identity of the Module
     */
    protected ModuleStructure(XvmStructure xsParent, ModuleConstant constId)
        {
        this(xsParent, (Format.MODULE.ordinal() << FORMAT_SHIFT) | ACCESS_PUBLIC | STATIC_BIT, constId, null);
        }

    /**
     * Construct a ModuleStructure with the specified identity.
     *
     * @param xsParent   the XvmStructure (probably a FileStructure) that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Module
     * @param condition  the optional condition for this ModuleStructure
     */
    protected ModuleStructure(XvmStructure xsParent, int nFlags, ModuleConstant constId, ConditionalConstant condition)
        {
        super(xsParent, nFlags, constId, condition);

        // when the main module is created in the FileStructure, the name has not yet been
        // configured, so if this is being created and the file already has a main module name, then
        // this module is being created to act as a fingerprint
        if (xsParent.getFileStructure().getModuleName() != null)
            {
            fFingerprint         = true;
            vtreeImportAllowVers = new VersionTree<>();
            listImportPreferVers = new ArrayList<>();
            }
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Obtain the ModuleConstant that holds the identity of this Module.
     *
     * @return the ModuleConstant representing the identity of this
     *         ModuleStructure
     */
    public ModuleConstant getModuleConstant()
        {
        return (ModuleConstant) getIdentityConstant();
        }

    /**
     * @return true iff this is the main module
     */
    public boolean isMainModule()
        {
        return getName().equals(getFileStructure().getModuleName());
        }

    /**
     * @return true iff this module represents the "fingerprint" of an external module dependency
     */
    public boolean isFingerprint()
        {
        return fFingerprint;
        }

    /**
     * Determine which versions this fingerprint module allows and disallows.
     *
     * @return
     */
    public VersionTree<Boolean> getFingerprintVersions()
        {
        assert isFingerprint();
        return vtreeImportAllowVers;
        }

    public List<Version> getFingerprintVersionPrefs()
        {
        assert isFingerprint();
        return listImportPreferVers;
        }

    /**
     * @return true iff this module is an embedded module that exists to fulfill a dependency
     *         requirement of the main module
     */
    public boolean isEmbeddedModule()
        {
        return !isMainModule() && !isFingerprint();
        }


    // ----- Component methods ---------------------------------------------------------------------

    @Override
    public String getName()
        {
        return getModuleConstant().getName();
        }

    @Override
    public boolean isGloballyVisible()
        {
        // modules are always public, and always "top level" visible
        return true;
        }

    @Override
    public boolean isPackageContainer()
        {
        return true;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        super.disassemble(in);

        // TODO
//        final int cVers = readMagnitude(in);
//        if (cVers > 0)
//            {
//            final SortedSet<VersionConstant> setVer = m_setVer;
//            final ConstantPool               pool   = getConstantPool();
//            for (int i = 0; i < cVers; ++i)
//                {
//                setVer.add((VersionConstant) pool.getConstant(readMagnitude(in)));
//                }
//            }
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        // TODO
//        final TreeSet<VersionConstant> setOld = m_setVer;
//        if (!setOld.isEmpty())
//            {
//            final TreeSet<VersionConstant> setNew = new TreeSet<>();
//
//            for (VersionConstant ver : setOld)
//                {
//                setNew.add((VersionConstant) pool.register(ver));
//                }
//
//            m_setVer = setNew;
//            }
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);

        // TODO
//        final TreeSet<VersionConstant> setVer = m_setVer;
//        writePackedLong(out, setVer.size());
//        for (VersionConstant ver : setVer)
//            {
//            writePackedLong(out, ver.getPosition());
//            }
        }

    @Override
    public String getDescription()
        {
        // TODO
//        final TreeSet<VersionConstant> setVer = m_setVer;

        StringBuilder sb = new StringBuilder();
        sb.append(super.getDescription())
          .append(", version=");

//        switch (setVer.size())
//            {
//            case 0:
//                sb.append("none");
//                break;
//            case 1:
//                sb.append(setVer.iterator().next().getValueString());
//                break;
//            default:
//                sb.append("multiple");
//                break;
//            }

        return sb.toString();
        }

    @Override
    protected void dump(PrintWriter out, String sIndent)
        {
        super.dump(out, sIndent);
//        dumpStructureCollection(out, sIndent, "Versions", m_setVer);
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof ModuleStructure && super.equals(obj)))
            {
            return false;
            }

        // compare versions
        ModuleStructure that = (ModuleStructure) obj;
        return this.fFingerprint == that.fFingerprint
                && Handy.equals(this.vtreeImportAllowVers, that.vtreeImportAllowVers)
                && Handy.equals(this.listImportPreferVers, that.listImportPreferVers);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * True iff this is a fingerprint module, which is a secondary (not main) module in a file
     * structure that represents the set of external dependencies on a particular imported module
     * from the main module and any embedded modules.
     */
    private boolean              fFingerprint;
    /**
     * If this is a fingerprint, then this will be a non-null version tree (but potentially empty)
     * specifying which versions are allowed (via a TRUE value) and avoided (via a FALSE value).
     */
    private VersionTree<Boolean> vtreeImportAllowVers;
    /**
     * If this is a fingerprint, then this will be a non-null (but potentially empty) list of
     * versions that are specified as preferred, in their order of preference.
     */
    private List<Version>        listImportPreferVers;
    }
