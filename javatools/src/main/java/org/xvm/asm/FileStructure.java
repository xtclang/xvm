package org.xvm.asm;


import java.io.BufferedOutputStream;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.util.LinkedIterator;

import static org.xvm.util.Handy.intToHexString;
import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.toInputStream;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A representation of the file structure that contains one more more Ecstasy (XVM) modules. The
 * FileStructure is generally used as a container of one module, which may have dependencies on
 * other modules, some of which may be wholly embedded into the file structure. In other words, the
 * FileStructure is the "module container".
 */
public class FileStructure
        extends Component
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a file structure that will initially contain one module.
     *
     * @param sModule   the fully qualified module name
     */
    public FileStructure(String sModule)
        {
        super(null, Access.PUBLIC, true, true, true, Format.FILE, null, null);

        // module name required
        if (sModule == null)
            {
            throw new IllegalArgumentException("module name required");
            }

        // create and register the main module
        ConstantPool    pool   = new ConstantPool(this);
        ModuleStructure module = new ModuleStructure(this, pool.ensureModuleConstant(sModule));

        if (!addChild(module))
            {
            throw new IllegalStateException("module already exists");
            }

        m_pool        = pool;
        m_sModuleName = sModule;
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
            throws IOException
        {
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
            throws IOException
        {
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
            throws IOException
        {
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
            throws IOException
        {
        super(null, Access.PUBLIC, true, true, true, Format.FILE, null, null);

        m_fLazyDeser = fLazy;
        try
            {
            disassemble(new DataInputStream(in));
            }
        finally
            {
            if (fAutoClose)
                {
                try
                    {
                    in.close();
                    }
                catch (IOException e) {}
                }
            }
        }

    /**
     * Copy constructor.
     *
     * @param module  the module to copy
     */
    public FileStructure(ModuleStructure module)
        {
        super(null, Access.PUBLIC, true, true, true, Format.FILE, null, null);

        FileStructure fileStructure = module.getFileStructure();

        m_nMajorVer = fileStructure.m_nMajorVer;
        m_nMinorVer = fileStructure.m_nMinorVer;
        m_pool      = new ConstantPool(this);

        merge(module);
        }

    /**
     * Merge the specified module into this FileStructure.
     *
     * @param module  the module to merge
     */
    public void merge(ModuleStructure module)
        {
        m_sModuleName = module.getName();

        // connect fingerprints with real modules to allow component resolution
        // during "registerConstant" phase
        for (Component child : module.getFileStructure().children())
            {
            ModuleStructure moduleChild = (ModuleStructure) child;
            if (moduleChild.isFingerprint())
                {
                ModuleStructure moduleReal = getModule(moduleChild.getName());
                if (moduleReal != null)
                    {
                    moduleChild.setFingerprintOrigin(moduleReal);
                    }
                }
            }

        ModuleStructure moduleClone = module.cloneBody();
        moduleClone.setContaining(this);

        addChild(moduleClone);
        moduleClone.cloneChildren(module.children());

        ConstantPool pool = m_pool;

        moduleClone.registerConstants(pool);
        moduleClone.registerChildrenConstants(pool);

        TypeConstant typeNakedRef = module.getConstantPool().getNakedRefType();
        if (typeNakedRef != null)
            {
            pool.setNakedRefType(typeNakedRef);
            }

        // add fingerprints
        for (Component child : module.getFileStructure().children())
            {
            ModuleStructure moduleChild = (ModuleStructure) child;
            if (moduleChild.isFingerprint() && getModule(moduleChild.getName()) == null)
                {
                moduleClone = moduleChild.cloneBody();
                moduleClone.setContaining(this);
                addChild(moduleClone);
                moduleClone.registerConstants(pool);
                }
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
            throws IOException
        {
        FileOutputStream fos = new FileOutputStream(file);

        m_file = file;
        try
            {
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            try
                {
                writeTo(bos);
                }
            finally
                {
                bos.flush();
                bos.close();
                }
            }
        finally
            {
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
            throws IOException
        {
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
            throws IOException
        {
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
    public ModuleStructure getModule()
        {
        return (ModuleStructure) getChild(m_sModuleName);
        }

    /**
     * Determine the Module name.
     *
     * @return the name of the Module
     */
    public String getModuleName()
        {
        return m_sModuleName;
        }

    /**
     * @return a set of qualified module names contained within this FileStructure; the caller must
     *         treat the set as a read-only object
     */
    public Set<String> moduleNames()
        {
        Set<String> names = getChildByNameMap().keySet();
        assert (names = Collections.unmodifiableSet(names)) != null;
        return names;
        }

    /**
     * Obtain the specified module from the FileStructure.
     *
     * @param sName  the qualified module name
     *
     * @return the specified module, or null if it does not exist in this FileStructure
     */
    public ModuleStructure getModule(String sName)
        {
        Component child = getChild(sName);
        if (child == null || child instanceof ModuleStructure)
            {
            return (ModuleStructure) child;
            }

        throw new IllegalStateException("module must be resolved");
        }

    /**
     * Obtain the specified module from the FileStructure, creating it if it does not already
     * exist in the FileStructure.
     *
     * @param sName  the qualified module name
     *
     * @return the specified module
     */
    public ModuleStructure ensureModule(String sName)
        {
        ModuleStructure module = getModule(sName);
        if (module == null)
            {
            module = new ModuleStructure(this, m_pool.ensureModuleConstant(sName));
            addChild(module);
            }
        return module;
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
     * @return null iff success, otherwise the name of the first module that could not be linked to
     */
    public String linkModules(ModuleRepository repository, boolean fRuntime)
        {
        return linkModules(repository, new HashSet<>(), fRuntime);
        }

    private String linkModules(ModuleRepository repository, Set<String> setFilesDone, boolean fRuntime)
        {
        if (!setFilesDone.add(getModuleName()))
            {
            return null;
            }

        ArrayList<FileStructure> listFilesTodo   = new ArrayList<>();
        ArrayList<String>        listModulesTodo = new ArrayList<>(moduleNames());
        Set<String>              setModulesDone  = new HashSet<>();
        String                   sMissing        = null;

        // the primary module is implicitly linked already
        setModulesDone.add(getModuleName());

        // "recursive" link of all downstream modules
        for (int iNextTodo = 0; iNextTodo < listModulesTodo.size(); ++iNextTodo)
            {
            // only need to link it once (each node in the graph gets visited once)
            String sModule = listModulesTodo.get(iNextTodo);
            if (!setModulesDone.add(sModule))
                {
                continue;
                }

            ModuleStructure structFingerprint = getModule(sModule);
            if (structFingerprint != null && (!structFingerprint.isFingerprint()
                    || structFingerprint.getFingerprintOrigin() != null))
                {
                // this module is already in our FileStructure as a real, fully loaded and
                // linked module
                continue;
                }

            // load the module against which the compilation will occur
            if (repository.getModuleNames().contains(sModule))
                {
                ModuleStructure structUnlinked = repository.loadModule(sModule); // TODO versions etc.
                if (fRuntime)
                    {
                    ConstantPool    pool         = m_pool;
                    ModuleStructure structLinked = structUnlinked.cloneBody();
                    structLinked.setContaining(this);
                    structLinked.cloneChildren(structUnlinked.children());
                    structLinked.registerConstants(pool);
                    structLinked.registerChildrenConstants(pool);

                    if (structFingerprint == null)
                        {
                        this.addChild(structLinked);
                        }
                    else
                        {
                        this.replaceChild(structFingerprint, structLinked);
                        }

                    // TODO eventually we need to handle the case that these are actual modules and not just pointers to the modules
                    listModulesTodo.addAll(structUnlinked.getFileStructure().moduleNames());
                    }
                else // compile-time
                    {
                    assert structFingerprint != null;
                    structFingerprint.setFingerprintOrigin(structUnlinked);

                    FileStructure fileDownstream = structUnlinked.getFileStructure();
                    if (!setFilesDone.contains(sModule))
                        {
                        listFilesTodo.add(fileDownstream);
                        }
                    }
                }
            else if (sMissing == null)
                {
                // no error is logged here; the package that imports the module will detect
                // the error when it is asked to resolve global names; see
                // TypeCompositionStatement
                sMissing = sModule;
                }
            }

        for (FileStructure fileDownstream : listFilesTodo)
            {
            assert !fRuntime;
            String sMissingDownstream = fileDownstream.linkModules(repository, setFilesDone, false);
            if (sMissingDownstream != null && sMissing == null)
                {
                sMissing = sMissingDownstream;
                }
            }

        return sMissing;
        }

    /**
     * @return a read-only tree of versions in this FileStructure
     */
    public VersionTree<Boolean> getVersionTree()
        {
        return m_vtree;
        }

    /**
     * Determine if the FileStructure contains version information.
     *
     * @return true if this contains version label(s) for the contained module(s)
     */
    boolean isVersioned()
        {
        return !getVersionTree().isEmpty();
        }

    /**
     * Determine if the specified primary module version is contained within this FileStructure.
     *
     * @param ver  a version number
     *
     * @return true if the specified version label is present
     */
    public boolean containsVersion(Version ver)
        {
        return getVersionTree().get(ver);
        }

    /**
     * Determine if the specified version of the primary module is supported, either by that exact
     * version, or by a subsequent version.
     *
     * @param ver     a version number
     * @param fExact  true if the version has to match exactly
     *
     * @return true if this module structure supports the specified version
     */
    public boolean supportsVersion(Version ver, boolean fExact)
        {
        if (containsVersion(ver))
            {
            return true;
            }

        if (!fExact)
            {
            if (getVersionTree().findHighestVersion(ver) != null)
                {
                return true;
                }
            }

        return false;
        }

    /**
     * If the module in this file structure contains an unlabeled version of a module, then label
     * the module with the provided version number; if the module contains a single version label,
     * then replace that version label with the specified version number.
     *
     * @param ver  the version number for the module in this module structure
     *
     * @throws IllegalStateException if this module structure has more than one version label
     */
    public void labelModuleVersion(Version ver)
        {
        if (ver == null)
            {
            throw new IllegalArgumentException("version required");
            }

        // only normalized versions are used as version labels
        final Version VER = ver = ver.normalize();

        VersionTree<Boolean> vtree = m_vtree;
        switch (vtree.size())
            {
            case 1:
                // module is already labeled; it is either this version, or another version (which
                // will be replaced with this version)
                if (vtree.get(ver))
                    {
                    // already has the right version number
                    return;
                    }

                // it has a different version; get rid of that version first
                purgeVersion(vtree.findHighestVersion());
                // fall through
            case 0:
                // the modules should not be labeled with a version; add one
                visitChildren(component -> component.addVersion(VER), true, false);
                break;

            default:
                throw new IllegalStateException("the module (" + getModuleName()
                        + ") already contains more than one version label");
            }

        vtree.put(ver, true);
        markModified();
        }

    /**
     * Given a second FileStructure with the same primary module identity as this FileStructure,
     * and with both FileStructure objects containing one or more version labels, merge the versions
     * from the second FileStructure into this module. Note that any version labels in the second
     * FileStructure that exist in this FileStructure will not be merged, as they already exist in
     * this FileStructure.
     *
     * @param that  a FileStructure containing one or more version labels of the same primary module
     *              that is stored in this FileStructure
     *
     * @throws IllegalStateException     if this module structure does not contain version label(s)
     * @throws IllegalArgumentException  if that FileStructure does not contain the same primary
     *         module as this, or it does not contain version label(s)
     */
    public void mergeVersions(FileStructure that)
        {
        if (!this.isVersioned())
            {
            throw new IllegalStateException("this FileStructure ("+ getName()
                    + ") does not contain a version label");
            }

        if (that == null)
            {
            throw new IllegalArgumentException("second FileStructure is required");
            }

        if (!this.getModuleName().equals(that.getModuleName()))
            {
            throw new IllegalArgumentException("second FileStructure (" + that.getName()
                    + ") does not contain the same primary module as this FileStructure ("
                    + this.getName() + ")");
            }

        if (!that.isVersioned())
            {
            throw new IllegalArgumentException("second FileStructure (" + that.getName()
                    + ") does not contain a version label");
            }

        // first, determine what versions need to be moved over
        VersionTree<Boolean> vtreeThis = this.m_vtree;
        VersionTree<Boolean> vtreeThat = that.m_vtree;
        VersionTree<Boolean> vtreeAdd  = new VersionTree<>();
        for (Version ver : vtreeThat)
            {
            if (!vtreeThis.get(ver))
                {
                vtreeAdd.put(ver, true);
                }
            }

        // TODO - actual merge processing

        // update the list of versions in this FileStructure
        for (Version ver : vtreeAdd)
            {
            vtreeThis.put(ver, true);
            }
        markModified();
        }

    /**
     * Remove a version label from this FileStructure. If there are multiple versions within this
     * FileStructure, then only the specified version label is removed. If there is only one version
     * within this FileStructure, then the structure is left unchanged, but the version label is
     * removed.
     *
     * @param ver  the version label to remove from this module structure
     */
    public void purgeVersion(Version ver)
        {
        if (ver == null)
            {
            throw new IllegalArgumentException("version required");
            }

        final Version ver1 = ver = ver.normalize();

        VersionTree<Boolean> vtree = m_vtree;
        if (!vtree.get(ver))
            {
            return;
            }

        if (vtree.size() == 1)
            {
            // this just strips any remaining version label off the entire component tree
            visitChildren(component -> component.removeVersion(ver1), true, true);
            }
        else
            {
            // this has to find just the components that have the specified version label, and
            // remove it from just those components, and then remove those components if they no
            // longer have any version label
            // TODO
            }

        vtree.remove(ver);
        markModified();
        }

    public void purgeVersionsExcept(Version ver)
        {
        if (ver == null)
            {
            throw new IllegalArgumentException("version required");
            }

        VersionTree<Boolean> vtree = m_vtree;
        ver = ver.normalize();
        if (!vtree.get(ver))
            {
            throw new IllegalArgumentException("version " + ver  + " does not exist in this file");
            }

        if (vtree.size() == 1)
            {
            // already done
            return;
            }

        // this has to go to every component, and remove every version except the specified version,
        // and then remove those components if they no longer have any version label
        // TODO

        vtree.clear();
        vtree.put(ver, true);
        markModified();
        }


    // ----- FileStructure methods -----------------------------------------------------------------

    /**
     * Obtain the AssemblerContext that is used to specify conditional sections during creation of
     * the hierarchical XVM structures.
     *
     * @return the AssemblerContext, or null if none has been created
     */
    public AssemblerContext getContext()
        {
        return m_ctx;
        }

    /**
     * Obtain the AssemblerContext that is used to specify conditional sections during creation of
     * the hierarchical XVM structures.
     *
     * @return the AssemblerContext, creating one if necessary
     */
    protected AssemblerContext ensureContext()
        {
        AssemblerContext ctx = m_ctx;
        if (ctx == null)
            {
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
    public int getFileMajorVersion()
        {
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
    public int getFileMinorVersion()
        {
        return m_nMinorVer;
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
    public static int getToolMajorVersion()
        {
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
    public static int getToolMinorVersion()
        {
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
    public static boolean isFileVersionSupported(int nVerMajor, int nVerMinor)
        {
        if (nVerMajor == VERSION_MAJOR_CUR && nVerMinor == VERSION_MINOR_CUR)
            {
            return true;
            }

        if (nVerMajor > VERSION_MAJOR_CUR)
            {
            return false;
            }

        // NOTE to future self: this is where specific version number backwards compatibility checks
        //                      will be added

        return false;
        }


    // ----- Component methods ---------------------------------------------------------------------

    @Override
    public String getName()
        {
        return m_file == null
                ? getModule().getIdentityConstant().getUnqualifiedName() + ".xtc"
                : m_file.getName();
        }

    @Override
    public boolean isGloballyVisible()
        {
        // file is not identifiable, and therefore is not visible
        return false;
        }

    @Override
    protected boolean isSiblingAllowed()
        {
        // TODO
        return false;
        }

    @Override
    public Component getChild(Constant constId)
        {
        if (constId instanceof ModuleConstant)
            {
            Component firstSibling = getChildByNameMap().get(((ModuleConstant) constId).getName());

            return findLinkedChild(constId, firstSibling);
            }
        return super.getChild(constId);
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public FileStructure getFileStructure()
        {
        return this;
        }

    @Override
    public ConstantPool getConstantPool()
        {
        return m_pool;
        }

    @Override
    public Iterator<? extends XvmStructure> getContained()
        {
        return new LinkedIterator(
                Collections.singleton(m_pool).iterator(),
                getChildByNameMap().values().iterator());
        }

    @Override
    public boolean isConditional()
        {
        // the FileStructure does not support conditional inclusion of itself
        return false;
        }

    @Override
    public boolean isPresent(LinkerContext ctx)
        {
        // the FileStructure does not support conditional inclusion of itself
        return true;
        }

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        // validate that it is an xtc/xvm file format
        int nMagic = in.readInt();
        if (nMagic != FILE_MAGIC)
            {
            throw new IOException(
                    "not an .xtc format file; invalid magic header: " + intToHexString(nMagic));
            }

        // validate the version; this is rather simple in the beginning (before there is a long
        // history of evolution of the Ecstasy language and the XVM specification), as versions will
        // initially be ascending-only, but at some point, only up-to-specific-minor-versions of
        // specific-major-versions are likely to be supported
        m_nMajorVer = in.readUnsignedShort();
        m_nMinorVer = in.readUnsignedShort();
        if (!isFileVersionSupported(m_nMajorVer, m_nMinorVer))
            {
            throw new IOException("unsupported version: " + m_nMajorVer + "." + m_nMinorVer);
            }

        // read in the constant pool
        ConstantPool pool = new ConstantPool(this);
        m_pool = pool;
        pool.disassemble(in);

        m_sModuleName = ((ModuleConstant) pool.getConstant(readIndex(in))).getName();
        disassembleChildren(in, m_fLazyDeser);

        // must be at least one module (the first module is considered to be the "main" module)
        if (getModule() == null)
            {
            throw new IOException("the file does not contain a primary module");
            }
        }

    /**
     * Re-registers all referenced constants with the pool.
     */
    public void reregisterConstants(boolean fOptimize)
        {
        ConstantPool pool = m_pool;
        pool.preRegisterAll();
        registerConstants(pool);
        pool.postRegisterAll(fOptimize);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        pool.registerConstants(pool);
        registerChildrenConstants(pool);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeInt(FILE_MAGIC);
        out.writeShort(VERSION_MAJOR_CUR);
        out.writeShort(VERSION_MINOR_CUR);
        m_pool.assemble(out);
        writePackedLong(out, getModule().getIdentityConstant().getPosition());
        assembleChildren(out);
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();

        String sName = getModuleName();
        sb.append("main-module=")
          .append(sName);

        boolean first = true;
        for (String name : moduleNames())
            {
            if (!name.equals(sName))
                {
                if (first)
                    {
                    sb.append(", other-modules={");
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(name);
                }
            }
        if (!first)
            {
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
    protected void dump(PrintWriter out, String sIndent)
        {
        out.print(sIndent);
        out.println(toString());

        final ConstantPool pool = m_pool;
        if (pool != null)
            {
            pool.dump(out, nextIndent(sIndent));
            }

        dumpChildren(out, sIndent);
        }

    /**
     * For debugging only: ensure that all constants for all children belong to the same pool.
     */
    public boolean validateConstants()
        {
        assert m_pool.getNakedRefType() != null;

        Consumer<Component> visitor = component ->
            {
            if (component instanceof ClassStructure)
                {
                component.getContributionsAsList().forEach(contrib ->
                    {
                    assert contrib.getTypeConstant().getConstantPool() == m_pool;
                    });
                }
            else if (component instanceof MethodStructure)
                {
                MethodStructure method = (MethodStructure) component;
                Constant[]      aconst = method.getLocalConstants();
                if (aconst != null)
                    {
                    for (Constant constant : aconst)
                        {
                        assert constant.getConstantPool() == m_pool;
                        }
                    }
                }
            };
        visitChildren(visitor, false, true);
        return true;
        }

    @Override
    public ErrorListener getErrorListener()
        {
        return m_errs == null ? ErrorListener.RUNTIME : m_errs;
        }

    @Override
    public void setErrorListener(ErrorListener errs)
        {
        m_errs = errs;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return getModule().hashCode();
        }

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (obj instanceof FileStructure)
            {
            FileStructure that = (FileStructure) obj;
            // ignore the constant pool, since its only purpose is to be
            // referenced from the nested XVM structures
            return this.m_nMajorVer == that.m_nMajorVer
                    && this.m_nMinorVer == that.m_nMinorVer
                    && this.m_sModuleName.equals(that.m_sModuleName)
                    && this.getChildByNameMap().equals(
                       that.getChildByNameMap()); // TODO need "childrenEquals()"?
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
     * The name of the main module that the FileStructure represents. There may be additional
     * modules in the FileStructure, but generally, they only represent imports (included embedded
     * modules) of the main module.
     */
    private String m_sModuleName;

    /**
     * Tree of versions held by this FileStructure.
     * <ul>
     * <li>If the tree is empty, that indicates that the module structure does not contain version
     * information (the module information in the module structure is not version labeled.)</li>
     * <li>If the tree contains one version, that indicates that the module structure contains a
     * single version label, i.e. there is a single version of the module inside of the module
     * structure.</li>
     * <li>If the tree contains more than one version, that indicates that the module structure
     * contains multiple different versions of the module, and must be resolved in order to link the
     * module.</li>
     * </ul>
     */
    private VersionTree<Boolean> m_vtree = new VersionTree<>();

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
