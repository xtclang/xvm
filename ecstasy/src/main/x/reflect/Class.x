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
 * * From inside an object, the Class of the object is `this:class`. From outside of the object, the
 *   class may be obtained from the object's type iff the type is _classy_; for example, one can
 *   obtain the Class for an object `o`: `Class c = &o.actualClass;`. However, an object that is
 *   injected into a container may hide the _classy_ `actualType` and only expose an interface type,
 *   which results in the class of the injected object being hidden from the code running inside the
 *   container.
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
     * @param composition      the class composition information
     * @param canonicalParams  the canonical types corresponding to the generic type parameters of
     *                         this class; the canonical types represent the declared type constraint
     *                         for each type parameter, and not the actual specified type for each
     * @param allocateStruct   the function that is used to allocate an initial structure that can
     *                         be populated and used to instantiate an object of this class
     * @param implicitName     for a small number of commonly used classes in the
     *                         `Ecstasy.xtclang.org` module, an implicit name is provided to make
     *                         the usage of the class as simple as having imported the class "as"
     *                         the implicit name
     */
    construct(Composition            composition,
              ListMap<String, Type>? canonicalParams = Null,
              function StructType()? allocateStruct  = Null,
              String?                implicitName    = Null)
        {
        if (Type[] formalTypes := PublicType.parameterized(), formalTypes.size > 0)
            {
            assert:arg canonicalParams != Null;
            assert:arg canonicalParams.size == formalTypes.size;
            val formals    = formalTypes.iterator();
            val canonicals = canonicalParams.values.iterator();
            while (Type formal := formals.next())
                {
                assert:arg Type canonical := canonicals.next();
                assert:arg formal.isA(canonical);
                }
            }
        else
            {
            assert:arg canonicalParams?.size == 0;
            }

        this.composition     = composition;
        this.canonicalParams = canonicalParams ?: new ListMap();
        this.allocateStruct  = allocateStruct;
        this.implicitName    = implicitName;
        }


    // ----- attributes ----------------------------------------------------------------------------

    /**
     * The simple unqualified name of the Class.
     */
    @RO String name.get()
        {
        return composition.template.name;
        }

    /**
     * A name by which the Class is globally visible at compile time. This only applies to a small
     * subset of classes in the core Ecstasy module; for example, `numbers.Int64` has the implicit
     * name `Int`.
     */
    String? implicitName;

    /**
     * The path of a class is composed of its module qualified name followed by a colon, followed
     * by a dot-delimited sequence of names necessary to identify this class within its module.
     */
    @RO String path.get()
        {
        return composition.template.path;
        }

    /**
     * A name intended to be more easily human-readable than a fully qualified path (if possible),
     * while still being sufficiently descriptive that it would be accepted by
     * [TypeSystem.classForName], and would result in this class.
     */
    @RO String displayName.get()
        {
        String? alias = implicitName;
        if (alias != Null)
            {
            return alias;
            }

        if (String relative := pathWithin(this:service.typeSystem))
            {
            return relative;
            }

        // use absolute path
        return path;
        }

    /**
     * Given a specified TypeSystem, determine the path that identifies this Class within that
     * TypeSystem.
     *
     * @param a TypeSystem
     *
     * @return True iff the class exists within the specified TypeSystem
     * @return (optional) the qualified path to the class within the TypeSystem, in a format that
     *         is supported by [TypeSystem.classForName]
     */
    conditional String pathWithin(TypeSystem typesys)
        {
        String path       = this.path;
        assert Int colon := path.indexOf(':');
        String moduleName = path[0 .. colon);
        if (Module _module := typesys.moduleByQualifiedName.get(moduleName))
            {
            if (String modPath := typesys.modulePaths.get(_module))
                {
                // compute a relative path from the root of the primary module
                String relPath = path.substring(colon+1);
                return True, switch (modPath, relPath)
                        {
                        case ("", ""): typesys.primaryModule.qualifiedName + ':';
                        case ("", _ ): relPath;
                        case (_ , ""): modPath;
                        case (_ , _ ): modPath + '.' + relPath;
                        };
                }
            else
                {
                // use the absolute path including the module qualified name
                return True, path;
                }
            }

        return False;
        }

    /**
     * The composition of the class.
     */
    Composition composition;

    /**
     * Obtain the deannotated form of this class, and the annotations, if any, that were added to
     * the original underlying class. Note that the original underlying class may appear in the
     * source code to be annotated, but the use of the annotation syntax in a class declaration is
     * used to incorporate mixins into the underling class definition itself, and are not considered
     * to be annotations; fo example, "MyAnnotation" is incorporated into "MyClass", and is not an
     * annotation:
     *
     *     @MyAnnotation class MyClass {...}
     *
     * @return the underlying class
     * @return an array of the annotations that were applied to the underlying class
     */
    (Class!<> deannotated, Annotation[] annotations) deannotate()
        {
        Type type = PublicType;
        if (Annotation annotation := type.annotated())
            {
            Annotation[] annotations = new Annotation[];
            do
                {
                annotations.add(annotation);
                assert type.form == Annotated, type := type.modifying();
                }
            while (annotation := type.annotated());
            assert Class!<> deannotated := type.fromClass();
            return deannotated, annotations.reversed();
            }
        else
            {
            return this, [];
            }
        }

    /**
     * Add the specified annotations to this class to produce a new, annotated class.
     *
     * @param annotations  the annotations to add to this class
     *
     * @return the annotated class
     */
    Class!<> annotate(Annotation[] | Annotation annotations)
        {
        Type type = PublicType;
        if (annotations.is(Annotation[]))
            {
            if (annotations.size == 0)
                {
                return this;
                }

            for (Annotation annotation : annotations)
                {
                type = type.annotate(annotation);
                }
            }
        else
            {
            type = type.annotate(annotations);
            }

        assert Class!<> annotated := type.fromClass();
        return annotated;
        }

    /**
     * The formal type parameter names and the canonical (constraint) type for each. The order is
     * significant, and matches the type parameters in [PublicType], etc.
     */
    ListMap<String, Type> canonicalParams;

    /**
     * The values for each of the formal types required by the class. The order of the entries in
     * the map is significant.
     */
    @Lazy ListMap<String, Type> formalTypes.calc()
        {
        ListMap<String, Type> canonicals = canonicalParams;
        ListMap<String, Type> formals    = new ListMap(canonicals.size);
        if (Type[] formalTypes := PublicType.parameterized())
            {
            assert formalTypes.size == canonicals.size;
            Loop: for (String name : canonicals.keys)
                {
                formals[name] = formalTypes[Loop.count];
                }
            }
        return formals.makeImmutable();
        }

    /**
     * @return the canonical form of this class
     */
    @RO Class!<> canonicalClass.get()
        {
        if (PublicType.parameterized())
            {
            assert Class!<> that := PublicType.parameterize().fromClass();
            return that;
            }

        return this;
        }

    /**
     * Add, remove, or replace type parameters on this class.
     *
     * @param paramTypes  a sequence of types
     *
     * @return the corresponding parameterized class
     */
    Class!<> parameterize(Type[] paramTypes = [])
        {
        // TODO tuple support

        Type[] oldParams = [];
        oldParams := PublicType.parameterized();
        CheckSame: if (paramTypes.size == oldParams.size)
            {
            for (Int i = 0, Int c = paramTypes.size; i < c; ++i)
                {
                if (paramTypes[i] != oldParams[i])
                    {
                    break CheckSame;
                    }
                }
            return this;
            }

        Type[] canonicalTypes = canonicalParams.values.toArray();
        assert:arg paramTypes.size <= canonicalTypes.size;
        for (Int i = 0, Int c = paramTypes.size; i < c; ++i)
            {
            assert:arg paramTypes[i].isA(canonicalTypes[i]);     // REVIEW should this be isA(Ttype<cT[i]>) ?
            }

        assert Class!<> that := PublicType.parameterize(paramTypes).fromClass();
        return that;
        }

    /**
     * The factory for structure instances, if the class is not abstract in this [TypeSystem].
     */
    protected function StructType()? allocateStruct;

    /**
     * The singleton instance, which is either an `immutable Const` or a `Service`.
     */
    private @Lazy PublicType singletonInstance.calc()
        {
        function StructType()? alloc = allocateStruct;
        assert composition.template.singleton && alloc != Null;
        PublicType instance = instantiate(alloc());
        assert &instance.isConst || &instance.isService;
        return instance;
        }

    /**
     * True iff the class is abstract.
     */
    @RO Boolean abstract.get()
        {
        return composition.template.isAbstract; // TODO the composition itself should have abstract as a property
        }

    /**
     * Determine if the class is a virtual child class, which must be instantiated virtually.
     */
    Boolean virtualChild.get()
        {
        return composition.template.virtualChild;
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
        // one can only "extend" a class (or a mixin extend a mixin)
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
     * has all methods that the specified interface has. Instead, it returns True iff any of the
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


    // ----- construction --------------------------------------------------------------------------

    /**
     * Obtain the child class of the specified name, parameterized by the optional sequence of type
     * parameters.
     *
     * @param name        the name of the child class
     * @param paramTypes  the type parameters of the child class
     *
     * @return the child class as specified
     */
    Class!<> childForName(String name, Type[] paramTypes = [])
        {
        assert Type     childType  := PrivateType.childTypes.get(name);
        assert Class!<> childClass := childType.parameterize(paramTypes).fromClass();
        return childClass;
        }

    /**
     * Determine if the class defines a singleton, and if so, obtain that singleton. If a class is
     * a singleton class, that means that only one instance of that class can be instantiated, and
     * that instance is assumed to be instantiated no later than the first time that it is
     * requested).
     *
     * @throws IllegalState if the class is not part of the caller's type system, or a type system
     *         of a container nested under the caller's container
     * @throws Exception if an exception occurred instantiating the singleton, it is thrown when an
     *         attempt is made to access the singleton instance
     */
    conditional PublicType isSingleton()
        {
        return composition.template.singleton && allocateStruct != Null
                ? (True, singletonInstance)
                : False;
        }

    /**
     * Allocate an empty structure for this class.
     */
    conditional StructType allocate()
        {
        ClassTemplate template = composition.template;
        function StructType()? alloc = allocateStruct;
        return template.isAbstract || composition.template.singleton || alloc == Null
                ? False
                : (True, alloc());
        }

    /**
     * - if a corresponding constructor exists, it will be called before the automatic structure
     *   validation
     *
     * @throws IllegalState if the structure is illegal in any way
     */
    PublicType instantiate(StructType structure, PublicType.OuterType? outer = Null)
        {
        assert function PublicType (StructType) constructor := PublicType.structConstructor(outer);
        return constructor(structure);
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Obtain the default (public) [Type] for this Class instance.
     *
     * This method exists so that the compiler can obtain a type based on a class name, such as:
     *
     *      Type t = String;    // String as a literal is a Class, but it can also be used as a Type
     *
     * @return the PublicType
     */
    @Auto
    Type toType()
        {
        return PublicType;
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        Int size = 0;

        (_, Annotation[] annotations) = deannotate();
        if (annotations.size > 0)
            {
            for (Annotation annotation : annotations)
                {
                size += annotation.estimateStringLength();
                }
            size += annotations.size; // spaces
            }

        size += displayName.size;

        ListMap<String, Type> params = formalTypes;
        if (!params.empty)
            {
            size += 2;
            Params: for (Type type : params.values)
                {
                if (!Params.first)
                    {
                    size += 2;
                    }
                size += type.estimateStringLength();
                }
            }

        return size;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        (_, Annotation[] annotations) = deannotate();
        if (annotations.size > 0)
            {
            for (Annotation annotation : annotations.reversed())
                {
                annotation.appendTo(buf);
                buf.add(' ');
                }
            }

        buf.addAll(displayName);

        ListMap<String, Type> params = formalTypes;
        if (!params.empty)
            {
            buf.add('<');
            Params: for (Type type : params.values)
                {
                if (!Params.first)
                    {
                    buf.addAll(", ");
                    }
                type.appendTo(buf);
                }
            buf.add('>');
            }

        return buf;
        }
    }
