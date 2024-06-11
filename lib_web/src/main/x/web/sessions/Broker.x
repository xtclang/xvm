/**
 * An `SessionBroker` is a service that knows how to provide a session for an incoming request, if
 * the request is associated with a session or if the associated `Endpoint` requires a session and
 * one must be created.
 */
interface Broker
            extends Duplicable, service {
    /**
     * Find the session indicated by the request. If the request indicates a session and the
     * indicated session exists, then return it. If no session exists, and one is required, then
     * create an return it. If further communication with the client is required to establish the
     * session, or if an error occurs, then return a response for the client.
     *
     * @param request   the incoming request
     * @param required  `True` iff a [Session] is required
     *
     * @return the session iff one is indicated by the request and it exists, or if one is required
     *         (in which case it is created by this method); otherwise `Null`; if a response to the
     *         client must be sent related to the session, then return that response instead of the
     *         session
     */
    Session?|ResponseOut findSession(RequestIn request, Boolean required);
}
