import xunit.SkipResult;
import xunit.MethodOrFunction;

/**
 * A model that wraps another model.
 */
@Abstract const WrapperMethodModel<ModelType extends MethodModel>
        extends WrapperModel<ModelType>
        implements MethodModel {

    /**
     * Constructs a new wrapper model.
     *
     * @param delegate  the wrapped model
     * @param id        an optional unique identifier to override that of the delegate model
     */
    construct(ModelType delegate, UniqueId? id = Null) {
        construct WrapperModel(delegate, id);
    }

    @Override Class testClass.get() = delegate.testClass;

    @Override MethodOrFunction testMethod.get() = delegate.testMethod;

    @Override SkipResult skipResult.get() = delegate.skipResult;
}