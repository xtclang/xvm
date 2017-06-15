package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

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

        if (in.readBoolean())
            {
            ConstantPool pool = getConstantPool();

            VersionTree<Boolean> vtreeAllow = new VersionTree<>();
            for (int i = 0, c = readMagnitude(in); i < c; ++i)
                {
                VersionConstant constVer = (VersionConstant) pool.getConstant(readMagnitude(in));
                vtreeAllow.put(constVer.getVersion(), in.readBoolean());
                }

            List<Version> listPrefer = new ArrayList<>();
            for (int i = 0, c = readMagnitude(in); i < c; ++i)
                {
                VersionConstant constVer = (VersionConstant) pool.getConstant(readMagnitude(in));
                Version         ver      = constVer.getVersion();
                if (!listPrefer.contains(ver))
                    {
                    listPrefer.add(ver);
                    }
                }

            fFingerprint         = true;
            vtreeImportAllowVers = vtreeAllow;
            listImportPreferVers = listPrefer;
            }
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        if (fFingerprint)
            {
            for (Version ver : vtreeImportAllowVers)
                {
                pool.register(pool.ensureVersionConstant(ver));
                }

            for (Version ver : listImportPreferVers)
                {
                pool.register(pool.ensureVersionConstant(ver));
                }
            }
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);

        out.writeBoolean(fFingerprint);
        if (fFingerprint)
            {
            final ConstantPool pool = getConstantPool();

            final VersionTree<Boolean> vtreeAllow = vtreeImportAllowVers;
            writePackedLong(out, vtreeAllow.size());
            for (Version ver : vtreeAllow)
                {
                writePackedLong(out, pool.ensureVersionConstant(ver).getPosition());
                out.writeBoolean(vtreeAllow.get(ver));
                }

            final List<Version> listPrefer = listImportPreferVers;
            writePackedLong(out, listPrefer.size());
            for (Version ver : listPrefer)
                {
                writePackedLong(out, pool.ensureVersionConstant(ver).getPosition());
                }
            }
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getDescription());

        if (fFingerprint)
            {
            sb.append(", fingerprint=true");

            final VersionTree<Boolean> vtreeAllow = vtreeImportAllowVers;
            final List<Version>        listPrefer = listImportPreferVers;
            if (!vtreeAllow.isEmpty() || !listPrefer.isEmpty())
                {
                sb.append(", version={");
                boolean fFirst = true;

                for (Version ver : vtreeAllow)
                    {
                    if (fFirst)
                        {
                        sb.append(", ");
                        fFirst = false;
                        }

                    sb.append(vtreeAllow.get(ver) ? "allow " : "avoid ")
                      .append(ver);
                    }

                for (Version ver : listPrefer)
                    {
                    if (fFirst)
                        {
                        sb.append(", ");
                        fFirst = false;
                        }

                    sb.append("prefer ")
                      .append(ver);
                    }

                sb.append('}');
                }
            }

        return sb.toString();
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
