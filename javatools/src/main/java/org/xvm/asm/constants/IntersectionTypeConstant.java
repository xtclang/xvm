package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.ComponentResolver.ResolutionCollector;
import org.xvm.asm.ComponentResolver.ResolutionResult;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;

import org.xvm.util.ListMap;
import org.xvm.util.Severity;


/**
 * Represent a constant that specifies the intersection ("+") of two types.
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
            return type1;
            }
        if (type2.isA(type1))
            {
            return type2;
            }
        return null;
        }

    /**
     * Find a contributing type that is a parent for the specified virtual child name.
     *
     * @param sChild  the child name
     *
     * @return the parent type or null if none found
     */
    public TypeConstant extractParent(String sChild)
        {
        TypeConstant typeParent = extractParentImpl(m_constType1, sChild);

        return typeParent == null
                ? extractParentImpl(m_constType2, sChild)
                : typeParent;
        }

    private TypeConstant extractParentImpl(TypeConstant type1, String sChild)
        {
        if (type1.isSingleUnderlyingClass(true))
            {
            ClassStructure clz = (ClassStructure) type1.getSingleUnderlyingClass(true).getComponent();
            if (clz.findChildDeep(sChild) != null)
                {
                return type1;
                }
            }
        else if (type1 instanceof IntersectionTypeConstant typeInter)
            {
            return typeInter.extractParent(sChild);
            }
        return null;
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
    public boolean isService()
        {
        return m_constType1.isService() || m_constType2.isService();
        }

    @Override
    public TypeConstant ensureService()
        {
        TypeConstant type1 = m_constType1;
        TypeConstant type2 = m_constType2;

        // this could be called by the run-time for injected intersection types, for example:
        //     oodb:Connection<Bank:BankSchema> + Bank:BankSchema

        return type1.isService() && type2.isService()
                ? this
                : cloneRelational(getConstantPool(), type1.ensureService(), type2.ensureService());
        }

    @Override
    public boolean isNullable()
        {
        TypeConstant type1 = m_constType1;
        TypeConstant type2 = m_constType2;

        // (Element + Stringable?) is Nullable (assuming trivial Element's constraint)
        return type1.isNullable()   && type2.isNullable() ||
               type1.isFormalType() && type2.isNullable() ||
               type1.isNullable()   && type2.isFormalType();
        }

    @Override
    public TypeConstant removeNullable()
        {
        return isNullable()
                ? m_constType1.removeNullable().combine(getConstantPool(),
                  m_constType2.removeNullable())
                : this;
        }

    @Override
    public boolean isIncompatibleCombo(TypeConstant that)
        {
        TypeConstant type1 = m_constType1.resolveTypedefs();
        TypeConstant type2 = m_constType2.resolveTypedefs();
        return type1.isIncompatibleCombo(that) || type2.isIncompatibleCombo(that);
        }

    @Override
    public TypeConstant andNot(ConstantPool pool, TypeConstant that)
        {
        TypeConstant type1 = m_constType1.resolveTypedefs();
        TypeConstant type2 = m_constType2.resolveTypedefs();

        if (type1.equals(that))
            {
            // (A + B) - A => B
            return type2;
            }

        if (type2.equals(that))
            {
            // (A + B) - B => B
            return type1;
            }

        // recurse to cover cases like this:
        // ((A + B) + C) - B => A + C
        if (type1.isRelationalType() || type2.isRelationalType())
            {
            TypeConstant type1R = type1.andNot(pool, that);
            TypeConstant type2R = type2.andNot(pool, that);
            if (type1R != type1 || type2R != type2)
                {
                return type1R.combine(pool, type2R);
                }
            }

        return super.andNot(pool, that);
        }

    @Override
    public Category getCategory()
        {
        // an intersection of classes is a class;
        // an intersection of a class and an interface is a class
        // an intersection of interfaces is an interface

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
        // for Intersection types, either side needs to find it, but if both do, take the narrower one
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

        return typeActual1.combine(getConstantPool(), typeActual2);
        }

    @Override
    public ResolutionResult resolveContributedName(
            String sName, Access access, MethodConstant idMethod, ResolutionCollector collector)
        {
        // for the IntersectionType to contribute a name, either side needs to find it
        ResolutionResult result1 = m_constType1.resolveContributedName(sName, access, idMethod, collector);
        if (result1 == ResolutionResult.RESOLVED)
            {
            return result1;
            }

        ResolutionResult result2 = m_constType2.resolveContributedName(sName, access, idMethod, collector);
        if (result2 == ResolutionResult.RESOLVED)
            {
            return result2;
            }

        return result1.combine(result2);
        }

    @Override
    public TypeConstant resolveTypeParameter(TypeConstant typeActual, String sFormalName)
        {
        typeActual = typeActual.resolveTypedefs();

        if (getFormat() == typeActual.getFormat())
            {
            return super.resolveTypeParameter(typeActual, sFormalName);
            }

        // check if any of our legs can unambiguously resolve the formal type and take the narrowest
        TypeConstant typeResult1 = getUnderlyingType().resolveTypeParameter(typeActual, sFormalName);
        TypeConstant typeResult2 = getUnderlyingType2().resolveTypeParameter(typeActual, sFormalName);
        return typeResult1 == null
                ? typeResult2
                : typeResult2 == null
                        ? typeResult1
                        : typeResult1.isA(typeResult2) ? typeResult1
                        : typeResult2.isA(typeResult1) ? typeResult2
                                                       : null;
        }

    @Override
    public boolean isNestMateOf(IdentityConstant idClass)
        {
        TypeConstant type1 = m_constType1;
        TypeConstant type2 = m_constType2;

        // if a formal type's constraint is fully covered by another type, combining the formal
        // type doesn't change the "MateOf" relationship
        if (type1.isFormalType())
            {
            TypeConstant type1C = ((FormalConstant) type1.getDefiningConstant()).getConstraintType();
            if (type2.isA(type1C))
                {
                return type2.isNestMateOf(idClass);
                }
            }
        else if (type2.isFormalType())
            {
            TypeConstant type2C = ((FormalConstant) type2.getDefiningConstant()).getConstraintType();
            if (type1.isA(type2C))
                {
                return type1.isNestMateOf(idClass);
                }
            }

        return type1.isNestMateOf(idClass) && type2.isNestMateOf(idClass);
        }


    // ----- TypeInfo support ----------------------------------------------------------------------

    @Override
    protected Map<Object, ParamInfo> mergeTypeParams(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
        if (info1 == null)
            {
            return info2.getTypeParams();
            }

        if (info2 == null)
            {
            return info1.getTypeParams();
            }

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

    @Override
    protected Annotation[] mergeAnnotations(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
        // TODO
        return null;
        }

    @Override
    protected Map<PropertyConstant, PropertyInfo> mergeProperties(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
        if (info1 == null)
            {
            return info2.getProperties();
            }

        if (info2 == null)
            {
            return info1.getProperties();
            }

        Map<PropertyConstant, PropertyInfo> map = new HashMap<>();

        // take only non-nested properties
        for (Map.Entry<String, PropertyInfo> entry : info1.ensurePropertiesByName().entrySet())
            {
            String sName = entry.getKey();

            PropertyInfo prop1 = entry.getValue();
            assert prop1 != null;

            PropertyInfo prop2 = info2.findProperty(sName);
            if (prop2 == null)
                {
                // the property is only in the first map
                map.put(prop1.getIdentity(), prop1);
                }
            else
                {
                // the property exists in both maps
                if (prop2.containsBody(prop1.getIdentity()))
                    {
                    map.put(prop2.getIdentity(), prop2);
                    }
                else
                    {
                    // TODO: if neither "contains" the other, choose the best PropertyInfo
                    map.put(prop1.getIdentity(), prop1);
                    }
                }
            }

        for (Map.Entry<String, PropertyInfo> entry : info2.ensurePropertiesByName().entrySet())
            {
            String sName = entry.getKey();

            PropertyInfo prop2 = entry.getValue();
            assert prop2 != null;

            PropertyInfo prop1 = info1.findProperty(sName);
            if (prop1 == null)
                {
                // the property is only in the second map
                map.put(prop2.getIdentity(), prop2);
                }
            }
        return map;
        }

    @Override
    protected Map<MethodConstant, MethodInfo> mergeMethods(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
        if (info1 == null)
            {
            return info2.getMethods();
            }

        if (info2 == null)
            {
            return info1.getMethods();
            }

        Map<MethodConstant, MethodInfo> map = new HashMap<>();

        // take only non-nested methods
        for (Map.Entry<SignatureConstant, MethodInfo> entry : info1.ensureMethodsBySignature().entrySet())
            {
            SignatureConstant sig     = entry.getKey();
            MethodInfo        method1 = entry.getValue();

            if (method1.isConstructor() && !method1.containsVirtualConstructor())
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
                // the method exists in both maps
                if (method2.containsBody(method1.getIdentity()))
                    {
                    map.put(method2.getIdentity(), method2);
                    }
                else
                    {
                    // TODO: if neither contains the other, choose the best MethodInfo
                    map.put(method1.getIdentity(), method1);
                    }
                }
            }

        for (Map.Entry<SignatureConstant, MethodInfo> entry : info2.ensureMethodsBySignature().entrySet())
            {
            SignatureConstant sig     = entry.getKey();
            MethodInfo        method2 = entry.getValue();

            if (method2.isConstructor() && !method2.containsVirtualConstructor())
                {
                continue;
                }

            MethodInfo method1 = info1.getMethodBySignature(sig);
            if (method1 == null ||
                    method1.isConstructor() && !method1.containsVirtualConstructor())
                {
                // the method is only in the second map or a non-virtual constructor that has been
                // explicitly excluded by the check in the first loop
                map.put(method2.getIdentity(), method2);
                }
            }
        return map;
        }

    @Override
    protected ListMap<String, ChildInfo> mergeChildren(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
        ListMap<String, ChildInfo> map1 = info1 == null ? ListMap.EMPTY : info1.getChildInfosByName();
        ListMap<String, ChildInfo> map2 = info1 == null ? ListMap.EMPTY : info2.getChildInfosByName();

        if (map1.isEmpty())
            {
            return map2;
            }

        if (map2.isEmpty())
            {
            return map1;
            }

        ListMap<String, ChildInfo> mapMerge = new ListMap<>();
        for (Map.Entry<String, ChildInfo> entry : map1.entrySet())
            {
            String    sChild = entry.getKey();
            ChildInfo child1 = entry.getValue();

            if (map2.containsKey(sChild))
                {
                // the child exists in both maps
                ChildInfo child2 = map2.get(sChild);
                ChildInfo childM = child1.layerOn(child2);
                if (childM == null)
                    {
                    log(errs, Severity.ERROR, VE_CHILD_COLLISION,
                        getValueString(), sChild,
                        child2.getIdentity().getValueString(),
                        child1.getIdentity().getValueString());
                    }
                else
                    {
                    // the child is only in the first map
                    mapMerge.put(sChild, childM);
                    }
                }
            else
                {
                mapMerge.put(sChild, child1);
                }
            }

        for (Map.Entry<String, ChildInfo> entry : map2.entrySet())
            {
            String sChild = entry.getKey();

            if (!map1.containsKey(sChild))
                {
                // the child is only in the second map
                mapMerge.put(sChild, entry.getValue());
                }
            }
        return mapMerge;
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        // A relationship to an intersection (this type is an intersection on the right)
        //      A + B <= A' + B'
        // must be decomposed from the left, as well a relation to a complex union
        //      (A + B) | C <= (A' + B')
        // however, for a complex intersection relation to a union, such as:
        //      (A | B) <= (A' | B') + A"
        // it needs to start from decomposing the right.
        // As a result, for all relational types we'll start with decomposing the left,
        // and if that doesn't work, decompose the right at last
        if (typeLeft.isRelationalType() || typeLeft.isAnnotated())
            {
            Relation rel = super.calculateRelationToLeft(typeLeft);
            if (rel != Relation.INCOMPATIBLE)
                {
                return rel;
                }
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

        // since Enum values cannot be extended, an Enum value type cannot be combined with any
        // other type and an intersection with a formal type doesn't actually narrow that Enum value
        // type; this obviously applies to Nullable
        if (isEnumOrNullable(typeRight))
            {
            if (isEnumOrNullable(thisLeft1) && thisLeft2.isFormalType())
                {
                // Nullable + Element <= Nullable (if Element's constraint is trivial)
                return rel1.worseOf(typeRight.calculateRelation(thisLeft2.resolveConstraints()));
                }
            if (thisLeft1.isFormalType() && isEnumOrNullable(thisLeft2))
                {
                // Element + Lesser <= Lesser (if Element's constraint is trivial)
                return rel2.worseOf(typeRight.calculateRelation(thisLeft1.resolveConstraints()));
                }
            }

        return rel1.worseOf(rel2);
        }

    private boolean isEnumOrNullable(TypeConstant type)
        {
        return type.isEnumValue() || type.isOnlyNullable();
        }

    @Override
    protected Relation findUnionContribution(UnionTypeConstant typeLeft)
        {
        TypeConstant thisRight1 = getUnderlyingType();
        TypeConstant thisRight2 = getUnderlyingType2();

        Relation rel1 = thisRight1.findUnionContribution(typeLeft);
        Relation rel2 = thisRight2.findUnionContribution(typeLeft);
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

        setMiss1.addAll(setMiss2); // signatures in both (union) are still missing
        return setMiss1;
        }

    @Override
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access, boolean fFunction, List<TypeConstant> listParams)
        {
        return getUnderlyingType().containsSubstitutableMethod(signature, access, fFunction, listParams)
            || getUnderlyingType2().containsSubstitutableMethod(signature, access, fFunction, listParams);
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

    @Override
    public int callHashCode(Frame frame, ObjectHandle hValue, int iReturn)
        {
        // use just the first type (TODO: that needs to be improved to guarantee symmetric quality)
        return m_constType1.callHashCode(frame, hValue, iReturn);
        }

    @Override
    public MethodInfo findFunctionInfo(SignatureConstant sig)
        {
        MethodInfo info1 = m_constType1.findFunctionInfo(sig);
        MethodInfo info2 = m_constType2.findFunctionInfo(sig);

        return info1 == null                           ? info2 :
               info2 == null                           ? info1 :
               info1.containsBody(info2.getIdentity()) ? info1 :
               info2.containsBody(info1.getIdentity()) ? info2 :
                                                         null; // ambiguous
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
        return m_constType1.getValueString() + " + " + m_constType2.getValueString();
        }
    }