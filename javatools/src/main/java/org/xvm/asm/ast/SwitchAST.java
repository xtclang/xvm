package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.xvm.util.PackedInteger;

import static java.lang.Long.highestOneBit;
import static java.lang.Long.numberOfTrailingZeros;

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

    private ExprAST<C>     cond;
    private long           isaTest;
    private Object[]       cases;
    private BinaryAST<C>[] bodies;
    private Object[]       resultTypes; // TODO make this transient (but we can't right now, because "C")

    SwitchAST(NodeType nodeType) {
        resultTypes = nodeType == SwitchExpr ? NO_CONSTS : null;
    }

    /**
     * Construct a switch statement.
     *
     * @param cond     the expression representing the switch condition
     * @param isaTest  an array of booleans, with true indicating an ".is()" test
     * @param cases    an array of cases, each of which is an array of constant values
     * @param bodies   an array of bodies, corresponding to the cases
     */
    public SwitchAST(ExprAST<C>     cond,
                     long           isaTest,
                     C[]            cases,
                     BinaryAST<C>[] bodies) {
        this(cond, isaTest, cases, bodies, null);
    }

    /**
     * Construct a switch expression.
     *
     * @param cond     the expression representing the switch condition
     * @param isaTest  an array of booleans, with true indicating an ".is()" test
     * @param cases    an array of cases, each of which is an array of constant values
     * @param bodies   an array of bodies, corresponding to the cases
     * @param resultTypes  an array of types returned from a switch expression; a switch statement
     */
    public SwitchAST(ExprAST<C>     cond,
                     long           isaTest,
                     C[]            cases,
                     BinaryAST<C>[] bodies,
                     C[]            resultTypes) {
        // check the expressions that produce the values to match
        assert cond != null && cond.getCount() > 0;
        assert isaTest == 0 || numberOfTrailingZeros(highestOneBit(isaTest)) <= cond.getCount();

        // check cases (at most one null)
        assert cases != null && Arrays.stream(cases).filter(Objects::nonNull).count() + 1 >= cases.length;
        int rowCount = cases.length;

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
                if (body instanceof ExprAST<C> expr) {
                    // TODO GG tuple literal in List.x binarySearch() switch compiles as "False"
                    // assert expr.getCount() >= resultCount;
                } else {
                    assert body == null;
                }
            }
        }

        this.cond        = cond;
        this.isaTest     = isaTest;
        this.cases       = cases;
        this.bodies      = bodies;
        this.resultTypes = resultTypes;
    }

    @Override
    public NodeType nodeType() {
        return resultTypes == null ? SwitchStmt : SwitchExpr;
    }

    public ExprAST<C> getCondition() {
        return cond;
    }

    public long isIsTypeTest() {
        return isaTest;
    }

    public boolean isIsTypeTest(int column) {
        assert column >= 0 && column < 64;
        return (isaTest & (1L << column)) != 0;
    }

    /**
     * @return an array of  "case" values
     */
    public Object[] getCases() {
        return cases;
    }

    /**
     * @return an array of bodies (some of which may be null) that correspond to the cases
     */
    public BinaryAST<C>[] getBodies() {
        return bodies;
    }

    /**
     * @return an Iterator of {@Code (C | BinaryAST<C>)} in the order as they appeared in the
     *         source code, where a {@Code C} indicates a "case" value, and a {@Code BinaryAST<C>}
     *         indicates a body
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
        return resultTypes.length;
    }

    @Override
    public C getType(int i) {
        return (C) resultTypes[i];
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        res.enter();
        cond    = readExprAST(in, res);
        isaTest = readPackedLong(in);  // currently only supports up to 64 columns (values per case)

        int rowCount = readMagnitude(in);
        if (rowCount == 0) {
            cases = NO_CONSTS;
        } else {
            cases = new Object[rowCount];
            for (int i = 0; i < rowCount; ++i) {
                int id = (int) readPackedLong(in);
                cases[i] = id >= 0 ? res.getConstant(id) : null;
            }
        }

        assert isaTest == 0L || numberOfTrailingZeros(highestOneBit(isaTest)) < cond.getCount();

        // read the bodies, that correspond to some (or all) of the case statements; the header for
        // this section is a packed int representing a bitmap of which of the case statements are
        // followed by a body
        bodies = new BinaryAST[rowCount];
        boolean isExpr = nodeType() == SwitchExpr;
        if (rowCount < 64) {
            long hasBody = readPackedLong(in);
            for (int i = 0; i < rowCount;  ++i) {
                if ((hasBody & (1L << i)) != 0) {
                    res.enter();
                    bodies[i] = isExpr ? readExprAST(in, res) : readAST(in, res);
                    res.exit();
                }
            }
        } else {
            PackedInteger hasBody = new PackedInteger(in);
            for (int i = 0; hasBody.cmp(PackedInteger.ZERO) != 0; ++i) {
                if (hasBody.and(PackedInteger.ONE).cmp(PackedInteger.ZERO) != 0) {
                    res.enter();
                    bodies[i] = isExpr ? readExprAST(in, res) : readAST(in, res);
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
        prepareAST(cond, res);
        prepareConstArray(cases, res);
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
        cond.writeExpr(out, res);
        writePackedLong(out, isaTest);

        // write the case "statements"
        int count = cases.length;
        writePackedLong(out, count);
        for (int i = 0; i < count; ++i) {
            C value = (C) cases[i];
            writePackedLong(out, value == null ? -1 : res.indexOf(value));
        }

        // write the bodies that correspond to the case statements
        int rowCount = cases.length;
        int check    = 0;
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
        if (nodeType() == SwitchExpr) {
            for (BinaryAST<C> body : bodies) {
                if (body != null) {
                    writeExprAST((ExprAST<C>) body, out, res);
                    --check;
                }
            }
        } else {
            for (BinaryAST<C> body : bodies) {
                if (body != null) {
                    writeAST(body, out, res);
                    --check;
                }
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
        if (cond instanceof MultiExprAST<C> meast) {
            ExprAST<C>[] exprs = meast.getExprs();
            for (ExprAST<C> expr : exprs) {
                buf.append(expr.dump());
                buf.append(", ");
            }
            buf.setLength(buf.length()-2);
        } else {
            buf.append(cond.dump());
        }
        buf.append(") {");

        for (int row = 0, rowCount = cases.length; row < rowCount; ++row) {
            buf.append("\ncase ");
            buf.append(cases[row]);
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