import ecstasy.reflect.TypedefTemplate;
import ecstasy.reflect.TypeTemplate;


/**
 * The native reflected TypedefTemplate implementation.
 */
const RTTypedefTemplate
        extends RTComponentTemplate
        implements TypedefTemplate {

    @Override @RO TypeTemplate referredToType;
}
