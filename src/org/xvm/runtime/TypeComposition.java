package org.xvm.runtime;


import java.util.Collections;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;

import org.xvm.asm.constants.AccessTypeConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.template.xRef.RefHandle;

import org.xvm.util.ListMap;


/**
 * TypeComposition represents a fully resolved class (e.g. ArrayList<String> or
 * @Range Interval<Date>).
 */
public class TypeComposition
    {
    /**
     * The underlying {@link OpSupport} for the inception type.
     */
    private final OpSupport f_support;

    /**
     * The {@link ClassTemplate} for the defining class of the inception type. Note, that the
     * defining class could be {@link org.xvm.asm.constants.NativeRebaseConstant native}.
     */
    private final ClassTemplate f_template;

    /**
     * The inception TypeComposition.
     */
    private final TypeComposition f_clzInception;

    /**
     * The inception type - the maximum of what this type composition could be revealed as.
     *
     * Note: the access of the inception type is always Access.PRIVATE.
     */
    public final TypeConstant f_typeInception;

    /**
     * The type that is revealed by the ObjectHandle that refer to this composition.
     */
    private final TypeConstant f_typeRevealed;

    /**
     * A cache of derivative TypeCompositions keyed by the "revealed type".
     */
    private final Map<TypeConstant, TypeComposition> f_mapCompositions;

    // cached method call chain (the top-most method first)
    private final Map<SignatureConstant, CallChain> f_mapMethods;

    // cached property getter call chain (the top-most method first)
    private final Map<String, CallChain> f_mapGetters;

    // cached property setter call chain (the top-most method first)
    private final Map<String, CallChain> f_mapSetters;

    // cached map of fields (values are always nulls)
    private final Map<String, ObjectHandle> f_mapFields;

    /**
     * Construct the TypeComposition for a given "inception" type and a "revealed" type.
     *
     * The guarantees for the inception type are:
     *  - it has to be a class (TypeConstant.isClass())
     *  - it cannot be abstract
     *  - the only modifying types that are allowed are AnnotatedTypeConstant(s) and
     *    ParameterizedTypeConstant(s)
     *
     * @param support        the OpSupport implementation for the inception type
     * @param typeInception  the "origin type"
     * @param typeRevealed   the type to reveal an ObjectHandle reference to this class as
     */
    protected TypeComposition(OpSupport support, TypeConstant typeInception, TypeConstant typeRevealed)
        {
        assert typeInception.isSingleDefiningConstant();
        assert typeInception.getAccess() == Access.PUBLIC;

        // TODO: it should be a "super-private", allowing private access per inheritance level
        typeInception = typeInception.getConstantPool().
            ensureAccessTypeConstant(typeInception, Access.PRIVATE);

        f_clzInception = this;
        f_support = support;
        f_template = support.getTemplate(typeInception);
        f_typeInception = typeInception;
        f_typeRevealed = typeRevealed;
        f_mapCompositions = new ConcurrentHashMap<>();
        f_mapMethods = new ConcurrentHashMap<>();
        f_mapGetters = new ConcurrentHashMap<>();
        f_mapSetters = new ConcurrentHashMap<>();
        f_mapFields  = f_template.isGenericHandle() ? ensureFields() : null;
        }

    /**
     * Construct a TypeComposition for a masked or revealed type.
     */
    private TypeComposition(TypeComposition clzInception, TypeConstant typeRevealed)
        {
        f_clzInception = clzInception;
        f_support = clzInception.f_support;
        f_template = clzInception.f_template;
        f_typeInception = clzInception.f_typeInception;
        f_typeRevealed = typeRevealed;
        f_mapCompositions = f_clzInception.f_mapCompositions;
        f_mapMethods = f_clzInception.f_mapMethods;
        f_mapGetters = f_clzInception.f_mapGetters;
        f_mapSetters = f_clzInception.f_mapSetters;
        f_mapFields = f_clzInception.f_mapFields;
        }

    /**
     * @return the OpSupport for the inception type of this TypeComposition
     */
    public OpSupport getSupport()
        {
        return f_support;
        }
    /**
     * @return the template for the defining class for the inception type
     */
    public ClassTemplate getTemplate()
        {
        return f_template;
        }

    /**
     * @return the current (revealed) type of this TypeComposition
     */
    public TypeConstant getType()
        {
        return f_typeRevealed;
        }

    /**
     * Retrieve a TypeComposition that widens the current type to the specified type.
     *
     * Note that the underlying ClassTemplate doesn't change.
     */
    public TypeComposition maskAs(TypeConstant type)
        {
        if (type.equals(f_typeRevealed))
            {
            return this;
            }

        if (!f_typeRevealed.isA(type))
            {
            throw new IllegalArgumentException("Type " + f_typeRevealed + " cannot be widened to " + type);
            }

        return f_mapCompositions.computeIfAbsent(type, typeR -> new TypeComposition(this, typeR));
        }

    /**
     * Retrieve a TypeComposition that widens the actual type to the specified type.
     *
     * Note that the underlying ClassTemplate doesn't change.
     */
    public TypeComposition revealAs(TypeConstant type, Container container)
        {
        // TODO: this is only allowed within the container that created the original TypeComposition

        if (type.equals(f_typeRevealed))
            {
            return this;
            }

        if (!f_typeInception.isA(type))
            {
            throw new IllegalArgumentException("Type " + f_typeInception + " cannot be revealed as " + type);
            }

        return f_mapCompositions.computeIfAbsent(type, typeR -> new TypeComposition(this, typeR));
        }

    public ObjectHandle ensureOrigin(ObjectHandle handle)
        {
        assert handle.getComposition() == this;

        return isInception() ? handle : handle.cloneAs(f_clzInception);
        }

    /**
     * @return an equivalent ObjectHandle for the specified access
     */
    public ObjectHandle ensureAccess(ObjectHandle handle, Access access)
        {
        assert handle.getComposition() == this;

        return access == f_typeRevealed.getAccess()
            ? handle
            : handle.cloneAs(ensureAccess(access));
        }

    /**
     * @return an associated TypeComposition for the specified access
     */
    public TypeComposition ensureAccess(Access access)
        {
        TypeConstant typeCurrent = f_typeRevealed;

        Access accessCurrent = typeCurrent.getAccess();
        if (accessCurrent == access)
            {
            return this;
            }

        if (typeCurrent instanceof AccessTypeConstant)
            {
            // strip the access
            typeCurrent = typeCurrent.getUnderlyingType();
            }

        ConstantPool pool = typeCurrent.getConstantPool();
        TypeConstant typeTarget;
        switch (access)
            {
            case PUBLIC:
                typeTarget = typeCurrent;
                if (typeTarget.equals(f_clzInception.f_typeRevealed))
                    {
                    return f_clzInception;
                    }
                break;

            case PROTECTED:
                typeTarget = pool.ensureAccessTypeConstant(typeCurrent, Access.PROTECTED);
                break;

            case PRIVATE:
                typeTarget = pool.ensureAccessTypeConstant(typeCurrent, Access.PRIVATE);
                break;

            case STRUCT:
                typeTarget = pool.ensureAccessTypeConstant(typeCurrent, Access.STRUCT);
                break;

            default:
                throw new IllegalStateException();
            }

        return f_mapCompositions.computeIfAbsent(
            typeTarget, typeR -> new TypeComposition(f_clzInception, typeR));
        }

    /**
     * @return true iff this TypeComposition represents an inception class
     */
    public boolean isInception()
        {
        return this == f_clzInception;
        }


    /**
     * @return true iff the revealed type is a struct
     */
    public boolean isStruct()
        {
        return f_typeRevealed.getAccess() == Access.STRUCT;
        }

    /**
     * @return true iff the revealed type has a formal type parameter with the specified name
     */
    public boolean isGenericType(String sName)
        {
        return f_typeRevealed.isGenericType(sName);
        }

    /**
     * @return true iff the inception type represents a service
     */
    public boolean isService()
        {
        TypeConstant type = f_typeInception;
        return type.getSingleUnderlyingClass(false).getComponent().getFormat() == Format.SERVICE;
        }

    /**
     * @return true iff the inception type represents a const
     */
    public boolean isConst()
        {
        TypeConstant type = f_typeInception;
        return ((ClassStructure) type.getSingleUnderlyingClass(false).getComponent()).isConst();
        }

    /**
     * Find the type for the specified formal parameter. Note that the formal name could be declared
     * by some contributions, rather than this class itself.
     *
     * @param sName  the formal parameter name
     *
     * @return the corresponding actual type
     */
    public TypeConstant getActualParamType(String sName)
        {
        return f_typeRevealed.getGenericParamType(sName);
        }

    // is the revealed type of this class assignable to the revealed type of the specified class
    public boolean isA(TypeComposition that)
        {
        return this.f_typeRevealed.isA(that.f_typeRevealed);
        }

    // retrieve the call chain for the specified method
    public CallChain getMethodCallChain(SignatureConstant constSignature)
        {
        // we only cache the PUBLIC access chains; all others are only cached at the op-code level
        return f_mapMethods.computeIfAbsent(constSignature, this::collectMethodCallChain);
        }

    protected CallChain collectMethodCallChain(SignatureConstant constSignature)
        {
        TypeConstant typeActual = f_typeInception;

        TypeInfo info = typeActual.ensureTypeInfo();
        return new CallChain(info.getOptimizedMethodChain(constSignature));
        }

    public PropertyInfo getPropertyInfo(String sPropName)
        {
        return f_typeInception.ensureTypeInfo().findProperty(sPropName);
        }

    // retrieve the call chain for the specified property
    public CallChain getPropertyGetterChain(String sProperty)
        {
        return f_mapGetters.computeIfAbsent(sProperty, sPropName ->
                collectPropertyCallChain(sPropName, true));
        }

    public CallChain getPropertySetterChain(String sProperty)
        {
        return f_mapSetters.computeIfAbsent(sProperty, sPropName ->
                collectPropertyCallChain(sPropName, false));
        }

    protected CallChain collectPropertyCallChain(String sPropName, boolean fGetter)
        {
        TypeInfo     info = f_typeInception.ensureTypeInfo();
        PropertyInfo prop = info.findProperty(sPropName);

        PropertyConstant constProp = prop.getIdentity(); // TODO: this should be passed in

        return new CallChain(fGetter
            ? info.getOptimizedGetChain(constProp)
            : info.getOptimizedSetChain(constProp));
        }

    // return the set of field names
    public Set<String> getFieldNames()
        {
        Map mapCached = f_mapFields;
        return mapCached == null ? Collections.EMPTY_SET : mapCached.keySet();
        }

    // create unassigned (with a null value) entries for all fields
    protected Map<String, ObjectHandle> createFields()
        {
        Map mapCached = f_mapFields;
        if (mapCached == null)
            {
            return null;
            }

        Map<String, ObjectHandle> mapFields = new ListMap<>();
        mapFields.putAll(mapCached);
        return mapFields;
        }

    private Map<String, ObjectHandle> ensureFields()
        {
        ConstantPool pool = f_typeInception.getConstantPool();

        TypeConstant typeStruct = pool.ensureAccessTypeConstant(
            f_typeInception.getUnderlyingType(), Access.STRUCT);
        TypeInfo infoStruct = typeStruct.ensureTypeInfo();

        Map mapFields = new ListMap<>();
        for (Map.Entry<PropertyConstant, PropertyInfo> entry :
                infoStruct.getProperties().entrySet())
            {
            String sPropName = entry.getKey().getName();
            PropertyInfo infoProp = entry.getValue();

            RefHandle hRef = null;
            if (infoProp.hasField())
                {
                if (infoProp.isRefAnnotated())
                    {
                    TypeComposition clzRef =
                        f_template.f_templates.resolveClass(infoProp.getRefType());

                    hRef = ((VarSupport) clzRef.getSupport()).
                        createRefHandle(clzRef, sPropName);
                    }
                mapFields.put(sPropName, hRef);
                }
            }
        return mapFields.isEmpty() ? null : mapFields;
        }

    @Override
    public int hashCode()
        {
        return f_typeRevealed.hashCode();
        }

    @Override
    public boolean equals(Object obj)
        {
        // type compositions are singletons
        return this == obj;
        }

    @Override
    public String toString()
        {
        return f_typeRevealed.getValueString();
        }
    }
