interface Const
        extends collections.Hashable
        extends Orderable
    {
    /**
     * TODO
     */
    static Ordered compare(Const value1, Const value2)
        {
        // TODO check if the same const class; if not, make up a predictable answer
        // TODO same class, so either use the struct, or much better yet delegate somehow to const compare() impl
        TODO -- native
        }

    /**
     * The default implementation of comparison-for-equality for Const implementations is to
     * compare each of the fields for equality.
     */
    static Boolean equals(Const value1, Const value2)
        {
        // TODO check if the same const class; if not, return false
        // TODO same class, so either use the struct or somehow delegate to the const equals() impl
        TODO -- native
        }

    /**
     * TODO
     */
    Byte[] to<Byte[]>();
    }
