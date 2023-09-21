package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.xvm.asm.Constant;

import org.xvm.asm.constants.TypeConstant;

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
public class SwitchAST
        extends ExprAST {

    private ExprAST        cond;
    private long           isaTest;
    private Constant[]     cases;
    private BinaryAST[]    bodies;
    private TypeConstant[] resultTypes; // TODO

    SwitchAST(NodeType nodeType) {
        resultTypes = nodeType == SwitchExpr ? NO_TYPES : null;
    }

    /**
     * Construct a switch statement.
     *
     * @param cond     the expression representing the switch condition
     * @param isaTest  an array of booleans, with true indicating an ".is()" test
     * @param cases    an array of cases, each of which is an array of constant values
     * @param bodies   an array of bodies, corresponding to the cases
     */
    public SwitchAST(ExprAST     cond,
                     long           isaTest,
                     Constant[]            cases,
                     BinaryAST[] bodies) {
        this(cond, isaTest, cases, bodies, null);
    }

    /**
     * Construct a switch expression.
     *
     * @param cond         the expression representing the switch condition
     * @param isaTest      an array of booleans, with true indicating an ".is()" test
     * @param cases        an array of cases, each of which is an array of constant values
     * @param bodies       an array of bodies, corresponding to the cases
     * @param resultTypes  an array of types returned from a switch expression; null for a switch
     *                     statement
     */
    public SwitchAST(ExprAST        cond,
                     long           isaTest,
                     Constant[]     cases,
                     BinaryAST[]    bodies,
                     TypeConstant[] resultTypes) {
        // check the expressions that produce the values to match
        assert cond != null && cond.getCount() > 0;
        assert isaTest == 0 || numberOfTrailingZeros(highestOneBit(isaTest)) <= cond.getCount();

        // check cases (at most one null)
        assert cases != null && Arrays.stream(cases).filter(Objects::nonNull).count() + 1 >= cases.length;
        int rowCount = cases.length;

        // check bodies that are associated with some or all of the cases
        assert bodies != null && bodies.length <= cases.length;
        if (bodies.length < cases.length) {
            BinaryAST[] newBodies = new BinaryAST[cases.length];
            System.arraycopy(bodies, 0, newBodies, 0, bodies.length);
            bodies = newBodies;
        }

        // check result types
        if (resultTypes != null) {
            assert resultTypes.length > 0 && Arrays.stream(resultTypes).allMatch(Objects::nonNull);
            int resultCount = resultTypes.length;
            for (BinaryAST body : bodies) {
                if (body instanceof ExprAST expr) {
                    // there are three scenarios when the expression count could be less:
                    // - a Throw or Assert expressions
                    // - a conditional False (which is not currently possible to check here)
                    // - a Ternary expression containing any of these three
                    assert expr.getCount() >= resultCount
                        || expr instanceof ThrowExprAST
                        || (expr.getCount() == 1 && expr instanceof ConstantExprAST)
                        || expr instanceof TernaryExprAST;
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

    public ExprAST getCondition() {
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
    public Constant[] getCases() {
        return cases;
    }

    /**
     * @return an array of bodies (some of which may be null) that correspond to the cases
     */
    public BinaryAST[] getBodies() {
        return bodies;
    }

    /**
     * @return an Iterator of {@Code (Constant | BinaryAST)} in the order as they appeared in the
     *         source code, where a {@Code Constant} indicates a "case" value, and a {@Code BinaryAST}
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
    public TypeConstant getType(int i) {
        return resultTypes[i];
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        res.enter();
        cond    = readExprAST(in, res);
        isaTest = readPackedLong(in);  // currently only supports up to 64 columns (values per case)

        int rowCount = readMagnitude(in);
        if (rowCount == 0) {
            cases = NO_CONSTS;
        } else {
            cases = new Constant[rowCount];
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
            resultTypes = readTypeArray(in, res);
        }
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        res.enter();
        prepareAST(cond, res);
        prepareConstArray(cases, res);
        for (BinaryAST body : bodies) {
            res.enter();
            prepareAST(body, res);
            res.exit();
        }
        res.exit();
        prepareConstArray(resultTypes, res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        cond.writeExpr(out, res);
        writePackedLong(out, isaTest);

        // write the case "statements"
        int count = cases.length;
        writePackedLong(out, count);
        for (int i = 0; i < count; ++i) {
            Constant value = (Constant) cases[i];
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
            for (BinaryAST body : bodies) {
                if (body != null) {
                    writeExprAST((ExprAST) body, out, res);
                    --check;
                }
            }
            writeConstArray(resultTypes, out, res);
        } else {
            for (BinaryAST body : bodies) {
                if (body != null) {
                    writeAST(body, out, res);
                    --check;
                }
            }
        }
        assert check == 0;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("switch (");
        if (cond instanceof MultiExprAST meast) {
            ExprAST[] exprs = meast.getExprs();
            for (ExprAST expr : exprs) {
                buf.append(expr);
                buf.append(", ");
            }
            buf.setLength(buf.length()-2);
        } else {
            buf.append(cond);
        }
        buf.append(") {");

        for (int row = 0, rowCount = cases.length; row < rowCount; ++row) {
            buf.append("\ncase ");
            buf.append(cases[row]);
            buf.append(":");

            BinaryAST body = bodies[row];
            if (body != null) {
                String text = body.toString();
                if (text.indexOf('\n') < 0) {
                    buf.append(' ')
                       .append(text);
                } else {
                    buf.append('\n')
                       .append(indentLines(text, "  "));
                }
            }
        }
        buf.append("\n}");
        return buf.toString();
    }
}