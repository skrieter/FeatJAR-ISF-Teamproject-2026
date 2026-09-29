/*
 * Copyright (C) 2026 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-formula.
 *
 * formula is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * formula is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with formula. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatureIDE/FeatJAR-formula> for further information.
 */
package de.featjar.composition.cli;

import de.featjar.base.FeatJAR;
import de.featjar.base.cli.ACommand;
import de.featjar.base.cli.Option;
import de.featjar.base.cli.OptionList;
import de.featjar.base.cli.Options;
import de.featjar.base.data.Problem;
import de.featjar.base.data.Result;
import de.featjar.base.io.IO;
import de.featjar.base.tree.Trees;
import de.featjar.composition.Preprocessor;
import de.featjar.formula.assignment.Assignment;
import de.featjar.formula.io.FormulaFormats;
import de.featjar.formula.io.textual.CPPAssignmentFormat;
import de.featjar.formula.io.textual.ExpressionSerializer;
import de.featjar.formula.io.textual.JavaSymbols;
import de.featjar.formula.structure.IFormula;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class PreprocessorCommand extends ACommand {

    public static enum Mode {
        PROCESS,
        PRINT_VARIABLES,
        PRINT_ANNOTATIONS,
        CHECK_SYNTAX,
        CHECK_STRUCTURE,
        FIND_UNKNOWN_FEATURES,
        PRINT_PRESENCE_CONDITIONS
    }

    public static enum MissingVariables {
        IGNORE,
        TRUE,
        FALSE
    }

    public static enum AnnotationStyle {
        CPP(Preprocessor.Style.CPP),
        ANTENNA(Preprocessor.Style.ANTENNA),
        MUNGE(Preprocessor.Style.MUNGE);

        private final Preprocessor.Style style;

        private AnnotationStyle(Preprocessor.Style style) {
            this.style = style;
        }

        public Preprocessor.Style getStyle() {
            return style;
        }
    }

    public static final Option<Path> CONFIGURATION_OPTION = Options.newOption("configuration", Options.PathParser)
            .setDescription("Path to configuration file")
            .setValidator(Options.PathValidator);

    public static final Option<Path> FEATURE_MODEL_OPTION = Options.newOption("feature-model", Options.PathParser)
            .setDescription("Path to feature model file")
            .setValidator(Options.PathValidator);

    public static final Option<Mode> MODE_OPTION = Options.newEnumOption("mode", Mode.class)
            .setDefaultArgument(Mode.PROCESS.name())
            .setDescription("Mode of operation");

    public static final Option<MissingVariables> MISSING_VARIABLES_OPTION = Options.newEnumOption(
                    "missing-variables", MissingVariables.class)
            .setDefaultArgument(MissingVariables.IGNORE.name())
            .setDescription("How to deal with variables in the processed file that do not appear in the given config");

    public static final Option<Boolean> ALLOW_PARTIAL_OPTION = Options.newFlag("allow-partial")
            .setDescription("Keep undecided annotations in simplified form instead of reporting an error");

    public static final Option<AnnotationStyle> STYLE_OPTION = Options.newEnumOption(
                    "annotation-style", AnnotationStyle.class)
            .setDefaultArgument(AnnotationStyle.CPP.name())
            .setDescription("The syntax of the annotations (CPP: #if A, ANTENNA: //#if A, MUNGE: /*if[A]*/)");

    public static final Option<String> PREFIX_OPTION = Options.newOption("annotation-prefix", Options.StringParser)
            .setDescription("The prefix that precedes each annotation (overrides the prefix of the annotation style)");

    @Override
    public int run(OptionList optionParser) {
        Path in = optionParser.getResult(INPUT_OPTION).orElseThrow();
        Path out = optionParser.getResult(OUTPUT_OPTION).orElse(null);
        Charset charset = StandardCharsets.UTF_8;
        Preprocessor.Style style =
                optionParser.getResult(STYLE_OPTION).orElseThrow().getStyle();
        Result<String> annotationPrefix = optionParser.getResult(PREFIX_OPTION);
        if (annotationPrefix.isPresent()) {
            style = style.withPrefix(annotationPrefix.get());
        }

        Preprocessor preprocessor = new Preprocessor(style);

        Mode mode = optionParser.getResult(MODE_OPTION).orElseThrow();

        try (Stream<String> lines = Files.lines(in, charset)) {
            Stream<String> output;
            switch (mode) {
                case CHECK_STRUCTURE:
                    return checkStructure(lines, preprocessor);
                case PROCESS:
                    output = preprocess(
                            lines,
                            in,
                            optionParser.getResult(CONFIGURATION_OPTION).orElseThrow(),
                            optionParser.getResult(MISSING_VARIABLES_OPTION).orElseThrow(),
                            optionParser.getResult(ALLOW_PARTIAL_OPTION).orElseThrow(),
                            charset,
                            preprocessor);
                    break;
                case PRINT_VARIABLES:
                    output = printVariableNames(lines, preprocessor);
                    break;
                case PRINT_ANNOTATIONS:
                    output = printAnnotations(lines, preprocessor);
                    break;
                case PRINT_PRESENCE_CONDITIONS:
                    output = printPresenceConditions(lines, preprocessor);
                    break;
                case CHECK_SYNTAX:
                    output = detectInvalidSyntax(lines, preprocessor);
                    break;
                case FIND_UNKNOWN_FEATURES:
                    output = findUnknownFeatures(
                            lines, optionParser.getResult(FEATURE_MODEL_OPTION).orElseThrow(), preprocessor);
                    break;
                default:
                    return 1;
            }
            if (out != null) {
                try (BufferedWriter writer = Files.newBufferedWriter(
                        out, charset, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    Iterator<String> iterator = output.iterator();
                    while (iterator.hasNext()) {
                        writer.write(iterator.next());
                        writer.newLine();
                    }
                }
            } else {
                output.forEach(FeatJAR.log()::plainMessage);
            }
        } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
            FeatJAR.log().error(e);
            return 1;
        }
        return 0;
    }

    private int checkStructure(Stream<String> lines, Preprocessor preprocessor) {
        List<Problem> problems = preprocessor.checkStructure(lines);
        FeatJAR.log().problems(problems);
        return (problems.isEmpty() ? 0 : 1);
    }

    private Stream<String> preprocess(
            Stream<String> lines,
            Path in,
            Path assignmentPath,
            MissingVariables missingVariables,
            boolean allowPartial,
            Charset charset,
            Preprocessor preprocessor)
            throws IOException {
        Result<Assignment> parsedAssignment = IO.load(assignmentPath, new CPPAssignmentFormat());
        if (parsedAssignment.isEmpty()) {
            FeatJAR.log().problems(parsedAssignment);
            return Stream.empty();
        }

        Assignment assignment = parsedAssignment.get();
        if (missingVariables != MissingVariables.IGNORE) {
            try (Stream<String> variableLines = Files.lines(in, charset)) {
                assignment = addMissingVariablesToAssignment(
                        assignment,
                        preprocessor.extractVariableNames(variableLines),
                        missingVariables == MissingVariables.TRUE);
            }
        }

        return preprocessor.preprocess(lines, assignment, allowPartial);
    }

    private Assignment addMissingVariablesToAssignment(
            Assignment orgAssignment, List<String> extractVariableNames, Object value) {
        LinkedHashMap<String, Object> variableValuePairs = new LinkedHashMap<>(orgAssignment.getAll());
        for (String variableName : extractVariableNames) {
            if (!variableValuePairs.containsKey(variableName)) {
                variableValuePairs.put(variableName, value);
            }
        }
        return new Assignment(variableValuePairs);
    }

    private Stream<String> printVariableNames(Stream<String> lines, Preprocessor preprocessor) {
        return preprocessor.extractVariableNames(lines).stream();
    }

    private Stream<String> printAnnotations(Stream<String> lines, Preprocessor preprocessor) {
        return preprocessor.extractAnnotations(lines).stream();
    }

    private Stream<String> printPresenceConditions(Stream<String> lines, Preprocessor preprocessor) {
        ExpressionSerializer serializer = new ExpressionSerializer();
        serializer.setSymbols(JavaSymbols.INSTANCE);
        return preprocessor.computePresenceConditions(lines).stream()
                .map(formula -> Trees.traverse(formula, serializer).orElseThrow());
    }

    private Stream<String> detectInvalidSyntax(Stream<String> lines, Preprocessor preprocessor) {
        return preprocessor.checkSyntax(lines).stream()
                .map(problem -> String.format("Line %d: %s", problem.getLineNumber(), problem.getMessage()));
    }

    private Stream<String> findUnknownFeatures(Stream<String> lines, Path featureModelPath, Preprocessor preprocessor) {
        Result<IFormula> featureModel = IO.load(featureModelPath, FormulaFormats.getInstance());
        if (featureModel.isEmpty()) {
            FeatJAR.log().problems(featureModel);
            return Stream.empty();
        }
        return preprocessor.findUnknownFeatures(lines, featureModel.get()).stream()
                .map(problem -> String.format("line %d: %s", problem.getLineNumber(), problem.getMessage()));
    }

    @Override
    public Optional<String> getDescription() {
        return Optional.of("Preprocesses files with annotations");
    }

    @Override
    public Optional<String> getShortName() {
        return Optional.of("preprocessor");
    }
}
