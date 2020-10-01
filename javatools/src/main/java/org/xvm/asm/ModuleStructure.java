package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.VersionConstant;

import org.xvm.util.Handy;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An XVM Structure that represents an entire Module.
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
        String sPrimary = xsParent.getFileStructure().getModuleName();
        if (sPrimary != null && !sPrimary.equals(constId.getName()))
            {
            moduletype           = ModuleType.Optional;
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
    public ModuleConstant getIdentityConstant()
        {
        return (ModuleConstant) super.getIdentityConstant();
        }

    /**
     * @return the {@link ModuleType} of this module structure
     */
    public ModuleType getModuleType()
        {
        return moduletype;
        }

    /**
     * @return true iff this is the main module
     */
    public boolean isMainModule()
        {
        assert (moduletype == ModuleType.Primary) == getName().equals(getFileStructure().getModuleName());
        return moduletype == ModuleType.Primary;
        }

    /**
     * @return true iff this module represents the "fingerprint" of an external module dependency
     */
    public boolean isFingerprint()
        {
        switch (moduletype)
            {
            case Optional:
            case Desired:
            case Required:
                return true;

            case Primary:
            case Embedded:
                return false;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Determine which versions this fingerprint module allows and disallows.
     *
     * @return a read-only VersionTree that indicates which versions are allowed (true) and avoided
     *         (false)
     */
    public VersionTree<Boolean> getFingerprintVersions()
        {
        assert isFingerprint();
        return vtreeImportAllowVers;
        }

    /**
     * Update the list of preferred versions.
     *
     * @param vtreeAllow  the version tree of versions to allow (true) and avoid (false)
     */
    public void setFingerprintVersions(VersionTree<Boolean> vtreeAllow)
        {
        assert isFingerprint();
        vtreeImportAllowVers.clear();
        vtreeImportAllowVers.putAll(vtreeAllow);
        markModified();
        }

    /**
     * Determine which versions are desired.
     *
     * @return a read-only list of versions desired
     */
    public List<Version> getFingerprintVersionPrefs()
        {
        assert isFingerprint();

        List<Version> list = listImportPreferVers;
        assert (list = Collections.unmodifiableList(list)) != null;
        return list;
        }

    /**
     * Update the list of preferred versions.
     *
     * @param listPrefer  the list of preferred versions to use
     */
    public void setFingerprintVersionPrefs(List<Version> listPrefer)
        {
        assert isFingerprint();
        listImportPreferVers.clear();
        listImportPreferVers.addAll(listPrefer);
        markModified();
        }

    /**
     * Indicate that the fingerprint is optional.
     */
    public void fingerprintOptional()
        {
        assert isFingerprint();
        }

    /**
     * Indicate that the fingerprint is desired.
     */
    public void fingerprintDesired()
        {
        assert isFingerprint();
        if (moduletype == ModuleType.Optional)
            {
            moduletype = ModuleType.Desired;
            markModified();
            }
        }

    /**
     * Indicate that the fingerprint is required.
     */
    public void fingerprintRequired()
        {
        assert isFingerprint();
        if (moduletype == ModuleType.Optional || moduletype == ModuleType.Desired)
            {
            moduletype = ModuleType.Required;
            markModified();
            }
        }

    /**
     * @return true iff this module is an embedded module that exists to fulfill a dependency
     *         requirement of the main module
     */
    public boolean isEmbeddedModule()
        {
        assert (moduletype == ModuleType.Embedded) == (!isMainModule() && !isFingerprint());
        return moduletype == ModuleType.Embedded;
        }

    /**
     * Specify the ModuleStructure that corresponds to the fingerprint.
     *
     * @param structModule  the actual ModuleStructure that the fingerprint is based on
     */
    public void setFingerprintOrigin(ModuleStructure structModule)
        {
        assert isFingerprint();
        moduleActual = structModule;
        }

    /**
     * Obtain the ModuleStructure that corresponds to the fingerprint.
     *
     * @return the actual ModuleStructure that the fingerprint is based on
     */
    public ModuleStructure getFingerprintOrigin()
        {
        return moduleActual;
        }

    /**
     * Build a list of all of the module dependencies, and the shortest path to each.
     *
     * @return  a map containing all of the module dependencies, and the shortest path to each
     */
    public Map<ModuleConstant, String> collectDependencies()
        {
        Map<ModuleConstant, String> mapModulePaths = new HashMap<>();
        mapModulePaths.put(getIdentityConstant(), "");
        collectDependencies("", mapModulePaths);
        return mapModulePaths;
        }


    // ----- Component methods ---------------------------------------------------------------------

    @Override
    public String getName()
        {
        return getIdentityConstant().getName();
        }

    @Override
    public String getSimpleName()
        {
        return getIdentityConstant().getUnqualifiedName();
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

    @Override
    public ResolutionResult resolveName(String sName, Access access, ResolutionCollector collector)
        {
        if (sName.equals(getSimpleName()))
            {
            collector.resolvedComponent(this);
            return ResolutionResult.RESOLVED;
            }

        return  moduleActual == null
                ? super.resolveName(sName, access, collector)
                : moduleActual.resolveName(sName, access, collector);
        }

    @Override
    protected ModuleStructure cloneBody()
        {
        ModuleStructure that = (ModuleStructure) super.cloneBody();

        if (this.vtreeImportAllowVers != null)
            {
            that.vtreeImportAllowVers = new VersionTree<>();
            that.vtreeImportAllowVers.putAll(this.vtreeImportAllowVers);
            }

        if (this.listImportPreferVers != null)
            {
            that.listImportPreferVers = new ArrayList<>();
            that.listImportPreferVers.addAll(this.listImportPreferVers);
            }

        return that;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        super.disassemble(in);

        moduletype = ModuleType.valueOf(in.readUnsignedByte());

        if (isFingerprint())
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

            vtreeImportAllowVers = vtreeAllow;
            listImportPreferVers = listPrefer;
            }
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        if (isFingerprint())
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

        out.writeByte(moduletype.ordinal());

        if (isFingerprint())
            {
            ConstantPool pool = getConstantPool();

            VersionTree<Boolean> vtreeAllow = vtreeImportAllowVers;
            writePackedLong(out, vtreeAllow.size());
            for (Version ver : vtreeAllow)
                {
                writePackedLong(out, pool.ensureVersionConstant(ver).getPosition());
                out.writeBoolean(vtreeAllow.get(ver));
                }

            List<Version> listPrefer = listImportPreferVers;
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

        sb.append(", moduletype=")
                .append(moduletype);

        if (isFingerprint())
            {
            sb.append(", fingerprint=true");

            VersionTree<Boolean> vtreeAllow = vtreeImportAllowVers;
            List<Version>        listPrefer = listImportPreferVers;
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
        return this.moduletype == that.moduletype
                && Handy.equals(this.vtreeImportAllowVers, that.vtreeImportAllowVers)
                && Handy.equals(this.listImportPreferVers, that.listImportPreferVers);
        }


    // ----- ModuleType enumeration ----------------------------------------------------------------

    /**
     * A module serves one of three primary purposes:
     * <ul>
     * <li>The primary module is the module for which the FileStructure exists;</li>
     * <li>A fingerprint module represents an imported module;</li>
     * <li>An embedded module is an entire module that is embedded within the FileStructure in order
     *     to fully satisfy the dependencies of an import.</li>
     * </ul>
     * <p/>
     * A fingerprint module has three levels that indicate how desired or required it is:
     * <ul>
     * <li>Optional indicates that the dependency is supported, but leaves the decision regarding
     *     whether or not to import the module to the linker;</li>
     * <li>Desired also indicates that the dependency is supported, but even though the dependency
     *     is not required, the linker should make a best effort to obtain and link in the
     *     module;</li>
     * <li>Required indicates that the primary module can not be loaded unless the fingerprint
     *     module is obtained and linked in by the linker.</li>
     * </ul>
     */
    enum ModuleType
        {
        Primary, Optional, Desired, Required, Embedded;

        /**
         * Look up a Format enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the Format enum for the specified ordinal
         */
        public static ModuleType valueOf(int i)
            {
            return MODULE_TYPES[i];
            }

        /**
         * All of the Format enums.
         */
        private static final ModuleType[] MODULE_TYPES = ModuleType.values();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * True iff this is a fingerprint module, which is a secondary (not main) module in a file
     * structure that represents the set of external dependencies on a particular imported module
     * from the main module and any embedded modules.
     */
    private ModuleType moduletype = ModuleType.Primary;

    /**
     * If this is a fingerprint, then this will be a non-null version tree (but potentially empty)
     * specifying which versions are allowed (via a TRUE value) and avoided (via a FALSE value).
     */
    private VersionTree<Boolean> vtreeImportAllowVers;

    /**
     * If this is a fingerprint, then this will be a non-null (but potentially empty) list of
     * versions that are specified as preferred, in their order of preference.
     */
    private List<Version> listImportPreferVers;

    /**
     * If this is a fingerprint, during compilation this will hold the actual module from which the
     * fingerprint is being created.
     */
    transient private ModuleStructure moduleActual;
    }
