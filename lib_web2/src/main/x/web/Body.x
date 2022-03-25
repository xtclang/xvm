/**
 * A representation of the body of an HTTP message.
 */
interface Body
    {
    /**
     * The headers for this message.
     */
    @RO Header headers;

    /**
     * The media type (aka MIME type) of the body.
     */
    MediaType mediaType;

    /**
     * Determine if the size of the body is known.
     *
     * For a body being received that is being streamed, its size will not be known if the
     * `Content-Length` header is not present and the body has not been fully buffered. For a body
     * being sent, its size will not be known if the [bodyWriter] is used to stream it, and no
     * `knownSize` was provided to that method.
     *
     * @return True iff the size of the body is known
     * @return (conditional) the size in bytes of the body
     */
    conditional Int knownSize();

    /**
     * The bytes of the body.
     *
     * When the body size may be large, such as when receiving or sending the contents of a file,
     * using this property will consume the amount of memory corresponding to the entire body size.
     * To avoid this, use the [bodyReader] or [streamBodyTo] methods to receive the body, and use
     * the [bodyWriter] or [streamBodyFrom] methods to send the body.
     *
     * Note: The caller should assume that the array of bytes obtained from this property is
     * immutable.
     */
    Byte[] bytes;

    /**
     * In order to _consume_ the body as a stream of bytes, the caller can use this method to obtain
     * a stream that the bytes can be read from.
     *
     * By using this method, it may be possible for the body to be streamed without having to buffer
     * the entire body in memory.
     *
     * @return a BinaryInput that provides the body as a stream
     */
    BinaryInput bodyReader();

    /**
     * In order to _consume_ the body as a stream of bytes, the caller can use this method to
     * provide a stream that will receive the bytes of the body.
     *
     * By using this method, it may be possible for the body to be streamed without having to buffer
     * the entire body in memory.
     *
     * @param receiver  the BinaryOutput stream that the bytes of the body will written to
     */
    void streamBodyTo(BinaryOutput receiver);

    /**
     * In order to _provide_ the body as a stream of bytes, the caller can use this method to obtain
     * a stream that the body's bytes can be written to.
     *
     * By using this method, it may be possible for the body to be streamed without having to buffer
     * the entire body in memory.
     *
     * @param knownSize  (optional) if the number of bytes is known at this point, then the caller
     *                   should provide it so that the information can be included in the header
     *
     * @return the BinaryOutput to write the bytes of the body to
     */
    BinaryOutput bodyWriter(Int knownSize=0);

    /**
     * In order to _provide_ the body as a stream of bytes, the caller can use this method, passing
     * a stream that contains the bytes of the body.
     *
     * By using this method, it may be possible for the body to be streamed without having to buffer
     * the entire body in memory.
     *
     * @param source  the InputStream that provides the bytes of the body
     */
    void streamBodyFrom(InputStream source);
    }
