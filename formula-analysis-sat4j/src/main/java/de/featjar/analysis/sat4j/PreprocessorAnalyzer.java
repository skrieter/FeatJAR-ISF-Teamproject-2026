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
package de.featjar.analysis.sat4j;

import de.featjar.base.FeatJAR;
import de.featjar.base.data.Result;
import de.featjar.base.tree.Trees;
import de.featjar.composition.ExpressionParser;
import de.featjar.formula.assignment.Assignment;
import de.featjar.formula.io.textual.ExpressionSerializer;
import de.featjar.formula.io.textual.Symbols;
import de.featjar.formula.structure.IExpression;
import de.featjar.formula.structure.IFormula;
import de.featjar.formula.structure.connective.And;
import de.featjar.formula.structure.connective.Not;
import de.featjar.formula.structure.predicate.False;
import de.featjar.formula.structure.predicate.True;
import de.featjar.formula.structure.term.value.Variable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PreprocessorAnalyzer {

    private static final String IF_GROUP = "if";
    private static final String ELIF_GROUP = "elif";
    private static final String ELSE_GROUP = "else";
    private static final String ENDIF_GROUP = "endif";
    private static final String IF_CONDITION_GROUP = "ifCondition";
    private static final String ELIF_CONDITION_GROUP = "elifCondition";
    private static final String START_CONDITION_GROUP = "startCondition";

    private final ExpressionParser annotationParser;

    private final Pattern annotationPattern;
    private final Pattern startAnnotationPattern;

    private class Filter implements Predicate<String> {

        private final Assignment assignment;

        private final LinkedList<IExpression> expressionStack = new LinkedList<>();
        private final LinkedList<Boolean> evaluationStack = new LinkedList<>();

        private int lineNumber;

        public Filter(Assignment assignment) {
            this.assignment = assignment;
        }

        @Override
        public boolean test(String line) {
            lineNumber++;
            Matcher matcher = annotationPattern.matcher(line);
            if (matcher.matches()) {
                if (matcher.group(ENDIF_GROUP) != null) {
                    if (expressionStack.isEmpty()) {
                        FeatJAR.log().warning("Line %d: no annotation to end", lineNumber);
                    } else {
                        expressionStack.pop();
                        evaluationStack.pop();
                    }
                    return false;
                } else if (matcher.group(ELSE_GROUP) != null) {
                    if (expressionStack.isEmpty()) {
                        FeatJAR.log().warning("Line %d: no annotation for else", lineNumber);
                    } else {
                        Boolean eval = evaluationStack.pop();
                        if (evaluationStack.isEmpty() || evaluationStack.peek()) {
                            evaluationStack.push(!eval);
                        } else {
                            evaluationStack.push(Boolean.FALSE);
                        }
                    }
                    return false;
                } else if (matcher.group(ELIF_GROUP) != null) {
                    if (expressionStack.isEmpty()) {
                        FeatJAR.log().warning("Line %d: no annotation for elif", lineNumber);
                    } else {
                        Boolean eval = evaluationStack.pop();
                        if (evaluationStack.isEmpty() || evaluationStack.peek()) {
                            evaluationStack.push(!eval);
                        } else {
                            evaluationStack.push(Boolean.FALSE);
                        }
                    }
                    Result<IExpression> parse = annotationParser.parse(matcher.group(ELIF_CONDITION_GROUP));
                    if (parse.isPresent()) {
                        IExpression annotationExpression = parse.get();
                        expressionStack.push(annotationExpression);
                        if (evaluationStack.isEmpty() || evaluationStack.peek()) {
                            Object evaluation =
                                    annotationExpression.evaluate(assignment).orElse(null);
                            if (evaluation instanceof Boolean) {
                                evaluationStack.push((Boolean) evaluation);
                            } else {
                                FeatJAR.log().warning("Line %d: could not evaluate annotation: %s", lineNumber, line);
                                evaluationStack.push(Boolean.FALSE);
                            }
                        } else {
                            evaluationStack.push(Boolean.FALSE);
                        }
                    } else {
                        FeatJAR.log().warning("Line %d: could not parse annotation: %s", lineNumber, line);
                        return true;
                    }
                    return false;
                } else if (matcher.group(IF_GROUP) != null) {
                    Result<IExpression> parse = annotationParser.parse(matcher.group(IF_CONDITION_GROUP));
                    if (parse.isPresent()) {
                        IExpression annotationExpression = parse.get();
                        expressionStack.push(annotationExpression);
                        if (evaluationStack.isEmpty() || evaluationStack.peek()) {
                            Object evaluation =
                                    annotationExpression.evaluate(assignment).orElse(null);
                            if (evaluation instanceof Boolean) {
                                evaluationStack.push((Boolean) evaluation);
                            } else {
                                FeatJAR.log().warning("Line %d: could not evaluate annotation: %s", lineNumber, line);
                                evaluationStack.push(Boolean.FALSE);
                            }
                        } else {
                            evaluationStack.push(Boolean.FALSE);
                        }
                        return false;
                    } else {
                        FeatJAR.log().warning("Line %d: could not parse annotation: %s", lineNumber, line);
                        return true;
                    }
                } else {
                    FeatJAR.log().warning("Line %d: syntax error: %s", lineNumber, line);
                    return true;
                }
            } else {
                return evaluationStack.isEmpty() || evaluationStack.peek();
            }
        }
    }

    private class VariableNames implements Function<String, Stream<Variable>> {

        private int lineNumber;

        @Override
        public Stream<Variable> apply(String line) {
            lineNumber++;
            Matcher matcher = startAnnotationPattern.matcher(line);
            if (matcher.matches()) {
                Result<IExpression> parse = annotationParser.parse(matcher.group(IF_CONDITION_GROUP));
                if (parse.isPresent()) {
                    return parse.get().getVariableStream();
                } else {
                    FeatJAR.log().warning("Line %d: could not parse annotation: %s", lineNumber, line);
                    return null;
                }
            }
            return null;
        }
    }

    public PreprocessorAnalyzer(String annotationPrefix, Symbols symbols) {
        annotationParser = new ExpressionParser();
        annotationParser.setSymbols(symbols);
        String prefix = Pattern.quote(annotationPrefix);
        annotationPattern = Pattern.compile(prefix + "\\s*("
            + "(?<" + ENDIF_GROUP + ">endif\\s*)|"
            + "(?<" + ELSE_GROUP + ">else\\s*)|"
            + "(?<" + IF_GROUP + ">if\\s+(?<" + IF_CONDITION_GROUP + ">.+))|"
            + "(?<" + ELIF_GROUP + ">elif\\s+(?<" + ELIF_CONDITION_GROUP + ">.+))"
            + ")");

        startAnnotationPattern = Pattern.compile(prefix + "\\s*(?:if|elif)\\s+(?<" + START_CONDITION_GROUP + ">.+)");
    }

    /**
     * {@return a filtered stream that contains only lines that remain after preprocessing with the given variable assignment}
     *
     * <b>Note</b>: The return stream is <b>not state less</b>.
     * It is not suitable for parallel consumption.
     *
     * @param lines the line stream
     * @param assignment the variable assignment
     */
    public Stream<String> preprocess(Stream<String> lines, Assignment assignment) {
        return lines.sequential().filter(new Filter(assignment));
    }

    /**
     * {@return the presence condition of each line, in order}
     *
     * @param lines the line stream
     */
    public List<IFormula> computePresenceConditions(Stream<String> lines) {
        LinkedList<IFormula> stack = new LinkedList<>();
        LinkedList<Integer> elifCounts = new LinkedList<>(); // each elif adds one extra stack entry to its if
        return lines.sequential()
                .map(line -> {
                    Matcher matcher = annotationPattern.matcher(line);
                    if (!matcher.matches()) {
                        if (stack.isEmpty()) {
                            return (IFormula) True.INSTANCE;
                        }
                        List<IFormula> conjuncts = new ArrayList<>();
                        stack.descendingIterator().forEachRemaining(conjuncts::add);
                        return conjuncts.size() == 1 ? conjuncts.get(0) : new And(conjuncts);
                    }
                    if (matcher.group(IF_GROUP) != null) {
                        stack.push((IFormula)
                                annotationParser.parse(matcher.group(IF_CONDITION_GROUP)).orElseThrow());
                        elifCounts.push(0);
                    } else if (matcher.group(ELSE_GROUP) != null) {
                        stack.push(new Not(popChecked(stack, line)));
                    } else if (matcher.group(ENDIF_GROUP) != null) {
                        popChecked(stack, line);
                        for (int i = elifCounts.pop(); i > 0; i--) stack.pop();
                    } else if (matcher.group(ELIF_GROUP) != null) {
                        stack.push(new Not(popChecked(stack, line)));
                        elifCounts.push(elifCounts.pop() + 1);
                        stack.push((IFormula)
                                annotationParser.parse(matcher.group(ELIF_CONDITION_GROUP)).orElseThrow());
                    }
                    return (IFormula) False.INSTANCE;
                })
                .collect(Collectors.toList());
    }

    private IFormula popChecked(LinkedList<IFormula> stack, String line) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Unbalanced presence annotation (empty stack): " + line);
        }
        return stack.pop();
    }

    public List<String> extractVariableNames(Stream<String> lines) {
        return lines.flatMap(new VariableNames())
                .distinct()
                .map(Variable::getName)
                .collect(Collectors.toList());
    }

    public List<String> extractAnnotations(Stream<String> lines) {
        return lines.filter(annotationPattern.asMatchPredicate()).collect(Collectors.toList());
    }

    /**
     * {@return one message per block of code lines whose presence condition is not satisfiable}
     *
     * @param lines the line stream
     * @param isSatisfiable tests a presence condition, e.g., together with a feature model
     */
    public List<String> findDeadCode(Stream<String> lines, Predicate<IFormula> isSatisfiable) {
        List<IFormula> presence = computePresenceConditions(lines);
        ExpressionSerializer serializer = new ExpressionSerializer();
        serializer.setSymbols(annotationParser.getSymbols());
        List<String> dead = new ArrayList<>();
        // a code block is a maximal run of lines between annotations, which have the presence condition False
        for (int start = 0, i = 0; i <= presence.size(); i++) {
            if (i == presence.size() || presence.get(i) == False.INSTANCE) {
                if (start < i && !isSatisfiable.test(presence.get(start))) {
                    dead.add(String.format(
                            "Dead code at lines %d-%d: %s",
                            start + 1,
                            i,
                            Trees.traverse(presence.get(start), serializer).orElseThrow()));
                }
                start = i + 1;
            }
        }
        return dead;
    }

    /** Returns annotations whose effective condition holds in every valid configuration. */
    public List<String> findSuperfluousAnnotations(Stream<String> lines, Predicate<IFormula> isSatisfiable) {
        List<String> lineList = lines.toList();
        List<IFormula> presence = computePresenceConditions(lineList.stream());
        List<String> result = new ArrayList<>();

        for (int i = 0; i < lineList.size(); i++) {
            Matcher matcher = annotationPattern.matcher(lineList.get(i));

            if (!matcher.matches()
                    || matcher.group(ENDIF_GROUP) != null
                    || i + 1 >= presence.size()) {
                continue;
            }

            int nextLine = i + 1;

            while (nextLine < presence.size()
                    && presence.get(nextLine) == False.INSTANCE) {
                nextLine++;
            }

            if (nextLine < presence.size()
                    && !isSatisfiable.test(new Not(presence.get(nextLine)))) {
                result.add("Line " + (i + 1) + ": " + lineList.get(i));
            }
        }
        return result;
    }
}
