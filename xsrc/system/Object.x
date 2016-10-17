/**
 * Everything is an object, and every object is of a class that extends this Object class. As such, this class
 * represents the root of the class hierarchy, and all object references always include the public portion of the
 * interface of this class. Since everything is an object and an object is only accessible via a reference, the Object
 * class also represents the concept of a referent.
 */
public class Object
    {
    /**
     * The Type property provides the type of the reference that was used to obtain the value of the Type property.
     */
    public readonly Type type;

    /**
     * TODO - this shouldn't exist?!?!?
     * The Class property represents the class that contains the code that is .. no, that can't be right
     * .. provides the class of the TODO .. it's not the "virtual" class, it's "this" class, i.e. the
     * one that the code is on that is asking the question
     */
    private readonly Class class;
    // TODO need some sort of composite Class that represents Traits, Mixins, various interfaces, etc.
    // TODO it comes from the perspective through the reference, i.e. the same "object" could have a trait applied through one ref and not another
    // TODO consider a single "meta" property

    /**
     * Obtain a reference to this object that matches the specified type. This method may result in a reference that
     * contains methods that are not present in the reference used to invoke this method, and this method is likely to
     * result in a reference that does not contain methods that are present in the reference used to invoke this method.
     * The purpose of this method is to cast from one reference to a carefully controlled and narrow reference to the
     * same object, preventing access through the resulting reference to any methods not present in the specified type.
     *
     * @param T  the type of the desired reference to this object
     */
    public T as<T>()
        {
        return T.bind(this);
        }

    /**
     * Conversion.
     * TODO discuss immutable objects
     */
    public T to<T>()
        {
        if (
        }
    }
