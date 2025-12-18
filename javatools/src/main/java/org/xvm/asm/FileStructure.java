package org.xvm.asm;


import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import java.time.Instant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.util.LinkedIterator;

import static org.xvm.util.Handy.intToHexString;
import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.toInputStream;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A representation of the file structure that contains one or more Ecstasy (XVM) modules. The
 * FileStructure is generally used as a container of one module, which may have dependencies on
 * other modules, some of which may be wholly embedded into the file structure. In other words, the
 * FileStructure is the "module container".
 */
public class FileStructure
        extends Component {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a file structure that will initially contain one module.
     *
     * @param sModule   the fully qualified module name
     */
    public FileStructure(String sModule) {
        super(null, Access.PUBLIC, true, true, true, Format.FILE, null, null);

        // module name required
        if (sModule == null) {
            throw new IllegalArgumentException("module name required");
        }

        // create and register the main module
        ConstantPool    pool     = new ConstantPool(this);
        ModuleConstant  idModule = pool.ensureModuleConstant(sModule);
        ModuleStructure module   = new ModuleStructure(this, idModule);
        module.setTimestamp(pool.ensureTimeConstant(Instant.now()));

        if (!addChild(module)) {
            throw new IllegalStateException("module already exists");
        }

        m_pool        = pool;
        m_idModule    = idModule;
        m_nMajorVer   = VERSION_MAJOR_CUR;
        m_nMinorVer   = VERSION_MINOR_CUR;
    }

    /**
     * Construct a file structure for an existing file.
     *
     * @param file  the file that contains the existing FileStructure
     *
     * @throws IOException  if an IOException occurs while reading the FileStructure
     */
    public FileStructure(File file)
            throws IOException {
        this(file, true);
    }

    /**
     * Construct a file structure for an existing file.
     *
     * @param file   the file that contains the existing FileStructure
     * @param fLazy  true to defer the module deserialization until necessary
     *
     * @throws IOException  if an IOException occurs while reading the FileStructure
     */
    public FileStructure(File file, boolean fLazy)
            throws IOException {
        this(toInputStream(file), true, fLazy);

        m_file = file;
    }

    /**
     * Construct a file structure for an existing file. Note that the stream is not closed by the
     * constructor.
     *
     * @param in  a stream that contains a FileStructure
     *
     * @throws IOException  if an IOException occurs while reading the FileStructure
     */
    public FileStructure(InputStream in)
            throws IOException {
        this(in, false, true);
    }

    /**
     * Construct a file structure for an existing file.
     *
     * @param in          a stream that contains a FileStructure
     * @param fAutoClose  true to close the stream; false to leave the stream open
     * @param fLazy       true to defer the module deserialization until necessary
     *
     * @throws IOException  if an IOException occurs while reading the FileStructure
     */
    public FileStructure(InputStream in, boolean fAutoClose, boolean fLazy)
            throws IOException {
        super(null, Access.PUBLIC, true, true, true, Format.FILE, null, null);

        m_fLazyDeser = fLazy;
        try {
            disassemble(new DataInputStream(in));
        } finally {
            if (fAutoClose) {
                try {
                    in.close();
                } catch (IOException ignore) {}
            }
        }
    }

    /**
     * Copy constructor.
     *
     * @param module       the module to copy
     * @param fSynthesize  if true, synthesize all necessary structures
     */
    public FileStructure(ModuleStructure module, boolean fSynthesize) {
        super(null, Access.PUBLIC, true, true, true, Format.FILE, null, null);

        FileStructure fileStructure = module.getFileStructure();

        m_nMajorVer = fileStructure.m_nMajorVer;
        m_nMinorVer = fileStructure.m_nMinorVer;
        m_pool      = new ConstantPool(this);

        merge(module, fSynthesize, true);
    }

    /**
     * Merge the specified module into this FileStructure.
     *
     * @param module       the module to merge
     * @param fSynthesize  if true, synthesize all necessary structures
     * @param fTakeFile    if true, merge the os-file info as well
     */
    public void merge(ModuleStructure module, boolean fSynthesize, boolean fTakeFile) {
        ModuleStructure moduleClone = module.cloneBody();
        moduleClone.setContaining(this);

        addChild(moduleClone);
        moduleClone.cloneChildren(module.children());

        ConstantPool pool = m_pool;

        try (var ignore = ConstantPool.withPool(pool)) {
            // add fingerprints
            for (ModuleStructure moduleChild : module.getFileStructure().children()) {
                if (moduleChild.isFingerprint() && getModule(moduleChild.getIdentityConstant()) == null) {
                    ModuleStructure moduleChildClone = moduleChild.cloneBody();
                    moduleChildClone.setContaining(this);
                    addChild(moduleChildClone);
                    moduleChildClone.registerConstants(pool);
                }
            }

            moduleClone.registerConstants(pool);
            moduleClone.registerChildrenConstants(pool);
            if (fSynthesize) {
                moduleClone.synthesizeChildren();
            }

            TypeConstant typeNakedRef = module.getConstantPool().getNakedRefType();
            if (typeNakedRef != null) {
                pool.setNakedRefType(typeNakedRef);
            }
        }

        if (fTakeFile) {
            m_idModule = moduleClone.getIdentityConstant();
            m_file     = module.getFileStructure().m_file;
        }
    }


    // ----- serialization -------------------------------------------------------------------------

    /**
     * Write the FileStructure to the specified file.
     *
     * @param file  the file to write to
     *
     * @throws IOException  if an IOException occurs while writing the FileStructure
     */
    public void writeTo(File file)
            throws IOException {
        FileOutputStream fos = new FileOutputStream(file);

        try {
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            try {
                writeTo(bos);
            } finally {
                bos.flush();
                bos.close();
            }
        } finally {
            fos.flush();
            fos.close();
        }
    }

    /**
     * Write the FileStructure to the provided OutputStream.
     *
     * @param out  the stream to write to
     *
     * @throws IOException  if an IOException occurs while writing the FileStructure
     */
    public void writeTo(OutputStream out)
            throws IOException {
        writeTo((DataOutput) new DataOutputStream(out));
    }

    /**
     * Write the FileStructure to the provided DataOutput stream.
     *
     * @param out  the DataOutput stream to write to
     *
     * @throws IOException  if an IOException occurs while writing the FileStructure
     */
    public void writeTo(DataOutput out)
            throws IOException {
        reregisterConstants(true);
        assemble(out);
        resetModified();
    }


    // ----- module containment --------------------------------------------------------------------

    /**
     * Obtain the primary module that the FileStructure represents.
     *
     * @return the ModuleStructure that this FileStructure represents; never null
     */
    public ModuleStructure getModule() {
        return (ModuleStructure) getChild(m_idModule);
    }

    /**
     * @return the id of the primary Module
     */
    public ModuleConstant getModuleId() {
        return m_idModule;
    }

    /**
     * @return a set of module ids contained within this FileStructure; the caller must
     *         treat the set as a read-only object
     */
    public Set<ModuleConstant> moduleIds() {
        Set<ModuleConstant> setIds = f_moduleById.keySet();
        assert (setIds = Collections.unmodifiableSet(setIds)) != null;
        return setIds;
    }

    /**
     * Obtain the specified module from the FileStructure.
     *
     * @param id  the module id
     *
     * @return the specified module, or null if it does not exist in this FileStructure
     */
    public ModuleStructure getModule(ModuleConstant id) {
        return f_moduleById.get(id);
    }

    /**
     * Obtain the specified module from the FileStructure, creating it if it does not already
     * exist in the FileStructure.
     *
     * @param sName  the qualified module name
     *
     * @return the specified module
     */
    public ModuleStructure ensureModule(String sName) {
        return ensureModule(m_pool.ensureModuleConstant(sName));
    }

    /**
     * Obtain the specified module from the FileStructure, creating it if it does not already
     * exist in the FileStructure.
     *
     * @param id  the module ide
     *
     * @return the specified module
     */
    public ModuleStructure ensureModule(ModuleConstant id) {
        ModuleStructure module = getModule(id);
        if (module == null) {
            addChild(module = new ModuleStructure(this, id));
        }
        return module;
    }

    /**
     * Find a module with the specified name at this FileStructure.
     *
     * @param sName  the qualified module name
     *
     * @return the specified module or null if not found
     */
    public ModuleStructure findModule(String sName) {
        for (ModuleStructure module : f_moduleById.values()) {
            if (module.getName().equals(sName)) {
                return module;
            }
        }
        return null;
    }

    /**
     * Link the modules in this FileStructure.
     *
     * @param repository  the module repository to load modules from
     * @param fRuntime    if true, the linked modules need to be adopted by this file structure as
     *                    part of the linking process; otherwise the modules might still be in the
     *                    process of compiling, so cloning the current incomplete state would cause
     *                    the linking to miss the build completion
     *
     * @return null iff success, otherwise the id of the first module that could not be linked to
     */
    public ModuleConstant linkModules(ModuleRepository repository, boolean fRuntime) {
        if (fRuntime) {
            if (m_fLinked) {
                return null;
            }
        }

        ModuleConstant idMissing = findMissing(repository, new HashSet<>(), fRuntime);
        if (idMissing == null) {
            idMissing = linkModules(repository, this, new HashSet<>(), fRuntime);
            if (idMissing == null) {
                markLinked();
            }
        }
        return idMissing;
    }

    /**
     * The first phase of the linkModules() implementation - check if any necessary modules are
     * missing from the repository.
     *
     * @return the first missing module name; null all modules are present
     */
    private ModuleConstant findMissing(ModuleRepository repository, Set<String> setFilesChecked,
                               boolean fRuntime) {
        if (!setFilesChecked.add(getModuleId().getName())) {
            return null;
        }

        List<FileStructure>  listFilesTodo   = new ArrayList<>();
        List<ModuleConstant> listModulesTodo = new ArrayList<>(moduleIds());
        Set<ModuleConstant>  setModulesDone  = new HashSet<>();

        // the primary module is implicitly linked already
        setModulesDone.add(getModuleId());

        // recursive check of all downstream modules
        for (ModuleConstant idModule : listModulesTodo) {
            // only need to link it once (each node in the graph gets visited once)
            if (!setModulesDone.add(idModule)) {
                continue;
            }

            ModuleStructure moduleFingerprint = getModule(idModule);
            assert moduleFingerprint != null;
            if (moduleFingerprint.isLinked()) {
                // this module is already in our FileStructure as a real, fully loaded and linked
                // module
                continue;
            }

            String sModule = idModule.getName();
            if (repository.getModuleNames().contains(sModule)) {
                // no need to recurse at run-time since all contained modules must have
                // corresponding fingerprints at the top file
                if (!fRuntime) {
                    ModuleStructure moduleUnlinked = repository.loadModule(sModule,
                                                        idModule.getVersion(), !fRuntime);
                    assert moduleUnlinked != null;

                    FileStructure fileUnlinked = moduleUnlinked.getFileStructure();
                    if (!setFilesChecked.contains(sModule)) {
                        listFilesTodo.add(fileUnlinked); // recurse downstream
                    }
                }
            } else {
                // no error is logged here; the package that imports the module will detect the
                // error when it is asked to resolve global names; see TypeCompositionStatement
                return idModule;
            }
        }

        for (FileStructure fileDownstream : listFilesTodo) {
            assert !fRuntime;
            ModuleConstant idMissing = fileDownstream.findMissing(repository, setFilesChecked, false);
            if (idMissing != null) {
                return idMissing;
            }
        }

        return null;
    }

    /**
     * The second phase of the linkModules implementation - actual linking.
     *
     * @return null iff success, otherwise the id of the first module that could not be linked to
     */
    private ModuleConstant linkModules(ModuleRepository repository, FileStructure fileTop,
                                       Set<ModuleConstant> setFilesDone, boolean fRuntime) {
        if (!setFilesDone.add(getModuleId())) {
            return null;
        }

        List<FileStructure>   listFilesTodo = new ArrayList<>();
        List<ModuleStructure> listReplace   = new ArrayList<>();

        // collect the child module names; we're already processing the primary module of this
        // file structure, so mark it as "done" to avoid recursively processing the same one again
        List<ModuleConstant> listModulesTodo = new ArrayList<>(moduleIds());
        Set<ModuleConstant>  setModulesDone  = new HashSet<>();
        setModulesDone.add(getModuleId());

        // recursive link of all downstream modules; by now nothing is missing
        for (int iNextTodo = 0; iNextTodo < listModulesTodo.size(); ++iNextTodo) {
            // only need to link it once (each node in the graph gets visited once)
            ModuleConstant idModule = listModulesTodo.get(iNextTodo);
            if (!setModulesDone.add(idModule)) {
                continue;
            }

            ModuleStructure moduleFingerprint = getModule(idModule);
            if (moduleFingerprint == null) {
                return idModule;
            }

            ModuleStructure moduleUnlinked =
                    repository.loadModule(idModule.getName(), idModule.getVersion(), !fRuntime);
            if (moduleUnlinked == null) {
                return idModule;
            }

            FileStructure fileUnlinked = moduleUnlinked.getFileStructure();

            if (idModule.getVersion() != null) {
                moduleUnlinked.registerConstants(fileTop.m_pool);
                moduleUnlinked.registerChildrenConstants(fileTop.m_pool);
            }

            if (fRuntime) {
                if (!moduleFingerprint.isFingerprint()) {
                    // this module is already in our FileStructure as a real, fully loaded and linked
                    // module
                    continue;
                }

                listReplace.add(moduleUnlinked);
                listModulesTodo.addAll(fileUnlinked.moduleIds());
            } else { // compile-time
                if (!moduleFingerprint.isLinked()) {
                    moduleFingerprint.setFingerprintOrigin(moduleUnlinked);
                }

                if (fileTop.getModule(idModule) == null) {
                    fileTop.addChild(moduleFingerprint);
                }

                if (!setFilesDone.contains(idModule)) {
                    listFilesTodo.add(fileUnlinked); // recurse downstream
                }
            }
        }

        if (!listReplace.isEmpty()) {
            assert fRuntime;
            replace(listReplace);
        }

        for (FileStructure fileDownstream : listFilesTodo) {
            assert !fRuntime;
            ModuleConstant idMissing = fileDownstream.linkModules(repository, fileTop, setFilesDone, false);
            if (idMissing != null) {
                return idMissing;
            }
        }
        return null;
    }

    /**
     * Replace "fingerprint" modules with an actual ones.
     *
     * @param listUnlinked  the list of "raw" modules to replace the fingerprint with
     */
    public void replace(List<ModuleStructure> listUnlinked) {
        List<ModuleStructure> listLinked = new ArrayList<>();
        for (ModuleStructure moduleUnlinked : listUnlinked) {
            ModuleStructure moduleLinked = moduleUnlinked.cloneBody();

            moduleLinked.setContaining(this);
            moduleLinked.cloneChildren(moduleUnlinked.children());

            replaceChild(getModule(moduleLinked.getIdentityConstant()), moduleLinked);

            listLinked.add(moduleLinked);
        }

        ConstantPool pool = m_pool;
        for (ModuleStructure moduleLinked : listLinked) {
            moduleLinked.registerConstants(pool);
            moduleLinked.registerChildrenConstants(pool);
        }

        for (ModuleStructure moduleLinked : listLinked) {
            moduleLinked.synthesizeChildren();
        }
    }

    /**
     * @return true iff the FileStructure has been fully linked
     */
    public boolean isLinked() {
        return m_fLinked;
    }

    /**
     * Mark this FileStructure as linked.
     */
    public void markLinked() {
        m_fLinked = true;
    }

    // ----- FileStructure methods -----------------------------------------------------------------

    /**
     * Obtain the AssemblerContext that is used to specify conditional sections during creation of
     * the hierarchical XVM structures.
     *
     * @return the AssemblerContext, or null if none has been created
     */
    public AssemblerContext getContext() {
        return m_ctx;
    }

    /**
     * Obtain the AssemblerContext that is used to specify conditional sections during creation of
     * the hierarchical XVM structures.
     *
     * @return the AssemblerContext, creating one if necessary
     */
    protected AssemblerContext ensureContext() {
        AssemblerContext ctx = m_ctx;
        if (ctx == null) {
            m_ctx = ctx = new AssemblerContext(m_pool);
        }
        return ctx;
    }

    /**
     * Determine the major version of the XVM specification and related tool-chain that this
     * particular FileStructure corresponds to.
     * <p/>
     * This is unrelated to version labels and the versioning of modules.
     *
     * @return the major version of the binary form that this FileStructure was constructed from, or
     *         the current major version if this FileStructure was constructed from scratch
     */
    public int getFileMajorVersion() {
        return m_nMajorVer;
    }

    /**
     * Determine the minor version of the XVM specification and related tool-chain that this
     * particular FileStructure corresponds to.
     * <p/>
     * This is unrelated to version labels and the versioning of modules.
     *
     * @return the minor version of the binary form that this FileStructure was constructed from, or
     *         the current minor version if this FileStructure was constructed from scratch
     */
    public int getFileMinorVersion() {
        return m_nMinorVer;
    }

    /**
     * @return the OS file that this structure was loaded from or null if the structure
     *         is transient (in-memory only)
     */
    public File getOSFile() {
        return m_file;
    }

    /**
     * Determine the current major version of the XVM specification and related tool-chain that this
     * implementation supports.
     * <p/>
     * This is unrelated to version labels and the versioning of modules.
     *
     * @return the current major version of the XVM specification and related tool-chain that this
     *         implementation supports
     */
    public static int getToolMajorVersion() {
        return VERSION_MAJOR_CUR;
    }

    /**
     * Determine the current minor version of the XVM specification and related tool-chain that this
     * implementation supports.
     * <p/>
     * This is unrelated to version labels and the versioning of modules.
     *
     * @return the current minor version of the XVM specification and related tool-chain that this
     *         implementation supports
     */
    public static int getToolMinorVersion() {
        return VERSION_MINOR_CUR;
    }

    /**
     * Determine if the specified version of an XVM file structure is supported by this version of
     * the XVM assembler.
     * <p/>
     * This is unrelated to version labels and the versioning of modules.
     *
     * @param nVerMajor  major version number
     * @param nVerMinor  minor version number
     *
     * @return true if this version of the XVM assembler can correctly read in and deal with a file
     *         structure of the specified version
     */
    public static boolean isFileVersionSupported(int nVerMajor, int nVerMinor) {
        if (nVerMajor == VERSION_MAJOR_CUR && nVerMinor == VERSION_MINOR_CUR) {
            return true;
        }

        if (nVerMajor > VERSION_MAJOR_CUR) {
            return false;
        }

        // NOTE to future self: this is where specific version number backwards compatibility checks
        //                      will be added

        return false;
    }

    /**
     * Change the identity of the main (versionless) module in this FileStructure and all constants
     * that refer to its id with the specified versioned module id.
     *
     * Note: this FileStructure must be temporary as it will be rendered unusable.
     *
     * @return the new ModuleStructure that has all the child components referring to it by the
     *         new (versioned) id
     */
    public ModuleStructure replaceModuleId(ModuleConstant idNew) {
        ModuleStructure module = getModule();
        ModuleConstant  idOld  = module.getIdentityConstant();

        idNew = (ModuleConstant) m_pool.register(idNew);

        module.replaceThisIdentityConstant(idNew);

        f_moduleById.put(idNew, module);
        m_pool.replaceModule(idOld, idNew);

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        try {
            assemble(new DataOutputStream(outBytes));
            FileStructure fileStructure = new FileStructure(
                    new DataInputStream(new ByteArrayInputStream(outBytes.toByteArray())));
            return fileStructure.getModule();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ----- Component methods ---------------------------------------------------------------------

    @Override
    public String getName() {
        return m_file == null
                ? getModuleId().getUnqualifiedName() + ".xtc"
                : m_file.getName();
    }

    @Override
    public boolean isGloballyVisible() {
        // file is not identifiable, and therefore is not visible
        return false;
    }

    @Override
    protected boolean isSiblingAllowed() {
        // TODO
        return false;
    }

    @Override
    protected void replaceChildIdentityConstant(IdentityConstant idOld, IdentityConstant idNew) {
        assert idOld instanceof ModuleConstant;
        assert idNew instanceof ModuleConstant;

        Map<ModuleConstant, ModuleStructure> map = f_moduleById;

        ModuleStructure child = map.remove(idOld);
        if (child != null) {
            map.put((ModuleConstant) idNew, child);
        }

        if (m_idModule.equals(idOld)) {
            m_idModule = (ModuleConstant) idNew;
        }
    }

    @Override
    public Map<String, Component> getChildByNameMap() {
        // there could be number of child modules with the same name;
        // "Use f_moduleById or children() instead"
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Component> ensureChildByNameMap() {
        return getChildByNameMap();
    }

    @Override
    public boolean addChild(Component child) {
        // FileStructure can only hold ModuleStructures
        assert child instanceof ModuleStructure;

        Map<ModuleConstant, ModuleStructure> kids    = f_moduleById;
        ModuleStructure                      module  = (ModuleStructure) child;
        ModuleConstant                       id      = module.getIdentityConstant();
        ModuleStructure                      sibling = kids.get(id);
        if (sibling == null) {
            kids.put(id, module);
        } else if (isSiblingAllowed()) {
            linkSibling(module, sibling);
        } else {
            return false;
        }

        markModified();
        return true;
    }

    @Override
    public void removeChild(Component child) {
        assert child instanceof ModuleStructure;
        assert child.getParent() == this;

        Map<ModuleConstant, ModuleStructure> kids    = f_moduleById;
        ModuleStructure                      module  = (ModuleStructure) child;
        ModuleConstant                       id      = module.getIdentityConstant();
        ModuleStructure                      sibling = kids.remove(id);

        unlinkSibling(kids, id, module, sibling);
    }

    @Override
    protected void replaceChild(Component childOld, Component childNew) {
        assert childOld instanceof ModuleStructure;
        assert childNew instanceof ModuleStructure;
        assert childNew.getParent() == this;
        assert childOld.getIdentityConstant().equals(childNew.getIdentityConstant());

        ModuleStructure module = (ModuleStructure) childNew;
        f_moduleById.put(module.getIdentityConstant(), module);
    }

    @Override
    public Component getChild(Constant constId) {
        return constId instanceof ModuleConstant idModule ? f_moduleById.get(idModule) : null;
    }

    @Override
    public ModuleStructure getChild(String sName) {
        return f_moduleById.get(new ModuleConstant(m_pool, sName));
    }

    @Override
    public int getChildrenCount() {
        ensureChildren();

        return f_moduleById.size();
    }

    @Override
    public boolean hasChildren() {
        return getChildrenCount() > 0;
    }

    @Override
    public Collection<? extends ModuleStructure> children() {
        return f_moduleById.values();
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public FileStructure getFileStructure() {
        return this;
    }

    @Override
    public ConstantPool getConstantPool() {
        return m_pool;
    }

    @Override
    public Iterator<? extends XvmStructure> getContained() {
        return new LinkedIterator(
                Collections.singleton(m_pool).iterator(),
                children().iterator());
    }

    @Override
    public boolean isConditional() {
        // the FileStructure does not support conditional inclusion of itself
        return false;
    }

    @Override
    public boolean isPresent(LinkerContext ctx) {
        // the FileStructure does not support conditional inclusion of itself
        return true;
    }

    @Override
    protected void disassemble(DataInput in)
            throws IOException {
        // validate that it is an xtc/xvm file format
        int nMagic = in.readInt();
        if (nMagic != FILE_MAGIC) {
            throw new IOException(
                    "not an .xtc format file; invalid magic header: " + intToHexString(nMagic));
        }

        // validate the version; this is rather simple in the beginning (before there is a long
        // history of evolution of the Ecstasy language and the XVM specification), as versions will
        // initially be ascending-only, but at some point, only up-to-specific-minor-versions of
        // specific-major-versions are likely to be supported
        m_nMajorVer = in.readInt();
        m_nMinorVer = in.readInt();
        if (!isFileVersionSupported(m_nMajorVer, m_nMinorVer)) {
            throw new IOException("unsupported version: " + m_nMajorVer + "." + m_nMinorVer);
        }

        // read in the constant pool
        ConstantPool pool = new ConstantPool(this);
        m_pool = pool;
        pool.disassemble(in);

        m_idModule = pool.getConstant(readIndex(in), ModuleConstant.class);
        disassembleChildren(in, m_fLazyDeser);

        // must be at least one module (the first module is considered to be the "main" module)
        if (getModule() == null) {
            throw new IOException("the file does not contain a primary module");
        }
    }

    /**
     * Re-registers all referenced constants with the pool.
     */
    public void reregisterConstants(boolean fOptimize) {
        ConstantPool pool = m_pool;
        pool.preRegisterAll();
        registerConstants(pool);
        pool.postRegisterAll(fOptimize);
    }

    @Override
    protected void registerConstants(ConstantPool pool) {
        pool.registerConstants(pool);
        registerChildrenConstants(pool);
    }

    @Override
    protected void assemble(DataOutput out)
            throws IOException {
        out.writeInt(FILE_MAGIC);
        out.writeInt(VERSION_MAJOR_CUR);
        out.writeInt(VERSION_MINOR_CUR);
        m_pool.assemble(out);
        writePackedLong(out, getModuleId().getPosition());
        assembleChildren(out);
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();

        String sName = getModuleId().getName();
        sb.append("main-module=")
          .append(sName);

        boolean first = true;
        for (ModuleConstant id : moduleIds()) {
            if (!id.getName().equals(sName)) {
                if (first) {
                    sb.append(", other-modules={");
                    first = false;
                } else {
                    sb.append(", ");
                }
                sb.append(id.getName());
            }
        }
        if (!first) {
            sb.append('}');
        }

        sb.append(", xvm-version=")
                .append(getFileMajorVersion())
                .append('.')
                .append(getFileMinorVersion())
                .append(", ")
                .append(super.getDescription());

        return sb.toString();
    }

    @Override
    protected void dump(PrintWriter out, String sIndent) {
        out.print(sIndent);
        out.println(this);

        final ConstantPool pool = m_pool;
        if (pool != null) {
            pool.dump(out, nextIndent(sIndent));
        }

        dumpChildren(out, sIndent);
    }

    /**
     * For debugging only: ensure that all constants for all children belong to the same pool.
     */
    public boolean validateConstants() {
        assert m_pool.getNakedRefType() != null;

        Consumer<Component> visitor = component -> {
            if (component instanceof ClassStructure) {
                component.getContributionsAsList().forEach(contrib -> {
                    assert contrib.getTypeConstant().getConstantPool() == m_pool;
                });
            } else if (component instanceof MethodStructure method) {
                Constant[] aconst = method.getLocalConstants();
                if (aconst != null) {
                    for (Constant constant : aconst) {
                        assert constant.getConstantPool() == m_pool;
                    }
                }
            }
        };
        visitChildren(visitor, false, true);
        return true;
    }

    @Override
    public ErrorListener getErrorListener() {
        ErrorListener errs = m_errs;
        if (errs == null) {
            ConstantPool poolCurrent = ConstantPool.getCurrentPool();
            if (poolCurrent != m_pool) {
                errs = poolCurrent.getErrorListener();
            }
        }
        return errs == null ? ErrorListener.RUNTIME : errs;
    }

    @Override
    public void setErrorListener(ErrorListener errs) {
        m_errs = errs;
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode() {
        return getModule().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof FileStructure that) {
            // ignore the constant pool, since its only purpose is to be referenced from the nested
            // XVM structures
            return this.m_nMajorVer == that.m_nMajorVer
                    && this.m_nMinorVer == that.m_nMinorVer
                    && this.m_idModule.equals(that.m_idModule)
                    && this.f_moduleById.equals(that.f_moduleById);
        }

        return false;
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The file that the file structure was loaded from.
     */
    private File m_file;

    /**
     * The indicator that deserialization of components should be done lazily (deferred).
     */
    private boolean m_fLazyDeser;

    /**
     * The indicator that the file structure has been linked by the run-time.
     */
    private boolean m_fLinked;

    /**
     * The id of the main module that the FileStructure represents. There may be additional
     * modules in the FileStructure, but generally, they only represent imports (included embedded
     * modules) of the main module.
     *
     * Note: for persistent file structures the main module id is never versioned (its version is
     * null).
     */
    private ModuleConstant m_idModule;

    /**
     * This holds all of the children modules.
     */
    private final Map<ModuleConstant, ModuleStructure> f_moduleById = new HashMap<>();

    /**
     * The ConstantPool for the FileStructure.
     */
    private ConstantPool m_pool;

    /**
     * The AssemblerContext that is associated with this FileStructure.
     */
    private AssemblerContext m_ctx;

    /**
     * If the structure was disassembled from a binary, this is the major version of the Ecstasy/XVM
     * specification that the binary was assembled with. Otherwise, it is the current version.
     */
    private int m_nMajorVer;

    /**
     * If the structure was disassembled from a binary, this is the minor version of the Ecstasy/XVM
     * specification that the binary was assembled with. Otherwise, it is the current version.
     */
    private int m_nMinorVer;

    private transient ErrorListener m_errs;
}