import ecstasy.reflect.TypedefTemplate;
import ecstasy.reflect.TypeTemplate;


/**
 * The native reflected TypedefTemplate implementation.
 */
class RTTypedefTemplate
        extends RTComponentTemplate
        implements TypedefTemplate
    {
    @Override @RO TypeTemplate referredToType;
    }
