/**
 * Represents a route to an endpoint for an HTTP request.
 */
interface Route
    {
    /**
     * The media-types that the route consumes.
     */
    @RO MediaType[] consumes;

    /**
     * The media-types that the route produces.
     */
    @RO MediaType[] produces;

    /**
     * Determine whether this route matches a specific HttpMethod type.
     */
    Boolean matchesMethod(HttpMethod method)
        {
        return True;
        }
    }