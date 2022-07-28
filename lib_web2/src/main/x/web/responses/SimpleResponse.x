import ecstasy.io.ByteArrayOutputStream;

/**
 * The representation of a simple HTTP response that contains only an HTTP status.
 */
const SimpleResponse(HttpStatus status)
        implements Response;
