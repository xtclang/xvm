import ecstasy.reflect.MethodTemplate;
import ecstasy.reflect.Parameter;
import ecstasy.reflect.Return;

/**
 * The native Function implementation.
 */
const RTFunction<ParamTypes extends Tuple<ParamTypes>, ReturnTypes extends Tuple<ReturnTypes>>
        extends RTSignature<ParamTypes, ReturnTypes>
        implements Function<ParamTypes, ReturnTypes>
    {
    @Override <ParamType> Function!<> bind(
                Parameter<ParamType> param, ParamType value)      { TODO("native"); }
    @Override Function!<> bind(Map<Parameter, Object> params)     { TODO("native"); }
    @Override @Op("()") ReturnTypes invoke(ParamTypes args)       { TODO("native"); }
    @Override conditional (MethodTemplate, Function, Map<Parameter, Object>)
                isFunction()                                      { TODO("native"); }
    @Override <Target> conditional
              (Target, Method<Target>, Map<Parameter, Object>)
                isMethod()                                        { TODO("native"); }

    /**
     * Helper function used by the native code.
     */
    static (Int[], Object[]) toArray(Map<Parameter, Object> params)
        {
        Int      size     = params.size;
        Int[]    ordinals = new Array<Int>(size);
        Object[] values   = new Array<Object>(size);

        loop:
        for ((Parameter param, Object value) : params)
            {
            ordinals[loop.count] = param.ordinal;
            values  [loop.count] = value;
            }
        return (ordinals, values);
        }
    }
