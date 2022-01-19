/**
 * Represents an object that can be "sliced" into smaller objects that support the same interface as
 * the original object.
 */
interface Sliceable<Index extends Orderable>
    {
    /**
     * Returns a slice of this Sliceable.
     *
     * The new Sliceable may be backed by this Sliceable, which means that if this Sliceable is
     * mutable, then changes made to this Sliceable may be visible through the new Sliceable, and
     * vice versa. If that behavior is not desired, [reify] the value returned from this method.
     *
     * @param indexes  the range of indexes of this Sliceable to obtain a slice for
     *
     * @return a slice of this Sliceable corresponding to the specified range of indexes
     *
     * @throws OutOfBounds  if the range indicates a slice that would contains illegal indexes
     */
    @Op("[..]") Sliceable slice(Range<Index> indexes);

    /**
     * Returns a slice of this Sliceable.
     *
     * The new Sliceable may be backed by this Sliceable, which means that if this Sliceable is
     * mutable, then changes made to this Sliceable may be visible through the new Sliceable, and
     * vice versa. If that behavior is not desired, [reify] the value returned from this method.
     *
     * @param indexes  the range of indexes of this Sliceable to obtain a slice for, with both the
     *                  `lowerBound` and the 'upperBound' of the range assumed to be inclusive;
     *                  note that the `lowerExclusive` and `upperExclusive` properties of the
     *                  range are ignored
     *
     * @return a slice of this Sliceable corresponding to the specified range of indexes
     *
     * @throws OutOfBounds  if the range indicates a slice that would contains illegal indexes
     */
    @Op("[[..]]") Sliceable sliceInclusive(Range<Index> indexes)
        {
        return slice(indexes.ensureInclusive());
        }

    /**
     * Returns a slice of this Sliceable.
     *
     * The new Sliceable may be backed by this Sliceable, which means that if this Sliceable is
     * mutable, then changes made to this Sliceable may be visible through the new Sliceable, and
     * vice versa. If that behavior is not desired, [reify] the value returned from this method.
     *
     * @param indexes  the range of indexes of this Sliceable to obtain a slice for, with the
     *                  `lowerBound` of the range assumed to be inclusive, and the 'upperBound'
     *                  of the range assumed to be exclusive; note that the `lowerExclusive` and
     *                  `upperExclusive` properties of the range are ignored
     *
     * @return a slice of this Sliceable corresponding to the specified range of indexes
     *
     * @throws OutOfBounds  if the range indicates a slice that would contains illegal indexes
     */
    @Op("[[..)]") Sliceable sliceExclusive(Range<Index> indexes)
        {
        return slice(indexes.ensureExclusive());
        }

    /**
     * Obtain a Sliceable that has the same contents as this Sliceable, but which has two additional
     * attributes:
     *
     * * First, if this Sliceable was sliced from another Sliceable, then the returned Sliceable
     *   will no longer be dependent on the original Sliceable for its storage;
     * * Second, if this Sliceable was sliced from another Sliceable, then changes to the returned
     *   Sliceable will not be visible in the original Sliceable, and changes to the original
     *   Sliceable will not be visible in the returned Sliceable.
     *
     * The contract is designed to allow for the use of copy-on-write and other lazy semantics to
     * achieve efficiency for both time and space.
     *
     * @return a reified Sliceable
     */
    Sliceable reify()
        {
        // this method must be overridden by any implementing class that slices by returning a view
        // of the original Sliceable, such that mutations to either would be visible from the other
        return this;
        }
    }
