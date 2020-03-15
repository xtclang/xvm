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

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import org.xvm.asm.constants.ModuleConstant;

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

        this.pool       = pool;
        this.moduleName = sModule;
        this.nMajorVer  = VERSION_MAJOR_CUR;
        this.nMinorVer  = VERSION_MINOR_CUR;
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
        this.file = file;
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

        fLazyDeser = fLazy;
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
        nMajorVer  = fileStructure.nMajorVer;
        nMinorVer  = fileStructure.nMinorVer;
        pool       = new ConstantPool(this);

        merge(module);
        }

    /**
     * Merge the specified module into this FileStructure.
     *
     * @param module  the module to merge
     */
    public void merge(ModuleStructure module)
        {
        moduleName = module.getName();

        ModuleStructure moduleClone = module.cloneBody();
        addChild(moduleClone);
        moduleClone.setContaining(this);

        ConstantPool pool = this.getConstantPool();

        moduleClone.cloneChildren(module.children());

        moduleClone.registerConstants(pool);
        moduleClone.registerChildrenConstants(pool);

        pool.setNakedRefType(module.getConstantPool().getNakedRefType());
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
        this.file = file;
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
        return (ModuleStructure) getChild(moduleName);
        }

    /**
     * Determine the Module name.
     *
     * @return the name of the Module
     */
    public String getModuleName()
        {
        return moduleName;
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
            module = new ModuleStructure(this, pool.ensureModuleConstant(sName));
            addChild(module);
            }
        return module;
        }

    /**
     * Link the modules in this FileStructure.
     *
     * @param repository  the module repository to load modules from
     */
    public void linkModules(ModuleRepository repository)
        {
        for (String sModule : moduleNames())
            {
            if (!sModule.equals(getModuleName()))
                {
                ModuleStructure structFingerprint = getModule(sModule);
                assert structFingerprint.isFingerprint();
                assert structFingerprint.getFingerprintOrigin() == null;

                // load the module against which the compilation will occur
                if (!repository.getModuleNames().contains(sModule))
                    {
                    // no error is logged here; the package that imports the module will detect
                    // the error when it is asked to resolve global names; see
                    // TypeCompositionStatement
                    continue;
                    }

                ModuleStructure structActual = repository.loadModule(sModule); // TODO versions etc.
                structFingerprint.setFingerprintOrigin(structActual);
                }
            }
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
        return ctx;
        }

    /**
     * Obtain the AssemblerContext that is used to specify conditional sections during creation of
     * the hierarchical XVM structures.
     *
     * @return the AssemblerContext, creating one if necessary
     */
    protected AssemblerContext ensureContext()
        {
        AssemblerContext ctx = this.ctx;
        if (ctx == null)
            {
            this.ctx = ctx = new AssemblerContext(getConstantPool());
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
        return nMajorVer;
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
        return nMinorVer;
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
        return file == null
                ? getModule().getIdentityConstant().getUnqualifiedName() + ".xtc"
                : file.getName();
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
        return pool;
        }

    @Override
    public Iterator<? extends XvmStructure> getContained()
        {
        return new LinkedIterator(
                Collections.singleton(pool).iterator(),
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
        nMajorVer = in.readUnsignedShort();
        nMinorVer = in.readUnsignedShort();
        if (!isFileVersionSupported(nMajorVer, nMinorVer))
            {
            throw new IOException("unsupported version: " + nMajorVer + "." + nMinorVer);
            }

        // read in the constant pool
        ConstantPool pool = new ConstantPool(this);
        this.pool = pool;
        pool.disassemble(in);

        moduleName = ((ModuleConstant) pool.getConstant(readIndex(in))).getName();
        disassembleChildren(in, fLazyDeser);

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
        ConstantPool pool = this.pool;
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
        pool.assemble(out);
        writePackedLong(out, getModule().getIdentityConstant().getPosition());
        assembleChildren(out);
        }

    @Override
    public boolean validate(ErrorListener errlist)
        {
        boolean fAbort = super.validate(errlist);
        if (!fAbort && !errlist.isAbortDesired())
            {
            fAbort |= getConstantPool().postValidate(errlist) | errlist.isAbortDesired();
            }
        return fAbort;
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("main-module=")
                .append(getModuleName());

        boolean first = true;
        for (String name : moduleNames())
            {
            if (!name.equals(moduleName))
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

        final ConstantPool pool = this.pool;
        if (pool != null)
            {
            pool.dump(out, nextIndent(sIndent));
            }

        dumpChildren(out, sIndent);
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
            return this.nMajorVer == that.nMajorVer
                    && this.nMinorVer == that.nMinorVer
                    && this.moduleName.equals(that.moduleName)
                    && this.getChildByNameMap().equals(
                    that.getChildByNameMap()); // TODO need "childrenEquals()"?
            }

        return false;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The file that the file structure was loaded from.
     */
    private File file;

    /**
     * The indicator that deserialization of components should be done lazily (deferred).
     */
    private boolean fLazyDeser;

    /**
     * The name of the main module that the FileStructure represents. There may be additional
     * modules in the FileStructure, but generally, they only represent imports (included embedded
     * modules) of the main module.
     */
    private String moduleName;

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
    private ConstantPool pool;

    /**
     * The AssemblerContext that is associated with this FileStructure.
     */
    private AssemblerContext ctx;

    /**
     * If the structure was disassembled from a binary, this is the major version of the Ecstasy/XVM
     * specification that the binary was assembled with. Otherwise, it is the current version.
     */
    private int nMajorVer;

    /**
     * If the structure was disassembled from a binary, this is the minor version of the Ecstasy/XVM
     * specification that the binary was assembled with. Otherwise, it is the current version.
     */
    private int nMinorVer;

    private transient ErrorListener m_errs;
    }
