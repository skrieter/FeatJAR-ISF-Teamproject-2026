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
package de.featjar.composition;

import de.featjar.base.FeatJAR;
import de.featjar.base.data.Problem;
import de.featjar.base.data.Problem.Severity;
import de.featjar.base.data.Result;
import de.featjar.base.data.Void;
import de.featjar.base.io.format.ParseProblem;
import de.featjar.base.tree.Trees;
import de.featjar.base.tree.visitor.ITreeVisitor;
import de.featjar.formula.assignment.Assignment;
import de.featjar.formula.io.textual.ExpressionSerializer;
import de.featjar.formula.io.textual.Symbols;
import de.featjar.formula.structure.IExpression;
import de.featjar.formula.structure.IFormula;
import de.featjar.formula.structure.connective.And;
import de.featjar.formula.structure.connective.Not;
import de.featjar.formula.structure.connective.Or;
import de.featjar.formula.structure.connective.Reference;
import de.featjar.formula.structure.predicate.False;
import de.featjar.formula.structure.predicate.True;
import de.featjar.formula.structure.term.value.Constant;
import de.featjar.formula.structure.term.value.Variable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Preprocessor {

    private static final String IF_GROUP = "if";
    private static final String ELIF_GROUP = "elif";
    private static final String ELSE_GROUP = "else";
    private static final String ENDIF_GROUP = "endif";
    private static final String IF_CONDITION_GROUP = "ifCondition";
    private static final String ELIF_CONDITION_GROUP = "elifCondition";

    private final ExpressionParser annotationParser;

    private final Pattern annotationPattern;
    private final Pattern startAnnotationPattern;

    /**
     * The state of an open if-block, i.e., an if annotation whose endif was not reached yet.
     */
    private static final class Block {

        /** Whether the lines around this block are kept. */
        private final boolean enclosingActive;

        /** Whether the lines of the current branch are kept. */
        private boolean active;

        /** Whether one of the previous branches is taken for certain, so all following branches are removed. */
        private boolean decided;

        /** Whether an annotation of this block was kept, so its endif has to be kept, too. */
        private boolean annotated;

        /** The formulas whose values are known within the current branch. */
        private Map<IExpression, Boolean> knownValues;

        /** The formulas whose values are known if none of the previous branches is taken. */
        private Map<IExpression, Boolean> remainingKnownValues;

        private Block(boolean enclosingActive, Map<IExpression, Boolean> knownValues) {
            this.enclosingActive = enclosingActive;
            this.knownValues = knownValues;
            this.remainingKnownValues = knownValues;
        }
    }

    /**
     * Replaces assigned variables with their values, replaces sub-formulas with known values,
     * and evaluates each sub-expression that is fully determined.
     * Expects the root to be a {@link Reference}, such that the root expression can be replaced, too.
     */
    private static final class Reducer implements ITreeVisitor<IExpression, Void> {

        private final Assignment assignment;
        private final Map<IExpression, Boolean> knownValues;

        private Reducer(Assignment assignment, Map<IExpression, Boolean> knownValues) {
            this.assignment = assignment;
            this.knownValues = knownValues;
        }

        @Override
        public Result<Void> nodeValidator(List<IExpression> path) {
            return ITreeVisitor.rootValidator(path, root -> root instanceof Reference, "expected formula reference");
        }

        @Override
        public TraversalAction lastVisit(List<IExpression> path) {
            ITreeVisitor.getCurrentNode(path).replaceChildren(this::reduce);
            return TraversalAction.CONTINUE;
        }

        private IExpression reduce(IExpression expression) {
            if (expression instanceof Variable) {
                return assignment
                        .getValue(expression.getName())
                        .map(value -> (IExpression) new Constant(value, expression.getType()))
                        .orElse(null);
            }
            if (expression.getChildrenCount() == 0) {
                return null;
            }
            List<Object> values =
                    expression.getChildren().stream().map(Reducer::valueOf).collect(Collectors.toList());
            Object value = expression.evaluate(values).orElse(null);
            if (value != null) {
                if (expression instanceof IFormula) {
                    return (Boolean) value ? True.INSTANCE : False.INSTANCE;
                }
                return new Constant(value, expression.getType());
            }
            if (expression instanceof And || expression instanceof Or) {
                Class<?> neutral = expression instanceof And ? True.class : False.class;
                expression.flatReplaceChildren(child -> neutral.isInstance(child) ? List.of() : null);
                if (expression.getChildrenCount() == 1) {
                    return expression.getFirstChild().get();
                }
            }
            Boolean knownValue = knownValues.get(expression);
            if (knownValue != null) {
                return knownValue ? True.INSTANCE : False.INSTANCE;
            }
            return null;
        }

        private static Object valueOf(IExpression expression) {
            if (expression instanceof True) {
                return Boolean.TRUE;
            } else if (expression instanceof False) {
                return Boolean.FALSE;
            } else if (expression instanceof Constant) {
                return ((Constant) expression).getValue();
            }
            return null;
        }

        @Override
        public Result<Void> getResult() {
            return Result.ofVoid();
        }
    }

    /**
     * Maps each line to the line that remains after preprocessing, or to {@code null} if the line is removed.
     * Annotations whose condition cannot be decided with the given assignment are either kept in simplified form
     * or, if not allowed, treated as {@code false}.
     */
    private class Filter implements Function<String, String> {

        private final Assignment assignment;
        private final boolean keepUndecided;

        private final LinkedList<Block> blocks = new LinkedList<>();

        private int lineNumber;

        public Filter(Assignment assignment, boolean keepUndecided) {
            this.assignment = assignment;
            this.keepUndecided = keepUndecided;
        }

        @Override
        public String apply(String line) {
            lineNumber++;
            Matcher matcher = annotationPattern.matcher(line);
            if (!matcher.matches()) {
                return blocks.isEmpty() || blocks.peek().active ? line : null;
            }
            if (matcher.group(IF_GROUP) != null) {
                Block enclosing = blocks.peek();
                blocks.push(
                        enclosing == null
                                ? new Block(true, Map.of())
                                : new Block(enclosing.active, enclosing.knownValues));
                return enterBranch(matcher, IF_GROUP, IF_CONDITION_GROUP);
            }
            if (blocks.isEmpty()) {
                FeatJAR.log().warning("Line %d: no matching if annotation: %s", lineNumber, line);
                return null;
            }
            if (matcher.group(ELIF_GROUP) != null) {
                return enterBranch(matcher, ELIF_GROUP, ELIF_CONDITION_GROUP);
            }
            if (matcher.group(ELSE_GROUP) != null) {
                return enterBranch(matcher, ELSE_GROUP, null);
            }
            return blocks.pop().annotated ? line : null;
        }

        /**
         * {@return the parsed condition, or {@code null} if it cannot be parsed}
         */
        private IFormula parseCondition(String condition) {
            Result<IExpression> parse = annotationParser.parse(condition);
            if (parse.isPresent() && parse.get() instanceof IFormula) {
                return (IFormula) parse.get();
            }
            FeatJAR.log().warning("Line %d: could not parse annotation condition: %s", lineNumber, condition);
            return null;
        }

        /**
         * Enters the next branch of the innermost block.
         * A condition that cannot be parsed is treated like a condition that cannot be decided.
         *
         * @param matcher the matched annotation
         * @param keywordGroup the group of the annotation keyword
         * @param conditionGroup the group of the annotation condition, {@code null} for an else annotation
         * @return the annotation to keep, or {@code null} if the annotation is removed
         */
        private String enterBranch(Matcher matcher, String keywordGroup, String conditionGroup) {
            Block block = blocks.peek();
            block.active = false;
            if (!block.enclosingActive || block.decided) {
                return null;
            }
            String conditionString = conditionGroup == null ? null : matcher.group(conditionGroup);
            IFormula condition = conditionString == null ? True.INSTANCE : parseCondition(conditionString);
            if (condition != null) {
                condition = reduce(condition, assignment, block.remainingKnownValues);
                if (condition instanceof False) {
                    return null;
                }
            }
            String prefix = matcher.group().substring(0, matcher.start(keywordGroup));
            if (condition instanceof True) {
                block.active = true;
                block.decided = true;
                block.knownValues = block.remainingKnownValues;
                return block.annotated ? prefix + "else" : null;
            }
            if (!keepUndecided) {
                FeatJAR.log().warning("Line %d: could not evaluate annotation: %s", lineNumber, matcher.group());
                return null;
            }
            block.active = true;
            if (condition == null) {
                block.knownValues = block.remainingKnownValues;
            } else {
                block.knownValues = withKnownValue(block.remainingKnownValues, condition, true);
                block.remainingKnownValues = withKnownValue(block.remainingKnownValues, condition, false);
                ExpressionSerializer serializer = new ExpressionSerializer();
                serializer.setSymbols(annotationParser.getSymbols());
                conditionString = Trees.traverse(condition, serializer).orElseThrow();
            }
            String keyword = block.annotated ? "elif " : "if ";
            block.annotated = true;
            return prefix + keyword + conditionString;
        }
    }

    private class VariableNames implements Function<String, Stream<Variable>> {

        private int lineNumber;

        @Override
        public Stream<Variable> apply(String line) {
            lineNumber++;
            Matcher matcher = startAnnotationPattern.matcher(line);
            if (matcher.matches()) {
                Result<IExpression> parse = annotationParser.parse(matcher.group(2));
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

    public Preprocessor(String annotationPrefix, Symbols symbols) {
        annotationParser = new ExpressionParser();
        annotationParser.setSymbols(symbols);
        String prefix = Pattern.quote(annotationPrefix);
        annotationPattern = Pattern.compile(prefix
                + "\\s*(?:"
                + group(ENDIF_GROUP, "endif\\s*")
                + "|" + group(ELSE_GROUP, "else\\s*")
                + "|" + group(IF_GROUP, "if\\s+" + group(IF_CONDITION_GROUP, ".+"))
                + "|" + group(ELIF_GROUP, "elif\\s+" + group(ELIF_CONDITION_GROUP, ".+"))
                + ")");

        startAnnotationPattern = Pattern.compile(prefix + "\\s*(if|elif)\\s+(.+)");
    }

    private static String group(String name, String regex) {
        return "(?<" + name + ">" + regex + ")";
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
        return lines.sequential().map(new Filter(assignment, false)).filter(Objects::nonNull);
    }

    /**
     * {@return a stream of the lines that remain after preprocessing with the given partial variable assignment}
     * Annotations that cannot be decided are kept, with assigned variables replaced by their values and simplified.
     * Conditions of nested annotations are also simplified with the values their enclosing annotations imply.
     * Annotations whose condition cannot be parsed are kept unchanged.
     *
     * <b>Note</b>: The return stream is <b>not state less</b>.
     * It is not suitable for parallel consumption.
     *
     * @param lines the line stream
     * @param assignment the partial variable assignment
     */
    public Stream<String> preprocessPartially(Stream<String> lines, Assignment assignment) {
        return lines.sequential().map(new Filter(assignment, true)).filter(Objects::nonNull);
    }

    private static IFormula reduce(IFormula condition, Assignment assignment, Map<IExpression, Boolean> knownValues) {
        Reference reference = new Reference(condition);
        Trees.traverse(reference, new Reducer(assignment, knownValues));
        return reference.getExpression();
    }

    /**
     * {@return a copy of the given known values, extended by the given formula with the given value
     * and all sub-formulas whose values follow directly from it}
     */
    private static Map<IExpression, Boolean> withKnownValue(
            Map<IExpression, Boolean> knownValues, IFormula formula, boolean value) {
        Map<IExpression, Boolean> extendedKnownValues = new HashMap<>(knownValues);
        LinkedList<IExpression> formulas = new LinkedList<>(List.of(formula));
        LinkedList<Boolean> formulaValues = new LinkedList<>(List.of(value));
        while (!formulas.isEmpty()) {
            IExpression current = formulas.pop();
            boolean currentValue = formulaValues.pop();
            extendedKnownValues.put(current, currentValue);
            if (current instanceof Not) {
                formulas.push(((Not) current).getExpression());
                formulaValues.push(!currentValue);
            } else if (currentValue ? current instanceof And : current instanceof Or) {
                for (IExpression child : current.getChildren()) {
                    formulas.push(child);
                    formulaValues.push(currentValue);
                }
            }
        }
        return extendedKnownValues;
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
                        stack.push((IFormula) annotationParser
                                .parse(matcher.group(IF_CONDITION_GROUP))
                                .orElseThrow());
                        elifCounts.push(0);
                    } else if (matcher.group(ELSE_GROUP) != null) {
                        stack.push(new Not(popChecked(stack, line)));
                    } else if (matcher.group(ENDIF_GROUP) != null) {
                        popChecked(stack, line);
                        for (int i = elifCounts.pop(); i > 0; i--) stack.pop();
                    } else if (matcher.group(ELIF_GROUP) != null) {
                        stack.push(new Not(popChecked(stack, line)));
                        elifCounts.push(elifCounts.pop() + 1);
                        stack.push((IFormula) annotationParser
                                .parse(matcher.group(ELIF_CONDITION_GROUP))
                                .orElseThrow());
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

    /**
     * Checks matching if/endif annotations
     */
    public List<Problem> checkStructure(Stream<String> lines) {
        LinkedList<Integer> stack = new LinkedList<>();
        List<Problem> problems = new ArrayList<>();
        List<Integer> ifLines = new ArrayList<>();
        List<String> lineList = lines.toList();
        int lineNumber = 0;
        int lastEndifLine = 0;

        for (String line : lineList) {
            lineNumber++;
            Matcher matcher = annotationPattern.matcher(line);

            if (matcher.matches()) {
                if (matcher.group(IF_GROUP) != null) { // this line is an #if (check notes.md file for more)
                    stack.push(lineNumber);
                    ifLines.add(lineNumber);
                } else if (matcher.group(ENDIF_GROUP) != null) { // this is an #endif
                    if (stack.isEmpty()) {
                        String addIfSuggestion = lastEndifLine == 0
                                ? "add a matching #if before line 1"
                                : "add a matching #if on line " + (lastEndifLine + 1);
                        problems.add(new ParseProblem(
                                "#endif without #if. Suggestion: remove the #endif or " + addIfSuggestion + ".",
                                Severity.ERROR,
                                lineNumber));
                    } else {
                        stack.pop();
                    }
                    lastEndifLine = lineNumber;
                }
            }
        }

        // the remaining #if lines have no matching #endif
        ListIterator<Integer> iterator = ifLines.listIterator();
        while (!stack.isEmpty()) {
            int startLine = stack.removeLast();
            int nextIfLine = 0;
            while (iterator.hasNext()) {
                int next = iterator.next();
                if (next > startLine) {
                    nextIfLine = next;
                    iterator.previous();
                    break;
                }
            }

            String suggestion;
            if (nextIfLine > 0) {
                suggestion = "add a matching #endif before line " + nextIfLine;
            } else if (startLine == lineList.size()) {
                suggestion = "remove the #if";
            } else {
                suggestion = "add a matching #endif at the end of the file";
            }
            problems.add(new ParseProblem(
                    "#if has no matching #endif. Suggestion: " + suggestion + ".", Severity.ERROR, startLine));
        }
        return problems;
    }

    public List<String> extractVariableNames(Stream<String> lines) {
        return lines.flatMap(new VariableNames())
                .distinct()
                .map(Variable::getName)
                .collect(Collectors.toList());
    }

    /**
     * {@return a problem for each annotation with a syntactically invalid condition, including its line number}
     *
     * @param lines the line stream
     */
    public List<ParseProblem> validate(Stream<String> lines) {
        List<ParseProblem> problems = new ArrayList<>();

        Iterator<String> it = lines.iterator();
        int lineNumber = 0;
        while (it.hasNext()) {
            String line = it.next();
            lineNumber++;

            Matcher matcher = annotationPattern.matcher(line);
            if (!matcher.matches()) continue;

            if (matcher.group(IF_GROUP) != null) {
                problems.addAll(checkCondition(matcher.group(IF_CONDITION_GROUP), lineNumber));
            } else if (matcher.group(ELIF_GROUP) != null) {
                problems.addAll(checkCondition(matcher.group(ELIF_CONDITION_GROUP), lineNumber));
            }
        }

        return problems;
    }

    private List<ParseProblem> checkCondition(String condition, int lineNumber) {
        Result<IExpression> parse = annotationParser.parse(condition);

        if (!parse.isPresent()) {
            return parse.getProblems().stream()
                    .map(p -> new ParseProblem(p.getMessage(), p.getSeverity(), lineNumber))
                    .toList();
        } else if (!(parse.get() instanceof IFormula)) {
            return List.of(new ParseProblem(
                    String.format("condition is not a boolean formula: \"%s\"", condition),
                    Problem.Severity.ERROR,
                    lineNumber));
        }
        return List.of();
    }

    /**
     * {@return a problem for each feature used in an annotation that does not occur in the feature model, including its line number}
     *
     * @param lines the line stream
     * @param featureModel the feature model
     */
    public List<ParseProblem> findUnknownFeatures(Stream<String> lines, IFormula featureModel) {
        Set<String> features = featureModel.getVariableNames();
        List<ParseProblem> problems = new ArrayList<>();
        List<String> lineList = lines.toList();
        for (int i = 0; i < lineList.size(); i++) {
            Matcher matcher = startAnnotationPattern.matcher(lineList.get(i));
            if (matcher.matches()) {
                int lineNumber = i + 1;
                annotationParser.parse(matcher.group(2)).ifPresent(expression -> expression.getVariableNames().stream()
                        .filter(name -> !features.contains(name))
                        .forEach(name -> problems.add(new ParseProblem(
                                String.format("unknown feature \"%s\"", name), Severity.ERROR, lineNumber))));
            }
        }
        return problems;
    }

    public List<String> extractAnnotations(Stream<String> lines) {
        return lines.filter(annotationPattern.asMatchPredicate()).collect(Collectors.toList());
    }
}
