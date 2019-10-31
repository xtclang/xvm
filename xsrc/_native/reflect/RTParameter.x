import Ecstasy.reflect.Parameter;

/**
 * Parameter implementation.
 */
const RTParameter<ParamType>(Int ordinal, String? name, Boolean formal, Boolean hasDefault, ParamType? valDefault)
        implements Parameter<ParamType>
    {
    @Override
    conditional String hasName()
        {
        return name == null
                ? False
                : True, name.as(String);
        }

    @Override
    conditional ParamType defaultValue()
        {
        return hasDefault
                ? (True, valDefault.as(ParamType))
                : False;
        }

    /**
     * TODO GG Helper method invoked from native code to create an array of parameters.
     */
//    static Parameter<>[] toParameterArray(Type[] types, String?[] names, Int formalCount, Boolean[] defaulted, Object[] values)
//        {
//        Int count = types.size;
//
//        assert:arg names.size == count;
//        assert:arg formalCount >= 0 && formalCount <= count;
//        assert:arg defaulted.size == count;
//        assert:arg values.size == count;
//
//        return new Array<Parameter<>>(count, i ->
//                {
//                Type type = types[i];
//                return new RTParameter<type.DataType>(i, names[i], i < formalCount, defaulted[i], values[i].as(type.DataType));
//                }
//        }
    }