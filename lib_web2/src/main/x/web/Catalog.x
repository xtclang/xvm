/**
 * TODO
 */
interface Catalog
    {
    // data about endpoints etc.
    // TODO

    /**
     * Create a dispatcher that is capable of delivering an incoming request to any of the Catalog's
     * endpoints.
     *
     * @return a new [Dispatcher]
     */
    Dispatcher createDispatcher();
    }

