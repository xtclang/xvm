/**
 * An error list is designed for collecting errors, summarizing those errors, and reporting when
 * some arbitrary limit of errors has been reached.
 */
class ErrorList(Int maxSize = MaxValue, ErrorList? parent = Null)
    {
    assert()
        {
        assert:arg maxSize >= 0;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The number of serious errors encountered.
     */
    public/private Int seriousCount;

    /**
     * The worst severity encountered.
     */
    public/private Severity severity = None;

    /**
     * The accumulated list of errors, which may be read-only.
     */
    public Error[] errors.get()
        {
        return &list.assigned ? list : [];
        }

    /**
     * The internal list of errors, which is read-write if the max size is greater than zero.
     */
    protected/private @Lazy Error[] list.calc()
        {
        return maxSize == 0 ? [] : new Error[];
        }

    /**
     * The UIDs of previously logged errors.
     */
    private @Lazy HashSet<String> uniqueIds.calc()
        {
        return new HashSet();
        }

    /**
     * True indicates that at least one error has been logged with at least the Severity of Error.
     */
    Boolean hasSeriousErrors.get()
        {
        return seriousCount > 0;
        }

    /**
     * True indicates that the process that reported the error should attempt to abort at this point
     * if it is able to do so.
     */
    Boolean abortDesired.get()
        {
        return hasSeriousErrors && seriousCount >= maxSize;
        }


    // ----- API -----------------------------------------------------------------------------------

    /**
     * Log an error.
     *
     * @param err  the error
     *
     * @return True indicates that the process that reported the error should attempt to abort at
     *         this point if it is able to
     */
    Boolean log(Error err)
        {
        // keep track of the most serious error encountered
        if (err.severity > this.severity)
            {
            this.severity = err.severity;
            }

        // count the serious errors
        if (err.severity >= Error)
            {
            ++seriousCount;
            }

        // only log the first `maxSize` errors
        if (maxSize > 0)
            {
            String uniqueId = err.uniqueId;
            if (!uniqueIds.contains(uniqueId))
                {
                uniqueIds.add(err.uniqueId);

                if (errors.size < maxSize)
                    {
                    list.add(err);
                    }
                else
                    {
                    // find a less-sever error to throw away to make room for this error
                    Loop: for (Error errLogged : errors)
                        {
                        if (errLogged.severity < err.severity)
                            {
                            list.delete(Loop.count); // yes, it's inefficient
                            list.add(err);
                            break;
                            }
                        }
                    }
                }
            }

        // once enough serious errors have occurred, attempt to abort
        return abortDesired;
        }

    /**
     * Log all of the errors in this error list into another error list.
     *
     * @param that  another ErrorList to log errors into
     *
     * @return True iff the other ErrorList indicates that an abort is desired
     */
    Boolean logTo(ErrorList that)
        {
        Boolean abort = False;
        for (Error err : this.errors)
            {
            abort |= that.log(err);
            }

        if (this.seriousCount > that.seriousCount)
            {
            // this is not correct, but it is arguably less incorrect
            that.seriousCount = this.seriousCount;
            }

        if (this.severity > that.severity)
            {
            that.severity = this.severity;
            }

        return abort;
        }

    /**
     * Branch this ErrorList by creating a new one that will collect subsequent errors in the same
     * manner as this one until a [merge] occurs or it is discarded.
     *
     * @return the branched ErrorList
     */
    ErrorList branch(Int branchMax = MaxValue)
        {
        branchMax = branchMax.minOf(maxSize - seriousCount).maxOf(maxSize == 0 ? 0 : 1);
        return new ErrorList(branchMax, this);
        }

    /**
     * Merge all errors collected by this ErrorList into the ErrorList that it is a branch of.
     *
     * @return the ErrorList that this branched from
     */
    ErrorList merge()
        {
        ErrorList result = parent ?: assert;
        if (errors.size > 0)
            {
            logTo(result);
            list   = [];
            parent = Null;
            }

        return result;
        }

    /**
     * Scan the ErrorList for the specified error code.
     *
     * @param code  the error code
     *
     * @return True iff an error has been logged (and retained) with the specified code
     */
    Boolean hasError(String code)
        {
        return errors.iterator().untilAny(err -> err.code == code);
        }
    }