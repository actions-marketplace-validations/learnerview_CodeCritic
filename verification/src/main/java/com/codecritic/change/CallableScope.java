package com.codecritic.change;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;

/**
 * Determines whether an AST node belongs to the scope of a specific callable.
 * <p>
 * The check walks up the parent chain and treats the first enclosing callable
 * declaration as the owner. Nodes inside nested lambdas or anonymous classes are
 * therefore attributed to the nearest enclosing method or constructor.
 */
public final class CallableScope {

    private CallableScope() {
    }

    /**
     * Tests whether the given node is contained within the body of the supplied callable.
     *
     * @param node the AST node to inspect
     * @param owner the callable declaration that should own the node
     * @return {@code true} if no intermediate callable is encountered before {@code owner};
     *         {@code false} if the node is outside the callable or belongs to a nested callable
     */
    public static boolean belongsTo(Node node, CallableDeclaration<?> owner) {
        Node parent = node.getParentNode().orElse(null);
        while (parent != null) {
            if (parent instanceof CallableDeclaration<?>) {
                return parent == owner;
            }
            parent = parent.getParentNode().orElse(null);
        }
        return false;
    }
}
