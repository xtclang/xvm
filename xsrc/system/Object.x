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
public class Object
    {
    @ro protected Meta meta;

    /**
     * By default, comparing any two objects will only result in equality if they are the
     * same object, or if they are two constant objects with identical values.
     */
    function Boolean equals(Object o1, Object o2)
        {
        return &o1 == &o2;
        }
        
    /**
     * Provide a String representation of the object.
     */
    String to<String>()
        {
        // TODO implement a rudimentary to<String> showing class information
        meta.
        }

    /**
     * Obtain a 
     */
    Sequence<Object> to<Sequence<Object>>()
    Tuple<Object> to<Tuple<Object>>()
    }

