package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.function.Consumer;
import java.util.function.Predicate;

import org.xvm.asm.constants.*;
import org.xvm.asm.constants.IdentityConstant.NestedIdentity;
import org.xvm.asm.constants.TypeConstant.Relation;

import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.MethodStructure.ConcurrencySafety;

import org.xvm.asm.op.*;

import org.xvm.compiler.Constants;

import org.xvm.runtime.ClassComposition.FieldInfo;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.reflect.xRef.RefHandle;

import org.xvm.util.Handy;
import org.xvm.util.LinkedIterator;
import org.xvm.util.ListMap;
import org.xvm.util.Severity;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An XVM Structure that represents an entire Class. This is also the base class for module and
 * package structures.
 */
public class ClassStructure
        extends Component
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ClassStructure with the specified identity.
     *
     * @param xsParent   the XvmStructure that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Module, Package, or Class
     * @param condition  the optional condition for this ClassStructure
     */
    protected ClassStructure(XvmStructure xsParent, int nFlags, IdentityConstant constId,
                             ConditionalConstant condition)
        {
        super(xsParent, nFlags, constId, condition);
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the literal constant indicating the source path, or Null
     */
    public LiteralConstant getSourcePath()
        {
        if (m_constPath != null)
            {
            return m_constPath;
            }

        if (getFormat() == Format.MODULE)
            {
            return null;
            }

        Component outer = getParent();
        while (!(outer instanceof ClassStructure))
            {
            outer = outer.getParent();
            }
        return ((ClassStructure) outer).getSourcePath();
        }

    /**
     * Specify the source path.
     *
     * @param constPath  the literal constant indicating the source path, or Null
     */
    public void setSourcePath(LiteralConstant constPath)
        {
        m_constPath = constPath;
        markModified();
        }

    /**
     * Return the simple name of the source file that this class originates from.
     *
     * @return the file name
     */
    public String getSourceFileName()
        {
        LiteralConstant constPath = getSourcePath();
        if (constPath != null)
            {
            String sPath = constPath.getValue();
            int of = sPath.lastIndexOf('/');
            return of >= 0 ? sPath.substring(of+1) : sPath;
            }

        // best guess: walk up to the first "top level" class
        ClassStructure clzTopLevel = this;
        while (!clzTopLevel.isTopLevel())
            {
            clzTopLevel = clzTopLevel.getOuter();
            }

        // assume the implied class name
        return clzTopLevel.getSimpleName() + ".x";
        }

    /**
     * @return true iff this class is a singleton
     */
    public boolean isSingleton()
        {
        switch (getFormat())
            {
            case MODULE:
            case PACKAGE:
            case ENUMVALUE:
                // these types are always singletons
                return true;

            case INTERFACE:
            case CLASS:
            case ENUM:
            case MIXIN:
                // these types are never singletons
                return false;

            case CONST:
            case SERVICE:
                // these COULD be singletons (if they are static and NOT an inner class)
                if (isStatic())
                    {
                    Format format = getParent().getFormat();
                    return format == Format.MODULE || format == Format.PACKAGE;
                    }
                return false;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Check if this class is annotated as "Abstract".
     *
     * Note: class structures are never marked with the {@link #isAbstract abstract} flag and
     * can only be made abstract via the annotation.
     *
     * @return true iff this class is annotated as Abstract
     */
    public boolean isExplicitlyAbstract()
        {
        return containsAnnotation(getConstantPool().clzAbstract());
        }

    /**
     * Check if this class is annotated as "Override".
     *
     * @return true iff this class is annotated with {@code @Override}
     */
    public boolean isExplicitlyOverride()
        {
        return containsAnnotation(getConstantPool().clzOverride());
        }

    /**
     * @return true iff this class is annotated by the specified annotation
     */
    public boolean containsAnnotation(ClassConstant idAnno)
        {
        for (Contribution contrib : getContributionsAsList())
            {
            if (contrib.getComposition() == Composition.Annotation)
                {
                TypeConstant type = contrib.getTypeConstant();

                if (type.isExplicitClassIdentity(false) &&
                    type.getSingleUnderlyingClass(false).equals(idAnno))
                    {
                    return true;
                    }
                }
            }
        return false;
        }

    /**
     * Collect the annotations for this class.
     *
     * @param fIntoClass if true, return only the "Class" annotations (e.g. Abstract); otherwise
     *                   only the "regular" mixins
     *
     * @return an array of annotations
     */
    public Annotation[] collectAnnotations(boolean fIntoClass)
        {
        Annotation[] annos = fIntoClass ? m_aAnnoClass : m_aAnnoMixin;
        if (annos == null)
            {
            List<Annotation> listAnnos = null;

            for (Contribution contrib : getContributionsAsList())
                {
                if (contrib.getComposition() == Composition.Annotation)
                    {
                    Annotation anno = contrib.getAnnotation();

                    if (fIntoClass == anno.getAnnotationType().getExplicitClassInto().isIntoClassType())
                        {
                        if (listAnnos == null)
                            {
                            listAnnos = new ArrayList<>();
                            }
                        listAnnos.add(anno);
                        }
                    }
                }
            annos = listAnnos == null
                    ? Annotation.NO_ANNOTATIONS
                    : listAnnos.toArray(Annotation.NO_ANNOTATIONS);
            if (fIntoClass)
                {
                m_aAnnoClass = annos;
                }
            else
                {
                m_aAnnoMixin = annos;
                }
            }
        return annos;
        }

    /**
     * @return true iff this class is a module, package, or class whose immediate parent is a module
     *         or package
     */
    public boolean isTopLevel()
        {
        switch (getFormat())
            {
            case MODULE:
            case PACKAGE:
                return true;

            case INTERFACE:
            case CLASS:
            case ENUM:
            case MIXIN:
            case CONST:
            case SERVICE:
                {
                Format format = getParent().getFormat();
                return format == Format.MODULE || format == Format.PACKAGE;
                }

            case ENUMVALUE:
                // enum values are always a child of an enum
                return false;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * @return true iff this class is an inner class nested under a method or property member
     */
    public boolean isMemberClass()
        {
        return switch (getParent().getFormat())
            {
            case METHOD, PROPERTY -> true;
            default               -> false;
            };
        }

    /**
     * @return true iff this class is an anonymous inner class
     */
    public boolean isAnonInnerClass()
        {
        return isMemberClass() && isSynthetic()
                && getIdentityConstant().getParentConstant() instanceof MethodConstant;
        }

    /**
     * @return true iff this class is a non-static inner class
     */
    public boolean isInnerChild()
        {
        return isMemberClass() && !isStatic();
        }

    /**
     * @return true iff this class is a virtual child of its parent
     */
    public boolean isVirtualChild()
        {
        switch (getFormat())
            {
            case MODULE:
            case PACKAGE:
            case ENUM:
            case ENUMVALUE:
                return false;

            case INTERFACE:
            case MIXIN:
            case CLASS:
            case CONST:
            case SERVICE:
                {
                if (isSynthetic() || isStatic())
                    {
                    // anonymous and static child classes are not virtual
                    return false;
                    }

                Component parent = getParent();
                Format    format = parent.getFormat();
                while (format == Format.PROPERTY)
                    {
                    parent = parent.getParent();
                    format = parent.getFormat();
                    }
                // neither a top-level class nor a local class inside a method are considered
                // virtual children
                return format != Format.MODULE && format != Format.PACKAGE && format != Format.METHOD;
                }

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * @return true iff an object of this class needs to hold a reference to its parent
     */
    public boolean isInstanceChild()
        {
        return isVirtualChild() || isInnerChild();
        }

    /**
     * Note: A virtual child class can be instantiated using any of the "NEWC_*" op codes.
     *       Singletons and abstract types (mixins, interfaces) are not instantiatable.
     *
     * @return true iff this class is a virtual child class
     */
    public boolean isVirtualChildClass()
        {
        if (isVirtualChild())
            {
            switch (getFormat())
                {
                case CLASS:
                case CONST:
                case SERVICE:
                    return true;
                }
            }
        return false;
        }

    /**
     * @return true iff this class is the specified parent class itself, or a virtual child of that
     *         class or any of its virtual children (recursively)
     */
    public boolean isVirtualDescendant(ClassConstant idParent)
        {
        return getIdentityConstant().equals(idParent) ||
                isVirtualChild() && getOuter().isVirtualDescendant(idParent);
        }

    /**
     * @return true iff this class is the specified parent class itself, or a child of that class or
     *         any of its children (recursively)
     */
    public boolean isDescendant(IdentityConstant idParent)
        {
        return getIdentityConstant().equals(idParent) ||
            getParent() instanceof ClassStructure parent && parent.isDescendant(idParent);
        }

    /**
     * Ensure that all generic types referred to by the specified type are accessible in the context
     * of this class.
     *
     * @param type  the type to check
     *
     * @return null iff all generic types are accessible; otherwise a non-accessible generic
     *         property name (used for error reporting)
     */
    public String checkGenericTypeVisibility(TypeConstant type)
        {
        String[] asName = new String[1];

        Consumer<Constant> visitor = new Consumer<>()
            {
            public void accept(Constant c)
                {
                if (c instanceof TypeConstant t)
                    {
                    if (t.isGenericType())
                        {
                        Constant constId = t.getDefiningConstant();
                        if (constId.getFormat() == Constant.Format.Property)
                            {
                            String sName = ((PropertyConstant) constId).getName();
                            if (!getFormalType().containsGenericParam(sName)
                                    && asName[0] == null)
                                {
                                asName[0] = sName;
                                }
                            }
                        }
                    else
                        {
                        t.forEachUnderlying(this);
                        }
                    }
                }
            };

        type.forEachUnderlying(visitor);
        return asName[0];
        }

    /**
     * @return an "outer this" class structure
     */
    public ClassStructure getOuter()
        {
        assert isInstanceChild();

        Component parent = getParent();
        while (true)
            {
            switch (parent.getFormat())
                {
                case MULTIMETHOD:
                case METHOD:
                case PROPERTY:
                    parent = parent.getParent();
                    continue;

                case MODULE:
                case PACKAGE:
                case INTERFACE:
                case CLASS:
                case CONST:
                case SERVICE:
                case ENUM:
                case ENUMVALUE:
                case MIXIN:
                    return (ClassStructure) parent;

                default:
                    throw new IllegalStateException(
                        parent.getIdentityConstant() + " format=" + parent.getFormat());
                }
            }
        }

    /**
     * @return true iff this class is a Service, or a virtual child thereof
     */
    public boolean isService()
        {
        return getFormat() == Format.SERVICE || isVirtualChild() && getOuter().isService();
        }

    /**
     * @return true iff this class is a Const
     */
    public boolean isConst()
        {
        return switch (getFormat())
            {
            case MODULE, PACKAGE, CONST, ENUM, ENUMVALUE ->
                true;

            default ->
                false;
            };
        }

    /**
     * @return true iff this class implements an "immutable X" interface
     */
    public boolean isImmutable()
        {
        switch (getFormat())
            {
            case MODULE:
            case PACKAGE:
            case CONST:
            case ENUM:
            case ENUMVALUE:
                return true;

            case MIXIN:
                {
                TypeConstant typeInto = getTypeInto();
                if (typeInto.containsUnresolved())
                    {
                    return false;
                    }
                if (typeInto.isImmutable())
                    {
                    return true;
                    }
                }
                // fall through
            case CLASS:
            case INTERFACE:
                for (Contribution contrib : getContributionsAsList())
                    {
                    if (contrib.getComposition() == Composition.Implements)
                        {
                        if (!contrib.containsUnresolved() && contrib.getTypeConstant().isImmutable())
                            {
                            return true;
                            }
                        }
                    }
                return false;

            case SERVICE: // service is always assumed to be NOT immutable
            default:
                return false;
            }
        }

    /**
     * Get a virtual child class by the specified name on this class or any of its contributions.
     *
     * @param sName  the child class name
     *
     * @return a child structure or null if not found
     */
    public ClassStructure getVirtualChild(String sName)
        {
        Component child = findChildDeep(sName, true);
        return child instanceof ClassStructure clz && clz.isVirtualChild()
                ? (ClassStructure) child
                : null;
        }

    /**
     * Find a child with a given name in the class or any of its contributions.
     */
    public Component findChildDeep(String sName)
        {
        return findChildDeep(sName, true);
        }

    private Component findChildDeep(String sName, boolean fAllowInto)
        {
        Component child = getChild(sName);
        if (child != null)
            {
            return child;
            }

        for (Contribution contrib : getContributionsAsList())
            {
            TypeConstant typeContrib = contrib.getTypeConstant();

            // TODO: allow intersection types to be traversed as well
            if (   typeContrib.containsUnresolved()
               || !typeContrib.isExplicitClassIdentity(true)) // disregard relational type contributions
                {
                continue;
                }

            boolean fCheck;
            switch (contrib.getComposition())
                {
                case Annotation:
                    fCheck     = !isIntoClassMixin(typeContrib);
                    fAllowInto = false;
                    break;

                case Into:
                    fCheck     = fAllowInto;
                    fAllowInto = false;
                    break;

                case Incorporates:
                    fAllowInto = false;
                    // break through
                case Delegates, Implements, Extends:
                    fCheck = true;
                    break;

                default:
                    fCheck = false;
                    break;
                }

            if (fCheck)
                {
                ClassStructure clzContrib = (ClassStructure)
                        typeContrib.getSingleUnderlyingClass(true).getComponent();
                child = clzContrib.findChildDeep(sName, fAllowInto);
                if (child != null)
                    {
                    return child;
                    }
                }
            }
        return null;
        }

    /**
     * @return the number of type parameters for this class
     */
    public int getTypeParamCount()
        {
        Map<StringConstant, TypeConstant> mapThis = m_mapParams;
        return mapThis == null
                ? 0
                : mapThis.size();
        }

    /**
     * Obtain the type parameters for the class as an ordered read-only map, keyed by name and with
     * a corresponding value of the type constraint for the parameter.
     *
     * @return a read-only map of type parameter name to type
     */
    public ListMap<StringConstant, TypeConstant> getTypeParams()
        {
        ListMap<StringConstant, TypeConstant> mapThis = m_mapParams;
        return mapThis == null
                ? ListMap.EMPTY
                : mapThis;
        }

    /**
     * Obtain the type parameters for the class as a list of map entries from name to type.
     *
     * @return a read-only list of map entries from type parameter name to type
     */
    public List<Map.Entry<StringConstant, TypeConstant>> getTypeParamsAsList()
        {
        ListMap<StringConstant, TypeConstant> mapThis = m_mapParams;
        return mapThis == null
                ? Collections.EMPTY_LIST
                : mapThis.asList();
        }

    /**
     * Add a generic type parameter.
     *
     * @param sName            the type parameter name
     * @param typeConstraint   the type parameter constraint type
     *
     * @return a newly created PropertyStructure that represents the generic type parameter
     */
    public PropertyStructure addTypeParam(String sName, TypeConstant typeConstraint)
        {
        ListMap<StringConstant, TypeConstant> map = m_mapParams;
        if (map == null)
            {
            m_mapParams = map = new ListMap<>();
            }

        ConstantPool pool = getConstantPool();

        // check for turtles, for example: "ElementTypes extends Tuple<ElementTypes>"
        if (typeConstraint.getParamsCount() >= 1 &&
                typeConstraint.isTuple() &&
                typeConstraint.getParamType(0).getValueString().equals(sName))
            {
            typeConstraint = pool.ensureTypeSequenceTypeConstant();
            }
        map.put(pool.ensureStringConstant(sName), typeConstraint);

        // each type parameter also has a synthetic property of the same name,
        // whose type is of type "Type<constraint-type>"
        TypeConstant typeConstraintType = pool.ensureClassTypeConstant(
            pool.clzType(), null, typeConstraint);

        // create the property and mark it as a type parameter
        PropertyStructure prop = createProperty(false, Access.PUBLIC, Access.PUBLIC, typeConstraintType, sName);
        prop.markAsGenericTypeParameter();
        markModified();

        // invalidate cached types
        m_typeCanonical = null;
        m_typeFormal    = null;
        return prop;
        }

    /**
     * Obtain the constraint type for the specified generic type.
     *
     * @param sName  the generic type name
     *
     * @return the constraint type
     */
    public TypeConstant getConstraint(String sName)
        {
        return m_mapParams.get(getConstantPool().ensureStringConstant(sName));
        }

    /**
     * Update (narrow) the constraint type for the specified generic type.
     *
     * @param sName           the generic type name
     * @param typeConstraint  the new constraint type
     */
    public void updateConstraint(String sName, TypeConstant typeConstraint)
        {
        ListMap<StringConstant, TypeConstant> map = m_mapParams;
        assert map != null;

        ConstantPool pool = getConstantPool();

        TypeConstant typeConstraintType = pool.ensureClassTypeConstant(
            pool.clzType(), null, typeConstraint);

        map.put(pool.ensureStringConstant(sName), typeConstraint);

        PropertyStructure prop = (PropertyStructure) getChild(sName);
        prop.setType(typeConstraintType);

        markModified();
        }

    /**
     * @return true if this class is parameterized (generic)
     */
    public boolean isParameterized()
        {
        return m_mapParams != null;
        }

    /**
     * @return true iff if this class is parameterized or has a parameterized virtual parent
     */
    public boolean isParameterizedDeep()
        {
        return isParameterized() ||
               isVirtualChild() && getOuter().isParameterized();
        }

    /**
     * @return the formal type (e.g. Map<Key, Value>)
     */
    public TypeConstant getFormalType()
        {
        TypeConstant typeFormal = m_typeFormal;
        if (typeFormal == null)
            {
            ConstantPool     pool        = getConstantPool();
            IdentityConstant constantClz = getIdentityConstant();

            if (isAnonInnerClass())
                {
                typeFormal = constantClz.getType();

                // for anonymous inner class the constraints are the formal types
                if (isParameterized())
                    {
                    typeFormal = pool.ensureParameterizedTypeConstant(typeFormal,
                        m_mapParams.values().toArray(TypeConstant.NO_TYPES));
                    }
                }
            else
                {
                if (isVirtualChild())
                    {
                    TypeConstant typeParent = ((ClassStructure) getParent()).getFormalType();
                    typeFormal = pool.ensureVirtualChildTypeConstant(typeParent, getName());
                    }
                else if (isInnerChild())
                    {
                    TypeConstant typeParent = getOuter().getFormalType();
                    typeFormal = pool.ensureInnerChildTypeConstant(typeParent,
                                        (ClassConstant) getIdentityConstant());
                    }
                else
                    {
                    typeFormal = constantClz.getType();
                    }

                if (isParameterized())
                    {
                    Map<StringConstant, TypeConstant> mapThis = m_mapParams;
                    TypeConstant[] aTypes = new TypeConstant[mapThis.size()];
                    int ix = 0;
                    for (StringConstant constName : mapThis.keySet())
                        {
                        PropertyStructure prop = (PropertyStructure) getChild(constName.getValue());
                        aTypes[ix++] = prop.getIdentityConstant().getFormalType();
                        }

                    typeFormal = pool.ensureParameterizedTypeConstant(typeFormal, aTypes);
                    }
                }

            m_typeFormal = typeFormal;
            }
        return typeFormal;
        }

    /**
     * This method is similar to the one above, except that it creates a "this"
     * {@link VirtualChildTypeConstant} for virtual child components.
     *
     * @return the formal type
     */
    public TypeConstant getAutoNarrowingFormalType()
        {
        if (isVirtualChild())
            {
            ClassStructure clzParent = (ClassStructure) getParent();
            return getConstantPool().ensureThisVirtualChildTypeConstant(
                    clzParent.getAutoNarrowingFormalType(), getName());
            }
        return getFormalType();
        }

    /**
     * @return the canonical type (e.g. Map<Object, Object>)
     */
    public TypeConstant getCanonicalType()
        {
        TypeConstant typeCanonical = m_typeCanonical;
        if (typeCanonical == null)
            {
            ConstantPool     pool     = getConstantPool();
            IdentityConstant constClz = getIdentityConstant();

            if (constClz.equals(pool.clzTuple()))
                {
                // canonical Tuple
                return m_typeCanonical = pool.typeTuple0();
                }

            if (isVirtualChild())
                {
                TypeConstant typeParent = ((ClassStructure) getParent()).getCanonicalType();
                typeCanonical = pool.ensureVirtualChildTypeConstant(typeParent, getName());
                }
            else if (isInnerChild())
                {
                TypeConstant typeParent = getOuter().getCanonicalType();
                typeCanonical = pool.ensureInnerChildTypeConstant(typeParent, (ClassConstant) constClz);
                }
            else
                {
                typeCanonical = constClz.getType();
                }

            if (isParameterized())
                {
                Map<StringConstant, TypeConstant> mapParams = getTypeParams();
                TypeConstant[] atypeParam = new TypeConstant[mapParams.size()];
                int ix = 0;
                GenericTypeResolver resolver = new SimpleTypeResolver(pool, new ArrayList<>());
                for (TypeConstant typeParam : mapParams.values())
                    {
                    atypeParam[ix++] = typeParam.isFormalTypeSequence()
                            ? pool.typeTuple0()
                            : typeParam.resolveGenerics(pool, resolver);
                    }
                typeCanonical = pool.ensureParameterizedTypeConstant(typeCanonical, atypeParam);
                }

            m_typeCanonical = typeCanonical;
            }
        return typeCanonical;
        }

    /**
     * Resolve the formal type for this class based on the specified list of actual types.
     *
     * Note: the specified list is allowed to skip some number of actual parameters (at the tail);
     *       they will be replaced by the corresponding resolved canonical types
     *
     * @param pool        the ConstantPool to place a potentially created new constant into
     * @param listActual  the list of actual types
     *
     * @return the resolved type
     */
    public TypeConstant resolveType(ConstantPool pool, List<TypeConstant> listActual)
        {
        return listActual.isEmpty() && !isParameterized()
            ? getCanonicalType()
            : getFormalType().resolveGenerics(pool, new SimpleTypeResolver(pool, listActual));
        }

    /**
     * If the specified list of actual parameters is missing some number of actual parameters,
     * add the corresponding resolved canonical types to the end of the list.
     *
     * @param pool        the ConstantPool to place a potentially created new constant into
     * @param listActual  the list of actual types
     *
     * @return a list of types that has exact size as the map of formal parameters for this class
     */
    public List<TypeConstant> normalizeParameters(ConstantPool pool, List<TypeConstant> listActual)
        {
        int cActual = listActual.size();
        int cFormal = getTypeParamCount();

        return cActual == cFormal
            ? listActual
            : resolveType(pool, listActual).getParamTypes();
        }

    /**
     * If the specified array of actual parameters is missing some number of actual parameters,
     * add the corresponding resolved canonical types to the end of the list.
     *
     * @param pool         the ConstantPool to place a potentially created new constant into
     * @param atypeActual  the array of actual types
     *
     * @return an array of types that has exact size as the map of formal parameters for this class
     */
    public TypeConstant[] normalizeParameters(ConstantPool pool, TypeConstant[] atypeActual)
        {
        int cActual = atypeActual.length;
        int cFormal = getTypeParamCount();

        return cActual == cFormal
            ? atypeActual
            : resolveType(pool, Arrays.asList(atypeActual)).getParamTypesArray();
        }

    /**
     * Given this structure being a module or a package, build a list of all module dependencies,
     * and the shortest path to each.
     *
     * @param sModulePath     pass "" for the primary module
     * @param mapModulePaths  pass a map containing all previously encountered modules (including
     *                        the current one)
     */
    protected void collectDependencies(String sModulePath, Map<ModuleConstant, String> mapModulePaths)
        {
        for (Component child : children())
            {
            if (child instanceof PackageStructure pkg)
                {
                if (pkg.isModuleImport())
                    {
                    ModuleStructure moduleDep  = pkg.getImportedModule();
                    ModuleConstant  idDep      = moduleDep.getIdentityConstant();
                    String          sOldPath   = mapModulePaths.get(idDep);
                    String          sLocalPath = pkg.getIdentityConstant().getPathString();
                    String          sNewPath   = sModulePath.length() == 0
                                               ? sLocalPath
                                               : sModulePath + '.' + sLocalPath;
                    if (sOldPath == null)
                        {
                        mapModulePaths.put(idDep, sNewPath);
                        moduleDep.collectDependencies(sNewPath, mapModulePaths);
                        }
                    else if (sNewPath.length() < sOldPath.length())
                        {
                        mapModulePaths.put(idDep, sNewPath);

                        // replace everything else using the new path that was already registered
                        // as being reached via the old path
                        mapModulePaths.entrySet().stream()
                                .filter(e -> e.getValue().startsWith(sOldPath + '.'))
                                .forEach(e -> e.setValue(sNewPath + e.getValue().substring(sOldPath.length())));
                        }
                    }
                else
                    {
                    pkg.collectDependencies(sModulePath, mapModulePaths);
                    }
                }
            }
        }


    // ----- Tuple support -------------------------------------------------------------------------

    /**
     * @return true iff this class is a Tuple or a Tuple mixin
     */
    public boolean isTuple()
        {
        if (getIdentityConstant().equals(getConstantPool().clzTuple()))
            {
            return true;
            }

        for (Contribution contrib : getContributionsAsList())
            {
            if (contrib.getComposition() == Composition.Into)
                {
                if (contrib.getTypeConstant().isTuple())
                    {
                    return true;
                    }
                }
            }
        return false;
        }

    /**
     * When this class represents a Tuple or a Tuple mixin, get the resulting Tuple type parameters
     *
     * @param pool        the ConstantPool to use
     * @param listParams  the list of actual generic parameters for this class
     *
     * @return the list of types
     */
    public List<TypeConstant> getTupleParamTypes(ConstantPool pool, List<TypeConstant> listParams)
        {
        if (getIdentityConstant().equals(pool.clzTuple()))
            {
            return listParams;
            }

        for (Contribution contrib : getContributionsAsList())
            {
            if (contrib.getComposition() == Composition.Into)
                {
                TypeConstant typeContrib = contrib.resolveGenerics(pool,
                                                new SimpleTypeResolver(pool, listParams));
                if (typeContrib != null && typeContrib.isTuple())
                    {
                    return typeContrib.getTupleParamTypes();
                    }
                }
            }
        return Collections.EMPTY_LIST;
        }


    // ----- component methods ---------------------------------------------------------------------

    @Override
    public boolean isClassContainer()
        {
        return true;
        }

    @Override
    public boolean isMethodContainer()
        {
        return true;
        }

    @Override
    public ConcurrencySafety getConcurrencySafety()
        {
        if (m_safety != null)
            {
            return m_safety;
            }

        ConcurrencySafety safety;
        switch (getFormat())
            {
            case MODULE:
            case PACKAGE:
            case CONST:
            case ENUM:
            case ENUMVALUE:
                safety = ConcurrencySafety.Safe;
                break;

            case CLASS:
            case INTERFACE:
            case MIXIN:
            case SERVICE:
                ConstantPool pool = getConstantPool();
                if (containsAnnotation(pool.clzSynchronized()))
                    {
                    safety = ConcurrencySafety.Unsafe;
                    }
                else if (containsAnnotation(pool.clzConcurrent()))
                    {
                    safety = ConcurrencySafety.Safe;
                    }
                else
                    {
                    safety = ConcurrencySafety.Instance;
                    }
                break;

            default:
                throw new IllegalStateException();
            }
        return m_safety = safety;
        }

    @Override
    public boolean isAutoNarrowingAllowed()
        {
        if (!getFormat().isAutoNarrowingAllowed())
            {
            return false;
            }

        // a class must not be a singleton
        if (isSingleton())
            {
            return false;
            }

        // inner classes inside a method (including anonymous inner classes) do not auto-narrow
        // because they are effectively final
        Component parent = getParent();
        if (parent instanceof MethodStructure)
            {
            return false;
            }

        // otherwise, assuming that this not the "outermost" class, keep asking up the parent chain
        return ((ClassConstant) getIdentityConstant()).getDepthFromOutermost() == 0
                || parent.isAutoNarrowingAllowed();
        }

    @Override
    public ResolutionResult resolveName(String sName, Access access, ResolutionCollector collector)
        {
        ResolutionResult result = super.resolveName(sName, access, collector);
        if (result == ResolutionResult.UNKNOWN && getFormat() == Format.SERVICE)
            {
            // look into the Service interface itself
            ClassStructure   clzSvc       = (ClassStructure) getConstantPool().clzService().getComponent();
            SimpleCollector  collectorSvc = new SimpleCollector(ErrorListener.BLACKHOLE);
            ResolutionResult resultSvc    = clzSvc.resolveName(sName, Access.PROTECTED, collectorSvc);
            if (resultSvc == ResolutionResult.RESOLVED)
                {
                // only allow child classes; properties and methods are resolved by the TypeInfo
                Constant constant = collectorSvc.getResolvedConstant();
                if (constant.getFormat() == Constant.Format.Class)
                    {
                    collector.resolvedConstant(constant);
                    return ResolutionResult.RESOLVED;
                    }
                }
            }
        return result;
        }

    @Override
    protected ClassStructure cloneBody()
        {
        ClassStructure that = (ClassStructure) super.cloneBody();

        // deep-clone the parameter list information (since the structure is mutable)
        if (this.m_mapParams != null)
            {
            ListMap<StringConstant, TypeConstant> mapThis = this.m_mapParams;
            ListMap<StringConstant, TypeConstant> mapThat = new ListMap<>();
            mapThat.putAll(mapThis);
            that.m_mapParams = mapThat;
            }

        return that;
        }

    /**
     * Check if this class extends the specified class.
     *
     * @param idClass  the class to test if this class represents an extension of
     *
     * @return true if this type represents a sub-classing of the specified class
     */
    public boolean extendsClass(IdentityConstant idClass)
        {
        if (getFormat() == Format.INTERFACE)
            {
            // interfaces do not extend; they implement
            return false;
            }

        if (idClass.equals(getIdentityConstant()))
            {
            // while a class cannot technically extend itself, this does satisfy the "is-a" test
            return true;
            }

        ClassStructure structCur = this;
        NextSuper: while (true)
            {
            for (Contribution contrib : structCur.getContributionsAsList())
                {
                if (contrib.getComposition() == Composition.Extends)
                    {
                    // even though this class may be id'd by a ModuleConstant or PackageConstant,
                    // the super will always be a class (because a Module and a Package cannot be
                    // extended)
                    ClassConstant constSuper = (ClassConstant)
                        contrib.getTypeConstant().getSingleUnderlyingClass(false);
                    if (idClass.equals(constSuper))
                        {
                        return true;
                        }

                    structCur = (ClassStructure) constSuper.getComponent();
                    continue NextSuper;
                    }
                }

            return false;
            }
        }

    /**
     * Check if the specified annotation mixin type is "into Class", meaning that the mixin applies
     * to the meta-data of the class and is not actually mixed into the class functionality itself.
     *
     * A slight complication comes from a scenario when the mixin applies to a union of
     * types, for example:
     * <code>
     *   <pre>
     *     mixin Override
     *         into Class | Property | Method
     *   </pre>
     * </code>
     * In such a case it may be a "real" mixin, if it applies to the class itself.
     *
     * @param typeMixin  the annotation mixin type
     *
     * @return true iff the annotation mixin applies to the class meta-data
     */
    public boolean isIntoClassMixin(TypeConstant typeMixin)
        {
        assert typeMixin.isExplicitClassIdentity(true);

        if (typeMixin.getExplicitClassFormat() != Format.MIXIN)
            {
            return false;
            }

        TypeConstant typeInto = typeMixin.getExplicitClassInto();

        return typeInto.isIntoClassType() &&
               !typeInto.isComposedOfAny(Collections.singleton(getIdentityConstant()));
        }

    /**
     * @return true iff any contribution for this class has a cyclical dependency
     */
    public Contribution hasCyclicalContribution()
        {
        return findContributionImpl(getIdentityConstant(), false);
        }

    /**
     * Check if this class has the specified class as any of its contributions (recursively).
     *
     * @param idClass  the class to test if this class has a contribution of
     *
     * @return true if this type has a contribution of the specified class
     */
    public boolean hasContribution(IdentityConstant idClass)
        {
        if (idClass.equals(getConstantPool().clzObject()))
            {
            // everything is considered to implement the Object interface
            return true;
            }

        if (idClass.equals(getIdentityConstant()))
            {
            return true;
            }

        return findContributionImpl(idClass, true) != null;
        }

    /**
     * Find a contribution of a specified identity.
     *
     * @param idContrib   the contribution identity
     *
     * @return a first (if more than one) contribution matching the specified identity
     *         or null if none found
     */
    public Contribution findContribution(IdentityConstant idContrib)
        {
        if (idContrib.equals(getIdentityConstant()))
            {
            return new Contribution(Composition.Equal, getFormalType());
            }

        return findContributionImpl(idContrib, true);
        }

    /**
     * Implementation of the contribution lookup.
     *
     * @param idContrib   the contribution identity (must mot be this class's id)
     * @param fAllowInto  if false, ignore the Into contributions
     *
     * @return a first (if more than one) contribution matching the specified identity
     *         or null if none found
     */
    private Contribution findContributionImpl(IdentityConstant idContrib, boolean fAllowInto)
        {
        for (Contribution contrib : getContributionsAsList())
            {
            TypeConstant typeContrib  = contrib.getTypeConstant();
            Contribution contribMatch = null;

            if (typeContrib.isExplicitClassIdentity(true))
                {
                boolean fCheck;
                switch (contrib.getComposition())
                    {
                    case Annotation:
                        {
                        fCheck     = !isIntoClassMixin(typeContrib);
                        fAllowInto = false;
                        break;
                        }
                    case Into:
                        fCheck     = fAllowInto;
                        fAllowInto = false;
                        break;

                    case Incorporates:
                        fAllowInto = false;
                        // break through
                    case Delegates, Implements, Extends:
                        fCheck = true;
                        break;

                    default:
                        // ignore any other contributions
                        fCheck = false;
                        break;
                    }

                if (fCheck)
                    {
                    contribMatch = checkContribution(contrib, typeContrib, idContrib);
                    }
                }
            else if (typeContrib instanceof IntersectionTypeConstant typeIntersection)
                {
                // the only relational type contributions we can process further are the
                // intersection types
                contribMatch = checkIntersectionContribution(contrib, typeIntersection, idContrib);
                }

            if (contribMatch != null)
                {
                return contribMatch;
                }
            }
        return null;
        }

    /**
     * Check whether the specified contribution or any of its descendants matches the specified
     * identity.
     *
     * @param contrib      the contribution
     * @param typeContrib  the type of the contribution or one of its composing types (in the case
     *                     of an intersection type)
     * @param idTest       the identity to test with
     *
     * @return the contribution that matches the specified identity
     */
    private Contribution checkContribution(Contribution contrib,
                                           TypeConstant typeContrib, IdentityConstant idTest)
        {
        IdentityConstant idContrib = typeContrib.getSingleUnderlyingClass(true);
        if (idContrib.equals(idTest))
            {
            return contrib;
            }

        ClassStructure clzContrib = (ClassStructure) idContrib.getComponent();
        return clzContrib.findContributionImpl(idTest, false);
        }

    /**
     * Check whether specified contribution of the intersection type or any of its descendants
     * matches the specified identity.
     *
     * @param contrib      the contribution
     * @param typeContrib  the type of the contribution
     * @param idTest       the identity to test with
     *
     * @return the contribution that matches the specified identity
     */
    private Contribution checkIntersectionContribution(Contribution             contrib,
                                                       IntersectionTypeConstant typeContrib,
                                                       IdentityConstant         idTest)
        {
        TypeConstant type1    = typeContrib.getUnderlyingType();
        Contribution contrib1 = type1.isExplicitClassIdentity(true)
                ? checkContribution(contrib, type1, idTest)
                : type1 instanceof IntersectionTypeConstant typeIntersection1
                        ? checkIntersectionContribution(contrib, typeIntersection1, idTest)
                        : null;
        if (contrib1 != null)
            {
            return contrib1;
            }

        TypeConstant type2 = typeContrib.getUnderlyingType2();
        return type2.isExplicitClassIdentity(true)
                ? checkContribution(contrib, type2, idTest)
                : type2 instanceof IntersectionTypeConstant typeIntersection2
                        ? checkIntersectionContribution(contrib, typeIntersection2, idTest)
                        : null;
        }

    /**
     * @return true iff this class is an Exception
     */
    public boolean isException()
        {
        return extendsClass(getConstantPool().clzException());
        }

    /**
     * Determine the type to rebase onto. Note that a rebase type is NEVER parameterized.
     *
     * @return a type to rebase onto, if rebasing is required by this class; otherwise null
     */
    public TypeConstant getRebaseType()
        {
        ConstantPool pool   = getConstantPool();
        Format       format = getFormat();

        switch (format)
            {
            case MODULE:
                return pool.typeModuleRB();

            case PACKAGE:
                return pool.typePackageRB();

            case ENUM:
                return pool.typeEnumRB();

            case CONST:
            case SERVICE:
                // only if the format differs from the format of the super
                ClassStructure clzSuper = getSuper();
                if (clzSuper == null || format != clzSuper.getFormat())
                    {
                    return format == Format.CONST ? pool.typeConstRB() : pool.typeServiceRB();
                    }
                // break through
            default:
                return null;
            }
        }

    /**
     * @return the ClassStructure that this ClassStructure extends, or null if this ClassStructure
     *         is an interface or does not contain an Extends contribution
     */
    public ClassStructure getSuper()
        {
        Contribution contribExtends = findContribution(Composition.Extends);
        if (contribExtends != null)
            {
            TypeConstant typeExtends = contribExtends.getTypeConstant();
            if (typeExtends.isExplicitClassIdentity(true) &&
                typeExtends.isSingleUnderlyingClass(false))
                {
                return (ClassStructure) typeExtends.getSingleUnderlyingClass(false).getComponent();
                }
            }
        return null;
        }

    /**
     * For a "virtual child" ClassStructure component that is still being resolved during the
     * compilation process, determine the set of "virtual child super" classes and interfaces for
     * this virtual child.
     *
     * @param setContribs  the set to collect implicit contributions (extends/implements) in
     *
     * @return false iff some unresolved structure was encountered that may have prevented the
     *         determination of a virtual child super; the return valued does NOT imply the presence
     *         or the absence of a virtual child super
     */
    public boolean resolveVirtualSuper(Set<Contribution> setContribs)
        {
        assert isVirtualChild();

        // prevent circularity and repeatedly checking the same components
        Set<IdentityConstant> setVisited = new HashSet<>();
        IdentityConstant idThis = getIdentityConstant();
        setVisited.add(idThis);

        Component parent = getParent();
        int       cDepth = 1;
        while (true)
            {
            // avoid circular / repetitive checks
            if (setVisited.add(parent.getIdentityConstant()))
                {
                Iterator<IdentityConstant> iter = parent.potentialVirtualChildContributors();
                if (iter == null)
                    {
                    return false;
                    }
                while (iter.hasNext())
                    {
                    IdentityConstant idContrib = iter.next();
                    if (idContrib.containsUnresolved())
                        {
                        return false;
                        }

                    Component component = idContrib.getComponent();
                    if (component != null)
                        {
                        Object o = component.findVirtualChildSuper(idThis, cDepth, setVisited);
                        if (o != null)
                            {
                            if (o instanceof Boolean Flag)
                                {
                                // something necessary hasn't resolved yet
                                assert !Flag;
                                return false;
                                }

                            // we found a virtual child super, which may be a super interface
                            // (implying "implements") or class (implying "extends"); the identity
                            // of the super is created relative to the contributed identity, instead
                            // of just using the identity of the class that we found, because the
                            // actual super type may be a phantom (it might not have a structure)
                            IdentityConstant idSuper   = idThis.appendTrailingPathTo(idContrib, cDepth);
                            Component        compSuper = idSuper.getComponent();
                            ClassStructure   clzSuper;
                            TypeConstant     typeSuper;
                            if (compSuper == null)
                                {
                                ConstantPool pool = getConstantPool();
                                clzSuper  = (ClassStructure) o;
                                assert clzSuper.isVirtualChild();
                                typeSuper = pool.ensureVirtualChildTypeConstant(
                                        ((ClassStructure) component).getFormalType(), idThis.getName());
                                if (clzSuper.isParameterized())
                                    {
                                    typeSuper = typeSuper.adoptParameters(pool, clzSuper.getFormalType());
                                    }
                                }
                            else
                                {
                                clzSuper  = (ClassStructure) compSuper;
                                typeSuper = clzSuper.getFormalType();
                                }
                            Composition composition = clzSuper.getFormat() == Format.INTERFACE
                                    ? Composition.Implements : Composition.Extends;
                            setContribs.add(new Contribution(composition, typeSuper));
                            }
                        }
                    }
                }

            // we are finished walking up the parent chain AFTER we have processed the first class
            // in that chain that is NOT a virtual child class
            if (parent instanceof ClassStructure clz && !clz.isVirtualChild())
                {
                break;
                }

            parent = parent.getParent();
            ++cDepth;
            }

        return true;
        }

    @Override
    protected Object findVirtualChildSuper(
            IdentityConstant        idVirtChild,
            int                     cDepth,
            Set<IdentityConstant>   setVisited)
        {
        Object oResult = super.findVirtualChildSuper(idVirtChild, cDepth, setVisited);

        if (oResult == null && isVirtualChild())
            {
            // classes also walk up the parent chain (as long as the chain corresponds to a chain of
            // virtual children and the parent containing them) to find contributions that can lead
            // to the virtual child super
            oResult = getParent().findVirtualChildSuper(idVirtChild, cDepth+1, setVisited);
            }

        return oResult;
        }

    @Override
    protected Iterator<IdentityConstant> potentialVirtualChildContributors()
        {
        List<IdentityConstant> list = null;

        for (Contribution contrib : getContributionsAsList())
            {
            TypeConstant type = contrib.getTypeConstant();
            switch (contrib.getComposition())
                {
                case Annotation:
                    if (isIntoClassMixin(type))
                        {
                        break;
                        }
                    // break through
                case Extends:
                case Incorporates:
                case Implements:
                    if (type.containsUnresolved())
                        {
                        return null;
                        }
                    if (type.isExplicitClassIdentity(true))
                        {
                        if (list == null)
                            {
                            list = new ArrayList<>();
                            }

                        list.add(type.getSingleUnderlyingClass(true));
                        }
                    break;
                }
            }

        return list == null
                ? Collections.emptyIterator()
                : list.iterator();
        }

    /**
     * Determine the "into" type of this mixin.
     *
     * @return a TypeConstant of the "into" contribution
     */
    public TypeConstant getTypeInto()
        {
        if (getFormat() != Format.MIXIN)
            {
            throw new IllegalStateException("not a mixin: " + getIdentityConstant());
            }

        Contribution contribInto = findContribution(Composition.Into);
        if (contribInto != null)
            {
            return contribInto.getTypeConstant();
            }

        ClassStructure structSuper = getSuper();
        if (structSuper != null)
            {
            return structSuper.getTypeInto();
            }

        return getConstantPool().typeObject();
        }

    /**
     * Find an index of a generic parameter with the specified name.
     *
     * Note: this method only looks for parameters declared by this class.
     *
     * @param sParamName  the parameter name
     *
     * @return the parameter index or -1 if not found
     */
    public int indexOfGenericParameter(String sParamName)
        {
        List<Map.Entry<StringConstant, TypeConstant>> listFormal = getTypeParamsAsList();
        for (int i = 0, c = listFormal.size(); i < c; i++)
            {
            if (listFormal.get(i).getKey().getValue().equals(sParamName))
                {
                return i;
                }
            }
        return -1;
        }

    /**
     * Recursively check if the formal name is introduced by this class or any of its contributions.
     * <p/>
     * Note: while this seems to be a duplication of what TypoInfo does, we need to keep this
     * functionality since the TypeInfo generation itself uses it.
     *
     * @return true if the formal type with this name exists
     */
    public boolean containsGenericParamType(String sName)
        {
        return containsGenericParamTypeImpl(sName, true);
        }

    /**
     * Recursive implementation of containsGenericParamType method.
     *
     * @param sName       teh formal type name
     * @param fAllowInto  specifies whether the "Into" contribution is to be skipped
     *
     * @return the corresponding actual type or null if there is no matching formal type
     */
    protected boolean containsGenericParamTypeImpl(String sName, boolean fAllowInto)
        {
        int ix = indexOfGenericParameter(sName);
        if (ix >= 0)
            {
            return true;
            }

        for (Contribution contrib : getContributionsAsList())
            {
            TypeConstant typeContrib = contrib.getTypeConstant();

            if (typeContrib.containsUnresolved() || !typeContrib.isSingleUnderlyingClass(true))
                {
                // TODO: how do we process relational types?
                continue;
                }

            boolean fCheck;
            switch (contrib.getComposition())
                {
                case Annotation:
                    fCheck     = !isIntoClassMixin(typeContrib);
                    fAllowInto = false;
                    break;

                case Into:
                    fCheck     = fAllowInto;
                    fAllowInto = false;
                    break;

                case Incorporates:
                    fAllowInto = false;
                    // break through
                case Delegates, Implements, Extends:
                    fCheck = true;
                    break;

                case Import:
                    continue;

                default:
                    throw new IllegalStateException();
                }

            if (fCheck)
                {
                ClassStructure clzContrib = (ClassStructure)
                        typeContrib.getSingleUnderlyingClass(true).getComponent();
                if (clzContrib.containsGenericParamTypeImpl(sName, fAllowInto))
                    {
                    return true;
                    }
                }
            }

        return false;
        }

    /**
     * Recursively find the type for the specified formal name. Note that the formal name could
     * be introduced by some contributions, rather than this class itself.
     * <p/>
     * Note: while this seems to be a duplication of what TypoInfo does, we need to keep this
     * functionality since the TypeInfo generation itself uses it.
     *
     * @return the corresponding actual type or null if there is no matching formal type
     */
    public TypeConstant getGenericParamType(ConstantPool pool, String sName, TypeConstant typeActual)
        {
        return getGenericParamTypeImpl(pool, sName, typeActual, true);
        }

    /**
     * Recursive implementation of getGenericParamType method.
     *
     * @param pool        the ConstantPool to place a potentially created new constant into
     * @param fAllowInto  specifies whether the "Into" contribution is to be skipped
     *
     * @return the corresponding actual type or null if there is no matching formal type
     */
    protected TypeConstant getGenericParamTypeImpl(ConstantPool pool, String sName,
                                                   TypeConstant typeActual, boolean fAllowInto)
        {
        int ix = indexOfGenericParameter(sName);
        if (ix >= 0)
            {
            // the formal name is declared at this level; don't traverse the contributions
            return extractGenericType(pool, ix, typeActual.getParamTypes());
            }

        for (Contribution contrib : getContributionsAsList())
            {
            TypeConstant typeContrib = contrib.getTypeConstant();

            if (typeContrib.containsUnresolved() || !typeContrib.isSingleUnderlyingClass(true))
                {
                // TODO: how do we process relational types?
                continue;
                }

            TypeConstant typeResolved = contrib.resolveType(pool, this, typeActual);
            if (typeResolved == null)
                {
                // conditional incorporation
                continue;
                }

            boolean fCheck;
            switch (contrib.getComposition())
                {
                case Annotation:
                    fCheck     = !isIntoClassMixin(typeContrib);
                    fAllowInto = false;
                    break;

                case Into:
                    fCheck     = fAllowInto;
                    fAllowInto = false;
                    break;

                case Incorporates:
                    fAllowInto = false;
                    // break through
                case Delegates, Implements, Extends:
                    fCheck = true;
                    break;

                case Import:
                    // ignore
                    continue;

                default:
                    throw new IllegalStateException();
                }

            if (fCheck)
                {
                ClassStructure clzContrib = (ClassStructure)
                        typeContrib.getSingleUnderlyingClass(true).getComponent();

                TypeConstant type = clzContrib.getGenericParamTypeImpl(
                        pool, sName, typeResolved, fAllowInto);
                if (type != null)
                    {
                    return type;
                    }

                if (clzContrib.isVirtualChild() && typeResolved.isVirtualChild())
                    {
                    type = clzContrib.getOuter().getGenericParamTypeImpl(pool, sName,
                            typeResolved.getParentType(), fAllowInto);
                    if (type != null)
                        {
                        return type;
                        }
                    }
                }
            }

        return null;
        }

    /**
     * Extract a generic type for the formal parameter of the specified name from the specified list.
     *
     * @param pool   the ConstantPool to use
     * @param sName  the formal name
     * @param list   the actual type list
     *
     * @return the type corresponding to the specified formal type or null if cannot be determined
     */
    private TypeConstant extractGenericType(ConstantPool pool, String sName, List<TypeConstant> list)
        {
        int ix = indexOfGenericParameter(sName);

        return ix >= 0
                ? extractGenericType(pool, ix, list)
                : null;
        }

    /**
     * Extract a generic type for the formal parameter at the specified index from the specified list.
     *
     * @param pool   the ConstantPool to use
     * @param ix     the index
     * @param list   the actual type list
     *
     * @return the type corresponding to the specified formal type or null if cannot be determined
     */
    private TypeConstant extractGenericType(ConstantPool pool, int ix, List<TypeConstant> list)
        {
        if (isTuple())
            {
            return pool.ensureTupleType(list.toArray(TypeConstant.NO_TYPES));
            }

        return ix < list.size() ? list.get(ix) : null;
        }

    @Override
    public void addAnnotation(Annotation annotation)
        {
        super.addAnnotation(annotation);

        // clear the cache
        m_aAnnoMixin = null;
        m_aAnnoClass = null;
        }

    // ----- type comparison support ---------------------------------------------------------------

    /**
     * Calculate assignability between two parameterized types of this class.
     *
     *  C<L1, L2, ...> lvalue = (C<R1, R2, ...>) rvalue;
     */
    public Relation calculateAssignability(ConstantPool pool,
                                           List<TypeConstant> listLeft, Access accessLeft,
                                           List<TypeConstant> listRight)
        {
        int     cParamsLeft  = listLeft.size();
        int     cParamsRight = listRight.size();
        int     cParamsMax   = Math.max(cParamsRight, cParamsLeft);
        boolean fTuple       = isTuple();
        boolean fWeak        = false;

        List<Map.Entry<StringConstant, TypeConstant>> listFormal          = getTypeParamsAsList();
        List<TypeConstant>                            listRightNormalized = cParamsRight < cParamsLeft
                ? normalizeParameters(pool, listRight)
                : listRight;

        if (!fTuple && cParamsMax > listFormal.size())
            {
            // soft assert
            System.err.println("Invalid number of arguments for " + getName()
                    + ": required=" + listFormal.size()
                    + ", provided " + cParamsMax);
            return Relation.INCOMPATIBLE;
            }

        Iterator<Map.Entry<StringConstant, TypeConstant>> iterFormal = listFormal.iterator();
        for (int i = 0; i < cParamsMax; i++)
            {
            String sName = fTuple ? null : iterFormal.next().getKey().getValue();

            if (i >= cParamsLeft)
                {
                // if an assignment C<L1> = C<R1> is allowed, then an assignment
                // C<L1> = C<R1, R2> is allowed for any R2, but is "weak" for consumers
                if (fTuple || consumesFormalType(pool, sName, accessLeft, listLeft))
                    {
                    fWeak = true;
                    }
                break;
                }

            TypeConstant typeLeft = listLeft.get(i);
            TypeConstant typeRight;
            boolean      fProduces;
            boolean      fLeftIsRight;

            if (i < cParamsRight)
                {
                typeRight = listRight.get(i);

                if (typeLeft.equals(typeRight))
                    {
                    continue;
                    }

                fProduces    = fTuple || producesFormalType(pool, sName, accessLeft, listLeft);
                fLeftIsRight = typeLeft.isA(typeRight);

                if (fLeftIsRight && !fProduces)
                    {
                    // consumer only methods; rule 1.2.1
                    continue;
                    }
                }
            else
                {
                // since dynamic types are added automatically, we can treat them as they are
                // "not there" and therefore non-conflicting with the right type
                if (typeLeft.isDynamicType())
                    {
                    continue;
                    }

                // Assignment  C<L1, L2> = C<R1> is not the same as
                //             C<L1, L2> = C<R1, [normalized (resolved canonical) type for R2]>;
                // the former is only allowed if class C produces L2
                // and then all L2 consuming methods (if any) must be "wrapped".
                //
                // For example, this assignment should be "weakly" allowed
                //    List<Object> <-- List
                // However, the following is not allowed:
                //    Logger<Object> <-- Logger
                typeRight    = fTuple ? pool.typeObject() : listRightNormalized.get(i);
                fProduces    = fTuple || producesFormalType(pool, sName, accessLeft, listLeft);
                fLeftIsRight = false;
                }

            if (typeRight.isA(typeLeft))
                {
                if (fLeftIsRight)
                    {
                    // both hold true:
                    //   typeLeft.isA(typeRight), and
                    //   typeRight.isA(typeLeft)
                    // we take it that the types are congruent
                    // (e,g. "this:class", but with different declaration levels)
                    continue;
                    }

                boolean fConsumes = fTuple || consumesFormalType(pool, sName, accessLeft, listLeft);
                if (fProduces || !fConsumes)
                    {
                    // there are some producing methods; rule 1.2.2.2
                    // or the formal type is completely unused
                    // consuming methods may need to be "wrapped"
                    if (fConsumes)
                        {
                        fWeak = true;
                        }
                    continue;
                    }

                if (typeLeft.isFormalType())
                    {
                    // the left type is formal (e.g. "HashSet.Element") and right "isA" left;
                    // therefore, the right must be some narrowing of that formal type, for example:
                    // (Element + Hashable), which warrants a "weak" assignment, such as:
                    // Consumer<Element> <- Consumer<Element + Hashable>
                    fWeak = true;
                    continue;
                    }
                }

            // this parameter didn't match
            return Relation.INCOMPATIBLE;
            }
        return fWeak ? Relation.IS_A_WEAK : Relation.IS_A;
        }

    /**
     * Helper method to find a method by the name and number of arguments.
     *
     * @param sName  the method name to find
     * @param cArgs  the number of arguments
     * @param aType  (optional or partial) an array of parameter types to match
     *
     * @return the specified MethodStructure of null if not found
     */
    public MethodStructure findMethod(String sName, int cArgs, TypeConstant... aType)
        {
        return findMethod(sName, method ->
            {
            int cParamsAll     = method.getParamCount();
            int cParamsDefault = method.getDefaultParamCount();
            if (cArgs < cParamsAll - cParamsDefault || cArgs > cParamsAll)
                {
                return false;
                }

            if (aType == null)
                {
                return true;
                }

            for (int i = 0, c = Math.min(method.getParamCount(), aType.length); i < c; i++)
                {
                TypeConstant typeParam = method.getParam(i).getType();
                TypeConstant typeTest  = aType[i];
                if (typeTest != null && !typeTest.isA(typeParam))
                    {
                    return false;
                    }
                }
            return true;
            });
        }

    /**
     * Helper method to find a method by the name and a predicate.
     *
     * @param sName  the method name to find
     * @param test   the predicate to check whether a method is a one to return
     *
     * @return the specified MethodStructure or null if not found
     */
    public MethodStructure findMethod(String sName, Predicate<MethodStructure> test)
        {
        MultiMethodStructure structMM = (MultiMethodStructure) getChild(sName);
        if (structMM != null)
            {
            for (MethodStructure method : structMM.methods())
                {
                if (test.test(method))
                    {
                    return method;
                    }
                }
            }
        return null;
        }

    /**
     * Helper method to find a method by the name and a predicate for this class and all its
     * contributions.
     *
     * @param sName  the method name to find
     * @param test   the predicate to check whether a method is a one to return
     *
     * @return the specified MethodStructure or null if not found
     */
    public MethodStructure findMethodDeep(String sName, Predicate<MethodStructure> test)
        {
        MethodStructure method = findMethod(sName, test);
        if (method == null)
            {
            for (Contribution contrib : getContributionsAsList())
                {
                TypeConstant typeContrib = contrib.getTypeConstant();
                switch (contrib.getComposition())
                    {
                    case Into:
                    case Delegates:
                        break;

                    case Annotation:
                        if (isIntoClassMixin(typeContrib))
                            {
                            break;
                            }
                        // break through
                    case Incorporates:
                    case Extends:
                    case Implements:
                        {
                        if (typeContrib.isExplicitClassIdentity(true))
                            {
                            ClassStructure clzContrib = (ClassStructure)
                                typeContrib.getSingleUnderlyingClass(true).getComponent();
                            if (clzContrib == null)
                                {
                                // this method could be used before the pool is "connected"
                                break;
                                }
                            method = clzContrib.findMethodDeep(sName, test);
                            if (method != null)
                                {
                                return method;
                                }
                            }
                        break;
                        }
                    }
                }
            }
        return method;
        }

    /**
     * Find the specified constructor of this class.
     *
     * @param types  the types of the constructor parameters
     *
     * @return the constructor; never null
     *
     * @throws IllegalStateException if the constructor cannot be found
     */
    public MethodStructure findConstructor(TypeConstant... types)
        {
        MethodStructure method = findMethod("construct", types.length, types);
        if (method == null)
            {
            throw new IllegalStateException(
                "no such constructor for " + types.length + " params on " + this);
            }
        return method;
        }

    /**
     * Helper method to find a property by the name for this class and all its contributions.
     *
     * @param sName  the property name to find
     *
     * @return the specified PropertyStructure or null if not found
     */
    public PropertyStructure findPropertyDeep(String sName)
        {
        Component component = getChild(sName);
        if (component == null)
            {
            for (Contribution contrib : getContributionsAsList())
                {
                TypeConstant typeContrib = contrib.getTypeConstant();
                switch (contrib.getComposition())
                    {
                    case Into:
                    case Delegates:
                        break;

                    case Annotation:
                        if (isIntoClassMixin(typeContrib))
                            {
                            break;
                            }
                        // break through
                    case Incorporates:
                    case Extends:
                    case Implements:
                        {
                        if (typeContrib.isExplicitClassIdentity(true))
                            {
                            ClassStructure clzContrib = (ClassStructure)
                                typeContrib.getSingleUnderlyingClass(true).getComponent();
                            if (clzContrib == null)
                                {
                                // this method could be used before the pool is "connected"
                                break;
                                }
                            PropertyStructure prop = clzContrib.findPropertyDeep(sName);
                            if (prop != null)
                                {
                                return prop;
                                }
                            }
                        break;
                        }
                    }
                }
            }
        return component instanceof PropertyStructure prop ? prop : null;
        }

    /**
     * For this class structure representing an R-Value, find the best "isA" relation among all its
     * contributions to the specified L-Value type.
     *
     * @param typeLeft    the L-Value type
     * @param typeRight   the R-Value type that this ClassStructure is for
     *
     * @return the best "isA" relation
     */
    public Relation calculateRelation(TypeConstant typeLeft, TypeConstant typeRight)
        {
        return calculateRelationImpl(typeLeft, typeRight, true);
        }

    /**
     * The implementation of the {@link #calculateRelation} method above.
     *
     * @param fAllowInto  specifies whether the "Into" contribution is to be skipped
     */
    protected Relation calculateRelationImpl(TypeConstant typeLeft, TypeConstant typeRight,
                                             boolean fAllowInto)
        {
        assert typeLeft.isSingleDefiningConstant();

        ConstantPool     pool        = typeLeft.getConstantPool();
        Constant         constIdLeft = typeLeft.getDefiningConstant();
        IdentityConstant idClzRight  = getIdentityConstant();

        // virtual children implement an implicit "Inner" interface, and are contained inside a
        // container class that implements an implicit "Outer" interface
        if (containsVirtualChild() && typeLeft.equals(pool.typeOuter()) ||
            isVirtualChild()       && typeLeft.equals(pool.typeInner()))
            {
            return Relation.IS_A;
            }

        switch (constIdLeft.getFormat())
            {
            case Module:
            case Package:
                // modules and packages are never parameterized
                return constIdLeft.equals(idClzRight) ? Relation.IS_A : Relation.INCOMPATIBLE;

            case NativeClass:
                constIdLeft = ((NativeRebaseConstant) constIdLeft).getClassConstant();
                // fall through
            case Class:
                if (constIdLeft.equals(pool.clzObject()))
                    {
                    return Relation.IS_A;
                    }

                if (constIdLeft.equals(idClzRight))
                    {
                    Relation relation = calculateAssignability(pool, typeLeft.getParamTypes(),
                            typeLeft.getAccess(), typeRight.getParamTypes());
                    if (relation == Relation.INCOMPATIBLE || !isVirtualChild())
                        {
                        return relation;
                        }

                    assert typeLeft.isVirtualChild() && typeRight.isVirtualChild();

                    // for virtual child we need to repeat the check for the parent types
                    TypeConstant typeParentLeft  = typeLeft.getParentType();
                    TypeConstant typeParentRight = typeRight.getParentType();

                    // similar to the algorithm at TypeConstant.calculateRelationToContribution()
                    // for auto-narrowing constants, without any additional context auto-narrowing
                    // virtual children of the same name are assignable if the parents are
                    // assignable in any direction
                    if (typeParentRight.isA(typeParentLeft) ||
                            typeRight.containsAutoNarrowing(true) && typeParentLeft.isA(typeParentRight))
                        {
                        return relation;
                        }

                    return Relation.INCOMPATIBLE;
                    }

                // similar to the comment above, without any additional context auto-narrowing virtual
                // children of the same name are assignable if the parents are assignable in any direction
                IdentityConstant idClzLeft = (IdentityConstant) constIdLeft;
                if (typeLeft.isVirtualChild() && typeRight.isVirtualChild() &&
                        idClzLeft.getName().equals(idClzRight.getName()))
                    {
                    TypeConstant typeParentLeft  = typeLeft.getParentType();
                    TypeConstant typeParentRight = typeRight.getParentType();

                    if (typeParentRight.isA(typeParentLeft))
                        {
                        return calculateAssignability(pool, typeLeft.getParamTypes(),
                                    typeLeft.getAccess(), typeRight.getParamTypes());
                        }

                    if (typeRight.containsAutoNarrowing(true) && typeParentLeft.isA(typeParentRight))
                        {
                        return ((ClassStructure) idClzLeft.getComponent()).
                                calculateAssignability(pool, typeLeft.getParamTypes(),
                                    typeLeft.getAccess(), typeRight.getParamTypes());
                        }

                    return Relation.INCOMPATIBLE;
                    }

                TypeConstant typeRebase = getRebaseType();
                if (typeRebase != null)
                    {
                    ClassStructure clzRebase = (ClassStructure)
                        typeRebase.getSingleUnderlyingClass(true).getComponent();

                    // rebase types are never parameterized and therefore cannot be "weak"
                    if (clzRebase.calculateRelationImpl(typeLeft,
                            clzRebase.getCanonicalType(), fAllowInto) == Relation.IS_A)
                        {
                        return Relation.IS_A;
                        }
                    }
                break;

            case Property:
            case TypeParameter:
            case FormalTypeChild:
                // r-value (this) is a real type; it cannot be assigned to a formal type
            case UnresolvedName:
                return Relation.INCOMPATIBLE;

            case ThisClass:
            case ParentClass:
            case ChildClass:
                {
                assert typeLeft.containsAutoNarrowing(false);
                Relation relation = calculateRelationImpl(
                        typeLeft.removeAutoNarrowing(), typeRight, fAllowInto);
                if (relation != Relation.INCOMPATIBLE)
                    {
                    return relation;
                    }
                // TODO GG explain
                break;
                }

            case Typedef:
                return calculateRelationImpl(
                        ((TypedefConstant) constIdLeft).getReferredToType(), typeRight, fAllowInto);

            case IsConst:
            case IsEnum:
            case IsModule:
            case IsPackage:
            case IsClass:
                return calculateRelationImpl(
                        ((KeywordConstant) constIdLeft).getBaseType(), typeRight, fAllowInto);

            default:
                throw new IllegalStateException("unexpected constant: " + constIdLeft);
            }

        Relation relation = Relation.INCOMPATIBLE;

        for (Contribution contrib : getContributionsAsList())
            {
            TypeConstant typeContrib = contrib.getTypeConstant();
            Composition  composition = contrib.getComposition();
            switch (composition)
                {
                case Into:
                    if (!fAllowInto)
                        {
                        break;
                        }
                    // fall through
                case Delegates:
                case Implements:
                    if (typeContrib.equals(pool.typeObject()))
                        {
                        // ignore trivial "implement Object" contribution
                        continue;
                        }

                    typeContrib = typeContrib.resolveGenerics(pool, typeRight.normalizeParameters());
                    if (typeRight.isAutoNarrowing() && typeContrib.isSingleUnderlyingClass(true))
                        {
                        typeContrib = typeContrib.ensureAutoNarrowing();
                        }
                    relation = relation.bestOf(typeContrib.calculateRelation(typeLeft));
                    if (relation == Relation.IS_A)
                        {
                        return Relation.IS_A;
                        }
                    break;

                case Annotation:
                    if (isIntoClassMixin(typeContrib))
                        {
                        break;
                        }
                    // break through
                case Incorporates:
                    fAllowInto = false;
                case Extends:
                    {
                    // the identity constant for those contributions is always a class
                    assert typeContrib.isExplicitClassIdentity(true);

                    // the "resolveType()" method, which accounts for conditional incorporates,
                    // does not deal with virtual parent's formal types parameters, so we use it
                    // only to filter out inapplicable conditional incorporates
                    if (composition == Composition.Incorporates &&
                            contrib.resolveType(pool, this, typeRight) == null)
                        {
                        break;
                        }

                    typeContrib = typeContrib.resolveGenerics(pool, typeRight.normalizeParameters());

                    ClassConstant  idContrib = (ClassConstant) typeContrib.getSingleUnderlyingClass(true);
                    ClassStructure clzBase   = (ClassStructure) idContrib.getComponent();
                    if (typeContrib.isVirtualChild())
                        {
                        TypeConstant typeParent = typeRight.getOriginParentType();
                        if (typeParent.isA(typeContrib.getParentType()))
                            {
                            // see the doc for "ensureVirtualParent" for the explanation
                            typeContrib = typeContrib.ensureVirtualParent(typeParent,
                                                !getName().equals(clzBase.getName()));
                            clzBase = (ClassStructure) typeContrib.getSingleUnderlyingClass(true).
                                                getComponent();
                            }
                        }
                    relation = relation.bestOf(
                                    clzBase.calculateRelationImpl(typeLeft, typeContrib, fAllowInto));
                    if (relation == Relation.IS_A)
                        {
                        return Relation.IS_A;
                        }
                    break;
                    }

                case Import:
                    // import contribution doesn't play any role in the type relationship
                    break;

                default:
                    throw new IllegalStateException();
                }
            }

        return relation;
        }

    /**
     * For this class structure representing an R-Value, find a contribution assignable to the
     * specified L-Value union type.
     *
     * @param typeLeft    the L-Value union type
     * @param listRight   the list of actual generic parameters for this class
     *
     * @return the relation
     */
    public Relation findUnionContribution(UnionTypeConstant typeLeft,
                                          List<TypeConstant> listRight)
        {
        ConstantPool pool     = typeLeft.getConstantPool();
        Relation     relation = Relation.INCOMPATIBLE;

        for (Contribution contrib : getContributionsAsList())
            {
            TypeConstant typeContrib = contrib.getTypeConstant();
            switch (contrib.getComposition())
                {
                case Extends:
                    {
                    // the identity constant for extension is always a class
                    assert typeContrib.isExplicitClassIdentity(true);

                    ClassConstant constContrib = (ClassConstant)
                            typeContrib.getSingleUnderlyingClass(true);

                    TypeConstant typeResolved = contrib.resolveType(pool, this, listRight);

                    relation = relation.bestOf(((ClassStructure) constContrib.getComponent()).
                            findUnionContribution(typeLeft, typeResolved.getParamTypes()));
                    if (relation == Relation.IS_A)
                        {
                        return Relation.IS_A;
                        }
                    break;
                    }

                case Into:
                case Implements:
                    typeContrib = typeContrib.resolveGenerics(pool,
                                        new SimpleTypeResolver(pool, listRight));
                    if (typeContrib != null)
                        {
                        if (typeContrib.equals(pool.typeObject()))
                            {
                            return Relation.INCOMPATIBLE;
                            }

                        relation = relation.bestOf(typeContrib.calculateRelation(typeLeft));
                        if (relation == Relation.IS_A)
                            {
                            return Relation.IS_A;
                            }
                        }
                    break;

                case Delegates:
                case Annotation:
                case Incorporates:
                    // delegation, annotation and incorporation cannot be of a union type
                    return Relation.INCOMPATIBLE;

                default:
                    throw new IllegalStateException();
                }
            }
        return relation;
        }

    /**
     * Determine if this template consumes a formal type with the specified name for the specified
     * access policy.
     */
    public boolean consumesFormalType(ConstantPool pool,
                                      String sName, Access access, List<TypeConstant> listActual)
        {
        return consumesFormalTypeImpl(pool, sName, access, listActual, true);
        }

    protected boolean consumesFormalTypeImpl(ConstantPool pool, String sName, Access access,
                                             List<TypeConstant> listActual, boolean fAllowInto)
        {
        assert indexOfGenericParameter(sName) >= 0;

        for (Component child : children())
            {
            if (child instanceof MultiMethodStructure mms)
                {
                for (MethodStructure method : mms.methods())
                    {
                    if (!method.isStatic() && method.isAccessible(access)
                            && method.consumesFormalType(sName))
                        {
                        return true;
                        }
                    }
                }
            else if (child instanceof PropertyStructure property)
                {
                if (property.isGenericTypeParameter())
                    {
                    // generic types don't consume
                    continue;
                    }

                TypeConstant constType = property.getType();
                if (!listActual.isEmpty())
                    {
                    constType = constType.resolveGenerics(pool, new SimpleTypeResolver(pool, listActual));
                    }

                if (property.isRefAccessible(access)
                        && constType.consumesFormalType(sName, Access.PUBLIC))
                    {
                    return true;
                    }

                if (property.isExplicitReadOnly())
                    {
                    // read-only; skip the setter's check
                    continue;
                    }

                if (property.isVarAccessible(access)
                        && constType.producesFormalType(sName, Access.PUBLIC))
                    {
                    return true;
                    }
                }
            }

        // check the contributions
        for (Contribution contrib : getContributionsAsList())
            {
            TypeConstant typeContrib = contrib.getTypeConstant();
            switch (contrib.getComposition())
                {
                case Delegates:
                case Implements:
                    break;

                case Into:
                    if (!fAllowInto)
                        {
                        continue;
                        }
                    break;

                case Annotation:
                    if (isIntoClassMixin(typeContrib))
                        {
                        continue;
                        }
                    // break through
                case Incorporates:
                    fAllowInto = false;
                    // break through
                case Extends:
                    // the identity constant for those contributions is always a class
                    assert typeContrib.isExplicitClassIdentity(true);
                    break;

                default:
                    throw new IllegalStateException();
                }

            if (typeContrib.isExplicitClassIdentity(true))
                {
                TypeConstant typeResolved = contrib.resolveType(pool, this, listActual);
                if (typeResolved == null || !typeResolved.isParamsSpecified())
                    {
                    // conditional incorporation didn't apply to the actual type
                    // or the contribution is not parameterized
                    continue;
                    }

                ClassStructure clzContrib = (ClassStructure)
                    typeContrib.getSingleUnderlyingClass(true).getComponent();

                Map<StringConstant, TypeConstant> mapFormal = clzContrib.getTypeParams();
                List<TypeConstant> listContribParams = clzContrib.normalizeParameters(
                    pool, typeContrib.getParamTypes());
                List<TypeConstant> listContribActual = typeResolved.getParamTypes();

                Iterator<TypeConstant> iterParams = listContribParams.iterator();
                Iterator<StringConstant> iterNames = mapFormal.keySet().iterator();

                while (iterParams.hasNext())
                    {
                    TypeConstant constParam = iterParams.next();
                    String sFormal = iterNames.next().getValue();

                    if (constParam.producesFormalType(sName, access)
                            && clzContrib.consumesFormalTypeImpl(
                                    pool, sFormal, access, listContribActual, fAllowInto)
                        ||
                        constParam.consumesFormalType(sName, access)
                            && clzContrib.producesFormalTypeImpl(
                                    pool, sFormal, access, listContribActual, fAllowInto))
                        {
                        return true;
                        }
                    }
                }
            else
                {
                // the only contributions of relation type could be delegations and implementations
                // and since they cannot be conditional at any level, the actual types won't matter
                // for further recursion
                if (typeContrib.consumesFormalType(sName, access))
                    {
                    return true;
                    }
                }
            }

        return false;
        }

    /**
     * Determine if this template produces a formal type with the specified name for the
     * specified access policy.
     */
    public boolean producesFormalType(ConstantPool pool, String sName, Access access,
                                      List<TypeConstant> listActual)
        {
        return producesFormalTypeImpl(pool, sName, access, listActual, true);
        }

    protected boolean producesFormalTypeImpl(ConstantPool pool, String sName, Access access,
                                             List<TypeConstant> listActual, boolean fAllowInto)
        {
        assert indexOfGenericParameter(sName) >= 0;

        for (Component child : children())
            {
            if (child instanceof MultiMethodStructure mms)
                {
                for (MethodStructure method : mms.methods())
                    {
                    if (!method.isStatic() && method.isAccessible(access)
                            && method.producesFormalType(sName))
                        {
                        return true;
                        }
                    }
                }
            else if (child instanceof PropertyStructure property)
                {
                if (property.isGenericTypeParameter())
                    {
                    // generic types don't produce
                    continue;
                    }

                TypeConstant constType = property.getType();
                if (!listActual.isEmpty())
                    {
                    constType = constType.resolveGenerics(pool, new SimpleTypeResolver(pool, listActual));
                    }

                if (property.isRefAccessible(access)
                        && constType.producesFormalType(sName, Access.PUBLIC))
                    {
                    return true;
                    }

                if (property.isExplicitReadOnly())
                    {
                    // read-only; skip the setter's check
                    continue;
                    }

                if (property.isVarAccessible(access)
                        && constType.consumesFormalType(sName, Access.PUBLIC))
                    {
                    return true;
                    }
                }
            }

        // check the contributions
        for (Contribution contrib : getContributionsAsList())
            {
            TypeConstant typeContrib = contrib.getTypeConstant();
            switch (contrib.getComposition())
                {
                case Delegates:
                case Implements:
                    break;

                case Into:
                    if (!fAllowInto)
                        {
                        continue;
                        }
                    break;

                case Annotation:
                    if (isIntoClassMixin(typeContrib))
                        {
                        continue;
                        }
                    // break through
                case Incorporates:
                    fAllowInto = false;
                    // break through
                case Extends:
                    // the identity constant for those contributions is always a class
                    assert typeContrib.isExplicitClassIdentity(true);
                    break;

                default:
                    throw new IllegalStateException();
                }

            if (typeContrib.isExplicitClassIdentity(true))
                {
                TypeConstant typeResolved = contrib.resolveType(pool, this, listActual);
                if (typeResolved == null || !typeResolved.isParamsSpecified())
                    {
                    // conditional incorporation didn't apply to the actual type
                    // or the contribution is not parameterized
                    continue;
                    }

                ClassStructure clzContrib = (ClassStructure)
                    typeContrib.getSingleUnderlyingClass(true).getComponent();

                Map<StringConstant, TypeConstant> mapFormal = clzContrib.getTypeParams();
                List<TypeConstant> listContribParams = clzContrib.normalizeParameters(
                    pool, typeContrib.getParamTypes());
                List<TypeConstant> listContribActual = typeResolved.getParamTypes();

                Iterator<TypeConstant> iterParams = listContribParams.iterator();
                Iterator<StringConstant> iterNames = mapFormal.keySet().iterator();

                while (iterParams.hasNext())
                    {
                    TypeConstant constParam = iterParams.next();
                    String sFormal = iterNames.next().getValue();

                    if (constParam.producesFormalType(sName, access)
                            && clzContrib.producesFormalTypeImpl(
                                    pool, sFormal, access, listContribActual, fAllowInto)
                        ||
                        constParam.consumesFormalType(sName, access)
                            && clzContrib.consumesFormalTypeImpl(
                                    pool, sFormal, access, listContribActual, fAllowInto))
                        {
                        return true;
                        }
                    }
                }
            else
                {
                // the only contributions of relation type could be delegations and implementations
                // and since they cannot be conditional at any level, the actual types won't matter
                // for further recursion
                if (typeContrib.producesFormalType(sName, access))
                    {
                    return true;
                    }
                }
            }

        return false;
        }

    /**
     * For this class structure representing an L-Value interface, check recursively if all properties
     * and methods have a matching (substitutable) property or method on the specified R-value type.
     *
     * @param typeRight    the type to look for the matching methods
     * @param accessRight  the access level to extend the check for
     * @param listLeft     the actual generic parameters for this interface
     *
     * @return a set of method/property signatures that don't have a match
     */
    public Set<SignatureConstant> isInterfaceAssignableFrom(TypeConstant typeRight, Access accessRight,
                                                            List<TypeConstant> listLeft)
        {
        ConstantPool           pool     = typeRight.getConstantPool();
        Set<SignatureConstant> setMiss  = new HashSet<>();
        GenericTypeResolver    resolver = null;

        for (Component child : children())
            {
            if (child instanceof PropertyStructure prop)
                {
                if (prop.isGenericTypeParameter())
                    {
                    if (!typeRight.containsGenericParam(prop.getName()))
                        {
                        setMiss.add(prop.getIdentityConstant().getSignature());
                        }
                    }
                else
                    {
                    // TODO: should we check the "Var" access?
                    if (!prop.isRefAccessible(accessRight) || prop.isStatic())
                        {
                        continue;
                        }

                    SignatureConstant sig = prop.getIdentityConstant().getSignature();
                    if (!listLeft.isEmpty())
                        {
                        if (resolver == null)
                            {
                            resolver = new SimpleTypeResolver(pool, listLeft);
                            }
                        sig = sig.resolveGenericTypes(pool, resolver);
                        }

                    if (!typeRight.containsSubstitutableMethod(sig,
                            accessRight, false, Collections.EMPTY_LIST))
                        {
                        setMiss.add(sig);
                        }
                    }
                }
            else if (child instanceof MultiMethodStructure mms)
                {
                for (MethodStructure method : mms.methods())
                    {
                    if (!method.isAccessible(accessRight))
                        {
                        continue;
                        }

                    SignatureConstant sig = method.getIdentityConstant().getSignature();

                    if (method.isVirtualConstructor())
                        {
                        // a constructor cannot be duck-typed, because even if a base class has it,
                        // a derived class may not, which would break transitivity of "isA"
                        setMiss.add(sig);
                        }
                    else if (method.isFunction())
                        {
                        if (method.hasCode())
                            {
                            continue;
                            }

                        // funky interface scenario
                        if (!typeRight.containsSubstitutableMethod(sig,
                                accessRight, true, Collections.EMPTY_LIST))
                            {
                            setMiss.add(sig);
                            }
                        }
                    else
                        {
                        if (!listLeft.isEmpty())
                            {
                            if (resolver == null)
                                {
                                resolver = new SimpleTypeResolver(pool, listLeft);
                                }
                            sig = sig.resolveGenericTypes(pool, resolver);
                            }

                        if (!typeRight.containsSubstitutableMethod(sig,
                                accessRight, false, Collections.EMPTY_LIST))
                            {
                            setMiss.add(sig);
                            }
                        }
                    }
                }
            }

        for (Contribution contrib : getContributionsAsList())
            {
            if (contrib.getComposition() == Composition.Implements)
                {
                TypeConstant typeResolved = contrib.resolveType(pool, this, listLeft);
                if (typeResolved.containsUnresolved())
                    {
                    continue;
                    }

                ClassStructure clzSuper = (ClassStructure)
                    typeResolved.getSingleUnderlyingClass(true).getComponent();

                assert clzSuper.getFormat() == Component.Format.INTERFACE;

                setMiss.addAll(clzSuper.isInterfaceAssignableFrom(
                        typeRight, accessRight, typeResolved.getParamTypes()));
                }
            }
        return setMiss;
        }

    /**
     * Check recursively if this class contains a matching (substitutable) method or property.
     *
     * @param pool        the ConstantPool to use
     * @param signature   the signature to look for the match for (formal parameters resolved)
     * @param access      the access level to limit the check to
     * @param fFunction   if true, the signature represents a function
     * @param listParams  the actual generic parameters for this interface
     *
     * @return true iff there is a matching method or property
     */
    public boolean containsSubstitutableMethod(ConstantPool pool, SignatureConstant signature,
                                               Access access, boolean fFunction,
                                               List<TypeConstant> listParams)
        {
        return containsSubstitutableMethodImpl(pool, signature, access, fFunction,
                listParams, getIdentityConstant(), true);
        }

    protected boolean containsSubstitutableMethodImpl(ConstantPool pool, SignatureConstant signature,
                                                      Access access, boolean fFunction,
                                                      List<TypeConstant> listParams,
                                                      IdentityConstant idClass, boolean fAllowInto)
        {
        Component child = getChild(signature.getName());

        if (signature.isProperty())
            {
            if (child instanceof PropertyStructure prop)
                {
                // TODO: should we check the "Var" access?
                if (!prop.isStatic() &&
                        prop.isRefAccessible(access) &&
                        prop.isSubstitutableFor(pool, signature, listParams))
                    {
                    return true;
                    }
                }
            }
        else
            {
            if (child instanceof MultiMethodStructure mms)
                {
                GenericTypeResolver resolver = listParams.isEmpty()
                        ? null
                        : new SimpleTypeResolver(pool, listParams);

                for (MethodStructure method : mms.methods())
                    {
                    SignatureConstant sigMethod = method.getIdentityConstant().getSignature();
                    if (method.isAccessible(access) && method.isFunction() == fFunction)
                        {
                        if (fFunction)
                            {
                            if (sigMethod.isSubstitutableFor(signature, idClass.getType()))
                                {
                                return true;
                                }
                            }
                        else
                            {
                            sigMethod = sigMethod.resolveGenericTypes(pool, resolver);
                            if (sigMethod.isSubstitutableFor(signature, idClass.getType()))
                                {
                                return true;
                                }
                            }
                        }
                    }
                }
            }

        // check the contributions
        for (Contribution contrib : getContributionsAsList())
            {
            TypeConstant typeContrib = contrib.getTypeConstant();
            if (typeContrib.containsUnresolved())
                {
                continue;
                }

            switch (contrib.getComposition())
                {
                case Delegates:
                case Implements:
                case Import:
                    break;

                case Into:
                    if (!fAllowInto)
                        {
                        continue;
                        }
                    break;

                case Annotation:
                    if (isIntoClassMixin(typeContrib))
                        {
                        continue;
                        }
                    // break through
                case Incorporates:
                    fAllowInto = false;
                    // break through
                case Extends:
                    // the identity constant for these contributions is always a class
                    assert typeContrib.isExplicitClassIdentity(true);
                    break;

                default:
                    throw new IllegalStateException();
                }

            if (typeContrib.isExplicitClassIdentity(true))
                {
                TypeConstant typeResolved = contrib.resolveType(pool, this, listParams);
                if (typeResolved != null)
                    {
                    ClassStructure clzContrib = (ClassStructure)
                        typeResolved.getSingleUnderlyingClass(true).getComponent();

                    if (clzContrib.containsSubstitutableMethodImpl(pool, signature, access, fFunction,
                            typeResolved.getParamTypes(), idClass, fAllowInto))
                        {
                        return true;
                        }
                    }
                }
            else
                {
                typeContrib = contrib.resolveGenerics(pool, new SimpleTypeResolver(pool, listParams));

                if (typeContrib.containsSubstitutableMethod(signature, access, fFunction, new ArrayList<>()))
                    {
                    return true;
                    }
                }
            }
        return false;
        }


    // ----- run-time support ----------------------------------------------------------------------

    /**
     * Create a synthetic MethodStructure for a default initializer for a given type.
     * Note: the resulting method will be marked as abstract if there are no properties
     *       to initialize.
     *
     * @param pool        the ConstantPool to use
     * @param typeStruct  the type, for which a default constructor is to be created
     * @param mapFields   the field infos keyed by properties nids
     *
     * @return the [synthetic] MethodStructure for the corresponding default constructor
     */
    public MethodStructure createInitializer(ConstantPool pool, TypeConstant typeStruct,
                                             Map<Object, FieldInfo> mapFields)
        {
        int nFlags = Format.METHOD.ordinal() | Access.PUBLIC.FLAGS | STATIC_BIT | SYNTHETIC_BIT;

        // create a transient MethodStructure (without an intermediate MultiMethodStructure)
        MethodConstant idMethod = pool.ensureMethodConstant(
                (IdentityConstant) pool.register(getIdentityConstant()),
                "default", TypeConstant.NO_TYPES, TypeConstant.NO_TYPES);

        // use the module as a parent component without adding the method as a child
        ModuleStructure module = pool.getFileStructure().getModule();
        MethodStructure method = new MethodStructure(module, nFlags, idMethod, null,
                Annotation.NO_ANNOTATIONS, Parameter.NO_PARAMS, Parameter.NO_PARAMS, true, false);

        Code code = method.createCode();

        assert typeStruct.getAccess() == Access.STRUCT;

        TypeInfo infoStruct = typeStruct.ensureTypeInfo();
        for (Map.Entry<Object, FieldInfo> entry : mapFields.entrySet())
            {
            Object       nid      = entry.getKey();
            PropertyInfo infoProp = nid instanceof PropertyConstant idProp
                    ? infoStruct.findProperty(idProp)
                    : infoStruct.findPropertyByNid(nid);

            if (infoProp == null || infoProp.isInjected())
                {
                // synthetic field; skip
                continue;
                }

            FieldInfo        field     = entry.getValue();
            PropertyConstant idField   = infoProp.getFieldIdentity();
            MethodConstant   idInit    = null;
            Constant         constInit = null;

            if (infoProp.isInitialized())
                {
                constInit = infoProp.getInitialValue();
                if (constInit == null)
                    {
                    idInit = infoProp.getInitializer();
                    }
                }
            else
                {
                if (!infoProp.isImplicitlyAssigned())
                    {
                    constInit = infoProp.getType().getDefaultValue();
                    }
                }

            if (constInit != null)
                {
                code.add(new L_Set(idField, constInit));
                if (field.isTransient())
                    {
                    field.constInit = constInit;
                    }
                }
            else if (idInit != null)
                {
                MethodStructure methodInit = (MethodStructure) idInit.getComponent();
                if (methodInit.isFunction())
                    {
                    code.add(new Call_01(idInit, idField));
                    }
                else
                    {
                    code.add(new Invoke_01(new Register(typeStruct, Op.A_THIS), idInit, idField));
                    }

                // TODO: transient field initializer is not currently supported
                //  (non-static initializer would need a struct, which by that time is frozen)
                }

            if (field.isInflated())
                {
                // assign the ref's OUTER property; the auto-initializer will be called
                // by the constructor (see ClassTemplate#proceedConstruction)
                code.add(new Op()
                    {
                    @Override
                    public int process(Frame frame, int iPC)
                        {
                        GenericHandle hStruct = (GenericHandle) frame.getThis();
                        RefHandle     hRef    = (RefHandle) hStruct.getField(frame, idField);

                        hRef.setField(frame, GenericHandle.OUTER, hStruct);
                        return iPC + 1;
                        }

                    @Override
                    public void write(DataOutput out, ConstantRegistry registry)
                        {
                        }

                    @Override
                    public String toString()
                        {
                        return "initRef: " + idField;
                        }
                    });
                }
            }

        if (code.hasOps())
            {
            code.add(new Return_0());

            code.registerConstants(pool);
            }
        else
            {
            method.setAbstract(true);
            }
        return method;
        }

    /**
     * Create a synthetic "delegating" method for this class that delegates to the specified
     * method on the specified property.
     *
     * @param prop         the property to delegate to
     * @param propTarget   the delegation target property
     * @param sigAccessor  the delegating accessor's signature (getter vs. setter)
     *
     * @return a synthetic MethodStructure that has the auto-generated delegating code
     */
    public MethodStructure ensurePropertyDelegation(
            PropertyStructure prop, PropertyStructure propTarget, SignatureConstant sigAccessor)
        {
        PropertyStructure propHost = (PropertyStructure) getChild(prop.getName());
        if (propHost == null)
            {
            assert !prop.isStatic();
            propHost = createProperty(false, prop.getAccess(), prop.getVarAccess(),
                    prop.getType(), prop.getName());
            }

        MethodStructure methodDelegate = propHost.findMethod(sigAccessor);
        if (methodDelegate == null)
            {
            ConstantPool pool     = getConstantPool();
            TypeConstant typeProp = prop.getType();

            boolean      fGet;
            Parameter[]  aParams;
            Parameter[]  aReturns;
            if (sigAccessor.getName().equals("get"))
                {
                fGet     = true;
                aParams  = Parameter.NO_PARAMS;
                aReturns = new Parameter[] {new Parameter(pool, typeProp,
                                null, null, true, 0, false)};
                }
            else
                {
                fGet     = false;
                aParams  = new Parameter[] {new Parameter(pool, typeProp,
                                prop.getName(), null, false, 0, false)};
                aReturns = Parameter.NO_PARAMS;
                }

            methodDelegate = propHost.createMethod(false, prop.getAccess(), null,
                    aReturns, sigAccessor.getName(), aParams, true, false);

            Code             code       = methodDelegate.createCode();
            PropertyConstant idDelegate = prop.getIdentityConstant();

            Register regTarget = code.createRegister(propTarget.getType());
            code.add(new L_Get(propTarget.getIdentityConstant(), regTarget));

            if (fGet)
                {
                Register regReturn = new Register(typeProp, Op.A_STACK);

                code.add(new P_Get(idDelegate, regTarget, regReturn));
                code.add(new Return_1(regReturn));
                }
            else // "set"
                {
                Register regArg = new Register(typeProp, 0);

                code.add(new P_Set(idDelegate, regTarget, regArg));
                code.add(new Return_0());
                }

            code.registerConstants(pool);
            }
        return methodDelegate;
        }

    /**
     * Create a synthetic "delegating" method for this class that delegates to the specified
     * method on the specified property.
     *
     * @param method     the method to delegate to
     * @param sDelegate  the delegating property name
     *
     * @return a synthetic MethodStructure that has the auto-generated delegating code
     */
    public MethodStructure ensureMethodDelegation(MethodStructure method, String sDelegate)
        {
        SignatureConstant sig            = method.getIdentityConstant().getSignature();
        MethodStructure   methodDelegate = findMethod(sig);
        if (methodDelegate == null)
            {
            ConstantPool pool         = getConstantPool();
            TypeConstant typeFormal   = getFormalType();
            TypeConstant typePrivate  = pool.ensureAccessTypeConstant(typeFormal, Access.PRIVATE);
            TypeInfo     infoPrivate  = typePrivate.ensureTypeInfo();
            PropertyInfo infoDelegate = infoPrivate.findProperty(sDelegate);
            TypeConstant typeDelegate = infoDelegate.getType();

            Parameter[]  aParams      = method.getParamArray().clone();
            Parameter[]  aReturns     = method.getReturnArray().clone();

            methodDelegate = createMethod(false, method.getAccess(), null,
                    aReturns, method.getName(), aParams, true, false);

            Code           code       = methodDelegate.createCode();
            MethodInfo     infoMethod = typeDelegate.ensureTypeInfo().getMethodBySignature(sig);
            MethodConstant idMethod   = infoMethod.getIdentity();
            int            cParams    = method.getParamCount();
            int            cReturns   = method.getReturnCount();
            Register[]     aregParam  = cParams  == 0 ? null : new Register[cParams];
            boolean        fAtomic    = infoDelegate.isAtomic();

            for (int i = 0; i < cParams; i++)
                {
                aregParam[i] = new Register(aParams[i].getType(), i);
                }

            Register regProp;
            if (infoDelegate.isConstant())
                {
                Constant constValue = infoDelegate.getInitialValue();

                regProp = code.createRegister(typeDelegate);
                code.add(new Var_I(regProp, constValue == null
                        ? pool.ensureSingletonConstConstant(infoDelegate.getIdentity())
                        : constValue));
                }
            else
                {
                regProp = code.createRegister(typeDelegate);
                code.add(new L_Get(infoDelegate.getIdentity(), regProp));
                }

            switch (cReturns)
                {
                case 0:
                    if (fAtomic)
                        {
                        Register regReturn = code.createRegister(pool.ensureFutureVar(pool.typeTuple()));
                        code.add(new Var_D(regReturn));

                        switch (cParams)
                            {
                            case 0:
                                code.add(new Invoke_01(regProp, idMethod, regReturn));
                                break;

                            case 1:
                                code.add(new Invoke_11(regProp, idMethod, aregParam[0], regReturn));
                                break;

                            default:
                                code.add(new Invoke_N1(regProp, idMethod, aregParam, regReturn));
                                break;
                            }
                        code.add(new Return_1(regReturn));
                        }
                    else
                        {
                        switch (cParams)
                            {
                            case 0:
                                code.add(new Invoke_00(regProp, idMethod));
                                break;

                            case 1:
                                code.add(new Invoke_10(regProp, idMethod, aregParam[0]));
                                break;

                            default:
                                code.add(new Invoke_N0(regProp, idMethod, aregParam));
                                break;
                            }
                        code.add(new Return_0());
                        }
                    break;

                case 1:
                    {
                    TypeConstant typeReturn = aReturns[0].getType();
                    Register     regReturn;
                    if (fAtomic)
                        {
                        regReturn = code.createRegister(pool.ensureFutureVar(typeReturn));
                        code.add(new Var_D(regReturn));
                        }
                    else
                        {
                        regReturn = new Register(typeReturn, Op.A_STACK);
                        }

                    switch (cParams)
                        {
                        case 0:
                            code.add(new Invoke_01(regProp, idMethod, regReturn));
                            break;

                        case 1:
                            code.add(new Invoke_11(regProp, idMethod, aregParam[0], regReturn));
                            break;

                        default:
                            code.add(new Invoke_N1(regProp, idMethod, aregParam, regReturn));
                            break;
                        }
                    code.add(new Return_1(regReturn));
                    break;
                    }

                default:
                    {
                    Register[] aregReturn = new Register[cReturns];

                    for (int i = 0; i < cReturns; i++)
                        {
                        TypeConstant typeReturn = aReturns[i].getType();
                        if (fAtomic)
                            {
                            Register regReturn = code.createRegister(pool.ensureFutureVar(typeReturn));
                            code.add(new Var_D(regReturn));
                            aregReturn[i] = regReturn;
                            }
                        else
                            {
                            aregReturn[i] = code.createRegister(typeReturn);
                            }
                        }

                    switch (cParams)
                        {
                        case 0:
                            code.add(new Invoke_0N(regProp, idMethod, aregReturn));
                            break;

                        case 1:
                            code.add(new Invoke_1N(regProp, idMethod, aregParam[0], aregReturn));
                            break;

                        default:
                            code.add(new Invoke_NN(regProp, idMethod, aregParam, aregReturn));
                            break;
                        }
                    code.add(new Return_N(aregReturn));
                    break;
                    }
                }

            code.registerConstants(pool);
            }
        return methodDelegate;
        }

    /**
     * Create necessary method structures for the Const interface functions and methods.
     *
     * All the methods that are created artificially will be marked as "transient" and should not be
     * persisted during the serialization phase.
     *
     * Note: we should not call "registerConstants()" for generated code when called during the
     *       compilation phase; it will be done by the compiler.
     *
     * @param fDisassemble  if true, indicates that this method is called during the "disassemble"
     *                      phase, when the classes are constructed from its persistent storage;
     *                      false indicates that it's called during the compilation, when classes
     *                      are created from the source code
     */
    public void synthesizeConstInterface(boolean fDisassemble)
        {
        assert getFormat() == Format.CONST;

        ConstantPool pool = getConstantPool();

        synthesizeConstFunction("equals",   2, pool.typeBoolean());
        synthesizeConstFunction("compare",  2, pool.typeOrdered());
        synthesizeConstFunction("hashCode", 1, pool.typeCInt64());
        synthesizeAppendTo(fDisassemble);
        }

    /**
     * Synthesize the function if it doesn't exist.
     */
    private void synthesizeConstFunction(String sName, int cParams, TypeConstant typeReturn)
        {
        Predicate<MethodStructure> test = method ->
            {
            if (    method.getTypeParamCount() != 1
                 || method.getParamCount()     != 1  + cParams
                 || !method.getParam(0).isTypeParameter()
                 || method.getReturnCount()    != 1
                 || !method.getReturn(0).getType().equals(typeReturn))
                {
                return false;
                }

            // abstract or synthesized functions don't count and neither do the Object methods
            return method.hasCode() && !method.isTransient() &&
                !method.getIdentityConstant().getNamespace().equals(getConstantPool().clzObject());
            };

        MethodStructure fnThis = findMethodDeep(sName, test);

        if (fnThis == null)
            {
            // 1) build parameters;
            //  note that to instantiate type parameters we need to have the method constant,
            //  which needs to be created UnresolvedTypeConstant first and resolved later
            ConstantPool pool     = getConstantPool();
            TypeConstant typeThis = pool.ensureThisTypeConstant(getIdentityConstant(), null);
            TypeConstant typeType = typeThis.getType();

            Parameter[] aParam = new Parameter[1 + cParams];

            aParam[0] = new Parameter(pool, typeType, "CompileType", null, false, 0, true);

            if (cParams == 1)
                {
                aParam[1] = new Parameter(pool, new UnresolvedTypeConstant(pool,
                        new UnresolvedNameConstant(pool, "CompileType")),
                        "value", null, false, 1, false);
                }
            else
                {
                for (int i = 1; i <= cParams; i++)
                    {
                    aParam[i] = new Parameter(pool, new UnresolvedTypeConstant(pool,
                            new UnresolvedNameConstant(pool, "CompileType")),
                            "value" + i, null, false, i, false);
                    }
                }

            Parameter[] aReturn = new Parameter[]
                {
                new Parameter(pool, typeReturn, null, null, true, 0, false)
                };

            // 2) create the method structure and [yet unresolved] identity
            fnThis = createMethod(/*function*/ true, Constants.Access.PUBLIC, null,
                    aReturn, sName, aParam, /*hasCode*/ true, /*usesSuper*/ false);
            fnThis.markNative();
            fnThis.markTransient();

            // 3) resolve the identity
            MethodConstant idMethod    = fnThis.getIdentityConstant();
            TypeConstant[] atypeParams = idMethod.getRawParams();

            TypeParameterConstant constParam = pool.ensureRegisterConstant(idMethod, 0, "CompileType");
            TypeConstant          typeFormal = constParam.getType();

            for (int i = 1, c = atypeParams.length; i < c; i++)
                {
                ((UnresolvedTypeConstant) atypeParams[i]).resolve(typeFormal);
                }

            // 4) get rid of the unresolved constants
            fnThis.resolveTypedefs();
            }
        }

    /**
     * If explicit "toString()" exists, not using the "super()", and "appendTo()" does not exist,
     * generate "appendTo()" to route to "toString" and a trivial "estimateStringLength".
     */
    private void synthesizeAppendTo(boolean fRegisterConstants)
        {
        MethodStructure methToString = findMethod("toString", 0);
        if (methToString != null && !methToString.usesSuper())
            {
            ConstantPool pool         = getConstantPool();
            TypeConstant typeAppender = pool.ensureParameterizedTypeConstant(
                pool.ensureEcstasyTypeConstant("Appender"), pool.typeChar());

            MethodStructure methAppendTo = findMethod("appendTo", 1, typeAppender);
            if (methAppendTo == null)
                {
                Parameter[] aRet = new Parameter[]
                    {
                    new Parameter(pool, typeAppender, null, null, true, 0, false)
                    };
                Parameter[] aParam = new Parameter[]
                    {
                    new Parameter(pool, typeAppender, "appender", null, false, 0, false)
                    };
                Annotation[] aAnno = new Annotation[]
                    {
                    pool.ensureAnnotation(pool.clzOverride())
                    };

                methAppendTo = createMethod(/*function*/ false, Constants.Access.PUBLIC, aAnno,
                        aRet, "appendTo", aParam, /*hasCode*/ true, /*usesSuper*/ false);
                methAppendTo.markTransient();

                Code code = methAppendTo.ensureCode();

                // Appender<Char> appendTo(Appender<Char> appender)
                //    {
                //    return this.toString().appendTo(appender);
                //    }
                Register regThis     = new Register(typeAppender, Op.A_THIS);
                Register regAppender = new Register(typeAppender, 0);
                Register regStack    = new Register(typeAppender, Op.A_STACK);
                Register regResult   = new Register(typeAppender, Op.A_STACK);

                code.add(new Invoke_01(regThis, methToString.getIdentityConstant(), regStack));
                code.add(new Invoke_11(regStack, methAppendTo.getIdentityConstant(), regAppender, regResult));
                code.add(new Return_1(regResult));

                if (fRegisterConstants)
                    {
                    code.registerConstants(pool);
                    }

                MethodStructure methEstimate = findMethod("estimateStringLength", 0);
                if (methEstimate == null)
                    {
                    Parameter[] aReturn = new Parameter[]
                        {
                        new Parameter(pool, pool.typeInt(), null, null, true, 0, false)
                        };
                    methEstimate = createMethod(/*function*/ false, Constants.Access.PUBLIC, aAnno,
                            aReturn, "estimateStringLength", Parameter.NO_PARAMS,
                            /*hasCode*/ true, /*usesSuper*/ false);
                    methEstimate.markTransient();

                    code = methEstimate.ensureCode();

                    // return 0;
                    code.add(new Return_1(pool.val0()));

                    if (fRegisterConstants)
                        {
                        code.registerConstants(pool);
                        }
                    }
                }
            }
        }


    // ----- XvmStructure/Component methods --------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        super.disassemble(in);

        // read in the type parameters
        m_mapParams = disassembleTypeParams(in);
        m_constPath = (LiteralConstant) getConstantPool().getConstant(readIndex(in));
        }

    @Override
    protected void synthesizeChildren()
        {
        if (getFormat() == Format.CONST)
            {
            synthesizeConstInterface(true);
            }

        super.synthesizeChildren();
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        // register the type parameters
        m_mapParams = registerTypeParams(m_mapParams);
        m_constPath = (LiteralConstant) pool.register(m_constPath);

        // invalidate cached types
        m_typeCanonical = null;
        m_typeFormal    = null;
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);

        // write out the type parameters
        assembleTypeParams(m_mapParams, out);
        writePackedLong(out, Constant.indexOf(m_constPath));
        }

    @Override
    public Iterator<? extends XvmStructure> getContained()
        {
        // we cannot use the "collectAnnotations" API at this time, since our module may not yet
        // be linked
        List<Annotation> listAnno = null;
        for (Contribution contrib : getContributionsAsList())
            {
            if (contrib.getComposition() == Composition.Annotation)
                {
                if (listAnno == null)
                    {
                    listAnno = new ArrayList<>();
                    }
                listAnno.add(contrib.getAnnotation());
                }
            }

        return listAnno == null
                ? super.getContained()
                : new LinkedIterator(
                        super.getContained(),
                        listAnno.iterator());
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getDescription())
          .append(", type-params=");

        final ListMap<StringConstant, TypeConstant> map = m_mapParams;
        if (map == null || map.size() == 0)
            {
            sb.append("none");
            }
        else
            {
            sb.append('<');
            boolean fFirst = true;
            for (Map.Entry<StringConstant, TypeConstant> entry : map.entrySet())
                {
                if (fFirst)
                    {
                    fFirst = false;
                    }
                else
                    {
                    sb.append(", ");
                    }

                sb.append(entry.getKey().getValue());

                TypeConstant constType = entry.getValue();

                // if constType is unresolved, we cannot call constType.isEcstasy("Object")
                if (!constType.getValueString().equals("ecstasy:Object"))
                    {
                    sb.append(" extends ")
                      .append(constType);
                    }
                }
            sb.append('>');
            }

        sb.append(", source-path=")
          .append(m_constPath == null ? "none" : m_constPath.getValueString());

        return sb.toString();
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Helper method to read a collection of type parameters.
     *
     * @param in  the DataInput containing the type parameters
     *
     * @return null if there are no type parameters, otherwise a map from CharStringConstant to the
     *         type constraint for each parameter
     *
     * @throws IOException  if an I/O exception occurs during disassembly from the provided
     *                      DataInput stream, or if there is invalid data in the stream
     */
    protected ListMap<StringConstant, TypeConstant> disassembleTypeParams(DataInput in)
        throws IOException
        {
        int c = readMagnitude(in);
        if (c <= 0)
            {
            assert c == 0;
            return null;
            }

        ListMap<StringConstant, TypeConstant> map = new ListMap<>();
        ConstantPool pool = getConstantPool();
        for (int i = 0; i < c; ++i)
            {
            StringConstant constName = (StringConstant) pool.getConstant(readIndex(in));
            TypeConstant   constType = (TypeConstant)   pool.getConstant(readIndex(in));
            assert !map.containsKey(constName);
            map.put(constName, constType);
            }
        return map;
        }

    /**
     * Register all the constants associated with a list of type parameters.
     *
     * @param mapOld  the map containing the type parameters
     *
     * @return the map of registered type parameters (might be different from the map passed in)
     */
    protected ListMap<StringConstant, TypeConstant> registerTypeParams(ListMap<StringConstant, TypeConstant> mapOld)
        {
        if (mapOld == null || mapOld.isEmpty())
            {
            return mapOld;
            }

        ConstantPool                          pool   = getConstantPool();
        ListMap<StringConstant, TypeConstant> mapNew = mapOld;
        for (Map.Entry<StringConstant, TypeConstant> entry : mapOld.entrySet())
            {
            StringConstant constOldKey = entry.getKey();
            StringConstant constNewKey = (StringConstant) pool.register(constOldKey);

            TypeConstant   constOldVal = entry.getValue();
            TypeConstant   constNewVal = (TypeConstant) pool.register(constOldVal);

            if (mapNew != mapOld || constOldKey != constNewKey)
                {
                if (mapNew == mapOld)
                    {
                    // up to this point, we've been using the old map, but now we need to change a
                    // key (which map does not support), so create a new map, and copy the old map
                    // to the new map, but only up to (but not including!) the current entry
                    mapNew = new ListMap<>();
                    for (Map.Entry<StringConstant, TypeConstant> entryCopy : mapOld.entrySet())
                        {
                        if (entryCopy.getKey() == constOldKey)
                            {
                            break;
                            }

                        mapNew.put(entryCopy.getKey(), entryCopy.getValue());
                        }
                    }

                mapNew.put(constNewKey, constNewVal);
                }
            else if (constOldVal != constNewVal)
                {
                entry.setValue(constNewVal);
                }
            }
        return mapNew;
        }

    /**
     * Helper method to write type parameters to the DataOutput stream.
     *
     * @param map  the type parameters
     * @param out  the DataOutput to write the XVM structure to
     *
     * @throws IOException  if an I/O exception occurs during assembly to the provided DataOutput
     *                      stream
     */
    protected void assembleTypeParams(ListMap<StringConstant, TypeConstant> map, DataOutput out)
        throws IOException
        {
        int c = map == null ? 0 : map.size();
        writePackedLong(out, c);

        if (c == 0)
            {
            return;
            }

        for (Map.Entry<StringConstant, TypeConstant> entry : map.entrySet())
            {
            writePackedLong(out, entry.getKey().getPosition());
            writePackedLong(out, entry.getValue().getPosition());
            }
        }

    /**
     * Assuming that this component is a class containing nested members, and using a value
     * previously returned from {@link IdentityConstant#getNestedIdentity()} that would be usable
     * in relation to this class, determine the nested component that the identity refers to.
     *
     * @param id  a nested identity, which is null, or a String name, or a SignatureConstant, or an
     *            instance of NestedIdentity
     *
     * @return the specified component, or null
     */
    public Component getNestedChild(Object id)
        {
        // a null identity indicates the class itself
        if (id == null)
            {
            return this;
            }

        // immediately-nested properties are identified by using only a string name
        if (id instanceof String sName)
            {
            return getChild(sName);
            }

        // immediately-nested multi-method/method combinations are identified by using only a sig
        if (id instanceof SignatureConstant sig)
            {
            return findMethod(sig);
            }

        return ((NestedIdentity) id).getIdentityConstant().relocateNestedIdentity(this);
        }

    /**
     * Create (if necessary) a synthetic method for the specified signature at this synthetic class.
     *
     * @param sig  the SignatureConstant for the synthetic method
     *
     * @return the corresponding method
     */
    public MethodStructure ensureSyntheticMethod(SignatureConstant sig)
        {
        assert isSynthetic();

        MethodStructure method = findMethod(sig);
        if (method == null)
            {
            ConstantPool   pool        = getConstantPool();
            TypeConstant[] atypeParam  = sig.getRawParams();
            TypeConstant[] atypeReturn = sig.getRawReturns();
            int            cParams     = atypeParam.length;
            int            cReturns    = atypeReturn.length;

            Parameter[] aParam = new Parameter[cParams];
            for (int i = 0; i < cParams; i++)
                {
                aParam[i] = new Parameter(pool, atypeParam[i], "p"+i, null, false, i, false);
                }

            Parameter[] aReturn = new Parameter[cReturns];
            for (int i = 0; i < cReturns; i++)
                {
                aReturn[i] = new Parameter(pool, atypeReturn[i], null, null, true, i, false);
                }
            method = createMethod(/*function*/ false, Access.PUBLIC, null, aReturn,
                            sig.getName(), aParam, /*hasCode*/ false, /*usesSuper*/ false);
            method.setSynthetic(true);
            }
        return method;
        }

    /**
     * Create (if necessary) a synthetic property for the specified name and type.
     *
     * @param sName  the property name
     * @param type   the property type
     *
     * @return the corresponding property
     */
    public PropertyStructure ensureSyntheticProperty(String sName, TypeConstant type)
        {
        assert isSynthetic();

        PropertyStructure prop = (PropertyStructure) getChild(sName);
        if (prop == null)
            {
            prop = createProperty(false, Access.PUBLIC, Access.PUBLIC, type, sName);
            }
        return prop;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof ClassStructure that) || !super.equals(obj))
            {
            return false;
            }

        // type parameters
        Map mapThisParams = this.m_mapParams;
        Map mapThatParams = that.m_mapParams;
        int cThisParams   = mapThisParams == null ? 0 : mapThisParams.size();
        int cThatParams   = mapThatParams == null ? 0 : mapThatParams.size();

        return cThisParams == cThatParams
            && (cThisParams == 0 || mapThisParams.equals(mapThatParams))
            && Handy.equals(this.m_constPath, that.m_constPath);
        }

    /**
     * If there are any undeclared formal parameters for the "extend", "implement" or "into"
     * contributions for this mixin, add them to the component's formal parameter list.
     *
     * @param pool  the ConstantPool to use
     * @param errs  the error listener
     */
    public void addImplicitTypeParameters(ConstantPool pool, ErrorListener errs)
        {
        assert getFormat() == Format.MIXIN;

        ListMap<String, TypeConstant> mapTypeParams = null;
        for (Contribution contrib : getContributionsAsList())
            {
            switch (contrib.getComposition())
                {
                case Extends:
                case Implements:
                case Incorporates:
                case Into:
                    break;

                default:
                    // disregard any other contribution
                    continue;
                }

            TypeConstant typeContrib = contrib.getTypeConstant();
            if (typeContrib.isParamsSpecified())
                {
                continue;
                }

            TypeConstant[] atypeGeneric = typeContrib.collectGenericParameters();
            if (atypeGeneric == null)
                {
                continue;
                }

            if (atypeGeneric.length == 0)
                {
                // there is a possibility that we have not yet had a chance to add implicit
                // parameters for the underlying mixin; do it now
                if (typeContrib.isSingleUnderlyingClass(false) &&
                        typeContrib.getExplicitClassFormat() == Format.MIXIN)
                    {
                    ClassStructure clzContrib = (ClassStructure)
                            typeContrib.getSingleUnderlyingClass(false).getComponent();
                    clzContrib.addImplicitTypeParameters(pool, errs);

                    atypeGeneric = typeContrib.collectGenericParameters();
                    if (atypeGeneric.length == 0)
                        {
                        continue;
                        }
                    // implicit parameters were added to the contribution type; proceed
                    }
                else
                    {
                    continue;
                    }
                }

            if (mapTypeParams == null)
                {
                mapTypeParams = new ListMap<>();
                }

            // collect the formal types and verify that there are no collisions
            for (TypeConstant typeParam : atypeGeneric)
                {
                assert typeParam.isGenericType();

                PropertyConstant idParam        = (PropertyConstant) typeParam.getDefiningConstant();
                String           sName          = idParam.getName();
                TypeConstant     typeConstraint = idParam.getConstraintType();

                TypeConstant typeOld = mapTypeParams.get(sName);
                if (typeOld == null)
                    {
                    mapTypeParams.put(sName, typeConstraint);
                    }
                else if (!typeOld.equals(typeConstraint))
                    {
                    // {0} type parameter {1} must be of type {2}, but has been specified as {3} by {4}
                    log(errs, Severity.ERROR, VE_TYPE_PARAM_INCOMPATIBLE_TYPE,
                        getName(), typeOld.getValueString(),
                        typeConstraint.getValueString(), contrib.getTypeConstant().getValueString());
                    return;
                    }
                }

            // update the contribution
            contrib.narrowType(typeContrib.adoptParameters(pool, atypeGeneric));
            }

        if (mapTypeParams != null)
            {
            for (Map.Entry<String, TypeConstant> entry : mapTypeParams.entrySet())
                {
                addTypeParam(entry.getKey(), entry.getValue())
                         .setSynthetic(true);
                }
            }
        }


    // ----- inner class: SimpleTypeResolver -------------------------------------------------------

    /**
     * Generic type resolver based on the actual parameter list.
     */
    public class SimpleTypeResolver
            implements GenericTypeResolver
        {
        /**
         * Create a TypeResolver based on the actual type list.
         *
         * @param pool        the ConstantPool to use
         * @param listActual  the actual type list
         */
        public SimpleTypeResolver(ConstantPool pool, List<TypeConstant> listActual)
            {
            f_pool       = pool;
            m_listActual = listActual;

            IdentityConstant id = getIdentityConstant();
            if (!id.equals(pool.clzTuple()) && !id.equals(pool.clzCondTuple()))
                {
                int cFormal = getTypeParamCount();
                int cActual = listActual.size();
                if (cFormal < cActual)
                    {
                    // soft assert; this should have already been reported
                    System.err.println("Too many parameters: " + listActual +
                                       " passed to " + ClassStructure.this);
                    }
                else if (cFormal > cActual)
                    {
                    m_listActual = listActual = new ArrayList<>(listActual); // clone
                    List<Map.Entry<StringConstant, TypeConstant>> entries = getTypeParamsAsList();

                    // fill the missing actual parameters with the canonical constraint types
                    // Note: since there is a possibility of Tuple self-reference
                    // (e.g. Tuple<ElementTypes extends Tuple<ElementTypes...>>)
                    // we'll prime each args with Object for now
                    for (int i = cActual; i < cFormal; i++)
                        {
                        listActual.add(pool.typeObject());
                        }

                    for (int i = cActual; i < cFormal; i++)
                        {
                        // the constraint type itself could be formal, depending on another parameter
                        TypeConstant typeConstraint = entries.get(i).getValue();
                        listActual.set(i,  typeConstraint.containsUnresolved()   ||
                                          !typeConstraint.containsFormalType(true)
                                ? typeConstraint
                                : typeConstraint.isFormalTypeSequence()
                                    ? pool.typeTuple0()
                                    : typeConstraint.resolveGenerics(pool, this));
                        }
                    }
                }
            }

        @Override
        public TypeConstant resolveGenericType(String sFormalName)
            {
            return extractGenericType(f_pool, sFormalName, m_listActual);
            }

        private final ConstantPool f_pool;
        private List<TypeConstant> m_listActual;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The name-to-type information for type parameters. The type constant is used to specify a
     * type constraint for the parameter.
     */
    private ListMap<StringConstant, TypeConstant> m_mapParams;

    /**
     * The file name that indicates the location from which this class was compiled. This file name
     * is in network format ('/'-delimited) and relative to the directory which contains the module.
     * @see ModuleStructure#getSourceDir()
     */
    private LiteralConstant m_constPath;

    /**
     * Cached formal type.
     */
    private transient TypeConstant m_typeFormal;

    /**
     * Cached canonical type.
     */
    private transient TypeConstant m_typeCanonical;

    /**
     * Cached information about this class concurrency.
     */
    private transient ConcurrencySafety m_safety;

    /**
     * A cached array of the annotations that apply to the class itself.
     */
    private transient Annotation[] m_aAnnoClass;

    /**
     * A cached array of the annotations that represent mixins.
     */
    private transient Annotation[] m_aAnnoMixin;
    }