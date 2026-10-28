package org.xvm.runtime;

import java.util.ArrayList;
import java.util.List;

import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorList;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.CompilerException;
import org.xvm.compiler.Lexer;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.runtime.ClassComposition.FieldInfo;
import org.xvm.runtime.Frame.VarInfo;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xOrdered;
import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template.collections.xArray.ArrayHandle;

import org.xvm.runtime.template.numbers.xBit;
import org.xvm.runtime.template.numbers.xNumber;

import org.xvm.runtime.template.text.xChar;
import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

/**
 * An expression that the debugger evaluates itself, without running any of the program's code:
 * variables, {@code this}, {@link SessionDebugger#EXCEPTION} and the fields of {@code this}, their
 * fields ({@code .name}) and array elements ({@code [index]}); integer, decimal, character and
 * string literals, {@code True}, {@code False} and {@code Null}; and, with Ecstasy's precedence,
 * the unary {@code -}, {@code !} and {@code ~}, the operators {@code * / % + - << >> >>> & ^ |},
 * the comparisons {@code < <= > >= == !=}, chained as in Ecstasy, {@code && || ?:}, the
 * conditional {@code ? :} and parentheses. The text is read with the compiler's own lexer.
 *
 * <p>Each operator on a value of the program runs the runtime's own native operation for the
 * value's type, the Java code the interpreter runs for it, so overflow, division by zero and
 * rounding are exactly the program's. An operation that would run Ecstasy code instead, because
 * the type's operation is not native, is undone before any of that code runs, and refused, naming
 * the method it would have run. The
 * operators take numbers, {@code Boolean} for {@code & ^ |}, and a {@code String} on the left of
 * {@code +}, which joins any value whose text the runtime makes natively; the comparisons take
 * numbers, characters and strings, and {@code ==} and {@code !=} also enum values, such as
 * Booleans, and {@code Null}. The two sides of an operator have the same type, as Ecstasy's
 * compiler requires, except a shift's count, which is an {@code Int}. A literal takes the type of
 * the other side, as the compiler converts it; between two literals, an operator computes what the
 * compiler would.
 *
 * <p>The values are read, and the operations run, on the thread of the waiting fiber whose frame
 * holds them, through that frame's stack, which is left as it was.
 */
record PureExpression(String text, Node root) {

    /**
     * @return the expression the text writes, which may be surrounded by spaces
     *
     * @throws IllegalArgumentException if the text is not such an expression
     */
    static PureExpression parse(String text) {
        String trimmed = text.strip();
        var    source  = new Source(trimmed);
        var    errs    = new ErrorList(1);
        var    tokens  = new ArrayList<Token>();
        try {
            var lexer = new Lexer(source, errs);
            while (lexer.hasNext()) {
                tokens.add(lexer.next());
            }
        } catch (CompilerException e) {
            throw notEvaluated(trimmed);
        }
        if (errs.hasSeriousErrors() || tokens.isEmpty()) {
            throw notEvaluated(trimmed);
        }

        var  parser = new Parser(trimmed, source, tokens);
        Node root   = parser.ternary();
        if (!parser.atEnd()) {
            throw notEvaluated(trimmed);
        }
        return new PureExpression(trimmed, root);
    }

    /**
     * @param waitingFrame  the frame the waiting fiber waits at, on whose thread this runs
     * @param frame         the frame of that fiber whose variables the expression reads
     *
     * @return the expression's value in the frame, which may be a reference
     *
     * @throws IllegalArgumentException if it cannot be evaluated, saying why
     */
    ObjectHandle evaluate(Frame waitingFrame, Frame frame) {
        return value(waitingFrame, operand(waitingFrame, frame, root), root);
    }

    /**
     * @return true if the expression's value in the frame is the Boolean True
     *
     * @throws IllegalArgumentException if it cannot be evaluated, or is not a Boolean
     */
    boolean isTrue(Frame waitingFrame, Frame frame) {
        return isTrue(waitingFrame, frame, root);
    }

    // ----- evaluation ----------------------------------------------------------------------------

    private static Operand operand(Frame waitingFrame, Frame frame, Node node) {
        return switch (node) {
            case Name(String name) -> new Value(named(frame, name));
            case Numeral(_, String literal, Format format) -> new Literal(literal(waitingFrame, format, literal, node));
            case Text(_, String value) -> new Value(xString.makeHandle(value));
            case Char(_, char value) -> new Value(xChar.makeHandle(value));
            case Field(String path, Node target, String name) ->
                    new Value(field(waitingFrame, onTheWay(waitingFrame, frame, target, path), name, path));
            case Element(String path, Node target, Node index) ->
                    new Value(element(waitingFrame, onTheWay(waitingFrame, frame, target, path),
                            index(waitingFrame, frame, index), path));
            case Unary unary -> unary(waitingFrame, frame, unary);
            case Binary binary -> binary(waitingFrame, frame, binary);
            case Ternary(_, Node condition, Node then, Node otherwise) ->
                    operand(waitingFrame, frame, isTrue(waitingFrame, frame, condition) ? then : otherwise);
        };
    }

    private static boolean isTrue(Frame waitingFrame, Frame frame, Node node) {
        ObjectHandle value = SessionDebugger.shown(value(waitingFrame, operand(waitingFrame, frame, node), node));
        if (value instanceof BooleanHandle bool) {
            return bool.get();
        }
        String what = value == null ? "unassigned" : SessionDebugger.typeText(value);
        throw new IllegalArgumentException(node.text() + " is " + what + ", not a Boolean.");
    }

    private static Operand unary(Frame waitingFrame, Frame frame, Unary unary) {
        Id   op   = unary.op();
        Node node = unary.operand();
        if (op == Id.NOT) {
            return new Value(xBoolean.makeHandle(!isTrue(waitingFrame, frame, node)));
        }

        Operand operand = operand(waitingFrame, frame, node);
        if (operand instanceof Literal(LiteralConstant literal)) {
            return folded(waitingFrame, () -> literal.apply(op, null), unary);
        }
        ObjectHandle value = assigned(operand, node);
        if (!isNumber(value)) {
            throw notNative(unary, op, value);
        }
        OpSupport support = value.getOpSupport();
        return new Value(run(waitingFrame, unary, () -> op == Id.SUB
                ? support.invokeNeg(waitingFrame, value, Op.A_STACK)
                : support.invokeCompl(waitingFrame, value, Op.A_STACK)));
    }

    private static Operand binary(Frame waitingFrame, Frame frame, Binary binary) {
        Id   op    = binary.op();
        Node left  = binary.left();
        Node right = binary.right();
        switch (op) {
        case COND_AND:
            return new Value(xBoolean.makeHandle(isTrue(waitingFrame, frame, left) && isTrue(waitingFrame, frame, right)));

        case COND_OR:
            return new Value(xBoolean.makeHandle(isTrue(waitingFrame, frame, left) || isTrue(waitingFrame, frame, right)));

        case COND_ELSE: {
            Operand value = operand(waitingFrame, frame, left);
            return isNull(value) ? operand(waitingFrame, frame, right) : value;
        }
        }

        Operand one = operand(waitingFrame, frame, left);
        Operand two = operand(waitingFrame, frame, right);
        if (one instanceof Literal(LiteralConstant first) && two instanceof Literal(LiteralConstant second)) {
            return folded(waitingFrame, () -> first.apply(op, second), binary);
        }
        return new Value(switch (op) {
            case COMP_EQ, COMP_NEQ -> xBoolean.makeHandle(equal(waitingFrame, binary, one, two) == (op == Id.COMP_EQ));
            case COMP_LT, COMP_LTEQ, COMP_GT, COMP_GTEQ -> compare(waitingFrame, binary, one, two);
            case SHL, SHR, USHR -> shift(waitingFrame, binary, one, two);
            default -> arithmetic(waitingFrame, binary, one, two);
        });
    }

    /**
     * @return true if the two sides are equal
     */
    private static boolean equal(Frame waitingFrame, Binary binary, Operand left, Operand right) {
        if (isNull(left) || isNull(right)) {
            return left instanceof Value(ObjectHandle one) && right instanceof Value(ObjectHandle two)
                    && SessionDebugger.shown(one) == SessionDebugger.shown(two);
        }

        Sides sides = sides(waitingFrame, binary, left, right,
                value -> value instanceof EnumHandle || isNumber(value) || isText(value));
        ObjectHandle one = sides.one();
        ObjectHandle two = sides.two();
        if (one instanceof EnumHandle) {
            // each enum value is the only one of its kind
            return one == two;
        }
        TypeConstant type = one.getType();
        return ((BooleanHandle) run(waitingFrame, binary, () -> type.callEquals(waitingFrame, one, two, Op.A_STACK))).get();
    }

    private static BooleanHandle compare(Frame waitingFrame, Binary binary, Operand left, Operand right) {
        Sides        sides = sides(waitingFrame, binary, left, right, value -> isNumber(value) || isText(value));
        ObjectHandle one   = sides.one();
        ObjectHandle two   = sides.two();
        TypeConstant type  = one.getType();
        ObjectHandle order = run(waitingFrame, binary, () -> type.callCompare(waitingFrame, one, two, Op.A_STACK));
        return xBoolean.makeHandle(switch (binary.op()) {
            case COMP_LT   -> order == xOrdered.LESSER;
            case COMP_LTEQ -> order != xOrdered.GREATER;
            case COMP_GT   -> order == xOrdered.GREATER;
            default        -> order != xOrdered.LESSER;
        });
    }

    private static ObjectHandle shift(Frame waitingFrame, Binary binary, Operand left, Operand right) {
        // a shift's count is an Int, and so is a literal that is shifted
        TypeConstant typeInt = waitingFrame.poolContext().typeInt64();
        ObjectHandle value   = left instanceof Literal(LiteralConstant literal)
                ? converted(waitingFrame, literal, typeInt, binary)
                : assigned(left, binary.left());
        ObjectHandle count   = right instanceof Literal(LiteralConstant literal)
                ? converted(waitingFrame, literal, typeInt, binary)
                : assigned(right, binary.right());
        if (!isNumber(value)) {
            throw notNative(binary, binary.op(), value);
        }
        if (!count.getType().equals(typeInt)) {
            throw new IllegalArgumentException(binary.text() + ": " + binary.op().TEXT + " shifts by an Int, not by "
                    + SessionDebugger.typeText(count) + ".");
        }
        OpSupport support = value.getOpSupport();
        return run(waitingFrame, binary, () -> switch (binary.op()) {
            case SHL -> support.invokeShl(waitingFrame, value, count, Op.A_STACK);
            case SHR -> support.invokeShr(waitingFrame, value, count, Op.A_STACK);
            default  -> support.invokeShrAll(waitingFrame, value, count, Op.A_STACK);
        });
    }

    private static ObjectHandle arithmetic(Frame waitingFrame, Binary binary, Operand left, Operand right) {
        Id           op = binary.op();
        ObjectHandle one;
        ObjectHandle two;
        if (op == Id.ADD && left instanceof Value && assigned(left, binary.left()) instanceof StringHandle string) {
            // a string joins the text of any value whose text the runtime makes without running code
            one = string;
            two = right instanceof Literal(LiteralConstant literal)
                    ? handle(waitingFrame, literal, binary)
                    : assigned(right, binary.right());
        } else {
            boolean bits  = op == Id.BIT_AND || op == Id.BIT_OR || op == Id.BIT_XOR;
            Sides   sides = sides(waitingFrame, binary, left, right,
                    value -> isNumber(value) || bits && value instanceof BooleanHandle);
            one = sides.one();
            two = sides.two();
        }

        ObjectHandle target  = one;
        ObjectHandle arg     = two;
        OpSupport    support = target.getOpSupport();
        return run(waitingFrame, binary, () -> switch (op) {
            case ADD     -> support.invokeAdd(waitingFrame, target, arg, Op.A_STACK);
            case SUB     -> support.invokeSub(waitingFrame, target, arg, Op.A_STACK);
            case MUL     -> support.invokeMul(waitingFrame, target, arg, Op.A_STACK);
            case DIV     -> support.invokeDiv(waitingFrame, target, arg, Op.A_STACK);
            case MOD     -> support.invokeMod(waitingFrame, target, arg, Op.A_STACK);
            case BIT_AND -> support.invokeAnd(waitingFrame, target, arg, Op.A_STACK);
            case BIT_OR  -> support.invokeOr(waitingFrame, target, arg, Op.A_STACK);
            default      -> support.invokeXor(waitingFrame, target, arg, Op.A_STACK);
        });
    }

    /**
     * @return the two sides of a binary operator as values of one type that it takes, a literal
     *         given the type of the other side
     *
     * @throws IllegalArgumentException if the operator does not take the type, or the sides have
     *                                  different types
     */
    private static Sides sides(Frame waitingFrame, Binary binary, Operand left, Operand right, Predicate<ObjectHandle> takes) {
        // at most one side is a literal
        ObjectHandle known = left instanceof Value ? assigned(left, binary.left()) : assigned(right, binary.right());
        if (!takes.test(known)) {
            throw notNative(binary, binary.op(), known);
        }

        ObjectHandle one = left instanceof Literal(LiteralConstant literal)
                ? converted(waitingFrame, literal, known.getType(), binary)
                : assigned(left, binary.left());
        ObjectHandle two = right instanceof Literal(LiteralConstant literal)
                ? converted(waitingFrame, literal, known.getType(), binary)
                : assigned(right, binary.right());
        if (!sameType(one, two)) {
            throw new IllegalArgumentException(binary.text() + ": " + binary.op().TEXT + " takes two values of the same type, not "
                    + SessionDebugger.typeText(one) + " and " + SessionDebugger.typeText(two) + ".");
        }
        return new Sides(one, two);
    }

    /**
     * @return true if the values have the same type: of two enum values, the same enumeration
     */
    private static boolean sameType(ObjectHandle one, ObjectHandle two) {
        return one instanceof EnumHandle && two instanceof EnumHandle
                ? one.getTemplate() == two.getTemplate()
                : one.getType().equals(two.getType());
    }

    /**
     * @return the literal as a value of the specified type, as the compiler converts it
     */
    private static ObjectHandle converted(Frame waitingFrame, LiteralConstant literal, TypeConstant type, Node node) {
        String   name = type.getValueString();
        Constant converted;
        try {
            converted = literal.convertTo(type);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(node.text() + ": " + literal.getValue() + " is out of range for " + name + ".");
        }
        if (converted == null || converted == literal) {
            throw new IllegalArgumentException(node.text() + ": " + literal.getValue() + " is not a " + name + ".");
        }
        return handle(waitingFrame, converted, node);
    }

    /**
     * @return the result of an operator on literals, as the compiler computes it
     */
    private static Operand folded(Frame waitingFrame, Supplier<Constant> operation, Node node) {
        Constant result;
        try {
            result = operation.get();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(node.text() + ": the debugger does not compute this on literals.");
        }

        ConstantPool pool = waitingFrame.poolContext();
        if (result instanceof LiteralConstant literal && isUntyped(literal)) {
            return new Literal(literal);
        }
        return new Value(result == pool.valTrue() ? xBoolean.TRUE
                       : result == pool.valFalse() ? xBoolean.FALSE
                       : handle(waitingFrame, result, node));
    }

    /**
     * @return the value an operation leaves on the waiting frame's stack
     *
     * @throws IllegalArgumentException if the operation throws, or would run Ecstasy code, which
     *                                  is then never run
     */
    private static ObjectHandle run(Frame waitingFrame, Node node, IntSupplier operation) {
        ExceptionHandle pending = waitingFrame.m_hException;
        Frame           next    = waitingFrame.m_frameNext;
        int             result  = operation.getAsInt();
        if (result == Op.R_NEXT) {
            return waitingFrame.popStack();
        }
        if (result == Op.R_EXCEPTION) {
            ExceptionHandle thrown = waitingFrame.m_hException;
            waitingFrame.m_hException = pending;
            throw new IllegalArgumentException(node.text() + " throws " + SessionDebugger.describe(thrown));
        }
        // the call it set up is dropped before it runs; its method is the code it would run
        Frame           call     = waitingFrame.m_frameNext;
        MethodStructure function = call == null || call == next ? null : call.getFunction();
        waitingFrame.m_frameNext = next;
        String code = function == null ? "the program's code" : SessionDebugger.functionName(function) + "()";
        throw new IllegalArgumentException(node.text() + " would run " + code + ", which the debugger does not run here.");
    }

    private static IllegalArgumentException notNative(Node node, Id op, ObjectHandle value) {
        return new IllegalArgumentException(node.text() + ": " + op.TEXT + " is not evaluated on "
                + SessionDebugger.typeText(value) + " without running the program's code.");
    }

    // ----- values --------------------------------------------------------------------------------

    /**
     * @return the operand's value: a literal as the compiler makes it, without a type given
     */
    private static ObjectHandle value(Frame waitingFrame, Operand operand, Node node) {
        return switch (operand) {
            case Value(ObjectHandle value) -> value;
            case Literal(LiteralConstant literal) -> handle(waitingFrame, literal, node);
        };
    }

    /**
     * @return the operand's value, assigned
     *
     * @throws IllegalArgumentException if it is unassigned
     */
    private static ObjectHandle assigned(Operand operand, Node node) {
        ObjectHandle value = operand instanceof Value(ObjectHandle held) ? SessionDebugger.shown(held) : null;
        if (value == null) {
            throw new IllegalArgumentException(node.text() + " is unassigned.");
        }
        return value;
    }

    private static ObjectHandle handle(Frame waitingFrame, Constant constant, Node node) {
        ObjectHandle value = waitingFrame.getConstHandle(constant);
        if (value instanceof DeferredCallHandle) {
            throw new IllegalArgumentException(node.text() + ": its value is made by running the program's code.");
        }
        return value;
    }

    private static LiteralConstant literal(Frame waitingFrame, Format format, String literal, Node node) {
        if (waitingFrame.poolContext().ensureLiteralConstant(format, literal) instanceof LiteralConstant constant) {
            return constant;
        }
        throw new IllegalArgumentException(node.text() + " is not a literal the debugger reads.");
    }

    private static boolean isNull(Operand operand) {
        return operand instanceof Value(ObjectHandle value) && SessionDebugger.shown(value) == xNullable.NULL;
    }

    private static boolean isUntyped(LiteralConstant literal) {
        return literal.getFormat() == Format.IntLiteral || literal.getFormat() == Format.FPLiteral;
    }

    private static boolean isNumber(ObjectHandle value) {
        return value.getTemplate() instanceof xNumber || value.getTemplate() instanceof xBit;
    }

    private static boolean isText(ObjectHandle value) {
        return value instanceof StringHandle || value.getTemplate() instanceof xChar;
    }

    // ----- paths ---------------------------------------------------------------------------------

    /**
     * @return the frame's value of the specified name: {@code True}, {@code False}, {@code Null},
     *         {@code this}, the exception it throws, its innermost variable of that name, or a
     *         field of {@code this}
     */
    private static ObjectHandle named(Frame frame, String name) {
        switch (name) {
        case "True":
            return xBoolean.TRUE;

        case "False":
            return xBoolean.FALSE;

        case "Null":
            return xNullable.NULL;

        case "this":
            if (frame.f_hThis == null) {
                throw new IllegalArgumentException("This frame has no \"this\".");
            }
            return frame.f_hThis;

        case SessionDebugger.EXCEPTION:
            if (frame.m_hException == null) {
                throw new IllegalArgumentException("This frame throws no exception.");
            }
            return frame.m_hException;
        }

        for (int i = frame.getCurrentVarCount() - 1; i >= 0; i--) {
            VarInfo info = frame.getVarInfo(i);
            if (info != null && name.equals(info.getName())) {
                return frame.f_ahVar[i];
            }
        }
        if (frame.f_hThis instanceof GenericHandle self) {
            FieldInfo field = DebugConsole.getFieldLayout(self.getComposition()).get(name);
            if (field != null) {
                return self.getField(field.getIndex());
            }
        }
        throw new IllegalArgumentException("There is no variable \"" + name + "\" in this frame.");
    }

    /**
     * @return the assigned value whose field or element a path reads
     */
    private static ObjectHandle onTheWay(Frame waitingFrame, Frame frame, Node target, String path) {
        ObjectHandle value = SessionDebugger.shown(value(waitingFrame, operand(waitingFrame, frame, target), target));
        if (value == null) {
            throw new IllegalArgumentException(path + ": a value on the way is unassigned.");
        }
        return value;
    }

    /**
     * @return the object's field of the specified name
     */
    private static ObjectHandle field(Frame waitingFrame, ObjectHandle value, String name, String path) {
        if (!(value instanceof GenericHandle object) || value instanceof EnumHandle || value.getComposition() == null) {
            throw new IllegalArgumentException(path + ": " + SessionDebugger.typeText(value) + " has no fields to read.");
        }
        if (value instanceof ServiceHandle service && service.f_context != waitingFrame.f_context) {
            throw new IllegalArgumentException(path + ": another service's state is read only while it is stopped.");
        }
        FieldInfo field = DebugConsole.getFieldLayout(object.getComposition()).get(name);
        if (field == null) {
            throw new IllegalArgumentException(path + ": " + SessionDebugger.typeText(value) + " has no field \"" + name + "\".");
        }
        return object.getField(field.getIndex());
    }

    /**
     * @return the index of an element, an Int
     */
    private static long index(Frame waitingFrame, Frame frame, Node index) {
        TypeConstant typeInt = waitingFrame.poolContext().typeInt64();
        Operand      operand = operand(waitingFrame, frame, index);
        ObjectHandle value   = operand instanceof Literal(LiteralConstant literal)
                ? converted(waitingFrame, literal, typeInt, index)
                : assigned(operand, index);
        if (!(value instanceof JavaLong number) || !value.getType().equals(typeInt)) {
            throw new IllegalArgumentException(index.text() + " is " + SessionDebugger.typeText(value) + ", not an Int index.");
        }
        return number.getValue();
    }

    /**
     * @return the array's element at the specified index
     */
    private static ObjectHandle element(Frame waitingFrame, ObjectHandle value, long index, String path) {
        if (!(value instanceof ArrayHandle array)) {
            throw new IllegalArgumentException(path + ": " + SessionDebugger.typeText(value) + " is not an array.");
        }
        long         size    = array.m_hDelegate.m_cSize;
        ObjectHandle element = index >= 0 && index < size ? SessionDebugger.element(waitingFrame, array, index) : null;
        if (element == null) {
            throw new IllegalArgumentException(path + ": index " + index + " is out of bounds for size " + size + ".");
        }
        return element;
    }

    private static IllegalArgumentException notEvaluated(String text) {
        return new IllegalArgumentException("\"" + text + "\" is not evaluated: only variables, their fields and array"
                + " elements, literals and operators are, such as point.x + 1 or ints[i] > 0.");
    }

    // ----- parsing -------------------------------------------------------------------------------

    /**
     * Reads an expression's tokens, one level of precedence at a time, as Ecstasy's parser does.
     */
    private static final class Parser {
        private final String      text;
        private final Source      source;
        private final List<Token> tokens;
        private int               at;

        Parser(String text, Source source, List<Token> tokens) {
            this.text   = text;
            this.source = source;
            this.tokens = tokens;
        }

        boolean atEnd() {
            return at == tokens.size();
        }

        Node ternary() {
            int  first     = at;
            Node condition = or();
            if (!accept(Id.COND)) {
                return condition;
            }
            Node then = ternary();
            expect(Id.COLON);
            Node otherwise = ternary();
            return new Ternary(textFrom(first), condition, then, otherwise);
        }

        private Node or() {
            int  first = at;
            Node left  = and();
            while (accept(Id.COND_OR)) {
                Node right = and();
                left = new Binary(textFrom(first), Id.COND_OR, left, right);
            }
            return left;
        }

        private Node and() {
            int  first = at;
            Node left  = equality();
            while (accept(Id.COND_AND)) {
                Node right = equality();
                left = new Binary(textFrom(first), Id.COND_AND, left, right);
            }
            return left;
        }

        private Node equality() {
            return chain(this::relational, Id.COMP_EQ, Id.COMP_NEQ);
        }

        private Node relational() {
            return chain(this::bitwise, Id.COMP_LT, Id.COMP_LTEQ, Id.COMP_GT, Id.COMP_GTEQ);
        }

        /**
         * @return a comparison, or a chain of them as Ecstasy reads {@code a < b <= c}: each
         *         adjacent pair compared, all in one direction, or all with the same equality
         *         operator
         */
        private Node chain(Supplier<Node> next, Id... ops) {
            int  first  = at;
            int  start  = at;
            Node left   = next.get();
            Node result = left;
            Id   prior  = null;
            for (Id op; (op = acceptAny(ops)) != null; prior = op) {
                if (prior != null && direction(op) != direction(prior)) {
                    throw notEvaluated(text);
                }
                int  rightStart = at;
                Node right      = next.get();
                Node comparison = new Binary(textFrom(start), op, left, right);
                result = prior == null ? comparison : new Binary(textFrom(first), Id.COND_AND, result, comparison);
                left   = right;
                start  = rightStart;
            }
            return result;
        }

        private static int direction(Id op) {
            return switch (op) {
                case COMP_LT, COMP_LTEQ -> -1;
                case COMP_GT, COMP_GTEQ -> -2;
                default                 -> op.ordinal();
            };
        }

        private Node bitwise() {
            int  first = at;
            Node left  = additive();
            for (Id op; (op = acceptAny(Id.SHL, Id.SHR, Id.USHR, Id.BIT_AND, Id.BIT_XOR, Id.BIT_OR)) != null; ) {
                Node right = additive();
                left = new Binary(textFrom(first), op, left, right);
            }
            return left;
        }

        private Node additive() {
            int  first = at;
            Node left  = multiplicative();
            for (Id op; (op = acceptAny(Id.ADD, Id.SUB)) != null; ) {
                Node right = multiplicative();
                left = new Binary(textFrom(first), op, left, right);
            }
            return left;
        }

        private Node multiplicative() {
            int  first = at;
            Node left  = elvis();
            for (Id op; (op = acceptAny(Id.MUL, Id.DIV, Id.MOD)) != null; ) {
                Node right = elvis();
                left = new Binary(textFrom(first), op, left, right);
            }
            return left;
        }

        private Node elvis() {
            int  first = at;
            Node left  = prefix();
            if (!accept(Id.COND_ELSE)) {
                return left;
            }
            Node right = elvis();
            return new Binary(textFrom(first), Id.COND_ELSE, left, right);
        }

        private Node prefix() {
            int first = at;
            Id  op    = acceptAny(Id.SUB, Id.NOT, Id.BIT_NOT);
            if (op == null) {
                return postfix();
            }
            Node operand = prefix();
            if (op == Id.SUB && operand instanceof Numeral(_, String literal, Format format)) {
                // a negative literal, as the compiler reads one
                return new Numeral(textFrom(first), "-" + literal, format);
            }
            return new Unary(textFrom(first), op, operand);
        }

        private Node postfix() {
            int  first = at;
            Node node  = primary();
            while (true) {
                if (accept(Id.DOT)) {
                    String name = identifier();
                    node = new Field(textFrom(first), node, name);
                } else if (accept(Id.L_SQUARE)) {
                    Node index = ternary();
                    expect(Id.R_SQUARE);
                    node = new Element(textFrom(first), node, index);
                } else {
                    return node;
                }
            }
        }

        private Node primary() {
            if (atEnd()) {
                throw notEvaluated(text);
            }
            Token token = tokens.get(at++);
            switch (token.getId()) {
            case LIT_INT:
                return new Numeral(token.getString(source), token.getString(source), Format.IntLiteral);

            case LIT_DEC:
            case LIT_FLOAT:
                return new Numeral(token.getString(source), token.getString(source), Format.FPLiteral);

            case LIT_STRING:
                return new Text(token.getString(source), (String) token.getValue());

            case LIT_CHAR:
                return new Char(token.getString(source), (Character) token.getValue());

            case IDENTIFIER:
                return new Name((String) token.getValue());

            case THIS:
                return new Name("this");

            case L_PAREN: {
                Node inner = ternary();
                expect(Id.R_PAREN);
                return inner;
            }

            case COMP_LT:
                // the exception the frame throws: <exception>
                if ("exception".equals(identifier()) && accept(Id.COMP_GT)) {
                    return new Name(SessionDebugger.EXCEPTION);
                }
                throw notEvaluated(text);

            default:
                throw notEvaluated(text);
            }
        }

        private String identifier() {
            if (atEnd() || tokens.get(at).getId() != Id.IDENTIFIER) {
                throw notEvaluated(text);
            }
            return (String) tokens.get(at++).getValue();
        }

        private boolean accept(Id id) {
            if (!atEnd() && tokens.get(at).getId() == id) {
                at++;
                return true;
            }
            return false;
        }

        private Id acceptAny(Id... ids) {
            for (Id id : ids) {
                if (accept(id)) {
                    return id;
                }
            }
            return null;
        }

        private void expect(Id id) {
            if (!accept(id)) {
                throw notEvaluated(text);
            }
        }

        /**
         * @return the text from the token at the specified index to the last one read
         */
        private String textFrom(int first) {
            return source.toString(tokens.get(first).getStartPosition(), tokens.get(at - 1).getEndPosition());
        }
    }

    // ----- nodes ---------------------------------------------------------------------------------

    /**
     * A part of an expression, and its text.
     */
    private sealed interface Node {
        String text();
    }

    /**
     * A variable, {@code this}, the exception, a field of {@code this}, {@code True},
     * {@code False} or {@code Null}.
     */
    private record Name(String text) implements Node {
    }

    /**
     * An integer or decimal literal, as written, and whether it is an IntLiteral or an FPLiteral.
     */
    private record Numeral(String text, String literal, Format format) implements Node {
    }

    private record Text(String text, String value) implements Node {
    }

    private record Char(String text, char value) implements Node {
    }

    private record Field(String text, Node target, String name) implements Node {
    }

    private record Element(String text, Node target, Node index) implements Node {
    }

    private record Unary(String text, Id op, Node operand) implements Node {
    }

    private record Binary(String text, Id op, Node left, Node right) implements Node {
    }

    private record Ternary(String text, Node condition, Node then, Node otherwise) implements Node {
    }

    // ----- operands ------------------------------------------------------------------------------

    /**
     * What a node evaluates to: a value, or a literal not yet given a type.
     */
    private sealed interface Operand {
    }

    private record Value(ObjectHandle value) implements Operand {
    }

    private record Literal(LiteralConstant literal) implements Operand {
    }

    /**
     * The two sides of a binary operator, as values of one type.
     */
    private record Sides(ObjectHandle one, ObjectHandle two) {
    }
}
