import convert.Codec;

/**
 * Represents a simple outgoing HTTP [Request] that can be configured and sent by a [Client].
 */
@AutoFreezable
class SimpleRequest
        implements RequestOut, Header, Body
        implements Duplicable {

    construct(Client client, HttpMethod method, Uri uri, Object? content = Null,
              MediaType? mediaType = Null, AcceptList? accepts = Null) {

        this.client  = client;
        this.method  = method;
        this.uri     = uri;
        this.accepts = accepts ?: Everything;
    } finally {
        client.defaultHeaders.entries.forEach(entry -> add(entry));

        if (content != Null || mediaType != Null) {
            assert method.body != Forbidden;

            if (mediaType == Null) {
                if (!(mediaType := client.registry.inferMediaType(content))) {
                    throw new IllegalArgument($|Unable to find MediaType for \
                                               |"{&content.type}"
                                             );
                }
            }
            header[Header.ContentType] = mediaType.text;
            ensureBody(mediaType).from(content?);
        }
        header[Header.Accept] = this.accepts.text;
    }

    @Override
    construct(SimpleRequest that) {
        this.client  = that.client;
        this.method  = that.method;
        this.uri     = that.uri;
        this.accepts = that.accepts;
        this.entries = that.entries.as(Header.Entry[]).toArray(Mutable, inPlace=False);

        if (that.body != Null) {
            this.mediaType = that.mediaType;
            this.bytes     = new Byte[](that.bytes);
            this.body      = this;
        }
    }

    /**
     * Context for this message.
     */
    private Client client;

    // ----- SimpleRequest methods -----------------------------------------------------------------

    /**
     * Ensure that the `SimpleRequest` is not `immutable`.
     *
     * @return a mutable `SimpleRequest` copy of this request
     */
    SimpleRequest ensureMutable() = this.is(immutable) ? new(this) : this;

    // ----- Request interface ---------------------------------------------------------------------

    @Override
    HttpMethod method;

    @Override
    Uri uri;

    @Override
    AcceptList accepts;

    // ----- HttpMessage interface -----------------------------------------------------------------

    @Override
    Header header.get() = this;

    @Override
    Body ensureBody(MediaType mediaType, Boolean streaming = False) {
        // this is a simple request; it does not support streaming
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

    @Override
    immutable SimpleRequest freeze(Boolean inPlace = False) {
        return (inPlace ? this : new(this)).makeImmutable();
    }

    // ----- Header interface ----------------------------------------------------------------------

    @Override
    @RO Boolean isRequest.get() {
        return True;
    }

    @Override
    List<Header.Entry> entries = new Array();

    // ----- Body interface ------------------------------------------------------------------------

    @Override
    @Unassigned
    MediaType mediaType;

    @Override
    @Unassigned
    Byte[] bytes;

    @Override
    Body from(Object content) {
        if (content.is(Byte[])) {
            this.bytes = content;
            return this;
        }

        Type type = &content.type;
        if (Codec codec := client.registry.findCodec(mediaType, type)) {
            this.bytes = codec.encode(content);
            return this;
        }
        throw new IllegalArgument($"Unable to find Codec for Type {type} on MediaType {mediaType}");
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override
    String toString() {
        return $"{method.name} {uri}";
    }
}