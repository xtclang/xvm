package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.runtime.ObjectHandle;
import org.xvm.util.Hash;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a FileStore that was embedded into the constant pool.
 */
public class FileStoreConstant
        extends ValueConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a FileStore.
     *
     * @param pool      the ConstantPool that will contain this Constant
     * @param sPath     the path of the directory that acts as the root of this FileStore
     * @param constDir  the directory that acts as the root of this FileStore
     */
    public FileStoreConstant(ConstantPool pool, String sPath, FSNodeConstant constDir)
        {
        super(pool);

        if (sPath == null)
            {
            throw new IllegalArgumentException("directory path required");
            }

        if (constDir == null)
            {
            throw new IllegalArgumentException("directory contents required");
            }

        m_constPath = pool.ensureStringConstant(sPath);
        m_constDir  = constDir;
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
    public FileStoreConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iPath = readMagnitude(in);
        m_iDir  = readMagnitude(in);
        }

    @Override
    protected void resolveConstants()
        {
        ConstantPool pool = getConstantPool();

        m_constPath = (StringConstant) pool.getConstant(m_iPath);
        m_constDir  = (FSNodeConstant) pool.getConstant(m_iDir);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the original path that this filestore represents the contents of
     */
    public String getPath()
        {
        return getPathConstant().getValue();
        }

    /**
     * @return the StringConstant holding the path that this filestore represents the contents of
     */
    public StringConstant getPathConstant()
        {
        return m_constPath;
        }

    /**
     * {@inheritDoc}
     * @return  the directory constant
     */
    @Override
    public FSNodeConstant getValue()
        {
        return m_constDir;
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


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.FileStore;
        }

    @Override
    public TypeConstant getType()
        {
        return getConstantPool().ensureEcstasyTypeConstant("fs.FileStore");
        }

    @Override
    public boolean containsUnresolved()
        {
        return m_constDir.containsUnresolved();
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constPath);
        visitor.accept(m_constDir);
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof FileStoreConstant))
            {
            return this.getFormat().compareTo(that.getFormat());
            }

        int nResult = this.m_constPath.compareTo(((FileStoreConstant) that).m_constPath);
        if (nResult == 0)
            {
            nResult = this.m_constDir.compareTo(((FileStoreConstant) that).m_constDir);
            }
        return nResult;
        }

    @Override
    public String getValueString()
        {
        return m_constDir.getName();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constPath = (StringConstant) pool.register(m_constPath);
        m_constDir  = (FSNodeConstant) pool.register(m_constDir);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constPath.getPosition());
        writePackedLong(out, m_constDir .getPosition());
        }

    @Override
    public String getDescription()
        {
        return "filestore:" + m_constPath.getValue();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_constPath, Hash.of(m_constDir));
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Used during deserialization: holds the index of the origin path.
     */
    private transient int m_iPath;

    /**
     * Used during deserialization: holds the index of the class constant.
     */
    private transient int m_iDir;

    /**
     * The path that the FileStore was created from.
     */
    private StringConstant m_constPath;

    /**
     * The FSNodeConstant for the root directory of the FileStore.
     */
    private FSNodeConstant m_constDir;

    /**
     * The ObjectHandle representing this singleton's value.
     */
    private transient ObjectHandle m_handle;
    }
