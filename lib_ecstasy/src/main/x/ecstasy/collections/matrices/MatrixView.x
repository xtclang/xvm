/**
 * A Matrix represents a fixed size, two-dimensional container of values. Most 2D coordinate systems
 * use `(x,y)` ordering, but by convention, matrices use `(row,col)`.
 *
 * TODO
 * * add "ensureMutable()" or something like that (constructor?)
 * * mixins for e.g. numeric types
 * * rotate, flip, etc.
 */
@Abstract class Matrix<Element>
        implements Stringable
        incorporates conditional HashableMatrix<Element extends Hashable>
        incorporates conditional FreezableMatrix<Element extends Shareable> {
    /**
     * (Internal) Construct a matrix of the specified size.
     *
     * @param rows  the number of rows
     * @param cols  the number of columns
     */
    protected construct(Int rows, Int cols) {
        assert:bounds rows >= 0 && cols >= 0;
        this.rows  = rows;
        this.cols  = cols;
    }

    /**
     * An mix-in implementation of [CopyableCollection] that requires the underlying [Collection] to
     * be [Replicable].
     */
    static mixin ReplicableCopier<Element>
            into Replicable + Collection<Element>
            implements CopyableCollection<Element> {
        /**
         * @param transform  an optional element transformer
         */
        @Override
        ReplicableCopier duplicate(function Element(Element)? transform = Null) {
            if (this.is(immutable ReplicableCopier) && transform == Null) {
                return this;
            }

            if (transform == Null) {
                return this.new(this);
            }

            return this.map(transform, Collect.to(ReplicableCopier<Element>));
        }

        @Override
        ReplicableCopier clear() {
            return inPlace ? super() : this.new();
        }
    }

    /**
     * The height of the Matrix, which is the number of rows. The height of the matrix is fixed.
     */
    public/private Int rows;

    /**
     * The width of the Matrix, which is the number of columns. The width of the matrix is fixed.
     */
    public/private Int cols;

    /**
     * True iff the matrix is square.
     */
    Boolean square.get() {
        return rows == cols;
    }

    /**
     * True iff the matrix is symmetric.
     */
    Boolean symmetric.get() {
        if (!square) {
            return False;
        }

        for (Int row : 0..<rows) {
            for (Int col : row+1..<cols) {
                if (this[row,col] != this[col,row]) {
                    return False;
                }
            }
        }

        return True;
    }

    /**
     * Obtain the value of the specified element of the matrix.
     *
     * @param row  a value in the range `0..<rows`
     * @param col  a value in the range `0..<cols`
     *
     * @return the Element at the specified coordinates
     */
    @Op("[]") Element getElement(Int row, Int col);

    /**
     * Modify the value in the specified element of the matrix.
     *
     * @param row    a value in the range `0..<rows`
     * @param col    a value in the range `0..<cols`
     * @param value  the Element to store in the matrix at the specified coordinates
     */
    @Op("[]=") void setElement(Int col, Int row, Element value);

    /**
     * Obtain a row vector from the matrix. The result _may_ be an unrealized slice, so use
     * [reify()](Array.reify) to ensure that the result will not have its contents modified by
     * subsequent modifications to this matrix.
     *
     * @param row  a value in the range `0..<rows`
     *
     * @return an `List<Element>` representing the specified row
     */
    @Op("[?,_]") Vector<Element> getRow(Int row) {
        assert:bounds 0 <= row < rows;
        return new MatrixRow(this, row);
    }

    /**
     * Modify the specified row vector in the matrix.
     *
     * @param row     a value in the range `0..<rows`
     * @param vector  an `List<Element>` of size `cols` representing the row to store
     */
    @Op("[?,_]=") void setRow(Int row, List<Element> vector) {
        assert:bounds 0 <= row < rows && vector.size == cols;
        for (Int col : 0..<cols) {
            this[row, col] = vector[col];
        }
    }

    /**
     * Obtain a column vector from the matrix. The result _may_ be an unrealized slice, so use
     * [reify()](Array.reify) to ensure that the result will not have its contents modified by
     * subsequent modifications to this matrix.
     *
     * @param col  a value in the range `0..<cols`
     *
     * @return an `List<Element>` representing the specified column
     */
    @Op("[_,?]") Vector<Element> getCol(Int col) {
        assert:bounds 0 <= col < cols;
        return new MatrixCol(this, col);
    }

    /**
     * Modify the specified column vector in the matrix.
     *
     * @param col     a value in the range `0..<cols`
     * @param vector  an `List<Element>` of size `rows` representing the column to store
     */
    @Op("[_,?]=") void setCol(Int col, List<Element> vector) {
        assert:bounds 0 <= col < cols && vector.size == rows;
        for (Int row : 0..<rows) {
            this[row, col] = vector[row];
        }
    }

    /**
     * Returns a sub-matrix of this Matrix. The new Matrix behaves as a _view_  of this Matrix,
     * such that any changes made to this Matrix may be visible through the new Matrix as well,
     * and vice versa; if the resulting Matrix should *not* exhibit that bidirectional shared
     * mutability [reify] the value returned from this method.
     *
     * @param rowRange  the range of rows of this Matrix to obtain a slice for
     * @param colRange  the range of columns of this Matrix to obtain a slice for
     *
     * @return a slice of this Matrix corresponding to the specified ranges of columns and rows
     *
     * @throws OutOfBounds  if the specified ranges exceed either the lower or upper bounds of
     *                      the dimensions of this Matrix
     */
    @Op("[..]") Matrix slice(Range<Int> rowRange, Range<Int> colRange) {
        assert:bounds 0 <= colRange.effectiveLowerBound < colRange.effectiveUpperBound < cols;
        assert:bounds 0 <= rowRange.effectiveLowerBound < rowRange.effectiveUpperBound < rows;

        Int r0 = rowRange.effectiveFirst;
        Int c0 = colRange.effectiveFirst;

        return new MatrixView<Element>(rowRange.size, colRange.size, this,
                switch (rowRange.descending, colRange.descending) {
                case (False, False): (r, c) -> (r0 + r, c0 + c);
                case (False, True ): (r, c) -> (r0 + r, c0 - c);
                case (True,  False): (r, c) -> (r0 - r, c0 + c);
                case (True,  True ): (r, c) -> (r0 - r, c0 - c);
                });
    }

    /**
     * Returns a transpose of this Matrix. The new Matrix behaves as a _view_  of this Matrix,
     * such that any changes made to this Matrix may be visible through the new Matrix as well,
     * and vice versa; if the resulting Matrix should *not* exhibit that bidirectional shared
     * mutability [reify] the value returned from this method.
     *
     * @return the transpose of this Matrix
     */
    Matrix transpose() {
        return new MatrixView<Element>(cols, rows, this, (r, c) -> (c, r);
    }

    /**
     * Returns a rightwards-rotation of this Matrix. The new Matrix behaves as a _view_  of this
     * Matrix, such that any changes made to this Matrix may be visible through the new Matrix as
     * well, and vice versa; if the resulting Matrix should *not* exhibit that bidirectional shared
     * mutability [reify] the value returned from this method.
     *
     * @param count  the number of 90 degree rotations to perform on this Matrix
     *
     * @return a right-rotation of this Matrix `count` times
     */
    Matrix rotateRight(Int count=1) {
        return switch (count % 4) {
            case 0: new MatrixView<Element>(rows, cols, this, (r, c) -> (r, c);
            case 1: new MatrixView<Element>(cols, rows, this, (r, c) -> (c, rows - r - 1);
            case 2: new MatrixView<Element>(rows, cols, this, (r, c) -> (rows - r - 1, cols - c - 1);
            case 3: new MatrixView<Element>(cols, rows, this, (r, c) -> (cols - c - 1, r);
        }
    }

    /**
     * Returns a leftwards-rotation of this Matrix. The new Matrix behaves as a _view_  of this
     * Matrix, such that any changes made to this Matrix may be visible through the new Matrix as
     * well, and vice versa; if the resulting Matrix should *not* exhibit that bidirectional shared
     * mutability [reify] the value returned from this method.
     *
     * @param count  the number of 90 degree rotations to perform on this Matrix
     *
     * @return a left-rotation of this Matrix `count` times
     */
    Matrix rotateLeft(Int count=1) = rotateRight(-count);

    /**
     * Obtain a Matrix of the same dimensions containing the same values as this Matrix, but
     * which has two additional attributes:
     *
     * * First, if this Matrix is a view of another Matrix, then the Matrix returned from `reify()`
     *   will no longer be dependent on the underlying Matrix for its storage;
     * * Second, if this Matrix is a view of another Matrix, then any subsequent changes to either
     *   Matrix will not be visible in the other Matrix.
     *
     * The contract is designed to allow for the use of copy-on-write and other lazy semantics to
     * achieve efficiency for both time and space.
     *
     * @return a reified Matrix
     */
    Matrix! reify() {
        return this.is(immutable) ? this : duplicate();
    }

    /**
     * Duplicate this Matrix, optionally transforming each element.
     *
     * @param transform  an optional element transformer
     *
     * @return a new Matrix, duplicated from this Matrix, and optionally transformed
     */
    Matrix! duplicate(function Element(Element)? transform = Null) {
        return transform == Null
                ? new Element[rows, cols]((r, c) -> this[r, c])
                : new Element[rows, cols]((r, c) -> transform(this[r, c]));
    }


    // ----- Hashable mixin ------------------------------------------------------------------------

    /**
     * A mixin that implements the Hashable interface on a Matrix of Hashable elements.
     */
    protected static mixin HashableMatrix<Element extends Hashable>
            into Matrix<Element>
            implements Hashable {

        @Override
        static <CompileType extends HashableMatrix> Int hashCode(CompileType matrix) {
            return this.is(immutable) ? cachedHash : calcHash();
        }

        private @Lazy(calcHash) cachedHash;

        protected Int calcHash() {
            for (Int row : 0..rows) {
                for (Int col : 0..cols) {
                    hash ^= this[row, col].hashCode.rotateRight(row) * (col+1);
                }
            }
        }
    }


    // ----- Freezable interface -------------------------------------------------------------------

    /**
     * A mixin that implements the Freezable interface on a Matrix of Shareable elements.
     */
    protected static mixin FreezableMatrix<Element extends Shareable>
            into Matrix<Element>
            implements Freezable {

        @Override
        immutable FreezableMatrix freeze(Boolean inPlace = False) {
            if (this.is(immutable)) {
                return this;
            }

            if (inPlace) {
                for (Int row : 0..rows) {
                    for (Int col : 0..cols) {
                        this[row, col] = frozen(this[row, col]);
                    }
                }
                return makeImmutable();
            }

            return duplicate(frozen).makeImmutable();
        }
    }
}



//-----

class Matrix<Element>
        implements Duplicable
        incorporates conditional HashableMatrix<Element extends Hashable>
        incorporates conditional FreezableMatrix<Element extends Shareable> {

    /**
     * Construct a fixed size matrix with the specified size and initial value. An initial value is
     * always required.
     *
     * @param rows    the number of rows
     * @param cols    the number of columns
     * @param supply  the value or the supply function for initializing the elements of the matrix
     */
    construct(Int rows, Int cols, Element | function Element(Int, Int) supply) {
        assert:bounds rows > 0 && cols > 0;
        this.rows  = rows;
        this.cols  = cols;
        this.cells = supply.is(Element)
                ? new Element[rows*cols](supply)
                : new Element[rows*cols](TODO("translate i->row,col")); // TODO
    }

    /**
     * `Duplicable` interface constructor.
     *
     * @param that       the Matrix to copy from
     * @param transform  optional element transformer
     */
    @Override
    construct(Matrix that, function Element(Element)? transform = Null, Boolean freeze = False) {
        this.rows  = that.rows;
        this.cols  = that.cols;
        this.cells = transform == Null && freeze == that.cells.is(immutable))
                ? that.cells.duplicate()
                : new Element[that.cells.size](i -> transform(that.cells[i]));
    } finally {
        if (freeze) {
            cells.freeze(inPlace=True);
            makeImmutable();
        }
    }

    /**
     * (Internal) Construct a fixed size matrix with the specified size and element storage.
     *
     * @param rows   the number of rows
     * @param cols   the number of columns
     * @param cells  the internal storage array of element values in the matrix
     */
    protected construct(Int rows, Int cols, Element[] cells) {
        this.rows  = rows;
        this.cols  = cols;
        this.cells = cells;
    }

    /**
     * The contents of the Matrix. The form of the internal storage of elements is purposefully
     * hidden behind the API, so that the implementation of `Matrix` can be optimized by the
     * runtime in order to take advantage of hardware acceleration and optimized math libraries.
     */
    private Element[] cells;

    private Int index(Int row, Int col=0) {
        return row * cols + col;
    }

    @Override
    @Op("[]") Element getElement(Int row, Int col) {
        assert:bounds 0 <= row < rows && 0 <= col < cols;
        return cells[index(row, col)];
    }

    @Override
    @Op("[]=") void setElement(Int col, Int row, Element value) {
        assert:bounds 0 <= row < rows && 0 <= col < cols;
        cells[index(row, col)] = value;
    }

    @Op("[_,?]=") void setCol(Int col, List<Element> vector) {
        assert:bounds 0 <= col < cols && vector.size == rows;
        Int index = col;
        for (Int row : 0..<rows) {
            cells[index] = vector[row];
            index += cols;
        }
    }

    @Override
    @Op("[?,_]") List<Element> getRow(Int row) {
        assert:bounds 0 <= row < rows;
        return cells[index(row) ..< index(row+1)];
    }

    @Override
    @Op("[?,_]=") void setRow(Int row, List<Element> vector) {
        assert:bounds 0 <= row < rows && vector.size == cols;
        Int offset = index(row);
        for (Int col : 0..<cols) {
            cells[col+offset] = vector[col];
        }
    }

    /**
     * Returns a sub-matrix of this Matrix. The new Matrix will likely be backed by this Matrix,
     * which means that if this Matrix is mutable, changes made to this Matrix may be visible
     * through the new Matrix, and vice versa; if that behavior is not desired, {@link reify} the
     * value returned from this method.
     *
     * @param rowRange  the range of rows of this Matrix to obtain a slice for
     * @param colRange  the range of columns of this Matrix to obtain a slice for
     *
     * @return a slice of this Matrix corresponding to the specified ranges of columns and rows
     *
     * @throws OutOfBounds  if the specified ranges exceed either the lower or upper bounds of
     *                      the dimensions of this Matrix
     */
    @Op("[..]") Matrix slice(Range<Int> rowRange, Range<Int> colRange) {
        assert:bounds 0 <= colRange.effectiveLowerBound < colRange.effectiveUpperBound < cols;
        assert:bounds 0 <= rowRange.effectiveLowerBound < rowRange.effectiveUpperBound < rows;
// TODO GG return new Element[colRange.size, colRange.size](row,col->TODO("slice"));
        return new Matrix<Element>(rowRange.size, colRange.size,(row,col)->TODO("slice")); // TODO
    }

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
        return this;
    }

    // ----- Comparable ----------------------------------------------------------------------------

    /**
     * Compare two arrays of the same type for equality.
     *
     * @return True iff the arrays have the same size, and for each index _i_, the element at that
     *         index from each array is equal
     */
    static <CompileType extends Matrix> Boolean equals(CompileType value1, CompileType value2) {
        Int rows = value1.rows;
        if (rows != value2.rows) {
            return False;
        }

        Int cols = value1.cols;
        if (cols != value2.cols) {
            return False;
        }

        for (Int row : 0 ..< rows) {
            for (Int col : 0 ..< cols) {
                if (value1[row, col] != value2[row, col]) {
                    return False;
                }
            }
        }

        return True;
    }


// TODO numeric mixin
//    /**
//     * True iff the matrix is identity.
//     */
//    Boolean square.get() {
//        if (!square) {
//            return False;
//        }
//
//        for (Int i : 0..<rows) {
//            if (this[i,i] != 1) {
//                return False;
//            }
//        }
//
//        return True;
//    }
// TODO determinant
// TODO trace

    static class RowMatrix<Element>
            extends Matrix<Element> {
    }

    static class RowMatrix<Element>
            extends Matrix<Element> {
    }

    static class ColMatrix<Element>
            extends Matrix<Element> {
    }

    private static class ViewMatrix<Element>
            extends Matrix<Element> {
        /**
         *
         */
        construct(Matrix<Element> matrix, Range<Int> rowRange, Range<Int> colRange) {
            construct Matrix(rowRange.size, colRange.size, []);
            rowOffset = rowRange.effectiveFirst;
            rowFactor = rowRange.descending ? +1 : -1;
            colOffset = colRange.effectiveFirst;
            colFactor = colRange.descending ? +1 : -1;
        } finally {
            if (matrix.is(immutable)) {
                makeImmutable();
            }
        }

        private Matrix<Element> matrix;
        private Range<Int> rowRange;
        private Range<Int> colRange)

        // TODO
    }

    protected static class RowView(Matrix<Element> matrix)
}
