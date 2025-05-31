package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.VersionConstant;

import org.xvm.util.Handy;
import org.xvm.util.ListMap;

import static org.xvm.util.Handy.readIndex;
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
    protected ModuleStructure(XvmStructure xsParent, int nFlags, ModuleConstant constId,
                              ConditionalConstant condition)
        {
        super(xsParent, nFlags, constId, condition);

        // when the main module is created in the FileStructure, the name has not yet been
        // configured, so if this is being created and the file already has a main module name, then
        // this module is being created to act as a fingerprint
        ModuleConstant idPrimary = xsParent.getFileStructure().getModuleId();
        if (idPrimary != null && !idPrimary.equals(constId))
            {
            m_moduletype           = ModuleType.Optional;
            m_vtreeImportAllowVers = new VersionTree<>();
            m_listImportPreferVers = new ArrayList<>();
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
        return m_moduletype;
        }

    /**
     * @return true iff this is the main module
     */
    public boolean isMainModule()
        {
        return m_moduletype == ModuleType.Primary &&
                getIdentityConstant().equals(getFileStructure().getModuleId());
        }

    /**
     * Check if this is a fingerprint module, which is a secondary (not main) module in a file
     * structure that represents the set of external dependencies on a particular imported module
     * from the main module and any embedded modules.
     *
     * @return true iff this module represents the "fingerprint" of an external module dependency
     */
    public boolean isFingerprint()
        {
        return switch (m_moduletype)
            {
            case Optional, Desired, Required -> true;
            case Primary, Embedded           -> false;
            };
        }

    /**
     * @return a VersionTree that provides a catalog of all versions of this module that are present
     */
    public VersionTree getVersions()
        {
        VersionTree tree = new VersionTree();
        if (isRefined())
            {
            Version ver = getVersion();
            if (ver != null)
                {
                tree.put(ver, null);
                }
            }
        else
            {
            // TODO GG
            if (isMainModule())
                {
                tree.putAll(getFileStructure().getVersionTree());
                }
            }
        return tree;
        }

    /**
     * @return the Version of this module, or null if there is no version (or more than one version)
     */
    public Version getVersion()
        {
        VersionConstant constant = getVersionConstant();
        return constant == null ? null : constant.getVersion();
        }

    /**
     * Obtain all the _conditional_ names that this module is aware of, and whether the names are
     * defined (if that is known). The keys are the conditional names. For each name, the value is
     * `null` to indicate that the condition is unknown (no choice has been made), `FALSE` to
     * indicate that the name was explicitly NOT defined, and `TRUE` to indicate that the name was
     * explicitly defined.
     *
     * @return a Map containing the conditional names known within this module
     */
    public Map<String, Boolean> getConditionalNames()
        {
        // TODO GG
        return Collections.emptyMap();
        }

    /**
     * Obtain all dependencies from this module.
     *
     * Because the result from this method can differ from version to version, and based on
     * conditional names, the module version and the conditional names must be refined before
     * using this method.
     *
     * @return a Map keyed by the ModuleConstant of each dependency, with a corresponding value of
     *         {@link ModuleType#Required}, {@link ModuleType#Desired}, or {@link ModuleType#Optional}
     */
    public Map<ModuleConstant, ModuleType> getDependencyTypes()
        {
        if (!isRefined())
            {
            if (getVersions().size() > 1)
                {
                throw new IllegalStateException("module contains unrefined versions");
                }

            if (getConditionalNames().containsValue(null))
                {
                throw new IllegalStateException("module contains unrefined conditional names");
                }
            }

        // TODO GG
        return Collections.emptyMap();
        }

    /**
     * @return a Map keyed by the ModuleConstant of each dependency, with a corresponding value of
     *         {@link Boolean#FALSE} for omitted optional and desired dependencies,
     *         {@link Boolean#TRUE} for required dependencies and for optional and desired
     *         dependencies that a decision was made to fill, and `null` for dependencies that no
     *         decision has been made yet regarding omitting or filling the dependency
     */
    public Map<ModuleConstant, Boolean> getDependencies()
        {
        Map<ModuleConstant, Boolean> map = new ListMap<>();
        for (Iterator<Map.Entry<ModuleConstant, ModuleType>> iter = getDependencyTypes().entrySet().iterator();
                iter.hasNext(); )
            {
            var entry = iter.next();
            // TODO GG
            map.put(entry.getKey(), entry.getValue() == ModuleType.Required ? Boolean.TRUE : Boolean.FALSE);
            }
        return map;
        }

    /**
     * Produce a strong, repeatable hash of the module. This value is intended to show that two
     * modules with the same name, version, etc. are truly the same by hashing every constituent  or are not the same.
     *
     * @return a digest value that acts as strong hash of the module
     */
    public byte[] getDigest()
        {
        // TODO GG
        return (getName() + '/' + getVersions().toString() + '/' + getConditionalNames().toString()
                + '/' + getDependencies().toString()).getBytes();
        }

    /**
     * @return `true` iff the module is real (not just a fingerprint) and all of its version,
     *         optional dependencies, and conditional names have been decided and resolved
     */
    public boolean isRefined()
        {
        if (isFingerprint())
            {
            return false;
            }

        if (isLinked())
            {
            return true;
            }

        // TODO GG
        return true;
        }

    /**
     * @return true iff this module is already fully loaded and linked
     */
    public boolean isLinked()
        {
        return !isFingerprint() || getFingerprintOrigin() != null;
        }

    /**
     * Determine the directory path that contains the module. The path is in the format of the
     * machine that compiled the module; in other words, it might look like "c:\dev\prj\", or it
     * might look like "/development/prj/", or any other valid format.
     *
     * @return the literal constant indicating the source directory, or null
     */
    public LiteralConstant getSourceDir()
        {
        return m_constDir;
        }

    /**
     * Specify the source directory.
     *
     * @param constDir  the literal constant indicating the source directory, or null
     */
    public void setSourceDir(LiteralConstant constDir)
        {
        m_constDir = constDir;
        markModified();
        }

    /**
     * Determine the date/time that the module was created.
     *
     * @return the literal constant indicating the compilation timestamp, or null
     */
    public LiteralConstant getTimestamp()
        {
        return m_constTimestamp;
        }

    /**
     * Specify the date/time that the module was created.
     *
     * @param constTimestamp  the literal constant indicating the compile date/time, or null
     */
    public void setTimestamp(LiteralConstant constTimestamp)
        {
        m_constTimestamp = constTimestamp;
        markModified();
        }

    /**
     * @return the VersionConstant that holds the version of this module, or null
     */
    public VersionConstant getVersionConstant()
        {
        assert !isFingerprint();
        return m_constVersion;
        }

    /**
     * Set the module version.
     *
     * @param version  the version constant
     */
    public void setVersion(Version version)
        {
        assert !isFingerprint();
        markModified();
        m_constVersion = getConstantPool().ensureVersionConstant(version);
        }

    /**
     * @return the version of this module formatted as a String, or null
     */
    public String getVersionString()
        {
        Version ver = getVersion();
        return ver == null ? null : ver.toString();
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
        return m_vtreeImportAllowVers;
        }

    /**
     * Update the list of preferred versions.
     *
     * @param vtreeAllow  the version tree of versions to allow (true) and avoid (false)
     */
    public void setFingerprintVersions(VersionTree<Boolean> vtreeAllow)
        {
        assert isFingerprint();
        m_vtreeImportAllowVers.clear();
        m_vtreeImportAllowVers.putAll(vtreeAllow);
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
        List<Version> list = m_listImportPreferVers;
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
        m_listImportPreferVers.clear();
        m_listImportPreferVers.addAll(listPrefer);
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
        if (m_moduletype == ModuleType.Optional)
            {
            m_moduletype = ModuleType.Desired;
            markModified();
            }
        }

    /**
     * Indicate that the fingerprint is required.
     */
    public void fingerprintRequired()
        {
        assert isFingerprint();
        if (m_moduletype == ModuleType.Optional || m_moduletype == ModuleType.Desired)
            {
            m_moduletype = ModuleType.Required;
            markModified();
            }
        }

    /**
     * @return true iff this module is an embedded module that exists to fulfill a dependency
     *         requirement of the main module
     */
    public boolean isEmbeddedModule()
        {
        assert (m_moduletype == ModuleType.Embedded) == (!isMainModule() && !isFingerprint());
        return m_moduletype == ModuleType.Embedded;
        }

    /**
     * Specify the ModuleStructure that corresponds to the fingerprint.
     *
     * @param moduleActual  the actual ModuleStructure that the fingerprint is based on
     */
    public void setFingerprintOrigin(ModuleStructure moduleActual)
        {
        assert isFingerprint();
        m_moduleActual = moduleActual;
        }

    /**
     * Obtain the ModuleStructure that corresponds to the fingerprint.
     *
     * @return the actual ModuleStructure that the fingerprint is based on
     */
    public ModuleStructure getFingerprintOrigin()
        {
        return m_moduleActual;
        }

    /**
     * @return a map containing the module dependencies, and the shortest path to each
     */
    public Map<ModuleConstant, String> collectDependencies()
        {
        Map<ModuleConstant, String> mapModulePaths = new HashMap<>();
        mapModulePaths.put(getIdentityConstant(), "");
        collectDependencies("", mapModulePaths);
        return mapModulePaths;
        }

    /**
     * Create (if necessary) a synthetic ClassStructure for the specified name.
     *
     * This method is currently used to create a synthetic interface that represents a union
     * of two types.
     *
     * @return a synthetic ClassStructure
     */
    public ClassStructure ensureSyntheticInterface(String sName)
        {
        ClassStructure clzInterface = (ClassStructure) getChild(sName);
        if (clzInterface == null)
            {
            clzInterface = createClass(Access.PUBLIC, Component.Format.INTERFACE, sName, null);
            clzInterface.setSynthetic(true);
            }
        return clzInterface;
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

        return m_moduleActual == null
                ? super.resolveName(sName, access, collector)
                : m_moduleActual.resolveName(sName, access, collector);
        }

    @Override
    protected ModuleStructure cloneBody()
        {
        ModuleStructure that = (ModuleStructure) super.cloneBody();

        if (this.m_vtreeImportAllowVers != null)
            {
            that.m_vtreeImportAllowVers = new VersionTree<>();
            that.m_vtreeImportAllowVers.putAll(this.m_vtreeImportAllowVers);
            }

        if (this.m_listImportPreferVers != null)
            {
            that.m_listImportPreferVers = new ArrayList<>();
            that.m_listImportPreferVers.addAll(this.m_listImportPreferVers);
            }

        return that;
        }

    @Override
    public PackageStructure getImportedPackage(ModuleConstant idMainModule)
        {
        PackageStructure pkg = m_pkgImport;
        if (pkg == null)
            {
            ModuleStructure moduleMain = (ModuleStructure) idMainModule.getComponent();

            String sPath = moduleMain.collectDependencies().get(getIdentityConstant());
            assert sPath != null;

            pkg = m_pkgImport = (PackageStructure) moduleMain.getChildByPath(sPath);

            assert pkg.isModuleImport();
            }
        return pkg;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        super.disassemble(in);

        m_moduletype = ModuleType.valueOf(in.readUnsignedByte());

        ConstantPool pool = getConstantPool();
        if (isFingerprint())
            {
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

            m_vtreeImportAllowVers = vtreeAllow;
            m_listImportPreferVers = listPrefer;
            }
        else
            {
            if (in.readBoolean())
                {
                m_constVersion = (VersionConstant) pool.getConstant(readMagnitude(in));
                }
            }

        m_constDir       = (LiteralConstant) getConstantPool().getConstant(readIndex(in));
        m_constTimestamp = (LiteralConstant) getConstantPool().getConstant(readIndex(in));
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        if (isFingerprint())
            {
            for (Version ver : m_vtreeImportAllowVers)
                {
                pool.ensureVersionConstant(ver);
                }

            for (Version ver : m_listImportPreferVers)
                {
                pool.ensureVersionConstant(ver);
                }
            }
        else if (m_constVersion != null)
            {
            m_constVersion = (VersionConstant) pool.register(m_constVersion);
            }

        m_constDir       = (LiteralConstant) pool.register(m_constDir);
        m_constTimestamp = (LiteralConstant) pool.register(m_constTimestamp);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);

        out.writeByte(m_moduletype.ordinal());

        ConstantPool pool = getConstantPool();

        if (isFingerprint())
            {
            VersionTree<Boolean> vtreeAllow = m_vtreeImportAllowVers;
            writePackedLong(out, vtreeAllow.size());
            for (Version ver : vtreeAllow)
                {
                writePackedLong(out, pool.ensureVersionConstant(ver).getPosition());
                out.writeBoolean(vtreeAllow.get(ver));
                }

            List<Version> listPrefer = m_listImportPreferVers;
            writePackedLong(out, listPrefer.size());
            for (Version ver : listPrefer)
                {
                writePackedLong(out, pool.ensureVersionConstant(ver).getPosition());
                }
            }
        else
            {
            if (m_constVersion == null)
                {
                out.writeBoolean(false);
                }
            else
                {
                out.writeBoolean(true);
                writePackedLong(out, m_constVersion.getPosition());
                }
            }

        writePackedLong(out, Constant.indexOf(m_constDir));
        writePackedLong(out, Constant.indexOf(m_constTimestamp));
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getDescription());

        sb.append(", module type=")
          .append(m_moduletype);

        if (isFingerprint())
            {
            sb.append(", fingerprint=true");

            VersionTree<Boolean> vtreeAllow = m_vtreeImportAllowVers;
            List<Version>        listPrefer = m_listImportPreferVers;
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
        else
            {
            if (m_constVersion != null)
                {
                sb.append(", version={")
                  .append(m_constVersion.getVersion())
                  .append('}');
                }
            }

        sb.append(", source-dir=")
          .append(m_constDir == null ? "none" : m_constDir.getValueString())
          .append(", timestamp=")
          .append(m_constDir == null ? "none" : m_constTimestamp.getValueString());

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

        if (!(obj instanceof ModuleStructure that && super.equals(obj)))
            {
            return false;
            }

        // compare versions
        return this.m_moduletype == that.m_moduletype
                && Handy.equals(this.m_vtreeImportAllowVers, that.m_vtreeImportAllowVers)
                && Handy.equals(this.m_listImportPreferVers, that.m_listImportPreferVers)
                && Handy.equals(this.m_constVersion,         that.m_constVersion)
                && Handy.equals(this.m_constDir, that.m_constDir);
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
     *     is not required, the linker should make the best effort to obtain and link in the
     *     module;</li>
     * <li>Required indicates that the primary module can not be loaded unless the fingerprint
     *     module is obtained and linked in by the linker.</li>
     * </ul>
     */
    public enum ModuleType
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
     * The directory path that contains the module, or null. The path is in the format of the
     * machine that compiled the module; in other words, it might look like "c:\dev\prj\", or it
     * might look like "/development/prj/", or any other valid format.
     */
    private LiteralConstant m_constDir;

    /**
     * The date/time that the module was created, or null.
     */
    private LiteralConstant m_constTimestamp;

    /**
     * Module type.
     */
    private ModuleType m_moduletype = ModuleType.Primary;

    /**
     * Module version; always null for fingerprints.
     */
    private VersionConstant m_constVersion;

    /**
     * If this is a fingerprint, then this will be a non-null version tree (but potentially empty)
     * specifying which versions are allowed (via a TRUE value) and avoided (via a FALSE value).
     */
    private VersionTree<Boolean> m_vtreeImportAllowVers;

    /**
     * If this is a fingerprint, then this will be a non-null (but potentially empty) list of
     * versions that are specified as preferred, in their order of preference.
     */
    private List<Version> m_listImportPreferVers;

    /**
     * If this is a fingerprint, during compilation this will hold the actual module from which the
     * fingerprint is being created.
     */
    private transient ModuleStructure m_moduleActual;

    /**
     * If this is an imported module, this will hold the package from the main module that imports
     * this module.
     */
    private transient PackageStructure m_pkgImport;
    }