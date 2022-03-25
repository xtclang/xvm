/**
 *
 */
interface Dispatcher
        extends Closeable
    {
    void dispatch(Protocol protocol, HttpMethod httpMethod, Path path, String? query, String? fragment);

//    Request request, Response response);
    }

