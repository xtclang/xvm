/**
 * A representation of an HTTP request.
 */
interface Request
        extends HttpMessage
    {
    /**
     * The request line. For HTTP v1, the request line is directly in the message; for v2 and v3,
     * this information is spread across a number of synthetic header entries.
     */
    @RO String requestLine;

    @RO Protocol protocol;

    @RO String authority;

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
     * @return the HTTP parameters contained with the URI query string
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
