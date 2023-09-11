package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.xvm.util.PackedInteger;

import static org.xvm.asm.ast.BinaryAST.NodeType.SwitchExpr;
import static org.xvm.asm.ast.BinaryAST.NodeType.SwitchStmt;

import static org.xvm.util.Handy.indentLines;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.readPackedLong;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Supports the "switch" statement and expression.
 */
public class SwitchAST<C>
        extends ExprAST<C> {

    private ExprAST<C>[]   exprs;
    private transient int  colCount;
    private long           isaTest;
    private Object[][]     cases;
    private BinaryAST<C>[] bodies;
    private Object[]       resultTypes; // TODO make this transient (but we can't right now, because "C")

    SwitchAST(NodeType nodeType) {
        resultTypes = nodeType == SwitchExpr ? NO_CONSTS : null;
    }

    /**
     * Construct a switch statement.
     *
     * @param exprs    one or more switch expressions, each of which must yield one or more values
     * @param isaTest  an array of booleans, with true indicating an ".is()" test
     * @param cases    an array of cases, each of which is an array of constant values
     * @param bodies   an array of bodies, corresponding to the cases
     */
    public SwitchAST(ExprAST<C>[]   exprs,
                     long           isaTest,
                     C[][]          cases,
                     BinaryAST<C>[] bodies) {
        this(exprs, isaTest, cases, bodies, null);
    }

    /**
     * Construct a switch expression.
     *
     * @param exprs    one or more switch expressions, each of which must yield one or more values
     * @param isaTest  an array of booleans, with true indicating an ".is()" test
     * @param cases    an array of cases, each of which is an array of constant values
     * @param bodies   an array of bodies, corresponding to the cases
     * @param resultTypes  an array of types returned from a switch expression; a switch statement
     */
    public SwitchAST(ExprAST<C>[]   exprs,
                     long           isaTest,
                     C[][]          cases,
                     BinaryAST<C>[] bodies,
                     C[]            resultTypes) {

        // check the expressions that produce the values to match, and determine the "width" of each
        // case, i.e. the number of values to match
        assert exprs != null && exprs.length > 0 && Arrays.stream(exprs).allMatch(Objects::nonNull);
        for (ExprAST expr : exprs) {
            int resultCount = expr.getCount();
            assert resultCount > 0;
            colCount += resultCount;
        }

        // check the
        assert isaTest == 0 || Long.numberOfTrailingZeros(Long.highestOneBit(isaTest)) <= colCount;

        // check cases
        assert cases != null && Arrays.stream(cases).allMatch(row -> row != null
                && row.length == colCount && Arrays.stream(row).allMatch(Objects::nonNull));

        // check bodies that are associated with some or all of the cases
        assert bodies != null && bodies.length <= cases.length;
        if (bodies.length < cases.length) {
            BinaryAST<C>[] newBodies = new BinaryAST[cases.length];
            System.arraycopy(bodies, 0, newBodies, 0, bodies.length);
            bodies = newBodies;
        }

        // check result types
        if (resultTypes != null) {
            assert resultTypes.length > 0 && Arrays.stream(resultTypes).allMatch(Objects::nonNull);
            int resultCount = resultTypes.length;
            for (BinaryAST<C> body : bodies) {
                assert body instanceof ExprAST<C>;
                assert ((ExprAST<C>) body).getCount() >= resultCount;     // REVIEW GG ">=" or "=="?
            }
        }

        this.exprs       = exprs;
        this.isaTest     = isaTest;
        this.cases       = cases;
        this.bodies      = bodies;
        this.resultTypes = resultTypes;
    }

    @Override
    public NodeType nodeType() {
        return resultTypes == null ? SwitchStmt : SwitchExpr;
    }

    public ExprAST<C>[] getConditions() {
        return exprs;
    }

    public int getColumnCount() {
        return colCount;
    }

    public long isIsTypeTest() {
        return isaTest;
    }

    public boolean isIsTypeTest(int column) {
        assert column >= 0 && column < colCount;
        return (isaTest & (1L << column)) != 0;
    }

    /**
     * @return an array or rows, each of which is an array of columns, each of which is a "C" that
     *         represents a "case" value
     */
    public Object[][] getCases() {
        return cases;
    }

    public C getCaseValue(int row, int col) {
        return (C) cases[row][col];
    }

    public BinaryAST<C>[] getBodies() {
        return bodies;
    }

    /**
     * @return an Iterator of {@Code (Object[] | BinaryAST<C>)} in the order as they appeared in the
     *         source code, where a {@Code Object[]} indicates a "case" (containing a {@Code C}
     *         instance for each case value), and a {@Code BinaryAST<C>} indicates a body
     */
    Iterator contents() {
        return new Iterator() {
            int     cur       = 0;
            boolean checkBody = false;
            Object  loaded    = null;

            @Override
            public boolean hasNext() {
                if (loaded != null) {
                    return true;
                }

                if (checkBody) {
                    loaded = bodies[cur];
                    ++cur;
                    checkBody = false;
                    if (loaded != null) {
                        return true;
                    }
                }

                if (cur < cases.length) {
                    loaded    = cases[cur];
                    checkBody = true;
                    return true;
                }

                return false;
            }

            @Override
            public Object next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                Object value = loaded;
                loaded = null;
                return value;
            }
        };
    }

    @Override
    public int getCount() {
        return colCount;
    }

    @Override
    public C getType(int i) {
        assert i >= 0 && i < colCount;
        return (C) resultTypes[i];
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        res.enter();
        exprs = readExprArray(in, res);
        isaTest = readPackedLong(in);       // currently only supports up to 64 values in each case

        int rowCount = readMagnitude(in);

        // calculate column count
        colCount = 0;
        for (ExprAST expr : exprs) {
            int resultCount = expr.getCount();
            assert resultCount > 0;
            colCount += resultCount;
        }

        // read the case "statements"
        cases = new Object[rowCount][colCount];
        for (int row = 0; row < rowCount; ++row) {
            for (int col = 0; col < colCount; ++col) {
                cases[row][col] = res.getConstant(readMagnitude(in));
            }
        }

        // read the bodies, that correspond to some (or all) of the case statements; the header for
        // this section is a packed int representing a bitmap of which of the case statements are
        // followed by a body
        bodies = new BinaryAST[rowCount];
        if (rowCount < 64) {
            long hasBody = readPackedLong(in);
            for (int i = 0; i < rowCount;  ++i) {
                if ((hasBody & (1L << i)) != 0) {
                    res.enter();
                    bodies[i] = readAST(in, res);
                    res.exit();
                }
            }
        } else {
            PackedInteger hasBody = new PackedInteger(in);
            for (int i = 0; hasBody.cmp(PackedInteger.ZERO) != 0; ++i) {
                if (hasBody.and(PackedInteger.ONE).cmp(PackedInteger.ZERO) != 0) {
                    res.enter();
                    bodies[i] = readAST(in, res);
                    res.exit();
                }
                hasBody = hasBody.ushr(1);
            }
        }
        res.exit();

        if (nodeType() == SwitchExpr) {
            resultTypes = readConstArray(in, res);
        }
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        res.enter();
        prepareASTArray(exprs, res);
        for (Object[] row : cases) {
            prepareConstArray(row, res);
        }
        for (BinaryAST<C> body : bodies) {
            res.enter();
            prepareAST(body, res);
            res.exit();
        }
        res.exit();
        prepareConstArray(resultTypes, res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        writeExprArray(exprs, out, res);
        writePackedLong(out, isaTest);

        // write the case "statements"
        int rowCount = cases.length;
        writePackedLong(out, rowCount);
        for (Object[] row : cases) {
            for (int col = 0; col < colCount; ++col) {
                writePackedLong(out, res.indexOf((C) row[col]));
            }
        }

        // write the bodies that correspond to the case statements
        int check = 0;
        if (rowCount < 64) {
            long bits = 0;
            for (int row = 0; row < rowCount; ++row) {
                if (bodies[row] != null) {
                    bits |= 1L << row;
                    ++check;
                }
            }
            writePackedLong(out, bits);
        } else {
            PackedInteger bits = PackedInteger.ZERO;
            for (int row = 0; row < rowCount; ++row) {
                if (bodies[row] != null) {
                    bits = bits.or(PackedInteger.ONE.shl(row));
                    ++check;
                }
            }
            bits.writeObject(out);
        }
        for (BinaryAST<C> body : bodies) {
            if (body != null) {
                writeAST(body, out, res);
                --check;
            }
        }
        assert check == 0;

        if (nodeType() == SwitchExpr) {
            writeConstArray(resultTypes, out, res);
        }
    }

    @Override
    public String dump() {
        StringBuilder buf = new StringBuilder("switch (");
        for (ExprAST<C> expr : exprs) {
            buf.append(expr.dump());
            buf.append(", ");
        }
        buf.setLength(buf.length()-2);
        buf.append(") {");

        for (int row = 0, rowCount = cases.length; row < rowCount; ++row) {
            buf.append("\ncase ");
            Object[] values = cases[row];
            for (int col = 0; col < colCount; ++col) {
                if (col > 1) {
                    buf.append(", ");
                }
                buf.append(values[col]);
            }
            buf.append(":");

            BinaryAST<C> body = bodies[row];
            if (body != null) {
                buf.append("\n").append(indentLines(body.dump(), "  "));
            }
        }
        buf.append("\n}");
        return buf.toString();
    }
}