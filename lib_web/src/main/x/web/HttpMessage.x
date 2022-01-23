/**
 * The base class for http requests and responses.
 */
class HttpMessage
    {
    construct(HttpHeaders headers, Object? body = Null)
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
    public Object? body;

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