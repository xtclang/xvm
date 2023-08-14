package org.xvm.asm.node;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.HashSet;


/**
 * A serializable AST-like node to be used for an AST interpreter or back end compiler.
 *
 * @param <C> the class representing constants in the pool
 */
public abstract class LanguageNode<C> {
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
    public static <C, N extends LanguageNode<C>> N deserialize(DataInput in, ConstantResolver<C> res)
            throws IOException {
        N node = (N) instantiate(in.readUnsignedByte());
        node.read(in, res);
        return node;
    }


    // ----- LanguageNode public interface ---------------------------------------------------------

    /**
     * @return the byte value (as an int in the range 0..255) that indicates the node type
     */
    public int nodeType() {
        reportUnimplemented("TODO implement nodeType() for " + this.getClass().getSimpleName());
        return 0;
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
        return "TODO implement dump() for " + this.getClass().getSimpleName();
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
     * Class hierarchy root for all statements.
     */
    public abstract static class StatementNode<C>  extends LanguageNode<C> {}

    /**
     * Class hierarchy root for all expressions.
     */
    public abstract static class ExpressionNode<C> extends LanguageNode<C> {}

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


    // ----- internal ------------------------------------------------------------------------------

    private static HashSet<String> ALREADY_DISPLAYED = new HashSet();

    static void reportUnimplemented(String msg) {
        if (ALREADY_DISPLAYED.add(msg)) {
            System.err.println(msg);
        }
    }

    static <C> LanguageNode<C> instantiate(int nodeType) {
        return switch (nodeType) {
            case STMT_BLOCK          -> new StatementBlock<C>();
//            case IF_THEN_STMT        -> new ;
//            case IF_ELSE_STMT        -> new ;
//            case SWITCH_STMT         -> new ;
//            case LOOP_STMT           -> new ;
//            case WHILE_DO_STMT       -> new ;
//            case DO_WHILE_STMT       -> new ;
//            case FOR_STMT            -> new ;
//            case FOR_ITERATOR_STMT   -> new ;
//            case FOR_RANGE_STMT      -> new ;
//            case FOR_LIST_STMT       -> new ;
//            case FOR_MAP_STMT        -> new ;
//            case FOR_ITERABLE_STMT   -> new ;
//            case CONTINUE_LOOP_STMT  -> new ;
//            case BREAK_STMT          -> new ;
            case EXPR_STMT           -> new ExpressionStatement<C>();
//            case RETURN_STMT         -> new ;
//            case TRY_USING_STMT      -> new ;
//            case TRY_CATCH_STMT      -> new ;
//            case TRY_FINALLY_STMT    -> new ;
//            case REG_DECL_STMT       -> new ;
//            case REG_STORE_STMT      -> new ;
//            case ANY_STORE_STMT      -> new ;
//            case ASSIGN_STMT         -> new ;
//            case SWITCH_EXPR         -> new ;
//            case MULTI_COND          -> new ;
//            case DECL_COND           -> new ;
//            case AND_EXPR            -> new ;
//            case OR_EXPR             -> new ;
//            case NOT_EXPR            -> new ;
//            case XOR_EXPR            -> new ;
//            case LOGICAL_OR_EXPR     -> new ;
//            case LOGICAL_AND_EXPR    -> new ;
            default -> throw new IllegalStateException("unknown nodeType: " + nodeType);
        };
    }


    // ----- constants -----------------------------------------------------------------------------

    public static final int STMT_BLOCK          = 0x01;     // {...}, do{...}while(False); etc.
    public static final int IF_THEN_STMT        = 0x02;     // if(cond){...}
    public static final int IF_ELSE_STMT        = 0x03;     // if(cond){...}else{...}
    public static final int SWITCH_STMT         = 0x04;     // switch(cond){...}
    public static final int LOOP_STMT           = 0x05;     // while(True){...} etc.
    public static final int WHILE_DO_STMT       = 0x05;     // while(cond){...}
    public static final int DO_WHILE_STMT       = 0x05;     // do{...}while(cond);
    public static final int FOR_STMT            = 0x05;     // for(init,cond,next){...}
    public static final int FOR_ITERATOR_STMT   = 0x06;     // for(var v : iterator){...}
    public static final int FOR_RANGE_STMT      = 0x07;     // for(var v : iterator){...}
    public static final int FOR_LIST_STMT       = 0x08;     // for(var v : iterator){...}
    public static final int FOR_MAP_STMT        = 0x09;     // for((var k, var v)) : map){...} etc.
    public static final int FOR_ITERABLE_STMT   = 0x0A;     // for(var v : iterable){...}
    public static final int CONTINUE_LOOP_STMT  = 0x20;     // continue; or continue Label;
    public static final int BREAK_STMT          = 0x20;     // break; or break Label;
    public static final int EXPR_STMT           = 0x20;     // foo(); etc.
    public static final int RETURN_STMT         = 0x20;     // return expr;
    public static final int TRY_USING_STMT      = 0x20;     // using(res){...} or try(res){...}
    public static final int TRY_CATCH_STMT      = 0x20;     // try{...}catch(T e){...} etc.
    public static final int TRY_FINALLY_STMT    = 0x20;     // try{...}finally{...}

    public static final int REG_DECL_STMT       = 0x20;
    public static final int REG_STORE_STMT      = 0x20;
    public static final int ANY_STORE_STMT      = 0x20;
    public static final int ASSIGN_STMT         = 0x20;     // lvalue op rvalue, for

    public static final int SWITCH_EXPR         = 0x55;
    public static final int MULTI_COND          = 0x00;     // >1 ","-delimited conditions
    public static final int DECL_COND           = 0x00;     // condition that declares variable
    public static final int AND_EXPR            = 0x00;
    public static final int OR_EXPR             = 0x00;
    public static final int NOT_EXPR            = 0x00;
    public static final int XOR_EXPR            = 0x00;
    public static final int LOGICAL_OR_EXPR     = 0x00;
    public static final int LOGICAL_AND_EXPR    = 0x00;

}
