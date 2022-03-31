import ecstasy.io.ByteArrayOutputStream;

import codec.MediaTypeCodec;
import codec.MediaTypeCodecRegistry;

/**
 * A representation of an HTTP response.
 */
class HttpResponse(HttpStatus status = HttpStatus.OK)
        extends HttpMessage(new HttpHeaders())
    {
    construct(HttpStatus status, String[] names, String[][] values, Byte[]? body)
        {
        this.status = status;
        construct HttpMessage(new HttpHeaders(names, values), body);
        }

    /**
     * Process the `Tuple` returned from a request handler into a `HttpResponse`.
     *
     * @param tuple      the `Tuple` of return values from the endpoint method execution
     * @param method     the HTTP request method (i.e. GET, POST, etc)
     * @param mediaType  the media type of the response body
     *
     * @return a HttpResponse
     */
    static HttpResponse encodeResponse(Tuple tuple, Int index, HttpMethod method, MediaType mediaType,
                                       MediaTypeCodecRegistry codecRegistry)
        {
        if (index > 0 && tuple[index].is(HttpResponse))
            {
            // the endpoint returned a HttpResponse so use that as the response
            return tuple[index].as(HttpResponse);
            }

        HttpResponse response = new HttpResponse();

        if (tuple.size <= index)
            {
            // method had a void return type so there is no response body
            if (method.permitsRequestBody)
                {
                // method allows a body so set the length to zero
                response.headers.add("Content-Length", "0");
                }
            }
        else
            {
            // Iterate over the return values from the endpoint assigning them to the
            // relevant parts of the request (REVIEW CP: report duplicates?)
            Object body = Null;
            for (Int i : [index..tuple.size))
                {
                Object o = tuple[i];
                if (o.is(HttpStatus))
                    {
                    response.status = o;
                    }
                else if (o.is(MediaType))
                    {
                    mediaType = o;
                    }
                else if (o != Null)
                    {
                    body = o;
                    }
                }

            if (body != Null)
                {
                if (body.is(Byte[]))
                    {
                    response.body = body;
                    }
                else
                    {
                    // convert the body to the required response media type
                    if (MediaTypeCodec codec := codecRegistry.findCodec(mediaType))
                        {
                        response.body = codec.encode(body);
                        }
                    // ToDo: the else should probably be an error/exception
                    }
                }
            }

        response.headers.add("Content-Type", mediaType.name);

        return response;
        }

    void send(HttpServer httpServer, Object context)
        {
        (String[] names, String[][] values) = headers.toArrays();

        httpServer.send(context, status.code, names, values, body ?: []);
        }
    }