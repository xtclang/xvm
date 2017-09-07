package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.proto.ClassTemplate;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An XVM Structure that represents a property.
 *
 * @author cp 2016.04.25
 */
public class PropertyStructure
        extends Component
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a PropertyStructure with the specified identity.
     *
     * @param xsParent   the XvmStructure that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Property
     * @param condition  the optional condition for this PropertyStructure
     */
    protected PropertyStructure(XvmStructure xsParent, int nFlags, PropertyConstant constId, ConditionalConstant condition)
        {
        super(xsParent, nFlags, constId, condition);
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public PropertyConstant getIdentityConstant()
        {
        return (PropertyConstant) super.getIdentityConstant();
        }

    /**
     * @return the TypeConstant representing the data type of the property value
     */
    public TypeConstant getType()
        {
        return m_type;
        }

    /**
     * Configure the property's type.
     *
     * @param type  the type constant that indicates the property's type
     */
    public void setType(TypeConstant type)
        {
        assert type != null;
        m_type = type;
        }

    /**
     * @return the transient property info
     */
    public ClassTemplate.PropertyInfo getInfo()
        {
        return m_info;
        }

    /**
     * Store the transient property info.
     */
    public void setInfo(ClassTemplate.PropertyInfo info)
        {
        if (m_info != null)
            {
            throw new IllegalStateException("Info is not resettable");
            }
        m_info = info;
        }

    // ----- component methods ---------------------------------------------------------------------

    @Override
    public boolean isMethodContainer()
        {
        return true;
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

    private TypeConstant m_type;


    // ----- TEMPORARY -----------------------------------------------------------------------------

    /**
     * The transient run-time method data.
     */
    private transient ClassTemplate.PropertyInfo m_info;
    }
