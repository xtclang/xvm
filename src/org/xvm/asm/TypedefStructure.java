package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypedefConstant;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An XVM Structure that represents a "typedef" statement, which acts as a way to name an arbitrary
 * type, by associating a named structure (this) with a type constant.
 *
 * @author cp 2016.06.27
 */
public class TypedefStructure
        extends Component
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a TypeDefStructure with the specified identity.
     *
     * @param xsParent   the XvmStructure that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Typedef
     * @param condition  the optional condition for this TypeDefStructure
     */
    protected TypedefStructure(XvmStructure xsParent, int nFlags, TypedefConstant constId,
            ConditionalConstant condition)
        {
        super(xsParent, nFlags, constId, condition);
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public TypedefConstant getIdentityConstant()
        {
        return (TypedefConstant) super.getIdentityConstant();
        }

    /**
     * @return the TypeConstant representing the data type of the typedef
     */
    public TypeConstant getType()
        {
        return m_type;
        }

    /**
     * Configure the typedef's type.
     *
     * @param type  the type constant that indicates the typedef's type
     */
    public void setType(TypeConstant type)
        {
        assert type != null;
        m_type = type;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
    throws IOException
        {
        super.disassemble(in);

        m_type = (TypeConstant) getConstantPool().getConstant(readIndex(in));
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        m_type = (TypeConstant) pool.register(m_type);
        }

    @Override
    protected void assemble(DataOutput out)
    throws IOException
        {
        super.assemble(out);

        writePackedLong(out, m_type.getPosition());
        }

    @Override
    public String getDescription()
        {
        return new StringBuilder()
                .append("type=")
                .append(m_type)
                .append(", ")
                .append(super.getDescription())
                .toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The actual type that the typedef represents.
     */
    private TypeConstant m_type;
    }
