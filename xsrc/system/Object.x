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
    @Inject protected Meta<Object:public, Object:protected, Object:private, Object:struct> meta;

    /**
     * By default, comparing any two objects will only result in equality if they are the
     * same object, or if they are two constant objects with identical values.
     */
    static <CompileType extends Object> Boolean equals(CompileType o1, CompileType o2)
        {
        return &o1 == &o2;
        }

    /**
     * Provide a String representation of the object.
     *
     * This is intended primarily for debugging, log messages, and other diagnostic features.
     */
    String toString()
        {
        // the Object's rudimentary to<String> shows class information only
        return meta.class_.toString();
        }

    /**
     * Make this object immutable.
     */
    immutable Object makeImmutable()
        {
        if (!meta.isImmutable)
            {
            meta.isImmutable = true;
            }

        return this.as(immutable Object);
        }
    }