/**
 * This type represents the relationship between an inner and an outer class.
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
