import executor.ExecutionLifecycle;

import xunit.extensions.ExtensionProvider;

/**
 * A model that wraps another model.
 */
@Abstract const WrapperModel<ModelType extends Model>
        implements Model {

    /**
     * Constructs a new wrapper model.
     *
     * @param delegate  the wrapped model
     * @param id        an optional unique identifier to override that of the delegate model
     */
    construct(ModelType delegate, UniqueId? uniqueId = Null){
        this.delegate = delegate;
        if (uniqueId.is(UniqueId)) {
            this.uniqueId = uniqueId;
        } else {
            this.uniqueId = delegate.uniqueId;
        }
    }

    ModelType delegate;

	@Override UniqueId uniqueId;

	@Override UniqueId? parentId.get() = delegate.parentId;

	@Override Iterable<Model> children.get() = delegate.children;

    @Override String displayName.get() = delegate.displayName;

    @Override Constructor? constructor.get() = delegate.constructor;

    @Override ExtensionProvider[] extensionProviders.get() = delegate.extensionProviders;

    @Override Int priority.get() = delegate.priority;

    @Override Boolean isContainer.get() = delegate.isContainer;

	@Override Set<Model> getDescendants() = delegate.getDescendants();

	@Override Boolean isRoot() = delegate.isRoot();

	@Override Boolean mayRegisterTests() = delegate.mayRegisterTests();

	@Override conditional Model findByUniqueId(UniqueId uniqueId)
	        = delegate.findByUniqueId(uniqueId);

    @Override ExecutionLifecycle createExecutionLifecycle() = delegate.createExecutionLifecycle();

    @Override Int estimateStringLength() = uniqueId.estimateStringLength();

    @Override Appender<Char> appendTo(Appender<Char> buf) = uniqueId.appendTo(buf);
}
