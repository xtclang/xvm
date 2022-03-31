/**
 * The base class for HTTP requests and responses.
 */
class HttpMessage
    {
    construct(HttpHeaders headers, Byte[]? body = Null)
        {
        this.headers    = headers;
        this.body       = body;
        this.attributes = new HttpAttributes();
        }

    /**
     * The headers for this message.
     */
    public/protected HttpHeaders headers;

    /**
     * The optional message body.
     */
    public Byte[]? body;

    /**
     * The message attributes.
     */
    public/protected HttpAttributes attributes;

    /**
     * The content type, if set in the headers.
     */
    MediaType? contentType.get()
        {
        return headers.getContentType();
        }
    }