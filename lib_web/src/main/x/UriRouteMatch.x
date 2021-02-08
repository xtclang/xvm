import ecstasy.reflect.Parameter;

/**
 * A route that matches a URI and HttpMethod.
 */
public interface UriRouteMatch
        extends RouteMatch
        extends UriMatchInfo
    {
    /**
     * @return the UriRoute
     */
    @RO UriRoute route;
    }