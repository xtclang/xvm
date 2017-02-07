/**
 * This class represents the capabilities that are common to every Ecstasy object;
 * it is the single inheritance root for the Ecstasy type system.
 * <p>
 * In Ecstasy, "everything is an object", and every object is of a class that extends
 * this Object class. As such, this class
 * represents the root of the class hierarchy, and all object references always include the public portion of the
 * interface of this class. Since everything is an object and an object is only accessible via a reference, the Object
 * class also represents the concept of a referent.
 */
class Object
    {
    @ro protected Meta meta;

    /**
     * By default, comparing any two objects will only result in equality if they are the
     * same object, or if they are two constant objects with identical values.
     */
    static Boolean equals(Object o1, Object o2)
        {
        return &o1 == &o2;
        }
        
    /**
     * Provide a String representation of the object.
     */
    String to<String>()
        {
        // TODO implement a rudimentary to<String> showing class information
        return meta.class.to<String>();
        }
    
    /**
     * Obtain a read-only array of length 1 containing a reference to this object.
     */
    Object[] to<Object[]>()
        {
        return new this:type[] {this};
        }
        
    /**
     * Obtain a read-only tuple of one element containing a reference to this object.
     */
    (Object) to<(Object)>()
        {
        return (this);
        }

    /**
     * A reference to any object can be used to provide a function that returns a
     * reference to that same object.
     */
    @auto function Object() to<function Object()>()
        {
        return () -> return this;
        }
    }

