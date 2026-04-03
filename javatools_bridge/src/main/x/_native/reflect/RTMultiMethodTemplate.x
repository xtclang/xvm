import ecstasy.reflect.MethodTemplate;
import ecstasy.reflect.MultiMethodTemplate;


/**
 * The native reflected MultiMethodTemplate implementation.
 */
const RTMultiMethodTemplate
        extends RTComponentTemplate
        implements MultiMethodTemplate {
    @Override
    MethodTemplate[] children();
}
