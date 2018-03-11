package org.xvm.asm.constants;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;

import org.xvm.asm.constants.ParamInfo.TypeResolver;
import org.xvm.asm.constants.TypeConstant.Origin;

import org.xvm.util.ListMap;


/**
 * Represents the "flattened" information about the type.
 */
public class TypeInfo
    {
    /**
     * Construct a TypeInfo.
     *
     * @param type                 the type that the TypeInfo represents
     * @param struct               the structure that underlies the type, or null if there is none
     * @param cDepth               TODO
     * @param fAbstract            true if the type is abstract
     * @param mapTypeParams        the collected type parameters for the type
     * @param aannoClass           the annotations for the type that mix into "Class"
     * @param typeExtends          the type that is extended
     * @param typeRebases          the type that is rebased onto
     * @param typeInto             for mixins, the type that is mixed into; for interfaces, Object
     * @param listProcess
     * @param listmapClassChain    the potential call chain of classes
     * @param listmapDefaultChain  the potential call chain of default implementations
     * @param mapProperties        the properties of the type
     * @param mapMethods           the methods of the type
     * @param mapVirtProperties    the virtual properties of the type, keyed by nested id
     * @param mapVirtMethods       the virtual methods of the type, keyed by nested id
     * @param progress             the Progress for this TypeInfo
     */
    public TypeInfo(
            TypeConstant                        type,
            ClassStructure                      struct,
            int                                 cDepth,
            boolean                             fAbstract,
            Map<String, ParamInfo>              mapTypeParams,
            Annotation[]                        aannoClass,
            TypeConstant                        typeExtends,
            TypeConstant                        typeRebases,
            TypeConstant                        typeInto,
            List<Contribution>                  listProcess,
            ListMap<IdentityConstant, Origin>   listmapClassChain,
            ListMap<IdentityConstant, Origin>   listmapDefaultChain,
            Map<PropertyConstant, PropertyInfo> mapProperties,
            Map<MethodConstant, MethodInfo>     mapMethods,
            Map<Object, PropertyInfo>           mapVirtProperties,
            Map<Object, MethodInfo>             mapVirtMethods,
            Progress                            progress)
        {
        assert type                 != null;
        assert mapTypeParams        != null;
        assert listmapClassChain    != null;
        assert listmapDefaultChain  != null;
        assert mapProperties        != null;
        assert mapMethods           != null;
        assert progress             != null && progress != Progress.Absent;

        m_type                  = type;
        m_struct                = struct;
        m_cDepth                = cDepth;
        m_fAbstract             = fAbstract;
        m_mapTypeParams         = mapTypeParams;
        m_aannoClass            = validateAnnotations(aannoClass);
        m_typeExtends           = typeExtends;
        m_typeRebases           = typeRebases;
        m_typeInto              = typeInto;
        m_listProcess           = listProcess;
        m_listmapClassChain     = listmapClassChain;
        m_listmapDefaultChain   = listmapDefaultChain;
        m_mapProperties         = mapProperties;
        m_mapVirtProperties     = mapVirtProperties;
        m_mapMethods            = mapMethods;
        m_mapVirtMethods        = mapVirtMethods;
        m_progress              = progress;
        }

    /**
     * Create a new TypeInfo that represents a more limited (public or protected) access to the
     * members of this private type.
     *
     * @param access  the desired access, either PUBLIC or PROTECTED
     *
     * @return a new TypeInfo
     */
    public TypeInfo limitAccess(Access access)
        {
        assert m_type.getAccess() == Access.PRIVATE;
        assert access == Access.PROTECTED ||  access == Access.PUBLIC;

        TypeConstant typeNew = m_type.getUnderlyingType();
        if (access == Access.PROTECTED)
            {
            typeNew = m_type.getConstantPool().ensureAccessTypeConstant(typeNew, Access.PROTECTED);
            }

        Map<PropertyConstant, PropertyInfo> mapProps     = new HashMap<>();
        Map<Object          , PropertyInfo> mapVirtProps = new HashMap<>();
        for (Entry<PropertyConstant, PropertyInfo> entry : m_mapProperties.entrySet())
            {
            PropertyInfo prop = entry.getValue().limitAccess(access);
            if (prop != null)
                {
                PropertyConstant id = entry.getKey();
                mapProps.put(id, prop);

                if (prop.isVirtual())
                    {
                    mapVirtProps.put(id.getNestedIdentity(), prop);
                    }
                }
            }

        Map<MethodConstant, MethodInfo> mapMethods     = new HashMap<>();
        Map<Object        , MethodInfo> mapVirtMethods = new HashMap<>();
        for (Entry<MethodConstant, MethodInfo> entry : m_mapMethods.entrySet())
            {
            MethodInfo method = entry.getValue();
            if (method.getAccess().compareTo(access) <= 0)
                {
                MethodConstant id = entry.getKey();
                mapMethods.put(id, method);

                if (method.isVirtual())
                    {
                    mapVirtMethods.put(id.getNestedIdentity(), method);
                    }
                }
            }

        return new TypeInfo(typeNew, m_struct, m_cDepth, m_fAbstract,
                m_mapTypeParams, m_aannoClass,
                m_typeExtends, m_typeRebases, m_typeInto,
                m_listProcess, m_listmapClassChain, m_listmapDefaultChain,
                mapProps, mapMethods, mapVirtProps, mapVirtMethods, m_progress);
        }

    /**
     * Obtain a type resolver that uses the information from this type's type parameters.
     *
     * @param errs  the error list to log any errors to
     *
     * @return a GenericTypeResolver
     */
    public GenericTypeResolver ensureTypeResolver(ErrorListener errs)
        {
        assert errs != null;

        TypeResolver resolver = m_resolver;
        if (resolver == null || resolver.errs != errs)
            {
            m_resolver = resolver = new TypeResolver(m_mapTypeParams, errs);
            }
        return resolver;
        }

    /**
     * Contribute this TypeInfo's knowledge of potential call chain information to another deriving
     * type's TypeInfo information.
     *
     * @param listmapClassChain    the class chain being collected for the derivative type
     * @param listmapDefaultChain  the default chain being collected for the derivative type
     * @param composition          the composition of the contribution
     */
    public void contributeChains(
            ListMap<IdentityConstant, Origin> listmapClassChain,
            ListMap<IdentityConstant, Origin> listmapDefaultChain,
            Composition composition)
        {
        Origin originTrue  = m_type.new Origin(true);
        Origin originFalse = m_type.new Origin(false);
        if (composition != Composition.Implements && composition != Composition.Delegates)
            {
            boolean fAnnotation = composition == Composition.Annotation;
            for (Entry<IdentityConstant, Origin> entry : m_listmapClassChain.entrySet())
                {
                IdentityConstant constId      = entry.getKey();
                Origin           originThis   = entry.getValue();
                Origin           originResult = originThis.isAnchored() & fAnnotation
                                              ? originTrue
                                              : originFalse;
                Origin           originThat   = listmapClassChain.get(constId);
                if (originThat == null)
                    {
                    // the identity does not already appear in the chain, so add it to the chain
                    listmapClassChain.put(constId, originResult);
                    }
                else if (!originThat.isAnchored())
                    {
                    // the identity in the chain is owned by this type, so remove it from its old
                    // location in the chain, and add it to the end
                    listmapClassChain.remove(constId);
                    listmapClassChain.put(constId, originResult);
                    }
                // else ... the identity in the chain was "yanked" from us, so we can't claim it;
                // just leave it where it is in the chain
                }
            }

        // append our defaults to the default chain (just the ones that are absent from the chain)
        for (IdentityConstant constId : m_listmapDefaultChain.keySet())
            {
            listmapDefaultChain.putIfAbsent(constId, originTrue);
            }
        }

    /**
     * @return the type that the TypeInfo represents
     */
    public TypeConstant getType()
        {
        return m_type;
        }

    /**
     * @return the ClassStructure, or null if none is available; a non-abstract type will always
     *         have a ClassStructure
     */
    public ClassStructure getClassStructure()
        {
        return m_struct;
        }

    /**
     * @return the format of the topmost structure that the TypeConstant refers to, or
     *         {@code INTERFACE} for any non-class / non-mixin type (such as a difference type)
     */
    public Format getFormat()
        {
        return m_struct == null ? Format.INTERFACE : m_struct.getFormat();
        }

    /**
     * @return true iff this type is abstract, which is always true for an interface, and may be
     *         true for a class or mixin
     */
    public boolean isAbstract()
        {
        return m_fAbstract;
        }

    /**
     * @return true iff this type is static (a static global type is a singleton; a static local
     *         type does not hold a reference to its parent)
     */
    public boolean isStatic()
        {
        return m_struct != null && m_struct.isStatic();
        }

    /**
     * @return true if this type represents a singleton instance of a class
     */
    public boolean isSingleton()
        {
        return m_struct != null && m_struct.isSingleton();
        }

    /**
     * @return true iff this is a class type, which is not an interface type or a mixin type
     */
    public boolean isClass()
        {
        switch (getFormat())
            {
            case MODULE:
            case PACKAGE:
            case CLASS:
            case CONST:
            case ENUM:
            case ENUMVALUE:
            case SERVICE:
                return true;

            default:
                return false;
            }
        }

    /**
     * @return true iff this is a type that can be instantiated
     */
    public boolean isNewable()
        {
        return !isAbstract() && !isSingleton() && isClass();
        }

    /**
     * @return true iff this class is considered to be "top level"
     */
    public boolean isTopLevel()
        {
        return m_struct != null && m_struct.isTopLevel();
        }

    /**
     * @return true iff this class is scoped within another class, such that it requires a parent
     *         reference in order to be instantiated
     */
    public boolean isChild()
        {
        return isClass() && m_struct != null && m_struct.isChild();
        }

    /**
     * @return the complete set of type parameters declared within the type
     */
    public Map<String, ParamInfo> getTypeParams()
        {
        return m_mapTypeParams;
        }

    /**
     * @return the type annotations that had an "into" clause of "Class"
     */
    public Annotation[] getClassAnnotations()
        {
        return m_aannoClass;
        }

    /**
     * @return the TypeConstant representing the "mixin into" type for a mixin, or null if it is
     *         not a mixin
     */
    public TypeConstant getRebases()
        {
        return m_typeRebases;
        }

    /**
     * @return the TypeConstant representing the "mixin into" type for a mixin, or null if it is
     *         not a mixin
     */
    public TypeConstant getExtends()
        {
        return m_typeExtends;
        }

    /**
     * @return the TypeConstant representing the "mixin into" type for a mixin, or null if it is
     *         not a mixin
     */
    public TypeConstant getInto()
        {
        return m_typeInto;
        }

    /**
     * @return the list of contributions that made up this TypeInfo
     */
    public List<Contribution> getContributionList()
        {
        return m_listProcess;
        }

    /**
     * @return the potential call chain of classes
     */
    public ListMap<IdentityConstant, Origin> getClassChain()
        {
        return m_listmapClassChain;
        }

    /**
     * @return the potential default call chain of interfaces
     */
    public ListMap<IdentityConstant, Origin> getDefaultChain()
        {
        return m_listmapDefaultChain;
        }

    /**
     * @return all of the properties for this type, indexed by their "flattened" property constant
     */
    public Map<PropertyConstant, PropertyInfo> getProperties()
        {
        return m_mapProperties;
        }

    /**
     * @return all of the properties for this type that can be identified by a simple name, indexed
     *         by that name
     */
    public Map<String, PropertyInfo> ensurePropertiesByName()
        {
        Map<String, PropertyInfo> map = m_mapPropertiesByName;

        if (map == null)
            {
            map = new HashMap<>();
            for (PropertyInfo prop : m_mapProperties.values())
                {
                // only include the non-nested properties
                if (prop.getIdentity().getNestedDepth() == m_cDepth + 1)
                    {
// TODO requires collision check, with the private/private property being discarded
                    map.put(prop.getName(), prop);
                    }
                }

            m_mapPropertiesByName = map;
            }

        return map;
        }

    /**
     * Obtain all of the properties declared within the specified method.
     *
     * @param constMethod  the MethodConstant identifying the method that may contain properties
     *
     * @return a map from property name to PropertyInfo
     */
    public Map<String, PropertyInfo> ensureNestedPropertiesByName(MethodConstant constMethod)
        {
        Map<String, PropertyInfo> map = null;
        for (PropertyInfo prop : m_mapProperties.values())
            {
            // only include the properties nested under the specified method
            if (prop.getParent().equals(constMethod))
                {
                if (map == null)
                    {
                    map = new HashMap<>();
                    }
                map.put(prop.getName(), prop);
                }
            }

        return map == null
                ? Collections.EMPTY_MAP
                : map;
        }

    /**
     * Obtain all of the properties declared within the specified property.
     *
     * @param constProp  the PropertyConstant identifying the property that may contain properties
     *
     * @return a map from property name to PropertyInfo
     */
    public Map<String, PropertyInfo> ensureNestedPropertiesByName(PropertyConstant constProp)
        {
        Map<String, PropertyInfo> map = null;
        int cDepth = constProp.getNestedDepth();
        for (PropertyInfo prop : m_mapProperties.values())
            {
            IdentityConstant constParent = prop.getParent();
            // only include the properties nested under the specified property
            if (constParent == constProp || constParent.trailingPathEquals(constProp, cDepth))
                {
                if (map == null)
                    {
                    map = new HashMap<>();
                    }
                map.put(prop.getName(), prop);
                }
            }

        return map == null
                ? Collections.EMPTY_MAP
                : map;
        }

    public PropertyInfo findProperty(String sName)
        {
        return ensurePropertiesByName().get(sName);
        }

    /**
     * Look up the property by its identity constant.
     *
     * @param constId  the constant that identifies the property
     *
     * @return the PropertyInfo for the specified constant, or null
     */
    public PropertyInfo findProperty(PropertyConstant constId)
        {
        PropertyInfo prop = m_mapProperties.get(constId);
        if (prop != null)
            {
            return prop;
            }

        prop = ensurePropertiesByName().get(constId.getName());
        return prop != null && prop.isIdentityValid(constId) ? prop : null;
        }

    /**
     * @return all of the non-scoped methods for this type
     */
    public Map<MethodConstant, MethodInfo> getMethods()
        {
        return m_mapMethods;
        }

    // TODO this should be the "virt map" from Object to MethodInfo
    /**
     * @return all of the methods for this type that can be identified by just a signature, indexed
     *         by that signature
     */
    public Map<SignatureConstant, MethodInfo> ensureMethodsBySignature()
        {
        Map<SignatureConstant, MethodInfo> map = m_mapMethodsBySignature;

        if (map == null)
            {
            map = new HashMap<>();
            for (MethodInfo method : m_mapMethods.values())
                {
                // only include the non-nested Methods
                if (method.getIdentity().getNestedDepth() == m_cDepth + 2)
                    {
                    map.put(method.getSignature(), method);
                    }
                }

            m_mapMethodsBySignature = map;
            }

        return map;
        }

    public MethodInfo getMethodById(MethodConstant id)
        {
        return getMethods().get(id);
        }

    public MethodInfo getMethodByNestedId(Object nid)
        {
        // TODO
        return null;
        }

    public MethodBody[] getOptimizedMethodChain(Object nid)
        {
        MethodInfo info = getMethodByNestedId(nid);
        return info == null
                ? null
                : info.ensureOptimizedMethodChain(this);
        }

    /**
     * Obtain all of the methods that are annotated with "@Op".
     *
     * @return a set of zero or more method constants
     */
    public Set<MethodInfo> getOpMethodInfos()
        {
        Set<MethodInfo> setOps = m_setOps;
        if (setOps == null)
            {
            for (MethodInfo info : ensureMethodsBySignature().values())
                {
                if (info.isOp())
                    {
                    if (setOps == null)
                        {
                        setOps = new HashSet<>(7);
                        }
                    setOps.add(info);
                    }
                }

            // cache the result
            m_setOps = setOps = (setOps == null ? Collections.EMPTY_SET : setOps);
            }

        return setOps;
        }

    /**
     * Given the specified method signature, find the most appropriate method that matches that
     * signature, and return that method. If there is no matching method, then return null. If
     * there are multiple methods that match, but it is ambiguous which method is "the best"
     * match, then log an error to the error list, and return null.
     *
     * @param constSig  the method signature to search for
     * @param errs      the error list to log errors to
     *
     * @return the MethodInfo for the method that is the "best match" for the signature, or null
     *         if no method is a best match (including the case in which more than one method
     *         matches, but no one of those methods is a provable unambiguous "best match")
     */
    public MethodInfo findMethod(SignatureConstant constSig, ErrorListener errs)
        {
        // TODO
        return null;
        }

    /**
     * Obtain all of the matching op methods for the specified name and/or the operator string, that
     * take the specified number of params.
     *
     * @param sName    the default op name, such as "add"
     * @param sOp      the operator string, such as "+"
     * @param cParams  the number of parameters for the operator method, such as 1
     *
     * @return a set of zero or more method constants
     */
    public Set<MethodConstant> findOpMethods(String sName, String sOp, int cParams)
        {
        Set<MethodConstant> setOps = null;

        String sKey = sName + sOp + cParams;
        if (m_sOp != null && sKey.equals(m_sOp))
            {
            setOps = m_setOp;
            }
        else
            {
            for (MethodInfo info : getOpMethodInfos())
                {
                if (info.isOp(sName, sOp, cParams))
                    {
                    if (setOps == null)
                        {
                        setOps = new HashSet<>(7);
                        }
                    setOps.add(info.getIdentity());
                    }
                }

            // cache the result
            m_sOp   = sKey;
            m_setOp = setOps = (setOps == null ? Collections.EMPTY_SET : setOps);
            }

        return setOps;
        }

    /**
     * Obtain all of the auto conversion methods found on this type.
     *
     * @return a set of zero or more method constants
     */
    public Set<MethodInfo> getAutoMethodInfos()
        {
        Set<MethodInfo> setAuto = m_setAuto;
        if (setAuto == null)
            {
            for (MethodInfo info : ensureMethodsBySignature().values())
                {
                if (info.isAuto())
                    {
                    if (setAuto == null)
                        {
                        setAuto = new HashSet<>(7);
                        }
                    setAuto.add(info);
                    }
                }

            // cache the result
            m_setAuto = setAuto = (setAuto == null ? Collections.EMPTY_SET : setAuto);
            }

        return setAuto;
        }

    /**
     * Find a method on this type that converts an object of this type to a desired type.
     *
     * @param typeDesired  the type desired to convert to, or that the conversion result would be
     *                     assignable to ("isA" would be true)
     *
     * @return a MethodConstant representing an {@code @Auto} conversion method resulting in an
     *         object whose type is compatible with the specified (desired) type, or null if either
     *         no method matches, or more than one method matches (ambiguous)
     */
    public MethodConstant findConversion(TypeConstant typeDesired)
        {
        MethodConstant methodMatch = null;

        // check the cached result
        if (m_typeAuto != null && typeDesired.equals(m_typeAuto))
            {
            methodMatch = m_methodAuto;
            }
        else
            {
            for (MethodInfo info : getAutoMethodInfos())
                {
                MethodConstant method     = info.getIdentity();
                TypeConstant   typeResult = method.getRawReturns()[0];
                if (typeResult.equals(typeDesired))
                    {
                    // exact match -- it's not going to get any better than this
                    return method;
                    }

                if (typeResult.isA(typeDesired))
                    {
                    if (methodMatch == null)
                        {
                        methodMatch = method;
                        }
                    else
                        {
                        TypeConstant typeResultMatch = methodMatch.getRawReturns()[0];
                        boolean fSub = typeResult.isA(typeResultMatch);
                        boolean fSup = typeResultMatch.isA(typeResult);
                        if (fSub ^ fSup)
                            {
                            // use the obviously-more-specific type conversion
                            methodMatch = fSub ? method : methodMatch;
                            }
                        else
                            {
                            // ambiguous - there are at least two methods that match
                            methodMatch = null;
                            break;
                            }
                        }
                    }
                }

            // cache the result
            m_typeAuto   = typeDesired;
            m_methodAuto = methodMatch;
            }

        return methodMatch;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("TypeInfo: ")
          .append(m_type.getValueString())
          .append(" (format=")
          .append(getFormat())
          .append(")");

        if (!m_mapTypeParams.isEmpty())
            {
            sb.append("\n- Parameters (")
              .append(m_mapTypeParams.size())
              .append(')');
            int i = 0;
            for (Entry<String, ParamInfo> entry : m_mapTypeParams.entrySet())
                {
                sb.append("\n  [")
                  .append(i++)
                  .append("] ")
                  .append(entry.getKey())
                  .append("=")
                  .append(entry.getValue());
                }
            }

        if (m_typeInto != null)
            {
            sb.append("\n- Into: ")
              .append(m_typeInto.getValueString());
            }
        if (m_typeRebases != null)
            {
            sb.append("\n- Rebases: ")
              .append(m_typeRebases.getValueString());
            }
        if (m_typeExtends != null)
            {
            sb.append("\n- Extends: ")
              .append(m_typeExtends.getValueString());
            }

        if (!m_listmapClassChain.isEmpty())
            {
            sb.append("\n- Class Chain (")
              .append(m_listmapClassChain.size())
              .append(')');
            int i = 0;
            for (Entry<IdentityConstant, Origin> entry : m_listmapClassChain.entrySet())
                {
                sb.append("\n  [")
                  .append(i++)
                  .append("] ")
                  .append(entry.getKey().getValueString());

                if (entry.getValue().isAnchored())
                    {
                    sb.append(" (Anchored)");
                    }
                }
            }

        if (!m_listmapDefaultChain.isEmpty())
            {
            sb.append("\n- Default Chain (")
              .append(m_listmapDefaultChain.size())
              .append(')');
            int i = 0;
            for (IdentityConstant constId : m_listmapDefaultChain.keySet())
                {
                sb.append("\n  [")
                  .append(i++)
                  .append("] ")
                  .append(constId.getValueString());
                }
            }

        if (!m_mapProperties.isEmpty())
            {
            sb.append("\n- Properties (")
              .append(m_mapProperties.size())
              .append(')');
            int i = 0;
            for (Entry<PropertyConstant, PropertyInfo> entry : m_mapProperties.entrySet())
                {
                sb.append("\n  [")
                  .append(i++)
                  .append("] ")
                  .append(entry.getKey())
                  .append("=")
                  .append(entry.getValue());
                }
            }

        if (!m_mapMethods.isEmpty())
            {
            sb.append("\n- Methods (")
              .append(m_mapMethods.size())
              .append(')');
            int i = 0;
            for (Entry<MethodConstant, MethodInfo> entry : m_mapMethods.entrySet())
                {
                sb.append("\n  [")
                  .append(i++)
                  .append("] ")
                  .append(entry.getKey().getValueString())
                  .append("=")
                  .append(entry.getValue());
                }
            }

        return sb.toString();
        }


    // ----- deferred TypeInfo creation ------------------------------------------------------------

    Progress getProgress()
        {
        return m_progress;
        }

    boolean isPlaceHolder()
        {
        return m_progress == Progress.Building;
        }

    boolean isIncomplete()
        {
        return m_progress == Progress.Incomplete;
        }

    boolean isComplete()
        {
        return m_progress == Progress.Complete;
        }


    // ----- internal helpers ----------------------------------------------------------------------

    public static Annotation[] validateAnnotations(Annotation[] annotations)
        {
        if (annotations == null)
            {
            return Annotation.NO_ANNOTATIONS;
            }

        for (Annotation annotation : annotations)
            {
            if (annotation == null)
                {
                throw new IllegalStateException("null annotation");
                }
            }

        return annotations;
        }

    public static Annotation[] mergeAnnotations(Annotation[] anno1, Annotation[] anno2)
        {
        if (anno1.length == 0)
            {
            return anno2;
            }

        if (anno2.length == 0)
            {
            return anno1;
            }

        ArrayList<Annotation> list = new ArrayList<>();
        Set<Constant> setPresent = new HashSet<>();
        appendAnnotations(list, anno1, setPresent);
        appendAnnotations(list, anno2, setPresent);
        return list.toArray(new Annotation[list.size()]);
        }

    public static void appendAnnotations(ArrayList<Annotation> list, Annotation[] aAnno, Set<Constant> setPresent)
        {
        for (Annotation anno : aAnno)
            {
            if (setPresent.add(anno.getAnnotationClass()))
                {
                list.add(anno);
                }
            }
        }

    public static boolean containsAnnotation(Annotation[] annotations, String sName)
        {
        if (annotations == null || annotations.length == 0)
            {
            return false;
            }

        IdentityConstant clzFind = annotations[0].getConstantPool().getImplicitlyImportedIdentity(sName);
        for (Annotation annotation : annotations)
            {
            if (annotation.getAnnotationClass().equals(clzFind))
                {
                return true;
                }
            }

        return false;
        }


    // ----- fields --------------------------------------------------------------------------------

    public enum Progress {Absent, Building, Incomplete, Complete}


    /**
     * Represents the completeness of the TypeInfo.
     */
    private final Progress m_progress;

    /**
     * The data type that this TypeInfo represents.
     */
    private final TypeConstant m_type;

    /**
     * The ClassStructure of the type, if the type is based on a ClassStructure.
     */
    private final ClassStructure m_struct;

    /**
     * The "depth from class" for this TypeInfo. A TypeInfo for an actual class will have a depth of
     * {@code 0}.
     */
    private final int m_cDepth;

    /**
     * Whether this type is abstract, which is always true for an interface, and may be true for a
     * class or mixin.
     */
    private final boolean m_fAbstract;

    /**
     * The type parameters for this TypeInfo.
     */
    private final Map<String, ParamInfo> m_mapTypeParams;

    /**
     * The class annotations.
     */
    private final Annotation[] m_aannoClass;

    /**
     * The type that is extended. The term "extends" has slightly different meanings for mixins and
     * other classes.
     */
    private final TypeConstant m_typeExtends;

    /**
     * The type that is rebased onto.
     */
    private final TypeConstant m_typeRebases;

    /**
     * For mixins, the type that is mixed into. For interfaces, this is always Object.
     */
    private final TypeConstant m_typeInto;

    /**
     * The list of contributions that made up this TypeInfo.
     */
    private final List<Contribution> m_listProcess;

    /**
     * The potential call chain of classes.
     */
    private final ListMap<IdentityConstant, Origin> m_listmapClassChain;

    /**
     * The potential default call chain of interfaces.
     */
    private final ListMap<IdentityConstant, Origin> m_listmapDefaultChain;

    /**
     * The properties of this type, indexed by PropertyConstant. Constants, private properties, and
     * properties declared within methods, are identified only by a single (non-virtual) property
     * constant. Other properties can show up at multiple virtual levels, and thus the same property
     * may be referred to by different PropertyConstants, although it will only show up once in this
     * map (using the identity from the highest "pane of glass" that the property shows up on.)
     */
    private final Map<PropertyConstant, PropertyInfo> m_mapProperties;

    /**
     * The properties of the type, indexed by nested identity. Properties nested immediately under
     * a class are identified by their (String) name, while properties nested further below the
     * class are identified by a NestedIdentity object. In either case, the index can be obtained by
     * calling {@link PropertyConstant#getNestedIdentity()}.
     */
    private final Map<Object, PropertyInfo> m_mapVirtProperties;

    // TODO can this be removed?
    /**
     * The properties of the type, indexed by name. This will not include nested properties, such
     * as those nested within a property or method. Lazily initialized
     */
    private transient Map<String, PropertyInfo> m_mapPropertiesByName;

    /**
     * The methods of the type, indexed by MethodConstant. Functions, private methods, and other
     * non-virtual methods are identified only by a single (non-virtual) MethodConstant. Other
     * methods can show up a multiple virtual levels, and thus the same method chain may be referred
     * to by different MethodConstants, although each virtual method will show up only once in this
     * map (using the identity from the highest "pane of glass" that the method shows up on.)
     */
    private final Map<MethodConstant, MethodInfo> m_mapMethods;

    /**
     * The virtual methods of the type, indexed by nested identity. Methods nested immediately under
     * a class are identified by their signature, while methods nested further below the class are
     * identified by a NestedIdentity object. In either case, the index can be obtained by calling
     * {@link MethodConstant#getNestedIdentity()}.
     */
    private final Map<Object, MethodInfo> m_mapVirtMethods;

    // TODO can this be removed?
    /**
     * The methods of the type, indexed by signature. This will not include nested methods, such
     * as those nested within a property or method. Lazily initialized
     */
    private transient Map<SignatureConstant, MethodInfo> m_mapMethodsBySignature;

    /**
     * A cached type resolver.
     */
    private transient TypeResolver m_resolver;

    // cached query results
    private transient Set<MethodInfo>     m_setAuto;
    private transient Set<MethodInfo>     m_setOps;
    private transient String              m_sOp;
    private transient Set<MethodConstant> m_setOp;
    private transient TypeConstant        m_typeAuto;
    private transient MethodConstant      m_methodAuto;
    }
