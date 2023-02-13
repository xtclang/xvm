/**
 * The base class for HTTP requests and responses.
 */
interface HttpMessage
        extends Freezable
    {
    /**
     * The header portion of this message. Note that the name is used in the singular, because it
     * does not refer to a single key/value pair, as the name "HTTP header" refers to, but rather
     * it refers to the entire collection of key/value pairs, including synthetic keys introduced
     * in HTTP/2.
     */
    @RO Header header;

    /**
     * The optional message body.
     */
    Body? body;

    /**
     * Create a body to hold the specified `MediaType`. This method is used when forming a [Request]
     * or [Response] to send. Calling this method on an incoming `Request` (received by a server) or
     * on a returned `Response` (received by a [Client]) will result in a `ReadOnly` exception.
     * Additionally, if the body already exists, and the requested `MediaType` is different than the
     * one in the existing body, this method _may_ throw an `IllegalArgument` exception.
     *
     * @param mediaType  the [MediaType] of the body
     * @param streaming  pass `True` to indicate that the body's content is not easily or
     *                   efficiently fully realizable in memory, and that the body should be
     *                   streamed if possible
     *
     * @throws ReadOnly         if the message is immutable
     * @throws IllegalArgument  if the body already exists and has a different `MediaType`
     */
    Body ensureBody(MediaType mediaType, Boolean streaming=False);

    @Override
    immutable HttpMessage freeze(Boolean inPlace = False)
        {
        // the interface for the message objects are designed to be freezable in-place; sub-classes
        // that cannot do so must override this method
        return this.is(immutable) ? this : makeImmutable();
        }

    /**
     * @return an `Iterator` of all cookie names in this message
     */
    Iterator<String> cookieNames();
    }