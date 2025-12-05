import xunit.SkipResult;
import xunit.MethodOrFunction;

/**
 * A model that wraps another model.
 *
 * @param delegate  the wrapped model
 */
@Abstract const WrapperMethodModel<ModelType extends MethodModel>
        extends WrapperModel<ModelType>
        implements MethodModel {

    construct(ModelType delegate, UniqueId? id = Null) {
        construct WrapperModel(delegate, id);
    }

    @Override Class testClass.get() = delegate.testClass;

    @Override MethodOrFunction testMethod.get() = delegate.testMethod;

    @Override SkipResult skipResult.get() = delegate.skipResult;
}