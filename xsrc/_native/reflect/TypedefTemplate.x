import Ecstasy.reflect.TypedefTemplate;
import Ecstasy.reflect.TypeTemplate;


/**
 * The native reflected TypedefTemplate implementation.
 */
class RTTypedefTemplate
        extends RTComponentTemplate
        implements TypedefTemplate
    {
    @Override @RO TypeTemplate referredToType;
    }
