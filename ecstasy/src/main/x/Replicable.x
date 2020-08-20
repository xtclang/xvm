/**
 * A Replicable object is one that is capable of producing new, default-state copies of itself.
 * Replicable is the interface for _"virtual new, using the default constructor"_.
 *
 * For example:
 *
 *     class Base implements Replicator { ... }
 *     class Derived extends Base { ... }
 *
 *     Base b1 = new Derived();
 *     // ...
 *     Base b2 = b1.new(); // creates a "new Derived()"
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
