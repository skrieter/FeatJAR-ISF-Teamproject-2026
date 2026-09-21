/*
 * Copyright (C) 2026 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-formula-analysis-sat4j.
 *
 * formula-analysis-sat4j is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * formula-analysis-sat4j is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with formula-analysis-sat4j. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatureIDE/FeatJAR-formula-analysis-sat4j> for further information.
 */
package de.featjar.analysis.sat4j.cli;

import de.featjar.analysis.sat4j.Preprocessor;
import de.featjar.base.FeatJAR;
import de.featjar.base.cli.ACommand;
import de.featjar.base.cli.Option;
import de.featjar.base.cli.OptionList;
import de.featjar.base.cli.Options;
import de.featjar.base.computation.Computations;
import de.featjar.base.data.Result;
import de.featjar.base.io.IO;
import de.featjar.base.tree.Trees;
import de.featjar.formula.assignment.Assignment;
import de.featjar.formula.assignment.BooleanAssignment;
import de.featjar.formula.assignment.BooleanAssignmentList;
import de.featjar.formula.assignment.conversion.ComputeBooleanClauseList;
import de.featjar.formula.computation.ComputeCNFFormula;
import de.featjar.formula.computation.ComputeNNFFormula;
import de.featjar.formula.io.FormulaFormats;
import de.featjar.formula.io.textual.CPPAssignmentFormat;
import de.featjar.formula.io.textual.ExpressionSerializer;
import de.featjar.formula.io.textual.JavaSymbols;
import de.featjar.formula.structure.IFormula;
import de.featjar.formula.structure.connective.And;
import de.featjar.formula.structure.connective.Reference;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.sat4j.core.VecInt;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.TimeoutException;

public class PreprocessorCommand extends ACommand {

    public static enum Mode {
        PROCESS,
        PRINT_VARIABLES,
        PRINT_ANNOTATIONS,
        PRINT_PRESENCE_CONDITIONS,
        FIND_DEAD_CODE
    }

    public static enum MissingVariables {
        IGNORE,
        TRUE,
        FALSE
    }

    public static final Option<Path> CONFIGURATION_OPTION = Options.newOption("configuration", Options.PathParser)
            .setDescription("Path to configuration file")
            .setValidator(Options.PathValidator);

    public static final Option<Mode> MODE_OPTION = Options.newEnumOption("mode", Mode.class)
            .setDefaultArgument(Mode.PROCESS.name())
            .setDescription("Mode of operation");

    public static final Option<MissingVariables> MISSING_VARIABLES_OPTION = Options.newEnumOption(
                    "missing-variables", MissingVariables.class)
            .setDefaultArgument(MissingVariables.IGNORE.name())
            .setDescription("How to deal with variables in the processed file that do not appear in the given config");

    public static final Option<String> PREFIX_OPTION = Options.newOption("annotation-prefix", Options.StringParser)
            .setDefaultArgument("#")
            .setDescription("The prefix that precedes each annotation");

    public static final Option<Path> FEATURE_MODEL_OPTION = Options.newOption("feature-model", Options.PathParser)
            .setDescription("Path to feature model file")
            .setValidator(Options.PathValidator);

    @Override
    public int run(OptionList optionParser) {
        Path in = optionParser.getResult(INPUT_OPTION).orElseThrow();
        Path out = optionParser.getResult(OUTPUT_OPTION).orElse(null);
        Charset charset = StandardCharsets.UTF_8;
        String annotationPrefix = optionParser.getResult(PREFIX_OPTION).orElseThrow();

        Preprocessor preprocessor = new Preprocessor(annotationPrefix, JavaSymbols.INSTANCE);

        Mode mode = optionParser.getResult(MODE_OPTION).orElseThrow();

        Stream<String> stream = null;
        try {
            switch (mode) {
                case PROCESS:
                    stream = preprocess(
                            in,
                            out,
                            optionParser.getResult(CONFIGURATION_OPTION).orElseThrow(),
                            optionParser.getResult(MISSING_VARIABLES_OPTION).orElseThrow(),
                            charset,
                            preprocessor);
                    break;
                case PRINT_VARIABLES:
                    stream = printVariableNames(in, charset, preprocessor);
                    break;
                case PRINT_ANNOTATIONS:
                    stream = printAnnotations(in, charset, preprocessor);
                    break;
                case PRINT_PRESENCE_CONDITIONS:
                    stream = printPresenceConditions(in, charset, preprocessor);
                    break;
                case FIND_DEAD_CODE:
                    stream = detectDeadCode(in, charset, preprocessor, optionParser);
                    break;
                default:
                    return 1;
            }
        } catch (IOException e) {
            FeatJAR.log().error(e);
            return 1;
        }

        if (out != null) {
            try (BufferedWriter writer = Files.newBufferedWriter(
                    out, charset, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                stream.forEach(line -> {
                    try {
                        writer.write(line);
                        writer.newLine();
                        writer.flush();
                    } catch (IOException e) {
                        FeatJAR.log().error(e);
                    }
                });
            } catch (IOException e) {
                FeatJAR.log().error(e);
                return 1;
            }
        } else {
            stream.forEach(FeatJAR.log()::plainMessage);
        }
        return 0;
    }

    private Stream<String> preprocess(
            Path in,
            Path out,
            Path assignmentPath,
            MissingVariables missingVariables,
            Charset charset,
            Preprocessor preprocessor)
            throws IOException {
        Result<Assignment> parsedAssignment = IO.load(assignmentPath, new CPPAssignmentFormat());
        if (parsedAssignment.isEmpty()) {
            FeatJAR.log().problems(parsedAssignment);
            return Stream.empty();
        }

        final Assignment assignment;
        switch (missingVariables) {
            case FALSE:
                assignment = addMissingVariablesToAssignment(
                        parsedAssignment.get(),
                        preprocessor.extractVariableNames(Files.lines(in, charset)),
                        Boolean.FALSE);
                break;
            case IGNORE:
                assignment = addMissingVariablesToAssignment(
                        parsedAssignment.get(), preprocessor.extractVariableNames(Files.lines(in, charset)), null);
                break;
            case TRUE:
                assignment = addMissingVariablesToAssignment(
                        parsedAssignment.get(),
                        preprocessor.extractVariableNames(Files.lines(in, charset)),
                        Boolean.TRUE);
                break;
            default:
                throw new IllegalStateException(String.valueOf(missingVariables));
        }

        return preprocessor.preprocess(Files.lines(in, charset), assignment);
    }

    private Assignment addMissingVariablesToAssignment(
            Assignment orgAssignment, List<String> extractVariableNames, Object value) throws IOException {
        LinkedHashMap<String, Object> variableValuePairs = new LinkedHashMap<>(orgAssignment.getAll());
        for (String variableName : extractVariableNames) {
            if (!variableValuePairs.containsKey(variableName)) {
                variableValuePairs.put(variableName, value);
            }
        }
        return new Assignment(variableValuePairs);
    }

    private Stream<String> printVariableNames(Path in, Charset charset, Preprocessor preprocessor) throws IOException {
        return preprocessor.extractVariableNames(Files.lines(in, charset)).stream();
    }

    private Stream<String> printAnnotations(Path in, Charset charset, Preprocessor preprocessor) throws IOException {
        return preprocessor.extractAnnotations(Files.lines(in, charset)).stream();
    }

    private Stream<String> printPresenceConditions(Path in, Charset charset, Preprocessor preprocessor)
            throws IOException {
        ExpressionSerializer serializer = new ExpressionSerializer();
        serializer.setSymbols(JavaSymbols.INSTANCE);
        List<IFormula> presenceConditions = preprocessor.computePresenceConditions(Files.lines(in, charset));
        return IntStream.range(0, presenceConditions.size())
                .mapToObj(i -> String.format(
                        "%d: %s",
                        i + 1,
                        Trees.traverse(presenceConditions.get(i), serializer).orElseThrow()));
    }

    private Stream<String> detectDeadCode(
            Path in, Charset charset, Preprocessor preprocessor, OptionList optionParser) throws IOException {
        Path featureModelPath = optionParser.getResult(FEATURE_MODEL_OPTION).orElseThrow();
        IFormula featureModel = IO.load(featureModelPath, FormulaFormats.getInstance()).orElseThrow();
        return preprocessor.findDeadCode(Files.lines(in, charset), consistentWith(featureModel))
                .stream();
    }

    /**
     * {@return a test whether a presence condition can be true in some configuration of the feature model}
     */
    public static Predicate<IFormula> consistentWith(IFormula featureModel) {
        IFormula model = featureModel instanceof Reference ref ? ref.getExpression() : featureModel;
        return condition -> {
            BooleanAssignmentList clauses = Computations.of((IFormula) new And(model, condition))
                    .map(ComputeNNFFormula::new)
                    .map(ComputeCNFFormula::new)
                    .map(ComputeBooleanClauseList::new)
                    .compute();

            ISolver solver = SolverFactory.newDefault();
            solver.newVar(clauses.getVariableMap().size());
            try {
                for (BooleanAssignment clause : clauses) {
                    solver.addClause(new VecInt(clause.get()));
                }
                return solver.isSatisfiable();
            } catch (ContradictionException e) {
                return false;
            } catch (TimeoutException e) {
                return false;
            }
        };
    }

    @Override
    public Optional<String> getDescription() {
        return Optional.of("Preprocesses files with annotations (SAT4J-based, supports dead-code detection)");
    }

    @Override
    public Optional<String> getShortName() {
        return Optional.of("preprocessor-sat4j");
    }
}