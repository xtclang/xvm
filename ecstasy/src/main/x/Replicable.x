/**
 * A Replicable object is one that is capable of producing new, default-state copies of itself.
 * Replicable is the interface for _"virtual new, using the default constructor"_.
 *
 * For example:
 *
 *     class BaseClass implements Replicable { ... }
 *     class DerivedClass extends BaseClass { ... }
 *
 *     BaseClass b1 = new DerivedClass();
 *
 *     // even though the compile-time type of both b1 and b2 is BaseClass, this will instantiate a
 *     // new DerivedClass, because that is the runtime type of b1
 *     BaseClass b2 = b1.new();
 */
interface Replicable
    {
    /**
     * Construct a new replica of this object, but with the default initial state for this object's
     * class, by using the default (no parameter) constructor.
     *
     * This is a virtual "default constructor".
     */
    construct();
    }
