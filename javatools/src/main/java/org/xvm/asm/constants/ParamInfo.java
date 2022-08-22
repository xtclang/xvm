package org.xvm.asm.constants;


import org.xvm.asm.Constant.Format;


/**
 * Represents information about a single, named type parameter.
 * <p/>
 * The ParamInfo does not maintain a reference to the containing TypeInfo, and is not modified after
 * construction, so it can be referenced by any number of containing TypeInfo objects.
 */
public class ParamInfo
    {
    /**
     * Construct a ParamInfo.
     *
     * @param sName           the name of the type parameter (required)
     * @param typeConstraint  the type constraint for the type parameter (required)
     * @param typeActual      the actual type of the type parameter; pass null to indicate that the
     *                        type parameter does not have a specified actual type, which causes
     *                        the actual type to default to the constraint type
     */
    public ParamInfo(String sName, TypeConstant typeConstraint, TypeConstant typeActual)
        {
        this(sName, sName, typeConstraint, typeActual);
        }

    /**
     * Construct a ParamInfo.
     *
     * @param sName           the name of the type parameter (required)
     * @param typeConstraint  the type constraint for the type parameter (required)
     * @param typeActual      the actual type of the type parameter; pass null to indicate that the
     *                        type parameter does not have a specified actual type, which causes
     *                        the actual type to default to the constraint type
     */
    public ParamInfo(Object nid, String sName, TypeConstant typeConstraint, TypeConstant typeActual)
        {
        assert nid != null;
        assert sName != null;
        assert typeConstraint != null;

        f_nid            = nid;
        f_sName          = sName;
        f_typeConstraint = typeConstraint;
        f_typeActual     = typeActual;
        }

    /**
     * @return the name of the type parameter
     */
    public String getName()
        {
        return f_sName;
        }

    /**
     * @return the type that the type parameter must be
     */
    public TypeConstant getConstraintType()
        {
        return f_typeConstraint;
        }

    /**
     * @return the actual type to use for the type parameter (defaults to the constraint type)
     */
    public TypeConstant getActualType()
        {
        return f_typeActual == null ? f_typeConstraint : f_typeActual;
        }

    /**
     * @return true iff the type parameter had an actual type specified for it
     */
    public boolean isActualTypeSpecified()
        {
        return f_typeActual != null;
        }

    public Object getNestedIdentity()
        {
        return f_nid;
        }

    /**
     * @return true iff the type parameter's actual is a formal type
     */
    public boolean isFormalType()
        {
        TypeConstant typeActual = f_typeActual;
        return typeActual != null
                && typeActual.isSingleDefiningConstant()
                && typeActual.getDefiningConstant().getFormat() == Format.Property;
        }

    /**
     * @return the type parameter's formal type name
     */
    public String getFormalTypeName()
        {
        assert isFormalType();

        return ((PropertyConstant) f_typeActual.getDefiningConstant()).getName();
        }

    /**
     * @return true iff the type parameter represents a formal type sequence
     */
    public boolean isFormalTypeSequence()
        {
        return f_typeConstraint.isFormalTypeSequence();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("<")
          .append(isActualTypeSpecified() ? getActualType().getValueString() : getName());

        TypeConstant typeConstraint = getConstraintType();
        if (!typeConstraint.equals(typeConstraint.getConstantPool().typeObject()) &&
            !typeConstraint.isTuple())
            {
            sb.append(" extends ")
              .append(typeConstraint.getValueString());
            }

        sb.append(">");

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The nested identity of the type parameter's property.
     */
    private final Object f_nid;

    /**
     * The name of the type parameter.
     */
    private final String f_sName;

    /**
     * The constraint type for the type parameter, which is both the type that constrains what the
     * actual type can be, and provides the default if an actual type is not specified.
     */
    private final TypeConstant f_typeConstraint;

    /**
     * The actual type of te type parameter, which may be null to indicate that an actual type was
     * not specified.
     */
    private final TypeConstant f_typeActual;
    }