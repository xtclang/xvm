package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.xvm.asm.Annotation;
import org.xvm.asm.Component;
import org.xvm.asm.Component.ResolutionCollector;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.Component.SimpleCollector;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xOrdered;

import org.xvm.util.ListMap;


/**
 * Represent a constant that specifies the intersection ("|") of two types.
 */
public class IntersectionTypeConstant
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
    public IntersectionTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }

    /**
     * Construct a constant whose value is the intersection of two types.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param constType1  the first TypeConstant to intersect
     * @param constType2  the second TypeConstant to intersect
     */
    public IntersectionTypeConstant(ConstantPool pool, TypeConstant constType1, TypeConstant constType2)
        {
        super(pool, constType1, constType2);
        }

    @Override
    protected TypeConstant cloneRelational(ConstantPool pool, TypeConstant type1, TypeConstant type2)
        {
        return pool.ensureIntersectionTypeConstant(type1, type2);
        }

    @Override
    protected TypeConstant simplifyInternal(TypeConstant type1, TypeConstant type2)
        {
        if (type1.isA(type2))
            {
            return type2;
            }
        if (type2.isA(type1))
            {
            return type1;
            }
        return null;
        }

    /**
     * Collect all parts of this intersection type that extend the specified type.
     *
     * @param typeMatch  the type to match
     * @param setTypes   (optional) the set to add matching types to
     *
     * @return a set containing all matching types
     */
    public Set<TypeConstant> collectMatching(TypeConstant typeMatch, Set<TypeConstant> setTypes)
        {
        if (setTypes == null)
            {
            setTypes = new HashSet<>();
            }
        testMatch(m_constType1, typeMatch, setTypes);
        testMatch(m_constType2, typeMatch, setTypes);
        return setTypes;
        }

    private void testMatch(TypeConstant type1, TypeConstant typeMatch, Set<TypeConstant> set)
        {
        if (type1 instanceof IntersectionTypeConstant)
            {
            ((IntersectionTypeConstant) type1).collectMatching(typeMatch, set);
            }
        else if (type1.isA(typeMatch))
            {
            set.add(type1);
            }
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isImmutabilitySpecified()
        {
        return m_constType1.isImmutabilitySpecified() && m_constType2.isImmutabilitySpecified();
        }

    @Override
    public boolean isImmutable()
        {
        return m_constType1.isImmutable() && m_constType2.isImmutable();
        }

    @Override
    public boolean isNullable()
        {
        return (m_constType1.isOnlyNullable() ^ m_constType2.isOnlyNullable())
             || m_constType1.isNullable()    || m_constType2.isNullable();
        }

    @Override
    public TypeConstant removeNullable()
        {
        if (!isNullable())
            {
            return this;
            }

        if (m_constType1.isOnlyNullable())
            {
            assert !m_constType2.isOnlyNullable();
            return m_constType2.removeNullable();
            }

        if (m_constType2.isOnlyNullable())
            {
            assert !m_constType1.isOnlyNullable();
            return m_constType1.removeNullable();
            }

        return m_constType1.removeNullable().intersect(getConstantPool(),
               m_constType2.removeNullable());
        }

    @Override
    public TypeConstant andNot(ConstantPool pool, TypeConstant that)
        {
        TypeConstant type1 = getUnderlyingType().resolveTypedefs();
        TypeConstant type2 = getUnderlyingType2().resolveTypedefs();

        if (type1.equals(that))
            {
            // (A | B) - A => B
            return type2;
            }

        if (type2.equals(that))
            {
            // (A | B) - B => B
            return type1;
            }

        // recurse to cover cases like this:
        // ((A | B) | C) - B => A | C
        if (type1.isRelationalType() || type2.isRelationalType())
            {
            TypeConstant type1R = type1.andNot(pool, that);
            TypeConstant type2R = type2.andNot(pool, that);
            if (type1R != type1 || type2R != type2)
                {
                return type1R.intersect(pool, type2R);
                }
            }

        if (that instanceof IntersectionTypeConstant)
            {
            // (A | B) - (C | D) => ((A | B) - C) | ((A | B) - D)
            TypeConstant type1R = andNot(pool, that.getUnderlyingType());
            TypeConstant type2R = andNot(pool, that.getUnderlyingType2());
            if (type1R != this || type2R != that)
                {
                return type1R.intersect(pool, type2R);
                }
            }
        else if (that.isFormalType())
            {
            // (A | B) - F => (A | B) - F.constraint
            TypeConstant typeConstraint = that.resolveConstraints();
            return andNot(pool, typeConstraint);
            }

        return super.andNot(pool, that);
        }

    @Override
    public Category getCategory()
        {
        // an intersection of classes is a class;
        // an intersection of interfaces is an interface

        Category cat1 = m_constType1.getCategory();
        Category cat2 = m_constType2.getCategory();

        switch (cat1)
            {
            case CLASS:
                switch (cat2)
                    {
                    case CLASS:
                        return Category.CLASS;

                    default:
                        return Category.OTHER;
                    }

            case IFACE:
                switch (cat2)
                    {
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
            && m_constType2.isSingleUnderlyingClass(fAllowInterface)
            && m_constType1.getSingleUnderlyingClass(fAllowInterface).equals(
               m_constType2.getSingleUnderlyingClass(fAllowInterface));
        }

    @Override
    public IdentityConstant getSingleUnderlyingClass(boolean fAllowInterface)
        {
        assert isSingleUnderlyingClass(fAllowInterface);
        return m_constType1.getSingleUnderlyingClass(fAllowInterface);
        }

    @Override
    public boolean containsGenericParam(String sName)
        {
        return m_constType1.containsGenericParam(sName) && m_constType2.containsGenericParam(sName);
        }

    @Override
    protected TypeConstant getGenericParamType(String sName, List<TypeConstant> listParams)
        {
        // for Intersection types, both sides need to find it and we'll take the wider one
        TypeConstant typeActual1 = m_constType1.getGenericParamType(sName, listParams);
        TypeConstant typeActual2 = m_constType2.getGenericParamType(sName, listParams);

        if (typeActual1 == null || typeActual2 == null)
            {
            return null;
            }

        return typeActual1.isA(typeActual2)
                ? typeActual2
                : typeActual2.isA(typeActual1)
                    ? typeActual1
                    : getConstantPool().ensureIntersectionTypeConstant(typeActual1, typeActual2);
        }

    @Override
    public ResolutionResult resolveContributedName(String sName, Access access, ResolutionCollector collector)
        {
        // for the IntersectionType to contribute a name, either both sides need to find exactly
        // the same component or just one side should find it
        ErrorListener    errs       = collector.getErrorListener();
        SimpleCollector  collector1 = new SimpleCollector(errs);
        ResolutionResult result1    = m_constType1.resolveContributedName(sName, access, collector1);
        SimpleCollector  collector2 = new SimpleCollector(errs);
        ResolutionResult result2    = m_constType2.resolveContributedName(sName, access, collector2);

        if (result1 == ResolutionResult.RESOLVED)
            {
            Constant const1 = collector1.getResolvedConstant();

            if (result2 == ResolutionResult.RESOLVED)
                {
                Constant const2 = collector2.getResolvedConstant();
                if (!const1.equals(const2))
                    {
                    return ResolutionResult.UNKNOWN; // ambiguous
                    }
                }

            collector.resolvedConstant(const1);
            return ResolutionResult.RESOLVED;
            }

        if (result2 == ResolutionResult.RESOLVED)
            {
            Constant const2 = collector2.getResolvedConstant();
            collector.resolvedConstant(const2);
            return ResolutionResult.RESOLVED;
            }

        return result1.combine(result2);
        }

    @Override
    public boolean isIntoClassType()
        {
        return getUnderlyingType().isIntoClassType()
            || getUnderlyingType2().isIntoClassType();
        }

    @Override
    public boolean isIntoPropertyType()
        {
        return getUnderlyingType().isIntoPropertyType()
            || getUnderlyingType2().isIntoPropertyType();
        }

    @Override
    public TypeConstant getIntoPropertyType()
        {
        TypeConstant typeInto1 = getUnderlyingType().getIntoPropertyType();
        TypeConstant typeInto2 = getUnderlyingType2().getIntoPropertyType();

        TypeConstant typeProperty = getConstantPool().typeProperty();
        if (typeInto1 != null && typeInto1.equals(typeProperty))
            {
            return typeInto1;
            }
        if (typeInto2 != null && typeInto2.equals(typeProperty))
            {
            return typeInto2;
            }

        return Objects.equals(typeInto1, typeInto2) ? typeInto1 : null;
        }

    @Override
    public boolean isIntoMethodType()
        {
        return getUnderlyingType().isIntoMethodType()
            || getUnderlyingType2().isIntoMethodType();
        }

    @Override
    public boolean isIntoMethodParameterType()
        {
        return getUnderlyingType().isIntoMethodParameterType()
            || getUnderlyingType2().isIntoMethodParameterType();
        }

    @Override
    public boolean isIntoVariableType()
        {
        return getUnderlyingType().isIntoVariableType()
            || getUnderlyingType2().isIntoVariableType();
        }

    @Override
    public TypeConstant getIntoVariableType()
        {
        TypeConstant typeInto1 = getUnderlyingType().getIntoVariableType();
        TypeConstant typeInto2 = getUnderlyingType2().getIntoVariableType();

        if (typeInto1 == null)
            {
            return typeInto2;
            }

        if (typeInto2 == null)
            {
            return typeInto1;
            }

        ConstantPool pool    = getConstantPool();
        TypeConstant typeVar = pool.typeVar();
        if (typeInto1.equals(typeVar) || typeInto2.equals(typeVar))
            {
            return typeVar;
            }

        return pool.typeRef();
        }

    @Override
    public TypeConstant resolveTypeParameter(TypeConstant typeActual, String sFormalName)
        {
        typeActual = typeActual.resolveTypedefs();

        if (getFormat() == typeActual.getFormat())
            {
            return super.resolveTypeParameter(typeActual, sFormalName);
            }

        // check if any of our legs can unambiguously resolve the formal type
        TypeConstant typeResult1 = getUnderlyingType().resolveTypeParameter(typeActual, sFormalName);
        TypeConstant typeResult2 = getUnderlyingType2().resolveTypeParameter(typeActual, sFormalName);
        return typeResult1 == null
                ? typeResult2
                : typeResult2 == null || typeResult1.equals(typeResult2)
                        ? typeResult1
                        : null;
        }


    // ----- TypeInfo support ----------------------------------------------------------------------

    @Override
    protected Map<Object, ParamInfo> mergeTypeParams(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
        if (info1 == null || info2 == null)
            {
            return Collections.EMPTY_MAP;
            }

        ConstantPool           pool = getConstantPool();
        Map<Object, ParamInfo> map1 = info1.getTypeParams();
        Map<Object, ParamInfo> map2 = info2.getTypeParams();
        Map<Object, ParamInfo> map  = new HashMap<>(map1);

        for (Iterator<Map.Entry<Object, ParamInfo>> iter = map.entrySet().iterator(); iter.hasNext();)
            {
            Map.Entry<Object, ParamInfo> entry = iter.next();

            Object nid = entry.getKey();

            ParamInfo param2 = map2.get(nid);
            if (param2 != null)
                {
                // the type param exists in both maps; ensure the types are compatible
                // and choose the wider one
                ParamInfo param1 = entry.getValue();

                assert param1.getName().equals(param2.getName());

                TypeConstant type1 = param1.getActualType();
                TypeConstant type2 = param2.getActualType();

                if (type2.isA(type1))
                    {
                    // param1 is good
                    // REVIEW should we compare the constraint types as well?
                    continue;
                    }

                if (type1.isA(type2))
                    {
                    // param2 is good; replace
                    entry.setValue(param2);
                    continue;
                    }

                TypeConstant typeParam = type1.intersect(pool, type2);
                entry.setValue(new ParamInfo(param1.getName(), pool.typeObject(), typeParam));
                }

            // the type param is missing in the second map or incompatible; remove it
            iter.remove();
            }
        return map;
        }

    @Override
    protected Annotation[] mergeAnnotations(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
        // TODO
        return null;
        }

    @Override
    protected Map<PropertyConstant, PropertyInfo> mergeProperties(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
        if (info1 == null || info2 == null)
            {
            return Collections.EMPTY_MAP;
            }

        Map<PropertyConstant, PropertyInfo> map = new HashMap<>();

        NextEntry:
        for (Map.Entry<String, PropertyInfo> entry : info1.ensurePropertiesByName().entrySet())
            {
            String sName = entry.getKey();

            PropertyInfo prop2 = info2.findProperty(sName);
            if (prop2 != null)
                {
                // the property exists in both maps;
                // check for a "common" structure and build a "subset" info

                PropertyInfo prop1 = entry.getValue();
                if (prop1.equals(prop2))
                    {
                    // trivial "equality" scenario
                    map.put(prop1.getIdentity(), prop1);
                    continue;
                    }

                PropertyBody[] abody1 = prop1.getPropertyBodies();
                PropertyBody[] abody2 = prop2.getPropertyBodies();

                for (PropertyBody body1 : abody1)
                    {
                    PropertyConstant id1 = body1.getIdentity();

                    for (PropertyBody body2 : abody2)
                        {
                        if (body2.getIdentity().equals(id1))
                            {
                            // for now we use just one body; may need to take a full chain
                            // (e.g. Arrays.copyOfRange(abody1, i1, c1))
                            map.put(id1, new PropertyInfo(body1, prop1.getRank()));
                            continue NextEntry;
                            }
                        }
                    }

                // there is no common identity; at least one must be an interfaces, the allow it
                // to be called as it was duck-typeable
                boolean f1 = info1.getFormat() == Component.Format.INTERFACE;
                boolean f2 = info2.getFormat() == Component.Format.INTERFACE;
                if (f1 || f2)
                    {
                    if (f1)
                        {
                        map.put(prop1.getIdentity(), prop1);
                        }
                    else
                        {
                        map.put(prop2.getIdentity(), prop2);
                        }
                    }
                }
            }
        return map;
        }

    @Override
    protected Map<MethodConstant, MethodInfo> mergeMethods(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
        if (info1 == null || info2 == null)
            {
            return Collections.EMPTY_MAP;
            }

        // calling info.getNarrowingMethod() can change the content of "MethodBySignature", causing
        // a CME, so we defer this call util later, collecting the source info
        Map<MethodInfo,     TypeInfo>   mapCapped = new HashMap<>();
        Map<MethodConstant, MethodInfo> mapMerged = new HashMap<>();

        NextEntry:
        for (Map.Entry<SignatureConstant, MethodInfo> entry : info1.ensureMethodsBySignature().entrySet())
            {
            SignatureConstant sig = entry.getKey();

            MethodInfo method2 = info2.getMethodBySignature(sig);
            if (method2 != null && !method2.isConstructor())
                {
                // the method exists in both maps;
                // check for a "common" structure and build a "subset" info

                MethodInfo method1 = entry.getValue();
                if (method1.equals(method2))
                    {
                    // trivial "equality" scenario
                    mapMerged.put(method1.getIdentity(), method1);

                    if (method1.isCapped())
                        {
                        mapCapped.put(method1, info1);
                        }
                    continue;
                    }

                MethodBody[] abody1 = method1.getChain();
                MethodBody[] abody2 = method2.getChain();

                for (int i1 = 0, c1 = abody1.length; i1 < c1; i1++)
                    {
                    MethodBody     body1 = abody1[i1];
                    MethodConstant id1   = body1.getIdentity();

                    for (MethodBody body2 : abody2)
                        {
                        if (body2.getIdentity().equals(id1))
                            {
                            MethodInfo methodBase = new MethodInfo(Arrays.copyOfRange(abody1, i1, c1));
                            mapMerged.put(id1, methodBase);
                            if (methodBase.isCapped())
                                {
                                mapCapped.put(methodBase, info1);
                                }

                            continue NextEntry;
                            }
                        }
                    }

                // there is no common identity; at least one must be an interface to allow it
                // to be called as it were duck-typeable
                boolean f1 = info1.getFormat() == Component.Format.INTERFACE;
                boolean f2 = info2.getFormat() == Component.Format.INTERFACE;
                if (f1 || f2)
                    {
                    if (f1)
                        {
                        mapMerged.put(method1.getIdentity(), method1);
                        if (method1.isCapped())
                            {
                            mapCapped.put(method1, info1);
                            }
                        }
                    else
                        {
                        mapMerged.put(method2.getIdentity(), method2);
                        if (method2.isCapped())
                            {
                            mapCapped.put(method2, info2);
                            }
                        }
                    }
                }
            }

        if (!mapCapped.isEmpty())
            {
            // make sure that for all capped method, the corresponding narrowing methods
            // made it; otherwise remove the capped one
            for (Map.Entry<MethodInfo, TypeInfo> entry : mapCapped.entrySet())
                {
                MethodInfo     methodCapped    = entry.getKey();
                TypeInfo       info            = entry.getValue();
                MethodConstant idCapped        = methodCapped.getIdentity();
                MethodInfo     methodNarrowing = info.getNarrowingMethod(methodCapped);
                MethodConstant idNarrowing     = methodNarrowing.getIdentity();

                if (!mapMerged.containsKey(idNarrowing))
                    {
                    mapMerged.remove(idCapped);
                    }
                }
            }
        return mapMerged;
        }

    @Override
    protected ListMap<String, ChildInfo> mergeChildren(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
        if (info1 == null || info2 == null)
            {
            return ListMap.EMPTY;
            }

        ListMap<String, ChildInfo> map1     = info1.getChildInfosByName();
        ListMap<String, ChildInfo> map2     = info2.getChildInfosByName();
        ListMap<String, ChildInfo> mapMerge = new ListMap<>();
        for (Map.Entry<String, ChildInfo> entry : map1.entrySet())
            {
            String sChild = entry.getKey();

            if (map2.containsKey(sChild))
                {
                ChildInfo child1 = map1.get(sChild);
                ChildInfo child2 = map2.get(sChild);

                ChildInfo childM = child1.layerOn(child2);
                if (childM != null)
                    {
                    mapMerge.put(sChild, entry.getValue());
                    }
                }
            }
        return mapMerge;
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        TypeConstant thisRight1 = getUnderlyingType();
        TypeConstant thisRight2 = getUnderlyingType2();

        Relation rel1 = thisRight1.calculateRelation(typeLeft);
        Relation rel2 = thisRight2.calculateRelation(typeLeft);
        return rel1.worseOf(rel2);
        }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight)
        {
        // A | B <= A' | B' must have been decomposed from the right
        assert !(typeRight instanceof IntersectionTypeConstant);

        TypeConstant thisLeft1 = getUnderlyingType();
        TypeConstant thisLeft2 = getUnderlyingType2();

        Relation rel1 = typeRight.calculateRelation(thisLeft1);
        Relation rel2 = typeRight.calculateRelation(thisLeft2);
        Relation rel  = rel1.bestOf(rel2);
        if (rel == Relation.INCOMPATIBLE)
            {
            // to deal with a scenario of A | B <= M [+ X], where M is a mixin into A' | B'
            // should be looked at holistically, without immediate decomposition;
            // since a mixin is the only (for purposes of "isA" decision making) terminal type
            // that allows an intersection as an "into" contribution
            return typeRight.findIntersectionContribution(this);
            }
        return rel;
        }

    @Override
    protected Relation findIntersectionContribution(IntersectionTypeConstant typeLeft)
        {
        TypeConstant thisRight1 = getUnderlyingType();
        TypeConstant thisRight2 = getUnderlyingType2();

        Relation rel1 = thisRight1.findIntersectionContribution(typeLeft);
        Relation rel2 = thisRight2.findIntersectionContribution(typeLeft);
        return rel1.worseOf(rel2);
        }

    @Override
    protected Set<SignatureConstant> isInterfaceAssignableFrom(
            TypeConstant typeRight, Access accessLeft, List<TypeConstant> listLeft)
        {
        assert isInterfaceType();

        TypeConstant thisLeft1 = getUnderlyingType();
        TypeConstant thisLeft2 = getUnderlyingType2();

        Set<SignatureConstant> setMiss1 = thisLeft1.isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);
        if (setMiss1.isEmpty())
            {
            return setMiss1; // type1 is assignable from that
            }

        Set<SignatureConstant> setMiss2 = thisLeft2.isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);
        if (setMiss2.isEmpty())
            {
            return setMiss2; // type2 is assignable from that
            }

        // neither is assignable; merge the misses
        if (setMiss2 != null)
            {
            setMiss1.addAll(setMiss2);
            }
        return setMiss1;
        }

    @Override
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access,
                                               boolean fFunction, List<TypeConstant> listParams)
        {
        return getUnderlyingType().containsSubstitutableMethod(signature, access, fFunction, listParams)
            && getUnderlyingType2().containsSubstitutableMethod(signature, access, fFunction, listParams);
        }

    /**
     * Find one side of this intersection type that is assignable to Tuple.
     *
     * Note: if both sides of the intersection are assignable to Tuple, null is returned to indicate
     *       an ambiguous request (theoretically, this could be improved).
     *
     * @return the part of this intersection type that is assignable to a Tuple or null
     */
    public TypeConstant extractTuple()
        {
        TypeConstant typeTuple1 = extractTuple(getUnderlyingType());
        TypeConstant typeTuple2 = extractTuple(getUnderlyingType2());

        return typeTuple1 == null ? typeTuple2 :
               typeTuple2 == null ? typeTuple1 :
                                    null;
        }

    private static TypeConstant extractTuple(TypeConstant type)
        {
        return type.isTuple()
                ? type
                : type instanceof IntersectionTypeConstant
                    ? ((IntersectionTypeConstant) type).extractTuple()
                    : null;
        }


    // ----- run-time support ----------------------------------------------------------------------

    @Override
    public int callEquals(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        TypeConstant typeV1 = hValue1.getType();
        TypeConstant typeV2 = hValue2.getType();

        TypeConstant type = m_constType1;
        if (typeV1.isA(type) && typeV2.isA(type))
            {
            return type.callEquals(frame, hValue1, hValue2, iReturn);
            }

        type = m_constType2;
        if (typeV1.isA(type) && typeV2.isA(type))
            {
            return type.callEquals(frame, hValue1, hValue2, iReturn);
            }

        assert !typeV1.equals(typeV2);
        return frame.assignValue(iReturn, xBoolean.FALSE);
        }

    @Override
    public int callCompare(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        TypeConstant typeV1 = hValue1.getType();
        TypeConstant typeV2 = hValue2.getType();

        TypeConstant type = m_constType1;
        if (typeV1.isA(type) && typeV2.isA(type))
            {
            return type.callCompare(frame, hValue1, hValue2, iReturn);
            }

        type = m_constType2;
        if (typeV1.isA(type) && typeV2.isA(type))
            {
            return type.callCompare(frame, hValue1, hValue2, iReturn);
            }

        assert !typeV1.equals(typeV2);
        return frame.assignValue(iReturn, xOrdered.makeHandle(typeV1.compareTo(typeV2)));
        }

    @Override
    public MethodInfo findFunctionInfo(SignatureConstant sig)
        {
        MethodInfo info1 = m_constType1.findFunctionInfo(sig);
        MethodInfo info2 = m_constType2.findFunctionInfo(sig);

        return info1 == null || info2 == null ||
                    !info1.getIdentity().equals(info2.getIdentity()) // ambiguous?
                ? null
                : info1;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.IntersectionType;
        }

    @Override
    public String getValueString()
        {
        return m_constType1.getValueString() + " | " + m_constType2.getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        // '|' == 124
        return 124 ^ m_constType1.hashCode() ^ m_constType2.hashCode();
        }
    }
