/**
 * The base class for HTTP requests and responses.
 */
interface HttpMessage
    {
    /**
     * The containing HttpMessage, if this message is a part of a multi-part message.
     */
    @RO HttpMessage!? parentMessage;

    /**
     * The headers for this message. For a nested message, the Header also include all of the
     * headers from the parent message.
     */
    @RO Header headers;

    /**
     * Each HttpMessage either has no body, exactly one body, or is a multi-part message which
     * contains nested HttpMessages.
     */
    enum ContentArity {None, Single, Multi}

    /**
     * The arity of the content of the HttpMessage.
     */
    @RO ContentArity contentArity;

    /**
     * The optional message body. Note that a multi-part message does **not** have a body; rather,
     * it is represented as a sequence of HttpMessages.
     */
    Body ensureBody();

    /**
     * Determine if the message is a multi-part message, and if it is, provide an Iterator over the
     * sequence of parts.
     *
     * Because a multi-part message may be large (e.g. containing files), and because it may not yet
     * have all arrived, the returned type is an Iterator, and not an Array or even an Iterable.
     * This is a necessary trade-off in order to support streaming messages, and because even a
     * simple question like [Iterable.size] (the number of parts of the multi-part message) cannot
     * be determined for a streaming message until after all the parts have been processed. As a
     * result, **this method should only be called one time** for a given message, since the data
     * may not be buffered and thus a second iteration through that data would not be possible.
     */
    List<HttpMessage> ensureMultiPart();
    }