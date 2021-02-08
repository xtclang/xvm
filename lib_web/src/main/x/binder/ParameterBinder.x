import ecstasy.reflect.Parameter;

/**
 * An interface capable of binding a value from a specific source to a Parameter.
 * 
 * The selection of a ParameterBinder is done by the ParameterBinderRegistry. Selection could
 * be based on type, annotation or other factors, for example, the request media type.
 *
 * @param <ParamType> the parameter type
 * @param <Source>    the type of the value source
 */
public interface ParameterBinder<Source>
    {
    /**
     * The default priority value used to order ParameterBinder instances.
     */
    static Int DefaultPriority = 0;

    /**
     * The priority value for this ParameterBinder.
     * This value is used to order multiple ParameterBinder instances
     * that might apply to a parameter.
     */
    @RO Int priority.get()
        {
        return DefaultPriority;
        }

    /**
     * Bind the given parameter from the given source.
     *
     * @param param   the Parameter to bind
     * @param source  the source of values to bind to the Parameter
     *
     * @return the result of the binding
     */
    <ParamType> BindingResult<ParamType> bind(Parameter<ParamType> param, Source source);

    /**
     * Returns True if this binder can bind the specified Parameter.
     */
    Boolean canBind(Parameter param)
        {
        return True;
        }
    }
