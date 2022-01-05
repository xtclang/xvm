/**
 * Transient is a compile-time annotation for properties that **significantly** modifies the normal
 * property contract, in the following ways:
 *
 * * The value of a `@Transient` property is **always** permitted to be modified, _even when the
 *   property it part of an immutable object_. This is the fundamental change to the contract, and
 *   the primary purpose of the annotation; it allows an otherwise-immutable object to be mutated,
 *   and further, it allows an otherwise-immutable object to hold a reference to a mutable value.
 *
 * * All `@Transient` properties are _service-local_; a value set within the realm of one service is
 *   **never** visible from within the realm of another service. In other words, the contents of a
 *   `@Transient` property are **never** passed through a service boundary.
 *
 * * Because they are never passed through a service boundary, the value in a `@Transient` property
 *   does not need to be `immutable` or even [Shareable], and is not automatically _frozen_ or
 *   otherwise made immutable when the containing object is passed through a service boundary. This
 *   allows the _content_ of the property to be a mutable object, even when the containing object is
 *   itself _immutable_.
 *
 * * When an object with one or more `@Transient` properties is passed through a service boundary,
 *   the object that emerges on the other side of the boundary (i.e. within the other service) will
 *   have an initial property value for each `@Transient` property, as defined by that property, or
 *   the property will be _unassigned_ if the it does not have an initial value.
 *
 * * It is a compile-time error for a `@Transient` property to not have an initial value (either
 *   explicit or implicit), unless the property also has the [@Unassigned](UnassignedVar) or
 *   [@Lazy](LazyVar) annotation.
 *
 * * `@Transient` properties on a `const` object are **not** included in any of the following
 *   automatically-generated capabilities of the object:
 * * * The equality comparison, as defined by [Comparable];
 * * * The ordering comparison, as defined by [Orderable];
 * * * The hashing operation, as defined by [Hashable];
 * * * The string rendering operations, as defined by `toString()` on the [Object] interface, and by
 *     the methods of the [Stringable] interface.
 *
 * The primary use case for `@Transient` properties is to support the caching of information that is
 * computationally expensive or otherwise latency-inducing to produce, particularly when the cached
 * information may itself change over time or cannot be passed through a service boundary. One
 * should not use the `@Transient` annotation when the `@Lazy` annotation would suffice.
 *
 * It is self-evident that the `@Transient` annotation can be misused to circumvent immutability
 * within the realm of a particular service, but this does not violate any tenets of the XVM memory
 * model. Furthermore, there is a designed manner to hold a reference to mutable information on an
 * otherwise-immutable object: Simply hold a reference to a `service` that manages the mutable
 * state.
 */
mixin Transient
        into Property
    {
    }
