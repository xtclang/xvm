package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.compiler.Lexer.isValidIdentifier;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents the type that is specified by a type parameter.
 *
 * @author cp 2017.06.27
 */
public class ParameterTypeConstant
        extends TypeConstant
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
    public ParameterTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iParent = readMagnitude(in);
        m_iName   = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is a name that represents a data type parameter (which is a
     * specific data type at runtime).
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the type that contains the type parameter
     * @param sParamName   the simple name of the type parameter
     */
    public ParameterTypeConstant(ConstantPool pool, TypeConstant constParent, String sParamName)
        {
        super(pool);

        if (constParent == null)
            {
            throw new IllegalArgumentException("parent required");
            }

        switch (constParent.getFormat())
            {
            case ClassType:
            case ParentType:
            case ChildType:
                break;

            default:
                throw new IllegalArgumentException("parent type (" + constParent.getFormat()
                        + ") must be one of ClassType, ParentType, or ChildType");
            }

        if (sParamName == null)
            {
            throw new IllegalArgumentException("parameter name required");
            }

        if (!isValidIdentifier(sParamName))
            {
            throw new IllegalArgumentException("parameter name is invalid: " + sParamName);
            }

        m_constParent = constParent;
        m_constName   = pool.ensureCharStringConstant(sParamName);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the TypeConstant for the type that contains this type parameter
     */
    public TypeConstant getParentType()
        {
        return m_constParent;
        }

    /**
     * @return the name of the type parameter
     */
    public String getName()
        {
        return m_constName.getValue();
        }

    /**
     * @return the name of the type parameter, as a CharStringConstant
     */
    public StringConstant getNameConstant()
        {
        return m_constName;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ParameterType;
        }

    @Override
    protected Object getLocator()
        {
        // the locator is the name of the parameter, but only if the parameter is a parameter of
        // the public "this:type"
        return m_constParent.isAutoNarrowing() && m_constParent instanceof ClassTypeConstant
            && ((ClassTypeConstant) m_constParent).getAccess() == Access.PUBLIC
                ? m_constName.getValue()
                : null;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constParent);
        visitor.accept(m_constName);
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        ParameterTypeConstant that = (ParameterTypeConstant) obj;
        int n = this.m_constParent.compareTo(that.m_constParent);
        if (n == 0)
            {
            n = this.m_constName.compareDetails(that.m_constName);
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        // TODO this isn't quite right
        return m_constName.getValue();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_constParent = (TypeConstant      ) getConstantPool().getConstant(m_iParent);
        m_constName   = (StringConstant) getConstantPool().getConstant(m_iName  );
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constParent = (TypeConstant      ) pool.register(m_constParent);
        m_constName   = (StringConstant) pool.register(m_constName  );
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constParent.getPosition());
        writePackedLong(out, m_constName  .getPosition());
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constParent.hashCode() ^ m_constName.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the parent type.
     */
    private int m_iParent;

    /**
     * During disassembly, this holds the index of the parameter name constant.
     */
    private int m_iName;

    /**
     * The type that contains the type parameter.
     */
    private TypeConstant m_constParent;

    /**
     * The constant that holds the name of the type parameter.
     */
    private StringConstant m_constName;
    }
