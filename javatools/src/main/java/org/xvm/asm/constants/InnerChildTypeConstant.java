package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.util.Hash;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents an inner child class; for example:
 *
 * <pre>
 * class Parent&lt;ParentType>
 *     {
 *     void test()
 *         {
 *         class Child&lt;ChildType>
 *             {
 *             }
 *         Child&lt;Int> c = new Child();
 *         ...
 *         }
 *     }
 * </pre>
 *
 * The type of the variable "c" above is:
 *   {@code ParameterizedTypeConstant(T1, Int)}
 * <br/>where T1 is {@code InnerChildTypeConstant(T2, idChild)},
 * <br/>where T2 is {@code ParameterizedTypeConstant(T3, ParentType)},
 * <br/>where T3 is {@code TerminalTypeConstant(Parent)}
 */
public class InnerChildTypeConstant
        extends AbstractDependantChildTypeConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a inner child type.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param typeParent  the parent's type
     * @param idChild     the child class id
     */
    public InnerChildTypeConstant(ConstantPool pool, TypeConstant typeParent, ClassConstant idChild)
        {
        super(pool, typeParent);

        if (typeParent.isAccessSpecified() || typeParent.isAnnotated())
            {
            throw new IllegalArgumentException("parent's access or annotations cannot be specified");
            }
        if (idChild == null)
            {
            throw new IllegalArgumentException("id is required");
            }
        m_idChild = idChild;
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
    public InnerChildTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);

        m_iChild = readIndex(in);
        }

    @Override
    protected void resolveConstants()
        {
        super.resolveConstants();

        m_idChild = (ClassConstant) getConstantPool().getConstant(m_iChild);
        }

    @Override
    protected ClassStructure getChildStructure()
        {
        return (ClassStructure) m_idChild.getComponent();
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isInnerChildClass()
        {
        return true;
        }

    @Override
    protected TypeConstant cloneSingle(ConstantPool pool, TypeConstant type)
        {
        return pool.ensureInnerChildTypeConstant(type, m_idChild);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.InnerChildType;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        super.forEachUnderlying(visitor);

        visitor.accept(m_idChild);
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        int n = super.compareDetails(obj);
        if (n == 0)
            {
            if (!(obj instanceof InnerChildTypeConstant that))
                {
                return -1;
                }

            n = this.m_idChild.compareTo(that.m_idChild);
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        return m_typeParent.getValueString() + '.' + m_idChild.getValueString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        m_idChild = (ClassConstant) pool.register(m_idChild);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);

        writePackedLong(out, m_idChild.getPosition());
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_typeParent,
               Hash.of(m_idChild));
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the ClassConstant for the id.
     */
    private transient int m_iChild;

    /**
     * The ClassConstant representing this child.
     */
    protected ClassConstant m_idChild;
    }