package org.xvm.runtime;


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Objects.requireNonNull;

import org.xvm.runtime.template.Proxy;


/**
 * The container-owned lookup table for native templates.
 *
 * <p>Native templates are built per container - {@link NativeContainer} constructs a complete set
 * for every instance, and a single JVM can build several. Every template therefore carries an
 * owner ({@link ClassTemplate#f_container}), and a lookup has to answer with the template that
 * belongs to the container doing the asking.</p>
 *
 * <p>This class is the one place that answers that question. It replaces the {@code INSTANCE}
 * static that each native template used to publish from its own constructor: a process-global
 * mutable field, written by a {@code this}-escape, which the last container to be constructed
 * silently took over from every container built before it.</p>
 *
 * <h2>How a template is found</h2>
 *
 * <p>Most native templates are addressable by name in the container's own registry, and the name
 * is derived from the template class - {@code org.xvm.runtime.template.numbers.xFloat64} is the
 * template for {@code numbers.Float64}. Those are resolved lazily, on first use, through
 * {@link Container#getTemplate(String, Class)}; nothing is published while a template is still
 * being constructed.</p>
 *
 * <p>A minority of templates have no name of their own, because they implement a composite type
 * declared by some other template - the bit/byte/nibble arrays, the array delegates and the array
 * views, and {@code Identity}. Those are handed to {@link #register} by the template that declares
 * them, from {@link ClassTemplate#registerNativeTemplates()}, which runs after construction has
 * finished.</p>
 *
 * <p>{@link Proxy} has neither a name nor a declaring template: it borrows {@code Object}'s
 * structure and exists only to back proxied service handles. It is created on demand, once per
 * container.</p>
 */
public final class NativeTemplates {
    /**
     * The owning container. Always a {@link NativeContainer}; nested containers share their
     * native container's templates, exactly as {@link Container#getTemplate(String)} does.
     */
    private final Container f_container;

    /**
     * The resolved templates, keyed by template class.
     */
    private final ConcurrentMap<Class<?>, ClassTemplate> f_mapByClass = new ConcurrentHashMap<>();

    /**
     * The package prefix shared by every native template class.
     */
    private static final String TEMPLATE_PACKAGE = "org.xvm.runtime.template.";

    NativeTemplates(Container container) {
        f_container = requireNonNull(container, "container");
    }


    // ----- resolving the table -------------------------------------------------------------------

    // The adapter: whatever owner-bearing thing is in hand, this is its table. Its job is
    // normalising the four inputs - in particular ClassTemplate and TypeComposition, which have no
    // lookup method of their own and are not meant to grow one.
    //
    // The other two ways in are Container.nativeTemplates(), the table itself for bulk work, and
    // Container.nativeTemplate(clz) / Frame.nativeTemplate(clz), the shorthand for a single lookup.
    // Three layers, not three spellings of one thing; none of them is redundant.

    /**
     * @return the native template table for the specified container
     */
    public static NativeTemplates of(Container container) {
        return requireNonNull(container, "container").nativeTemplates();
    }

    /**
     * @return the native template table for the container the specified frame runs in
     */
    public static NativeTemplates of(Frame frame) {
        return of(requireNonNull(frame, "frame").container());
    }

    /**
     * @return the native template table for the specified template's container
     */
    public static NativeTemplates of(ClassTemplate template) {
        return of(requireNonNull(template, "template").f_container);
    }

    /**
     * @return the native template table for the container the specified composition belongs to
     */
    public static NativeTemplates of(TypeComposition composition) {
        return of(requireNonNull(composition, "composition").getContainer());
    }


    // ----- looking a template up -----------------------------------------------------------------

    /**
     * Obtain this container's native template of the specified class.
     *
     * <p>This is the container-scoped replacement for the old {@code Xxx.INSTANCE} static: the
     * answer is always owned by this table's container, never by whichever container happened to
     * be constructed most recently.</p>
     *
     * @param clzTemplate  the native template class
     *
     * @return the template of that class belonging to this container
     */
    public <T extends ClassTemplate> T get(Class<T> clzTemplate) {
        ClassTemplate template = f_mapByClass.get(requireNonNull(clzTemplate, "clzTemplate"));
        if (template == null) {
            // Resolve outside of the map's update path: a lookup can recurse during bootstrap, and
            // computeIfAbsent() forbids recursive updates to the same map.
            template = clzTemplate == Proxy.class
                    ? new Proxy(f_container)
                    : f_container.getTemplate(componentNameOf(clzTemplate), clzTemplate);

            ClassTemplate templateExisting = f_mapByClass.putIfAbsent(clzTemplate, template);
            if (templateExisting != null) {
                template = templateExisting;
            }
        }
        return clzTemplate.cast(template);
    }

    /**
     * Add a template that cannot be found by name because it implements a composite type declared
     * by another template.
     *
     * <p>Called from {@link ClassTemplate#registerNativeTemplates()}, after the template has been
     * fully constructed - the map write is what publishes it.</p>
     *
     * <p>The last registration for a class wins, which is what the {@code INSTANCE} assignment in
     * the constructor did. It matters: {@code xRTDelegate} registers a nibble delegate twice, and
     * the second one is the template the runtime used to hand out.</p>
     *
     * @param template  the fully constructed template to publish
     */
    void register(ClassTemplate template) {
        f_mapByClass.put(requireNonNull(template, "template").getClass(), template);
    }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Derive the component name a native template is registered under from its class name, using
     * the same rule {@link NativeContainer} uses when it scans for template classes:
     * {@code org.xvm.runtime.template.numbers.xFloat64} backs {@code numbers.Float64}.
     *
     * @param clzTemplate  the native template class
     *
     * @return the component name
     */
    static String componentNameOf(Class<? extends ClassTemplate> clzTemplate) {
        String sClass = clzTemplate.getName();

        assert sClass.startsWith(TEMPLATE_PACKAGE) : sClass + " is not a native template";

        String sPath   = sClass.substring(TEMPLATE_PACKAGE.length());
        int    ofDot   = sPath.lastIndexOf('.');
        String sSimple = sPath.substring(ofDot + 1);

        assert sSimple.charAt(0) == 'x' : sClass + " has no derivable component name";

        return sPath.substring(0, ofDot + 1) + sSimple.substring(1);
    }
}
