import ecstasy.iterators.CompoundIterator;

import xunit.Extension;

/**
 * A registry to hold `Extension` instances.
 *
 * If the parent `ExtensionRegistry` is not `Null` the parent's extensions
 * will be visible to callers of this registry but any additions will only
 * apply to this registry.
 *
 * @param parent  an optional parent `ExtensionRegistry`
 */
class ExtensionRegistry
    {
    /**
     * An optional parent registry.
     */
    ExtensionRegistry? parent = Null;

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
     * The extensions.
     */
    private List<ExtensionHolder> extensions = new Array();

    /**
     * The types of `Extension` in the registry.
     */
    private Set<Type<Extension>> extensionTypes = new HashSet();

    /**
     * Return all the `Extension`s of a given type in order of priority, where a lower priority
     * value comes first. For `Extension`s with the default `Int.MaxValue` priority, `Extension`s
     * from the parent registry will be first.
     *
     * @param type  the type of `Extension` to return
     */
    <ExtensionType extends Extension> ExtensionType[] get(Type<ExtensionType> type) {
        return getByType(type)
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
     * Add an `Extension` to this registry.
     *
     * @param extension  the extension type to add
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
     * @param type  the `Type` of the `Extension` to return
     *
     * @return all the `Extension` instances  of a specific `Type`
     */
    private <ExtensionType extends Extension> ExtensionHolder[] getByType(Type<ExtensionType> type) {
        Array<ExtensionHolder> thisMatches = new Array();
        thisMatches.addAll(this.extensions.filter(holder -> holder.isType(type)));

        Collection<ExtensionHolder> thisPriority = thisMatches.filter(holder -> holder.isType(type))
                                                              .filter(holder -> holder.priority != Int.MaxValue)
                                                              .sorted();

        Collection<ExtensionHolder> thisNonPriority = thisMatches.filter(holder -> holder.priority == Int.MaxValue);

        ExtensionHolder[] extensions = new Array();
        ExtensionRegistry? parent = this.parent;
        if (parent.is(ExtensionRegistry) && parent.size > 0) {
            ExtensionHolder[]           parentMatches     = parent.getByType(type);
            Collection<ExtensionHolder> parentPriority    = parentMatches.filter(holder -> holder.priority != Int.MaxValue);
            Collection<ExtensionHolder> parentNonPriority = parentMatches.filter(holder -> holder.priority == Int.MaxValue);

            Collection<ExtensionHolder> priority = new Array();
            priority.addAll(parentPriority);
            priority.addAll(thisPriority);
            extensions.addAll(priority.sorted());
            extensions.addAll(parentNonPriority);
            extensions.addAll(thisNonPriority);
        } else {
            extensions.addAll(thisPriority);
            extensions.addAll(thisNonPriority);
        }
        return extensions;
    }

    // ----- inner class: ExtensionHolder -----------------------------------------------------------------------------

    /**
     * A holder of an `Extension` and its source.
     */
    private static class ExtensionHolder<ExtensionType extends Extension>(ExtensionType extension, Object? source)
            implements Orderable {
        /**
         * The priority of the extension.
         */
        @Lazy Int priority.calc() {
            Object? source = this.source;
            if (source.is(Test)) {
                if (source.priority != 0) {
                    return source.priority;
                }
            }
            return this.extension.priority;
        }

        /**
         * Determine whether this holder contains an `Extension` of a specific `Type`
         *
         * @param type  the `Type` to test
         *
         * @return `True` if this holder contains an `Extension` of the specified `Type`
         */
        Boolean isType(Type type) {
            return type.isInstance(extension);
        }

        /**
         * Order holders by priority.
         */
        static <CompileType extends ExtensionHolder> Ordered compare(CompileType value1, CompileType value2) {
            // Highest priority comes first (i.e. reverse natural Int order)
            return value1.priority <=> value2.priority;
        }
    }
}
