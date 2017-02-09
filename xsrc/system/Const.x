interface Const
        extends Hashable
    {
    /**
     * TODO
     */
    static Ordered compare(Const value1, Const value2)
        {
        // TODO check if the same const class; if not, make up a predictable answer
        // TODO same class, so either use the struct, or much better yet delegate somehow to const compare() impl
        }

    /**
     * TODO
     */
    static Boolean equals(Const value1, Const value2)
        {
        // TODO check if the same const class; if not, return false
        // TODO same class, so either use the struct or somehow delegate to the const equals() impl
        }

    /**
     * Represent the value of the Const as a String. By default, this method recursively
     * decomposes the Const into a tree of its constituent values.
     * <p>
     * This is particularly useful for debugging, log messages, and other diagnosis
     * features.
     */
    String to<String>()
        {
        // TODO return something JSON like for complex const types
        }

    /**
     * TODO
     */
    Byte[] to<Byte[]>()
        {
        Field[] fields = meta.struct.to<Field[]>();
        // TODO use meta.struct
        }
    
    /**
     * TODO
     */
    @lazy UInt hash.get()
        {
        // TODO use meta.struct
        }
    }
