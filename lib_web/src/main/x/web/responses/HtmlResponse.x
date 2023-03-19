/**
 * The representation of a simple HTTP response containing an HTML page.
 */
@AutoFreezable
class HtmlResponse
        extends SimpleResponse
    {
    construct(String html)
        {
        construct SimpleResponse(OK, HTML, html.utf8());
        }
    }