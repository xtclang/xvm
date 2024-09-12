/**
 * `PartitionedCollection` is the deferred result of a `partition()` operation on a `Collection`.
 * In many ways, this is identical to the FilteredCollection, except that there is an implicit
 * inverse of the filter for the other partition, and when we realize one partition, we forcibly
 * realize them both, which avoids having to re-evaluate the same data twice.
 */
class PartitionedCollection<Element>
        extends DeferredCollection<Element, Element> {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct the "matches" PartitionedCollection for the specified matching filter.
     *
     * @param original  the unpartitioned collection
     * @param match     the inclusion filter for the "matches" result of the partition
     */
    construct(Collection<Element> original, function Boolean(Element) match) {
        construct DeferredCollection(original);
        this.primary = True;
        this.match   = match;
    } finally {
        this.buddy   = new PartitionedCollection<Element>(this);
    }

    /**
     * Construct the "misses" PartitionedCollection corresponding to the specified "matches"
     * PartitionedCollection. This collection represents the missing elements.
     *
     * @param buddy  the primary PartitionedCollection instance that this is the "inverse" buddy of
     */
    protected construct(PartitionedCollection<Element> buddy) {
        construct DeferredCollection(buddy.original ?: assert);
        function Boolean(Element) match = buddy.match ?: assert;
        function Boolean(Element) miss  = e -> !match(e);
        this.primary = False;
        this.match   = miss;
        this.buddy   = buddy;
    }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * The partitioning function, or Null after it has been partitioned (which allows memory to be
     * collected).
     */
    protected function Boolean(Element)? match;

    /**
     * The buddy partition.
     */
    protected PartitionedCollection<Element>? buddy;

    /**
     * A property used to expose the existence of the buddy partition after construction.
     */
    PartitionedCollection<Element> inverse.get() = buddy ?: assert;

    /**
     * True for the first of the two PartitionedCollection buddies instantiated (the "matches);
     * false for the second (the "misses").
     */
    protected/private @Final Boolean primary;

    /**
     * This property may be set to the reified data by the buddy.
     */
    protected Collection<Element>? data;

    @Override
    protected Collection<Element> createReified() {
        Collection<Element> result;
        if (result ?= data) {
            // our buddy already did the reify work and pushed our reified data to us; just use that
        } else if (primary || buddy?.alreadyReified : True) {
            // if "this" is the primary partition aka "matches", then the actual partitioning work
            // is done here; otherwise, either we no longer have a buddy, or the buddy already did
            // its reification work without us doing our reification work; this can happen (to
            // either buddy) if we have already been called to evaluateInto(), and as a side-effect
            // of that call, we reified the buddy, but being a smidge too clever, we didn't reify
            // ourselves (optimizing for the assumption that no one would ask the same question
            // again)
            result = instantiateEmptyReified();
            evaluateInto(result);
        } else {
            // this is not the primary partition, i.e. this is the "misses"; tell the primary to do
            // the actual partitioning work, and then use the reified result that it gives us
            buddy?.reify();
            assert result ?= data;
        }

        postReifyCleanup();
        return result;
    }

    @Override
    protected void postReifyCleanup() {
        data = Null;
        // if we're the non-primary, then we're always allowed to forget the primary after
        // reification because it never relies on us; if we're the primary, our buddy relies on us
        // until it has reified
        if (!primary || buddy?.alreadyReified) {
            match = Null;
            super();
        }
    }

    @Override
    protected Iterator<Element> unreifiedIterator() {
        assert Collection<Element>       original ?= original;
        assert function Boolean(Element) match    ?= match;
        return original.iterator().filter(match);
    }

    @Override
    protected void evaluateInto(Appender<Element> accumulator) {
        // this is a bit different from other deferred collections, because we assume that going
        // through all of the data twice (once for matches and once for misses) is much more
        // expensive than going through it once for this partition **and** creating our buddy's
        // reified result during the same pass; obviously, this assumption is sometimes wrong
        if (Collection<Element> original ?= original,
                function Boolean(Element) match ?= match) {
            // if we don't have a buddy, then don't bother collecting the reified data for the buddy
            Appender<Element> buddyData = buddy == Null
                ? new Appender<Element>() {
                    @Override Appender<Element> add(Element v) = this;
                  }
                : instantiateEmptyReified();

            if (DeferredCollection<Element> nextDeferred := original.is(DeferredCollection<Element>)){
                // provide an accumulator to the deferred collection so that it gives us all of its
                // data, and we'll collect the data (for the other partition) that our buddy needs,
                // while all the data that is in our partition will get piped straight through to
                // the provided accumulator
                class ApplyPartition(Appender<Element> matches, Appender<Element> misses, function Boolean(Element) match)
                        implements Appender<Element> {
                    @Override Appender<Element> add(Element v) {
                        (match(v) ? matches : misses).add(v);
                        return this;
                    }
                }
                nextDeferred.evaluateInto(new ApplyPartition(accumulator, buddyData, match));
            } else {
                // we're the last in the chain of deferred collections, so do the work here
                for (Element e : original) {
                    (match(e) ? accumulator : buddyData).add(e);
                }
            }

            // push the buddy's reified data to the buddy
            if (PartitionedCollection<Element> buddy ?= buddy) {
                buddy.data = buddyData.as(Collection<Element>);
                buddy.reify();
            }
        } else {
            super(accumulator);
        }
    }


    // ----- Collection interface ------------------------------------------------------------------

    @Override
    conditional Orderer? ordered() = (original ?: reified).ordered();

    @Override
    Boolean contains(Element value) {
        if (Collection<Element> original ?= original, function Boolean(Element) match ?= match) {
            return match(value) && original.contains(value);
        } else {
            return reified.contains(value);
        }
    }

    @Override
    Boolean containsAll(Collection<Element> values) {
        if (Collection<Element> original ?= original, function Boolean(Element) match ?= match) {
            return values.all(match) && original.containsAll(values);
        } else {
            return reified.containsAll(values);
        }
    }
}