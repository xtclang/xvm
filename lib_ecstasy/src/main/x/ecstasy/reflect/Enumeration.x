import collections.ListMap;
import ClassTemplate.Composition;

/**
 * An Enumeration is a class of singleton abstract base of Enum value objects.
 *
 * Consider the following examples:
 * * {@link Nullable.Null} is a singleton instance Enum value, of the class (or a subclass of)
 *   {@link Nullable}. The *class* for Nullable implements the Enumeration interface.
 * * {@link Boolean.False} and {@link Boolean.True} are singleton instance Enum values, of the class
 *   (or each of a unique subclass of) {@link Boolean}. The *class* for Boolean implements the
 *   Enumeration interface.
 * * {@link Ordered.Lesser}, {@link Ordered.Equal}, and {@link Ordered.Greater} are singleton
 *   instance Enum values, of the class (or each a unique subclass of) {@link Ordered}. The *class*
 *   for Ordered implements the Enumeration interface.
 *
 * The result is that one can obtain the Enumeration of a set of Enum values by the name of the
 * Enumeration class:
 *
 *   Enumeration enumeration = Ordered;
 *   String[]    names       = enumeration.names;
 *
 * Or more simply:
 *
 *   String[] names = Ordered.names;
 *
 * Because each Enum value is a singleton value, it can also be referenced by its name:
 *
 *   // "Ordered" is referring to the class, while "Lesser" is referring to a singleton value
 *   Ordered value = Lesser;
 *
 * The syntax for declaring an Enumeration uses the `enum` keyword:
 *
 *   enum RGB {Red, Green, Blue}
 *
 * There are a number of rules and constraints related to Enumerations:
 * * Each Enum value is a singleton and a `const`, with an implicitly defined name and ordinal
 *   value. The name comes from the declaration of the Enum value, and the zero-based ordinal comes
 *   from its location in the list of declared Enum values for the Enumeration.
 * * The Enum values (and their classes, if they override the base Enumeration class) are contained
 *   within (i.e. are children of) the base Enumeration class. As such, the names of the Enum values
 *   must not collide with any of the other names in the same namespace, such as "toString"
 *   from {@link Object}.
 * * The Enum values do _not_ implement (are not instances of, nor castable to) Enumeration; the
 *   Enum values do implement the {@link Enum} interface.
 * * The class of Enumeration values (such as `Ordered` in the examples above) is not
 *   instantiable (as if it were an abstract class), nor can any class explicitly extend it. Each of
 *   its Enum values are instances of the class of Enumeration values (or a subclass thereof), and
 *   no class can extend the class of any Enum.
 */
const Enumeration<Value extends Enum>
        extends Class<Value>
    {
    construct(Composition composition, ListMap<String, Type>? canonicalParams = Null)
        {
        construct Class(composition, canonicalParams);
        }

    /**
     * The name of the Enumeration.
     *
     * Consider the following examples:
     * * "Nullable" for {@link Nullable}
     * * "Boolean" for {@link Boolean}
     * * "Ordered" for {@link Ordered}
     */
    @Override
    @RO String name.get()
        {
        // the name of the class is same as the name of the Enumeration
        return super();
        }

    /**
     * The number of Enum values in the Enumeration.
     *
     * Consider the following examples:
     * * 1 for {@link Nullable}
     * * 2 for {@link Boolean}
     * * 3 for {@link Ordered}
     */
    @Lazy Int count.calc()
        {
        return byName.size;
        }

    /**
     * The names of the Enum values in the Enumeration. These correspond in their positions to the
     * {@link values}.
     *
     * Consider the following examples:
     * * {"Null"} for {@link Nullable}
     * * {"False", "True"} for {@link Boolean}
     * * {"Lesser", "Equal", "Greater"} for {@link Ordered}
     */
    @Lazy String[] names.calc()
        {
        return byName.keys.toArray();
        }

    /**
     * The Enum values of the Enumeration. These correspond in their positions to the {@link names}.
     *
     * Consider the following examples:
     * * {Null} for {@link Nullable}
     * * {False, True} for {@link Boolean}
     * * {Lesser, Equal, Greater} for {@link Ordered}
     */
    @Lazy Value[] values.calc()
        {
        return byName.values.toArray();
        }

    /**
     * The Enum values of the Enumeration, indexed by their names.
     *
     * Consider the following examples:
     * * {"Null"=Null} for {@link Nullable}
     * * {"False"=False, "True"=True} for {@link Boolean}
     * * {"Lesser"=Lesser, "Equal"=Equal, "Greater"=Greater} for {@link Ordered}
     */
    @Lazy Map<String, Value> byName.calc()
        {
        // the Enumeration class contains singleton Enum class/values; collect those values into a
        // Map keyed by name
        ListMap<String, Value> map = new ListMap();

        for ((String name, Type type) : PublicType.childTypes)
            {
            if (type.is(Type<PublicType>))
                {
                assert Class<> clz := type.fromClass();
                assert Value instance := clz.as(Class<Value>).isSingleton();
                assert instance.ordinal == map.size;
                map.put(name, instance);
                }
            }
        return map.freeze();
        }
    }