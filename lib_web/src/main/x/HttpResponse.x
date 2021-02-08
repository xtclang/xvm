/**
 * A representation of a http response.
 */
class HttpResponse(HttpStatus status = HttpStatus.OK)
        extends HttpMessage(new HttpHeaders())
    {
    }