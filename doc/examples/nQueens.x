/**
 * A solver for the classic n-queens problem.
 *
 * @see https://rosettacode.org/wiki/N-queens_problem
 */
module nQueens {
    void run(String[] args=[]) {
        @Inject Console console;

        Int extent = 8;
        if (!args.empty) {
            try {
                extent = new IntLiteral(args[0]).toInt();
            } catch (Exception _) {}
        }

        Int count = new Board(extent).findSolutions(board ->
                console.print($"{board}\n"));
        console.print($"{count} solutions found");
    }

    /**
     * `Board` represents a chess board that holds only queens. The board
     * is organized as columns 0 ("A") to `n`, and rows 0 (rank "1") to `n`.
     */
    const Board(Int extent) {
        /**
         * Construct an empty board.
         */
        construct(Int extent) {
            this.extent  = extent;
            this.queens  = new Boolean[extent*extent];
            this.claimed = new Boolean[extent*extent];
        }

        /**
         * Internal: Construct a specifically-populated board.
         */
        private construct(Int extent, Boolean[] queens, Boolean[] claimed) {
            this.extent  = extent;
            this.queens  = queens;
            this.claimed = claimed;
        }

        /**
         * Each bit represents the presence of a queen on a specific square.
         */
        private Boolean[] queens;

        /**
         * Each bit represents a queen or a threat from queen to a specific square.
         */
        private Boolean[] claimed;

        /**
         * Translate a column and row to an index of the specified square,
         * used with the [queens] and [claimed] properties.
         */
        private Int index(Int col, Int row) = row * extent + col;

        /**
         * Determine if the specified square has a queen in it.
         */
        Boolean occupied(Int col, Int row) {
            return queens[index(col, row)];
        }

        /**
         * Determine if the specified square is safe from all the queens.
         */
        Boolean safe(Int col, Int row) {
            return !claimed[index(col, row)];
        }

        /**
         * Attempt to place a queen in a specified square.
         *
         * @return True iff a queen can be safely placed in the specified square
         * @return (conditional) the new Board with the queen placed as requested
         */
        conditional Board placeQueen(Int col, Int row) {
            assert:bounds 0 <= col < extent && 0 <= row < extent;
            if (!safe(col, row)) {
                return False;
            }

            Boolean[] newQueens  = queens.toArray(Fixed);
            newQueens[index(col, row)] = True;

            Boolean[] newClaimed = claimed.toArray(Fixed);
            newClaimed[index(col, row)] = True;

            // claim all threatened spaces
            for (Int i : 0..<extent) {
                newClaimed[index(i, row)] = True;
                newClaimed[index(col, i)] = True;
                val diagDownRow = row + i - col;
                if (0 <= diagDownRow < extent) {
                    newClaimed[index(i, diagDownRow)] = True;
                }
                val diagUpRow = row - i + col;
                if (0 <= diagUpRow < extent) {
                    newClaimed[index(i, diagUpRow)] = True;
                }
            }
            return True, new Board(extent, newQueens, newClaimed);
        }

        /**
         * Attempt to find all solutions to the n-queens problem.
         */
        Int findSolutions(function void(Board) yield) = findSolutions(yield, 0);

        /**
         * Internal: For a given `n`, attempt to find all solutions to the
         * n-queens problem, starting with the specified column and
         * recursively solving by moving to the next column for each
         * potential solution found in the specified column.
         */
        private Int findSolutions(function void(Board) yield, Int col) {
            if (col == extent) {
                // we've passed all of the columns, so it's a solution
                yield(this);
                return 1;
            }

            Int count = 0;
            for (Int rank : extent..1) {
                val row = extent-rank;
                if (Board afterPlacing := placeQueen(col, row)) {
                    count += afterPlacing.findSolutions(yield, col + 1);
                }
            }
            return count;
        }

        @Override String toString() {
            val buf = new StringBuffer();
            for (Int rank : extent..1) {
                buf.append($"{rank % 10} |");
                val row = extent-rank;
                for (Int col : 0..<extent) {
                    buf.add(occupied(col, row) ? 'q' : '_').add('|');
                }
                buf.add('\n');
            }
            buf.append("  ");
            for (Int col : 0..<extent) {
                buf.add(' ').add('A' + col % 26);
            }
            return buf.toString();
        }
    }
}