/**
 * The Concurrent annotation is used to annotate classes, properties, and methods, to indicate that the
 * class, property, or method **is safe** for concurrent/reentrant execution.
 *
 * Imagine a simple, provably reentrant- and concurrent-safe counter implementation, with silly
 * 'doSomething()' blocking call to another service added, for purposes of this example:
 *
 *     class Counter {
 *         private Int counter;
 *         private SomeService svc;
 *         Int next() {
 *             Int n = ++counter; // read, modify, and write the value without interruption
 *             svc.doSomething();
 *             return n;
 *         }
 *     }
 *
 * In most cases, one would expect that an instance of this class is mutable. As such, the class
 * (and thus the method) are implicitly `@Synchronized`, and thus the call to `doSomething()` does
 * not actually allow other fibers to execute.
 *
 * Any one of the following actions would mark this code "safe" for executing other fibers during
 * the call to `doSomething()`:
 *
 * * The `next()` method could be annotated with `@Concurrent`;
 *
 * * The `Counter` class could be annotated with `@Concurrent`.
 *
 * Miscellaneous notes:
 *
 * * Regardless of the class' and method's annotations, or the lack thereof, the ability to allow
 *   other fibers to execute requires each and every execution frame for the current fiber to be
 *   implicitly or explicitly `@Concurrent`.
 *
 * * In the absence of any annotation on a class, `@Synchronized` is implied for object instances of
 *   the class that are mutable, and [`@Concurrent`](Concurrent) is implied for object instances of
 *   the class that are immutable.
 *
 * * When both `@Synchronized` and `@Concurrent` appear on the same class, property, or method, the
 *   `@Synchronized` annotation takes precedence.
 *
 * * Special care should be taken when marking default interface methods as `@Concurrent`: if the
 *   implementation calls any method that can potentially "yield", doing it **while** retaining any
 *   state (e.g. using a non-concurrent iterator over a collection) should be avoided.
 */
annotation Concurrent
        into Class | Property | Method {
}
