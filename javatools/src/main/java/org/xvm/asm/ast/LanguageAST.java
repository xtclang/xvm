package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.HashSet;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A serializable AST-like node to be used for an AST interpreter or back end compiler.
 *
 * @param <C> the class representing constants in the pool
 */
public abstract class LanguageAST<C> {
    // ----- deserialization from stream -----------------------------------------------------------

    /**
     * Deserialize a node and its tree of sub-nodes (if any).
     *
     * @param in   the steam containing the node to deserialize
     * @param res  the ConstantResolver to use
     *
     * @return the node deserialized from the stream
     *
     * @param <C> the class representing constants in the pool
     * @param <N> the class representing the expected LanguageNode sub-class
     *
     * @throws IOException  indicates a corrupt stream
     */
    public static <C, N extends LanguageAST<C>> N deserialize(DataInput in, ConstantResolver<C> res)
            throws IOException {
        N node = (N) instantiate(NodeType.valueOf(in.readUnsignedByte()));
        node.read(in, res);
        return node;
    }


    // ----- LanguageNode public interface ---------------------------------------------------------

    /**
     * @return the byte value (as an int in the range 0..255) that indicates the node type
     */
    public NodeType nodeType() {
        reportUnimplemented("TODO implement nodeType() for " + this.getClass().getSimpleName());
        return NodeType.STMT_NOT_IMPL_YET;
    }

    /**
     * Populate the LanguageNode with data from the passed stream.
     *
     * @param in   the stream to read from
     * @param res  the ConstantResolver to use to look up constants
     */
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        reportUnimplemented("TODO implement read() for " + this.getClass().getSimpleName());
    }

    /**
     * @param res  the ConstantResolver to register constants on
     */
    public void prepareWrite(ConstantResolver<C> res) {
        reportUnimplemented("TODO implement prepareWrite() for " + this.getClass().getSimpleName());
    }

    /**
     * Write the node to the provided stream.
     *
     * @param out  a DataOutput stream
     */
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        reportUnimplemented("TODO implement write() for " + this.getClass().getSimpleName());
    }

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


    // ----- helpers -------------------------------------------------------------------------------

    public static <C> Object[] readConstArray(DataInput in, ConstantResolver<C> res)
            throws IOException {
        int count  = readMagnitude(in);
        if (count == 0) {
            return NO_CONSTS;
        }

        Object[] values = new Object[count];
        for (int i = 0; i < count; ++i) {
            values[i] = res.getConstant(readMagnitude(in));
        }
        return values;
    }

    public static <C> ExprAST<C>[] readExprArray(DataInput in, ConstantResolver<C> res)
            throws IOException {
        int count = readMagnitude(in);
        if (count == 0) {
            return NO_EXPRS;
        }

        ExprAST<C>[] exprs = new ExprAST[count];
        for (int i = 0; i < count; ++i) {
            exprs[i] = deserialize(in, res);
        }
        return exprs;
    }

    public static <C> StmtAST<C>[] readStmtArray(DataInput in, ConstantResolver<C> res)
            throws IOException {
        int count = readMagnitude(in);
        if (count == 0) {
            return NO_STMTS;
        }

        StmtAST<C>[] stmts = new StmtAST[count];
        for (int i = 0; i < count; ++i) {
            stmts[i] = deserialize(in, res);
        }
        return stmts;
    }

    public static <C> LanguageAST<C>[] readASTArray(DataInput in, ConstantResolver<C> res)
            throws IOException {
        int count = readMagnitude(in);
        if (count == 0) {
            return NO_ASTS;
        }

        LanguageAST<C>[] nodes = new LanguageAST[count];
        for (int i = 0; i < count; ++i) {
            nodes[i] = deserialize(in, res);
        }
        return nodes;
    }

    // nodes can have nulls at the tail
    public static <C> void prepareWriteASTArray(ConstantResolver<C> res, LanguageAST<C>[] nodes) {
        for (LanguageAST node : nodes) {
            if (node != null) {
                node.prepareWrite(res);
            }
        }
    }

    public static <C> void writeConstArray(DataOutput out, ConstantResolver<C> res, Object[] values)
            throws IOException {
        int count = values.length;

        writePackedLong(out, count);
        for (int i = 0; i < count; ++i) {
            writePackedLong(out, res.indexOf((C) values[i]));
        }
    }

    // nodes can have nulls at the tail
    public static <C> void writeASTArray(DataOutput out, ConstantResolver<C> res, LanguageAST<C>[] nodes)
            throws IOException {
        writePackedLong(out, nodes.length);
        for (LanguageAST child : nodes) {
            if (child != null) {
                child.write(out, res);
            }
        }
    }


    // ----- inner types ---------------------------------------------------------------------------

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
         * @param constant a constant that must be present in the constant pool
         *
         * @return the index in the constant pool of that constant
         */
        int indexOf(C constant);

        /**
         * Helper method to register an array of constants by changing the array content.
         */
        default void registerAll(Object[] constants) {
            for (int i = 0, c = constants.length; i < c; i++) {
                constants[i] = register((C) constants[i]);
            }
        }
    }

    /**
     * Enumeration of instantiable LanguageAST node types.
     */
    public enum NodeType {
        STMT_NOT_IMPL_YET,  // "a node for some statement form has not yet been implemented"
        STMT_BLOCK,         // {...}, do{...}while(False); etc.
        IF_THEN_STMT,       // if(cond){...}
        IF_ELSE_STMT,       // if(cond){...}else{...}
        SWITCH_STMT,        // switch(cond){...}
        LOOP_STMT,          // while(True){...} etc.
        WHILE_DO_STMT,      // while(cond){...}
        DO_WHILE_STMT,      // do{...}while(cond);
        FOR_STMT,           // for(init,cond,next){...}
        FOR_ITERATOR_STMT,  // for(var v : iterator){...}
        FOR_RANGE_STMT,     // for(var v : range){...}
        FOR_LIST_STMT,      // for(var v : list){...}
        FOR_MAP_STMT,       // for((var k, var v)) : map){...} etc.
        FOR_ITERABLE_STMT,  // for(var v : iterable){...}
        CONTINUE_STMT,      // continue; or continue Label;
        BREAK_STMT,         // break; or break Label;
        EXPR_STMT,          // foo(); etc.
        RETURN_STMT,        // return expr;
        TRY_STMT,           // using(res){...}, try(res){...}
                            // try{...} catch(T e){...}
                            // try{...} finally{...}
        REG_DECL_STMT,
        REG_STORE_STMT,
        ANY_STORE_STMT,
        ASSIGN_STMT,        // lvalue op rvalue, for
        EXPR_NOT_IMPL_YET,  // "a node for some expression form has not yet been implemented"
        CONSTANT_EXPR,
        LIST_EXPR,
        MAP_EXPR,
        REGISTER_EXPR,      // "register" expr
        INVOKE_EXPR,        // invoke expr
        SWITCH_EXPR,
        MULTI_COND,         // >1 ","-delimited conditions
        DECL_COND,          // condition that declares variable
        AND_EXPR,
        OR_EXPR,
        NOT_EXPR,
        XOR_EXPR,
        LOGICAL_OR_EXPR,
        LOGICAL_AND_EXPR,
        ;

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

    static LanguageAST[] NO_ASTS = new LanguageAST[0];

    /**
     * Class hierarchy root for all statements.
     */
    public abstract static class StmtAST<C> extends LanguageAST<C> {}

    /**
     * Class hierarchy root for all expressions.
     */
    public abstract static class ExprAST<C> extends LanguageAST<C> {
        /**
         * @return the number of values yielded by the expression
         */
        public int getCount() {
            // subclasses that can yield more than one value must override this
            return 1;
        }

        /**
         * @param i  a value in the range {@code 0 ..< getCount()}
         *
         * @return the type constant of the i-th value yielded by the expression
         */
        public abstract C getType(int i);
    }

    public static StmtAST[] NO_STMTS  = new StmtAST[0];
    public static ExprAST[] NO_EXPRS  = new ExprAST[0];
    public static Object[]  NO_CONSTS = new Object[0];


    // ----- internal ------------------------------------------------------------------------------

    static <C> LanguageAST<C> instantiate(NodeType nodeType) {
        return switch (nodeType) {
            case STMT_NOT_IMPL_YET  -> new StmtNotImplAST<C>();
            case STMT_BLOCK         -> new StmtBlockAST<C>();
            case IF_THEN_STMT       -> new IfStmtAST<C>(false);
            case IF_ELSE_STMT       -> new IfStmtAST<C>(true);
//            case SWITCH_STMT        -> new ;
            case LOOP_STMT          -> new LoopStmtAST<C>();
            case WHILE_DO_STMT      -> new WhileStmtAST<C>();
            case DO_WHILE_STMT      -> new DoWhileStmtAST<C>();
            case FOR_STMT           -> new ForStmtAST<>();
//            case FOR_ITERATOR_STMT  -> new ;
//            case FOR_RANGE_STMT     -> new ;
//            case FOR_LIST_STMT      -> new ;
//            case FOR_MAP_STMT       -> new ;
//            case FOR_ITERABLE_STMT  -> new ;
//            case CONTINUE_LOOP_STMT -> new ;
            case CONTINUE_STMT      -> new ContinueStmtAST<C>();
            case BREAK_STMT         -> new BreakStmtAST<C>();
            case RETURN_STMT        -> new ReturnStmtAST<C>();
            case TRY_STMT           -> new TryStmtAST<>();
//            case REG_DECL_STMT      -> new ;
//            case REG_STORE_STMT     -> new ;
//            case ANY_STORE_STMT     -> new ;
//            case ASSIGN_STMT        -> new ;
            case EXPR_STMT          -> new ExprStmtAST<C>();
            case EXPR_NOT_IMPL_YET  -> new ExprNotImplAST<C>();
            case CONSTANT_EXPR      -> new ConstantExprAST<C>();
            case LIST_EXPR          -> new ListExprAST<>();
            case MAP_EXPR           -> new MapExprAST<>();
            case REGISTER_EXPR      -> new RegisterAST<>();
            case INVOKE_EXPR        -> new InvokeExprAST<>();
//            case SWITCH_EXPR        -> new ;
//            case MULTI_COND         -> new ;
//            case DECL_COND          -> new ;
//            case AND_EXPR           -> new ;
//            case OR_EXPR            -> new ;
//            case NOT_EXPR           -> new ;
//            case XOR_EXPR           -> new ;
//            case LOGICAL_OR_EXPR    -> new ;
//            case LOGICAL_AND_EXPR   -> new ;
            default -> throw new IllegalStateException("unknown nodeType: " + nodeType);
        };
    }

    private static final HashSet<String> ALREADY_DISPLAYED = new HashSet();

    static void reportUnimplemented(String msg) {
        if (ALREADY_DISPLAYED.add(msg)) {
            System.err.println(msg);
        }
    }
}