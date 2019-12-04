import Ecstasy.reflect.MethodTemplate;
import Ecstasy.reflect.MultiMethodTemplate;


/**
 * The native reflected MultiMethodTemplate implementation.
 */
class RTMultiMethodTemplate
        extends RTComponentTemplate
        implements MultiMethodTemplate
    {
    @Override Iterator<MethodTemplate> children();
    }
