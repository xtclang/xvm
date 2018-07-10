package org.xvm.asm.constants;


import java.util.Map;

import org.xvm.asm.Constant.Format;
import org.xvm.asm.ErrorListener;


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

        m_nid            = nid;
        m_sName          = sName;
        m_typeConstraint = typeConstraint;
        m_typeActual     = typeActual;
        }

    /**
     * @return the name of the type parameter
     */
    public String getName()
        {
        return m_sName;
        }

    /**
     * @return the type that the type parameter must be
     */
    public TypeConstant getConstraintType()
        {
        return m_typeConstraint;
        }

    /**
     * @return the actual type to use for the type parameter (defaults to the constraint type)
     */
    public TypeConstant getActualType()
        {
        return m_typeActual == null ? m_typeConstraint : m_typeActual;
        }

    /**
     * @return true iff the type parameter had an actual type specified for it
     */
    public boolean isActualTypeSpecified()
        {
        return m_typeActual != null;
        }

    public Object getNestedIdentity()
        {
        return m_nid;
        }

    public boolean isFormalType()
        {
        TypeConstant typeActual = m_typeActual;
        return typeActual != null
                && typeActual.isSingleDefiningConstant()
                && typeActual.getDefiningConstant().getFormat() == Format.Property;
        }

    public String getFormalTypeName()
        {
        assert isFormalType();

        TypeConstant typeActual = m_typeActual;
        return ((PropertyConstant) typeActual.getDefiningConstant()).getName();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("<")
          .append(isActualTypeSpecified() ? getActualType().getValueString() : getName());

        TypeConstant typeConstraint = getConstraintType();
        if (!typeConstraint.isEcstasy("Object") && !typeConstraint.isTuple())
            {
            sb.append(" extends ")
              .append(typeConstraint.getValueString());
            }

        sb.append(">");

        return sb.toString();
        }


    // ----- inner class: TypeResolver -------------------------------------------------------------

    /**
     * A GenericTypeResolver that works from a TypeInfo's map from property name to ParamInfo.
     */
    public static class TypeResolver
            implements TypeInfo.TypeResolver
        {
        /**
         * Construct a GenericTypeResolver that will use the passed map as its source of type
         * resolution information, reporting any errors to the passed error list.
         *
         * @param idTarget    the identity of the component in which context the resolver operates
         * @param parameters  a map from nested identity (parameter name) to ParamInfo
         * @param errs        the error listener to log any errors to
         */
        public TypeResolver(IdentityConstant idTarget, Map<Object, ParamInfo> parameters, ErrorListener errs)
            {
            assert parameters != null;
            assert errs != null;

            this.idTarget   = idTarget instanceof NativeRebaseConstant
                                ? ((NativeRebaseConstant) idTarget).getClassConstant()
                                : idTarget;
            this.parameters = parameters;
            this.errs       = errs;
            }

        @Override
        public TypeConstant resolveGenericType(PropertyConstant constProperty)
            {
            // substitute only if "in the same context"
            if (constProperty.getParentConstant().equals(idTarget))
                {
                ParamInfo info = parameters.get(constProperty.getName());
                if (info != null && info.isActualTypeSpecified())
                    {
                    return info.getActualType();
                    }
                }
            return null;
            }

        @Override
        public ParamInfo findParamInfo(Object nid)
            {
            return parameters.get(nid);
            }

        @Override
        public void registerParamInfo(Object nid, ParamInfo param)
            {
            parameters.put(nid, param);
            }

        public final IdentityConstant idTarget;

        /*
         * The map from parameter name to ParamInfo to use to resolve generic types.
         */
        public final Map<Object, ParamInfo> parameters;

        /*
         * The error listener to log any errors to.
         */
        public final ErrorListener errs;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The nested identity of the type parameter's property.
     */
    private final Object m_nid;

    /**
     * The name of the type parameter.
     */
    private final String m_sName;

    /**
     * The constraint type for the type parameter, which is both the type that constrains what the
     * actual type can be, and provides the default if an actual type is not specified.
     */
    private final TypeConstant m_typeConstraint;

    /**
     * The actual type of te type parameter, which may be null to indicate that an actual type was
     * not specified.
     */
    private final TypeConstant m_typeActual;
    }
