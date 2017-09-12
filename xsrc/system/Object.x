/**
 * This class represents the capabilities that are common to every Ecstasy object; this class is the
 * single inheritance root for the Ecstasy type system. In other words, "everything is an object",
 * and every object is of a class that extends this Object class. An object is reachable through a
 * reference, and an object reference always includes the public portion of the interface of this
 * class. Some meta-information about the object is available through the reference to the object,
 * represented by the {@link Ref} interface; additional meta-information about an object is
 * available within the object itself, via its own protected {@link meta} property.
 */
class Object
    {
    /**
     * The meta-data for each object is represented by the Meta interface.
     */
    protected Meta<Object:public, Object:protected, Object:private> meta.get()
        {
        // the Meta object is provided by the runtime
        return super();
        }

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
     *
     * This is intended primarily for debugging, log messages, and other diagnostic features.
     */
    String to<String>()
        {
        // the Object's rudimentary to<String> shows class information only
        return meta.class_.to<String>();
        }

    /**
     * Obtain a read-only array of length 1 containing a reference to this object.
     */
    Object[] to<Object[]>()
        {
        return {this}.as(Object[]);
        }

    /**
     * Obtain a read-only tuple of one element containing a reference to this object.
     */
    Tuple<Object> to<Tuple<Object>>()
        {
        return Tuple:(this);
        }

    /**
     * A reference to any object can be used to provide a function that returns a
     * reference to that same object.
     */
    @auto function Object() to<function Object()>()
        {
        return () -> this;
        }
    }

