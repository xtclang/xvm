package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.IdentityConstant;
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

    /**
     * Construct a PropertyStructure with the specified identity, access, and type.
     *
     * @param xsParent   the XvmStructure that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Property
     * @param condition  the optional condition for this PropertyStructure
     * @param access2
     * @param type
     */
    protected PropertyStructure(XvmStructure xsParent, int nFlags, PropertyConstant constId, ConditionalConstant condition, Access access2, TypeConstant type)
        {
        this(xsParent, nFlags, constId, condition);
        if (access2 != null)
            {
            setVarAccess(access2);
            }
        if (type != null)
            {
            setType(type);
            }
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public PropertyConstant getIdentityConstant()
        {
        return (PropertyConstant) super.getIdentityConstant();
        }

    @Override
    public void setAccess(Access access)
        {
        super.setAccess(access);
        if (access.ordinal() > getVarAccess().ordinal())
            {
            setVarAccess(access);
            }
        }

    /**
     * The "Var" access of the property, which may be more restricted than the access of the
     * property (as a Ref). Null implies that the property does not have a "Var" access specified,
     * which means either that the "Var" access is the same as the "Ref" access, or that there is
     * no "Var" access.
     *
     * @return the access of the property as a Var if specified; otherwise null
     */
    public Access getVarAccess()
        {
        return m_accessVar;
        }

    public void setVarAccess(Access access)
        {
        assert access == null || access.ordinal() >= getAccess().ordinal();
        m_accessVar = access;
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
    protected void setType(TypeConstant type)
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

    /**
     * For a property structure that contains annotations whose types are now resolved, sort the
     * annotations into the annotations that apply to the property, the annotations that apply to
     * the Ref/Var, and the annotations that apply to the property type.
     * <p/>
     * This method isn't responsible for validating the annotations, so it doesn't log any errors.
     * Anything that it finds that is suspect, it leaves for someone else to validate later.
     *
     * @return true iff the annotations are resolved
     */
    public boolean resolveAnnotations()
        {
        // first, make sure that the property type and the annotations are all resolved
        List<Contribution> listContribs = getContributionsAsList();
        if (listContribs.isEmpty())
            {
            return true;
            }

        TypeConstant typeProp = getType();
        if (typeProp.containsUnresolved())
            {
            return false;
            }

        List<Contribution> listMove = null;
        for (Contribution contrib : listContribs)
            {
            if (contrib.getComposition() == Composition.Annotation)
                {
                Annotation annotation = contrib.getAnnotation();
                if (annotation.containsUnresolved())
                    {
                    return false;
                    }

                // find the "into" of the mixin
                ClassStructure structMixin    = (ClassStructure) ((IdentityConstant) annotation.getAnnotationClass()).getComponent();
                Contribution   contribInto    = null;
                Contribution   contribExtends = null;
                while (structMixin != null && structMixin.getFormat() == Format.MIXIN
                        && (contribInto    = structMixin.findContribution(Composition.Into   )) == null
                        && (contribExtends = structMixin.findContribution(Composition.Extends)) != null)
                    {
                    TypeConstant typeExtends = contribExtends.getTypeConstant();
                    if (typeExtends.containsUnresolved())
                        {
                        return false;
                        }

                    structMixin = null;
                    if (typeExtends.isExplicitClassIdentity(true))
                        {
                        Constant constExtends = ((TypeConstant) typeExtends.simplify()).getDefiningConstant();
                        if (constExtends instanceof IdentityConstant)
                            {
                            structMixin = (ClassStructure) ((IdentityConstant) constExtends).getComponent();
                            }
                        }
                    }

                // see if the mixin applies to a Property or a Ref/Var, in which case it stays in
                // this list; otherwise, move it
                boolean fMove = true;
                if (contribInto != null)
                    {
                    TypeConstant typeInto = contribInto.getTypeConstant();
                    if (typeInto.containsUnresolved())
                        {
                        return false;
                        }

                    fMove = !typeInto.isIntoPropertyType();
                    }

                if (fMove)
                    {
                    if (listMove == null)
                        {
                        listMove = new ArrayList<>();
                        }
                    listMove.add(contrib);
                    }
                }
            }

        // now that everything is figured out, do the actual move of any selected contributions
        if (listMove != null)
            {
            // go backwards to that the resulting type constant is built up (nested) correctly
            ConstantPool pool = getConstantPool();
            for (int i = listMove.size()-1; i >= 0; --i)
                {
                Contribution contrib    = listMove.get(i);
                Annotation   annotation = contrib.getAnnotation();
                removeContribution(contrib);
                typeProp = pool.ensureAnnotatedTypeConstant
                        (annotation.getAnnotationClass(), annotation.getParams(), typeProp);
                }
            setType(typeProp);
            }

        return true;
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

        int nAccess = in.readByte();
        m_accessVar = nAccess < 0 ? null : Access.valueOf(nAccess);
        m_type      = (TypeConstant) getConstantPool().getConstant(readIndex(in));
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

        out.writeByte(m_accessVar == null ? -1 : m_accessVar.ordinal());
        writePackedLong(out, m_type.getPosition());
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder()
                .append("id=")
                .append(getIdentityConstant().getValueString())
                .append(", type=")
                .append(m_type)
                .append(", ")
                .append("var-access=")
                .append(m_accessVar)
                .append(", ");

        return sb.append(super.getDescription()).toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The access for the Var (as opposed to the access to the Ref).
     */
    private Access m_accessVar;

    /**
     * The property type.
     */
    private TypeConstant m_type;

    /**
     * The initial value of the property, if it is a "static property" initialized to a constant.
     */
    private Constant m_constVal;


    // ----- TEMPORARY -----------------------------------------------------------------------------

    /**
     * The transient run-time method data.
     */
    private transient ClassTemplate.PropertyInfo m_info;
    }
