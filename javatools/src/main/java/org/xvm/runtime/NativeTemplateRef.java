package org.xvm.runtime;


import static java.util.Objects.requireNonNull;


/**
 * Immutable native-template lookup key.
 *
 * @param <T>  the native template type
 */
final class NativeTemplateRef<T extends ClassTemplate> {
    /**
     * Create a native template reference.
     *
     * @param sName        the native template name
     * @param clzTemplate  the expected template class
     *
     * @return the template reference
     */
    static <T extends ClassTemplate> NativeTemplateRef<T> of(String sName, Class<T> clzTemplate) {
        return new NativeTemplateRef<>(sName, clzTemplate);
    }

    private NativeTemplateRef(String sName, Class<T> clzTemplate) {
        f_sName       = requireNonNull(sName,       "sName");
        f_clzTemplate = requireNonNull(clzTemplate, "clzTemplate");
    }

    String getName() {
        return f_sName;
    }

    T resolve(Container container) {
        return container.getTemplate(f_sName, f_clzTemplate);
    }

    T cast(ClassTemplate template) {
        return f_clzTemplate.cast(template);
    }

    @Override
    public String toString() {
        return f_sName;
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The native template name.
     */
    private final String f_sName;

    /**
     * The expected template class.
     */
    private final Class<T> f_clzTemplate;
}
