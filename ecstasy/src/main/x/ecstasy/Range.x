/**
 * A range is composed of a "first" and a "last" element. The Range determines which is the lower
 * bound and which is the upper bound, and if the lower bound is the "last" element, then the range
 * is [descending]. Each bound can be _inclusive_, which means that the value at the bound is included
 * in the range, or _exclusive_, which means that the value at the bound is excluded from the range.
 */
const Range<Element extends Orderable>
        incorporates conditional Interval<Element extends Sequential>
    {
    /**
     * Construct a Range.
     *
     * @param first           the first value in the range
     * @param last            the last value in the range
     * @param firstExclusive  True iff the first value is _exclusive_ (not included in the range)
     * @param lastExclusive   True iff the last value is _exclusive_ (not included in the range)
     */
    construct(Element first,
              Element last,
              Boolean firstExclusive = False,
              Boolean lastExclusive = False)
        {
        if (first > last)
            {
            lowerBound     = last;
            lowerExclusive = lastExclusive;
            upperBound     = first;
            upperExclusive = firstExclusive;
            descending     = True;
            }
        else
            {
            lowerBound     = first;
            lowerExclusive = firstExclusive;
            upperBound     = last;
            upperExclusive = lastExclusive;
            descending     = False;
            }
        }

    /**
     * Internal constructor.
     *
     * @param lowerBound      the lower bound of the range
     * @param lowerExclusive  True iff the lowerBound is exclusive
     * @param upperBound      the upper bound of the range
     * @param upperExclusive  True iff the upperBound is exclusive
     * @param descending      True iff the range proceeds from its upper bound to its lower bound
     */
    private construct(Element lowerBound,
                      Boolean lowerExclusive,
                      Element upperBound,
                      Boolean upperExclusive,
                      Boolean descending,
                     )
        {
        this.lowerBound     = lowerBound;
        this.lowerExclusive = lowerExclusive;
        this.upperBound     = upperBound;
        this.upperExclusive = upperExclusive;
        this.descending     = descending;
        }

    /**
     * The starting bound of the range.
     */
    Element first.get()
        {
        return descending ? upperBound : lowerBound;
        }

    /**
     * True iff the starting bound of the range is exclusive.
     */
    Boolean firstExclusive.get()
        {
        return descending ? upperExclusive : lowerExclusive;
        }

    /**
     * The ending bound of the range.
     */
    Element last.get()
        {
        return descending ? lowerBound : upperBound;
        }

    /**
     * True iff the ending bound of the range is exclusive.
     */
    Boolean lastExclusive.get()
        {
        return descending ? lowerExclusive : upperExclusive;
        }

    /**
     * The lower bound of the range.
     */
    Element lowerBound;

    /**
     * If the [lowerBound] is exclusive.
     */
    Boolean lowerExclusive;

    /**
     * The upper bound of the range.
     */
    Element upperBound;

    /**
     * If the [upperBound] is exclusive.
     */
    Boolean upperExclusive;

    /**
     * The value is True if the range was specified from its upper bound to its lower bound.
     */
    Boolean descending;

    /**
     * If the range is descending, reverse it.
     *
     * @return the range in ascending order
     */
    Range! asAscending()
        {
        return descending
                ? this.reversed()
                : this;
        }

    /**
     * If the range is ascending, reverse it.
     *
     * @return the range in descending order
     */
    Range! asDescending()
        {
        return descending
                ? this
                : this.reversed();
        }

    /**
     * Create a new range in the reverse order of this range.
     */
    Range! reversed()
        {
        return new Range(lowerBound,
                         lowerExclusive,
                         upperBound,
                         upperExclusive,
                         !descending);
        }

    /**
     * @return the Range `[first..last]` (ignoring the values of both `this.lowerExclusive` and
     *         `this.upperExclusive`)
     */
    Range! ensureInclusive()
        {
        return !firstExclusive & !lastExclusive ? this : [first..last];
        }

    /**
     * @return the Range `[first..last)` (ignoring the values of both `this.lowerExclusive` and
     *         `this.upperExclusive`)
     */
    Range! ensureExclusive()
        {
        return !firstExclusive & lastExclusive ? this : [first..last);
        }

    /**
     * Determine if the specified value exists within this range.
     */
    Boolean contains(Element value)
        {
        return switch (value <=> lowerBound, value <=> upperBound)
            {
            case (Lesser , _      ): False;                              // below lower bound
            case (_      , Greater): False;                              // above upper bound
            case (Greater, Lesser ): True;                               // between lower and upper
            case (Equal  , Lesser ): !lowerExclusive;                    // at lower bound
            case (Equal  , Equal  ): !lowerExclusive && !upperExclusive; // at both bounds
            case (Greater, Equal  ): !upperExclusive;                    // at upper bound
            };
        }

    /**
     * This range contains that range iff every value within that range is also in this range.
     */
    Boolean contains(Range that)  // REVIEW this name should be changed to avoid potential collisions
        {
        return switch (that.lowerBound <=> this.lowerBound, that.upperBound <=> this.upperBound)
            {
            case (Lesser , _      ): False;                                     // below lower bound
            case (_      , Greater): False;                                     // above upper bound
            case (Greater, Lesser ): True;                                      // between bounds
            case (Equal  , Lesser ): !this.lowerExclusive | that.lowerExclusive;// at lower bound
            case (Equal  , Equal  ): !this.lowerExclusive | that.lowerExclusive
                                  && !this.upperExclusive | that.upperExclusive;// at both bounds
            case (Greater, Equal  ): !this.upperExclusive | that.upperExclusive;// at upper bound
            };
        }

    /**
     * That range contains this range iff every value within this range is also in that range.
     */
    Boolean isContainedBy(Range that)
        {
        return that.contains(this);
        }

    /**
     * @return True if `this` and `that` intersect
     */
    Boolean intersects(Range that)
        {
        return switch (that.lowerBound <=> this.upperBound, that.upperBound <=> this.lowerBound)
            {
            case (Greater, _      ): False;                                      // above upper bound
            case (_      , Lesser ): False;                                      // below lower bound
            case (Lesser , Greater): True;                                       // between bounds
            case (Lesser , Equal  ): !this.lowerExclusive & !that.upperExclusive;// at lower bound
            case (Equal  , Greater): !this.upperExclusive & !that.lowerExclusive;// at upper bound
            case (Equal  , Equal  ): !this.lowerExclusive & !this.upperExclusive // zero length!
                                   & !that.lowerExclusive & !that.upperExclusive;
            };
        }

    /**
     * The intersection of this range and that range is the range that contains all of the values
     * that exist within both this range and that range.
     *
     * @return True if `this` and `that` intersect
     * @return (conditional) the `Range` that represents the intersecting values from the two ranges
     */
    conditional Range intersection(Range that)
        {
        if (!this.intersects(that))
            {
            return False;
            }

        Element lower;
        Boolean excludeLower;
        switch (this.lowerBound <=> that.lowerBound)
            {
            case Lesser:
                lower        = that.lowerBound;
                excludeLower = False;
                break;

            case Equal:
                lower        = this.lowerBound;
                excludeLower = this.lowerExclusive & that.lowerExclusive;
                break;

            case Greater:
                lower        = this.lowerBound;
                excludeLower = False;
                break;
            }

        Element upper;
        Boolean excludeUpper;
        switch (this.upperBound <=> that.upperBound)
            {
            case Lesser:
                upper        = this.upperBound;
                excludeUpper = False;
                break;

            case Equal:
                upper        = this.upperBound;
                excludeUpper = this.upperExclusive & that.upperExclusive;
                break;

            case Greater:
                upper        = that.upperBound;
                excludeUpper = False;
                break;
            }

        return True, (this.descending & that.descending
                ? new Range(upper, lower, firstExclusive=excludeUpper, lastExclusive=lowerExclusive)
                : new Range(lower, upper, firstExclusive=excludeLower, lastExclusive=upperExclusive));
        }

    /**
     * @return True iff the union of `this` and `that` is a contiguous range
     */
    Boolean adjoins(Range that)
        {
        return switch (that.lowerBound <=> this.upperBound, that.upperBound <=> this.lowerBound)
            {
            case (Greater, _      ): False;                                      // above upper bound
            case (_      , Lesser ): False;                                      // below lower bound
            case (Lesser , Greater): True;                                       // between bounds
            case (Lesser , Equal  ): !this.lowerExclusive | !that.upperExclusive;// at lower bound
            case (Equal  , Greater): !this.upperExclusive | !that.lowerExclusive;// at upper bound
            case (Equal  , Equal  ): True;                                       // zero length!
            };
        }

    /**
     * Two ranges that are contiguous or overlap can be joined together to form a larger range.
     */
    conditional Range union(Range that)
        {
        if (!this.adjoins(that))
            {
            return False;
            }

        Element lower;
        Boolean excludeLower;
        switch (this.lowerBound <=> that.lowerBound)
            {
            case Lesser:
                lower        = this.lowerBound;
                excludeLower = this.lowerExclusive;
                break;

            case Equal:
                lower        = this.lowerBound;
                excludeLower = this.lowerExclusive & that.lowerExclusive;
                break;

            case Greater:
                lower        = that.lowerBound;
                excludeLower = that.lowerExclusive;
                break;
            }

        Element upper;
        Boolean excludeUpper;
        switch (this.upperBound <=> that.upperBound)
            {
            case Lesser:
                upper        = that.upperBound;
                excludeUpper = that.upperExclusive;
                break;

            case Equal:
                upper        = this.upperBound;
                excludeUpper = this.upperExclusive & that.upperExclusive;
                break;

            case Greater:
                upper        = this.upperBound;
                excludeUpper = this.upperExclusive;
                break;
            }

        return True, (this.descending & that.descending
                ? new Range(upper, lower, firstExclusive=excludeUpper, lastExclusive=lowerExclusive)
                : new Range(lower, upper, firstExclusive=excludeLower, lastExclusive=upperExclusive));
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        Int estimate = 4;
        if (Element.is(Type<Stringable>))
            {
            estimate += lowerBound.estimateStringLength()
                      + upperBound.estimateStringLength();
            }
        else
            {
            estimate += lowerBound.is(Stringable) ? lowerBound.estimateStringLength() : 4;
            estimate += upperBound.is(Stringable) ? upperBound.estimateStringLength() : 4;
            }
        return estimate;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        buf.add(lowerExclusive ? '(' : '[');
        if (Element.is(Type<Stringable>))
            {
            lowerBound.appendTo(buf);
            "..".appendTo(buf);
            upperBound.appendTo(buf);
            }
        else
            {
            (lowerBound.is(Stringable) ? lowerBound : lowerBound.toString()).appendTo(buf);
            "..".appendTo(buf);
            (upperBound.is(Stringable) ? upperBound : upperBound.toString()).appendTo(buf);
            }
        return buf.add(upperExclusive ? ')' : ']');
        }
    }
