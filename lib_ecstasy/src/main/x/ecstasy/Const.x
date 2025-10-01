/**
 * This interface represents the automatically-implemented contract for all `const` classes. A
 * `const` class is a class declared using the `const` keyword; for example:
 *
 *     const Point(Dec x, Dec y);
 *
 * The declaration as a `const` (instead of using the `class`) keyword has several significant
 * effects on the resulting class:
 *
 * * When you `new` a `const`, after the `construct()` functions are executed (but before any
 *   associated `finally` methods are executed), the object is _frozen_, i.e. the object is made
 *   _deeply immutable_. The reference to the object itself (aka the `this` reference) does not
 *   exist until **after** the object has been fully constructed **and** made deeply immutable.
 *
 * * Because a `const` is deeply immutable, all of the field values in its `struct` (i.e. the
 *   storage for its properties) must be of the [Shareable] type. A [Shareable] type is one that is
 *   either `immutable`, is [Freezable] so that it can be made `immutable`, or is a reference to
 *   another `service`. But there are two important exceptions to the rule of deep immutability:
 *
 * * * A `service` is never `immutable`, but a deeply immutable object is allowed to hold `service`
 *     references, which are `service` _proxies_. While the reference held by the `const` cannot be
 *     modified, the `service` that is referenced is still modifiable.
 *
 * * * The value of a [@Lazy](Lazy) property on a `const` object is _deferred_: it does not have an
 *     initial value when the `const` object is created. The first time that the `@Lazy` property is
 *     accessed, the property calculates _and stores_ the value. Like every property value on a
 *     `const` object, that value must be [Shareable] so that it can be stored as part of the deeply
 *     immutable object. The act of storing that value is a mutation of the `const` object, though,
 *     and thus `@Lazy` is an explicit exception to the deep immutability rule.
 *
 * * Every `const` class automatically receives an implementation of the [Comparable], [Hashable],
 *   and [Orderable] interfaces, specifically the [equals], [hashCode], and [compare] functions.
 *
 * * * The automatically generated implementation of these functions relies only on the
 *     non-[@Lazy](Lazy) properties of the `const` that have a _field_ in the `const`'s `struct`.
 *
 * * * For any of these functions that is already implemented on this particular `const` class, no
 *     automatically generated implementation is provided; in this way, the developer has full
 *     control of the implementation of these interfaces when that is desirable.
 *
 * * * If no implementation of a particular function is provided, but the _super-class_ of this
 *     `const` class provides an implementation of that function, then the auto-generated
 *     implementation will first delegate to the function on the super-class, and after that
 *     function returns, the auto-generated implementation will -- if necessary -- handle only the
 *     properties that are declared on this `const` (i.e. not present on the super-class).
 *
 * * * The auto-generated implementations of these functions operate in the order of the properties
 *     as their fields occur in the `struct`.
 *
 * * * For more information, see the documentation on [equals], [hashCode], and [compare].
 *
 * * Ecstasy objects **always** provide _reference semantics_, but certain `const` classes also
 *   provide _value semantics_, which means that `(v1 == v2) == (&v1 == &v2)` for any two instances
 *   `v1` and `v2` of the same class. These are known as _Superposed types_.
 *
 * * * A simple example is the `Int` value `4`: No matter how one creates, calculates, or otherwise
 *     obtains the value `4`, there is only one `4` -- so it's not just that `4 == 4`, but that the
 *     identity of `4` is _effectively_ `4` itself!
 *
 * * * This duality of reference and value semantics is conceptually similar to the wave/particle
 *     duality, which in quantum physics is referred to as _quantum superposition_, from which the
 *     Ecstasy term describing value/reference duality originates. (See also: the Schrödinger
 *     equation, the Heisenberg picture, and the Stone–von Neumann theorem.)
 *
 * * * The Ecstasy rules do not attempt to guarantee that there is only one `Int 4` object in all of
 *     memory, but instead guarantees that the system will behave as if there were only one. In
 *     other words, the detail of whether two references refer to the same bits at the same exact
 *     location in memory is intended to be completely invisible to the programmer. This is
 *     accomplished by guaranteeing that for any two references to Superposed objects of the same
 *     class that answer `True` for the `==` operator (i.e. the class'
 *     [equals](Comparable.equals(CompileType, CompileType)) function), the system behavior will be
 *     _identical to_ the behavior that would result from having two perfectly identical references
 *     to the same object at the same specific location in memory.
 *
 * * * Requirements for Superposed types:
 *
 * * * * A Superposed type must be a class type of a `const` class, and can apply only to a `@Final
 *       const` class, or to a `@Sealed const` class whose permitted subclasses (if any) are each
 *       Superposed types that add no fields;
 *
 * * * * The type of each property that has a _field_ must also be a Superposed type;
 *
 * * * * The class must not have any [@Lazy](Lazy) or [@Transient](Transient) properties;
 *
 * * * * The class (including each of its superclasses) must not contain any implementation
 *       of the [hashCode()] or [equals()] function, other than that which is defined on the root
 *       [Object] interface;
 *
 * * * * A union type `T1 | T2` is a Superposed type iff `T1` and `T2` are both Superposed types;
 *
 * * * * Without regard to the above rules, the following `const` classes are guaranteed to be
 *       Superposed types: [Nullable] [Null], [Boolean] [False] and [True], [Bit], [Int8], [Int16],
 *       [Int32], [Int64], [Int128], [UInt8] aka [Byte], [UInt16], [UInt32], [UInt64], [UInt128],
 *       [Float8e4], [Float8e5], [BFloat16], [Float16], [Float32], [Float64], [Float128], [Dec32],
 *       [Dec64], [Dec128], [Nibble], [Char], [Time], [Date], [TimeOfDay], [TimeZone], [Duration],
 *       and [Range<Superposed>](Range).
 *
 * * Every `const` class automatically receives an implementation of the [estimateStringLength] and
 *   [appendTo] methods from the [Stringable] interface:
 *
 * * * The default [Stringable.toString] implementation uses the combination of the
 *     [estimateStringLength] and [appendTo] methods to render a [String] value from the `const`.
 *     If the `const` (or a super-class of the `const`) provides an implementation of the [toString]
 *     method, then the automatically-generated [estimateStringLength] and [appendTo]
 *     implementations may or may not be used, depending on that `toString` implementation; in this
 *     case, to enable their use, implement the [toString] method on the `const` using the code from
 *     the [Stringable.toString] implementation.
 *
 * * * The format of the [estimateStringLength] and [appendTo] methods is "`(`" _prop1_ "`=`" _val1_
 *     "`, `" _prop2_ "`=`" _val2_ "`)`", and so on. Each property appears in the same evaluation
 *     order as in [equals], but [@Lazy](Lazy) properties are also included iff they are
 *     [assigned](Ref.assigned).
 *
 * * * For each property value `v`, in order of precedence:
 *
 * * * * If the property is an unassigned [@Lazy](Lazy) property, then it is omitted;
 *
 * * * * If `v` is [Stringable], then the implementation delegates to the corresponding [Stringable]
 *       method;
 *
 * * * * Otherwise, the generated [estimateStringLength()] implementation will assume that the
 *       `v.toString()` is the default [Object.toString()] implementation, and the generated
 *       [appendTo] implementation uses the `String` returned from [v.toString](Object.toString).
 *
 * One can implement this big-"C" [Const] interface by hand on a class, but that does not make the
 * class a little-"c" `const`; an object is only a `const` if it is declared as a `const` class, or
 * any of the `const` derivatives: `enum`, `module`, and `package`. Consider the meaning of the
 * three following type tests for some object `o`:
 *
 * * `o.is(Const)` - tests if the class of `o` implements this `Const` interface, but does not test
 *   whether the class of `o` is a `const` class, or is even immutable;
 * * `o.is(immutable Const)` - tests if `o` is an `immutable` Object, and that the class of `o`
 *   implements this `Const` interface, but it does not test whether the class of `o` is a `const`
 *   class;
 * * `o.is(const)` - tests if the class of `o` is a `const` class -- which implies that
 *   `o.is(immutable Const)`
 *
 * (All little-"c" `const` objects will pass all three of these tests.)
 */
interface Const
        extends Hashable
        extends Orderable
        extends Stringable {

    /**
     * The automatically generated implementation of the [hashCode] function evaluates each property
     * based on its compile-time type, and must produce a result that supports the contract defined
     * by the [Hashable] interface. Properties are evaluated in the same order as [equals], and the
     * hashed value of each is combined to produce a final `hashCode` for the `const`. Based on
     * property compile-time types, in order of precedence:
     *
     * * For a property whose compile-time type is `const`, the implementation delegates to the
     *   [hashCode] function on the compile-time type.
     *
     * * For a property whose compile-time type is `service`, the implementation uses the property
     *   value's [Identity.hashCode()].
     *
     * * For a property whose compile-time type is a _union type_, for each sub-type of the union
     *   (in the order specified by the union), if the values is of that type, then re-evaluate
     *   this list of rules using that type in lieu of the union type.
     *
     * * For a property whose compile-time type is [Hashable], the implementation delegates to the
     *   [hashCode](Hashable.hashCode) function on the compile-time type.
     *
     * * Otherwise, the implementation uses the constant `0` for the property's hash-code.
     */
    @Override
    static <CompileType extends Const> Int64 hashCode(CompileType value);

    /**
     * The automatically generated implementation of the [equals] function evaluates each property
     * based on its compile-time type, with the first non-equal value resulting in `equals`
     * returning `False`; in order of precedence:
     *
     * * For a property whose compile-time type is `const`, the implementation delegates to the
     *   [equals] function on the compile-time type.
     *
     * * For a property whose compile-time type is `service`, the implementation relies on reference
     *   equality only (i.e. using the [Identity.equals] function).
     *
     * * For a property whose compile-time type is a _union type_, for each sub-type of the union
     *   (in the order specified by the union), if the two values are of that type, then re-evaluate
     *   this list of rules using that type in lieu of the union type. If the two values are not
     *   both of any one of the sub-types, then return `False`.
     *
     * * For a property whose compile-time type is [Hashable], the implementation delegates to the
     *   [equals](Hashable.equals) function on the compile-time type.
     *
     * * For a property whose compile-time type is [Orderable], the implementation delegates to the
     *   [compare] function on the compile-time type.
     *
     * * Otherwise, the implementation delegates to the [equals] function on the compile-time type.
     */
    @Override
    static <CompileType extends Const> Boolean equals(CompileType value1, CompileType value2);

    /**
     * The automatically generated implementation of the [compare] function evaluates each property
     * based on its compile-time type, and must produce a result that supports the contract defined
     * by the [Orderable] interface, such that the [compare] function must return `Equal` iff the
     * [equals] function returns `True` for the same two values. Properties are evaluated in the
     * same order as [equals]. Based on property compile-time types, in order of precedence:
     *
     * * For a property whose compile-time type is `const`, the implementation delegates to the
     *   [compare] function on the compile-time type.
     *
     * * For a property whose compile-time type is `service`, the implementation compares the two
     *   property values' [Identity.hashCode()] values.
     *
     * * For a property whose compile-time type is a _union type_, for each sub-type of the union
     *   (in the order specified by the union), if the two values are of that type, then re-evaluate
     *   this list of rules using that type in lieu of the union type. If the two values are not
     *   both of any one of the sub-types, then skip to the last rule below.
     *
     * * For a property whose compile-time type is [Orderable], the implementation delegates to the
     *   [compare] function on the compile-time type.
     *
     * * Otherwise, the implementation compares the two property values' [Identity.hashCode()]
     *   values.
     */
    @Override
    static <CompileType extends Const> Ordered compare(CompileType value1, CompileType value2);
}