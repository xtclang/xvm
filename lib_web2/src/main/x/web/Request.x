import net.SocketAddress;
import net.URI;

/**
 * A representation of an HTTP request.
 *
 * TODO doc how to get a request passed to an end point
 */
interface Request
        extends HttpMessage
    {
    /**
     * The request line. For HTTP v1, the request line is directly in the message; for HTTP v2 and
     * HTTP v3, this information is spread across a number of synthetic header entries, so the
     * `requestLine` has to be built from that information.
     */
    @RO String requestLine;

    /**
     * The protocol over which the request was received, if it is known.
     *
     * For an out-going request, the protocol is the requested protocol to use to send the request;
     * a client implementation may choose to use a different protocol if necessary.
     */
    @RO Protocol? protocol;

    /**
     * The IP address and port number used by the client to issue the request, if it is known.
     */
    @RO SocketAddress? client;

    /**
     * The IP address and port number used by the server to receive the request, if it is known.
     */
    @RO SocketAddress? server;

    /**
     * TODO
     */
    @RO String authority;

    /**
     * TODO
     */
    @RO String path;

    /**
     * The URI of the request.
     */
    URI uri;

    /**
     * The HTTP method ("GET", "POST", etc.)
     */
    HttpMethod method;

    /**
     * The accepted media types.
     */
    MediaType[] accepts;

    /**
     * The HTTP parameters contained with the URI query string.
     * REVIEW what about parameters located in the body?
     */
    Map<String, List<String>> parameters;


// TODO ignore:
//    /**
//     * The accepted media types.
//     *
//     * @return the accepted media types.
//     */
//    MediaType[] accepts.get()
//        {
//        List<MediaType> accepts = new Array();
//        if (List<String> list := getAll("Accept"))
//            {
//            for (String mt : list)
//                {
//                for (String s : mt.split(','))
//                    {
//                    accepts.add(new MediaType(s));
//                    }
//                }
//            }
//        return accepts.toArray();
//        }
//
//    /**
//     * The request or response content type.
//     *
//     * @return the content type
//     */
//    MediaType? getContentType()
//        {
//        if (String ct := get("Content-Type"))
//            {
//            return new MediaType(ct);
//            }
//        return Null;
//        }
//
//    /**
//     * Set the request or response content type.
//     *
//     * @param mediaType  the content type
//     */
//    void setContentType(MediaType? mediaType)
//        {
//        if (mediaType != Null)
//            {
//            set("Content-Type", mediaType.name);
//            }
//        else
//            {
//            headers.remove("Content-Type");
//            }
//        }
//
//    /**
//     * Set the request or response content type.
//     *
//     * @param mediaType  the content type
//     */
//    void setContentType(String? mediaType)
//        {
//        if (mediaType != Null)
//            {
//            set("Content-Type", mediaType);
//            }
//        else
//            {
//            headers.remove("Content-Type");
//            }
//        }
//
//    /**
//     * The request or response content length.
//     *
//     * @return a True iff the content length header is present
//     * @return the content type
//     */
//    Int? contentLength.get()
//        {
//        if (String len := get("Content-Length"))
//            {
//            return new IntLiteral(len).toInt64();
//            }
//        return Null;
//        }

    }
