import Ecstasy.reflect.MethodTemplate;
import Ecstasy.reflect.Parameter;
import Ecstasy.reflect.Return;

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
    @Override FutureVar<ReturnTypes> invokeAsync(ParamTypes args) { TODO("native"); }
    }
