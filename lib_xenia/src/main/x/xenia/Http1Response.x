import web.Body;
import web.Header;

/**
 * An implementation of an HTTP/1 (i.e. 0.9, 1.0, 1.1) response, as sent by a server or received by
 * a client.
 */
const Http1Response {
    /**
     * Helper to transform a [Response] object into the constituent pieces used to send a response
     * via the HTTP/1-based prototype.
     *
     * @return status          the response `Status-Code`, as defined by rfc2616
     * @return headerNames     an array of `field-name` strings, each as defined by rfc2616
     * @return headerValues    an array of `field-value` corresponding to `headerNames`
     * @return responseLength  if positive, specifies a fixed response body length;
     *                         if negative, then there is no response body;
     *                         if zero, then chunked encoding is used and the body should be streamed
     */
    static (Int      status,
            String[] headerNames,
            String[] headerValues,
            Int      responseLength)
        prepare(ResponseOut response) {

        Int      status         = response.status.code;
        String[] headerNames    = new String[];
        String[] headerValues   = new String[];
        Int      responseLength = -1;

        for (val kv : response.header.entries) {
            headerNames  += kv[0];
            headerValues += kv[1];
        }

        if (Body body ?= response.body) {
            headerNames  += Header.ContentType;
            headerValues += body.mediaType.text;
            if (body.streaming) {
                responseLength = 0;
                headerNames   += Header.TransferEncoding;
                headerValues  += "chunked";
            } else {
                responseLength = body.bytes.size;
                headerNames   += Header.ContentLength;
                headerValues  += responseLength.toString();
            }
        }

        return status,
               headerNames .freeze(inPlace=True),
               headerValues.freeze(inPlace=True),
               responseLength;
    }
}