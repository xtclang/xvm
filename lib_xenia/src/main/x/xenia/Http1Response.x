/**
 * An implementation of an HTTP/1 (i.e. 0.9, 1.0, 1.1) response, as sent by a server or receivied by
 * a client.
 */
const Http1Response
    {
    // TODO

    static (Int status, String[] headerNames, String[] headerValues, Byte[] body) prepare(Response response)
        {
        Int      status       = response.status.code;
        String[] headerNames  = response.header.names.toArray(Mutable);
        String[] headerValues = []; // TODO
        Byte[]   body         = []; // TODO also add a header
        return status, headerNames, headerValues, body;
        }
    }