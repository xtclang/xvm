/**
 * A representation of a received HTTP response.
 */
interface ResponseIn
        extends Response
    {
    @Override
    @RO RequestOut? request;

    /**
     * Obtain the value of the specified cookie, if it is included in the response.
     *
     * @return True iff the specified cookie name is present
     * @return (conditional) the value associated with the specified cookie
     */
    conditional Cookie getCookie(String name)
        {
        for (String value : header.valuesOf(Header.SET_COOKIE))
            {
            // TODO parse name, and if it matches, build the Cookie object
            }
        return False;
        }
    }