import ecstasy.reflect.Parameter;

/**
 * Parameter implementation.
 */
const RTParameter<ParamType>(Int ordinal, String? name, Boolean formal, Boolean hasDefault, ParamType? valDefault)
        implements Parameter<ParamType> {
    @Override
    conditional String hasName() {
        return name == Null
                ? False
                : (True, name);
    }

    @Override
    conditional ParamType defaultValue() {
        return hasDefault
                ? (True, valDefault.as(ParamType))
                : False;
    }
}