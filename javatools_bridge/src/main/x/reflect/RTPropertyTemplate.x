import ecstasy.reflect.MethodTemplate;
import ecstasy.reflect.PropertyTemplate;
import ecstasy.reflect.TypeTemplate;


/**
 * The native reflected PropertyTemplate implementation.
 */
class RTPropertyTemplate
        extends RTComponentTemplate
        implements PropertyTemplate
    {
    @Override @RO TypeTemplate type;
    @Override @RO Boolean      isConstant;

    @Override conditional Const hasInitialValue();
    @Override conditional MethodTemplate hasInitializer();
    }
