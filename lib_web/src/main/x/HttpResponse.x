/**
 * A representation of an http response.
 */
class HttpResponse(HttpStatus status = HttpStatus.OK)
        extends HttpMessage(new HttpHeaders())
    {
    }