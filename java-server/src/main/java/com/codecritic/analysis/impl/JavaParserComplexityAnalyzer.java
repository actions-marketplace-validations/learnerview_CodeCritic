package com.codecritic.analysis.impl;

import com.codecritic.analysis.ComplexityAnalyzer;
import com.codecritic.dto.ComplexityResponse;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
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
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AST-based complexity analyzer using JavaParser.
 *
 * If parsing fails it delegates to a lightweight token heuristic so the API
 * still returns a useful answer (graceful degradation).
 */
@Component
public class JavaParserComplexityAnalyzer implements ComplexityAnalyzer {

    /**
     * Creates the analyzer with an empty parser state.
     */
    public JavaParserComplexityAnalyzer() {
    }

    private static final Logger log = LoggerFactory.getLogger(JavaParserComplexityAnalyzer.class);

    /**
     * Analyzes the supplied Java source and reports the highest cyclomatic
     * complexity found in any single method.
     *
     * <p>Blank or {@code null} input yields zero scores. If the source cannot be
     * parsed, a lightweight token-based heuristic is used instead so the caller
     * still receives a useful approximation.</p>
     *
     * @param code source code to analyze; may be {@code null} or blank
     * @return the maximum cyclomatic complexity and a derived cognitive score
     */
    @Override
    public ComplexityResponse analyze(String code) {
        if (code == null || code.isBlank()) {
            return new ComplexityResponse(0, 0);
        }
        try {
            CompilationUnit cu = StaticJavaParser.parse(code);
            ComplexityVisitor visitor = new ComplexityVisitor();
            visitor.visit(cu, null);
            int maxCyclomatic = visitor.getMaxCyclomatic();
            int cognitive = Math.max(1, maxCyclomatic / 2);
            return new ComplexityResponse(maxCyclomatic, cognitive);
        } catch (Exception e) {
            log.warn("JavaParser failed to parse source; falling back to heuristic: {}", e.getMessage());
            return fallbackHeuristic(code);
        }
    }

    private ComplexityResponse fallbackHeuristic(String code) {
        String cleaned = stripCommentsAndStrings(code == null ? "" : code);
        int cyclomatic = 1;
        String[] tokens = {"if(", "for(", "while(", "case ", "&&", "||", "catch(", "?:", "default:"};
        for (String t : tokens) {
            int idx = 0;
            while ((idx = cleaned.indexOf(t, idx)) != -1) {
                cyclomatic++;
                idx += t.length();
            }
        }
        int cognitive = Math.max(1, cyclomatic / 2);
        return new ComplexityResponse(cyclomatic, cognitive);
    }

    private static String stripCommentsAndStrings(String code) {
        StringBuilder sb = new StringBuilder(code.length());
        int i = 0;
        int n = code.length();
        while (i < n) {
            char c = code.charAt(i);
            if (c == '"') {
                // skip string literal
                i++;
                while (i < n) {
                    char sc = code.charAt(i);
                    if (sc == '\\') { i += 2; continue; }
                    if (sc == '"') { i++; break; }
                    i++;
                }
                sb.append(' ');
            } else if (c == '\'') {
                // skip char literal
                i++;
                while (i < n) {
                    char sc = code.charAt(i);
                    if (sc == '\\') { i += 2; continue; }
                    if (sc == '\'') { i++; break; }
                    i++;
                }
                sb.append(' ');
            } else if (c == '/' && i + 1 < n && code.charAt(i + 1) == '/') {
                // skip line comment
                while (i < n && code.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && code.charAt(i + 1) == '*') {
                // skip block comment
                i += 2;
                while (i + 1 < n && !(code.charAt(i) == '*' && code.charAt(i + 1) == '/')) i++;
                i += 2;
                sb.append(' ');
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static class ComplexityVisitor extends VoidVisitorAdapter<Void> {
        private int current = 0;
        private int max = 0;

        /**
         * Tracks complexity per method and keeps the highest score seen.
         *
         * @param md method declaration being visited
         * @param arg visitor argument; unused
         */
        @Override
        public void visit(MethodDeclaration md, Void arg) {
            int before = current;
            current = 1;
            super.visit(md, arg);
            if (current > max) max = current;
            current = before;
        }

        /**
         * Adds one decision point for an {@code if} statement.
         *
         * @param n statement being visited
         * @param arg visitor argument; unused
         */
        @Override
        public void visit(IfStmt n, Void arg) {
            current++;
            super.visit(n, arg);
        }

        /**
         * Adds one decision point for a {@code for} loop.
         *
         * @param n statement being visited
         * @param arg visitor argument; unused
         */
        @Override
        public void visit(ForStmt n, Void arg) {
            current++;
            super.visit(n, arg);
        }

        /**
         * Adds one decision point for an enhanced {@code for} loop.
         *
         * @param n statement being visited
         * @param arg visitor argument; unused
         */
        @Override
        public void visit(ForEachStmt n, Void arg) {
            current++;
            super.visit(n, arg);
        }

        /**
         * Adds one decision point for a {@code while} loop.
         *
         * @param n statement being visited
         * @param arg visitor argument; unused
         */
        @Override
        public void visit(WhileStmt n, Void arg) {
            current++;
            super.visit(n, arg);
        }

        /**
         * Adds one decision point for a {@code do}-{@code while} loop.
         *
         * @param n statement being visited
         * @param arg visitor argument; unused
         */
        @Override
        public void visit(DoStmt n, Void arg) {
            current++;
            super.visit(n, arg);
        }

        /**
         * Adds one decision point per non-default {@code switch} label.
         *
         * @param n switch entry being visited
         * @param arg visitor argument; unused
         */
        @Override
        public void visit(SwitchEntry n, Void arg) {
            if (n.getLabels() != null && !n.getLabels().isEmpty()) {
                current += n.getLabels().size();
            }
            super.visit(n, arg);
        }

        /**
         * Adds one decision point for a {@code catch} clause.
         *
         * @param n clause being visited
         * @param arg visitor argument; unused
         */
        @Override
        public void visit(CatchClause n, Void arg) {
            current++;
            super.visit(n, arg);
        }

        /**
         * Adds one decision point for a ternary expression.
         *
         * @param n expression being visited
         * @param arg visitor argument; unused
         */
        @Override
        public void visit(ConditionalExpr n, Void arg) {
            current++;
            super.visit(n, arg);
        }

        /**
         * Adds one decision point for each logical {@code &&} or {@code ||}.
         *
         * @param n expression being visited
         * @param arg visitor argument; unused
         */
        @Override
        public void visit(BinaryExpr n, Void arg) {
            if (n.getOperator() == BinaryExpr.Operator.AND || n.getOperator() == BinaryExpr.Operator.OR) {
                current++;
            }
            super.visit(n, arg);
        }

        /**
         * Returns the highest method complexity found, clamped to at least one.
         *
         * @return the maximum cyclomatic complexity
         */
        public int getMaxCyclomatic() {
            return Math.max(1, max);
        }
    }
}
