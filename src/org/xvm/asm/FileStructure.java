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
 *
 * @author cp 2015.12.04
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
        addChild(module);

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
        super(null);

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
        ConstantPool pool = this.pool;
        pool.preRegisterAll();
        registerConstants(pool);
        pool.postRegisterAll(true);

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
        return (ModuleStructure) getChild(sName);
        }

    /**
     * Obtain the specified module from the FileStructure, creating it if it does not already
     * exist in the FileStructure.
     *
     * @param sName  the qualified module name
     *
     * @return the specified module
     */
    ModuleStructure ensureModule(String sName)
        {
        ModuleStructure module = getModule(sName);
        if (module == null)
            {
            module = new ModuleStructure(this, pool.ensureModuleConstant(sName));
            addChild(module);
            }
        return module;
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
     * Determine the major version of the XVM specification and related
     * tool-chain that this particular FileStructure corresponds to.
     *
     * @return the major version of the binary form that this FileStructure was
     *         constructed from, or the current major version if this
     *         FileStructure was constructed from scratch
     */
    public int getFileMajorVersion()
        {
        return nMajorVer;
        }

    /**
     * Determine the minor version of the XVM specification and related
     * tool-chain that this particular FileStructure corresponds to.
     *
     * @return the minor version of the binary form that this FileStructure was
     *         constructed from, or the current minor version if this
     *         FileStructure was constructed from scratch
     */
    public int getFileMinorVersion()
        {
        return nMinorVer;
        }

    /**
     * Determine the current major version of the XVM specification and related tool-chain that this
     * implementation supports.
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
     *
     * @param nVerMajor  major version number
     * @param nVerMinor  minor version number
     *
     * @return true if this version of the XVM assembler can correctly read in and deal with a file
     *         structure of the specified version
     */
    public static boolean isVersionSupported(int nVerMajor, int nVerMinor)
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
                ? getModule().getModuleConstant().getUnqualifiedName() + ".xtc"
                : file.getName();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public FileStructure getFileStructure()
        {
        return this;
        }

    @Override
    protected ConstantPool getConstantPool()
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
            throw new IOException("not an .xtc format file; invalid magic header: " + intToHexString(nMagic));
            }

        // validate the version; this is rather simple in the beginning (before there is a long
        // history of evolution of the Ecstasy language and the XVM specification), as versions will
        // initially be ascending-only, but at some point, only up-to-specific-minor-versions of
        // specific-major-versions are likely to be supported
        nMajorVer = in.readUnsignedShort();
        nMinorVer = in.readUnsignedShort();
        if (!isVersionSupported(nMajorVer, nMinorVer))
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
        writePackedLong(out, getModule().getModuleConstant().getPosition());
        assembleChildren(out);
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
                    sb.append("other-modules={");
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


    // ----- Object methods ----------------------------------------------------

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
                && this.getChildByNameMap().equals(that.getChildByNameMap()); // TODO need "childrenEquals()"?
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
    }
