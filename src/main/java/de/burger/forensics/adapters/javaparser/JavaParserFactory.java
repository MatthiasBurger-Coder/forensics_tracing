package de.burger.forensics.adapters.javaparser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates per-scan JavaParser instances with an isolated symbol solver.
 */
final class JavaParserFactory {

    JavaParser create(Path root) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver(false));
        if (Files.isDirectory(root)) {
            typeSolver.add(new JavaParserTypeSolver(root));
        } else {
            Path parent = root.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                typeSolver.add(new JavaParserTypeSolver(parent));
            }
        }
        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setSymbolResolver(new JavaSymbolSolver(typeSolver));
        return new JavaParser(configuration);
    }
}
