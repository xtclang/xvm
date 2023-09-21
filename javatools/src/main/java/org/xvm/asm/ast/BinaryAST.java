package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.HashSet;

import org.xvm.asm.Constant;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.asm.Op.CONSTANT_OFFSET;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A serializable AST-like node to be used for an AST interpreter or back end compiler.
 */
public abstract class BinaryAST {

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
    protected abstract void readBody(DataInput in, ConstantResolver res)
            throws IOException;

    /**
     * @param res  the ConstantResolver to register constants on
     */
    public abstract void prepareWrite(ConstantResolver res);

    /**
     * Write the node to the provided stream.
     *
     * @param out  a DataOutput stream
     */
    public void write(DataOutput out, ConstantResolver res)
            throws IOException {
        out.writeByte(nodeType().ordinal());
        writeBody(out, res);
    }

    /**
     * Write the "body" (the contents) of the node to the provided stream.
     *
     * @param out  a DataOutput stream
     */
    protected abstract void writeBody(DataOutput out, ConstantResolver res)
            throws IOException;

    /**
     * @return a String describing this binary AST node, for debugging purposes
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
        None,               // 00:
        PropertyExpr,       // 01: property access
        InvokeExpr,         // 02: foo() (method)   (foo is a const)
        CondOpExpr,         // 03: "<", ">=", etc.
        Assign,             // 04: x=y;
        NamedRegAlloc,      // 05: int x; (classified as an expression to simplify "l-value" design)
        RelOpExpr,          // 06: "&&", "||", "^", etc.
        NarrowedExpr,       // 07:
        UnaryOpExpr,        // 08: "+", "-", etc.
        NewExpr,            // 09:
        ThrowExpr,          // 0A:
        CallExpr,           // 0B: foo() (function) (foo is a register/property)
        ArrayAccessExpr,    // 0C: x[i]
        BinOpAssign,        // 0D: x*=y; etc.
        TernaryExpr,        // 0E: x ? y : z
        OuterExpr,          // 0F:
        NotExpr,            // 10: !x
        MultiExpr,          // 11: (expr1, expr2, ...)
        BindFunctionExpr,   // 12: bind function's arguments
        DivRemExpr,         // 13: x /% y
        BindMethodExpr,     // 14: bind method's target
        NotNullExpr,        // 15: x?
        ConvertExpr,        // 16:
        TemplateExpr,       // 17:
        NewChildExpr,       // 18:
        TupleExpr,          // 19:
        CmpChainExpr,       // 1A: x < y <= z, etc.
        UnpackExpr,         // 1B:
        SwitchExpr,         // 1C: s = switch () {...}
        NewVirtualExpr,     // 1D:
        ListExpr,           // 1E:
        Escape,             // 1F: reserved #31: followed by NodeType ordinal as unsigned byte
        AnnoRegAlloc,       // same as RegAlloc, but annotated
        AnnoNamedRegAlloc,  // same as NamedRegAlloc, but annotated
        RegAlloc,           // int _; (classified as an expression to simplify "l-value" design)
        RegisterExpr,       // _ (unnamed register expr)
        InvokeAsyncExpr,    // foo^() (method)   (foo is a const)
        CallAsyncExpr,      // foo^() (function) (foo is a register/property)
        IsExpr,             // x.is(y)
        NegExpr,            // -x
        BitNotExpr,         // ~x
        PreIncExpr,         // --x
        PreDecExpr,         // ++x
        PostIncExpr,        // x--
        PostDecExpr,        // x++
        RefOfExpr,          // &x
        VarOfExpr,          // &x
        ConstantExpr,       //
        MapExpr,            //
        StmtExpr,           //
        NotCond,            // if (!(String s ?= foo())){...} TODO
        NotNullCond,        // if (String s ?= foo()){...}    TODO
        NotFalseCond,       // if (String s := bar()){...}    TODO
        MatrixAccessExpr,   // x[i, j]                        TODO
        AssertStmt,         //
        StmtBlock,          // {...}, do{...}while(False); etc.
        MultiStmt,          //
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
        NotImplYet,         // "a node for some form has not yet been implemented" TODO delete this
        ;

        /**
         * @return a new instance of the AST node that corresponds to this node type
         */
         BinaryAST instantiate() {
            return switch (this) {
                case None               -> null;
                case Escape,
                     RegisterExpr       -> throw new IllegalStateException();
                case RegAlloc,
                     NamedRegAlloc,
                     AnnoRegAlloc,
                     AnnoNamedRegAlloc  -> new RegAllocAST(this);
                case Assign             -> new AssignAST(false);
                case BinOpAssign        -> new AssignAST(true);
                case InvokeExpr,
                     InvokeAsyncExpr    -> new InvokeExprAST(this);
                case CallExpr,
                     CallAsyncExpr      -> new CallExprAST(this);
                case BindMethodExpr     -> new BindMethodAST();
                case BindFunctionExpr   -> new BindFunctionAST();
                case ConstantExpr       -> new ConstantExprAST();
                case ListExpr           -> new ListExprAST();
                case TupleExpr          -> new TupleExprAST();
                case MultiExpr          -> new MultiExprAST();
                case MapExpr            -> new MapExprAST();
                case ConvertExpr        -> new ConvertExprAST();
                case StmtExpr           -> new StmtExprAST();
                case OuterExpr          -> new OuterExprAST();
                case PropertyExpr       -> new PropertyExprAST();
                case NarrowedExpr       -> new NarrowedExprAST();
                case NewExpr,
                     NewVirtualExpr,
                     NewChildExpr       -> new NewExprAST(this);
                case ArrayAccessExpr    -> new ArrayAccessExprAST();
                case RelOpExpr          -> new RelOpExprAST();
                case DivRemExpr         -> new DivRemExprAST();
                case IsExpr             -> new IsExprAST();
                case CondOpExpr         -> new CondOpExprAST();
                case CmpChainExpr       -> new CmpChainExprAST();
                case NotExpr,
                     NegExpr,
                     BitNotExpr,
                     PreIncExpr,
                     PreDecExpr,
                     PostIncExpr,
                     PostDecExpr,
                     RefOfExpr,
                     VarOfExpr          -> new UnaryOpExprAST(this);
                case UnaryOpExpr        -> new UnaryOpExprAST();
                case NotNullExpr        -> new NotNullExprAST();
                case TernaryExpr        -> new TernaryExprAST();
                case UnpackExpr         -> new UnpackExprAST();
                case SwitchStmt,
                     SwitchExpr         -> new SwitchAST(this);
                case TemplateExpr       -> new TemplateExprAST();
                case ThrowExpr          -> new ThrowExprAST();
                case StmtBlock,
                     MultiStmt          -> new StmtBlockAST(this);
                case IfThenStmt,
                     IfElseStmt         -> new IfStmtAST(this);
                case LoopStmt           -> new LoopStmtAST();
                case WhileDoStmt        -> new WhileStmtAST();
                case DoWhileStmt        -> new DoWhileStmtAST();
                case ForStmt            -> new ForStmtAST();
                case ForIteratorStmt,
                     ForRangeStmt,
                     ForListStmt,
                     ForMapStmt,
                     ForIterableStmt    -> new ForEachStmtAST(this);
                case ContinueStmt       -> new ContinueStmtAST();
                case BreakStmt          -> new BreakStmtAST();
                case Return0Stmt,
                     Return1Stmt,
                     ReturnNStmt        -> new ReturnStmtAST(this);
        // TODO case ReturnTStmt        ->
                case TryCatchStmt       -> new TryCatchStmtAST();
                case TryFinallyStmt     -> new TryFinallyStmtAST();
                case AssertStmt         -> new AssertStmtAST();
                case NotImplYet         -> new NotImplAST();
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

    public static final BinaryAST[]    NO_ASTS   = new BinaryAST[0];
    public static final ExprAST[]      NO_EXPRS  = new ExprAST[0];
    public static final Constant[]     NO_CONSTS = Constant.NO_CONSTS;
    public static final TypeConstant[] NO_TYPES  = TypeConstant.NO_TYPES;
    public static final RegisterAST[]  NO_REGS   = new RegisterAST[0];
    public static final RegAllocAST[]  NO_ALLOCS = new RegAllocAST[0];
    public static final ExprAST        POISON    = PoisonAST.INSTANCE;


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
     * @param <N> the class representing the expected BinaryAST sub-class
     *
     * @throws IOException  indicates a corrupt stream
     */
    public static <N extends BinaryAST> N readAST(DataInput in, ConstantResolver res)
            throws IOException {
        N node = (N) (NodeType.valueOf(in.readUnsignedByte())).instantiate();
        if (node == null) {
            node = (N) new StmtBlockAST(NO_ASTS, false);
        } else {
            node.readBody(in, res);
        }
        return node;
    }

    protected static void prepareAST(BinaryAST node, ConstantResolver res) {
        if (node != null) {
            node.prepareWrite(res);
        }
    }

    protected static void writeAST(BinaryAST node, DataOutput out, ConstantResolver res)
        throws IOException {
        if (node == null) {
            out.writeByte(NodeType.None.ordinal());
        } else {
            node.write(out, res);
        }
    }

    protected static BinaryAST unwrapStatement(BinaryAST stmt) {
        if (stmt instanceof StmtBlockAST block && block.getStmts().length == 1) {
            return block.getStmts()[0];
        }
        return stmt;
    }

    /**
     * Make a synthetic statement block for the specified statements without creating a new scope.
     */
    public static BinaryAST makeMultiStatement(BinaryAST[] stmts) {
        return switch (stmts == null ? 0 : stmts.length) {
            case 0  -> new StmtBlockAST(NO_ASTS, false);
            case 1  -> stmts[0];
            default -> new StmtBlockAST(stmts, false);
        };
    }

    public static ExprAST makeCondition(ExprAST[] exprs) {
        if (exprs == null) {
            return null;
        }

        int count = exprs.length;
        if (count == 0) {
            return null;
        }

        if (count == 1) {
            ExprAST expr = exprs[0];
            assert expr != null;
            return expr;
        }

        return new MultiExprAST(exprs);
    }

    protected static ExprAST readExprAST(DataInput in, ConstantResolver res)
            throws IOException {
        int n = readPackedInt(in);
        if (n <= CONSTANT_OFFSET) {
            // calculate constant pool identity
            int id  = CONSTANT_OFFSET - n;
            Constant   val = res.getConstant(id);
            return new ConstantExprAST(val);
        }

        if (n < 0 || n >= 32) {
            // special id or register number
            int id = n < 0 ? n : n-32;
            return res.getRegister(id);
        }

        NodeType nodeType = NodeType.valueOf(n);
        if (nodeType == NodeType.None) {
            return null;
        }

        if (nodeType == NodeType.Escape) {
            return readAST(in, res); // "escape" for expressions
        }

        ExprAST node = (ExprAST) nodeType.instantiate();
        node.readBody(in, res);
        return node;
    }

    protected static void writeExprAST(ExprAST node, DataOutput out, ConstantResolver res)
        throws IOException {
        if (node == null) {
            writePackedLong(out, NodeType.None.ordinal());
        } else {
            node.writeExpr(out, res);
        }
    }

    protected static BinaryAST[] readASTArray(DataInput in, ConstantResolver res)
            throws IOException {
        int count = readMagnitude(in);
        if (count == 0) {
            return NO_ASTS;
        }

        BinaryAST[] nodes = new BinaryAST[count];
        for (int i = 0; i < count; ++i) {
            nodes[i] = readAST(in, res);
        }
        return nodes;
    }

    protected static void prepareASTArray(BinaryAST[] nodes, ConstantResolver res) {
        for (BinaryAST node : nodes) {
            node.prepareWrite(res);
        }
    }

    protected static void writeASTArray(BinaryAST[] nodes, DataOutput out, ConstantResolver res)
            throws IOException {
        writePackedLong(out, nodes.length);
        for (BinaryAST child : nodes) {
            child.write(out, res);
        }
    }

    protected static Constant[] readConstArray(DataInput in, ConstantResolver res)
            throws IOException {
        int count = readMagnitude(in);
        if (count == 0) {
            return NO_CONSTS;
        }

        Constant[] values = new Constant[count];
        for (int i = 0; i < count; ++i) {
            values[i] = res.getConstant(readMagnitude(in));
        }
        return values;
    }

    protected static TypeConstant[] readTypeArray(DataInput in, ConstantResolver res)
            throws IOException {
        int count = readMagnitude(in);
        if (count == 0) {
            return NO_TYPES;
        }

        TypeConstant[] types = new TypeConstant[count];
        for (int i = 0; i < count; ++i) {
            types[i] = (TypeConstant) res.getConstant(readMagnitude(in));
        }
        return types;
    }

    protected static void prepareConstArray(Constant[] values, ConstantResolver res) {
        int count = values == null ? 0 : values.length;
        for (int i = 0; i < count; ++i) {
            values[i] = res.register(values[i]);
        }
    }

    protected static void writeConstArray(Constant[] values, DataOutput out, ConstantResolver res)
            throws IOException {
        int count = values == null ? 0 : values.length;
        writePackedLong(out, count);
        for (int i = 0; i < count; ++i) {
            writePackedLong(out, res.indexOf(values[i]));
        }
    }

    protected static ExprAST[] readExprArray(DataInput in, ConstantResolver res)
            throws IOException {
        int count = readMagnitude(in);
        if (count == 0) {
            return NO_EXPRS;
        }

        ExprAST[] exprs = new ExprAST[count];
        for (int i = 0; i < count; ++i) {
            exprs[i] = readExprAST(in, res);
        }
        return exprs;
    }

    protected static void writeExprArray(ExprAST[] nodes, DataOutput out, ConstantResolver res)
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
     */
    public interface ConstantResolver {
        /**
         * @param constant  a constant that must be present in the constant pool, or null
         *
         * @return the unique constant reference to use; null iff the passed constant is null
         */
        Constant register(Constant constant);

        /**
         * @param id  constant pool index
         *
         * @return the constant from the constant pool
         */
        Constant getConstant(int id);

        /**
         * @param name  the type name
         *
         * @return the type constant from the constant pool for the specified name
         */
        TypeConstant typeForName(String name);

        /**
         * @param constant a constant that must be present in the constant pool
         *
         * @return the index in the constant pool of that constant
         */
        int indexOf(Constant constant);

        /**
         * @param params  the registers to use to accept the parameter arguments for the method
         */
        void init(RegisterAST[] params);

        /**
         * Used during deserialization: Notify the resolver that a register scope is being entered.
         */
        void enter();

        /**
         * Used during deserialization: Register a register.
         *
         * @param reg  the register to register
         */
        void register(RegisterAST reg);

        /**
         * Used during deserialization: Obtain a previously registered register.
         *
         * @param regId  the id of a previously registered register
         *
         * @return the previously registered register
         */
        RegisterAST getRegister(int regId);

        /**
         * Used during deserialization: Notify the resolver that a register scope is being exited.
         */
        void exit();
    }
}