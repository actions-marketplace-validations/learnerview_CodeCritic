package com.codecritic.change;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.WhileStmt;

/**
 * Computes a McCabe-style cyclomatic complexity value for a Java method or
 * constructor using the parsed JavaParser AST.
 * <p>
 * Decision points include conditionals, loops, catch clauses, switch entries,
 * ternary expressions, and short-circuit boolean operators. Constructs inside
 * nested callables are excluded from the enclosing callable's score.
 */
public final class CyclomaticComplexity {

    private CyclomaticComplexity() {
    }

    /**
     * Calculates the cyclomatic complexity of the given callable.
     *
     * @param callable the method or constructor to analyze
     * @return one plus the number of decision points, or {@code 0} when the
     *         callable has no analyzable body
     */
    public static int of(CallableDeclaration<?> callable) {
        Node body = bodyOf(callable);
        if (body == null) {
            return 0;
        }

        int decisions = count(body, IfStmt.class, callable)
                + count(body, ForStmt.class, callable)
                + count(body, ForEachStmt.class, callable)
                + count(body, WhileStmt.class, callable)
                + count(body, DoStmt.class, callable)
                + count(body, CatchClause.class, callable)
                + count(body, ConditionalExpr.class, callable)
                + count(body, SwitchEntry.class, callable)
                + (int) body.findAll(BinaryExpr.class).stream()
                        .filter(node -> CallableScope.belongsTo(node, callable))
                        .filter(node -> node.getOperator() == BinaryExpr.Operator.AND
                                || node.getOperator() == BinaryExpr.Operator.OR)
                        .count();

        return 1 + decisions;
    }

    private static Node bodyOf(CallableDeclaration<?> callable) {
        if (callable instanceof MethodDeclaration method) {
            return method.getBody().orElse(null);
        }
        if (callable instanceof ConstructorDeclaration constructor) {
            return constructor.getBody();
        }
        return null;
    }

    private static <N extends Node> int count(Node body, Class<N> type, CallableDeclaration<?> owner) {
        return (int) body.findAll(type).stream()
                .filter(node -> CallableScope.belongsTo(node, owner))
                .count();
    }

}
