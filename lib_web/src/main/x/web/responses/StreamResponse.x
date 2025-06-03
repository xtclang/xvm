/**
 * The representation of a streaming HTTP response.
 */
@AutoFreezable
class StreamResponse
        implements ResponseIn
        implements ResponseOut
        implements Header
        implements Body {

    construct(HttpStatus   status    = OK,
              MediaType?   mediaType = Null,
              BinaryInput? source    = Null,
             ) {
        assert WebService svc := this:service.is(WebService) as "StreamingRequest is out of context";

        this.svc       = svc;
        this.status    = status;
        this.mediaType = mediaType ?: Binary;
        this.source    = source.is(service) ? source : svc.new BinaryInputProxy(source?);
    }

    /**
     * The underlying `WebService`.
     */
    protected WebService svc;

    /**
     * The `BinaryInput` to stream the body from.
     *
     * Note, that since the response is [Freezable], this value should be [Sharable].
     */
    protected BinaryInput? source;

    // ----- HttpMessage interface -----------------------------------------------------------------

    @Override
    Header header.get() = this;

    @Override
    Body body.get() = this;

    @Override
    Body ensureBody(MediaType mediaType, Boolean streaming = True) {
        assert:arg streaming as "StreamResponse only supports streaming";
        return this;
    }

    // ----- Response interface --------------------------------------------------------------------

    @Override
    Request? request.get() = svc.request;

    @Override
    HttpStatus status;

    // ----- Header interface ----------------------------------------------------------------------

    @Override
    @RO Boolean isRequest.get() = False;

    @Override
    @Unassigned List<Entry> entries.get() {
        if (assigned) {
            return super();
        }

        // an unassigned list of entries on an immutable response means that we froze without adding
        // any
        if (this.is(immutable)) {
            return [];
        }

        // need to create a mutable (but freezable) List of Entry
        set(new Entry[]);
        return super();
    }

    // ----- Body interface ------------------------------------------------------------------------

    @Override
    MediaType mediaType;

    @Override
    Byte[] bytes.get() = throw new Unsupported("Only streaming is supported");

    @Override
    Boolean streaming.get() = True;

    @Override
    Body from(Object content) = throw new Unsupported();

    @Override
    BinaryInput bodyReader() = source ?: assert as "The source has not been set";

    @Override
    void streamBodyFrom(BinaryInput source) {
        assert this.source == Null as "The source has been already set";
        this.source = source.is(service) ? source : svc.new BinaryInputProxy(source);
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override
    String toString() = status.toString();
}