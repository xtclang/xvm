package org.xvm.asm.constants;


import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant.Origin;

import org.xvm.util.ListMap;


/**
 * Represents the "flattened" information about a type.
 * <p/>
 * An implementation may fully realize the information or provide a view of another TypeInfo.
 */
public abstract class TypeInfo {
    /**
     * Create a new TypeInfo that represents a more limited (public or protected) access to the
     * members of this private type.
     *
     * @param access  the desired access, either PUBLIC or PROTECTED
     *
     * @return a new TypeInfo
     */
    public abstract TypeInfo limitAccess(Access access);

    /**
     * @param setFromInto  the pre-calculated graph of identity nodes that form the "into" and omit
     *                     the mixin (preventing circularity); never null
     *
     * @return the information from this TypeInfo, but excluding members from the passed set
     */
    public abstract TypeInfo excluding(Set<IdentityConstant> setFromInto);

    /**
     * @param setFromInto  the pre-calculated graph of identity nodes that form the "into" and omit
     *                     the mixin (preventing circularity), or null to include everything
     *
     * @return the "into" version of this TypeInfo
     */
    public abstract TypeInfo asInto(Set<IdentityConstant> setFromInto);

    /**
     * @return the "delegates" version of this TypeInfo
     */
    public abstract TypeInfo asDelegates();

    /**
     * Create a NakedRef TypeInfo for the specified referent type.
     *
     * @param pool          the ConstantPool creating this NakedRef (required)
     * @param typeReferent  the "referent type" (required)
     * @param resolver      the TypeResolver to use, or null
     *
     * @return the TypeInfo for the NakedRef of the specified referent type
     */
    public abstract TypeInfo asNakedRef(
            ConstantPool        pool,
            TypeConstant        typeReferent,
            GenericTypeResolver resolver);

    /**
     * Contribute this TypeInfo's knowledge of potential call chain information to another deriving
     * type's TypeInfo information.
     *
     * @param listmapClassChain   the class chain being collected for the derivative type
     * @param listmapDefaultChain the default chain being collected for the derivative type
     * @param listmapRootChain    default chain for types covered by isRootInterface()
     * @param composition         the composition of the contribution
     */
    public abstract void contributeChains(
            ListMap<IdentityConstant, Origin> listmapClassChain,
            ListMap<IdentityConstant, Origin> listmapDefaultChain,
            ListMap<IdentityConstant, Origin> listmapRootChain,
            Composition                       composition);

    /**
     * Add the types this TypeInfo depends on to the specified set.
     *
     * @param setDepends (optional) the set to add the types to
     */
    public abstract Set<TypeConstant> collectDependTypes(Set<TypeConstant> setDepends);

    /**
     * @return true iff the progress of this TypeInfo depends on the specified type resolution
     */
    public abstract boolean dependsOn(TypeConstant type);

    /**
     * @return the type that the TypeInfo represents
     */
    public abstract TypeConstant getType();

    abstract int getInvalidationCount();

    /**
     * Determine if this TypeInfo is impacted by changes in the TypeInfos built for any of the
     * classes specified by the passed set of IdentityConstants.
     *
     * @param setModified  the set of class IdentityConstants whose TypeInfos may have changed
     *
     * @return true iff this TypeInfo depends on any of the specified IdentityConstants for its
     *         contents, or if the info needs to be rebuilt for another reason
     */
    public abstract boolean needsRebuild(Set<IdentityConstant> setModified);

    /**
     * @return the ClassStructure, or null if none is available; a non-abstract type will always
     *         have a ClassStructure (unless it's a virtual child "projection")
     */
    public abstract ClassStructure getClassStructure();

    /**
     * @return an identity of ths structure this info represents (can be null for relational types)
     */
    public IdentityConstant getIdentity() {
        TypeConstant type = getType();
        if (type instanceof PropertyClassTypeConstant typeProp) {
            return typeProp.getProperty();
        }

        ClassStructure struct = getClassStructure();
        return struct == null ? null : struct.getIdentityConstant();
    }

    /**
     * @return the format of the topmost structure that the TypeConstant refers to, or
     *         {@code INTERFACE} for any non-class type (such as a difference type)
     */
    public abstract Format getFormat();

    /**
     * @return true iff this type is explicitly abstract
     */
    public abstract boolean isExplicitlyAbstract();

    /**
     * @return true iff this type is abstract, which is always true for an interface, and may be
     *         true for a class or mixin
     */
    public abstract boolean isAbstract();

    /**
     * @return true iff this type is static (a static global type is a singleton; a static local
     *         type does not hold a reference to its parent)
     */
    public abstract boolean isStatic();

    /**
     * @return true if this type represents a singleton instance of a class
     */
    public abstract boolean isSingleton();

    /**
     * @return true if this type represents a singleton instance of a class
     */
    public abstract boolean isSynthetic();

    /**
     * @return true iff this is a class type, which is not an interface, annotation or mixin type
     */
    public boolean isClass() {
        return switch (getFormat()) {
            case MODULE,
                 PACKAGE,
                 CLASS,
                 CONST,
                 ENUM,
                 ENUMVALUE,
                 SERVICE -> true;
            default -> false;
        };
    }

    /**
     * Check if this type can be instantiated.
     * <p/>
     * Note, that a virtual child that is not explicitly marked as @Abstract is always assumed to be
     * instantiatable, since any abstract aspects of the class could be implemented by its virtual
     * sub-classes at the parent's sub level.
     * <p/>
     * The actual check is always done at the parent's level, so for a parent class to be "newable",
     * all the virtual children have to be non-abstract.
     *
     * @param fSingleton  if true, don't disallow singletons, but check for the default constructor
     *                    instead
     *
     * @return true iff this is a type that can be instantiated
     */
    public abstract boolean isNewable(boolean fSingleton, ErrorListener errs);

    /**
     * Report one or more reasons why this type is "not newable".
     *
     * @param sTarget     the name of the type that is being new'd
     * @param sChild      (optional) a child name
     * @param fSingleton  if true, report an absence of the default constructor
     * @param errs        the error listener
     */
    public abstract void reportNotNewable(
            String        sTarget,
            String        sChild,
            boolean       fSingleton,
            ErrorListener errs);

    /**
     * @return true iff this class is considered to be "top level"
     */
    public abstract boolean isTopLevel();

    /**
     * @return true iff this class is scoped within another class, such that it requires a parent
     *         reference in order to be instantiated
     */
    public abstract boolean isVirtualChildClass();

    /**
     * @return true iff this class is an anonymous inner class
     */
    public abstract boolean isAnonInnerClass();

    /**
     * @return the complete set of type parameters declared within the type
     */
    public abstract Map<Object, ParamInfo> getTypeParams();

    /**
     * @return true iff this type has any formal type parameters
     */
    public abstract boolean hasGenericTypes();

    /**
     * @return the type annotations that had an "into" clause of "Class"
     */
    public abstract Annotation[] getClassAnnotations();

    /**
     * @return the "regular" annotations (not incorporation)
     */
    public abstract Annotation[] getMixinAnnotations();

    /**
     * @return the TypeConstant representing the "native rebase" type
     */
    public abstract TypeConstant getRebases();

    /**
     * @return the TypeConstant representing the super class
     */
    public abstract TypeConstant getExtends();

    /**
     * @return the TypeConstant representing the "mixin into" type for an annotation or mixin;
     *         null otherwise
     */
    public abstract TypeConstant getInto();

    /**
     * @return the list of contributions that made up this TypeInfo
     */
    public abstract List<Contribution> getContributionList();

    /**
     * @return the potential call chain of classes
     */
    public abstract ListMap<IdentityConstant, Origin> getClassChain();

    /**
     * @return the potential default call chain of interfaces
     */
    public abstract ListMap<IdentityConstant, Origin> getDefaultChain();

    /**
     * Calculate a child type for a given name.
     *
     * @param sName  the name of the child
     *
     * @return the type of the typedef, a virtual child or null if neither exists
     */
    public abstract TypeConstant calculateChildType(ConstantPool pool, String sName);

    /**
     * @return all the properties for this type, indexed by their "flattened" property constant
     */
    public abstract Map<PropertyConstant, PropertyInfo> getProperties();

    /**
     * @return all properties for this type sorted by their {@link PropertyInfo#getRank() rank}
     */
    public abstract Entry<PropertyConstant, PropertyInfo>[] sortedProperties();

    /**
     * @return virtual properties keyed by nested id
     */
    public abstract Map<Object, PropertyInfo> getVirtProperties();

    /**
     * @return all the properties for this type that can be identified by a simple name, indexed
     *         by that name
     */
    public abstract Map<String, PropertyInfo> ensurePropertiesByName();

    /**
     * Obtain all the properties declared within the specified method.
     *
     * @param idMethod  the identity of the method that may contain properties
     *
     * @return a map from property name to PropertyInfo
     */
    public abstract Map<String, PropertyInfo> ensureNestedPropertiesByName(
            MethodConstant idMethod);

    /**
     * Obtain all of the properties declared within the specified property.
     * REVIEW this implementation is probably insufficient, considering possible visibility rules
     *
     * @param idProp  the identity of the property that may contain properties
     *
     * @return a map from property name to PropertyInfo
     */
    public abstract Map<String, PropertyInfo> ensureNestedPropertiesByName(
            PropertyConstant idProp);

    /**
     * Look up any of the following (in that order):
     * <ol>
     *   <li>a property;</li>
     *   <li>a method;</li>
     *   <li>a child class;</li>
     * </ol>
     * Note: if more than one method with the specified name exists, a MultiMethodConstant is
     *       returned.
     *
     * @param pool   the ConstantPool to use
     * @param sName  the name to look for
     *
     * @return an IdentityConstant representing a component of that name or null if none found
     */
    public abstract IdentityConstant findName(ConstantPool pool, String sName);

    /**
     * Look up the property by its name.
     *
     * @param sName  the property name
     *
     * @return the PropertyInfo for the specified constant, or null
     */
    public PropertyInfo findProperty(String sName) {
        return ensurePropertiesByName().get(sName);
    }

    /**
     * Look up the property by its identity constant.
     *
     * @param id  the constant that identifies the property
     *
     * @return the PropertyInfo for the specified constant, or null
     */
    public PropertyInfo findProperty(PropertyConstant id) {
        return findProperty(id, false);
    }

    /**
     * Implementation of "findProperty" above, allowing for a duck-typed properties at runtime.
     */
    public abstract PropertyInfo findProperty(PropertyConstant id, boolean fRuntime);

    /**
     * Look up the property by its nested identity.
     * <p/>
     * Note: this lookup is not cached since the results are always cached by the caller.
     *
     * @param nid  the id (String | NestedIdentity)
     *
     * @return the PropertyInfo for the specified constant, or null
     */
    public abstract PropertyInfo findPropertyByNid(Object nid);

    /**
     * @return all non-scoped methods for this type
     */
    public abstract Map<MethodConstant, MethodInfo> getMethods();

    /**
     * @return all methods for this type sorted by their {@link MethodInfo#getRank() rank}
     */
    public abstract Entry<MethodConstant, MethodInfo>[] sortedMethods();

    /**
     * @return virtual methods keyed by nested id
     */
    public abstract Map<Object, MethodInfo> getVirtMethods();

    /**
     * @return all the methods for this type that can be identified by just a signature, indexed
     *         by that signature
     */
    public abstract Map<SignatureConstant, MethodInfo> ensureMethodsBySignature();

    /**
     * Find the MethodInfo for the specified SignatureConstant. If possible, find
     * a non-capped method; return a capped one *only* if nothing else matches.
     *
     * @param sig  a SignatureConstant
     *
     * @return the MethodInfo corresponding to the specified identity
     */
    public MethodInfo getMethodBySignature(SignatureConstant sig) {
        return getMethodBySignature(sig, getType(), false);
    }

    /**
     * Find the MethodInfo for the specified SignatureConstant, on behalf of the specified context
     * type. If possible, find a non-capped method; return a capped one *only* if nothing else
     * matches.
     *
     * @param sig       a SignatureConstant
     * @param typeThis  the context type
     *
     * @return the MethodInfo corresponding to the specified identity
     */
    public MethodInfo getMethodBySignature(SignatureConstant sig, TypeConstant typeThis) {
        return getMethodBySignature(sig, typeThis, false);
    }

    /**
     * Same as the method above, but allowing for relaxed run-time matching rules.
     *
     * Warning: Do NOT use this method to find "regular" (non-virtual) constructors.
     *
     * @param sig       a SignatureConstant to find the method for
     * @param fRuntime  true iff this method is called by the runtime chain computation logic
     *
     * @return the MethodInfo corresponding to the specified identity
     */
    public MethodInfo getMethodBySignature(SignatureConstant sig, boolean fRuntime) {
        return getMethodBySignature(sig, getType(), fRuntime);
    }

    public abstract MethodInfo getMethodBySignature(
            SignatureConstant sig,
            TypeConstant      typeThis,
            boolean           fRuntime);

    /**
     * Find the MethodInfo for the specified MethodConstant identity.
     *
     * @param id  a MethodConstant identity
     *
     * @return the MethodInfo corresponding to the specified identity
     */
    public MethodInfo getMethodById(MethodConstant id) {
        return getMethodById(id, false);
    }

    /**
     * Same as the method above, but allowing for relaxed run-time matching rules.
     *
     * @param id        a MethodConstant identity
     * @param fRuntime  true iff this method is called by the runtime chain computation logic
     *
     * @return the MethodInfo corresponding to the specified identity
     */
    public abstract MethodInfo getMethodById(MethodConstant id, boolean fRuntime);

    /**
     * Find the MethodInfo for the specified nested identity.
     *
     * @param nid  a nested identity, as obtained from {@link MethodConstant#getNestedIdentity}
     *             or {@link IdentityConstant#resolveNestedIdentity}
     *
     * @return the specified MethodInfo, or null if no MethodInfo could be found by the provided
     *         nested identity
     */
    public MethodInfo getMethodByNestedId(Object nid) {
        return getMethodByNestedId(nid, false);
    }

    /**
     * Same as the method above, but allowing for relaxed run-time matching rules.
     *
     * @param nid       a nested identity, as obtained from {@link MethodConstant#getNestedIdentity}
     *                  or {@link IdentityConstant#resolveNestedIdentity}
     * @param fRuntime  true iff this method is called by the runtime chain computation logic
     *
     * @return the specified MethodInfo, or null if no MethodInfo could be found by the provided
     *         nested identity
     */
    public abstract MethodInfo getMethodByNestedId(Object nid, boolean fRuntime);

    /**
     * Obtain the method chain for the specified method.
     *
     * @param id  the MethodConstant for the method
     *
     * @return the method chain iff the method exists; otherwise null
     */
    public abstract MethodBody[] getOptimizedMethodChain(MethodConstant id);

    /**
     * Obtain the method chain for the specified method.
     *
     * @param nid  the nested id for the method
     *
     * @return the method chain iff the method exists; otherwise null
     */
    public abstract MethodBody[] getOptimizedMethodChain(Object nid);

    /**
     * Obtain the method chain for the property getter for the specified property id.
     *
     * @param id  the property id
     *
     * @return the method chain iff the property exists; otherwise null
     */
    public abstract MethodBody[] getOptimizedGetChain(PropertyConstant id);

    /**
     * Obtain the method chain for the property setter for the specified property id.
     *
     * @param id  the property id
     *
     * @return the method chain iff the property exists and is a Var; otherwise null
     */
    public abstract MethodBody[] getOptimizedSetChain(PropertyConstant id);

    /**
     * Find a named method or function that best matches the specified requirements.
     *
     * @param sName       the name of the method or function
     * @param fMethod     true to include methods in the search
     * @param fFunction   true to include functions in the search
     * @param aRedundant  an optional array of redundant return type information (helps to clarify
     *                    which method or function to select)
     * @param aArgs       an optional array of the types of the arguments being provided (some of
     *                    which may be null to indicate "unknown" in a pre-validation stage, or
     *                    "non-binding unknown")
     *
     * @return the id of a matching method or function (null if none found)
     */
    public abstract MethodConstant findCallable(
            String         sName,
            boolean        fMethod,
            boolean        fFunction,
            TypeConstant[] aRedundant,
            TypeConstant[] aArgs);

    /**
     * Find a constructor that best matches the specified requirements.
     *
     * @param aArgs  the types of the arguments being provided (some of which may be null to
     *               indicate "unknown" in a pre-validation stage, or "non-binding unknown")
     *
     * @return the matching constructor id (null if none found)
     */
    public MethodConstant findConstructor(TypeConstant... aArgs) {
        return findCallable("construct", false, false, TypeConstant.NO_TYPES, aArgs);
    }

    /**
     * Find a virtual constructor that best matches the specified signature.
     *
     * Note: this method is used only by the runtime.
     *
     * @param sig  the virtual constructor signature
     *
     * @return the matching constructor info (null if none found)
     */
    public abstract MethodInfo findVirtualConstructor(SignatureConstant sig);

    /**
     * See if any method has the specified name.
     *
     * @param sName  a method name
     *
     * @return true if the type contains at least one method (or function) by the specified name
     */
    public abstract boolean containsMultiMethod(String sName);

    /**
     * Check if there is any method with the specified name inside of the container
     * property or method.
     *
     * @param idContainer  the id of the property or method to look inside of
     * @param sName        a method name to look for
     *
     * @return true if the property contains at least one method (or function) by the specified name
     */
    public abstract boolean containsNestedMultiMethod(
            IdentityConstant idContainer,
            String           sName);

    /**
     * Obtain all the matching op methods for the specified name and/or the operator string, that
     * take the specified number of params.
     *
     * @param sName   the default op name, such as "add" (optional)
     * @param sOp     the operator string, such as "+" (optional)
     * @param cParams the number of parameters for the operator method, or -1 to match any
     *
     * @return a set of zero or more method constants
     */
    public abstract Set<MethodConstant> findOpMethods(String sName, String sOp, int cParams);

    /**
     * Obtain the matching op method for the specified name and/or the operator string, that
     * take the specified number of params.
     *
     * Note: this method is quite similar to {@link org.xvm.runtime.ClassTemplate#findOpChain}
     *
     * @param sName    the default op name, such as "add" (optional)
     * @param sOp      the operator string, such as "+" (optional)
     * @param typeArg  the type of the first operator method parameter (optional)
     *
     * @throws IllegalStateException if there is no matching or more than one matching methods
     */
    public abstract MethodInfo findOpMethod(
            String       sName,
            String       sOp,
            TypeConstant typeArg);

    /**
     * @return resolved method constant, which may be synthetic (not pointing to a structure)
     */
    public abstract MethodConstant resolveMethodConstant(MethodInfo method);

    /**
     * Obtain all the matching methods for the specified name and the number of parameters.
     * <p/>
     * Note: the returned method constants could be synthetic and with auto-narrowing resolved.
     *
     * @param sName    the method name
     * @param cParams  the number of parameters (-1 for any)
     * @param kind     the kind of methods to consider
     *
     * @return a set of zero or more method constants
     */
    public abstract Set<MethodConstant> findMethods(
            String     sName,
            int        cParams,
            MethodKind kind);

    /**
     * Obtain all methods with specified name and the number of parameters inside the container
     * property or method.
     *
     * @param idContainer  the id of the container property ir method
     * @param sName        the method name to look for
     * @param cParams      the number of parameters (-1 for any)
     *
     * @return a set of zero or more method constants
     */
    public abstract Set<MethodConstant> findNestedMethods(
            IdentityConstant idContainer,
            String           sName,
            int              cParams);

    /**
     * Get the method that the specified capped method is narrowed by.
     *
     * @param methodCapped  a capped method
     *
     * @return the narrowing method (should never be null after the construction - see validateCapped())
     */
    public abstract MethodInfo getNarrowingMethod(MethodInfo methodCapped);

    /**
     * Obtain all the auto conversion methods found on this type.
     *
     * @return a set of zero or more method constants
     */
    public abstract Set<MethodInfo> getAutoMethodInfos();

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
    public abstract MethodConstant findConversion(TypeConstant typeDesired);

    /**
     * @return a map of information about child types of this type, keyed by name
     */
    public abstract ListMap<String, ChildInfo> getChildInfosByName();

    /**
     * Render a one-line header describing this TypeInfo: identity, progress, format and flags.
     * <p/>
     * This method is called IMPLICITLY - by string concatenation, and by an IDE debugger rendering
     * a row in the Variables view - so it must be pure: no member walk, no
     * {@code resolveNestedIdentity} (which interns into the shared ConstantPool), no method-chain
     * optimization, and no reach for the ambient pool. The full member dump lives on {@link
     * #dump(boolean)}, which is only ever reached by naming it.
     * <p/>
     * Declared abstract so that a new subclass cannot silently inherit a member-walking
     * {@code toString()}.
     */
    @Override
    public abstract String toString();

    /**
     * Render the full member dump of this TypeInfo. Unlike {@link #toString()} this is a
     * deliberate, explicitly-named diagnostic: it walks every member, resolves nested identities
     * (which interns into the shared ConstantPool), and - when {@code fRuntime} is set - computes
     * and CACHES optimized method chains. Never call it from a display path.
     *
     * @param fRuntime  if specified, optimize the method call chains
     */
    public abstract String dump(boolean fRuntime);

    protected abstract Progress getProgress();

    protected abstract boolean isPlaceHolder();

    /**
     * Indicates that this TypeInfo may have been retained despite errors encountered during the
     * TypeInfo computation. Those errors could be transient, caused by the insufficient information
     * from the type contributions (most likely annotations).
     */
    public abstract void markWithError();

    /**
     * @return true iff there were errors encountered during the TypeInfo computation
     */
    public abstract boolean hasErrors();

    /**
     * Helper: validate the integrity of all "capped" methods.
     *
     * @return null if all is good; a first offending method otherwise
     */
    public abstract MethodInfo validateCapped();

    public static boolean containsAnnotation(Annotation[] annotations, String sName) {
        if (annotations == null || annotations.length == 0) {
            return false;
        }

        IdentityConstant clzFind =
                annotations[0].getConstantPool().getImplicitlyImportedIdentity(sName);
        for (Annotation annotation : annotations) {
            if (annotation.getAnnotationClass().equals(clzFind)) {
                return true;
            }
        }

        return false;
    }

    public enum Progress {
        // the ordinal values are significant: place-holder=1, incomplete=2, complete=3
        Absent, Building, Incomplete, Complete;

        public Progress worstOf(Progress that) {
            return this.ordinal() > that.ordinal() ? that : this;
        }
    }

    public enum MethodKind {
        Constructor("c"), Method("m"), Function("f"), Any("a");

        MethodKind(String key) {
            this.key = key;
        }

        public final String key;

        public boolean matches(MethodStructure method) {
            return switch (this) {
                case Constructor -> method.isConstructor();
                case Method      -> !method.isFunction() && !method.isConstructor();
                case Function    -> method.isFunction();
                case Any         -> true;
            };
        }

        public boolean matches(MethodInfo method) {
            return switch (this) {
                case Constructor -> method.isConstructor();
                case Method      -> !method.isFunction() && !method.isCtorOrValidator();
                case Function    -> method.isFunction();
                case Any         -> true;
            };
        }
    }
}
