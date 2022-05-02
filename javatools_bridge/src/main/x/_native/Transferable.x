/**
 * An internal-only interface representing an object that is capable of producing an object to pass
 * across a service boundary. This interface creates an explicit exception to the rules of Ecstasy,
 * in that it allows a mutable object to be passed (transferred) through a service boundary; as a
 * result, this capability is not a standard part of the language, and it is reserved for internal
 * use only, i.e. as an internal implementation detail to optimize specific use cases within the
 * standard library.
 */
interface Transferable
    {
    /**
     * Request a new object to transfer to a different service.
     *
     * This method is called within the context of the originating service.
     */
    Transferable transfer();
    }