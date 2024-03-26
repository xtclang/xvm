/**
 * A Matrix represents a two-dimensional container of values.
 */
interface Matrix<Element> {
    /**
     * The width of the Matrix, which is the number of columns.
     */
    @RO Int cols;

    /**
     * The height of the Matrix, which is the number of rows.
     */
    @RO Int rows;

    /**
     * Obtain the value of the specified element of the matrix.
     */
    @Op("[]") Element getElement(Int col, Int row);

    /**
     * Modify the value in the specified element of the matrix.
     */
    @Op("[]=") void setElement(Int col, Int row, Element value) {
        throw new ReadOnly();
    }

    /**
     * Obtain a column vector from the matrix.
     */
    @Op("[_,?]") Element[] getCol(Int col);

    /**
     * Modify the specified column vector in the matrix.
     */
    @Op("[_,?]=") void setCol(Int col, Element[] vector) {
        throw new ReadOnly();
    }

    /**
     * Obtain a row vector from the matrix.
     */
    @Op("[?,_]") Element[] getRow(Int row);

    /**
     * Modify the specified row vector in the matrix.
     */
    @Op("[?,_]=") void setRow(Int row, Element[] vector) {
        throw new ReadOnly();
    }

    /**
     * Returns a sub-matrix of this Matrix. The new Matrix will likely be backed by this Matrix,
     * which means that if this Matrix is mutable, changes made to this Matrix may be visible
     * through the new Matrix, and vice versa; if that behavior is not desired, [reify] the value
     * returned from this method.
     *
     * @param colRange  the range of columns of this Matrix to obtain a slice for; note that the
     *                  top end of the interval is _inclusive_, such that the interval `0..cols-1`
     *                  represents the entirety of the Matrix
     * @param rowRange  the range of rows of this Matrix to obtain a slice for; note that the top
     *                  end of the interval is _inclusive_, such that the interval `0..rows-1`
     *                  represents the entirety of the Matrix
     *
     * @return a slice of this Matrix corresponding to the specified ranges of columns and rows
     *
     * @throws OutOfBounds  if the specified ranges exceed either the lower or upper bounds of
     *                      the dimensions of this Matrix
     */
    @Op("[..]") Matrix slice(Range<Int> colRange, Range<Int> rowRange);

    /**
     * Obtain a Matrix of the same dimensions and that contains the same values as this Matrix, but
     * which has two additional attributes:
     *
     * * First, if this Matrix is a portion of a larger Matrix, then the returned Matrix will
     *   no longer be dependent on the larger Matrix for its storage;
     * * Second, if this Matrix is a portion of a larger Matrix, then changes to the returned
     *   Matrix will not be visible in the larger Matrix, and changes to the larger Matrix
     *   will not be visible in the returned Matrix.
     *
     * The contract is designed to allow for the use of copy-on-write and other lazy semantics to
     * achieve efficiency for both time and space.
     *
     * @return a reified Matrix
     */
    Matrix reify() {
        // this must be overridden by any implementation that can represent a Matrix based on the
        // contents of (via a reference to) another Matrix
        return this;
    }
}
