import ecstasy.io.ByteArrayOutputStream;

/**
 * The representation of a simple HTTP response that contains only an HTTP status.
 */
class SimpleResponse
        implements Response
        implements Header
        implements Body
    {
    construct(HttpStatus status    = OK,
              MediaType? mediaType = Null,
              Byte[]?    bytes     = Null,
             )
        {
        if (WebService svc := this:service.is(WebService))
            {
            this.request = svc.request;
            }

        this.status = status;
        }
    finally
        {
        if (mediaType != Null || bytes != Null)
            {
            ensureBody(mediaType ?: Json);
            this.bytes = bytes?;
            }
        }


    // ----- HttpMessage interface -----------------------------------------------------------------

    @Override
    Header header.get()
        {
        return this;
        }

    @Override
    Body? body;

    @Override
    Body ensureBody(MediaType mediaType, Boolean streaming=False)
        {
        // this is a simple response; it does not support streaming
        assert:TODO !streaming;

        Body? body = this.body;
        if (body == Null || mediaType != body.mediaType)
            {
            this.mediaType = mediaType;
            this.bytes     = [];
            this.body      = this;
            return this;
            }

        return body;
        }


    // ----- Response interface --------------------------------------------------------------------

    @Override
    Request? request.get()
        {
        Request? request = super();
        if (request == Null, WebService svc := this:service.is(WebService), request ?= svc.request)
            {
            set(request);
            }
        return request;
        }


    // ----- Header interface ----------------------------------------------------------------------

    @Override
    @RO Boolean isRequest.get()
        {
        return False;
        }

    @Override
    List<Entry> entries.get()
        {
        if (assigned)
            {
            return super();
            }

        // an unassigned list of entries on an immutable response means that we froze without adding
        // any
        if (this.is(immutable))
            {
            return [];
            }

        // need to create a mutable (but freezable) List of Entry
        // TODO could make a "safe" wrapper that validates the entries
        return new Entry[];
        }


    // ----- Body interface ------------------------------------------------------------------------

    @Override
    @Unassigned
    MediaType mediaType;

    @Override
    @Unassigned
    Byte[] bytes;

    @Override
    Body from(Object content)
        {
        TODO
        }
    }
