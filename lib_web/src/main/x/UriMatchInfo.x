/**
 * Information of a route endpoint that matches a URI.
 */
interface UriMatchInfo
    {
    /**
     * The matched URI.
     */
    @RO String uri;

    /**
     * The variable values following a successful match.
     */
    @RO Map<String, Object> variableValues;

    /**
     * The list of template variables.
     */
    @RO List<UriMatchVariable> variables;

    /**
     * A map of the variables.
     */
    @RO Map<String, UriMatchVariable> variableMap;
    }
