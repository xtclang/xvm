package org.xvm.javajit;


import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.xvm.asm.constants.TypeConstant;


/**
 * Represents the supplier of objects that are injected into a Container.
 *
 * Some Resources have a single value, such as a constant value or a singleton service. Other
 * Resources may provide a different value each time the Resource is requested; for these, a caller
 * may to ask for a Supplier up front and hold on to it via {@link #supplierOf(Resource)}, invoking
 * it once each time that injection is required. Resources that are known not to vary can be
 * obtained once via the {@link #valueOf(Resource)} method, and the resulting value can be reused
 * over and over again.
 */
public class Injector {
    /**
     * A Resource is something that can be requested for injection.
     *
     * @param type  the type of the resource to inject
     * @param name  the name of the resource to inject
     */
    public record Resource(TypeConstant type, String name) {}

    /**
     * Obtain a Supplier for the specified Resource
     *
     * @param res  the Resource that is required for dependency injection
     *
     * @return a Supplier, or null if that Resource cannot be supplied
     */
    public <T> Supplier<T> supplierOf(Resource res) {
        return null;
    }

    /**
     * Obtain the value of the specified Resource. Some Resources have a single value, such as a
     * constant value or a singleton service. Other Resources may provide a different value each
     * time it is requested.
     *
     * @param res  the Resource identifier
     *
     * @return the value of the specified Resource, or null
     */
    public <T> T valueOf(Resource res) {
        return supplierOf(res) instanceof Supplier supply ? ((Supplier<T>) supply).get() : null;
    }

    /**
     * If an Injector knows what Resources it can provide, it provides that information via this
     * method.
     *
     * @return a Map keyed by Resource with a corresponding indicator of whether the Resource is
     *         available as a fixed singleton value (which can be obtained via {@link #valueOf})
     */
    public Map<Resource, Boolean> known() {
        return Collections.emptyMap();
    }

    /**
     * If an Injector can validate what Resources it can provide based on a known set of requests,
     * it can provide that information via this method.
     *
     * @param resources  a collection of Resources that may be requested
     *
     * @return a Map keyed by Resource with a corresponding indicator of whether the Resource is
     *         available as a fixed singleton value (which can be obtained via {@link #valueOf});
     *         anything not in this Map is not known for certain to be available, but may still be
     *         available upon request
     */
    public Map<Resource, Boolean> confirm(Collection<Resource> resources) {
        var known = new HashMap<>(known());
        known.keySet().retainAll(resources);
        return known;
    }
}
