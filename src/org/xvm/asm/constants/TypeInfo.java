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
     * @param fAbstract            true if the type is abstract
     * @param mapTypeParams        the collected type parameters for the type
     * @param aannoClass           the annotations for the type that mix into "Class"
     * @param typeExtends          the type that is extended
     * @param typeRebases          the type that is rebased onto
     * @param typeInto             for mixins, the type that is mixed into; for interfaces, Object
     * @param listProcess
     * @param listmapClassChain    the potential call chain of classes
     * @param listmapDefaultChain  the potential call chain of default implementations
     * @param mapProperties        the public and protected properties of the type
     * @param mapScopedProperties  the various scoped properties of the type
     * @param mapMethods           the public and protected methods of the type
     * @param mapScopedMethods     the various scoped methods of the type
     * @param progress             the Progress for this TypeInfo
     */
    public TypeInfo(TypeConstant type, ClassStructure struct, boolean fAbstract,
            Map<String, ParamInfo> mapTypeParams, Annotation[] aannoClass,
            TypeConstant typeExtends, TypeConstant typeRebases, TypeConstant typeInto,
            List<Contribution>                 listProcess,
            ListMap<IdentityConstant, Boolean> listmapClassChain,
            ListMap<IdentityConstant, Boolean> listmapDefaultChain,
            Map<String, PropertyInfo> mapProperties, Map<PropertyConstant, PropertyInfo> mapScopedProperties,
            Map<SignatureConstant, MethodInfo> mapMethods, Map<MethodConstant, MethodInfo> mapScopedMethods,
            Progress progress)
        {
        assert progress != null && progress != Progress.Absent;
        assert type != null;
        assert mapTypeParams != null;
        assert listmapClassChain != null;
        assert listmapDefaultChain != null;
        assert mapProperties != null;
        assert mapScopedProperties != null;
        assert mapMethods != null;
        assert mapScopedMethods != null;

        m_type                  = type;
        m_struct                = struct;
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
        m_mapScopedProperties   = mapScopedProperties;
        m_mapMethods            = mapMethods;
        m_mapScopedMethods      = mapScopedMethods;
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

        Map<String           , PropertyInfo> mapProperties       = new HashMap<>();
        Map<PropertyConstant , PropertyInfo> mapScopedProperties = new HashMap<>();
        Map<SignatureConstant, MethodInfo  > mapMethods          = new HashMap<>();
        Map<MethodConstant   , MethodInfo  > mapScopedMethods    = new HashMap<>();

        for (Entry<String, PropertyInfo> entry : m_mapProperties.entrySet())
            {
            PropertyInfo propertyInfo = entry.getValue().limitAccess(access);
            if (propertyInfo != null)
                {
                mapProperties.put(entry.getKey(), propertyInfo);
                }
            }

        for (Entry<PropertyConstant, PropertyInfo> entry : m_mapScopedProperties.entrySet())
            {
            PropertyInfo propertyInfo = entry.getValue().limitAccess(access);
            if (propertyInfo != null)
                {
                mapScopedProperties.put(entry.getKey(), propertyInfo);
                }
            }

        for (Entry<SignatureConstant, MethodInfo> entry : m_mapMethods.entrySet())
            {
            if (entry.getValue().getAccess().compareTo(access) <= 0)
                {
                mapMethods.put(entry.getKey(), entry.getValue());
                }
            }

        for (Entry<MethodConstant, MethodInfo> entry : m_mapScopedMethods.entrySet())
            {
            if (entry.getValue().getAccess().compareTo(access) <= 0)
                {
                mapScopedMethods.put(entry.getKey(), entry.getValue());
                }
            }

        return new TypeInfo(typeNew, m_struct, m_fAbstract,
                m_mapTypeParams, m_aannoClass,
                m_typeExtends, m_typeRebases, m_typeInto,
                m_listProcess, m_listmapClassChain, m_listmapDefaultChain,
                mapProperties, mapScopedProperties, mapMethods, mapScopedMethods, m_progress);
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
            ListMap<IdentityConstant, Boolean> listmapClassChain,
            ListMap<IdentityConstant, Boolean> listmapDefaultChain,
            Composition composition)
        {
        if (composition != Composition.Implements && composition != Composition.Delegates)
            {
            boolean fAnnotation = composition == Composition.Annotation;
            for (Entry<IdentityConstant, Boolean> entry : m_listmapClassChain.entrySet())
                {
                IdentityConstant constId = entry.getKey();
                boolean fYank = entry.getValue();

                Boolean BAnchored = listmapClassChain.get(constId);
                if (BAnchored == null)
                    {
                    // the identity does not already appear in the chain, so add it to the chain
                    listmapClassChain.put(constId, fAnnotation & fYank);
                    }
                else if (!BAnchored)
                    {
                    // the identity in the chain is owned by this type, so remove it from its old
                    // location in the chain, and add it to the end
                    listmapClassChain.remove(constId);
                    listmapClassChain.put(constId, fAnnotation & fYank);
                    }
                // else ... the identity in the chain was "yanked" from us, so we can't claim it;
                // just leave it where it is in the chain
                }
            }

        // append our defaults to the default chain (just the ones that are absent from the chain)
        for (IdentityConstant constId : m_listmapDefaultChain.keySet())
            {
            listmapDefaultChain.putIfAbsent(constId, true);
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
    public ListMap<IdentityConstant, Boolean> getClassChain()
        {
        return m_listmapClassChain;
        }

    /**
     * @return the potential default call chain of interfaces
     */
    public ListMap<IdentityConstant, Boolean> getDefaultChain()
        {
        return m_listmapDefaultChain;
        }

    /**
     * @return all of the non-scoped properties for this type
     */
    public Map<String, PropertyInfo> getProperties()
        {
        return m_mapProperties;
        }

    /**
     * @return all of the scoped properties for this type
     */
    Map<PropertyConstant, PropertyInfo> getScopedProperties()
        {
        return m_mapScopedProperties;
        }

    /**
     * @return all of the non-scoped methods for this type
     */
    public Map<SignatureConstant, MethodInfo> getMethods()
        {
        return m_mapMethods;
        }

    /**
     * @return all of the scoped methods for this type
     */
    Map<MethodConstant, MethodInfo> getScopedMethods()
        {
        return m_mapScopedMethods;
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
            for (MethodInfo info : m_mapMethods.values())
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
                    setOps.add(info.getMethodConstant());
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
            for (MethodInfo info : m_mapMethods.values())
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
                MethodConstant method     = info.getMethodConstant();
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
            for (Entry<IdentityConstant, Boolean> entry : m_listmapClassChain.entrySet())
                {
                sb.append("\n  [")
                  .append(i++)
                  .append("] ")
                  .append(entry.getKey().getValueString());

                if (entry.getValue())
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
            for (Entry<String, PropertyInfo> entry : m_mapProperties.entrySet())
                {
                sb.append("\n  [")
                  .append(i++)
                  .append("] ")
                  .append(entry.getKey())
                  .append("=")
                  .append(entry.getValue());
                }
            }

        if (!m_mapScopedProperties.isEmpty())
            {
            sb.append("\n- Scoped Properties (")
              .append(m_mapScopedProperties.size())
              .append(')');
            int i = 0;
            for (Entry<PropertyConstant, PropertyInfo> entry : m_mapScopedProperties.entrySet())
                {
                sb.append("\n  [")
                  .append(i++)
                  .append("] ")
                  .append(entry.getKey().getValueString())
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
            for (Entry<SignatureConstant, MethodInfo> entry : m_mapMethods.entrySet())
                {
                sb.append("\n  [")
                  .append(i++)
                  .append("] ")
                  .append(entry.getKey().getValueString())
                  .append("=")
                  .append(entry.getValue());
                }
            }

        if (!m_mapScopedMethods.isEmpty())
            {
            sb.append("\n- Scoped Methods (")
              .append(m_mapScopedMethods.size())
              .append(')');
            int i = 0;
            for (Entry<MethodConstant, MethodInfo> entry : m_mapScopedMethods.entrySet())
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
    private final ListMap<IdentityConstant, Boolean> m_listmapClassChain;

    /**
     * The potential default call chain of interfaces.
     */
    private final ListMap<IdentityConstant, Boolean> m_listmapDefaultChain;

    /**
     * The properties of the type.
     */
    private final Map<String, PropertyInfo> m_mapProperties;

    /**
     * The scoped properties for this type.
     */
    private final Map<PropertyConstant, PropertyInfo> m_mapScopedProperties;

    /**
     * The methods of the type.
     */
    private final Map<SignatureConstant, MethodInfo> m_mapMethods;

    /**
     * The scoped methods for this type.
     */
    private final Map<MethodConstant, MethodInfo> m_mapScopedMethods;

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
