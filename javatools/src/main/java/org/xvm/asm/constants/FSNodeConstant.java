package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.nio.file.attribute.FileTime;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.runtime.ObjectHandle;

import org.xvm.util.Handy;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent an entire filing system directory stored in the constant pool.
 */
public class FSNodeConstant
        extends ValueConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a filing system directory.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param sName       the name of the directory
     * @param ftCreated   the date/time value that the directory was created
     * @param ftModified  the date/time value that the directory was last modified
     * @param aFSNodes    the contents of the directory
     */
    public FSNodeConstant(
            ConstantPool     pool,
            String           sName,
            FileTime         ftCreated,
            FileTime         ftModified,
            FSNodeConstant[] aFSNodes)
        {
        super(pool);

        if (sName == null)
            {
            throw new IllegalArgumentException("name required");
            }
        Handy.checkElementsNonNull(aFSNodes);

        m_fmt           = Format.FSDir;
        m_constName     = pool.ensureStringConstant(sName);
        m_constCreated  = pool.ensureTimeConstant(ftCreated);
        m_constModified = pool.ensureTimeConstant(ftModified);
        m_constData     = pool.ensureArrayConstant(pool.ensureArrayType(pool.typeFileNode()), aFSNodes);
        }

    /**
     * Construct a constant whose value is a filing system file.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param sName       the name of the file
     * @param ftCreated   the date/time value that the file was created
     * @param ftModified  the date/time value that the file was last modified
     * @param ab          the bytes in the file
     */
    public FSNodeConstant(
            ConstantPool pool,
            String       sName,
            FileTime     ftCreated,
            FileTime     ftModified,
            byte[]       ab)
        {
        super(pool);

        if (sName == null)
            {
            throw new IllegalArgumentException("name required");
            }
        if (ab == null)
            {
            throw new IllegalArgumentException("file contents required");
            }

        m_fmt           = Format.FSFile;
        m_constName     = pool.ensureStringConstant(sName);
        m_constCreated  = pool.ensureTimeConstant(ftCreated);
        m_constModified = pool.ensureTimeConstant(ftModified);
        m_constData     = pool.ensureByteStringConstant(ab);
        }

    /**
     * Construct a constant whose value is a link to another filing system node.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param sName        the name of the link file
     * @param ftCreated    the date/time value that the link was created
     * @param ftModified   the date/time value that the link was last modified
     * @param constLinkTo  the value of the constant
     */
    public FSNodeConstant(
            ConstantPool   pool,
            String         sName,
            FileTime       ftCreated,
            FileTime       ftModified,
            FSNodeConstant constLinkTo)
        {
        super(pool);

        if (sName == null)
            {
            throw new IllegalArgumentException("name required");
            }
        if (constLinkTo == null)
            {
            throw new IllegalArgumentException("link-to node required");
            }

        m_fmt           = Format.FSLink;
        m_constName     = pool.ensureStringConstant(sName);
        m_constCreated  = pool.ensureTimeConstant(ftCreated);
        m_constModified = pool.ensureTimeConstant(ftModified);
        m_constData     = constLinkTo;
        }

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public FSNodeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_fmt       = format;
        m_iName     = readMagnitude(in);
        m_iCreated  = readMagnitude(in);
        m_iModified = readMagnitude(in);
        m_iData     = readMagnitude(in);
        }

    @Override
    protected void resolveConstants()
        {
        ConstantPool pool = getConstantPool();

        m_constName     = (StringConstant ) pool.getConstant(m_iName    );
        m_constCreated  = (LiteralConstant) pool.getConstant(m_iCreated );
        m_constModified = (LiteralConstant) pool.getConstant(m_iModified);
        m_constData     =                   pool.getConstant(m_iData    );
        }


    // ----- FSNodeConstant methods ----------------------------------------------------------------

    public StringConstant getNameConstant()
        {
        return m_constName;
        }

    public String getName()
        {
        return getNameConstant().getValue();
        }

    public LiteralConstant getPathConstant()
        {
        LiteralConstant constPath = m_constPath;
        if (m_constPath == null)
            {
            constPath = m_constPath =
                new LiteralConstant(getConstantPool(), Format.Path, getName(), getName());
            }
        return constPath;
        }

    public String getPath()
        {
        return getPathConstant().getValue();
        }

    public LiteralConstant getCreatedConstant()
        {
        return m_constCreated;
        }

    public String getCreated()
        {
        return getCreatedConstant().getValue();
        }

    public LiteralConstant getModifiedConstant()
        {
        return m_constModified;
        }

    public String getModified()
        {
        return getModifiedConstant().getValue();
        }

    public FSNodeConstant[] getDirectoryContents()
        {
        assert m_fmt == Format.FSDir;

        Constant[]       aConst  = ((ArrayConstant) m_constData).getValue();
        int              cConsts = aConst.length;
        FSNodeConstant[] aFSNode = new FSNodeConstant[cConsts];
        for (int i = 0; i < cConsts; ++i)
            {
            aFSNode[i] = (FSNodeConstant) aConst[i];
            }
        return aFSNode;
        }

    public byte[] getFileBytes()
        {
        assert m_fmt == Format.FSFile;

        return ((UInt8ArrayConstant) m_constData).getValue();
        }

    public FSNodeConstant getLinkTarget()
        {
        assert m_fmt == Format.FSLink;
        return (FSNodeConstant) m_constData;
        }


    // ----- run-time support  ---------------------------------------------------------------------

    /**
     * @return an ObjectHandle representing this singleton value
     */
    public ObjectHandle getHandle()
        {
        return m_handle;
        }

    /**
     * Set the handle for this singleton's value.
     *
     * @param handle  the corresponding handle
     */
    public void setHandle(ObjectHandle handle)
        {
        assert handle != null;
        assert m_handle == null;

        m_handle = handle;
        }


    // ----- ValueConstant methods -----------------------------------------------------------------

    @Override
    public TypeConstant getType()
        {
        ConstantPool pool = getConstantPool();
        switch (m_fmt)
            {
            case FSDir:
                return pool.ensureEcstasyTypeConstant("fs.Directory");
            case FSFile:
            case FSLink:
                return pool.ensureEcstasyTypeConstant("fs.File");
            default:
                throw new IllegalStateException();
            }
        }

    @Override
    public Constant getValue()
        {
        return m_constData;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return m_fmt;
        }

    @Override
    public boolean containsUnresolved()
        {
        return     m_constName    .containsUnresolved()
                || m_constCreated .containsUnresolved()
                || m_constModified.containsUnresolved()
                || m_constData    .containsUnresolved();
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constName    );
        visitor.accept(m_constCreated );
        visitor.accept(m_constModified);
        visitor.accept(m_constData    );
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (this.getFormat() != that.getFormat())
            {
            return this.getFormat().ordinal() - that.getFormat().ordinal();
            }

        FSNodeConstant nodeThat = (FSNodeConstant) that;

        int nResult = this.m_constName.compareTo(nodeThat.m_constName);
        if (nResult == 0)
            {
            nResult = this.m_constCreated.compareTo(nodeThat.m_constCreated);
            if (nResult == 0)
                {
                nResult = this.m_constModified.compareTo(nodeThat.m_constModified);
                if (nResult == 0)
                    {
                    nResult = this.m_constData.compareTo(nodeThat.m_constData);
                    }
                }
            }

        return nResult;
        }

    @Override
    public String getValueString()
        {
        switch (m_fmt)
            {
            case FSDir:
                return "./" + m_constName.getValue() + "/";
            case FSFile:
                return "./" + m_constName.getValue();
            case FSLink:
                return "./" + m_constName.getValue() + " -> " + m_constData.getValueString();
            default:
                throw new IllegalStateException();
            }
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constName     = (StringConstant ) pool.register(m_constName    );
        m_constCreated  = (LiteralConstant) pool.register(m_constCreated );
        m_constModified = (LiteralConstant) pool.register(m_constModified);
        m_constData     =                   pool.register(m_constData    );
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constName    .getPosition());
        writePackedLong(out, m_constCreated .getPosition());
        writePackedLong(out, m_constModified.getPosition());
        writePackedLong(out, m_constData    .getPosition());
        }

    @Override
    public String getDescription()
        {
        switch (m_fmt)
            {
            case FSDir:
                return "directory:" + m_constName.getValue();
            case FSFile:
                return "file:" + m_constName.getValue();
            case FSLink:
                return "link:" + m_constName.getValue();
            default:
                throw new IllegalStateException();
            }
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        int nHash = m_nHash;
        if (nHash == 0)
            {
            nHash =   m_constName    .hashCode()
                    ^ m_constCreated .hashCode()
                    ^ m_constModified.hashCode()
                    ^ m_constData    .hashCode();
            m_nHash = nHash;
            }
        return nHash;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * An immutable empty array of FSNodeConstants.
     */
    public static final FSNodeConstant[] NO_NODES = new FSNodeConstant[0];

    /**
     * The format of this constant.
     */
    private final Format m_fmt;

    /**
     * Holds the index of the node name during deserialization.
     */
    private transient int m_iName;

    /**
     * Holds the index of the node creation date/time constant during deserialization.
     */
    private transient int m_iCreated;

    /**
     * Holds the index of the node modification date/time constant during deserialization.
     */
    private transient int m_iModified;

    /**
     * Holds the index of the node data constant (array for dir, node for link, bytes for file)
     * during deserialization.
     */
    private transient int m_iData;

    /**
     * The name of the node.
     */
    private StringConstant m_constName;

    /**
     * The path specified for the node, or null if this node was not specified by path.
     */
    private LiteralConstant m_constPath;

    /**
     * The date/time that the node was created.
     */
    private LiteralConstant m_constCreated;

    /**
     * The date/time that the node was modified.
     */
    private LiteralConstant m_constModified;

    /**
     * The contents of the node:
     * <ul><li>
     * For an <b>FSDir</b> node, this is an ArrayConstant of FSNodeConstant values;
     * </li><li>
     * For an <b>FSFile</b> node, this is an UInt8ArrayConstant containing the file bytes;
     * </li><li>
     * For an <b>FSLink</b> node, this is an FSNodeConstant value that is linked to.
     * </li></ul>
     */
    private Constant m_constData;

    /**
     * Cached hash code.
     */
    private transient int m_nHash;

    /**
     * The ObjectHandle representing this singleton's value.
     */
    private transient ObjectHandle m_handle;
    }