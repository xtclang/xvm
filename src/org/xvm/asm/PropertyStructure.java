package org.xvm.asm;


import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnresolvedTypeConstant;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

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
     * @param xsParent   the XvmStructure (probably a FileStructure) that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Module
     * @param condition  the optional condition for this ModuleStructure
     */
    protected PropertyStructure(XvmStructure xsParent, int nFlags, PropertyConstant constId, ConditionalConstant condition)
        {
        super(xsParent, nFlags, constId, condition);
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * For a PropertyStructure whose type is unresolved, provide the type that the property will
     * be using. (If the PropertyStructure has a resolved type, this will fail.)
     *
     * @param type  the new type for the property to use
     */
    public void resolveType(TypeConstant type)
        {
        assert type != null;
        assert m_type instanceof UnresolvedTypeConstant;
        assert !((UnresolvedTypeConstant) m_type).isTypeResolved();

        ((UnresolvedTypeConstant) m_type).resolve(type);
        m_type = type;
        }

    /**
     * Obtain the PropertyConstant that holds the identity of this Property.
     *
     * @return the PropertyConstant representing the identity of this PropertyStructure
     */
    public PropertyConstant getPropertyConstant()
        {
        return (PropertyConstant) getIdentityConstant();
        }

    /**
     * @return the TypeConstant representing the data type of the property value
     */
    public TypeConstant getType()
        {
        return m_type;
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
        m_type = (TypeConstant) getConstantPool().getConstant(readIndex(in));

        super.disassemble(in);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        ((Constant) m_type).registerConstants(pool);

        super.registerConstants(pool);
        }

    @Override
    protected void assemble(DataOutput out)
    throws IOException
        {
        writePackedLong(out, m_type.getPosition());

        super.assemble(out);
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
    }
