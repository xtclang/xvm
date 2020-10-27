package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.function.Consumer;

import org.xvm.asm.Annotation;
import org.xvm.asm.Component.ResolutionCollector;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.PropertyStructure;

import org.xvm.runtime.OpSupport;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.util.ListMap;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A synthetic TypeConstant that represents a custom property; for example:
 *
 * <pre><code>
 * class Outer
 *     {
 *     &#64;Lazy String prop.calc()
 *         {
 *         return "hello";
 *         }
 *     static void test()
 *         {
 *         Outer o = new Outer();
 *         Ref&lt;String> ref = o.&amp;p;
 *         ...
 *         }
 *     }
 * </code></pre>
 *
 * The run-time type of the variable ref above is {@code PropertyClassType(T1, "prop")}, where T1
 * is TerminalTypeConstant(Outer).
 */
public class PropertyClassTypeConstant
        extends AbstractDependantTypeConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a data type.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param typeParent  the parent's type
     * @param idProp      the property id
     */
    public PropertyClassTypeConstant(ConstantPool pool, TypeConstant typeParent, PropertyConstant idProp)
        {
        super(pool, typeParent);

        // unlike VirtualChildConstant, it's never unresolved
        if (typeParent.containsUnresolved() || !typeParent.isExplicitClassIdentity(true))
            {
            throw new IllegalArgumentException("parent's must be a resolved single class type");
            }
        if (idProp == null)
            {
            throw new IllegalArgumentException("property is required");
            }

        m_idProp = idProp;
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
    public PropertyClassTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);

        m_iProp = readIndex(in);
        }

    @Override
    protected void resolveConstants()
        {
        super.resolveConstants();

        m_idProp = (PropertyConstant) getConstantPool().getConstant(m_iProp);
        }

    /**
     * @return the property id
     */
    public PropertyConstant getProperty()
        {
        return m_idProp;
        }

    /**
     * @return the PropertyInfo associated with this type
     */
    public PropertyInfo getPropertyInfo()
        {
        PropertyInfo info = m_info;
        if (info == null)
            {
            m_info = info = m_typeParent.ensureTypeInfo().findProperty(m_idProp);
            }
        return info;
        }

    /**
     * @return the property ref type
     */
    public TypeConstant getRefType()
        {
        return getPropertyInfo().getBaseRefType();
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public int getMaxParamsCount()
        {
        return 0;
        }

    @Override
    public Constant getDefiningConstant()
        {
        return getPropertyInfo().getIdentity();
        }

    @Override
    protected TypeConstant cloneSingle(ConstantPool pool, TypeConstant type)
        {
        return pool.ensurePropertyClassTypeConstant(type, m_idProp);
        }

    @Override
    public boolean isAutoNarrowing(boolean fAllowVirtChild)
        {
        return false;
        }

    @Override
    public ResolutionResult resolveContributedName(String sName, Access access, ResolutionCollector collector)
        {
        return ResolutionResult.UNKNOWN;
        }

    @Override
    public TypeConstant resolveTypedefs()
        {
        return this;
        }

    @Override
    public TypeConstant resolveGenerics(ConstantPool pool, GenericTypeResolver resolver)
        {
        return this;
        }

    @Override
    public TypeConstant adoptParameters(ConstantPool pool, TypeConstant[] atypeParams)
        {
        return this;
        }

    @Override
    public TypeConstant[] collectGenericParameters()
        {
        // property class type is not formalizable
        return null;
        }

    @Override
    public TypeConstant resolveAutoNarrowing(ConstantPool pool, boolean fRetainParams, TypeConstant typeTarget)
        {
        return this;
        }

    @Override
    public boolean extendsClass(IdentityConstant constClass)
        {
        PropertyConstant idProp = (PropertyConstant) getDefiningConstant();
        return idProp.getType().extendsClass(constClass);
        }

    @Override
    public Category getCategory()
        {
        return Category.OTHER;
        }

    @Override
    public boolean isSingleUnderlyingClass(boolean fAllowInterface)
        {
        return false;
        }

    @Override
    public boolean containsGenericParam(String sName)
        {
        return getRefType().containsGenericParam(sName)
            || m_typeParent.containsGenericParam(sName);
        }

    @Override
    protected TypeConstant getGenericParamType(String sName, List<TypeConstant> listParams)
        {
        TypeConstant type = getRefType().getGenericParamType(sName, listParams);
        if (type != null)
            {
            return type.isGenericType()
                    ? type.resolveGenerics(getConstantPool(), m_typeParent)
                    : type;
            }

        // the passed in list represents the "child" and should not be used by the parent
        return m_typeParent.getGenericParamType(sName, Collections.EMPTY_LIST);
        }

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        return getRefType().calculateRelationToLeft(typeLeft);
        }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight)
        {
        return getRefType().calculateRelationToRight(typeRight);
        }

    @Override
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access,
                                               boolean fFunction, List<TypeConstant> listParams)
        {
        return getRefType().containsSubstitutableMethod(signature, access, fFunction, listParams);
        }

    @Override
    protected Usage checkConsumption(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        return Usage.NO;
        }

    @Override
    protected Usage checkProduction(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        return Usage.NO;
        }


    // ----- TypeInfo support ----------------------------------------------------------------------

    @Override
    protected TypeInfo buildTypeInfo(ErrorListener errs)
        {
        ConstantPool pool     = getConstantPool();
        int          cInvals  = pool.getInvalidationCount();
        PropertyInfo infoProp = getPropertyInfo();
        TypeConstant typeBase = pool.ensureAccessTypeConstant(infoProp.getBaseRefType(), Access.PROTECTED);
        TypeInfo     infoBase = typeBase.buildTypeInfo(errs);

        PropertyStructure prop = infoProp.getHead().getStructure();

        Map<Object           , ParamInfo>    mapTypeParams      = new HashMap<>();
        Map<PropertyConstant , PropertyInfo> mapContribProps    = new HashMap<>();
        Map<MethodConstant   , MethodInfo  > mapContribMethods  = new HashMap<>();
        ListMap<String       , ChildInfo   > mapContribChildren = new ListMap<>();
        ArrayList<PropertyConstant>          listExplode        = new ArrayList<>();

        IdentityConstant idBase = (IdentityConstant) infoBase.getType().getDefiningConstant();

        collectChildInfo(idBase, false, prop, mapTypeParams,
            mapContribProps, mapContribMethods, mapContribChildren, listExplode, 0, errs);

        if (!listExplode.isEmpty())
            {
            // TODO: explode properties
            }

        if (mapContribProps.isEmpty() && mapContribMethods.isEmpty() && mapContribChildren.isEmpty())
            {
            // nothing has been added
            return infoBase;
            }

        Map<PropertyConstant, PropertyInfo> mapProps       = infoBase.getProperties();
        Map<MethodConstant  , MethodInfo  > mapMethods     = infoBase.getMethods();
        Map<Object          , PropertyInfo> mapVirtProps   = infoBase.getVirtProperties();
        Map<Object          , MethodInfo  > mapVirtMethods = infoBase.getVirtMethods();
        ListMap<String      , ChildInfo   > mapChildren    = new ListMap<>();

        if (!mapContribProps.isEmpty())
            {
            // process properties by moving them to the base ref level
            Map<PropertyConstant, PropertyInfo> mapContrib = new HashMap<>(mapContribProps.size());
            for (Map.Entry<PropertyConstant, PropertyInfo> entry : mapContribProps.entrySet())
                {
                PropertyConstant idContrib = entry.getKey();
                PropertyConstant idReplace = pool.ensurePropertyConstant(idBase, idContrib.getName());
                mapContrib.put(idReplace, entry.getValue());
                }

            layerOnProps(idBase, true, null, mapProps, mapVirtProps, this, mapContrib, errs);
            }

        if (!mapContribMethods.isEmpty())
            {
            // process methods by moving them to the base ref level
            Map<MethodConstant, MethodInfo> mapContrib = new HashMap<>(mapContribMethods.size());
            for (Map.Entry<MethodConstant, MethodInfo> entry : mapContribMethods.entrySet())
                {
                MethodConstant idContrib = entry.getKey();
                MethodConstant idReplace = pool.ensureMethodConstant(idBase, idContrib.getSignature());
                mapContrib.put(idReplace, entry.getValue());
                }
            layerOnMethods(idBase, true, false, null, mapMethods, mapVirtMethods, this, mapContrib, errs);
            }

        if (!mapContribChildren.isEmpty())
            {
            // TODO process children
            }

        return new TypeInfo(this, cInvals, infoBase.getClassStructure(),
                idBase.getNestedDepth() + 1, false, mapTypeParams,
                Annotation.NO_ANNOTATIONS, infoBase.getMixinAnnotations(), typeBase, null, null,
                Collections.EMPTY_LIST, ListMap.EMPTY, ListMap.EMPTY,
                mapProps, mapMethods, mapVirtProps, mapVirtMethods, mapChildren,
                TypeInfo.Progress.Complete);
        }


    // ----- run-time support ----------------------------------------------------------------------

    @Override
    public OpSupport getOpSupport(TemplateRegistry registry)
        {
        return getRefType().getOpSupport(registry);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.PropertyClassType;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        super.forEachUnderlying(visitor);

        visitor.accept(m_idProp);
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        int n = super.compareDetails(obj);
        if (n == 0)
            {
            PropertyClassTypeConstant that = (PropertyClassTypeConstant) obj;
            return this.m_idProp.compareTo(that.m_idProp);
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        return m_typeParent.getValueString() + '.' + m_idProp.getName();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        m_idProp = (PropertyConstant) pool.register(m_idProp);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);

        writePackedLong(out, m_idProp.getPosition());
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_typeParent.hashCode() + m_idProp.hashCode();
        }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The PropertyConstant representing the property.
     */
    protected PropertyConstant m_idProp;

    /**
     * During disassembly, this holds the index of the PropertyConstant.
     */
    private transient int m_iProp;

    /**
     * Cached property info.
     */
    private transient PropertyInfo m_info;
    }
