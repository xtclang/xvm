import collections.Collection;
import collections.ListMap;
import collections.Set;

import reflect.Access;
import reflect.AnnotationTemplate;
import reflect.Class;
import reflect.ClassTemplate.Composition;
import reflect.InvalidType;
import reflect.Method;
import reflect.MultiMethod;
import reflect.Parameter;
import reflect.Property;


/**
 * The TypeTemplate interface represents information about an Ecstasy data type. Unlike [Type],
 * TypeTemplate can be used both with "live" runtime types (for reflection purposes) **and** with
 * "dead" type information that exists at compile-, load-, and link-time.
 *
 * Despite its name, TypeTemplate may not always be immutable.
 */
interface TypeTemplate // TODO move
        extends Const
    {
    // ----- inner classes -------------------------------------------------------------------------

    /**
     * There are a number of different forms that a type can take. Each form has a different
     * meaning, and exposes type information that is specific to that form.
     */
    enum Form(Boolean modifying = False, Boolean relational = False)
        {
        /**
         * A pure interface type that only contains methods, properties, and abstract functions.
         */
        Pure,
        /**
         * A type that is drawn from (represents the implementation of) a class composition.
         * A class type may also specify be parameterized.
         */
        Class,
        /**
         * A type that is drawn from (represents the implementation of) a property.
         */
        Property,
        /**
         * A type that represents a class that is a virtual child of another class.
         * A child type may also specify be parameterized.
         */
        Child,
        /**
         * A formal type used to define a generic type, such as `Array.Element`.
         */
        FormalProperty,
        /**
         * A formal type used as a type parameter to a method or function.
         */
        FormalParameter,
        /**
         * A child type of a formal type, such as `Element.Key` if the formal `Element` type is
         * constrained by the `Map` type.
         */
        FormalChild,
        /**
         * An intersection of two types, such as `(Int | String)`, or `String?`.
         */
        Intersection (relational = True),
        /**
         * A union of two types, such as `(Hashable + Orderable)`.
         */
        Union        (relational = True),
        /**
         * The _relative complement_ of two types, such as `(DataInput - BinaryInput)`
         */
        Difference   (relational = True),
        /**
         * A type that adds immutability _to another type_
         */
        Immutable    (modifying = True),
        /**
         * A type that adds an access modifier _to another type_.
         */
        Access       (modifying = True),
        /**
         * A type that adds an annotation _to another type_.
         */
        Annotated    (modifying = True),
        /**
         * A type that acts as a name _of another type_.
         */
        Typedef      (modifying = True),
        /**
         * A type that acts as a sequence _of other types_. It is primarily used by the [Tuple] and
         * [Function] interfaces.
         */
        Sequence
        }


    // ----- state representation ------------------------------------------------------------------

    /**
     * The name of the type, if it has one.
     *
     * Typedefs, child classes, properties, and formal types always have a name. Other types _may_
     * provide a name.
     */
    @RO String? name;

    /**
     * A brief descriptive string for the type, intended to provide clarity for a developer.
     */
    @RO String desc.get()
        {
        // this default implementation should be overridden by any type template that can provide
        // a more detailed and/or succinct description in a form well-known to a developer
        return name ?: this.TypeTemplate.toString();
        }

    /**
     * The form of the type.
     */
    @RO Form form;

    /**
     * The type or types that are under this type. A modifying type will have a single underlying
     * type; a relational type will have two underlying types; and a Sequence type will have zero
     * or more underlying types.
     */
    @RO TypeTemplate[] underlyingTypes;

    /**
     * Determine if the type non-ambiguously represents a Class [Composition], and if so, obtain the
     * composition. The composition can be ambiguous, for example, if the type is an intersection
     * type and the two intersected types do not each represent the same class.
     *
     * A type of form `Class` always represents a class composition.
     *
     * @return True iff this type represents a class composition
     * @return (conditional) the class composition
     */
    conditional Composition fromClass();

    /**
     * Determine if the type non-ambiguously represents a property. The property can be ambiguous,
     * for example, if the type is a intersection type and the two intersected types do not each
     * represent the same property.
     *
     * A type of form `Property` always represents a property.
     *
     * @return True iff this type represents a property
     * @return (conditional) the property
     */
    conditional PropertyTemplate fromProperty();

    /**
     * Determine if this type modifies an underlying type, and if it does, obtain that underlying
     * type.
     *
     * A type whose form has `modifying==True` always represents a modification to an underlying
     * type.
     *
     * @return True iff this type delegates in some manner to an underlying type
     * @return (conditional) the underlying type
     */
    conditional TypeTemplate! modifying();

    /**
     * Determine if this type is a relational type, and if it is, obtain the two types that it is
     * a relation of.
     *
     * A type whose form has `relational==True` always represents an relational type.
     *
     * @return True iff the type is relational
     * @return (conditional) the first of the two types of the relation
     * @return (conditional) the second of the two types of the relation
     */
    conditional (TypeTemplate!, TypeTemplate!) relational();

    /**
     * Determine if the type is contextually contained within another type, from which it may draw
     * type information.
     *
     * A type whose form is Child or FormalChild will always have a parent.
     *
     * @return True iff this type is contextually contained within another type
     * @return (conditional) the parent that contains this type
     */
    conditional TypeTemplate! contained();

    /**
     * Determine if the type has a non-conflicting specified access control, and if so, obtain the
     * access control. A conflicting access control can occur with a relational type that combines
     * two types, each with a different access control.
     *
     * A type whose form is `Access` will always have an access control specified.
     *
     * @return True iff this type has an unambiguous specified access control
     * @return (conditional)
     */
    conditional Access accessSpecified();

    /**
     * Determine if the type has a non-conflicting annotation, and if so, obtain the first one. A
     * conflicting annotation can occur, for example, in an intersection of two types that do not
     * each have the same annotation.
     *
     * A type whose form is `Annotated` will always have a non-conflicting annotation.
     *
     * @return True iff there is an unambiguous annotation
     * @return (conditional) the first annotation
     */
    conditional AnnotationTemplate annotated();

    /**
     * Determine if the type has non-conflicting type parameter information, and if so, obtain the
     * type for each specified parameter. (Conflicting type parameter information can result in a
     * relational type, since it is composed of multiple types.)
     *
     * A type whose form is `Class` or `Child` will have type parameters if the type is
     * parameterized.
     *
     * @return True iff the type is parameterized
     * @return (conditional) an array of the type parameters
     */
    conditional TypeTemplate![] parameterized();

    /**
     * Determine if the type is recursive. Certain types may recursively refer to themselves, either
     * directly or indirectly; consider the example:
     *
     *     typedef (Nullable | Boolean | Number | String | JsonVal[] | Map<String, JsonVal>) JsonVal
     */
    @RO Boolean recursive;

    /**
     * A type can be explicitly immutable. An object can only be assigned to an explicitly immutable
     * type if the object is immutable.
     */
    @RO Boolean explicitlyImmutable;

    /**
     * Obtain the `Pure` form of this type.
     */
    TypeTemplate! purify();


    // ----- type operations -----------------------------------------------------------------------

    /**
     * Test whether this type is type-compatible with the specified second type, such that any
     * object of `this` type would also be an object of `that` type.
     *
     * It is possible for the two type templates to be incompatible for comparison, if their origins
     * differ, because they may not share a common type system understanding. For example, a type
     * template can originate from a runtime type, and a type template can be manually constructed
     * for compile-time use. In any such case, this method must return `False`.
     *
     * @param that  a second type template
     *
     * @return True iff all objects of `this` type are also objects of `that` type
     */
    Boolean isA(TypeTemplate! that);


    // ----- reification ---------------------------------------------------------------------------

    /**
     * Find (or build) the Type within the specified TypeSystem that corresponds to this
     * TypeTemplate.
     *
     * @param typeSystem  the TypeSystem within which to find and/or build the corresponding Type
     *
     * @return True iff the corresponding Type exists (or can be built within) the TypeSystem
     * @return (conditional) the Type within the TypeSystem corresponding to this TypeTemplate
     */
    conditional Type reifyWithin(TypeSystem typeSystem);


    // ----- Stringable ----------------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return 0;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        Boolean fParams = False;
        switch (form)
            {
            case Pure:
                TODO

            case Class:
                assert Composition cmp := fromClass();
                while ((AnnotationTemplate annotation, cmp) := cmp.deannotate())
                    {
                    buf.add('@')
                       .addAll(annotation.template.name)
                       .add(' ');
                    }
                assert cmp.is(ClassTemplate);
                buf.addAll(cmp.name);
                fParams = True;
                break;

            case Child:
                fParams = True;
                continue;
            case Property:
            case FormalProperty:
            case FormalParameter:
            case FormalChild:
                TODO

            case Intersection:
            case Union:
            case Difference:
                assert (TypeTemplate t1, TypeTemplate t2) := relational();
                t1.appendTo(buf);
                buf.addAll(switch (form)
                        {
                        case Intersection: " | ";
                        case Union:        " + ";
                        case Difference:   " - ";
                        default: assert;
                        });
                t2.appendTo(buf);
                break;

            case Immutable:
                assert TypeTemplate t1 := modifying();
                buf.addAll("immutable ");
                t1.appendTo(buf);
                break;

            case Access:
                assert val access := accessSpecified();
                assert TypeTemplate t1 := modifying();
                t1.appendTo(buf);
                buf.add(':')
                   .addAll(access.keyword);
                break;

            case Annotated:
                TODO

            case Typedef:
                (name?.as(Stringable) : underlyingTypes[0]).appendTo(buf);
                break;

            case Sequence:
                TODO

            default:
                assert;
            }

        if (fParams, TypeTemplate[] params := parameterized())
            {
            buf.add('<');
            EachParam: for (TypeTemplate param : params)
                {
                if (!EachParam.first)
                    {
                    buf.addAll(", ");
                    }
                param.appendTo(buf);
                }
            buf.add('>');
            }

        return buf;
        }


    // ----- Comparable, Hashable, and Orderable ---------------------------------------------------

    static <CompileType extends TypeTemplate> Int hashCode(CompileType value)
        {
        TODO
        }

    static <CompileType extends TypeTemplate> Boolean equals(CompileType value1, CompileType value2)
        {
        // the definition for type equality is fairly simple: each of the two types must be
        // type-compatible with the other
        return value1.isA(value2) && value2.isA(value1);
        }

    static <CompileType extends TypeTemplate> Ordered compare(CompileType value1, CompileType value2)
        {
        if (value1 == value2)
            {
            return Equal;
            }

        TODO <=>
        }
    }


