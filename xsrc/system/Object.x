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
    /**
     * Convert this object to an object of another specified type.
     */
    ToType to<ToType extends Object>();

    /**
     * Provide a String representation of the object.
     */
    String to<String>()
        {
        // TODO implement a rudimentary to<String> showing class information
        meta.
        }

    @ro protected Meta meta;
    }

