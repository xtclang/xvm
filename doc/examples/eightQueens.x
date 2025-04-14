/**
 * A solver for the classic 8-queens problem.
 *
 * @see https://rosettacode.org/wiki/N-queens_problem
 */
module eightQueens {
    void run() {
        @Inject Console console;
        Int count = new Board().solve(b -> console.print($"{b}\n"));
        console.print($"{count} solutions found");
    }

    /**
     * `Board` represents a chess board that holds only queens. The board
     * is organized as columns 0 ("A") to 7 ("H"), and rows 0 (rank "1")
     * to 7 (rank "8").
     */
    const Board {
        /**
         * Construct an empty board.
         */
        construct() {}

        /**
         * Internal: Construct a specifically-populated board.
         */
        private construct(Int queens, Int claimed) {
            this.queens  = queens;
            this.claimed = claimed;
        }

        /**
         * Each bit of this 64-bit integer represents a queen.
         */
        private Int queens;
        /**
         * Each bit of this 64-bit integer represents a queen or a threat.
         */
        private Int claimed;

        /**
         * Translate a column and row to a bit-mask, used with the
         * [queens] and [claimed] properties. Examples:
         * * A1 is (0,0) => 0x0000000000000001
         * * H8 is (7,7) => 0x8000000000000000
         */
        private Int mask(Int col, Int row) = 1 << (row << 3) + col;

        /**
         * Determine if the specified square has a queen in it.
         */
        Boolean occupied(Int col, Int row) {
            return queens & mask(col, row) != 0;
        }

        /**
         * Determine if the specified square is safe from the queens.
         */
        Boolean safe(Int col, Int row) {
            return claimed & mask(col, row) == 0;
        }

        /**
         * Attempt to place a queen in a specified square.
         *
         * @return True iff a queen can be safely placed in the square
         * @return (conditional) the new Board with the new queen on it
         */
        conditional Board placeQueen(Int col, Int row) {
            assert:bounds 0 <= col < 8 && 0 <= row < 8;
            if (!safe(col, row)) {
                return False;
            }

            Int newQueens  = queens | mask(col, row);
            Int newClaimed = claimed | queens;
            // claim all threatened spaces
            for (Int i : 0..7) {
                newClaimed |= mask(i, row) | mask(col, i);
                val diagDownRow = row + i - col;
                if (0 <= diagDownRow < 8) {
                    newClaimed |= mask(i, diagDownRow);
                }
                val diagUpRow = row - i + col;
                if (0 <= diagUpRow < 8) {
                    newClaimed |= mask(i, diagUpRow);
                }
            }
            return True, new Board(newQueens, newClaimed);
        }

        /**
         * Attempt to find all solutions to the n-queens problem.
         */
        Int solve(function void(Board) yield) = solve(yield, 0);

        /**
         * Internal: Attempt to find all solutions to the n-queens problem,
         * starting with the specified column and recursively solving by
         * moving to the next column for each potential solution found in
         * the specified column.
         */
        private Int solve(function void(Board) yield, Int col) {
            if (col == 8) {
                // there is no column 8; we've found a solution
                yield(this);
                return 1;
            }

            Int count = 0;
            for (Int rank : 8..1) {
                val row = 8-rank;
                if (Board afterPlacing := placeQueen(col, row)) {
                    count += afterPlacing.solve(yield, col + 1);
                }
            }
            return count;
        }

        @Override String toString() {
            val buf = new StringBuffer();
            for (Int rank : 8..1) {
                buf.append($"{rank} |");
                val row = 8-rank;
                for (Int col : 0..7) {
                    buf.add(occupied(col, row) ? 'q' : '_').add('|');
                }
                buf.add('\n');
            }
            return buf.append("   A B C D E F G H").toString();
        }
    }
}