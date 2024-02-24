/**
 * A concrete implementation of Matrix that relies on an array for storage. This is a row-dominant
 * implementation.
 *
 * TODO
 * * rely on array for hash code (once Vector and Array are aligned with Matrix)
 */
class ArrayMatrix<Element>
        extends Matrix<Element>
        incorporates conditional FreezableMatrix<Element extends Shareable> { // TODO GG is this order correct?

    /**
     * Construct a fixed size matrix with the specified size and initial value. An initial value is
     * always required.
     *
     * @param rows    the number of rows
     * @param cols    the number of columns
     * @param supply  the value or the supply function for initializing the elements of the matrix
     */
    construct(Int rows, Int cols, Element | function Element(Int, Int) supply) {
        if (supply.is(function Element(Int, Int))) {
            class Flattened<Element>(function Element(Int, Int) supply, Int cols) {
                Int row = 0;
                Int col = 0;
                Element translate(Int _) {
                    if (col >= cols) {
                        ++row;
                        col = 0;
                    }
                    return supply(row,col++);
                }
            }
            construct(rows, cols, new Element[rows*cols](new Flattened(supply, cols).translate));
        } else {
            construct(rows, cols, new Element[rows*cols](supply));
        }
    }

    /**
     * (Internal) Construct a fixed size matrix with the specified size and element storage.
     *
     * @param rows   the number of rows
     * @param cols   the number of columns
     * @param cells  the internal storage array of element values in the matrix
     */
    private construct(Int rows, Int cols, Element[] cells) {
        construct Matrix(rows, cols);
        this.cells = cells;
    }

    /**
     * The storage for the contents of the Matrix. The form of the internal storage of elements is
     * not visible through the API, so that the implementation of `Matrix` can be optimized by the
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

    @Override
    ArrayMatrix reify() {
        return this;
    }

    @Override
    ArrayMatrix duplicate(function Element(Element)? transform = Null) {
        return transform == Null
                ? new ArrayMatrix<Element>(rows, cols, new Array<Element>(cells))
                : new ArrayMatrix<Element>(rows, cols, cells.map(transform).toArray(Fixed));
    }


    // ----- Freezable interface -------------------------------------------------------------------

    /**
     * A mixin that implements the Freezable interface on a Matrix of Shareable elements.
     */
    protected static mixin FreezableMatrix<Element extends Shareable>
            into ArrayMatrix<Element>
            extends Matrix.FreezableMatrix<Element> {

        @Override
        immutable FreezableMatrix freeze(Boolean inPlace = False) {
            if (this.is(immutable)) {
                return this;
            }

            if (inPlace) {
                // while "in-place" means that we're avoiding any unnecessary copies, the reify()
                // is necessary because this Matrix could be a view of another Matrix, and we don't
                // "own" that other Matrix; if this is not a view, then reify() is a no-op
                Matrix<Element> result = reify();
                for (Int row : 0..rows) {
                    for (Int col : 0..cols) {
                        result[row, col] = frozen(result[row, col]);
                    }
                }
                return result.makeImmutable();
            }

            return duplicate(frozen).makeImmutable();
        }
    }
}