import ecstasy.iterators.CompoundIterator;

import xunit.extensions.Extension;

/**
 * A registry to hold `Extension` instances.
 *
 * If the parent `ExtensionRegistry` is not `Null` the parent's extensions will be visible to
 * callers of this registry but any additions will only apply to this registry.
 *
 * @param parent  an optional parent `ExtensionRegistry`
 */
class ExtensionRegistry
    {
    /**
     * Create an `ExtensionRegistry`.
     *
     * @param parent  the optional parent `ExtensionRegistry`
     */
    construct (Model model, ExtensionRegistry? parent = Null) {
        this.model  = model;
        this.parent = parent;
    }

    public/private Model model;

    /**
     * An optional parent registry.
     */
    public/private ExtensionRegistry? parent = Null;

    /**
     * The extensions.
     */
    private List<ExtensionHolder> extensions = new Array();

    /**
     * The types of `Extension` in the registry.
     */
    private Set<Type<Extension>> extensionTypes = new HashSet();

    /**
     * The number of `Extension` instances in the registry.
     */
    Int size.get()
        {
        ExtensionRegistry? parent = this.parent;
        if (parent.is(ExtensionRegistry))
            {
            return extensions.size + parent.size;
            }
        return extensions.size;
        }

    /**
     * Return all the `Extension`s of a given type in order of priority, where a lower priority
     * value comes first. For `Extension`s with the default zero priority, `Extension`s from the
     * parent registry will be first.
     *
     * @param type        the type of `Extension` to return
     * @param fromParent  `True` to include the parent registries extensions
     * @param predicate   a predicate function to use to filter the extensions
     */
    <ExtensionType extends Extension> ExtensionType[] get(
            Type<ExtensionType>              type,
            Boolean                          fromParent  = True,
            Boolean                          parentFirst = True,
            function Boolean(ExtensionType)? predicate   = Null) {

        return getByType(type, fromParent, parentFirst, predicate)
            .map(holder -> holder.extension.as(ExtensionType))
            .toArray();
        }

    /**
     * Add an `Extension` to this registry.
     *
     * @param type  the extension type to add
     */
    <ExtensionType extends Extension> void add(Type<ExtensionType> type) {
        if (!isRegistered(type)) {
            extensionTypes.add(type);
            assert function ExtensionType() constructor := type.defaultConstructor();
            add(constructor(), type);
        }
    }

    /**
     * Add all the `Extension`s to this registry.
     *
     * @param extensions  the extensions to add
     * @param source      the optional source of the extensions
     */
    <ExtensionType extends Extension> void addAll(ExtensionType[] extensions, Object? source=Null) {
        for (ExtensionType extension : extensions) {
            add(extension, source);
        }
    }

    /**
     * Add an `Extension` to this registry.
     *
     * @param extension  the extension to add
     * @param source     the optional source of the extension
     */
    <ExtensionType extends Extension> void add(ExtensionType extension, Object? source = Null) {
        ExtensionHolder holder = new ExtensionHolder(extension, source);
        extensions.add(holder);
    }

    /**
     * Determine whether this registry contains an `Extension` of a specific `Type`.
     *
     * @param type  the `Type` of the `Extension`
     *
     * @return `True` if this registry contains an `Extension` of the specified `Type`
     */
    private <ExtensionType extends Extension> Boolean isRegistered(Type<ExtensionType> type) {
        if (extensionTypes.contains(type)) {
            return True;
        }
        ExtensionRegistry? parent = this.parent;
        if (parent.is(ExtensionRegistry)) {
            return parent.isRegistered(type);
        }
        return False;
    }

    /**
     * Return all the `Extension` instances  of a specific `Type`.
     *
     * @param type        the `Type` of the `Extension` to return
     * @param fromParent  `True` to include the parent registries extensions
     * @param parentFirst `True` if non-priority parent extensions should be first in the results
     * @param predicate   a predicate function to use to filter the extensions
     *
     * @return all the `Extension` instances  of a specific `Type`
     */
    private <ExtensionType extends Extension> ExtensionHolder[] getByType(
                Type<ExtensionType>              type,
                Boolean                          fromParent,
                Boolean                          parentFirst,
                function Boolean(ExtensionType)? predicate) {

        Array<ExtensionHolder> thisMatches = new Array();
        thisMatches.addAll(this.extensions
                .filter(holder -> holder.isType(type)
                        && (predicate == Null || predicate(holder.extension.as(ExtensionType)))));

        Collection<ExtensionHolder> thisPriority
                = thisMatches.filter(holder -> holder.priority != 0)
                             .sorted();

        Collection<ExtensionHolder> thisNonPriority
                = thisMatches.filter(holder -> holder.priority == 0);

        ExtensionHolder[]  extensions = new Array();
        ExtensionRegistry? parent     = this.parent;
        if (fromParent && parent.is(ExtensionRegistry) && parent.size > 0) {

            if (predicate == Null) {
                if (model.isContainer) {
                    predicate = (e) -> !e.requiresTarget;
                }
            }

            ExtensionHolder[]           parentMatches     = parent.getByType(type, fromParent, parentFirst, predicate);
            Collection<ExtensionHolder> parentPriority    = parentMatches.filter(holder -> holder.priority != 0);
            Collection<ExtensionHolder> parentNonPriority = parentMatches.filter(holder -> holder.priority == 0);

            Collection<ExtensionHolder> priority = new Array();
            priority.addAll(parentPriority);
            priority.addAll(thisPriority);
            extensions.addAll(priority.sorted());
            if (parentFirst) {
                extensions.addAll(parentNonPriority);
                extensions.addAll(thisNonPriority);
            } else {
                extensions.addAll(thisNonPriority);
                extensions.addAll(parentNonPriority);
            }
        } else {
            extensions.addAll(thisPriority);
            extensions.addAll(thisNonPriority);
        }
        return extensions;
    }

    // ----- inner class: ExtensionHolder ----------------------------------------------------------

    /**
     * A holder of an `Extension` and its source.
     */
    private static class ExtensionHolder<ExtensionType extends Extension>(ExtensionType extension,
                                                                          Object?       source)
            implements Orderable {
        /**
         * The priority of the extension.
         */
        @Lazy Int priority.calc() {
            Object? source = this.source;
            // ToDo if we ever support ordering of extensions on tests (maybe using something like
            // an @Order annotation, this is where we would check the source's order and return that
            return 0;
        }

        /**
         * Determine whether this holder contains an `Extension` of a specific `Type`
         *
         * @param type  the `Type` to test
         *
         * @return `True` if this holder contains an `Extension` of the specified `Type`
         */
        <Required> Boolean isType(Type<Required> type) {
            if (Class clz := type.fromClass()) {
                return &extension.class.implements(clz);
            }
            return extension.is(type);
        }

        /**
         * Order holders by priority.
         */
        static <CompileType extends ExtensionHolder> Ordered compare(CompileType value1, CompileType value2) {
            // Highest priority comes first (i.e. reverse natural Int order)
            return value2.priority <=> value1.priority;
        }
    }
}
