import reflect.ClassTemplate.Composition;

/**
 * A Class represents the information about a _class of_ objects. Since the term _class_ is widely
 * used and often abused in programming, and since the definition of class is clearly recursive, it
 * is particularly important that Ecstasy's use of the term be clear and concise. Specifically:
 *
 * * Objects are instantiated _from a_, and _by a_ Class.
 * * Instantiation is creation. Instantiation is the means by which an object becomes existent.
 * * Therefore, if an object exists, then it was instantiated, and it was instantiated _by a_ Class.
 * * Existence implies a completed act of creation. If that act of creation fails, then the object
 *   that would have resulted, does not come into existence, and never existed.
 * * An object that comes into existence knows the Class that created it. The object is _of_ that
 *   Class.
 * * From inside an object, the Class of the object is `this:class`. From outside of the object,
 *   the class may be obtained from the object's type iff the type is _classy_; for example, one
 *   one can obtain the Class for an object `o`: `if (Class c := &o.actualType.classy()) {...}`.
 *   However, an object that is injected into a container may hide the _classy_ `actualType` and
 *   only expose an interface type, which results in the class of the injected object being hidden
 *   from the code running inside the container.
 * * Since _everything is an object_, it follows that everything -- all objects -- were created,
 *   each from its own Class, and therefore each _of_ its own Class.
 * * Each Class is itself an object. Like all objects, each Class object is itself _of a_ Class. As
 *   with many recursive aspects of Ecstasy: It's turtles the whole way down.
 *
 * A Class is intricately defined by a [Composition]. A Composition includes the discrete steps
 * of how the Class definition was formed; each step is like a step in a recipe, providing an
 * ingredient and the manner in which that ingredient is contributed to the resulting whole. The
 * base ingredient form is the [ComponentTemplate], which typically represents a binary structure
 * that corresponds to (i.e. "is the compiled form of") a class, or a member of a class. For
 * convenience purposes, some of the information from the composition or the underlying templates
 * is also made available as part of the Class interface.
 *
 * A Class always provides four types:
 *
 * * The _public_ type represents the programming interface that the class chooses to use to
 *   describe and expose its functionality to the world.
 * * The _protected_ type builds on the public type, adding functionality that is necessary or
 *   useful when the class is being used as a building block to create a sub-class or an aggregate
 *   component.
 * * The _private_ type builds upon the protected type, allowing the class to hide functionality
 *   and details that are necessary only for the implementation of the class itself.
 * * The _struct_ type represents the underlying state of an object. The _struct_ type is accessed
 *   and manipulated _as if_ it were composed of only the properties of the object; however, the
 *   properties of the _struct_ type directly access the underlying _fields_ of the object's
 *   structure, and thus do **not** invoke any virtual functionality of the object's properties.
 *
 * The terms "protected" and "private" do **not** refer to language security features; to consider
 * the use of these keywords as a form of "security" is both misleading and erroneous -- _in any
 * OO language_. Rather, these terms are used to indicate the desire of a developer to hide (or
 * encapsulate) information and functionality in order to make software components both more
 * understandable and easier to use _correctly_. For example, the use of "private" members allows
 * information to be accessed and manipulated that corresponds to the internal details of
 * implementation -- details that should not be exposed at all. Similarly, the use of "protected"
 * members provides information and functionality that may be useful when composing a new class from
 * an existing one.
 *
 * A Class may be a _singleton_ class.  A _singleton_ class is instantiated no later than the first
 * time that it is referenced. The no-parameter constructor is used to construct the singleton
 * instance. (Both type parameters and constructor parameters _with explicit defaults_ are
 * permitted, and the defaults for each will be used to construct the singleton instance.)
 *
 * There are several means by which an instance of a non-abstract class may exist:
 * * The class may be a singleton;
 * * An instance of the non-singleton class may be created using one of its _constructors_, either
 *   by way of the `new` keyword or by obtaining the constructor from the [Class];
 * * [Class] also provides a `Struct`-based constructor for that allows an object to be instantiated
 *   from its underlying structure. This is useful for reflection-based manipulation, such as object
 *   deserialization.
 *
 * @param PublicType
 * @param ProtectedType
 * @param PrivateType
 * @param StructType
 */
const Class<PublicType, ProtectedType extends PublicType,
                        PrivateType   extends ProtectedType,
                        StructType    extends Struct>
        incorporates conditional Enumeration<PublicType extends Enum>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a `Class` based on a [Composition], the formal types of the class (if any), and
     * (for singletons) a function that provides the singleton value.
     *
     * @param composition
     * @param formalTypes
     * @param obtainSingleton
     */
    construct(Composition            composition,
              Map<String, Type>      formalTypes,
              function PublicType()? obtainSingleton = Null)
        {
        this.formalTypes     = formalTypes;
        this.composition     = composition;
        this.obtainSingleton = obtainSingleton;
        }


    // ----- attributes ----------------------------------------------------------------------------

    /**
     * The values for each of the formal types required by the class. The order of the entries in
     * the map is significant.
     */
    Map<String, Type> formalTypes;

    /**
     * The composition of the class.
     */
    Composition composition;

    /**
     * The provider of the singleton instance. The singleton itself cannot be held in a property of
     * the class, because Class is a `const`, and the singleton may be a `service` (which cannot be
     * immutable, and thus a reference to a `service` cannot be held by a `const`).
     */
    private function PublicType()? obtainSingleton;

    /**
     * Determine if the class is an virtual child class, which must be instantiated virtually.
     */
    Boolean virtualChild.get()
        {
        return composition.template.virtualChild;
        }

    /**
     * Determine if the class defines a singleton, and if so, obtain that singleton. If a class is
     * a singleton class, that means that only one instance of that class can be instantiated, and
     * that instance is assumed to be instantiated by the first time that it is requested).
     *
     * @throws IllegalState if the class is not part of the caller's type system, or a type system
     *         of a container nested under the caller's container
     * @throws Exception if an exception occurred instantiating the singleton, it is thrown when an
     *         attempt is made to access the singleton instance
     */
    conditional PublicType isSingleton()
        {
        function PublicType()? obtain = obtainSingleton;
        return obtain == Null
                ? False
                : (True, obtain());
        }

    /**
     * Determine if the class of the referent extends (or is) the specified class.
     *
     * @param clz  the class to test if this class extends
     *
     * @return True iff this class extends the specified class
     */
    Boolean extends(Class!<> clz)
        {
        // one can only "incorporate" a mixin
        if (clz.composition.template.format == Interface)
            {
            return False;
            }

        return this.PublicType.isA(clz.PublicType) && this.composition.extends(clz.composition);
        }

    /**
     * Determine if the class of the referent incorporates the specified mixin.
     *
     * @param clz  the class to test if this class incorporates
     *
     * @return True iff this class incorporates the specified class
     */
    Boolean incorporates(Class!<> clz)
        {
        // one can only "incorporate" a mixin
        if (clz.composition.template.format != Mixin)
            {
            return False;
            }

        return this.PublicType.isA(clz.PublicType) && this.composition.incorporates(clz.composition);
        }

    /**
     * Determine if the class of the referent implements the specified interface.
     *
     * Note: Unlike [Type.isA], this method doesn't simply check if the referent's class
     * has all methods that the specified interface has. Instead, it returns true iff any of the
     * following conditions holds true:
     *  - the referent's class explicitly declares that it implements the specified interface, or
     *  - the referent's super class implements the specified interface (recursively), or
     *  - any of the interfaces that the referent's class declares to implement extends the
     *    specified interface (recursively)
     *
     * @param clz  the class to test if this class implements
     *
     * @return True iff this class implements the specified class (representing an interface)
     */
    Boolean implements(Class!<> clz)
        {
        // one can only "implement" an interface
        if (clz.composition.template.format != Interface)
            {
            return False;
            }

        return this.PublicType.isA(clz.PublicType) && this.composition.implements(clz.composition);
        }

    /**
     * Determine if this class "derives from" another class.
     *
     * @return True iff this (or something that this derives from) extends the specified class,
     *         incorporates the specified mixin, or implements the specified interface
     */
    Boolean derivesFrom(Class!<> clz)
        {
        return &this == &clz
                || this.extends(clz)
                || this.incorporates(clz)
                || this.implements(clz);
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Obtain the default (public) [Type] for this Class instance.
     *
     * @return the PublicType
     */
    @Auto
    Type toType()
        {
        return PublicType;
        }
    }
