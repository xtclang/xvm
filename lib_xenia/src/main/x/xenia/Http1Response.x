import web.Body;
import web.Header;

/**
 * An implementation of an HTTP/1 (i.e. 0.9, 1.0, 1.1) response, as sent by a server or received by
 * a client.
 */
const Http1Response
    {
    // TODO

    /**
     * Helper to transform a [Response] object into the constituent pieces used to send a response
     * via the HTTP/1-based prototype.
     */
    static (Int status, String[] headerNames, String[] headerValues, Byte[] body) prepare(ResponseOut response)
        {
        Int      status       = response.status.code;
        String[] headerNames  = new String[];
        String[] headerValues = new String[];
        Byte[]   bytes        = [];

        for (val kv : response.header.entries)
            {
            headerNames  += kv[0];
            headerValues += kv[1];
            }

        if (Body body ?= response.body)
            {
            bytes = body.bytes;
            headerNames  += Header.CONTENT_TYPE;
            headerValues += body.mediaType.text;
            headerNames  += Header.CONTENT_LENGTH;
            headerValues += bytes.size.toString();
            }

        return status, headerNames, headerValues, bytes;
        }
    }