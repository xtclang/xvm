package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.HashSet;


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
        return this.getClass().getSimpleName();
    }


    // ----- inner types ---------------------------------------------------------------------------

    /**
     * Represents a constant pool.
     *
     * @param <C>  the class of a constant from the pool
     */
    public static interface ConstantResolver<C> {
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
        FOR_RANGE_STMT,     // for(var v : iterator){...}
        FOR_LIST_STMT,      // for(var v : iterator){...}
        FOR_MAP_STMT,       // for((var k, var v)) : map){...} etc.
        FOR_ITERABLE_STMT,  // for(var v : iterable){...}
        CONTINUE_LOOP_STMT, // continue; or continue Label;
        BREAK_STMT,         // break; or break Label;
        EXPR_STMT,          // foo(); etc.
        RETURN_STMT,        // return expr;
        TRY_USING_STMT,     // using(res){...} or try(res){...}
        TRY_CATCH_STMT,     // try{...}catch(T e){...} etc.
        TRY_FINALLY_STMT,   // try{...}finally{...}
        REG_DECL_STMT,
        REG_STORE_STMT,
        ANY_STORE_STMT,
        ASSIGN_STMT,        // lvalue op rvalue, for
        EXPR_NOT_IMPL_YET,  // "a node for some expression form has not yet been implemented"
        LIT_EXPR,
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

    /**
     * Class hierarchy root for all statements.
     */
    public abstract static class StmtAST<C> extends LanguageAST<C> {}

    static StmtAST[] NO_STMTS = new StmtAST[0];

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

    static ExprAST[] NO_EXPRS = new ExprAST[0];


    // ----- internal ------------------------------------------------------------------------------

    static <C> LanguageAST<C> instantiate(NodeType nodeType) {
        return switch (nodeType) {
            case STMT_NOT_IMPL_YET  -> new StmtNotImplAST<C>();
            case STMT_BLOCK         -> new StmtBlockAST<C>();
//            case IF_THEN_STMT       -> new ;
//            case IF_ELSE_STMT       -> new ;
//            case SWITCH_STMT        -> new ;
//            case LOOP_STMT          -> new ;
//            case WHILE_DO_STMT      -> new ;
//            case DO_WHILE_STMT      -> new ;
//            case FOR_STMT           -> new ;
//            case FOR_ITERATOR_STMT  -> new ;
//            case FOR_RANGE_STMT     -> new ;
//            case FOR_LIST_STMT      -> new ;
//            case FOR_MAP_STMT       -> new ;
//            case FOR_ITERABLE_STMT  -> new ;
//            case CONTINUE_LOOP_STMT -> new ;
//            case BREAK_STMT         -> new ;
            case RETURN_STMT        -> new ReturnStmtAST<C>();
//            case TRY_USING_STMT     -> new ;
//            case TRY_CATCH_STMT     -> new ;
//            case TRY_FINALLY_STMT   -> new ;
//            case REG_DECL_STMT      -> new ;
//            case REG_STORE_STMT     -> new ;
//            case ANY_STORE_STMT     -> new ;
//            case ASSIGN_STMT        -> new ;
            case EXPR_STMT          -> new ExprStmtAST<C>();
            case EXPR_NOT_IMPL_YET  -> new ExprNotImplAST<C>();
            case LIT_EXPR           -> new LitExprAST<C>();
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
