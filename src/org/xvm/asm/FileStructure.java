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

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.constants.ModuleConstant;

import org.xvm.util.ListMap;

import static org.xvm.util.Handy.intToHexString;
import static org.xvm.util.Handy.toInputStream;


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

        // validate the combination of passed names
        if (sModule == null)
            {
            throw new IllegalArgumentException("module name required");
            }

        this.pool       = new ConstantPool(this);
        this.module     = new ModuleStructure(this, pool.ensureModuleConstant(sModule));
        this.nMajorVer  = VERSION_MAJOR_CUR;
        this.nMinorVer  = VERSION_MINOR_CUR;

        // TODO
        this.modulesByName.put(sModule, module);
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
        this(toInputStream(file), true);
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
        this(in, false);
        }

    /**
     * Construct a file structure for an existing file.
     *
     * @param in          a stream that contains a FileStructure
     * @param fAutoClose  true to close the stream; false to leave the stream open
     *
     * @throws IOException  if an IOException occurs while reading the FileStructure
     */
    public FileStructure(InputStream in, boolean fAutoClose)
            throws IOException
        {
        super(null);

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
     * Obtain the main module that the FileStructure represents.
     *
     * @return the ModuleStructure that this FileStructure represents; never null
     */
    public ModuleStructure getMainModule()
        {
        return module;
        }

    /**
     * Obtain the ModuleConstant for the main module in this FileStructure.
     *
     * @return the ModuleConstant; never null
     */
    ModuleConstant getModuleConstant()
        {
        return module.getModuleConstant();
        }

    /**
     * Determine the Module name.
     *
     * @return the name of the Module
     */
    public String getModuleName()
        {
        return getModuleConstant().getQualifiedName();
        }

    /**
     * @return a set of qualified module names contained within this FileStructure; the caller must
     *         treat the set as a read-only object
     */
    public Set<String> moduleNames()
        {
        Set<String> names = modulesByName.keySet();
        // if assertions are enabled, wrap it as unmodifiable
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
    ModuleStructure getModule(String sName)
        {
        return modulesByName.get(sName);
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
            modulesByName.put(module.getModuleConstant().getQualifiedName(), module);
            }
        return module;
        }

    /**
     * Remove the specified module from the FileStructure.
     *
     * @param sName  the qualified module name
     */
    void removeModule(String sName)
        {
        modulesByName.remove(sName);
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
        return Arrays.asList(pool, module).iterator();
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
            throw new IOException("invalid magic header: " + intToHexString(nMagic));
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

        // read in the modules
        List<ModuleStructure> modules = (List<ModuleStructure>) disassembleSubStructureCollection(in);
        Map<String, ModuleStructure> modulesByName = this.modulesByName;
        modulesByName.clear();
        module = null;
        for (ModuleStructure struct : modules)
            {
            modulesByName.put(struct.getModuleConstant().getQualifiedName(), struct);
            if (module == null)
                {
                module = struct;
                }
            }

        // must be at least one module (the first module is considered to be the "main" module)
        if (module == null)
            {
            throw new IOException("the file does not contain a module");
            }
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeInt(FILE_MAGIC);
        out.writeShort(VERSION_MAJOR_CUR);
        out.writeShort(VERSION_MINOR_CUR);
        pool.assemble(out);
        assembleSubStructureCollection(modulesByName.values(), out);
        }

    @Override
    public String getDescription()
        {
        return new StringBuilder()
                .append("module=")
                .append(getModuleName())
                .append(", xvm-version=")
                .append(getFileMajorVersion())
                .append('.')
                .append(getFileMinorVersion())
                .append(", ")
                .append(super.getDescription())
                .toString();
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

        dumpStructureMap(out, sIndent, "Modules", modulesByName);
        }


    // ----- Object methods ----------------------------------------------------

    @Override
    public int hashCode()
        {
        return module.hashCode();
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
            if (this.nMajorVer == that.nMajorVer &&
                this.nMinorVer == that.nMinorVer &&
                this.module.equals(that.module))
                {
                boolean first = true;
                for (ModuleStructure moduleThis : this.modulesByName.values())
                    {
                    // the first module is the "main" module, and we already compared the "main"
                    // module
                    if (first)
                        {
                        first = false;
                        }
                    else
                        {
                        ModuleStructure moduleThat = that.getModule(moduleThis.getModuleConstant().getQualifiedName());
                        if (moduleThat == null || !moduleThis.equals(moduleThat))
                            {
                            return false;
                            }
                        }
                    }
                return true;
                }
            }

        return false;
        }

    @Override
    public String toString()
        {
        return "FileStructure{" + getDescription() + "}";
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The ConstantPool for the FileStructure.
     */
    private ConstantPool pool;

    /**
     * The main module that the FileStructure represents. There may be additional modules in the
     * FileStructure, but generally, they only represent imports (included embedded modules) of the
     * main module.
     */
    private ModuleStructure module;

    /**
     * The map of module names to modules. The first module in the map is the main module.
     */
    private Map<String, ModuleStructure> modulesByName = new ListMap<>();

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

    /**
     * The AssemblerContext that is associated with this FileStructure.
     */
    private AssemblerContext ctx;
    }
