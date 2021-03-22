package org.xvm.asm.constants;


import org.xvm.asm.Annotation;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.IdentityConstant.NestedIdentity;
import org.xvm.asm.constants.MethodBody.Existence;
import org.xvm.asm.constants.MethodBody.Implementation;

import org.xvm.util.Handy;


/**
 * Represents the information about a single property at a single virtual level.
 */
public class PropertyBody
        implements Constants
    {
    /**
     * Construct a PropertyBody from the passed information.
     *
     * @param struct         the property structure that this body is derived from
     * @param impl           one of Implicit, Declared, Delegating, or Explicit
     * @param constDelegate  the property constant that provides the reference to delegate to
     * @param type           the type of the property, including any type annotations (required)
     * @param fRO            true iff the property has any of a number of indicators that would
     *                       indicate that the property is read-only
     * @param fRW            true iff the property has any of a number of indicators that would
     *                       indicate that the property is read-write
     * @param fCustomCode    true to indicate that the property has custom code that overrides
     *                       the underlying Ref/Var implementation (including native accessors)
     * @param effectGet      the Effect of the getter
     * @param effectSet      the Effect of the setter
     * @param fReqField      true iff the property requires the presence of a field
     * @param fConstant      true iff the property represents a named constant value
     * @param constInitVal   the initial value for the property
     * @param constInitFunc  the function that will provide an initial value for the property
     */
    public PropertyBody(
            PropertyStructure struct,
            Implementation    impl,
            PropertyConstant  constDelegate,
            TypeConstant      type,
            boolean           fRO,
            boolean           fRW,
            boolean           fCustomCode,
            Effect            effectGet,
            Effect            effectSet,
            boolean           fReqField,
            boolean           fConstant,
            Constant          constInitVal,
            MethodConstant    constInitFunc)
        {
        assert struct    != null;
        assert type      != null;
        assert     impl == Implementation.Implicit
                || impl == Implementation.Declared
                || impl == Implementation.Default
                || impl == Implementation.Delegating
                || impl == Implementation.Native
                || impl == Implementation.SansCode
                || impl == Implementation.Explicit;
        assert (impl == Implementation.Delegating) ^ (constDelegate == null);
        if (constInitVal != null && constInitFunc != null)
            {
            // this can happen when the initial value is a constant function (lambda)
            // or any other constant that refers to the initializer (see
            // PropertyDeclarationStatement,validateContent);
            // in that case we simply disregard the initializer
            constInitFunc = null;
            }
        else if (fConstant && constInitVal == null && constInitFunc == null && !struct.isInjected())
            {
            // this can only happen when we're building the TypeInfo for a partially compiled class,
            // so we will need to invalidate the TypeInfo afterwards;
            // mark the implementation as "Implicit" just to assert it gets replaced later
            impl = Implementation.Implicit;
            }
        assert effectGet != null && effectSet != null;

        m_structProp    = struct;
        m_impl          = impl;
        m_constDelegate = constDelegate;
        m_infoFormal    = null;
        m_type          = type;
        m_fRO           = fRO;
        m_fRW           = fRW;
        m_fCustom       = fCustomCode;
        m_effectGet     = effectGet;
        m_effectSet     = effectSet;
        m_fField        = fReqField;
        m_fConstant     = fConstant;
        m_constInitVal  = constInitVal;
        m_constInitFunc = constInitFunc;
        }

    /**
     * Construct a PropertyBody that represents the specified formal type parameter.
     *
     * @param struct      the property structure that this body is derived from
     * @param infoFormal  the formal type parameter information
     */
    public PropertyBody(PropertyStructure struct, ParamInfo infoFormal)
        {
        assert infoFormal != null;
        assert struct == null || struct.getName().equals(infoFormal.getName());
        assert struct != null || infoFormal.getNestedIdentity() instanceof NestedIdentity;

        TypeConstant typeActual = infoFormal.getActualType();
        TypeConstant typeType   = struct != null && struct.getIdentityConstant().isTypeSequenceTypeParameter()
                ? struct.getType() // for the turtle type property, the actual type is ignored
                : typeActual.getType();

        m_structProp    = struct;
        m_impl          = Implementation.Native;
        m_constDelegate = null;
        m_infoFormal    = infoFormal;
        m_type          = typeType;
        m_fRO           = true;
        m_fRW           = false;
        m_fCustom       = false;
        m_fField        = false;
        m_fConstant     = false;
        m_constInitVal  = null;
        m_constInitFunc = null;
        m_effectGet     = Effect.None;
        m_effectSet     = Effect.None;
        }

    /**
     * @return the container of the property
     */
    public IdentityConstant getParent()
        {
        return getIdentity().getParentConstant();
        }

    /**
     * @return the identity of the property
     */
    public PropertyConstant getIdentity()
        {
        return m_structProp == null
                ? (PropertyConstant) ((NestedIdentity) m_infoFormal.getNestedIdentity()).getIdentityConstant()
                : m_structProp.getIdentityConstant();
        }

    /**
     * @return the PropertyStructure for the property body; may be null for nested type params
     */
    public PropertyStructure getStructure()
        {
        return m_structProp;
        }

    /**
     * @return the property name
     */
    public String getName()
        {
        return m_structProp == null
                ? m_infoFormal.getName()
                : m_structProp.getName();
        }

    /**
     * Property body implementations are one of the following:
     * <p/>
     * <ul>
     * <li><b>Implicit</b> - the method body represents a property known to exist for compilation
     * purposes, but is otherwise not present; this is the result of the {@code into} clause, or any
     * properties of {@code Object} in the context of an interface, for example;</li>
     * <li><b>Declared</b> - the property body represents an interface-declared property;</li>
     * <li><b>Default</b> - the property body represents an interface-declared property with a
     * default implementation of {@code get()};</li>
     * <li><b>Delegating</b> - the property body delegates the Ref/Var functionality;</li>
     * <li><b>Native</b> - indicates a type param or a constant;</li>
     * <li><b>SansCode</b> - a property body that was created to represent the implicit adoption of
     * an interface's property declaration onto a class;</li>
     * <li><b>Explicit</b> - a "normal" property body</li>
     * </ul>
     *
     * @return the implementation type for the property
     */
    public Implementation getImplementation()
        {
        return m_impl;
        }

    /**
     * @return the existence for the property implementation
     */
    public Existence getExistence()
        {
        return m_impl.getExistence();
        }

    /**
     * @return the property that provides the reference to delegate to
     */
    public PropertyConstant getDelegate()
        {
        return m_constDelegate;
        }

    /**
     * @return the property type
     */
    public TypeConstant getType()
        {
        return m_type;
        }

    /**
     * @return true iff this property represents a formal type
     */
    public boolean isFormalType()
        {
        return m_infoFormal != null;
        }

    /**
     * @return the Access required for the Ref form of the property
     */
    public Access getRefAccess()
        {
        return m_structProp == null
                ? Access.PUBLIC
                : m_structProp.getAccess();
        }

    /**
     * @return the Access required for the Var form of the property, or null if not specified
     */
    public Access getVarAccess()
        {
        return m_structProp == null
                ? null
                : m_structProp.getVarAccess();
        }

    /**
     * @return true iff this property body must be a Ref-not-Var
     */
    public boolean isRO()
        {
        return m_fRO;
        }

    /**
     * @return true iff this property body must be a Var
     */
    public boolean isRW()
        {
        return m_fRW;
        }

    /**
     * @return true iff this property has a field, whether or not that field is reachable
     */
    public boolean hasField()
        {
        return m_fField;
        }

    public boolean impliesField()
        {
        // this needs to stay in sync with TypeConstant#createPropertyInfo()
        return m_fField ||
                (getExistence() == Existence.Class
                && !isRO()
                && !isExplicitAbstract()
                && !isGetterBlockingSuper());
        }

    /**
     * @return true iff this property represents a constant (a static property)
     */
    public boolean isConstant()
        {
        return m_fConstant;
        }

    /**
     * @return the initial value of the property as a constant, or null if there is no constant
     *         initial value
     */
    public Constant getInitialValue()
        {
        return m_constInitVal;
        }

    /**
     * @return the function that provides the initial value of the property, or null if there is no
     *         initializer
     */
    public MethodConstant getInitializer()
        {
        return m_constInitFunc;
        }

    /**
     * @return true iff the property has any methods in addition to the underlying Ref or Var
     *         "rebasing" implementation, and in addition to any annotations
     */
    public boolean hasCustomCode()
        {
        return m_fCustom;
        }

    /**
     * @return true iff the property is annotated with one or more annotations that alter the Ref or
     *         Var functionality
     */
    public boolean hasRefAnnotations()
        {
        return m_structProp != null && m_structProp.getRefAnnotations().length > 0;
        }

    /**
     * @return the property's Ref/Var annotations
     */
    public Annotation[] getRefAnnotations()
        {
        return m_structProp == null
                ? Annotation.NO_ANNOTATIONS
                : m_structProp.getRefAnnotations();
        }

    /**
     * @return true iff the property has a getter method
     */
    public boolean hasGetter()
        {
        return m_effectGet != Effect.None;
        }

    /**
     * @return true iff the property has a setter method
     */
    public boolean hasSetter()
        {
        return m_effectSet != Effect.None;
        }

    /**
     * @return true iff the property has a getter method that blocks the invocation of its super
     *         method
     */
    public boolean isGetterBlockingSuper()
        {
        return m_effectGet == Effect.BlocksSuper;
        }

    /**
     * @return true iff the property has a setter method that blocks the invocation of its super
     *         method
     */
    public boolean isSetterBlockingSuper()
        {
        return m_effectSet == Effect.BlocksSuper;
        }

    /**
     * @return true iff the property body is abstract, which means that it comes from an interface
     *         or "into" clause, or is annotated with "@Abstract"
     */
    public boolean isAbstract()
        {
        switch (getImplementation())
            {
            case Delegating:
            case Native:
                return false;

            case Explicit:
                return isExplicitAbstract();

            default:
                return true;
            }
        }

    /**
     * @return true iff the property is synthetic
     */
    public boolean isSynthetic()
        {
        PropertyStructure prop = m_structProp;
        return prop != null && prop.isSynthetic();
        }

    /**
     * @return true if the property is annotated by "@Abstract"
     */
    public boolean isExplicitAbstract()
        {
        PropertyStructure prop = m_structProp;
        return prop != null && m_impl != Implementation.Implicit
                && TypeInfo.containsAnnotation(prop.getPropertyAnnotations(), "Abstract");
        }

    /**
     * @return true if the property is annotated by "@Override"
     */
    public boolean isExplicitOverride()
        {
        PropertyStructure prop = m_structProp;
        return prop != null && m_impl != Implementation.Implicit && prop.isExplicitOverride();
        }

    /**
     * @return true if the property is annotated by "@RO"
     */
    public boolean isExplicitReadOnly()
        {
        PropertyStructure prop = m_structProp;
        return prop != null && m_impl != Implementation.Implicit && prop.isExplicitReadOnly();
        }

    /**
     * @return true if the property is annotated by "@Inject"
     */
    public boolean isInjected()
        {
        PropertyStructure prop = m_structProp;
        return prop != null && m_impl != Implementation.Implicit && prop.isInjected();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return getIdentity().hashCode();
        }

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof PropertyBody))
            {
            return false;
            }

        PropertyBody that = (PropertyBody) obj;
        return Handy.equals(this.m_structProp, that.m_structProp)
            && this.m_type .equals(that.m_type)
            && this.m_impl      == that.m_impl
            && this.m_effectGet == that.m_effectGet
            && this.m_effectSet == that.m_effectSet
            && this.m_fRO       == that.m_fRO
            && this.m_fRW       == that.m_fRW
            && this.m_fCustom   == that.m_fCustom
            && this.m_fField    == that.m_fField
            && this.m_fConstant == that.m_fConstant
            && Handy.equals(this.m_infoFormal   , that.m_infoFormal)
            && Handy.equals(this.m_constDelegate, that.m_constDelegate)
            && Handy.equals(this.m_constInitVal , that.m_constInitVal )
            && Handy.equals(this.m_constInitFunc, that.m_constInitFunc);
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(m_type.getValueString())
          .append(' ')
          .append(getName())
          .append("; (id=")
          .append(getIdentity().getValueString())
          .append(", impl=")
          .append(m_impl);

        if (m_infoFormal != null)
            {
            sb.append(", formal");
            }
        if (m_fRO)
            {
            sb.append(", RO");
            }
        if (m_fRW)
            {
            sb.append(", RW");
            }
        if (m_fConstant)
            {
            sb.append(", constant");
            }
        if (m_fField)
            {
            sb.append(", has-field");
            }
        if (m_fCustom)
            {
            sb.append(", has-code");
            }

        if (isInjected())
            {
            sb.append(", @Inject");
            }
        if (isExplicitAbstract())
            {
            sb.append(", @Abstract");
            }
        if (isExplicitOverride())
            {
            sb.append(", @Override");
            }
        if (isExplicitReadOnly())
            {
            sb.append(", @RO");
            }

        if (m_constInitVal != null)
            {
            sb.append(", has-init-value");
            }
        if (m_constInitFunc != null)
            {
            sb.append(", has-init-fn");
            }
        if (m_constDelegate != null)
            {
            sb.append(", delegate=")
              .append(m_constDelegate);
            }

        return sb.append(')').toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Represents the presence and effect of a "get()" or "set()" method.
     */
    enum Effect {None, BlocksSuper, MayUseSuper}

    /**
     * The property's underlying structure.
     */
    private final PropertyStructure m_structProp;

    /**
     * The implementation type for the property body.
     */
    private final Implementation m_impl;

    /**
     * For Implementation Delegating, this specifies the property which contains the reference
     * to delegate to.
     */
    private final PropertyConstant m_constDelegate;

    /**
     * Formal type parameter information.
     */
    private final ParamInfo m_infoFormal;

    /**
     * Type of the property, including any annotations on the type.
     */
    private final TypeConstant m_type;

    /**
     * True iff the property is explicitly a Ref and not a Var.
     */
    private final boolean m_fRO;

    /**
     * True iff the property is explicitly a Var and not a Ref.
     */
    private final boolean m_fRW;

    /**
     * True to indicate that the property has custom code that overrides the underlying Ref/Var
     * implementation.
     */
    private final boolean m_fCustom;

    /**
     * Represents the presence and effect of a "get()".
     */
    private final Effect m_effectGet;

    /**
     * Represents the presence and effect of a "set()".
     */
    private final Effect m_effectSet;

    /**
     * True iff the property requires a field. A field is assumed to exist iff the property is a
     * non-constant, non-type-parameter, non-interface property, @Abstract is not specified, @Inject
     * is not specified, @RO is not specified, @Override is not specified, and the Ref.get() does
     * not block its super.
     */
    private final boolean m_fField;

    /**
     * True iff the property is a constant value.
     */
    private final boolean m_fConstant;

    /**
     * The initial value of the property, as a constant.
     */
    private final Constant m_constInitVal;

    /**
     * The function that provides the initial value of the property.
     */
    private final MethodConstant m_constInitFunc;
    }
