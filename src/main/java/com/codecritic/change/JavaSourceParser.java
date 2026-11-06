package com.codecritic.change;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;

import java.util.Optional;

/**
 * Parses Java source text into a JavaParser compilation unit.
 */
public final class JavaSourceParser {

    private JavaSourceParser() {
    }

    /**
     * Parses Java source using the Java 21 language level.
     * <p>
     * Blank input and syntactically invalid sources yield an empty result rather
     * than an exception.
     *
     * @param source the Java source text to parse
     * @return the parsed compilation unit, or an empty optional when parsing fails
     */
    public static Optional<CompilationUnit> parse(String source) {
        if (source == null || source.isBlank()) {
            return Optional.empty();
        }

        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setAttributeComments(false);

        ParseResult<CompilationUnit> result = new JavaParser(configuration).parse(source);
        return result.isSuccessful() ? result.getResult() : Optional.empty();
    }
}
