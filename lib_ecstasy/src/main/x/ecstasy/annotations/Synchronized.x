/**
 * The Synchronized mixin is used to annotate classes, properties, methods, constructors or functions,
 * to indicate that the class, property, method, constructor or function is **not** safe for
 * concurrent/reentrant execution.
 *
 * Imagine a demonstrably concurrent-unsafe implementation of a counter:
 *
 *     @Concurrent class Counter
 *         {
 *         private Int counter = 0;
 *         Int next()
 *             {
 *             Int n = counter + 1;
 *             this:service.yield(); // allows other fibers to execute before this line completes
 *             counter = n;          // possible corruption: storing a stale value
 *             return n;
 *             }
 *         }
 *
 * Any one of the following three actions would disallow the `yield()` call in the above example
 * from executing other fibers:
 *
 * * The `next()` method could be annotated with `@Synchronized`;
 *
 * * Since the class is mutable, the class is _implicitly_ `@Synchronized`, and so the `@Concurrent`
 *   annotation could simply be removed;
 *
 * * The `@Concurrent` annotation could be replaced with the `@Synchronized` annotation.
 *
 * Miscellaneous notes:
 *
 * * Regardless of the class' and method's annotations, or the lack thereof, the ability to prevent
 *   other fibers from executing requires only a single execution frame (**any** frame) for the
 *   current fiber to be implicitly or explicitly `@Synchronized`.
 *
 * * In the absence of any annotation on a class, `@Synchronized` is implied for object instances of
 *   the class that are mutable, and [`@Concurrent`](Concurrent) is implied for object instances of
 *   the class that are immutable.
 *
 * * When both `@Synchronized` and `@Concurrent` appear on the same class, property, or method, the
 *   `@Synchronized` annotation takes precedence.
 */
mixin Synchronized
        into Class | Property | Method | Function
    {
    }