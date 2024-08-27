import ecstasy.reflect.ParameterTemplate;
import ecstasy.reflect.TypeTemplate;

/**
 * ParameterTemplate implementation.
 */
const RTParameterTemplate(String? name, Int index, TypeTemplate type,
                          (immutable Object)? defaultValue, Category category)
        implements ParameterTemplate {}