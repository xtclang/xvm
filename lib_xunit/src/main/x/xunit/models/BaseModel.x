
/**
 * A base class for test models.
 */
@Abstract const BaseModel
        implements Model {
    /**
     * Create a `BaseModel`.
     *
     * @param uniqueId            the unique identifier of this model
     * @param displayName         the human readable name of the test fixture this model represents
     * @param isContainer         `True` if this is a test container
     * @param constructor         the constructor to use to create the test fixture
     * @param extensionProviders  the `ExtensionProvider`s this model will add
     * @param children            the  children of this `Model`
     */
    construct (UniqueId uniqueId, String displayName, Boolean isContainer, TestMethodOrFunction? constructor = Null,
               ExtensionProvider[] extensionProviders = [], Model[] children = []) {
        this.uniqueId           = uniqueId;
        this.displayName        = displayName;
        this.isContainer        = isContainer;
        this.constructor        = constructor;
        this.children           = children;
        this.extensionProviders = extensionProviders;
    }

    @Override ExtensionProvider[] extensionProviders;

    /**
     * The constructor to use to create an instance of the test fixture.
     */
    @Override TestMethodOrFunction? constructor;

	/**
	 * The unique identifier for this model.
	 */
	@Override UniqueId uniqueId;

	/**
	 * The immutable set of children of this model.
	 */
	@Override Model[] children;

    /**
     * The human readable name for this model
     */
    @Override String displayName;

    /**
     * A flag indicating whether this model is a container of tests.
     */
    @Override Boolean isContainer;

    /**
     * The test identifier for this model.
     */
    @Override @Lazy TestIdentifier identifier.calc() {
        return new TestIdentifier(uniqueId, displayName);
    }

	@Override
	conditional Model findByUniqueId(UniqueId uniqueId) {
	    if (this.uniqueId == uniqueId) {
	        return True, this;
        }
	    for (Model child : children) {
	        if (Model found := child.findByUniqueId(uniqueId)) {
	            return True, found;
            }
        }
	    return False;
    }
}
