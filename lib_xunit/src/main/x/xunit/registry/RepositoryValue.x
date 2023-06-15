/**
 * A holder for resource objects and their (optional) `Observer`s.
 *
 * The `Observer` will be invoked when `close()` is invoked on this object.
 * Furthermore, if the provided resource implements `Closeable`, its `close()`
 * method will be invoked.
 */
class RegistryValue<RegisterAs, Resource>(Resource resource, Observer<RegisterAs>? observer)
        implements Closeable {

    @Override
    void close(Exception? cause = Null) {
        Observer<RegisterAs>? observer = this.observer;
        Resource              resource = this.resource;

        if (observer.is(Observer)) {
            observer.onClosing(resource.as(RegisterAs), cause);
        }

        if (resource.is(Closeable)) {
            resource.close(cause);
        }

        if (observer.is(Observer)) {
            observer.onClosed(resource.as(RegisterAs), cause);
        }
    }
}