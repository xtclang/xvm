interface Const
        extends Hashable
    {
    static Ordered compare(Const value1, Const value2)
        {
        // TODO check if the same const class; if not, make up a predictable answer
        // TODO same class, so either use the struct, or much better yet delegate somehow to const compare() impl
        }

    static Boolean equals(Const value1, Const value2)
        {
        // TODO check if the same const class; if not, return false
        // TODO same class, so either use the struct or somehow delegate to the const equals() impl
        }

    String to<String>()
        {
        // TODO return something JSON like for complex const types
        }
    
    @lazy UInt hash.get()
        {
        // TODO use struct
        }
    }
