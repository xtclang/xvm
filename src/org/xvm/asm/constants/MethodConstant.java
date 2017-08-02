package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.List;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a Method constant. A method constant uniquely identifies a method within a named
 * multi-method (a group of methods by the same name).
 */
public class MethodConstant
        extends IdentityConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public MethodConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iParent   = readMagnitude(in);
        m_access    = Access.valueOf(readIndex(in));
        m_aiReturns = readMagnitudeArray(in);
        m_aiParams  = readMagnitudeArray(in);
        }

    /**
     * Construct a constant whose value is a method identifier.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  specifies the MultiMethodConstant that contains this method
     * @param access       the accessibility of the method, public/private etc.
     * @param returns      the return types
     * @param params       the param types
     */
    public MethodConstant(ConstantPool pool, MultiMethodConstant constParent, Access access,
                          TypeConstant[] returns, TypeConstant[] params)
        {
        super(pool);

        if (constParent == null)
            {
            throw new IllegalArgumentException("parent required");
            }

        if (access == null)
            {
            throw new IllegalArgumentException("access specifier required");
            }

        m_constParent   = constParent;
        m_access        = access;
        m_aconstReturns = validateTypes(returns);
        m_aconstParams  = validateTypes(params);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the method's accessibility
     */
    public Access getAccess()
        {
        return m_access;
        }

    /**
     * @return the method's return types
     */
    public List<TypeConstant> getReturns()
        {
        return Arrays.asList(m_aconstReturns);
        }

    /**
     * @return the method's return types
     */
    public TypeConstant[] getRawReturns()
        {
        return m_aconstReturns;
        }

    /**
     * @return the method's parameter types
     */
    public List<TypeConstant> getParams()
        {
        return Arrays.asList(m_aconstParams);
        }

    /**
     * @return the method's parameter types
     */
    public TypeConstant[] getRawParams()
        {
        return m_aconstParams;
        }


    // ----- IdentityConstant methods --------------------------------------------------------------

    @Override
    public MultiMethodConstant getParentConstant()
        {
        return m_constParent;
        }

    @Override
    public IdentityConstant getNamespace()
        {
        return getParentConstant().getNamespace();
        }

    @Override
    public String getName()
        {
        return getParentConstant().getName();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Method;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constParent);
        for (Constant constant : m_aconstReturns)
            {
            visitor.accept(constant);
            }
        for (Constant constant : m_aconstParams)
            {
            visitor.accept(constant);
            }
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        MethodConstant that = (MethodConstant) obj;
        int n = this.m_constParent.compareTo(that.m_constParent);
        if (n == 0)
            {
            n = this.m_access.compareTo(that.m_access);
            if (n == 0)
                {
                n = compareTypes(this.m_aconstReturns, that.m_aconstReturns);
                if (n == 0)
                    {
                    n = compareTypes(this.m_aconstParams, that.m_aconstParams);
                    }
                }
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(m_constParent.getValueString())
          .append('(');

        boolean first = true;
        for (TypeConstant type : m_aconstParams)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(type.getValueString());
            }

        sb.append(')');
        return sb.toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        final ConstantPool pool = getConstantPool();
        m_constParent   = (MultiMethodConstant) pool.getConstant(m_iParent);
        m_aconstReturns = lookupTypes(m_aiReturns);
        m_aconstParams  = lookupTypes(m_aiParams);

        m_aiReturns = null;
        m_aiParams  = null;
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constParent = (MultiMethodConstant) pool.register(m_constParent);
        registerTypes(pool, m_aconstReturns);
        registerTypes(pool, m_aconstParams);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constParent.getPosition());
        writePackedLong(out, m_access.ordinal());
        writeTypes(out, m_aconstReturns);
        writeTypes(out, m_aconstParams);
        }

    @Override
    public String getDescription()
        {
        return "method=" + getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return (m_constParent.hashCode() * 17 + m_access.ordinal()) * 3
                + m_aconstReturns.length + m_aconstParams.length;
        }


    // ----- helpers -------------------------------------------------------------------------------

    protected static int[] readMagnitudeArray(DataInput in)
            throws IOException
        {
        int   c  = readMagnitude(in);
        int[] an = new int[c];
        for (int i = 0; i < c; ++i)
            {
            an[i] = readMagnitude(in);
            }
        return an;
        }

    protected TypeConstant[] lookupTypes(int[] an)
        {
        int c = an.length;
        TypeConstant[] aconst = new TypeConstant[c];
        final ConstantPool pool = getConstantPool();
        for (int i = 0; i < c; ++i)
            {
            aconst[i] = (TypeConstant) pool.getConstant(an[i]);
            }
        return aconst;
        }

    protected static void registerTypes(ConstantPool pool, TypeConstant[] aconst)
        {
        for (int i = 0, c = aconst.length; i < c; ++i)
            {
            aconst[i] = (TypeConstant) pool.register(aconst[i]);
            }
        }

    protected static void writeTypes(DataOutput out, TypeConstant[] aconst)
            throws IOException
        {
        int c = aconst.length;
        writePackedLong(out, c);

        for (int i = 0; i < c; ++i)
            {
            writePackedLong(out, aconst[i].getPosition());
            }
        }

    /**
     * Internal helper to scan a type array for nulls.
     *
     * @param aconst  an array of TypeConstant; may be null
     *
     * @return a non-null array of TypeConstant, each element of which is non-null
     */
    protected static TypeConstant[] validateTypes(TypeConstant[] aconst)
        {
        if (aconst == null)
            {
            return ConstantPool.NO_TYPES;
            }

        for (TypeConstant constant : aconst)
            {
            if (constant == null)
                {
                throw new IllegalArgumentException("type required");
                }
            }

        return aconst;
        }

    protected static int compareTypes(TypeConstant[] aconstThis, TypeConstant[] aconstThat)
        {
        int cThis = aconstThis.length;
        int cThat = aconstThat.length;
        for (int i = 0, c = Math.min(cThis, cThat); i < c; ++i)
            {
            int n = aconstThis[i].compareTo(aconstThat[i]);
            if (n != 0)
                {
                return n;
                }
            }
        return cThis - cThat;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that specifies the parent of this
     * method.
     */
    private int m_iParent;

    /**
     * During disassembly, this holds the indexes of the type constants for the return values.
     */
    private int[] m_aiReturns;

    /**
     * During disassembly, this holds the indexes of the type constants for the parameters.
     */
    private int[] m_aiParams;

    /**
     * The constant that represents the parent of this method.
     */
    private MultiMethodConstant m_constParent;

    /**
     * The accessibility of the method.
     */
    private Access m_access;

    /**
     * The return values from the method.
     */
    TypeConstant[] m_aconstReturns;

    /**
     * The invocation parameters of the method.
     */
    TypeConstant[] m_aconstParams;
    }
