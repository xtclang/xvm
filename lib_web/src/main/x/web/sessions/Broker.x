/**
 * An `SessionBroker` is a service that knows how to provide a session for an incoming request, if
 * the request is associated with a session or if the associated `Endpoint` requires a session and
 * one must be created.
 */
interface Broker
            extends Duplicable, service {
    /**
     * Find the [Session] indicated by the [request](RequestIn).
     *
     * @param request   the incoming [request](RequestIn)
     *
     * @return `True` iff a [Session] is indicated by the [request](RequestIn) and it exists
     * @return (conditional) the [Session] indicated by the [request](RequestIn)
     */
    conditional Session findSession(RequestIn request);

    /**
     * Find the [Session] indicated by the [request](RequestIn), if it exists, otherwise create one
     * if possible, returning either the new `Session` or the response to the client necessary to
     * establish the new `Session`.
     *
     * If the request indicates a session and the indicated session exists, then return it. If no
     * session exists, and one is required, then create and return it. If further communication with
     * the client is required to establish the session, or if an error occurs, then return a
     * [response](ResponseOut) for the client.
     *
     * @param request   the incoming [request](RequestIn)
     *
     * @return `True` iff this Broker is capable of handling the [Session]-related duties for the
     *         request
     * @return (conditional) the [Session] iff one is indicated by the request and it exists, or if one can be
     *         established without additional communication with the client; otherwise returns a
     *         [response](ResponseOut) to the client must be sent to establish the `Session`
     */
    conditional (Session|ResponseOut) requireSession(RequestIn request);
}
