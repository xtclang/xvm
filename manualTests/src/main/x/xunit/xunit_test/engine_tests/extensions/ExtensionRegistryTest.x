import xunit.Extension;

import xunit_engine.extensions.ExtensionRegistry;

/**
 * Tests for the `ExtensionRegistry` class.
 */
class ExtensionRegistryTest {
    @Test
    void shouldBeEmpty() {
        ExtensionRegistry registry = new ExtensionRegistry();
        assert:test registry.size == 0;
    }

    @Test
    void shouldHaveCorrectSize() {
        ExtensionRegistry registry = new ExtensionRegistry();
        registry.add(ExtensionStubOne);
        registry.add(ExtensionStubTwo);
        assert:test registry.size == 2;
    }

    @Test
    void shouldGetExtensionByType() {
        ExtensionRegistry registry = new ExtensionRegistry();
        registry.add(ExtensionStubOne);
        registry.add(ExtensionStubTwo);

        ExtensionStubOne[] extensions = registry.get(ExtensionStubOne);
        assert:test extensions.size == 1;
    }

    @Test
    void shouldGetExtensionByTypeIncludingSubClasses() {
        ExtensionRegistry registry = new ExtensionRegistry();
        registry.add(ExtensionStubOneSubOne);
        registry.add(ExtensionStubOneSubTwo);
        registry.add(ExtensionStubTwo);

        ExtensionStubOne[] extensions = registry.get(ExtensionStubOne);
        assert:test extensions.size == 2;
        assert:test extensions.any(e -> e.is(ExtensionStubOneSubOne));
        assert:test extensions.any(e -> e.is(ExtensionStubOneSubTwo));
    }

    @Test
    void shouldGetExtensionsInPriorityOrder() {
        ExtensionRegistry registry = new ExtensionRegistry();
        registry.add(PriorityExtensionDefault);
        registry.add(PriorityExtensionStubOne);
        registry.add(PriorityExtensionStubTwo);
        registry.add(PriorityExtensionStubThree);
        registry.add(ExtensionStubTwo);

        PriorityExtension[] extensions = registry.get(PriorityExtension);
        assert:test extensions.size == 4;
        assert:test extensions[0].is(PriorityExtensionStubOne);
        assert:test extensions[1].is(PriorityExtensionStubThree);
        assert:test extensions[2].is(PriorityExtensionStubTwo);
        assert:test extensions[3].is(PriorityExtensionDefault);
    }

    @Test
    void shouldGetParentExtensions() {
        ExtensionRegistry parent   = new ExtensionRegistry();
        ExtensionRegistry registry = new ExtensionRegistry();
        registry.parent = parent;
        parent.add(ExtensionStubOne);
        parent.add(ExtensionStubTwo);

        ExtensionStubOne[] extensions = registry.get(ExtensionStubOne);
        assert:test extensions.size == 1;
    }

    @Test
    void shouldGetGrandParentExtensions() {
        ExtensionRegistry grandParent = new ExtensionRegistry();
        ExtensionRegistry parent      = new ExtensionRegistry();
        ExtensionRegistry registry    = new ExtensionRegistry();
        parent.parent   = grandParent;
        registry.parent = parent;
        grandParent.add(ExtensionStubOne);
        grandParent.add(ExtensionStubTwo);

        ExtensionStubOne[] extensions = registry.get(ExtensionStubOne);
        assert:test extensions.size == 1;
    }


    static const ExtensionStubOne
            implements Extension {
    }

    static const ExtensionStubOneSubOne
            extends ExtensionStubOne {
    }

    static const ExtensionStubOneSubTwo
            extends ExtensionStubOne {
    }

    static const ExtensionStubTwo
            implements Extension {
    }

    static const PriorityExtension(Int priority = Int.MaxValue)
            implements Extension {
    }

    static const PriorityExtensionDefault
            extends PriorityExtension {
    }

    static const PriorityExtensionStubOne
            extends PriorityExtension(1) {
    }

    static const PriorityExtensionStubTwo
            extends PriorityExtension(100) {
    }

    static const PriorityExtensionStubThree
            extends PriorityExtension(10) {
    }
}