package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.Annotation;
import org.xvm.asm.Component.ResolutionCollector;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;

import org.xvm.util.ListMap;
import org.xvm.util.Severity;


/**
 * Represent a constant that specifies the union ("+") of two types.
 */
public class UnionTypeConstant
        extends RelationalTypeConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public UnionTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }

    /**
     * Construct a constant whose value is the union of two types.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param constType1  the first TypeConstant to union
     * @param constType2  the second TypeConstant to union
     */
    public UnionTypeConstant(ConstantPool pool, TypeConstant constType1, TypeConstant constType2)
        {
        super(pool, constType1, constType2);
        }

    @Override
    protected TypeConstant cloneRelational(ConstantPool pool, TypeConstant type1, TypeConstant type2)
        {
        return pool.ensureUnionTypeConstant(type1, type2);
        }

    @Override
    protected TypeConstant simplifyOrClone(ConstantPool pool, TypeConstant type1, TypeConstant type2)
        {
        if (type1.isA(type2))
            {
            return type1;
            }
        if (type2.isA(type1))
            {
            return type2;
            }
        return cloneRelational(pool, type1, type2);
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isImmutabilitySpecified()
        {
        return m_constType1.isImmutabilitySpecified() || m_constType2.isImmutabilitySpecified();
        }

    @Override
    public boolean isImmutable()
        {
        return m_constType1.isImmutable() || m_constType2.isImmutable();
        }

    @Override
    public boolean isNullable()
        {
        return m_constType1.isNullable() && m_constType2.isNullable();
        }

    @Override
    public TypeConstant removeNullable(ConstantPool pool)
        {
        return isNullable()
                ? pool.ensureUnionTypeConstant(m_constType1.removeNullable(pool),
                                               m_constType2.removeNullable(pool))
                : this;
        }

    @Override
    public Category getCategory()
        {
        // a union of classes is a class;
        // a union of a class and an interface is a class
        // a union of interfaces is an interface

        Category cat1 = m_constType1.getCategory();
        Category cat2 = m_constType2.getCategory();

        switch (cat1)
            {
            case CLASS:
                switch (cat2)
                    {
                    case CLASS:
                    case IFACE:
                        return Category.CLASS;

                    default:
                        return Category.OTHER;
                    }

            case IFACE:
                switch (cat2)
                    {
                    case CLASS:
                        return Category.CLASS;

                    case IFACE:
                        return Category.IFACE;

                    default:
                        return Category.OTHER;
                    }

            default:
                return Category.OTHER;
            }
        }

    @Override
    public boolean isSingleUnderlyingClass(boolean fAllowInterface)
        {
        return m_constType1.isSingleUnderlyingClass(fAllowInterface)
             ^ m_constType2.isSingleUnderlyingClass(fAllowInterface);
        }

    @Override
    public IdentityConstant getSingleUnderlyingClass(boolean fAllowInterface)
        {
        assert isSingleUnderlyingClass(fAllowInterface);

        return m_constType1.isSingleUnderlyingClass(fAllowInterface)
                ? m_constType1.getSingleUnderlyingClass(fAllowInterface)
                : m_constType2.getSingleUnderlyingClass(fAllowInterface);
        }

    @Override
    public boolean containsGenericParam(String sName)
        {
        return m_constType1.containsGenericParam(sName)
            || m_constType2.containsGenericParam(sName);
        }

    @Override
    protected TypeConstant getGenericParamType(String sName, List<TypeConstant> listParams)
        {
        // for Union types, either side needs to find it, but if both do, take the narrower one
        TypeConstant typeActual1 = m_constType1.getGenericParamType(sName, listParams);
        TypeConstant typeActual2 = m_constType2.getGenericParamType(sName, listParams);

        if (typeActual1 == null)
            {
            return typeActual2;
            }
        if (typeActual2 == null)
            {
            return typeActual1;
            }

        return typeActual1.isA(typeActual2)
                ? typeActual1
                : typeActual2.isA(typeActual1)
                    ? typeActual2
                    : getConstantPool().ensureUnionTypeConstant(typeActual1, typeActual2);
        }

    @Override
    public ResolutionResult resolveContributedName(String sName, ResolutionCollector collector)
        {
        // for the UnionType to contribute a name, either side needs to find it
        ResolutionResult result1 = m_constType1.resolveContributedName(sName, collector);
        if (result1 == ResolutionResult.RESOLVED)
            {
            return result1;
            }

        ResolutionResult result2 = m_constType2.resolveContributedName(sName, collector);
        if (result2 == ResolutionResult.RESOLVED)
            {
            return result2;
            }

        // combine the results
        switch (result1)
            {
            case POSSIBLE:
            case DEFERRED:
            case ERROR:
                return result1;

            case UNKNOWN:
            default:
                return result2;
            }
        }

    @Override
    public boolean supportsCompare(ConstantPool pool, TypeConstant that, boolean fThatIsConstant)
        {
        if (that instanceof UnionTypeConstant)
            {
            TypeConstant type1This = m_constType1;
            TypeConstant type2This = m_constType2;
            TypeConstant type1That = ((UnionTypeConstant) that).m_constType1;
            TypeConstant type2That = ((UnionTypeConstant) that).m_constType2;

            if (type1This.equals(type1That) && type2This.equals(type2That))
                {
                return type1This.supportsCompare(pool, type1That, false)
                    || type2This.supportsCompare(pool, type2That, false);
                }
            if (type1This.equals(type2That) && type2This.equals(type1That))
                {
                return type1This.supportsCompare(pool, type2That, false)
                    || type2This.supportsCompare(pool, type1That, false);
                }
            }
        return false;
        }

    @Override
    protected TypeInfo buildTypeInfo(ErrorListener errs)
        {
        TypeConstant type1   = m_constType1;
        TypeConstant type2   = m_constType2;
        int          cInvals = getConstantPool().getInvalidationCount();
        TypeInfo     info1   = type1.ensureTypeInfo(errs);
        TypeInfo     info2   = type2.ensureTypeInfo(errs);

        return new TypeInfo(this,
                            cInvals,
                            null,                   // struct
                            0,                      // depth
                            false,                  // synthetic
                            mergeTypeParams(info1, info2, errs),
                            mergeAnnotations(info1, info2, errs),
                            null,                   // typeExtends
                            null,                   // typeRebase
                            null,                   // typeInto
                            Collections.EMPTY_LIST, // listProcess,
                            ListMap.EMPTY,          // listmapClassChain
                            ListMap.EMPTY,          // listmapDefaultChain
                            mergeProperties(info1, info2, errs),
                            mergeMethods(info1, info2, errs),
                            Collections.EMPTY_MAP,  // mapVirtProps
                            Collections.EMPTY_MAP,  // mapVirtMethods
                            info1.getProgress().worstOf(info2.getProgress())
                            );
        }

    protected Map<Object, ParamInfo> mergeTypeParams(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
        Map<Object, ParamInfo> map1 = info1.getTypeParams();
        Map<Object, ParamInfo> map2 = info2.getTypeParams();
        Map<Object, ParamInfo> map  = new HashMap<>(map1);

        for (Iterator<Map.Entry<Object, ParamInfo>> iter = map.entrySet().iterator(); iter.hasNext();)
            {
            Map.Entry<Object, ParamInfo> entry = iter.next();

            Object nid = entry.getKey();

            ParamInfo param2 = map2.get(nid);
            if (param2 == null)
                {
                // the type param is missing in the second map; keep it "as is"
                continue;
                }

            // the type param exists in both maps; ensure the types are compatible
            // and choose the wider one
            ParamInfo    param1 = entry.getValue();
            TypeConstant type1  = param1.getActualType();
            TypeConstant type2  = param2.getActualType();

            if (type2.isAssignableTo(type1))
                {
                // param1 is good
                // REVIEW should we compare the constraint types as well?
                continue;
                }

            if (type1.isAssignableTo(type2))
                {
                // param2 is good; replace
                entry.setValue(param2);
                continue;
                }

            log(errs, Severity.ERROR, VE_TYPE_PARAM_INCOMPATIBLE_CONTRIB,
                info1.getType().getValueString(), nid, type1.getValueString(),
                info2.getType().getValueString(), type2.getValueString());
            }

        for (Iterator<Map.Entry<Object, ParamInfo>> iter = map2.entrySet().iterator(); iter.hasNext();)
            {
            Map.Entry<Object, ParamInfo> entry = iter.next();

            Object nid = entry.getKey();

            ParamInfo param1 = map1.get(nid);
            if (param1 == null)
                {
                // the type param is missing in the first map; add it "as is"
                map.put(nid, entry.getValue());
                }
            }

        return map;
        }

    protected Annotation[] mergeAnnotations(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
        // TODO
        return null;
        }

    protected Map<PropertyConstant, PropertyInfo> mergeProperties(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
        Map<PropertyConstant, PropertyInfo> map = new HashMap<>();

        // take only non-nested properties
        for (Map.Entry<String, PropertyInfo> entry : info1.ensurePropertiesByName().entrySet())
            {
            String sName = entry.getKey();

            PropertyInfo prop1 = info1.findProperty(sName);
            assert prop1 != null;

            PropertyInfo prop2 = info2.findProperty(sName);
            if (prop2 == null)
                {
                // the property is only in the first map
                map.put(prop1.getIdentity(), prop1);
                }
            else
                {
                // the property exists in both maps;
                // TODO: check for the type compatibility and maybe a "common" structure
                //       and then choose/build the best PropertyInfo

                map.put(prop1.getIdentity(), prop1);
                }
            }

        for (Map.Entry<String, PropertyInfo> entry : info2.ensurePropertiesByName().entrySet())
            {
            String sName = entry.getKey();

            PropertyInfo prop2 = info2.findProperty(sName);
            assert prop2 != null;

            PropertyInfo prop1 = info2.findProperty(sName);
            if (prop1 == null)
                {
                // the property is only in the second map
                map.put(prop2.getIdentity(), prop2);
                }
            }
        return map;
        }

    protected Map<MethodConstant, MethodInfo> mergeMethods(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
        Map<MethodConstant, MethodInfo> map = new HashMap<>();

        // take only non-nested methods
        for (Map.Entry<SignatureConstant, MethodInfo> entry : info1.ensureMethodsBySignature().entrySet())
            {
            SignatureConstant sig = entry.getKey();

            MethodInfo method1 = info1.getMethodBySignature(sig);
            assert method1 != null;

            if (method1.isConstructor())
                {
                continue;
                }

            MethodInfo method2 = info2.getMethodBySignature(sig);
            if (method2 == null)
                {
                // the method is only in the first map
                map.put(method1.getIdentity(), method1);
                }
            else
                {
                // the method exists in both maps;
                // TODO: check for the compatibility and choose the best MethodInfo

                map.put(method1.getIdentity(), method1);
                }
            }

        for (Map.Entry<SignatureConstant, MethodInfo> entry : info2.ensureMethodsBySignature().entrySet())
            {
            SignatureConstant sig = entry.getKey();

            MethodInfo method2 = info2.getMethodBySignature(sig);
            assert method2 != null;

            if (method2.isConstructor())
                {
                continue;
                }

            MethodInfo method1 = info1.getMethodBySignature(sig);
            if (method1 == null)
                {
                // the method is only in the first map
                map.put(method2.getIdentity(), method2);
                }
            }
        return map;
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        // A + B <= A' + B' must be decomposed from the left
        if (typeLeft instanceof UnionTypeConstant || typeLeft.isAnnotated())
            {
            return super.calculateRelationToLeft(typeLeft);
            }
        TypeConstant thisRight1 = getUnderlyingType();
        TypeConstant thisRight2 = getUnderlyingType2();

        Relation rel1 = thisRight1.calculateRelation(typeLeft);
        Relation rel2 = thisRight2.calculateRelation(typeLeft);
        return rel1.bestOf(rel2);
        }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight)
        {
        TypeConstant thisLeft1 = getUnderlyingType();
        TypeConstant thisLeft2 = getUnderlyingType2();

        Relation rel1 = typeRight.calculateRelation(thisLeft1);
        Relation rel2 = typeRight.calculateRelation(thisLeft2);
        return rel1.worseOf(rel2);
        }

    @Override
    protected Relation findIntersectionContribution(IntersectionTypeConstant typeLeft)
        {
        TypeConstant thisRight1 = getUnderlyingType();
        TypeConstant thisRight2 = getUnderlyingType2();

        Relation rel1 = thisRight1.findIntersectionContribution(typeLeft);
        Relation rel2 = thisRight2.findIntersectionContribution(typeLeft);
        return rel1.bestOf(rel2);
        }

    @Override
    protected Set<SignatureConstant> isInterfaceAssignableFrom(TypeConstant typeRight,
                                                               Access accessLeft, List<TypeConstant> listLeft)
        {
        assert isInterfaceType();

        Set<SignatureConstant> setMiss1 =
                getUnderlyingType().isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);
        Set<SignatureConstant> setMiss2 =
                getUnderlyingType2().isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);

        setMiss1.retainAll(setMiss2); // signatures in both (intersection) are still missing
        return setMiss1;
        }

    @Override
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access, List<TypeConstant> listParams)
        {
        return getUnderlyingType().containsSubstitutableMethod(signature, access, listParams)
            || getUnderlyingType2().containsSubstitutableMethod(signature, access, listParams);
        }


    // ----- run-time support ----------------------------------------------------------------------

    @Override
    public int callEquals(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return Utils.callEqualsSequence(frame,
            m_constType1, m_constType2, hValue1, hValue2, iReturn);
        }

    @Override
    public int callCompare(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return Utils.callCompareSequence(frame,
            m_constType1, m_constType2, hValue1, hValue2, iReturn);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.UnionType;
        }

    @Override
    public String getValueString()
        {
        return m_constType1.getValueString() + " + " + m_constType2.getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return "|".hashCode() ^ m_constType1.hashCode() ^ m_constType2.hashCode();
        }
    }
