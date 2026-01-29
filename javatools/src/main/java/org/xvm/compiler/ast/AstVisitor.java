package org.xvm.compiler.ast;


import org.jetbrains.annotations.NotNull;


/**
 * Visitor interface for AST nodes. Each concrete AST node type has a corresponding visit method.
 * <p/>
 * This visitor pattern replaces the reflection-based CHILD_FIELDS infrastructure. Instead of
 * using reflection to iterate over child nodes, each concrete class:
 * <ol>
 *   <li>Implements {@code accept(AstVisitor)} which calls {@code visitor.visit(this)}</li>
 *   <li>Provides explicit typed getters for its children</li>
 * </ol>
 * <p/>
 * Visitors that need to traverse children do so explicitly using the typed getters, giving
 * full control over traversal order and which children to visit.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * class ParentSetupVisitor implements AstVisitor<Void> {
 *     @Override
 *     public Void visit(ForStatement stmt) {
 *         // Explicitly visit each child category
 *         stmt.getInit().forEach(s -> {
 *             s.setParent(stmt);
 *             s.accept(this);
 *         });
 *         stmt.getConds().forEach(c -> {
 *             c.setParent(stmt);
 *             c.accept(this);
 *         });
 *         // ... etc.
 *         return null;
 *     }
 * }
 * }</pre>
 *
 * @param <R> the return type of the visit methods
 */
public interface AstVisitor<R> {
    // ---- Statements -------------------------------------------------------------------------

    /**
     * Visit an AssertStatement.
     */
    default R visit(@NotNull AssertStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit an AssignmentStatement.
     */
    default R visit(@NotNull AssignmentStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit a BreakStatement.
     */
    default R visit(@NotNull BreakStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit a CaseStatement.
     */
    default R visit(@NotNull CaseStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit a CatchStatement.
     */
    default R visit(@NotNull CatchStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit a ContinueStatement.
     */
    default R visit(@NotNull ContinueStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit an ExpressionStatement.
     */
    default R visit(@NotNull ExpressionStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit a ForEachStatement.
     */
    default R visit(@NotNull ForEachStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit a ForStatement.
     */
    default R visit(@NotNull ForStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit a GotoStatement.
     */
    default R visit(@NotNull GotoStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit an IfStatement.
     */
    default R visit(@NotNull IfStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit an ImportStatement.
     */
    default R visit(@NotNull ImportStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit a LabeledStatement.
     */
    default R visit(@NotNull LabeledStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit a MethodDeclarationStatement.
     */
    default R visit(@NotNull MethodDeclarationStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit a MultipleLValueStatement.
     */
    default R visit(@NotNull MultipleLValueStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit a PropertyDeclarationStatement.
     */
    default R visit(@NotNull PropertyDeclarationStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit a ReturnStatement.
     */
    default R visit(@NotNull ReturnStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit a StatementBlock.
     */
    default R visit(@NotNull StatementBlock stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit a SwitchStatement.
     */
    default R visit(@NotNull SwitchStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit a TryStatement.
     */
    default R visit(@NotNull TryStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit a TypeCompositionStatement.
     */
    default R visit(@NotNull TypeCompositionStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit a TypedefStatement.
     */
    default R visit(@NotNull TypedefStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit a VariableDeclarationStatement.
     */
    default R visit(@NotNull VariableDeclarationStatement stmt) {
        return visitStatement(stmt);
    }

    /**
     * Visit a WhileStatement.
     */
    default R visit(@NotNull WhileStatement stmt) {
        return visitStatement(stmt);
    }

    // ---- Expressions ------------------------------------------------------------------------

    /**
     * Visit an AnnotationExpression.
     */
    default R visit(@NotNull AnnotationExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit an ArrayAccessExpression.
     */
    default R visit(@NotNull ArrayAccessExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit an AsExpression.
     */
    default R visit(@NotNull AsExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a CmpChainExpression.
     */
    default R visit(@NotNull CmpChainExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a CmpExpression.
     */
    default R visit(@NotNull CmpExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a CondOpExpression.
     */
    default R visit(@NotNull CondOpExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a ConvertExpression.
     */
    default R visit(@NotNull ConvertExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit an ElseExpression.
     */
    default R visit(@NotNull ElseExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit an ElvisExpression.
     */
    default R visit(@NotNull ElvisExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a FileExpression.
     */
    default R visit(@NotNull FileExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit an IgnoredNameExpression.
     */
    default R visit(@NotNull IgnoredNameExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit an InvocationExpression.
     */
    default R visit(@NotNull InvocationExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit an IsExpression.
     */
    default R visit(@NotNull IsExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a LabeledExpression.
     */
    default R visit(@NotNull LabeledExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a LambdaExpression.
     */
    default R visit(@NotNull LambdaExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a ListExpression.
     */
    default R visit(@NotNull ListExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a LiteralExpression.
     */
    default R visit(@NotNull LiteralExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a MapExpression.
     */
    default R visit(@NotNull MapExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a NameExpression.
     */
    default R visit(@NotNull NameExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a NewExpression.
     */
    default R visit(@NotNull NewExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a NonBindingExpression.
     */
    default R visit(@NotNull NonBindingExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a NotNullExpression.
     */
    default R visit(@NotNull NotNullExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a PackExpression.
     */
    default R visit(@NotNull PackExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a ParenthesizedExpression.
     */
    default R visit(@NotNull ParenthesizedExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a RelOpExpression.
     */
    default R visit(@NotNull RelOpExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a SequentialAssignExpression.
     */
    default R visit(@NotNull SequentialAssignExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a StatementExpression.
     */
    default R visit(@NotNull StatementExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a SwitchExpression.
     */
    default R visit(@NotNull SwitchExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a TemplateExpression.
     */
    default R visit(@NotNull TemplateExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a TernaryExpression.
     */
    default R visit(@NotNull TernaryExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a ThrowExpression.
     */
    default R visit(@NotNull ThrowExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a ToIntExpression.
     */
    default R visit(@NotNull ToIntExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a TraceExpression.
     */
    default R visit(@NotNull TraceExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a TupleExpression.
     */
    default R visit(@NotNull TupleExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a UnaryComplementExpression.
     */
    default R visit(@NotNull UnaryComplementExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a UnaryMinusExpression.
     */
    default R visit(@NotNull UnaryMinusExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit a UnaryPlusExpression.
     */
    default R visit(@NotNull UnaryPlusExpression expr) {
        return visitExpression(expr);
    }

    /**
     * Visit an UnpackExpression.
     */
    default R visit(@NotNull UnpackExpression expr) {
        return visitExpression(expr);
    }

    // ---- Type Expressions -------------------------------------------------------------------

    /**
     * Visit an AnnotatedTypeExpression.
     */
    default R visit(@NotNull AnnotatedTypeExpression type) {
        return visitTypeExpression(type);
    }

    /**
     * Visit an ArrayTypeExpression.
     */
    default R visit(@NotNull ArrayTypeExpression type) {
        return visitTypeExpression(type);
    }

    /**
     * Visit a BadTypeExpression.
     */
    default R visit(@NotNull BadTypeExpression type) {
        return visitTypeExpression(type);
    }

    /**
     * Visit a BiTypeExpression.
     */
    default R visit(@NotNull BiTypeExpression type) {
        return visitTypeExpression(type);
    }

    /**
     * Visit a DecoratedTypeExpression.
     */
    default R visit(@NotNull DecoratedTypeExpression type) {
        return visitTypeExpression(type);
    }

    /**
     * Visit a FunctionTypeExpression.
     */
    default R visit(@NotNull FunctionTypeExpression type) {
        return visitTypeExpression(type);
    }

    /**
     * Visit a KeywordTypeExpression.
     */
    default R visit(@NotNull KeywordTypeExpression type) {
        return visitTypeExpression(type);
    }

    /**
     * Visit a ModuleTypeExpression.
     */
    default R visit(@NotNull ModuleTypeExpression type) {
        return visitTypeExpression(type);
    }

    /**
     * Visit a NamedTypeExpression.
     */
    default R visit(@NotNull NamedTypeExpression type) {
        return visitTypeExpression(type);
    }

    /**
     * Visit a NullableTypeExpression.
     */
    default R visit(@NotNull NullableTypeExpression type) {
        return visitTypeExpression(type);
    }

    /**
     * Visit a TupleTypeExpression.
     */
    default R visit(@NotNull TupleTypeExpression type) {
        return visitTypeExpression(type);
    }

    /**
     * Visit a VariableTypeExpression.
     */
    default R visit(@NotNull VariableTypeExpression type) {
        return visitTypeExpression(type);
    }

    // ---- Other Nodes ------------------------------------------------------------------------

    /**
     * Visit a CompositionNode.Extends.
     */
    default R visit(@NotNull CompositionNode.Extends node) {
        return visitCompositionNode(node);
    }

    /**
     * Visit a CompositionNode.Annotates.
     */
    default R visit(@NotNull CompositionNode.Annotates node) {
        return visitCompositionNode(node);
    }

    /**
     * Visit a CompositionNode.Incorporates.
     */
    default R visit(@NotNull CompositionNode.Incorporates node) {
        return visitCompositionNode(node);
    }

    /**
     * Visit a CompositionNode.Implements.
     */
    default R visit(@NotNull CompositionNode.Implements node) {
        return visitCompositionNode(node);
    }

    /**
     * Visit a CompositionNode.Delegates.
     */
    default R visit(@NotNull CompositionNode.Delegates node) {
        return visitCompositionNode(node);
    }

    /**
     * Visit a CompositionNode.Into.
     */
    default R visit(@NotNull CompositionNode.Into node) {
        return visitCompositionNode(node);
    }

    /**
     * Visit a CompositionNode.Import.
     */
    default R visit(@NotNull CompositionNode.Import node) {
        return visitCompositionNode(node);
    }

    /**
     * Visit a CompositionNode.Default.
     */
    default R visit(@NotNull CompositionNode.Default node) {
        return visitCompositionNode(node);
    }

    /**
     * Visit a Parameter.
     */
    default R visit(@NotNull Parameter param) {
        return visitNode(param);
    }

    /**
     * Visit a VersionOverride.
     */
    default R visit(@NotNull VersionOverride node) {
        return visitNode(node);
    }

    // ---- Category Methods -------------------------------------------------------------------

    /**
     * Default handler for all Statement nodes. Override specific visit methods to handle
     * specific statement types differently.
     *
     * @param stmt the statement being visited
     * @return the result of visiting
     */
    default R visitStatement(@NotNull Statement stmt) {
        return visitNode(stmt);
    }

    /**
     * Default handler for all Expression nodes. Override specific visit methods to handle
     * specific expression types differently.
     *
     * @param expr the expression being visited
     * @return the result of visiting
     */
    default R visitExpression(@NotNull Expression expr) {
        return visitNode(expr);
    }

    /**
     * Default handler for all TypeExpression nodes. Override specific visit methods to handle
     * specific type expression types differently.
     *
     * @param type the type expression being visited
     * @return the result of visiting
     */
    default R visitTypeExpression(@NotNull TypeExpression type) {
        return visitNode(type);
    }

    /**
     * Default handler for all CompositionNode types. Override specific visit methods to handle
     * specific composition types differently.
     *
     * @param node the composition node being visited
     * @return the result of visiting
     */
    default R visitCompositionNode(@NotNull CompositionNode node) {
        return visitNode(node);
    }

    /**
     * Default handler for all AST nodes. This is the ultimate fallback that is called
     * if no more specific handler is provided.
     *
     * @param node the node being visited
     * @return the result of visiting (default returns null)
     */
    default R visitNode(@NotNull AstNode node) {
        return null;
    }
}
