package org.xvm.compiler2.bridge;

import java.util.List;

import org.xvm.compiler.Token;
import org.xvm.compiler.ast.BiExpression;
import org.xvm.compiler.ast.DelegatingExpression;
import org.xvm.compiler.ast.Expression;
import org.xvm.compiler.ast.LiteralExpression;
import org.xvm.compiler.ast.NameExpression;
import org.xvm.compiler.ast.Statement;
import org.xvm.compiler.ast.StatementBlock;

import org.xvm.compiler2.syntax.SyntaxKind;
import org.xvm.compiler2.syntax.green.GreenBinaryExpr;
import org.xvm.compiler2.syntax.green.GreenBlockStmt;
import org.xvm.compiler2.syntax.green.GreenExpression;
import org.xvm.compiler2.syntax.green.GreenLiteralExpr;
import org.xvm.compiler2.syntax.green.GreenNameExpr;
import org.xvm.compiler2.syntax.green.GreenParenExpr;
import org.xvm.compiler2.syntax.green.GreenStatement;
import org.xvm.compiler2.syntax.green.GreenToken;

/**
 * Converts XVM AST nodes to immutable green tree nodes.
 * <p>
 * This is a bridge for incremental adoption. Long-term, the parser should
 * emit green nodes directly (see GreenParser - to be implemented).
 * <p>
 * NOTE: This converter only handles AST classes that expose public accessors.
 * Classes with protected-only fields will need either:
 * <ul>
 *   <li>Public getters added to the AST classes, or</li>
 *   <li>Direct green node emission from the parser</li>
 * </ul>
 */
public class AstToGreenConverter {

    /**
     * Convert an AST expression to a green expression.
     *
     * @param expr the AST expression
     * @return the green expression
     */
    public GreenExpression convertExpression(Expression expr) {
        if (expr == null) {
            return null;
        }

        // Dispatch based on expression type - only handle classes with public accessors
        if (expr instanceof LiteralExpression lit) {
            return convertLiteral(lit);
        }
        if (expr instanceof NameExpression name) {
            return convertName(name);
        }
        if (expr instanceof BiExpression bi) {
            return convertBinary(bi);
        }
        if (expr instanceof DelegatingExpression del) {
            return convertDelegating(del);
        }

        // Unsupported expression type - return placeholder
        return createPlaceholder(expr.getClass().getSimpleName());
    }

    /**
     * Convert an AST statement to a green statement.
     *
     * @param stmt the AST statement
     * @return the green statement
     */
    public GreenStatement convertStatement(Statement stmt) {
        if (stmt == null) {
            return null;
        }

        if (stmt instanceof StatementBlock block) {
            return convertBlock(block);
        }

        // Unsupported statement type - return empty block as placeholder
        return GreenBlockStmt.create();
    }

    // -------------------------------------------------------------------------
    // Expression converters (only classes with public accessors)
    // -------------------------------------------------------------------------

    private GreenLiteralExpr convertLiteral(LiteralExpression lit) {
        Token token = lit.getLiteral();
        GreenToken greenToken = TokenConverter.convert(token);
        return GreenLiteralExpr.create(greenToken);
    }

    private GreenExpression convertName(NameExpression name) {
        // NameExpression has public: getNameToken(), getName(), getLeftExpression()
        Token nameToken = name.getNameToken();
        String nameStr = name.getName();

        GreenToken token = nameToken != null
                ? TokenConverter.convert(nameToken)
                : GreenToken.identifier(nameStr);

        // Check for qualified name (a.b.c)
        Expression left = name.getLeftExpression();
        if (left != null) {
            GreenExpression leftGreen = convertExpression(left);
            GreenToken dot = GreenToken.create(SyntaxKind.DOT, ".");
            return GreenBinaryExpr.create(leftGreen, dot, GreenNameExpr.create(token));
        }

        return GreenNameExpr.create(token);
    }

    private GreenBinaryExpr convertBinary(BiExpression bi) {
        // BiExpression has public: getExpression1(), getOperator(), getExpression2()
        GreenExpression left = convertExpression(bi.getExpression1());
        GreenToken op = TokenConverter.convert(bi.getOperator());
        GreenExpression right = convertExpression(bi.getExpression2());

        return GreenBinaryExpr.create(left, op, right);
    }

    private GreenExpression convertDelegating(DelegatingExpression del) {
        // DelegatingExpression has public: getUnderlyingExpression()
        // This includes ParenthesizedExpression
        GreenExpression inner = convertExpression(del.getUnderlyingExpression());

        // Check if it's specifically a parenthesized expression
        if (del.getClass().getSimpleName().equals("ParenthesizedExpression")) {
            return GreenParenExpr.create(inner);
        }

        // Other delegating expressions just pass through
        return inner;
    }

    // -------------------------------------------------------------------------
    // Statement converters
    // -------------------------------------------------------------------------

    private GreenBlockStmt convertBlock(StatementBlock block) {
        // StatementBlock has public: getStatements()
        List<Statement> stmts = block.getStatements();
        GreenStatement[] greenStmts = new GreenStatement[stmts.size()];
        for (int i = 0; i < stmts.size(); i++) {
            greenStmts[i] = convertStatement(stmts.get(i));
        }
        return GreenBlockStmt.create(greenStmts);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private GreenExpression createPlaceholder(String typeName) {
        // Create a placeholder for unsupported expression types
        // This allows partial conversion while development continues
        return GreenNameExpr.create("TODO_" + typeName);
    }
}
