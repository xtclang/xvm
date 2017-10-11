package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.List;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a method signature constant. A signature constant identifies a method's call signature
 * for the purpose of invocation; in other words, it specifies what name, parameters, and return
 * values a method must have in order to be selected for invocation. This is particularly useful for
 * supporting virtual method invocation with auto-narrowing types, as the invocation site does not
 * have to specify which exact method is being invoked (such as a particular method on a particular
 * class), but rather that some virtual method chain exists such that it matches a particular
 * signature.
 * <p/>
 * In Ecstasy, a type is simply a collection of methods and properties. Even properties can be
 * expressed as methods; a property of type T and name N can be represented as a method N that takes
 * no parameters and returns a single value of type T. As such, a type can represented as a
 * collection of method signatures.
 * <p/>
 * Method signatures are not necessarily exact, however. Consider the support in Ecstasy for auto-
 * narrowing types:
 * <p/>
 * <code><pre>
 *     interface I
 *         {
 *         I foo();
 *         Void bar(I i);
 *         }
 * </pre></code>
 * <p/>
 * Now consider a class:
 * <p/>
 * <code><pre>
 *     class C
 *         {
 *         C! foo() {...}
 *         Void bar(C! c) {...}
 *         }
 * </pre></code>
 * <p/>
 * While the class does not explicitly implement the interface I, and while the methods on the class
 * are explicit (not auto-narrowing), the class C does implicitly implement interface I, and thus an
 * instance of C can be passed to (or returned from) any method that accepts (or returns) an "I".
 */
public class SignatureConstant
        extends PseudoConstant
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
    public SignatureConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iName     = readMagnitude(in);
        m_aiReturns = readMagnitudeArray(in);
        m_aiParams  = readMagnitudeArray(in);
        }

    /**
     * Construct a constant whose value is a method signature identifier.
     *
     * @param pool     the ConstantPool that will contain this Constant
     * @param sName    the name of the method
     * @param params   the param types
     * @param returns  the return types
     */
    public SignatureConstant(ConstantPool pool, String sName, TypeConstant[] params, TypeConstant[] returns)
        {
        super(pool);

        if (sName == null)
            {
            throw new IllegalArgumentException("name required");
            }

        m_constName     = pool.ensureStringConstant(sName);
        m_aconstParams  = validateTypes(params);
        m_aconstReturns = validateTypes(returns);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the name of the method specified by this signature
     */
    public String getName()
        {
        return m_constName.getValue();
        }

    /**
     * @return the method's parameter types
     */
    public TypeConstant[] getRawParams()
        {
        return m_aconstParams;
        }

    /**
     * @return the method's parameter types
     */
    public List<TypeConstant> getParams()
        {
        return Arrays.asList(m_aconstParams);
        }

    /**
     * @return the method's return types
     */
    public TypeConstant[] getRawReturns()
        {
        return m_aconstReturns;
        }

    /**
     * @return the method's return types
     */
    public List<TypeConstant> getReturns()
        {
        return Arrays.asList(m_aconstReturns);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Signature;
        }

    @Override
    public boolean containsUnresolved()
        {
        if (m_constName.containsUnresolved())
            {
            return true;
            }
        for (Constant constant : m_aconstParams)
            {
            if (constant.containsUnresolved())
                {
                return true;
                }
            }
        for (Constant constant : m_aconstReturns)
            {
            if (constant.containsUnresolved())
                {
                return true;
                }
            }
        return false;
        }

    @Override
    public Constant simplify()
        {
        m_constName = (StringConstant) m_constName.simplify();
        simplifyTypes(m_aconstParams);
        simplifyTypes(m_aconstReturns);

        // replace a void return with no return
        if (m_aconstReturns.length == 1)
            {
            if (m_aconstReturns[0].isVoid())
                {
                m_aconstReturns = NO_TYPES;
                }
            }

        return this;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constName);
        for (Constant constant : m_aconstParams)
            {
            visitor.accept(constant);
            }
        for (Constant constant : m_aconstReturns)
            {
            visitor.accept(constant);
            }
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        SignatureConstant that = (SignatureConstant) obj;
        int n = this.m_constName.compareTo(that.m_constName);
        if (n == 0)
            {
            n = compareTypes(this.m_aconstParams, that.m_aconstParams);
            if (n == 0)
                {
                n = compareTypes(this.m_aconstReturns, that.m_aconstReturns);
                }
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        StringBuilder sb = new StringBuilder();

        switch (m_aconstReturns.length)
            {
            case 0:
                sb.append("Void");
                break;

            case 1:
                sb.append(m_aconstReturns[0].getValueString());
                break;

            default:
                sb.append('(');
                boolean first = true;
                for (TypeConstant type : m_aconstReturns)
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
                break;
            }

        sb.append(' ')
          .append(m_constName.getValue())
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
        ConstantPool pool = getConstantPool();
        m_constName     = (StringConstant) pool.getConstant(m_iName);
        m_aconstParams  = lookupTypes(pool, m_aiParams);
        m_aconstReturns = lookupTypes(pool, m_aiReturns);

        m_aiReturns = null;
        m_aiParams  = null;
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constName = (StringConstant) pool.register(m_constName);
        registerTypes(pool, m_aconstParams);
        registerTypes(pool, m_aconstReturns);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constName.getPosition());
        writeTypes(out, m_aconstParams);
        writeTypes(out, m_aconstReturns);
        }

    @Override
    public String getDescription()
        {
        return "name=" + getName()
                + ", params=" + formatTypes(m_aconstParams)
                + ", returns=" + formatTypes(m_aconstReturns);
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return (m_constName.hashCode() * 17 + m_aconstParams.length * 3) + m_aconstReturns.length;
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Read a length encoded array of constant indexes.
     *
     * @param in  a DataInput stream to read from
     *
     * @return an array of integers, which are the indexes of the constants
     *
     * @throws IOException  if an error occurs attempting to read from the stream
     */
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

    /**
     * Convert the passed array of constant indexes into an array of type constants.
     *
     * @param pool  the ConstantPool
     * @param an    an array of constant indexes
     *
     * @return an array of type constants
     */
    protected static TypeConstant[] lookupTypes(ConstantPool pool, int[] an)
        {
        int c = an.length;
        TypeConstant[] aconst = new TypeConstant[c];
        for (int i = 0; i < c; ++i)
            {
            aconst[i] = (TypeConstant) pool.getConstant(an[i]);
            }
        return aconst;
        }

    /**
     * Simplify each of the type constants in the passed array.
     *
     * @param aconst  an array of constants
     */
    protected static void simplifyTypes(TypeConstant[] aconst)
        {
        for (int i = 0, c = aconst.length; i < c; ++i)
            {
            aconst[i] = (TypeConstant) aconst[i].simplify();
            }
        }

    /**
     * Register each of the type constants in the passed array.
     *
     * @param pool    the ConstantPool
     * @param aconst  an array of constants
     */
    protected static void registerTypes(ConstantPool pool, TypeConstant[] aconst)
        {
        for (int i = 0, c = aconst.length; i < c; ++i)
            {
            aconst[i] = (TypeConstant) pool.register(aconst[i]);
            }
        }

    /**
     * Write a length-encoded series of type constants to the specified stream.
     *
     * @param out     a DataOutput stream
     * @param aconst  an array of constants
     *
     * @throws IOException  if an error occurs while writing the type constants
     */
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

    /**
     * Compare two arrays of type constants for order, as per the rules described by
     * {@link Comparable}.
     *
     * @param aconstThis  the first array of type constants
     * @param aconstThat  the second array of type constants
     *
     * @return a negative, zero, or a positive integer, depending on if the first array is less
     *         than, equal to, or greater than the second array for purposes of ordering
     */
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

    /**
     * Render an array of TypeConstant objects as a comma-delimited string containing those types.
     *
     * @param aconst  the array of type constants
     *
     * @return a parenthesized, comma-delimited string of types
     */
    protected static String formatTypes(TypeConstant[] aconst)
        {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (int i = 0, c = aconst.length; i < c; ++i)
            {
            if (i > 0)
                {
                sb.append(", ");
                }
            sb.append(aconst[i].getValueString());
            }
        sb.append(')');
        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Empty array of types.
     */
    public static final TypeConstant[] NO_TYPES = new TypeConstant[0];

    /**
     * During disassembly, this holds the index of the constant that specifies the name of this
     * method.
     */
    private int m_iName;

    /**
     * During disassembly, this holds the indexes of the type constants for the parameters.
     */
    private int[] m_aiParams;

    /**
     * During disassembly, this holds the indexes of the type constants for the return values.
     */
    private int[] m_aiReturns;

    /**
     * The constant that represents the parent of this method.
     */
    private StringConstant m_constName;

    /**
     * The invocation parameters of the method.
     */
    TypeConstant[] m_aconstParams;

    /**
     * The return values from the method.
     */
    TypeConstant[] m_aconstReturns;
    }
