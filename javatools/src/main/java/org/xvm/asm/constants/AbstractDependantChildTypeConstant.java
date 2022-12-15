package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ComponentResolver.ResolutionCollector;
import org.xvm.asm.ComponentResolver.ResolutionResult;
import org.xvm.asm.ConstantPool;


/**
 * A base class for TypeConstants based on the parent's type and a child class structure.
 */
public abstract class AbstractDependantChildTypeConstant
        extends AbstractDependantTypeConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a data type.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param typeParent  the parent's type
     */
    public AbstractDependantChildTypeConstant(ConstantPool pool, TypeConstant typeParent)
        {
        super(pool, typeParent);
        }

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public AbstractDependantChildTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }

    /**
     * @return the child ClassStructure associated with this type
     */
    abstract protected ClassStructure getChildStructure();


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isComposedOfAny(Set<IdentityConstant> setIds)
        {
        return setIds.contains(getChildStructure().getIdentityConstant());
        }

    @Override
    public boolean isImmutable()
        {
        return getChildStructure().isImmutable();
        }

    @Override
    public int getMaxParamsCount()
        {
        return getChildStructure().getTypeParams().size();
        }

    @Override
    public TypeConstant adoptParameters(ConstantPool pool, TypeConstant[] atypeParams)
        {
        TypeConstant typeBase = this;
        if (atypeParams == null)
            {
            // this is a "normalization" call
            TypeConstant typeParent  = getParentType();
            TypeConstant typeParentN = typeParent.normalizeParameters();
            if (typeParentN != typeParent)
                {
                typeBase = cloneSingle(pool, typeParentN);
                }
            atypeParams = ConstantPool.NO_TYPES;
            }

        ClassStructure clz = getChildStructure();
        if (clz.isParameterized())
            {
            return pool.ensureParameterizedTypeConstant(typeBase,
                clz.normalizeParameters(pool, atypeParams));
            }

        // not parameterized
        return typeBase;
        }

    @Override
    public TypeConstant[] collectGenericParameters()
        {
        return getChildStructure().getFormalType().getParamTypesArray();
        }

    @Override
    public boolean extendsClass(IdentityConstant constClass)
        {
        return getChildStructure().extendsClass(constClass);
        }

    @Override
    public Category getCategory()
        {
        ClassStructure clz = getChildStructure();
        return clz.getFormat() == Component.Format.INTERFACE
                ? Category.IFACE : Category.CLASS;
        }

    @Override
    public boolean isSingleUnderlyingClass(boolean fAllowInterface)
        {
        return fAllowInterface || getExplicitClassFormat() != Component.Format.INTERFACE;
        }

    @Override
    public IdentityConstant getSingleUnderlyingClass(boolean fAllowInterface)
        {
        assert isSingleUnderlyingClass(fAllowInterface);

        return getChildStructure().getIdentityConstant();
        }

    @Override
    public boolean isExplicitClassIdentity(boolean fAllowParams)
        {
        return true;
        }

    @Override
    public Component.Format getExplicitClassFormat()
        {
        return getChildStructure().getFormat();
        }

    @Override
    public TypeConstant getExplicitClassInto(boolean fResolve)
        {
        ClassStructure struct = getChildStructure();
        if (struct == null || struct.getFormat() != Component.Format.MIXIN)
            {
            throw new IllegalStateException("mixin=" + struct);
            }

        return struct.getTypeInto();
        }

    @Override
    public boolean containsGenericParam(String sName)
        {
        return getChildStructure().containsGenericParamType(sName)
            || m_typeParent.containsGenericParam(sName);
        }

    @Override
    protected TypeConstant getGenericParamType(String sName, List<TypeConstant> listParams)
        {
        ConstantPool pool       = getConstantPool();
        TypeConstant typeParent = m_typeParent;

        TypeConstant type;
        if (typeParent.containsGenericParam(sName))
            {
            // the passed in list applies only to the child and should not be used by the parent
            type = typeParent.getGenericParamType(sName, Collections.EMPTY_LIST);
            }
        else
            {
            TypeConstant typeActual = listParams.isEmpty()
                    ? this
                    : pool.ensureParameterizedTypeConstant(this,
                            listParams.toArray(TypeConstant.NO_TYPES));
            type = getChildStructure().getGenericParamType(pool, sName, typeActual);
            if (type != null)
                {
                type = type.resolveGenerics(pool, typeParent);
                }
            }
        return type;
        }

    @Override
    public boolean isConstant()
        {
        return getChildStructure().isConst();
        }

    @Override
    public ResolutionResult resolveContributedName(
            String sName, Access access, MethodConstant idMethod, ResolutionCollector collector)
        {
        if (containsUnresolved())
            {
            return ResolutionResult.POSSIBLE;
            }

        return getChildStructure().resolveName(sName, access, collector);
        }


    // ----- type comparison support ---------------------------------------------------------------


    @Override
    protected Set<SignatureConstant> isInterfaceAssignableFrom(TypeConstant typeRight, Access accessLeft,
                                                               List<TypeConstant> listLeft)
        {
        ClassStructure clz = getChildStructure();

        assert clz.getFormat() == Component.Format.INTERFACE;

        return clz.isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);
        }

    @Override
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access,
                                               boolean fFunction, List<TypeConstant> listParams)
        {
        return getChildStructure().containsSubstitutableMethod(
                getConstantPool(), signature, access, fFunction, listParams);
        }

    @Override
    public Usage checkConsumption(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        if (!listParams.isEmpty())
            {
            ConstantPool   pool = getConstantPool();
            ClassStructure clz  = getChildStructure();

            Map<StringConstant, TypeConstant> mapFormal = clz.getTypeParams();

            listParams = clz.normalizeParameters(pool, listParams);

            Iterator<TypeConstant> iterParams = listParams.iterator();
            Iterator<StringConstant> iterNames = mapFormal.keySet().iterator();

            while (iterParams.hasNext())
                {
                TypeConstant constParam = iterParams.next();
                String       sFormal    = iterNames.next().getValue();

                if (constParam.consumesFormalType(sTypeName, access)
                        && clz.producesFormalType(pool, sFormal, access, listParams)
                    ||
                    constParam.producesFormalType(sTypeName, access)
                        && clz.consumesFormalType(pool, sFormal, access, listParams))
                    {
                    return Usage.YES;
                    }
                }
            }
        return Usage.NO;
        }

    @Override
    public Usage checkProduction(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        if (!listParams.isEmpty())
            {
            ConstantPool   pool = getConstantPool();
            ClassStructure clz  = getChildStructure();

            Map<StringConstant, TypeConstant> mapFormal = clz.getTypeParams();

            listParams = clz.normalizeParameters(pool, listParams);

            Iterator<TypeConstant>   iterParams = listParams.iterator();
            Iterator<StringConstant> iterNames  = mapFormal.keySet().iterator();

            while (iterParams.hasNext())
                {
                TypeConstant constParam = iterParams.next();
                String       sFormal    = iterNames.next().getValue();

                if (constParam.producesFormalType(sTypeName, access)
                        && clz.producesFormalType(pool, sFormal, access, listParams)
                    ||
                    constParam.consumesFormalType(sTypeName, access)
                        && clz.consumesFormalType(pool, sFormal, access, listParams))
                    {
                    return Usage.YES;
                    }
                }
            }
        return Usage.NO;
        }
    }