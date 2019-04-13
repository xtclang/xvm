import collections.ListMap;

import types.MultiMethod;
import types.MultiFunction;
import types.TypeParameter;

/**
 * A Class represents the following information about an Ecstasy type composition:
 * * Every class is of a specific {@link Category}, one of Module, Package, Class, Const, Enum,
 *   Service, Mixin, or Interface.
 * * Every class has an identity that begins with a module, identifies the location of the class
 *   within the module hierarchical structure, and provides the class with a name that is unique
 *   within its localized namespace in that hierarchy.
 * * A class can be a _parameterized type_.
 * * If the class is a {@code mixin}, a class is defined to which the {@code mixin} can apply.
 * * A class is composed through a series of steps, each of which identifies another class that is
 *   _extended by_, _implemented by_, or _mixed into_ this class.
 * * A class is further defined by the inclusion of child classes, properties, methods, functions,
 *   and literals (named constants).
 * * A class may be marked as abstract, as a singleton, or as an inner class.
 * * A class may identify the file name containing the source code for the class.
 *
 * A number of operations can be used to obtain a new class from an existing class:
 * * A class with type parameters can _narrow_ those type parameters to form a new class.
 * * A class can incorporate an applicable {@code mixin} to form a new class.
 *
 * The class is also the source of instances (objects) of the class:
 * * A class can be used to create a new instance (an object) of a non-singleton class.
 * * A class can be used to obtain the one instance (an object) of a singleton class.
 *
 * Unfortunately, Class cannot be declared as a {@code const} because of the potential for circular
 * references. (The property values of a {@code const} are fully known and immutable before the
 * {@code const} object even has a "`this`"; as a result, it is impossible to create circular
 * references using {@code const} classes.)
 */
const Class<PublicType, ProtectedType extends PublicType, PrivateType extends ProtectedType, StructType extends Struct>
        incorporates conditional Enumeration<PublicType extends Enum>
    {
    // ----- data types ----------------------------------------------------------------------------

    /**
     * A class exists within a namespace. The namespace can be one of several Ecstasy language
     * structures.
     */
    typedef Module | Package | Class<> | Property | Method | Function Namespace;

    /**
     * A class is of a given category of Ecstasy language structures. These categories are not
     * entirely discrete; an Enum, for example, is a Const.
     */
    enum Category {MODULE, PACKAGE, CLASS, CONST, ENUM, SERVICE, MIXIN, INTERFACE}

    /**
     * A class contains other named child structures.
     */
    typedef Class<> | MultiMethod | Property | MultiFunction NamedChild;

    /**
     * A normal constructor is a function that operates on a read/write structure that will contain
     * the values of the newly constructed object.
     */
    typedef function void (StructType) Constructor;

    /**
     * The second half of the constructor-finally pair.
     *
     * @see ConstructorFinally
     */
    typedef function void (PrivateType) Completion;

    /**
     * A constructor-finally pair is a combination of a constructor function that operates on the
     * read/write structure that will contain the values of the newly constructed object and which
     * returns a function that will be executed once a "this" exists for the constructed object,
     * but _before_ the constructed object is returned to the instantiator of the object.
     */
    typedef function Completion (StructType) ConstructorFinally;

    /**
     * An action describes the manner in which one step of the class composition was achieved:
     * * Extends - a class _extends_ (inherits from) another class.
     * * Implements - a class _implements_ an interface; this verb is also used when one interface
     *   "extends" another interface.
     * * Incorporates - a class _incorporates_ a mixin.
     */
    enum Action {Extends, Implements, Incorporates, Annotates}

    /**
     * A Composition represents a single step in a compositional recipe. A class is composed as a
     * series of composition steps.
     */
    static const Composition(Action action, Class<> ingredient);

    /**
     * SourceCodeInfo provides information about the name of the file that contains source code,
     * and the 0-based line number within that file that the relevant source code begins.
     */
    static const SourceCodeInfo(String sourceFile, Int lineNumber);

    // ----- primary state -------------------------------------------------------------------------

    /**
     * The category of the class.
     */
    Category category;

    /**
     * Every class is contained within a module, and the module is organized as a hierarchy of
     * named structures, starting with the module itself, which contains a tree of packages,
     * classes, properties, methods, and functions.
     */
    Namespace? parent;

    /**
     * The simple (unqualified) name of the class.
     */
    String name;

    /**
     * The type parameters for the class.
     */
    TypeParameter[] typeParams;

    /**
     * If the class is a mixin, this is the class to which it can be applied.
     */
    Class!<>? appliesTo;

    /**
     * The ordered steps of composition of this class.
     */
    Composition[] composition;

    /**
     * The child classes of this class.
     */
    Class!<>[] classes;

    /**
     * The child properties of this class.
     */
    Property[] properties;

    /**
     * The child methods of this class.
     */
    Method[] methods;

    /**
     * The child function literals of this class.
     */
    Function[] functions;

    /**
     * Determine if the class is abstract (meaning that it is not instantiable).
     */
    Boolean isAbstract;

    /**
     * Determine if the class defines a singleton (meaning that only one can be instantiated, and is
     * assumed to be instantiated by the first time that it is requested).
     */
    Boolean isSingleton;

    /**
     * Obtain the singleton instance (throws an exception if _isSingleton_ is false).
     */
    PublicType singleton;

    /**
     * Determine if the class is an inner class, which must be instantiated virtually.
     *
     * Consider the following example:
     *
     *   class BaseParent
     *       {
     *       class Child {}                                     // inner class
     *
     *       static class Orphan {}                             // inner class
     *       }
     *
     *   class DerivedParent
     *      extends BaseParent
     *     {
     *     @Override
     *     class Child {}                                       // inner class
     *
     *     @Override
     *     static class Orphan {}                               // inner class
     *     }
     *
     *   BaseParent parent1 = new BaseParent();
     *   BaseParent.Child  child1  = new parent1.Child();       // creates a BaseParent.Child
     *   BaseParent.Orphan orphan1 = new parent1.Orphan();      // creates a BaseParent.Orphan
     *
     *   BaseParent parent2 = new DerivedParent();
     *   BaseParent.Child  child2  = new parent2.Child();       // creates a DerivedParent.Child
     *   BaseParent.Orphan orphan2 = new parent2.Orphan();      // creates a DerivedParent.Orphan
     */
    Boolean isInnerClass;

    /**
     * The information that identifies the location of the source code for this class.
     */
    SourceCodeInfo? sourceInfo;

    // ----- calculated properties -----------------------------------------------------------------

    /**
     * The type parameters for the class.
     */
    @Lazy Map<String, TypeParameter> typeParamsByName.calc()
        {
        assert meta.immutable_;

        ListMap<String, TypeParameter> map = new ListMap();
        for (TypeParameter param : typeParams)
            {
            assert !map.contains(param.name);
            map.put(param.name, param);
            }

        return map.ensureConst();
        }

    /**
     * Determine the super-class of this class, if any.
     *
     * * Other than the Class for {@code Object}, a Class whose category is Class will *always* have
     *   a super-class.
     * * A Class whose category is Module, Package, Const, Enum, or Service will *always* have a
     *   super-class.
     * * A Mixin _may_ have a super-class, which must be a {@code mixin}.
     * * An Interface will *never* have a super-class.
     */
    @Lazy Class!<>? superClass.calc()
        {
        if (category == INTERFACE)
            {
            return null;
            }

        for (Composition step : composition)
            {
            if (step.action == Extends)
                {
                return step.ingredient;
                }
            }

        return null;
        }

    /**
     * Determine if this class "derives from" another class. A class derives from another class if
     * it (or something it derives from) extends the specified class, incorporates the specified
     * mixin, or implements the specified interface.
     */
    Boolean derivesFrom(Class!<> that)
        {
        if (&this == &that)
            {
            return true;
            }

        for (Composition step : composition)
            {
            if (step.ingredient == that || step.ingredient.derivesFrom(that))
                {
                return true;
                }
            }

        return false;
        }

    /**
     * Determine if the class extends (or is) the specified class.
     */
    Boolean extends_(Class!<> that)
        {
        assert that.category != INTERFACE;

        if (&this == &that)
            {
            return true;
            }

        for (Composition step : composition)
            {
            if ((step.action == Extends && step.ingredient == that) ||
                (step.action != Implements && step.ingredient.extends_(that)))
                {
                return true;
                }
            }

        return false;
        }

    /**
     * Determine if the class implements the specified interface.
     */
    Boolean implements_(Class!<> that)
        {
        assert that.category == INTERFACE;

        if (&this == &that)
            {
            return true;
            }

        for (Composition step : composition)
            {
            if ((step.action == Implements && step.ingredient == that)
                    || step.ingredient.implements_(that))
                {
                return true;
                }
            }

        return false;
        }

    /**
     * Determine if the class incorporates the specified mixin.
     */
    Boolean incorporates_(Class!<> that)
        {
        assert that.category == MIXIN;

        if (&this == &that)
            {
            return true;
            }

        for (Composition step : composition)
            {
            if ((step.action == Incorporates && step.ingredient == that) ||
                (step.action != Implements && step.ingredient.incorporates_(that)))
                {
                return true;
                }
            }

        return false;
        }

    /**
     * A class contains a variety of items, each identified by a unique name:
     *
     * * Classes (including packages)
     * * Properties
     * * Methods (grouped together by name as a {@link MultiMethod}
     * * Functions (grouped together by name as a {@link MultiFunction}
     */
    @Lazy Map<String, NamedChild> childrenByName.calc()
        {
        assert meta.immutable_;

        ListMap<String, NamedChild> map = new ListMap();
        map.putAll(classesByName);
        map.putAll(propertiesByName);
        map.putAll(methodsByName);
        map.putAll(functionsByName);

        assert map.size == classesByName.size
                         + propertiesByName.size
                         + methodsByName.size
                         + functionsByName.size;

        return map.ensureConst();
        }

    /**
     * The child classes, by name. This is a sub-set of the contents of {@link childrenByName}.
     */
    @Lazy Map<String, Class!<>> classesByName.calc()
        {
        assert meta.immutable_;

        ListMap<String, Class<>> map = new ListMap();
        for (Class<> class_ : classes)
            {
            assert !map.contains(class_.name);
            map.put(class_.name, class_);
            }

        return map.ensureConst();
        }

    /**
     * The class properties, by name. This is a sub-set of the contents of {@link childrenByName}.
     */
    @Lazy Map<String, Property> propertiesByName.calc()
        {
        assert meta.immutable_;

        ListMap<String, Property> map = new ListMap();
        for (Property property : properties)
            {
            assert !map.contains(property.name);
            map.put(property.name, property);
            }

        return map.ensureConst();
        }

    /**
     * The class methods, by name. This is a sub-set of the contents of {@link childrenByName}.
     */
    @Lazy Map<String, MultiMethod> methodsByName.calc()
        {
        assert meta.immutable_;

        ListMap<String, MultiMethod> map = new ListMap();
        for (Method method : methods)
            {
            if (MultiMethod multi : map.get(method.name))
                {
                map.put(method.name, multi.add(method));
                }
            else
                {
                map.put(method.name, new MultiMethod([method]));
                }
            }

        return map.ensureConst();
        }

    /**
     * The child function literals, by name. This is a sub-set of the contents of {@link
     * childrenByName}.
     */
    @Lazy Map<String, MultiFunction> functionsByName.calc()
        {
        assert meta.immutable_;

        ListMap<String, MultiFunction> map = new ListMap();
        for (Function function_ : functions)
            {
            if (MultiFunction multi : map.get(function_.name))
                {
                map.put(function_.name, multi.add(function_));
                }
            else
                {
                map.put(function_.name, new MultiFunction([function_]));
                }
            }

        return map.ensureConst();
        }

    // ----- dynamic class manipulation ------------------------------------------------------------

    /**
     * Narrow one or more of the class' type parameters, narrowing the type of the class.
     *
     * If the compile-time types of both _this_ class and _that_ class are known, then the
     * compile-time type of the returned class is known; otherwise, an explicit cast to a
     * compile-time type is required to regain the compile-time type.
     */
    Class!<> narrow(TypeParameter... params)
        {
        // first, verify that there is something that is being modified
        Map<String, TypeParameter> mapParams = typeParamsByName;
        Boolean different = false;
        for (TypeParameter paramNew : params)
            {
            TypeParameter paramOld = mapParams[paramNew.name] ?: assert;
            if (paramNew != paramOld)
                {
                assert paramOld.type.isA(paramNew.type);
                mapParams = mapParams.put(paramNew.name, paramNew);
                different = true;
                }
            }
        if (!different)
            {
            return this;
            }

        TODO // create the class that uses the new type parameters
        }

    /**
     * Incorporate a mixin, producing a new Class if the specified mixin is not already
     * incorporated.
     *
     * If the compile-time types of both _this_ class and _that_ class are known, then the
     * compile-time type of the returned class is known; otherwise, an explicit cast to a
     * compile-time type is required to regain the compile-time type.
     */
    Class!<> incorporate(Class!<> that);

    /**
     * Obtain a public type for this Class instance.
     */
    @Auto Type to<Type>()
        {
        return PublicType;
        }

    // ----- object creation -----------------------------------------------------------------------

    /**
     * Create a new instance of this class.
     */
    PublicType newInstance(Constructor | ConstructorFinally constructor);
    }
