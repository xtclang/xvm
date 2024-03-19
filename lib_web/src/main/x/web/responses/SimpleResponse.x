/**
 * The representation of a simple HTTP response that may contain only an HTTP status.
 */
@AutoFreezable
class SimpleResponse
        implements ResponseIn
        implements ResponseOut
        implements Header
        implements Body {

    construct(HttpStatus status    = OK,
              MediaType? mediaType = Null,
              Byte[]?    bytes     = Null,
             ) {
        if (WebService svc := this:service.is(WebService)) {
            this.request = svc.request;
        }

        this.status    = status;
        this.mediaType = mediaType ?: Json;
        this.bytes     = bytes ?: [];
    } finally {
        if (mediaType != Null || bytes != Null) {
            this.body = this;
        }
    }


    // ----- HttpMessage interface -----------------------------------------------------------------

    @Override
    Header header.get() {
        return this;
    }

    @Override
    Body? body;

    @Override
    Body ensureBody(MediaType mediaType, Boolean streaming=False) {
        // this is a simple response; it does not support streaming
        assert:TODO !streaming;

        Body? body = this.body;
        if (body == Null || mediaType != body.mediaType) {
            this.mediaType = mediaType;
            this.bytes     = [];
            this.body      = this;
            return this;
        }

        return body;
    }


    // ----- Response interface --------------------------------------------------------------------

    @Override
    Request? request;

    @Override
    HttpStatus status;


    // ----- Header interface ----------------------------------------------------------------------

    @Override
    @RO Boolean isRequest.get() {
        return False;
    }

    @Override
    List<Entry> entries.get() {
        if (assigned) {
            return super();
        }

        // an unassigned list of entries on an immutable response means that we froze without adding
        // any
        if (this.is(immutable)) {
            return [];
        }

        // need to create a mutable (but freezable) List of Entry
        // TODO could make a "safe" wrapper that validates the entries
        set(new Entry[]);
        return super();
    }


    // ----- Body interface ------------------------------------------------------------------------

    @Override
    MediaType mediaType;

    @Override
    Byte[] bytes;

    @Override
    Body from(Object content) {
        throw new Unsupported();
    }


    // ----- debugging support ---------------------------------------------------------------------

    @Override
    String toString() {
        Body? body = this.body;
        return body != Null && body.bytes.size > 0 && (mediaType == Json || mediaType == Text)
            ? $"{status} : {body.bytes.unpackUtf8()}"
            : status.toString();
    }
}