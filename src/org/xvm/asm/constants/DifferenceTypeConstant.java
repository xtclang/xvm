package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Annotation;
import org.xvm.asm.Component.ResolutionCollector;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.Component.SimpleCollector;
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
     * @param constType1  the first TypeConstant to union
     * @param constType2  the second TypeConstant to union
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
    public boolean isOnlyNullable()
        {
        // difference types are never nullable
        return false;
        }

    @Override
    public boolean containsGenericParam(String sName)
        {
        // only the left side needs to have it and it doesn't matter what the right side has
        return m_constType1.containsGenericParam(sName);
        }

    @Override
    protected TypeConstant getGenericParamType(String sName, List<TypeConstant> listParams)
        {
        // only the left side needs to have it and it doesn't matter what the right side has
        return m_constType1.getGenericParamType(sName, listParams);
        }

    @Override
    public ResolutionResult resolveContributedName(String sName, ResolutionCollector collector)
        {
        // for the DifferenceType to contribute a name, the first side needs to find it,
        // but the second should not
        SimpleCollector  collector1 = new SimpleCollector();
        ResolutionResult result1    = m_constType1.resolveContributedName(sName, collector1);

        if (result1 == ResolutionResult.RESOLVED)
            {
            ResolutionResult result2 = m_constType2.resolveContributedName(sName, new SimpleCollector());
            if (result2 == ResolutionResult.RESOLVED)
                {
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
    protected TypeInfo buildTypeInfo(ErrorListener errs)
        {
        // we've been asked to resolve some type defined as "T1 - T2", which means that we need to
        // first resolve T1 and T2, and then collect all the information from T1 that is not in T2
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

    protected Annotation[] mergeAnnotations(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
        // TODO
        return null;
        }

    protected Map<PropertyConstant, PropertyInfo> mergeProperties(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
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

    protected Map<MethodConstant, MethodInfo> mergeMethods(TypeInfo info1, TypeInfo info2, ErrorListener errs)
        {
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

    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        // this will be answered via duck-type check
        return Relation.INCOMPATIBLE;
        }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight)
        {
        // this will be answered via duck-type check
        return Relation.INCOMPATIBLE;
        }

    @Override
    protected Relation findIntersectionContribution(IntersectionTypeConstant typeLeft)
        {
        // this will be answered via duck-type check
        return Relation.INCOMPATIBLE;
        }

    @Override
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access,
                                               boolean fFunction, List<TypeConstant> listParams)
        {
        return m_constType1.containsSubstitutableMethod(signature, access, fFunction, listParams)
            && m_constType2.containsSubstitutableMethod(signature, access, fFunction, listParams);
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


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return "-".hashCode() ^ m_constType1.hashCode() ^ m_constType2.hashCode();
        }
    }
