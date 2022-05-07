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
     * Request a new object to transfer to a different service. Note that the result type is
     * expected to be masked, as this interface is not (and must not be) visible to user code, i.e.
     * this method **must not** return a type that includes this interface.
     *
     * This method is called within the context of the originating service.
     *
     * @return the appropriately [masked](Ref.maskAs) object reference that will emerge at the
     *         fiber initial call in the service to which this object is being transferred
     */
    Object transfer();
    }