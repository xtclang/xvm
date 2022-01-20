/**
 * This type represents the relationship between an inner and an outer class. An inner class with
 * a reference to its outer class will automatically implement the [Outer.Inner] interface.
 */
interface Outer
    {
    interface Inner
        {
        @RO Outer outer.get()
            {
            return this.Outer;
            }
        }
    }
