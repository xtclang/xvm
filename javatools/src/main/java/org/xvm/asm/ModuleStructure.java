package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.security.DigestOutputStream;
import java.security.MessageDigest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.function.Consumer;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.NamedCondition;
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
        extends ClassStructure {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ModuleStructure with the specified identity.
     *
     * @param xsParent  the XvmStructure (probably a FileStructure) that contains this structure
     * @param constId   the constant that specifies the identity of the Module
     */
    protected ModuleStructure(XvmStructure xsParent, ModuleConstant constId) {
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
                              ConditionalConstant condition) {
        super(xsParent, nFlags, constId, condition);

        // when the main module is created in the FileStructure, the name has not yet been
        // configured, so if this is being created and the file already has a main module name, then
        // this module is being created to act as a fingerprint
        ModuleConstant idPrimary = xsParent.getFileStructure().getModuleId();
        if (idPrimary != null && !idPrimary.equals(constId)) {
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
    public ModuleConstant getIdentityConstant() {
        return (ModuleConstant) super.getIdentityConstant();
    }

    /**
     * @return the {@link ModuleType} of this module structure
     */
    public ModuleType getModuleType() {
        return m_moduletype;
    }

    /**
     * @return true iff this is the main module
     */
    public boolean isMainModule() {
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
    public boolean isFingerprint() {
        return switch (m_moduletype) {
            case Optional, Desired, Required -> true;
            case Primary, Embedded           -> false;
        };
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
    public Map<ModuleConstant, ModuleType> getDependencyTypes() {
        if (!isRefined()) {
            if (getVersions().size() > 1) {
                throw new IllegalStateException("module contains unrefined versions");
            }

            if (getConditionalNames().containsValue(null)) {
                throw new IllegalStateException("module contains unrefined conditional names");
            }
        }

        Map<ModuleConstant, ModuleType> mapModuleTypes = new HashMap<>();
        Consumer<Component> visitor = component -> {
            if (component instanceof PackageStructure pkg && pkg.isModuleImport()) {
                ModuleStructure module = pkg.getImportedModule();
                assert module != null;
                mapModuleTypes.put(module.getIdentityConstant(), module.getModuleType());
            }
        };
        visitChildren(visitor, true, true);
        return mapModuleTypes;
    }

    /**
     * @return a Map keyed by the ModuleConstant of each dependency, with a corresponding value of
     *         {@link Boolean#FALSE} for omitted optional and desired dependencies,
     *         {@link Boolean#TRUE} for required dependencies and for optional and desired
     *         dependencies that a decision was made to fill, and `null` for dependencies that no
     *         decision has been made yet regarding omitting or filling the dependency
     */
    public Map<ModuleConstant, Boolean> getDependencies() {
        Map<ModuleConstant, Boolean> mapDependencies = m_mapDependencies;
        if (mapDependencies == null) {
            mapDependencies = m_mapDependencies = collectModuleDependencies();
        }
        return mapDependencies;
    }

    /**
     * An implementation for {@link #getDependencies()}
     */
    protected Map<ModuleConstant, Boolean> collectModuleDependencies() {
        Map<ModuleConstant, Boolean> map = new ListMap<>();
        for (Map.Entry<ModuleConstant, ModuleType> entry : getDependencyTypes().entrySet()) {
            map.put(entry.getKey(),
                switch (entry.getValue()) {
                    case Required, Embedded -> Boolean.TRUE;
                    case Optional, Desired  -> null;
                    case Primary -> throw new IllegalStateException();
                });
        }
        return map;
    }

    /**
     * Produce a strong, repeatable hash of the module. This value is intended to show that two
     * modules with the same name, version, etc. are truly the same by hashing every constituent
     * or are not the same.
     *
     * @return a digest value that acts as strong hash of the module
     */
    public byte[] getDigest() {
        assert !isFingerprint();

        byte[] abDigest = m_abDigest;
        if (abDigest == null) {
            try {
                DigestOutputStream dos = new DigestOutputStream(OutputStream.nullOutputStream(),
                                            MessageDigest.getInstance("SHA-256"));
                DataOutputStream   out = new DataOutputStream(dos);
                assemble(out);
                assembleChildren(out);
                out.close();

                abDigest = m_abDigest = dos.getMessageDigest().digest();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return abDigest;
    }

    /**
     * @return `true` iff the module is real (not just a fingerprint) and all of its version,
     *         optional dependencies, and conditional names have been decided and resolved
     */
    public boolean isRefined() {
        if (isFingerprint()) {
            return false;
        }

        if (isLinked()) {
            return true;
        }

        // TODO GG
        return true;
    }

    /**
     * @return true iff this module is already fully loaded and linked
     */
    public boolean isLinked() {
        return !isFingerprint() || getFingerprintOrigin() != null;
    }

    /**
     * Determine the directory path that contains the module. The path is in the format of the
     * machine that compiled the module; in other words, it might look like "c:\dev\prj\", or it
     * might look like "/development/prj/", or any other valid format.
     *
     * @return the literal constant indicating the source directory, or null
     */
    public LiteralConstant getSourceDir() {
        return m_constDir;
    }

    /**
     * Specify the source directory.
     *
     * @param constDir  the literal constant indicating the source directory, or null
     */
    public void setSourceDir(LiteralConstant constDir) {
        m_constDir = constDir;
        markModified();
    }

    /**
     * Determine the date/time that the module was created.
     *
     * @return the literal constant indicating the compilation timestamp, or null
     */
    public LiteralConstant getTimestamp() {
        return m_constTimestamp;
    }

    /**
     * Specify the date/time that the module was created.
     *
     * @param constTimestamp  the literal constant indicating the compile date/time, or null
     */
    public void setTimestamp(LiteralConstant constTimestamp) {
        m_constTimestamp = constTimestamp;
        markModified();
    }

    /**
     * @return the VersionConstant that holds the version of this module, or null
     */
    public VersionConstant getVersionConstant() {
        assert !isFingerprint();
        return m_constVersion;
    }

    /**
     * Set the module version.
     *
     * @param version  the version constant
     */
    public void setVersion(Version version) {
        assert !isFingerprint();
        markModified();
        m_constVersion = getConstantPool().ensureVersionConstant(version);
    }

    /**
     * @return the version of this module formatted as a String, or null
     */
    public String getVersionString() {
        Version ver = getVersion();
        return ver == null ? null : ver.toString();
    }

    /**
     * Determine which versions this fingerprint module allows and disallows.
     *
     * @return a read-only VersionTree that indicates which versions are allowed (true) and avoided
     *         (false)
     */
    public VersionTree<Boolean> getFingerprintVersions() {
        assert isFingerprint();
        return m_vtreeImportAllowVers;
    }

    /**
     * Update the list of preferred versions.
     *
     * @param vtreeAllow  the version tree of versions to allow (true) and avoid (false)
     */
    public void setFingerprintVersions(VersionTree<Boolean> vtreeAllow) {
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
    public List<Version> getFingerprintVersionPrefs() {
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
    public void setFingerprintVersionPrefs(List<Version> listPrefer) {
        assert isFingerprint();
        m_listImportPreferVers.clear();
        m_listImportPreferVers.addAll(listPrefer);
        markModified();
    }

    /**
     * Indicate that the fingerprint is optional.
     */
    public void fingerprintOptional() {
        assert isFingerprint();
    }

    /**
     * Indicate that the fingerprint is desired.
     */
    public void fingerprintDesired() {
        assert isFingerprint();
        if (m_moduletype == ModuleType.Optional) {
            m_moduletype = ModuleType.Desired;
            markModified();
        }
    }

    /**
     * Indicate that the fingerprint is required.
     */
    public void fingerprintRequired() {
        assert isFingerprint();
        if (m_moduletype == ModuleType.Optional || m_moduletype == ModuleType.Desired) {
            m_moduletype = ModuleType.Required;
            markModified();
        }
    }

    /**
     * @return true iff this module is an embedded module that exists to fulfill a dependency
     *         requirement of the main module
     */
    public boolean isEmbeddedModule() {
        assert (m_moduletype == ModuleType.Embedded) == (!isMainModule() && !isFingerprint());
        return m_moduletype == ModuleType.Embedded;
    }

    /**
     * Specify the ModuleStructure that corresponds to the fingerprint.
     *
     * @param moduleActual  the actual ModuleStructure that the fingerprint is based on
     */
    public void setFingerprintOrigin(ModuleStructure moduleActual) {
        assert isFingerprint();
        m_moduleActual = moduleActual;
    }

    /**
     * Obtain the ModuleStructure that corresponds to the fingerprint.
     *
     * @return the actual ModuleStructure that the fingerprint is based on
     */
    public ModuleStructure getFingerprintOrigin() {
        return m_moduleActual;
    }

    /**
     * @return a map containing the module dependencies, and the shortest path to each
     */
    public Map<ModuleConstant, String> collectDependencies() {
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
    public ClassStructure ensureSyntheticInterface(String sName) {
        ClassStructure clzInterface = (ClassStructure) getChild(sName);
        if (clzInterface == null) {
            clzInterface = createClass(Access.PUBLIC, Format.INTERFACE, sName, null);
            clzInterface.setSynthetic(true);
        }
        return clzInterface;
    }


    // ----- Version management --------------------------------------------------------------------

    /**
     * @return a VersionTree that provides a catalog of all versions of this module that are present
     */
    public VersionTree<Boolean> getVersions() {
        VersionTree vtree = m_vtree;
        if (vtree == null) {
            collectVersions(m_vtree = vtree = new VersionTree());
        }
        return vtree;
    }

    /**
     * An implementation for {@link #getVersions()}
     */
    protected void collectVersions(VersionTree vtree) {
        ModuleStructure module = this;
        do {
            Version version = module.getVersion();
            if (version != null) {
                vtree.put(version, Boolean.TRUE);
            }
            module = (ModuleStructure) module.getNextSibling();
        } while (module != null);
    }

    /**
     * Determine if this module version contains the specified version.
     *
     * @param ver  a version number
     *
     * @return true iff the specified version label is present
     */
    public boolean containsVersion(Version ver) {
        return getVersions().contains(ver);
    }

    /**
     * Obtain the Version of this module. Do not use this method with a ModuleStructure for a
     * fingerprint module, or an actual module that contains more than one version.
     *
     * @return the Version of this module, or null if there is no version (or more than one version)
     */
    public Version getVersion() {
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
     * Note: once the map is collected, it will be retained and allowed to be mutated by the
     *       "refining" process.
     *
     * @return a Map containing the conditional names known within this module
     */
    public Map<String, Boolean> getConditionalNames() {
        Map<String, Boolean> mapCondNames = m_mapCondNames;
        if (mapCondNames == null) {
            mapCondNames = m_mapCondNames = collectConditionalNames();
        }
        return mapCondNames;
    }

    /**
     * An implementation for {@link #getConditionalNames()}
     */
    protected Map<String, Boolean> collectConditionalNames() {
        Map<String, Boolean> mapConditions = new HashMap<>();
        Consumer<Component> visitor = component -> {
            ConditionalConstant constCond = component.getCondition();
            if (constCond != null) {
                for (ConditionalConstant condTerminal : constCond.terminals()) {
                    if (condTerminal instanceof NamedCondition condName) {
                        mapConditions.put(condName.getName(), null);
                    }
                }
            }
        };
        visitor.accept(this);
        visitChildren(visitor, true, true);
        return mapConditions;
    }


    /**
     * Determine if the specified version is supported, either by that exact version, or by a
     * subsequent version.
     *
     * @param ver     a version number
     * @param fExact  true if the version has to match exactly
     *
     * @return true if this module supports the specified version
     */
    public boolean supportsVersion(Version ver, boolean fExact) {
        if (containsVersion(ver)) {
            return true;
        }

        if (!fExact) {
            return getVersions().findHighestVersion(ver) != null;
        }

        return false;
    }

    /**
     * Remove a version label from this ModuleStructure. If there are multiple versions within this
     * module, then only the specified version label is removed. If there is only one version
     * within this module, then the structure is left unchanged, but the version label is removed.
     *
     * @param ver  the version label to remove from this module structure
     */
    public void purgeVersion(Version ver) {
        if (ver == null) {
            throw new IllegalArgumentException("version required");
        }

        VersionTree<Boolean> vtree = getVersions();
        if (!vtree.contains(ver)) {
            return;
        }

        // this has to find just the components that have the specified version label, and
        // remove it from just those components, and then remove those components if they no
        // longer have any version label
        // TODO
        markModified();
    }

    public void purgeVersionsExcept(Version ver) {
        if (ver == null) {
            throw new IllegalArgumentException("version required");
        }

        VersionTree<Boolean> vtree = getVersions();
        ver = ver.normalize();
        if (!vtree.contains(ver)) {
            throw new IllegalArgumentException("version " + ver  + " does not exist in this module");
        }

        if (vtree.size() == 1) {
            // already done
            return;
        }

        // this has to go to every component, and remove every version except the specified version,
        // and then remove those components if they no longer have any version label
        // TODO

        markModified();
    }

    /**
     * Create a copy of this main module and replace all occurrences of this module's id
     * (versionless) with the specified (versioned) module id. This method is called when a
     * versioned module fingerprint needs to be bound to an actual module.
     *
     * @param version   the version to extract
     *
     * @return a "versioned" ModuleStructure
     */
    public ModuleStructure extractVersion(Version version) {
        assert isMainModule() && containsVersion(version);

        String          sName    = getName();
        ModuleConstant  idModule = getConstantPool().ensureModuleConstant(sName, version);

        FileStructure fileClone = new FileStructure(sName);
        fileClone.removeChild(fileClone.getModule());
        fileClone.merge(this, false, true);

        ConstantPool    pool        = fileClone.getConstantPool();
        ModuleStructure moduleClone = fileClone.replaceModuleId(idModule);
        moduleClone.registerConstants(pool);
        moduleClone.registerChildrenConstants(pool);

        moduleClone.purgeVersionsExcept(version);
        return moduleClone;
    }


    // ----- Component methods ---------------------------------------------------------------------

    @Override
    public String getName() {
        return getIdentityConstant().getName();
    }

    @Override
    public String getSimpleName() {
        return getIdentityConstant().getUnqualifiedName();
    }

    @Override
    public boolean isGloballyVisible() {
        // modules are always public, and always "top level" visible
        return true;
    }

    @Override
    public boolean isPackageContainer() {
        return true;
    }

    @Override
    public ResolutionResult resolveName(String sName, Access access, ResolutionCollector collector) {
        if (sName.equals(getSimpleName())) {
            collector.resolvedComponent(this);
            return ResolutionResult.RESOLVED;
        }

        return m_moduleActual == null
                ? super.resolveName(sName, access, collector)
                : m_moduleActual.resolveName(sName, access, collector);
    }

    @Override
    protected ModuleStructure cloneBody() {
        ModuleStructure that = (ModuleStructure) super.cloneBody();

        if (this.m_vtreeImportAllowVers != null) {
            that.m_vtreeImportAllowVers = new VersionTree<>();
            that.m_vtreeImportAllowVers.putAll(this.m_vtreeImportAllowVers);
        }

        if (this.m_listImportPreferVers != null) {
            that.m_listImportPreferVers = new ArrayList<>();
            that.m_listImportPreferVers.addAll(this.m_listImportPreferVers);
        }

        return that;
    }

    @Override
    public PackageStructure getImportedPackage(ModuleConstant idMainModule) {
        PackageStructure pkg = m_pkgImport;
        if (pkg == null) {
            ModuleStructure moduleMain = (ModuleStructure) idMainModule.getComponent();

            String sPath = moduleMain.collectDependencies().get(getIdentityConstant());
            assert sPath != null;

            pkg = m_pkgImport = (PackageStructure) moduleMain.getChildByPath(sPath);

            assert pkg.isModuleImport();
        }
        return pkg;
    }

    @Override
    protected void markModified() {
        super.markModified();

        m_abDigest = null;
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException {
        super.disassemble(in);

        m_moduletype = ModuleType.valueOf(in.readUnsignedByte());

        ConstantPool pool = getConstantPool();
        if (isFingerprint()) {
            VersionTree<Boolean> vtreeAllow = new VersionTree<>();
            for (int i = 0, c = readMagnitude(in); i < c; ++i) {
                VersionConstant constVer = pool.getConstant(readMagnitude(in), VersionConstant.class);
                vtreeAllow.put(constVer.getVersion(), in.readBoolean());
            }

            List<Version> listPrefer = new ArrayList<>();
            for (int i = 0, c = readMagnitude(in); i < c; ++i) {
                VersionConstant constVer = pool.getConstant(readMagnitude(in), VersionConstant.class);
                Version         ver      = constVer.getVersion();
                if (!listPrefer.contains(ver)) {
                    listPrefer.add(ver);
                }
            }

            m_vtreeImportAllowVers = vtreeAllow;
            m_listImportPreferVers = listPrefer;
        } else {
            if (in.readBoolean()) {
                m_constVersion = pool.getConstant(readMagnitude(in), VersionConstant.class);
            }
        }

        m_constDir       = pool.getConstant(readIndex(in), LiteralConstant.class);
        m_constTimestamp = pool.getConstant(readIndex(in), LiteralConstant.class);
    }

    @Override
    protected void registerConstants(ConstantPool pool) {
        super.registerConstants(pool);

        if (isFingerprint()) {
            for (Version ver : m_vtreeImportAllowVers) {
                pool.ensureVersionConstant(ver);
            }

            for (Version ver : m_listImportPreferVers) {
                pool.ensureVersionConstant(ver);
            }
        } else if (m_constVersion != null) {
            m_constVersion = pool.register(m_constVersion);
        }

        m_constDir       = pool.register(m_constDir);
        m_constTimestamp = pool.register(m_constTimestamp);
    }

    @Override
    protected void assemble(DataOutput out)
            throws IOException {
        super.assemble(out);

        out.writeByte(m_moduletype.ordinal());

        ConstantPool pool = getConstantPool();

        if (isFingerprint()) {
            VersionTree<Boolean> vtreeAllow = m_vtreeImportAllowVers;
            writePackedLong(out, vtreeAllow.size());
            for (Version ver : vtreeAllow) {
                writePackedLong(out, pool.ensureVersionConstant(ver).getPosition());
                out.writeBoolean(vtreeAllow.get(ver));
            }

            List<Version> listPrefer = m_listImportPreferVers;
            writePackedLong(out, listPrefer.size());
            for (Version ver : listPrefer) {
                writePackedLong(out, pool.ensureVersionConstant(ver).getPosition());
            }
        } else {
            if (m_constVersion == null) {
                out.writeBoolean(false);
            } else {
                out.writeBoolean(true);
                writePackedLong(out, m_constVersion.getPosition());
            }
        }

        writePackedLong(out, Constant.indexOf(m_constDir));
        writePackedLong(out, Constant.indexOf(m_constTimestamp));
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getDescription());

        sb.append(", module type=")
          .append(m_moduletype);

        if (isFingerprint()) {
            sb.append(", fingerprint=true");

            VersionTree<Boolean> vtreeAllow = m_vtreeImportAllowVers;
            List<Version>        listPrefer = m_listImportPreferVers;
            if (!vtreeAllow.isEmpty() || !listPrefer.isEmpty()) {
                sb.append(", version={");
                boolean fFirst = true;

                for (Version ver : vtreeAllow) {
                    if (fFirst) {
                        sb.append(", ");
                        fFirst = false;
                    }

                    sb.append(vtreeAllow.get(ver) ? "allow " : "avoid ")
                      .append(ver);
                }

                for (Version ver : listPrefer) {
                    if (fFirst) {
                        sb.append(", ");
                        fFirst = false;
                    }

                    sb.append("prefer ")
                      .append(ver);
                }

                sb.append('}');
            }
        } else {
            if (m_constVersion != null) {
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
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof ModuleStructure that && super.equals(obj))) {
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
    public enum ModuleType {
        Primary, Optional, Desired, Required, Embedded;

        /**
         * Look up a Format enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the Format enum for the specified ordinal
         */
        public static ModuleType valueOf(int i) {
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

    /**
     * Cached crypto-digest of this module structure.
     */
    private transient byte[] m_abDigest;

    /**
     * @see {@link #getConditionalNames()}
     */
    private transient Map<String, Boolean> m_mapCondNames;

    /**
     * @see {@link #getDependencies()}
     */
    private transient Map<ModuleConstant, Boolean> m_mapDependencies;

    /**
     * Tree of versions held by this ModuleStructure.
     * <ul>
     * <li>If the tree is empty, that indicates that the module structure does not contain version
     * information (the module information is not version labeled.)</li>
     * <li>If the tree contains one version, that indicates that the module structure contains a
     * single version label, i.e. there is a single version of the module inside of the module
     * structure.</li>
     * <li>If the tree contains more than one version, that indicates that the module structure
     * contains multiple different versions of the module, and must be resolved in order to link the
     * module.</li>
     * </ul>
     */
    private transient VersionTree<Boolean> m_vtree;
}