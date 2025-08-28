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
import org.xvm.asm.Component.SimpleCollector;
import org.xvm.asm.ComponentResolver.ResolutionResult;
import org.xvm.asm.ComponentResolver.ResolutionCollector;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.util.ListMap;


/**
 * Represent a constant that specifies the difference (relative complement) ("-") of two types.
 */
public class DifferenceTypeConstant
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
    public DifferenceTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }

    /**
     * Construct a constant whose value is the difference of two types.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param constType1  the first TypeConstant (the minuend)
     * @param constType2  the second TypeConstant (the subtrahend)
     */
    public DifferenceTypeConstant(ConstantPool pool, TypeConstant constType1, TypeConstant constType2)
        {
        super(pool, constType1, constType2);
        }

    @Override
    protected TypeConstant cloneRelational(ConstantPool pool, TypeConstant type1, TypeConstant type2)
        {
        return pool.ensureDifferenceTypeConstant(type1, type2);
        }

    @Override
    protected TypeConstant simplifyInternal(TypeConstant type1, TypeConstant type2)
        {
        if (type1 instanceof DifferenceTypeConstant typeDiff)
            {
            return typeDiff.andNotInternal(type2);
            }

        if (type1.isImmutabilitySpecified() && type2.isOnlyImmutable())
            {
            // ((immutable X) - immutable) -> X
            return type1.removeImmutable();
            }

        if (type1.isRelationalType() || type1.isAnnotated())
            {
            TypeConstant typeResult = type1.andNot(getConstantPool(), type2);

            // andNot algorithm defaults to the "minuend" type
            if (typeResult != type1)
                {
                return typeResult;
                }
            }

        return null;
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isImmutabilitySpecified()
        {
        return m_constType1.isImmutabilitySpecified();
        }

    @Override
    public boolean isImmutable()
        {
        return m_constType1.isImmutable();
        }

    @Override
    public TypeConstant removeImmutable()
        {
        return cloneRelational(getConstantPool(), m_constType1.removeImmutable(), m_constType2);
        }

    @Override
    public TypeConstant freeze()
        {
        // the immutability of the second type is irrelevant
        TypeConstant typeOriginal1  = m_constType1;
        TypeConstant typeOriginal2  = m_constType2;
        TypeConstant typeImmutable1 = typeOriginal1.freeze();

        return typeOriginal1 == typeImmutable1
                ? this
                : cloneRelational(getConstantPool(), typeImmutable1, typeOriginal2);
        }

    @Override
    public boolean isIncompatibleCombo(TypeConstant that)
        {
        return false;
        }

    @Override
    public boolean extendsClass(IdentityConstant constClass)
        {
        // a difference type is NEVER a class type
        return false;
        }

    @Override
    public Category getCategory()
        {
        // a difference type for classes or interfaces is an interface

        Category cat1 = m_constType1.getCategory();
        Category cat2 = m_constType2.getCategory();

        switch (cat1)
            {
            case CLASS:
            case IFACE:
                switch (cat2)
                    {
                    case CLASS:
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
        // a difference type is NEVER a class type; it always resolves to an interface type
        return false;
        }

    @Override
    public IdentityConstant getSingleUnderlyingClass(boolean fAllowInterface)
        {
        // a difference type is NEVER a class type; it always resolves to an interface type
        throw new IllegalStateException();
        }

    @Override
    public boolean isConst()
        {
        return m_constType1.isConst();
        }

    @Override
    public boolean isOnlyNullable()
        {
        // difference types are never nullable
        return false;
        }

    @Override
    public TypeConstant andNot(ConstantPool pool, TypeConstant that)
        {
        TypeConstant typeR = andNotInternal(that);
        return typeR == null
                ? super.andNot(pool, that)
                : typeR;
        }

    /**
     * @return a simplified type or null if it cannot be simplified
     */
    private TypeConstant andNotInternal(TypeConstant that)
        {
        TypeConstant type1 = m_constType1.resolveTypedefs();
        TypeConstant type2 = m_constType2.resolveTypedefs();

        if (that.isA(type1))
            {
            // (A - B) - Sub(A) => Object
            return getConstantPool().typeObject();
            }

        if (type2.isA(that))
            {
            // (A - B) - Super(B) => (A - B)
            return this;
            }

        return null;
        }

    @Override
    public boolean containsGenericParam(String sName)
        {
        // only the left side needs to have it, and it doesn't matter what the right side has
        return m_constType1.containsGenericParam(sName);
        }

    @Override
    protected TypeConstant getGenericParamType(String sName, List<TypeConstant> listParams)
        {
        // only the left side needs to have it, and it doesn't matter what the right side has
        return m_constType1.getGenericParamType(sName, listParams);
        }

    @Override
    public ResolutionResult resolveContributedName(
            String sName, Access access, MethodConstant idMethod, ResolutionCollector collector)
        {
        // for the DifferenceType to contribute a name, the first side needs to find it,
        // but the second should not
        ErrorListener    errs       = collector.getErrorListener();
        SimpleCollector  collector1 = new SimpleCollector(errs);
        ResolutionResult result1    = m_constType1.resolveContributedName(sName, access, idMethod, collector1);

        if (result1 == ResolutionResult.RESOLVED)
            {
            ResolutionResult result2 = m_constType2.resolveContributedName(
                    sName, access, idMethod, new SimpleCollector(errs));
            if (result2 == ResolutionResult.RESOLVED)
                {
                // TODO: if the results are identical we can allow that
                return ResolutionResult.UNKNOWN;
                }
            collector.resolvedConstant(collector1.getResolvedConstant());
            }

        return result1;
        }

    @Override
    public TypeConstant resolveTypeParameter(TypeConstant typeActual, String sFormalName)
        {
        typeActual = typeActual.resolveTypedefs();

        if (getFormat() != typeActual.getFormat())
            {
            return null;
            }

        DifferenceTypeConstant that = (DifferenceTypeConstant) typeActual;

        // only use the first branch
        return this.m_constType1.resolveTypeParameter(that.m_constType1, sFormalName);
        }

    @Override
    public boolean isNestMateOf(IdentityConstant idClass)
        {
        TypeConstant type1 = m_constType1;
        if (type1.isFormalType())
            {
            type1 = ((FormalConstant) type1.getDefiningConstant()).getConstraintType();
            }
        return type1.isNestMateOf(idClass);
        }

    @Override
    public boolean containsFunctionType()
        {
        return false;
        }


    // ----- TypeInfo support ----------------------------------------------------------------------

    @Override
    public TypeInfo ensureTypeInfo(IdentityConstant idClass, ErrorListener errs)
        {
        TypeConstant type1 = m_constType1;
        if (type1.isFormalType())
            {
            TypeConstant typeC = ((FormalConstant) type1.getDefiningConstant()).getConstraintType();
            TypeConstant typeN = typeC.andNot(getConstantPool(), m_constType2);
            return typeN.ensureTypeInfo(idClass, errs);
            }

        return super.ensureTypeInfo(idClass, errs);
        }

    @Override
    protected Map<Object, ParamInfo> mergeTypeParams(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
        if (info1 == null || info2 == null)
            {
            return Collections.emptyMap();
            }

        Map<Object, ParamInfo> map1 = info1.getTypeParams();
        Map<Object, ParamInfo> map2 = info2.getTypeParams();
        Map<Object, ParamInfo> map  = new HashMap<>(map1);

        // keep all the type params from the first type unless they are not compatible with the second
        for (Iterator<Map.Entry<Object, ParamInfo>> iter = map.entrySet().iterator(); iter.hasNext();)
            {
            Map.Entry<Object, ParamInfo> entry = iter.next();

            Object nid = entry.getKey();

            ParamInfo param2 = map2.get(nid);
            if (param2 != null)
                {
                // the type param exists in both maps; check if the types are compatible
                ParamInfo    param1 = entry.getValue();
                TypeConstant type1  = param1.getActualType();
                TypeConstant type2  = param2.getActualType();

                if (!type2.isAssignableTo(type1))
                    {
                    // the type param is incompatible; remove it
                    iter.remove();
                    }
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
            return Collections.emptyMap();
            }

        if (info2 == null)
            {
            return info1.getProperties();
            }

        Map<PropertyConstant, PropertyInfo> map = new HashMap<>();

        for (Map.Entry<String, PropertyInfo> entry : info1.ensurePropertiesByName().entrySet())
            {
            String sName = entry.getKey();

            PropertyInfo prop2 = info2.findProperty(sName);
            if (prop2 == null)
                {
                // the property only exists in the first map; take it
                PropertyInfo prop1 = entry.getValue();
                map.put(prop1.getIdentity(), prop1);
                }
            }
        return map;
        }

    @Override
    protected Map<MethodConstant, MethodInfo> mergeMethods(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
        if (info1 == null)
            {
            return Collections.emptyMap();
            }

        if (info2 == null)
            {
            return info1.getMethods();
            }

        Map<MethodConstant, MethodInfo> map = new HashMap<>();

        for (Map.Entry<SignatureConstant, MethodInfo> entry : info1.ensureMethodsBySignature().entrySet())
            {
            SignatureConstant sig = entry.getKey();

            MethodInfo method1 = entry.getValue();
            MethodInfo method2 = info2.getMethodBySignature(sig);

            if (method2 == null && !method1.isConstructor())
                {
                // the method only exists in the first map; take it
                map.put(method1.getIdentity(), method1);
                }
            }
        return map;
        }

    @Override
    protected ListMap<String, ChildInfo> mergeChildren(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
        if (info1 == null)
            {
            return ListMap.EMPTY;
            }

        if (info2 == null)
            {
            return info1.getChildInfosByName();
            }

        ListMap<String, ChildInfo> map1     = info1.getChildInfosByName();
        ListMap<String, ChildInfo> map2     = info2.getChildInfosByName();
        ListMap<String, ChildInfo> mapMerge = new ListMap<>(map1);

        mapMerge.keySet().removeAll(map2.keySet());
        return mapMerge;
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        if (m_constType1.isFormalType())
            {
            // this (right) type is DR = (FR - X), where FR is a formal type with a constraint of CR
            // Note: we are treating this DifferenceType as a *formal type that is not constType2*
            //       (see the comment at TerminalTypeConstant.removeNullable)
            TypeConstant typeCR   = ((FormalConstant) m_constType1.getDefiningConstant()).getConstraintType();
            TypeConstant typeSubR = typeCR.andNot(getConstantPool(), m_constType2);

            if (typeSubR != null)
                {
                if (typeLeft.isFormalType())
                    {
                    // typeLeft is a formal type FL with a constraint of CL;
                    // the logic below implies that if (CR - X) is assignable to CL then (FR - X) is
                    // assignable to FL
                    TypeConstant typeCL = ((FormalConstant) typeLeft.getDefiningConstant()).getConstraintType();
                    return typeSubR.calculateRelation(typeCL);
                    }
                else
                    {
                    // if (CR - X) is assignable to L then (FR - X) is assignable to L
                    return typeSubR.calculateRelation(typeLeft);
                    }
                }
            }

        if (typeLeft instanceof DifferenceTypeConstant typeDiff)
            {
            // if A <= A' and B' <= B, then A - B <= A' - B'
            TypeConstant typeR1 = m_constType1;
            TypeConstant typeR2 = m_constType2;
            TypeConstant typeL1 = typeDiff.m_constType1;
            TypeConstant typeL2 = typeDiff.m_constType2;

            Relation rel = typeR1.calculateRelation(typeL1);
            if (rel != Relation.INCOMPATIBLE)
                {
                rel = rel.worseOf(typeL2.calculateRelation(typeR2));
                }
            return rel;
            }

        // defer the answer to the duck-type check
        return Relation.INCOMPATIBLE;
        }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight)
        {
        // if [typeRight] A is assignable to B, then A is assignable to [this] (B - C) unless B is
        // a union of (X | C), in which case it should have been simplified by andNot();
        // otherwise defer the answer to the duck-type check
        TypeConstant type1 = m_constType1;
        if (type1 instanceof UnionTypeConstant)
            {
            TypeConstant typeR = simplifyOrClone(getConstantPool(), type1, m_constType2);
            if (typeR != this)
                {
                return typeR.calculateRelationToRight(typeRight);
                }
            }

        return typeRight.calculateRelation(type1);
        }

    @Override
    protected Relation findUnionContribution(UnionTypeConstant typeLeft)
        {
        // defer the answer to the duck-type check
        return Relation.INCOMPATIBLE;
        }

    @Override
    protected boolean isDuckTypeAbleFrom(TypeConstant typeRight)
        {
        // HACK HACK: for now we only allow a diff to the Object (e.g. Stringable - Object)
        // TODO GG: implement isInterfaceAssignableFrom()
        return m_constType1.isInterfaceType() && m_constType2.equals(getConstantPool().typeObject());
        }

    @Override
    protected Set<SignatureConstant> isInterfaceAssignableFrom(TypeConstant typeRight,
                                                               Access accessLeft, List<TypeConstant> listLeft)
        {
        return m_constType1.isInterfaceAssignableFrom(typeRight, accessLeft, listLeft); // TODO GG
        }

    @Override
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access,
                                               boolean fFunction, List<TypeConstant> listParams)
        {
        return m_constType1.containsSubstitutableMethod(signature, access, fFunction, listParams)
            && !m_constType2.containsSubstitutableMethod(signature, access, fFunction, listParams);
        }


    // ----- run-time support ----------------------------------------------------------------------

    @Override
    public int callEquals(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        // disregard what the second type thinks
        return m_constType1.callEquals(frame, hValue1, hValue2, iReturn);
        }

    @Override
    public int callCompare(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        // disregard what the second type thinks
        return m_constType1.callCompare(frame, hValue1, hValue2, iReturn);
        }

    @Override
    public int callHashCode(Frame frame, ObjectHandle hValue, int iReturn)
        {
        // disregard what the second type thinks
        return m_constType1.callHashCode(frame, hValue, iReturn);
        }

    @Override
    public MethodInfo findFunctionInfo(SignatureConstant sig)
        {
        return m_constType1.findFunctionInfo(sig);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.DifferenceType;
        }

    @Override
    public String getValueString()
        {
        return m_constType1.getValueString() + " - " + m_constType2.getValueString();
        }
    }