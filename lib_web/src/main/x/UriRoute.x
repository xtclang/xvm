/**
 * Represents a Route that matches a URI.
 */
interface UriRoute
        extends Route
        extends UriMatcher
        extends Orderable
    {
    @RO UriMatchTemplate uriMatchTemplate;

    @RO ExecutableFunction executable;

    @Override
    conditional UriRouteMatch match(URI uri)
        {
        return match(uri.toString());
        }

    @Override
    conditional UriRouteMatch match(String uri);
    }
