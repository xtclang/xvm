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
import java.util.Iterator;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.PackageConstant;

import static org.xvm.util.Handy.intToHexString;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.toInputStream;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A representation of the file structure that contains an XVM module, package,
 * or class.
 *
 * @author cp 2015.12.04
 */
public class FileStructure
        extends StructureContainer
    {
    // ----- constructors ------------------------------------------------------

    /**
     * Construct a file structure that will contain a module, package, or class.
     *
     * @param sModule   the fully qualified module name
     * @param sPackage  the dot-delimited package name, or null if the
     *                  FileStructure contains a module
     * @param sClass    the class name, or null if the FileStructure contains a
     *                  module or a package
     */
    public FileStructure(String sModule, String sPackage, String sClass)
        {
        super(null);

        // validate the combination of passed names
        if (sModule == null)
            {
            throw new IllegalArgumentException("module name required");
            }

        if (sPackage == null && sClass != null)
            {
            throw new IllegalArgumentException("package name required if class name is present");
            }

        // register the passed names
        final XvmStructure xstop;
        final ConstantPool pool = new ConstantPool(this);
        final ModuleConstant constmodule = pool.ensureModuleConstant(sModule);
        if (sPackage == null)
            {
            xstop = new ModuleStructure(this, constmodule);
            }
        else
            {
            final PackageConstant constpackage = pool.ensurePackageConstant(constmodule, sPackage);
            xstop = sClass == null
                    ? new PackageStructure(this, constpackage)
                    : new ClassStructure(this, pool.ensureClassConstant(constpackage, sClass));
            }

        m_pool          = pool;
        m_xsTop         = xstop;
        m_nVerMajor     = VERSION_MAJOR_CUR;
        m_nVerMinor     = VERSION_MINOR_CUR;
        }

    /**
     * Construct a file structure for an existing file.
     *
     * @param file  the file that contains the existing FileStructure
     *
     * @throws IOException  if an IOException occurs while reading the
     *         FileStructure
     */
    public FileStructure(File file)
            throws IOException
        {
        this(toInputStream(file), true);
        }

    /**
     * Construct a file structure for an existing file. Note that the stream is
     * not closed by the constructor.
     *
     * @param in  a stream that contains a FileStructure
     *
     * @throws IOException  if an IOException occurs while reading the
     *         FileStructure
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
     * @param fAutoClose  true to close the stream; false to leave the stream
     *                    open
     *
     * @throws IOException  if an IOException occurs while reading the
     *         FileStructure
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


    // ----- serialization -----------------------------------------------------

    /**
     * Write the FileStructure to the specified file.
     *
     * @param file  the file to write to
     *
     * @throws IOException  if an IOException occurs while writing the
     *         FileStructure
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
     * @throws IOException  if an IOException occurs while writing the
     *         FileStructure
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
     * @throws IOException  if an IOException occurs while writing the
     *         FileStructure
     */
    public void writeTo(DataOutput out)
            throws IOException
        {
        ConstantPool pool = m_pool;
        pool.preRegisterAll();
        registerConstants(pool);
        pool.postRegisterAll(true);

        assemble(out);
        resetModified();
        }


    // ----- XvmStructure methods ----------------------------------------------

    @Override
    public FileStructure getFileStructure()
        {
        return this;
        }

    @Override
    protected ConstantPool getConstantPool()
        {
        return m_pool;
        }

    @Override
    public Iterator<? extends XvmStructure> getContained()
        {
        return Arrays.asList(m_pool, m_xsTop).iterator();
        }

    @Override
    protected boolean isModifiable()
        {
        return true;
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

        // validate the version; this is rather simple in the beginning, as
        // versions will initially be ascending-only, but at some point, only
        // up to specific minor versions of specific major versions are likely
        // to be supported
        int nMajorVer = in.readUnsignedShort();
        int nMinorVer = in.readUnsignedShort();
        if (!isVersionSupported(nMajorVer, nMinorVer))
            {
            throw new IOException("unsupported version: " + nMajorVer + "." + nMinorVer);
            }

        // read in the identity of this file structure
        int nId = readMagnitude(in);

        // read in the constant pool
        ConstantPool pool = new ConstantPool(this);
        m_pool = pool;
        pool.disassemble(in);

        // figure out what type of structure is in the file
        Constant     constTop = pool.getConstant(nId);
        XvmStructure xsTop;
        switch (constTop.getFormat())
            {
            case Module:
            case Package:
            case Class:
                xsTop = constTop.instantiate(this);
                break;

            default:
                throw new IOException("illegal format: file structure identity is not a module, package, or class");
            }

        // read in the top-most structure (which recursively disassembles)
        m_xsTop = xsTop;
        xsTop.disassemble(in);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeInt(FILE_MAGIC);
        out.writeShort(VERSION_MAJOR_CUR);
        out.writeShort(VERSION_MINOR_CUR);
        XvmStructure xsTop = getTopmostStructure();
        writePackedLong(out, xsTop.getIdentityConstant().getPosition());
        m_pool.assemble(out);
        xsTop.assemble(out);
        }

    @Override
    public String getDescription()
        {
        final XvmStructure xsTop = m_xsTop;

        StringBuilder sb = new StringBuilder();
        return sb.append("top-id=")
          .append(xsTop == null ? "<no-top-structure>" : xsTop.getIdentityConstant())
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

        final ConstantPool pool = m_pool;
        if (pool != null)
            {
            pool.dump(out, nextIndent(sIndent));
            }

        final XvmStructure xsTop = m_xsTop;
        if (xsTop != null)
            {
            xsTop.dump(out, nextIndent(sIndent));
            }
        }


    // ----- Object methods ----------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_xsTop.getIdentityConstant().hashCode();
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
            return  this.m_nVerMajor == that.m_nVerMajor &&
                    this.m_nVerMinor == that.m_nVerMinor &&
                    this.m_xsTop.equals(that.m_xsTop);
            }

        return false;
        }

    @Override
    public String toString()
        {
        return "FileStructure{" + getDescription() + "}";
        }


    // ----- accessors ---------------------------------------------------------

    /**
     * Obtain the AssemblerContext that is used to specify conditional sections
     * during creation of the hierarchical XVM structures.
     *
     * @return the AssemblerContext, or null if none has been created
     */
    public AssemblerContext getContext()
        {
        return m_ctx;
        }

    /**
     * Obtain the AssemblerContext that is used to specify conditional sections
     * during creation of the hierarchical XVM structures.
     *
     * @return the AssemblerContext, creating one if ncessary
     */
    protected AssemblerContext ensureContext()
        {
        AssemblerContext ctx = m_ctx;
        if (ctx == null)
            {
            m_ctx = ctx = new AssemblerContext(getConstantPool());
            }
        return ctx;
        }

    /**
     * Obtain the XvmStructure that the FileStructure represents. The
     * FileStructure represents a Module, a Package, or a Class.
     * <p>
     * The TopmostStructure will be an instance of:
     * <ul>
     * <li> ModuleStructure iff {@link #isModule()} returns true.</li>
     * <li> PackageStructure iff {@link #isPackage()} returns true.</li>
     * <li> ClassStructure iff {@link #isClass()} returns true.</li>
     * </ul>
     *
     * @return the ModuleStructure, PackageStructure, or ClassStructure that
     *         this FileStructure represents; never null
     */
    public XvmStructure getTopmostStructure()
        {
        return m_xsTop;
        }

    /**
     * Determine if the FileStructure represents a Module.
     * 
     * @return true iff the FileStructure represents a Module (not a Package or
     *         a Class)
     */
    public boolean isModule()
        {
        return m_xsTop.getIdentityConstant() instanceof ModuleConstant;
        }

    /**
     * Determine if the FileStructure represents a Package.
     *
     * @return true iff the FileStructure represents a Package (not a Module or
     *         a Class)
     */
    public boolean isPackage()
        {
        return m_xsTop.getIdentityConstant() instanceof PackageConstant;
        }

    /**
     * Determine if the FileStructure represents a Class.
     *
     * @return true iff the FileStructure represents a Class (not a Package or a
     *         Module)
     */
    public boolean isClass()
        {
        return m_xsTop.getIdentityConstant() instanceof ClassConstant;
        }

    /**
     * Obtain the ModuleConstant for the Module, Package, or Class represented
     * by this FileStructure.
     *
     * @return the ModuleConstant; never null
     */
    public ModuleConstant getModuleConstant()
        {
        Constant constant = m_xsTop.getIdentityConstant();
        if (constant instanceof ClassConstant)
            {
            constant = ((ClassConstant) constant).getNamespace();
            }
        while (constant instanceof PackageConstant)
            {
            constant = ((PackageConstant) constant).getNamespace();
            }
        if (constant instanceof ModuleConstant)
            {
            return (ModuleConstant) constant;
            }
        throw new IllegalStateException("top-most structure is not a module, package, or global class");
        }

    /**
     * If the FileStructure represents a Package or a Class, obtain the
     * PackageConstant for the class.
     *
     * @return the PackageConstant for the Package represented by the
     *         FileStructure, or null if the FileStructure represents a Module
     */
    public PackageConstant getPackageConstant()
        {
        Constant constant = m_xsTop.getIdentityConstant();
        if (constant instanceof ModuleConstant)
            {
            return null;
            }
        if (constant instanceof ClassConstant)
            {
            constant = ((ClassConstant) constant).getNamespace();
            }
        if (constant instanceof PackageConstant)
            {
            return (PackageConstant) constant;
            }
        throw new IllegalStateException("top-most structure is not a module, package, or global class");
        }

    /**
     * If the FileStructure represents a Class, obtain the ClassConstant for
     * the class.
     *
     * @return the ClassConstant for the Class represented by the FileStructure,
     *         or null if the FileStructure represents a Module or Package
     */
    public ClassConstant getClassConstant()
        {
        Constant constant = m_xsTop.getIdentityConstant();
        return constant instanceof ClassConstant
                ? (ClassConstant) constant : null;
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
     * If the FileStructure represents a Package or a Class, determine the
     * Package name.
     *
     * @return the name of the Package, or null if the FileStructure represents
     *         a Module
     */
    public String getPackageName()
        {
        return isModule() ? null : getPackageConstant().getName();
        }


    /**
     * If the FileStructure represents a Class, determine the name of the Class
     * represented by the FileStructure.
     *
     * @return the name of the Class, or null if the FileStructure represents
     *         a Module or a Package
     */
    public String getClassName()
        {
        return isClass() ? getClassConstant().getName() : null;
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
        return m_nVerMajor;
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
        return m_nVerMinor;
        }

    /**
     * Determine the current major version of the XVM specification and related
     * tool-chain that this implementation supports.
     *
     * @return the current major version of the XVM specification and related
     *         tool-chain that this implementation supports
     */
    public static int getToolMajorVersion()
        {
        return VERSION_MAJOR_CUR;
        }

    /**
     * Determine the current minor version of the XVM specification and related
     * tool-chain that this implementation supports.
     *
     * @return the current minor version of the XVM specification and related
     *         tool-chain that this implementation supports
     */
    public static int getToolMinorVersion()
        {
        return VERSION_MINOR_CUR;
        }

    /**
     * Determine if the specified version of an XVM file structure is supported
     * by this version of the XVM assembler.
     *
     * @param nVerMajor  major version number
     * @param nVerMinor  minor version number
     *
     * @return true if this version of the XVM assembler can correctly read in
     *         and deal with a file structure of the specified version
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

        // NOTE: this is where specific version number backwards compatibility
        // checks will be added

        return false;
        }


    // ----- data members ------------------------------------------------------

    /**
     * The ConstantPool for the FileStructure.
     */
    private ConstantPool m_pool;

    /**
     * The "top-most" XVM structure in the FileStructure; one of:
     * ModuleStructure, PackageStructure, or ClassStructure.
     */
    private XvmStructure m_xsTop;

    /**
     * If the structure was disassembled from a binary, this is the major
     * version of the xtc/xvm specification that the binary was assembled with.
     */
    private int m_nVerMajor;

    /**
     * If the structure was disassembled from a binary, this is the minor
     * version of the xtc/xvm specification that the binary was assembled with.
     */
    private int m_nVerMinor;

    /**
     * The AssemblerContext that is associated with this FileStructure.
     */
    private AssemblerContext m_ctx;
    }
