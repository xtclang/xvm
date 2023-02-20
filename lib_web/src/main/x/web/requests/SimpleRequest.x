import codecs.Codec;
import codecs.Registry;

/**
 * Represents a simple outgoing HTTP [Request] that can be configured and sent by a [Client].
 */
class SimpleRequest
        implements RequestOut
        implements Header
        implements Body
    {
    construct(Client client, HttpMethod method, Uri uri)
        {
        this.client = client; // REVIEW: all we need it the Registry
        this.method = method;
        this.uri    = uri;
        }

    /**
     * Context for this message.
     */
    private Client client;


    // ----- RequestOut interface ------------------------------------------------------------------

    @Override
    SimpleRequest with(Uri?        uri      = Null,
                       Protocol?   protocol = Null,
                       Path?       path     = Null,
                       AcceptList? accepts  = Null,
                       )
        {
        TODO CP
        }


    // ----- Request interface ---------------------------------------------------------------------

    @Override
    HttpMethod method;

    @Override
    Uri uri;

    @Override
    AcceptList accepts = Nothing;


    // ----- HttpMessage interface -----------------------------------------------------------------

    @Override
    Header header.get()
        {
        return this;
        }

    @Override
    Body ensureBody(MediaType mediaType, Boolean streaming=False)
        {
        // this is a simple request; it does not support streaming
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

    @Override
    immutable SimpleRequest freeze(Boolean inPlace = False)
        {
        return (inPlace ? this : with()).makeImmutable();
        }


    // ----- Header interface ----------------------------------------------------------------------

    @Override
    @RO Boolean isRequest.get()
        {
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
    Body from(Object content)
        {
        Type type = &content.actualType;
        if (Codec codec := client.registry.findCodec(mediaType, type))
            {
            bytes = codec.encode(content);
            return this;
            }
        throw new IllegalArgument($"Unable to find Codec for Type {type} on MediaType {mediaType}");
        }


    // ----- debugging support ---------------------------------------------------------------------

    @Override
    String toString()
        {
        return $"{method.name} {uri}";
        }
    }