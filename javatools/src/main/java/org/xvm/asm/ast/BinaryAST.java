package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.HashSet;

import static org.xvm.asm.Op.CONSTANT_OFFSET;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A serializable AST-like node to be used for an AST interpreter or back end compiler.
 *
 * @param <C> the class representing constants in the pool
 */
public abstract class BinaryAST<C> {

    /**
     * @return the byte value (as an int in the range 0..255) that indicates the node type
     */
    protected abstract NodeType nodeType();

    /**
     * Populate the BinaryAST with data from the passed stream.
     *
     * @param in   the stream to read from
     * @param res  the ConstantResolver to use to look up constants
     */
    protected abstract void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException;

    /**
     * @param res  the ConstantResolver to register constants on
     */
    public abstract void prepareWrite(ConstantResolver<C> res);

    /**
     * Write the node to the provided stream.
     *
     * @param out  a DataOutput stream
     */
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());
        writeBody(out, res);
    }

    /**
     * Write the "body" (the contents) of the node to the provided stream.
     *
     * @param out  a DataOutput stream
     */
    protected abstract void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException;

    /**
     * @return a String describing this node and its tree of contained nodes, for debugging purposes
     */
    public String dump() {
        // non-leaf-node implementations should overwrite this
        return toString();
    }

    /**
     * @return a String describing just this node, for debugging purposes
     */
    @Override
    public String toString() {
        reportUnimplemented("TODO implement toString() for " + this.getClass().getSimpleName());
        return nodeType().name();
    }


    // ----- NodeType enumeration ------------------------------------------------------------------

    /**
     * Enumeration of instantiable BinaryAST node types.
     */
    public enum NodeType {
        None,
        RegAlloc,           // int x; (classified as an expression to simplify "l-value" design)
        NamedRegAlloc,      // int _; (classified as an expression to simplify "l-value" design)
        Assign,             // x=y;
        CondAssign,         // (Boolean _, x) := y
        CondNotNullAssign,  // (Boolean _, x) ?= y
        Expr06,
        Expr07,
        Expr08,
        Expr09,
        Expr10,
        Expr11,
        Expr12,
        Expr13,
        Expr14,
        Expr15,
        Expr16,
        Expr17,
        Expr18,
        Expr19,
        Expr20,
        Expr21,
        Expr22,
        Expr23,
        Expr24,
        Expr25,
        Expr26,
        Expr27,
        Expr28,
        Expr29,
        Expr30,
        Escape,             // reserved #31; not an expression

        RegisterExpr,       // _ (unnamed register expr)
        InvokeExpr,         // foo() (method)   (foo is a const)
        CallExpr,           // foo() (function) (foo is a register/property)
        BindMethodExpr,     // bind method's target
        BindFunctionExpr,   // bind function's arguments
        BinOpAssign,        // x*=y; etc.

        AssignExpr,         // (x <- y)                                         // TODO
        SwitchExpr,                                                             // TODO
        MultiCond,          // >1 ","-delimited conditions                      // TODO
        NotCond,            // if (!(String s ?= foo())){...}                   // TODO
        NotNullCond,        // if (String s ?= foo()){...}                      // TODO
        NotFalseCond,       // if (String s := bar()){...}                      // TODO

        ArrayAccessExpr,    // x[i]
        MatrixAccessExpr,   // x[i, j]
        RelOpExpr,          // "&&", "||", "^", etc.
        DivRemExpr,         // x /% y
        IsExpr,             // x.is(y)
        CondOpExpr,         // "<", ">=", etc.
        CmpChainExpr,       // x < y <= z, etc.
        UnaryOpExpr,        // "+", "-", etc.
        NotExpr,            // !x
        NotNullExpr,        // x?
        TernaryExpr,        // x ? y : z
        TemplateExpr,
        ThrowExpr,
        AssertStmt,

        ConstantExpr,
        ListExpr,
        TupleExpr,
        MapExpr,
        ConvertExpr,
        StmtExpr,
        OuterExpr,
        PropertyExpr,
        NarrowedExpr,

        StmtBlock,          // {...}, do{...}while(False); etc.
        IfThenStmt,         // if(cond){...}
        IfElseStmt,         // if(cond){...}else{...}
        SwitchStmt,         // switch(cond){...}
        LoopStmt,           // while(True){...} etc.
        WhileDoStmt,        // while(cond){...}
        DoWhileStmt,        // do{...}while(cond);
        ForStmt,            // for(init,cond,next){...}
        ForIteratorStmt,    // for(var v : iterator){...}
        ForRangeStmt,       // for(var v : range){...}
        ForListStmt,        // for(var v : list){...}
        ForMapStmt,         // for((var k, var v)) : map){...} etc.
        ForIterableStmt,    // for(var v : iterable){...}
        ContinueStmt,       // continue; or continue Label;
        BreakStmt,          // break; or break Label;
        Return0Stmt,        // return;
        Return1Stmt,        // return expr;
        ReturnNStmt,        // return expr, expr, ...;
        ReturnTStmt,        // return (expr, expr, ...);
        TryCatchStmt,       // using(res){...}, try(res){...} [catch(T e){...}]
        TryFinallyStmt,     // try{...} [catch(T e){...}] finally{...}

        NotImplYet,         // "a node for some form has not yet been implemented" (TODO delete this when done)
        ;

        /**
         * @return a new instance of the AST node that corresponds to this node type
         */
        <C> BinaryAST<C> instantiate() {
            return switch (this) {
                case None               -> null;
                case RegAlloc           -> new RegAllocAST<>(false);
                case NamedRegAlloc      -> new RegAllocAST<>(true);
                case Assign             -> new AssignAST<>(false);
                case BinOpAssign        -> new AssignAST<>(true);

                case Escape,
                     RegisterExpr       -> throw new IllegalStateException();

                case InvokeExpr         -> new InvokeExprAST<>();
                case CallExpr           -> new CallExprAST<>();
                case BindMethodExpr     -> new BindMethodAST<>();
                case BindFunctionExpr   -> new BindFunctionAST<>();
                case ConstantExpr       -> new ConstantExprAST<>();
                case ListExpr           -> new ListExprAST<>();
                case TupleExpr          -> new TupleExprAST<>();
                case MapExpr            -> new MapExprAST<>();
                case ConvertExpr        -> new ConvertExprAST<>();
                case StmtExpr           -> new StmtExprAST<>();
                case OuterExpr          -> new OuterExprAST<>();
                case PropertyExpr       -> new PropertyExprAST<>();
                case NarrowedExpr       -> new NarrowedExprAST<>();
                case ArrayAccessExpr    -> new ArrayAccessExprAST<>();
                case RelOpExpr          -> new RelOpExprAST<>();
                case DivRemExpr         -> new DivRemExprAST<>();
                case IsExpr             -> new IsExprAST<>();
                case CondOpExpr         -> new CondOpExprAST<>();
                case CmpChainExpr       -> new CmpChainExprAST<>();
                case UnaryOpExpr        -> new UnaryOpExprAST<>();
                case NotExpr            -> new NotExprAST<>();
                case NotNullExpr        -> new NotNullExprAST<>();
                case TernaryExpr        -> new TernaryExprAST<>();
                case SwitchExpr         -> new SwitchAST<>(this);
                case TemplateExpr       -> new TemplateExprAST<>();
                case ThrowExpr          -> new ThrowExprAST<>();

                case StmtBlock          -> new StmtBlockAST<>();
                case IfThenStmt         -> new IfStmtAST<>(false);
                case IfElseStmt         -> new IfStmtAST<>(true);
                case LoopStmt           -> new LoopStmtAST<>();
                case WhileDoStmt        -> new WhileStmtAST<>();
                case DoWhileStmt        -> new DoWhileStmtAST<>();
                case ForStmt            -> new ForStmtAST<>();
                case SwitchStmt         -> new SwitchAST<>(this);
                case ContinueStmt       -> new ContinueStmtAST<>();
                case BreakStmt          -> new BreakStmtAST<>();
                case Return0Stmt,
                     Return1Stmt,
                     ReturnNStmt        -> new ReturnStmtAST<>(this);
        // TODO case ReturnTStmt        ->
                case TryCatchStmt       -> new TryCatchStmtAST<>();
                case TryFinallyStmt     -> new TryFinallyStmtAST<>();
                case AssertStmt         -> new AssertStmtAST<>();

                case NotImplYet         -> new NotImplAST<>();

                default -> throw new UnsupportedOperationException("nodeType: " + this);
            };
        }

        private static final NodeType[] NODE_TYPES = NodeType.values();

        /**
         * @param i  the ordinal
         *
         * @return the NodeType enum for the specified ordinal
         */
        public static NodeType valueOf(int i) {
            return NODE_TYPES[i];
        }
    }

    public static BinaryAST[] NO_ASTS   = new BinaryAST[0];
    public static ExprAST[]   NO_EXPRS  = new ExprAST[0];
    public static Object[]    NO_CONSTS = new Object[0];


    // ----- internal ------------------------------------------------------------------------------

    private static final HashSet<String> ALREADY_DISPLAYED = new HashSet();

    static void reportUnimplemented(String msg) {
        if (ALREADY_DISPLAYED.add(msg)) {
            System.err.println(msg);
        }
    }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Deserialize a node and its tree of sub-nodes (if any).
     *
     * @param in   the steam containing the node to deserialize
     * @param res  the ConstantResolver to use
     *
     * @return the node deserialized from the stream
     *
     * @param <C> the class representing constants in the pool
     * @param <N> the class representing the expected BinaryAST sub-class
     *
     * @throws IOException  indicates a corrupt stream
     */
    public static <C, N extends BinaryAST<C>> N readAST(DataInput in, ConstantResolver<C> res)
            throws IOException {
        N node = (N) (NodeType.valueOf(in.readUnsignedByte())).instantiate();
        if (node == null) {
            node = (N) new StmtBlockAST<C>(NO_ASTS);
        } else {
            node.readBody(in, res);
        }
        return node;
    }

    protected static <C> void prepareAST(BinaryAST<C> node, ConstantResolver<C> res) {
        if (node != null) {
            node.prepareWrite(res);
        }
    }

    protected static <C> void writeAST(BinaryAST<C> node, DataOutput out, ConstantResolver<C> res)
        throws IOException {
        if (node == null) {
            out.writeByte(NodeType.None.ordinal());
        } else {
            node.write(out, res);
        }
    }

    protected static <C> C prepareConst(C constant, ConstantResolver<C> res) {
        return constant == null ? null : res.register(constant);
    }

    public static <C> BinaryAST<C> makeStatement(BinaryAST<C>[] stmts) {
        return switch (stmts == null ? 0 : stmts.length) {
            case 0  -> new StmtBlockAST<>(NO_ASTS);
            case 1  -> stmts[0];
            default -> new StmtBlockAST<>(stmts);
        };
    }

    public static <C> ExprAST<C> makeCondition(ExprAST<C>[] exprs) {
        if (exprs == null) {
            return null;
        }

        int count = exprs.length;
        if (count == 0) {
            return null;
        }

        if (count == 1) {
            ExprAST<C> expr = exprs[0];
            assert expr != null;
            return expr;
        }

        // TODO return multi condition
        return null;
    }

    protected static <C, N extends ExprAST<C>> N readExprAST(DataInput in, ConstantResolver<C> res)
            throws IOException {
        int n = readPackedInt(in);
        if (n <= CONSTANT_OFFSET) {
            // calculate constant pool identity
            int id  = CONSTANT_OFFSET - n;
            C   val = res.getConstant(id);
            return (N) new ConstantExprAST<>(val);
        }

        if (n < 0 || n >= 32) {
            // special id or register number
            int id = n < 0 ? n : n-32;
            return (N) res.getRegister(id);
        }

        NodeType nodeType = NodeType.valueOf(n);
        if (nodeType == NodeType.None) {
            return null;
        }

        if (nodeType == NodeType.Escape) {
            return readAST(in, res); // "escape" for expressions
        }

        N node = (N) nodeType.instantiate();
        node.readBody(in, res);
        return node;
    }

    protected static <C> void writeExprAST(ExprAST<C> node, DataOutput out, ConstantResolver<C> res)
        throws IOException {
        if (node == null) {
            writePackedLong(out, NodeType.None.ordinal());
        } else {
            node.writeExpr(out, res);
        }
    }

    protected static <C> BinaryAST<C>[] readASTArray(DataInput in, ConstantResolver<C> res)
            throws IOException {
        int count = readMagnitude(in);
        if (count == 0) {
            return NO_ASTS;
        }

        BinaryAST<C>[] nodes = new BinaryAST[count];
        for (int i = 0; i < count; ++i) {
            nodes[i] = readAST(in, res);
        }
        return nodes;
    }

    protected static <C> void prepareASTArray(BinaryAST<C>[] nodes, ConstantResolver<C> res) {
        for (BinaryAST node : nodes) {
            node.prepareWrite(res);
        }
    }

    protected static <C> void writeASTArray(BinaryAST<C>[] nodes, DataOutput out, ConstantResolver<C> res)
            throws IOException {
        writePackedLong(out, nodes.length);
        for (BinaryAST child : nodes) {
            child.write(out, res);
        }
    }

    protected static <C> Object[] readConstArray(DataInput in, ConstantResolver<C> res)
            throws IOException {
        int count = readMagnitude(in);
        if (count == 0) {
            return NO_CONSTS;
        }

        Object[] values = new Object[count];
        for (int i = 0; i < count; ++i) {
            values[i] = res.getConstant(readMagnitude(in));
        }
        return values;
    }

    // TODO CP make sure we actually use this
    protected static <C> void prepareConstArray(Object[] values, ConstantResolver<C> res) {
        int count = values == null ? 0 : values.length;
        for (int i = 0; i < count; ++i) {
            values[i] = res.register((C) values[i]);
        }
    }

    protected static <C> void writeConstArray(Object[] values, DataOutput out, ConstantResolver<C> res)
            throws IOException {
        int count = values == null ? 0 : values.length;
        writePackedLong(out, count);
        for (int i = 0; i < count; ++i) {
            writePackedLong(out, res.indexOf((C) values[i]));
        }
    }

    protected static <C> ExprAST<C>[] readExprArray(DataInput in, ConstantResolver<C> res)
            throws IOException {
        int count = readMagnitude(in);
        if (count == 0) {
            return NO_EXPRS;
        }

        ExprAST<C>[] exprs = new ExprAST[count];
        for (int i = 0; i < count; ++i) {
            exprs[i] = readExprAST(in, res);
        }
        return exprs;
    }

    protected static <C> void writeExprArray(ExprAST<C>[] nodes, DataOutput out, ConstantResolver<C> res)
            throws IOException {
        int count = nodes == null ? 0 : nodes.length;
        writePackedLong(out, count);
        for (int i = 0; i < count; ++i) {
            nodes[i].writeExpr(out, res);
        }
    }


    // ----- ConstantResolver interface ------------------------------------------------------------

    /**
     * Represents a constant pool.
     *
     * @param <C>  the class of a constant from the pool
     */
    public interface ConstantResolver<C> {
        /**
         * @param constant  a constant that must be present in the constant pool, or null
         *
         * @return the unique constant reference to use; null iff the passed constant is null
         */
        C register(C constant);

        /**
         * @param id  constant pool index
         *
         * @return the constant from the constant pool
         */
        C getConstant(int id);

        /**
         * @param name  the type name
         *
         * @return the type constant from the constant pool for the specified name
         */
        C typeForName(String name);

        /**
         * @param value  some constant
         *
         * @return the type constant for the value constant
         */
        C typeOf(C value);

        /**
         * @param constant a constant that must be present in the constant pool
         *
         * @return the index in the constant pool of that constant
         */
        int indexOf(C constant);

        /**
         * Used during deserialization: Notify the resolver that a register scope is being entered.
         */
        void enter();

        /**
         * Used during deserialization: Register a register.
         *
         * @param reg  the register to register
         */
        void register(RegisterAST<C> reg);

        /**
         * Used during deserialization: Obtain a previously registered register.
         *
         * @param regId  the id of a previously registered register
         *
         * @return the previously registered register
         */
        RegisterAST<C> getRegister(int regId);

        /**
         * Used during deserialization: Notify the resolver that a register scope is being exited.
         */
        void exit();

        /**
         * Helper method to register an array of constants by changing the array content.
         */
        default void registerAll(Object[] constants) {
            for (int i = 0, c = constants.length; i < c; i++) {
                constants[i] = register((C) constants[i]);
            }
        }
    }
}