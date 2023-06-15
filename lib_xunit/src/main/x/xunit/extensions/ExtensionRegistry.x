import ecstasy.iterators.CompoundIterator;

/**
 * A registry to hold `Extension` instances.
 *
 * @param parent  an optional parent `ExtensionRegistry`
 */
class ExtensionRegistry(ExtensionRegistry? parent = Null)
    {
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
     * Return all the `Extension`s of a given type in order of priority.
     *
     * @param type  the type of `Extension` to return
     */
    <ExtensionType extends Extension> ExtensionType[] get(Type<ExtensionType> type)
        {
        return getByType(type)
            .sorted()
            .map(holder -> holder.extension.as(ExtensionType))
            .toArray();
        }

    /**
     * Return all the `Extension`s of a given type in reverse order of priority.
     *
     * @param type  the type of `Extension` to return
     */
    <ExtensionType extends Extension> ExtensionType[] reversed(Type<ExtensionType> type)
        {
        return getByType(type)
            .sorted((holder1, holder2) -> holder2 <=> holder1)
            .map(holder -> holder.extension.as(ExtensionType))
            .toArray();
        }

    /**
     * Add an `Extension` to this registry.
     *
     * @param type  the extension type to add
     */
    <ExtensionType extends Extension> void add(Type<ExtensionType> type)
        {
        if (!isRegistered(type))
            {
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
    <ExtensionType extends Extension> void add(ExtensionType extension, Object? source = Null)
        {
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
    private <ExtensionType extends Extension> Boolean isRegistered(Type<ExtensionType> type)
        {
        if (extensionTypes.contains(type))
            {
            return True;
            }
        ExtensionRegistry? parent = this.parent;
        if (parent.is(ExtensionRegistry))
            {
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
    private <ExtensionType extends Extension> Iterator<ExtensionHolder> getByType(Type<ExtensionType> type)
        {
        ExtensionRegistry? parent = this.parent;
        Iterator<ExtensionHolder> it;
        if (parent.is(ExtensionRegistry))
            {
            it = new CompoundIterator(parent.extensions.iterator(), this.extensions.iterator());
            }
        else
            {
            it = this.extensions.iterator();
            }

        return it.filter(holder -> holder.isType(type));
        }

    /**
     * A holder of an `Extension` and its source.
     */
    private static class ExtensionHolder<ExtensionType extends Extension>(ExtensionType extension, Object? source)
            implements Orderable
        {
        /**
         * The priority of the extension.
         */
        @Lazy Int priority.calc()
            {
            Object? source = this.source;
            if (source.is(Test))
                {
                if (source.priority != 0)
                    {
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
        Boolean isType(Type type)
            {
            return type.isInstance(extension);
            }

        /**
         * Order holders by priority.
         */
        static <CompileType extends ExtensionHolder> Ordered compare(CompileType value1, CompileType value2)
            {
            // Highest priority comes first (i.e. reverse natural Int order)
            return value2.priority <=> value1.priority;
            }
        }
    }