/**
 * A representation of a received HTTP response.
 */
interface ResponseIn
        extends Response
    {
    /**
     * Convert the content of the body to the specified type.
     *
     * @param type  the desired result type
     *
     * @return True iff the content of the body was successfully turned into a result of the
     *              desired type
     * @return (conditional) the result
     */
    <Result> conditional Result to(Type<Result> type)
        {
        if (status == OK, Body body ?= this.body)
            {
            return body.to(type);
            }

        return False;
        }
    }