public interface Meta
    {
    /**
     * This property represents the immutability of an object. Once the object
     * is immutable, it cannot be made mutable.
     */
    Boolean immutable;

    /**
    * The containing module.
    */
    Module module;

    /**
     * The read-only struct for this object
     */
    Struct struct;

    /**
    * TODO
    */
    TypeComposition class;
    }
