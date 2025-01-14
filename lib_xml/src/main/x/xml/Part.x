/**
 * This represents a "part" of an XML [Document], or even the entire `Document`. XML documents have
 * different forms of content, including the obvious (elements and attributes), but also the
 * not-so-obvious (comments, processing instructions, etc.) Each is represented by a `Part`.
 */
interface Part
        extends Freezable, Stringable {
    /**
     * The [Document] to which this part belongs. It is possible that a `Part` is an orphan, and its
     * `Document` would be `Null`.
     */
    @RO Document? doc.get() = parent?.doc : Null;

    /**
     * The root (the parent-most) `Element` of the XML.
     */
    @RO Element? root.get() = parent?.root : Null;

    /**
     * The parent `Part` of this `Part`, or `Null`.
     */
    @RO Part? parent;

    /**
     * This value indicates the level of nesting of this `Part`; a `Part` with no parent is at
     * depth `0`, its child parts are at depth `1`, and so on.
     */
    @RO Int depth.get() = parent?.depth + 1 : 0;

    /**
     * The sequence of `Part` objects nested within this `Part` object.
     */
    @RO List<Part> parts.get() = [];

    /**
     * A [List] of sibling `Part` objects including this `Part`.
     */
    @RO List<Part> siblings;

    /**
     * This `Part`'s index in its [siblings] list.
     */
    @RO Int index;

    /**
     * Delete this `Part` from the XML `Document` (or portion thereof) within which it exists.
     *
     * @throws ReadOnly  if the `Document` (or other container) is not mutable
     */
    void delete();

    /**
     * @param pretty  pass `True` to format for a human reader, which will be larger than the
     *                default space-optimized format
     */
    @Override
    String toString(Boolean pretty = False) {
        return appendTo(new StringBuffer(estimateStringLength(pretty)), pretty).toString();
    }

    /**
     * @param pretty  pass `True` to format for a human reader, which will be larger than the
     *                default space-optimized format
     */
    @Override
    @Abstract Int estimateStringLength(Boolean pretty = False);

    /**
     * @param pretty  pass `True` to format for a human reader, which will be larger than the
     *                default space-optimized format
     */
    @Override
    @Abstract Appender<Char> appendTo(Appender<Char> buf, Boolean pretty = False);
}