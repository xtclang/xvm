/**
 * An `ErrorList` is designed for collecting [Error]s, summarizing those errors, and reporting when
 * some arbitrary limit of errors has been reached.
 */
class ErrorList(Int maxSize = MaxValue, ErrorList? parent = Null)
        delegates Iterable<Error>(errors)
        implements Stringable {

    assert() {
        assert:arg maxSize >= 0;
    }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The number of serious [Error]s encountered.
     */
    public/private Int seriousCount;

    /**
     * The worst [Severity] encountered.
     */
    public/private Severity severity = None;

    /**
     * The accumulated list of [Error]s, which may be read-only.
     */
    public Error[] errors.get() = &list.assigned ? list : [];

    /**
     * The internal list of [Error]s, which is read-write if the max size is greater than zero.
     */
    protected/private @Lazy Error[] list.calc() = maxSize == 0 ? [] : new Error[];

    /**
     * The unique ids of previously logged [Error]s.
     */
    private @Lazy HashSet<String> uniqueIds.calc() = new HashSet();

    /**
     * `True` indicates that at least one error has been logged with at least the Severity of Error.
     */
    Boolean hasSeriousErrors.get() = seriousCount > 0;

    /**
     * `True` indicates that the process that reported the [Error] should attempt to abort at this
     * point if it is able to do so.
     */
    Boolean abortDesired.get() = hasSeriousErrors && seriousCount >= maxSize;

    // ----- API -----------------------------------------------------------------------------------

    /**
     * Log an [Error].
     *
     * @param err  the [Error]
     *
     * @return `True` indicates that the process that reported the [Error] should attempt to abort
     *         at this point if it is able to
     */
    Boolean log(Error err) {
        // keep track of the most serious error encountered
        if (err.severity > this.severity) {
            this.severity = err.severity;
        }

        // count the serious errors
        if (err.severity >= Error) {
            ++seriousCount;
        }

        // only log the first `maxSize` errors
        if (maxSize > 0) {
            String uniqueId = err.uniqueId;
            if (!uniqueIds.contains(uniqueId)) {
                uniqueIds.add(err.uniqueId);

                if (errors.size < maxSize) {
                    list.add(err);
                } else {
                    // find a less-severe error to throw away to make room for this error
                    Loop: for (Error errLogged : errors) {
                        if (errLogged.severity < err.severity) {
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
     * Log all of the [Error]s in this `ErrorList` into another `ErrorList`.
     *
     * @param that  another `ErrorList` to log [Error]s into
     *
     * @return `True` iff the other `ErrorList` indicates that an abort is desired
     */
    Boolean logTo(ErrorList that) {
        Boolean abort = False;
        for (Error err : this.errors) {
            abort |= that.log(err);
        }

        if (this.seriousCount > that.seriousCount) {
            // this is not correct, but it is arguably less incorrect
            that.seriousCount = this.seriousCount;
        }

        if (this.severity > that.severity) {
            that.severity = this.severity;
        }

        return abort;
    }

    /**
     * Branch this `ErrorList` by creating a new one that will collect subsequent [Error]s in the
     * same manner as this one until a [merge] occurs or it is discarded.
     *
     * @return the branched `ErrorList`
     */
    ErrorList branch(Int branchMax = MaxValue) {
        branchMax = branchMax.notLessThan(maxSize == 0 ? 0 : 1).notGreaterThan(maxSize - seriousCount);
        return new ErrorList(branchMax, this);
    }

    /**
     * Merge all [Error]s collected by this `ErrorList` into the `ErrorList` that it is a branch of.
     *
     * @return the `ErrorList` that this branched from
     */
    ErrorList merge() {
        ErrorList result = parent ?: assert;
        if (errors.size > 0) {
            logTo(result);
            list   = [];
            parent = Null;
        }

        return result;
    }

    /**
     * Reset the `ErrorList` to its initial empty state.
     */
    ErrorList reset() {
        seriousCount = 0;
        severity     = None;
        if (&list.assigned) {
            list.clear();
        }
        if (&uniqueIds.assigned) {
            uniqueIds.clear();
        }
        return this;
    }

    /**
     * Scan the `ErrorList` for the specified [Error] code.
     *
     * @param code  the [Error] code
     *
     * @return `True` iff an [Error] has been logged (and retained) with the specified code
     */
    Boolean hasError(String code) = errors.any(err -> err.code == code);

    @Override
    Int estimateStringLength() {
        // unfortunately, there are too many allocations required to build a suitable estimate of
        // the buffer size, which would cost far more than simply resizing the buffer as needed
        return errors.empty ? 9 : 100 * errors.size;
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        Error[] errors = this.errors;
        if (errors.empty) {
            return buf.addAll("No errors");
        }

        errors.size.appendTo(buf);
        " errors (".appendTo(buf);
        seriousCount.appendTo(buf);
        " serious, severity=".appendTo(buf);
        severity.appendTo(buf);
        buf.addAll("):");

        Errors: for (Error error : errors) {
            "\n  [".appendTo(buf);
            Errors.count.appendTo(buf);
            "] ".appendTo(buf);
            error.appendTo(buf);
        }
        return buf;
    }
}