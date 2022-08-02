/**
 * The base class for HTTP requests and responses.
 */
interface HttpMessage
        extends Freezable
    {
    /**
     * The header of this message.
     */
    @RO Header header;

    /**
     * The optional message body.
     */
    Body? body;

    /**
     * Create a body to hold the specified `MediaType`.
     *
     * @param mediaType  the [MediaType] of the body
     * @param streaming  pass `True` to indicate that the body's content is not easily or
     *                   efficiently fully realizable in memory, and that the body should be
     *                   streamed if possible
     */
    Body ensureBody(MediaType mediaType, Boolean streaming=False)
        {
        return body?;

        if (this.is(Body))
            {
            return this;
            }

        assert !this.is(immutable);

        TODO Body body = new ();
        this.body = body;
        return body;
        }

    @Override
    immutable HttpMessage freeze(Boolean inPlace = False)
        {
        // the interface for the message objects are designed to be freezable in-place; sub-classes
        // that cannot do so must override this method
        return makeImmutable();
        }
    }