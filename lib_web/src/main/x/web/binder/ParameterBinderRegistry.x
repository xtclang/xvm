import ecstasy.reflect.Parameter;

/**
 * A registry of ParameterBinder instances for a given source type.
 *
 * @param <Source> the source type
 */
interface ParameterBinderRegistry<Source>
    {
    /**
     * Adds a parameter binder to the registry.
     *
     * @param binder the binder to add
     */
    void addParameterBinder(ParameterBinder<Source> binder)
        {
        throw new UnsupportedOperation("Binder registry is not mutable");
        }

    /**
     * Locate a ParameterBinder for the given parameter and source type.
     *
     * @param parameter the parameter to bind a value to
     * @param source    the source of values to bind to the parameter
     * @param <T>       the parameter type
     *
     * @return True iff a ParameterBinder exists for the given parameter
     * @return the ParameterBinder for the given parameter
     */
    conditional ParameterBinder<Source> findParameterBinder(Parameter parameter, Source source);
    }
