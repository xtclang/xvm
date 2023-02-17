/**
 * A representation of the body of an HTTP message.
 */
interface Body
        extends Freezable
    {
    /**
     * The headers for this body and -- if available -- for the message containing this body.
     */
    @RO Header header;

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
    conditional Int knownSize()
        {
        // this should be overridden by any Body implementation that may not have the contents
        // stored in the `bytes` property
        return bytes.knownSize();
        }

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
     * Fill in the body's content from the specified object content.
     *
     * @param content  an object that can be mapped to this body's [mediaType] via a codec
     *
     * @return the Body
     */
    Body from(Object content);

    /**
     * Convert the body content to the specified type.
     *
     * @param type  the desired result type
     *
     * @return True iff the content was successfully turned into a result of the desired type
     * @return (conditional) the result
     */
    <Result> conditional Result to(Type<Result> type)
        {
        return False;
        }

    /**
     * In order to _consume_ the body as a stream of bytes, the caller can use this method to obtain
     * a stream that the bytes can be read from.
     *
     * By using this method, it may be possible for the body to be streamed without having to buffer
     * the entire body in memory.
     *
     * @return a BinaryInput that provides the body as a stream
     */
    BinaryInput bodyReader()
        {
        return new ecstasy.io.ByteArrayInputStream(bytes);
        }

    /**
     * In order to _consume_ the body as a stream of bytes, the caller can use this method to
     * provide a stream that will receive the bytes of the body.
     *
     * By using this method, it may be possible for the body to be streamed without having to buffer
     * the entire body in memory.
     *
     * @param receiver  the BinaryOutput stream that the bytes of the body will written to
     */
    void streamBodyTo(BinaryOutput receiver)
        {
        receiver.writeBytes(bytes);
        }

    /**
     * In order to _provide_ the body as a stream of bytes, the caller can use this method, passing
     * a stream that contains the bytes of the body.
     *
     * By using this method, it may be possible for the body to be streamed without having to buffer
     * the entire body in memory.
     *
     * @param source  the InputStream that provides the bytes of the body
     */
    void streamBodyFrom(InputStream source)
        {
        bytes = source.readBytes(source.size);
        }
    }