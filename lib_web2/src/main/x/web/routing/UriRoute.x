/**
 * Represents a Route that matches a URI.
 */
interface UriRoute
        extends Route
        extends UriMatcher
        extends Orderable
        extends Stringable
    {
    @RO UriMatchTemplate uriMatchTemplate;

    @RO ExecutableFunction executable;

    @RO PreProcessor[] preProcessors;

    @RO PostProcessor[] postProcessors;

    @Override
    conditional UriRouteMatch match(URI uri)
        {
        return match(uri.toString());
        }

    @Override
    conditional UriRouteMatch match(String uri);

    // ----- Orderable methods -----------------------------------------------------------------

    @Override
    static <CompileType extends UriRoute> Boolean equals(CompileType value1, CompileType value2)
        {
        return value1.uriMatchTemplate == value2.uriMatchTemplate;
        }

    @Override
    static <CompileType extends UriRoute> Ordered compare(CompileType value1, CompileType value2)
        {
        return value1.uriMatchTemplate <=> value2.uriMatchTemplate;
        }
    }
