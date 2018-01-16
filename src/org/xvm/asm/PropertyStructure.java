package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.List;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An XVM Structure that represents a property.
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
     * @return true iff this property is a type parameter
     */
    public boolean isTypeParameter()
        {
        // first, check the type
        TypeConstant constPropType = m_type;
        if (!(constPropType.isSingleDefiningConstant()                          // must be a class
                && constPropType.getDefiningConstant().equals(
                        getConstantPool().clzType())))                          // must be "Type"
            {
            return false;
            }

        // parent must be a class, and the name of this parameter must be a type parameter of the
        // parent class
        Component parent = getParent();
        if (parent instanceof CompositeComponent)
            {
            for (Component parentEach : ((CompositeComponent) parent).components())
                {
                if (!isTypeParameterOf(parentEach))
                    {
                    return false;
                    }
                }
            return true;
            }
        else
            {
            return isTypeParameterOf(parent);
            }
        }

    /**
     * Determine if this property is a type parameter of the specified parent component.
     *
     * @param component a non-composite parent component
     *
     * @return true iff this property represents a type parameter on the specified component
     */
    private boolean isTypeParameterOf(Component component)
        {
        return component instanceof ClassStructure
                && ((ClassStructure) component).getTypeParams().containsKey(
                        getIdentityConstant().getNameConstant());
        }

    /**
     * @return the base type for the type parameter, from the "extends" clause, or Object if no
     *         explicit "extends" clause was present, or null if the property is not a type param
     */
    public TypeConstant getTypeParameterExtendsType()
        {
        if (!isTypeParameter())
            {
            return null;
            }

        return m_type.isParamsSpecified()
                ? m_type.getParamTypesArray()[0]
                : m_type.getConstantPool().typeObject();
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

    /**
     * Check if this property could be accessed via the specified signature.
     *
     * @param sigThat     the signature of the matching property (resolved)
     * @param listActual  the actual generic types
     */
    public boolean isSubstitutableFor(SignatureConstant sigThat, List<TypeConstant> listActual)
        {
        assert getName().equals(sigThat.getName());
        assert sigThat.getRawParams().length == 0;
        assert sigThat.getRawReturns().length == 1;

        SignatureConstant sigThis = getIdentityConstant().getSignature();
        if (!listActual.isEmpty())
            {
            ClassStructure clzThis = (ClassStructure) getParent();
            sigThis = sigThis.resolveGenericTypes(clzThis.new SimpleTypeResolver(listActual));
            }

        // TODO: if read-only then isA() would suffice
        return sigThat.equals(sigThis);
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
                .append("id=")
                .append(getIdentityConstant().getValueString())
                .append(", type=")
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
