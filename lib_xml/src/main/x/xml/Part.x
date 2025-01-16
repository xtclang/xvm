/**
 * This represents a "part" of an XML [Document], or even the entire `Document`. XML documents have
 * different forms of content, including the obvious (elements and attributes), but also the
 * not-so-obvious (comments, processing instructions, etc.) Each is represented by a `Part`.
 *
 * TODO DTD and EntityRef support
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
    @RO Part!? parent;

    /**
     * This value indicates the level of nesting of this `Part`; a `Part` with no parent is at
     * depth `0`, its child parts are at depth `1`, and so on.
     */
    @RO Int depth.get() = parent?.depth + 1 : 0;

    /**
     * The sequence of `Part` objects nested within this `Part` object.
     *
     * If the containing [Document] (or substructure thereof) is mutable, then `Part` objects can
     * be added and removed from this `List`, subject to any constraints imposed by the XML
     * specification. `Part` objects added to this `List` may be replaced (as part of the add
     * operation, or at any subsequent point) with different objects representing the same
     * information, so the caller must not assume that the reference added to the `List` is the same
     * reference that is held by the `List`.
     */
    @RO List<Part!> parts.get() = [];

    /**
     * A [List] of sibling `Part` objects including this `Part`.
     *
     * If the containing [Document] (or substructure thereof) is mutable, then `Part` objects can
     * be added and removed from this `List`, subject to any constraints imposed by the XML
     * specification. `Part` objects added to this `List` may be replaced (as part of the add
     * operation, or at any subsequent point) with different objects representing the same
     * information, so the caller must not assume that the reference added to the `List` is the same
     * reference that is held by the `List`.
     */
    @RO List<Part!> siblings.get() = parent?.parts : [this];

    /**
     * This `Part`'s index in its [siblings] list.
     */
    @RO Int index.get() = parent?.parts.indexOf(this)? : 0;

    /**
     * Delete this `Part` from the XML `Document` (or portion thereof) within which it exists.
     *
     * @throws ReadOnly  if the `Document` (or other container) is not mutable
     */
    void delete() = parent?.parts.delete(index);

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
    @Abstract Int estimateStringLength(Boolean pretty = False, Int indent=0);

    /**
     * @param pretty  pass `True` to format for a human reader, which will be larger than the
     *                default space-optimized format
     */
    @Override
    @Abstract Writer appendTo(Writer buf, Boolean pretty = False, String indent="");
}