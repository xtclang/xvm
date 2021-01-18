import ecstasy.reflect.MethodTemplate;
import ecstasy.reflect.MultiMethodTemplate;


/**
 * The native reflected MultiMethodTemplate implementation.
 */
class RTMultiMethodTemplate
        extends RTComponentTemplate
        implements MultiMethodTemplate
    {
    @Override
    Iterator<MethodTemplate> children();
    }
