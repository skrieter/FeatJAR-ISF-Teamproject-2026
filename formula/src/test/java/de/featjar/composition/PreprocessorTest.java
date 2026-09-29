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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import de.featjar.Common;
import de.featjar.base.FeatJAR;
import de.featjar.base.cli.OptionList;
import de.featjar.base.data.Problem;
import de.featjar.base.data.Problem.Severity;
import de.featjar.base.io.format.ParseProblem;
import de.featjar.base.tree.Trees;
import de.featjar.composition.cli.PreprocessorCommand;
import de.featjar.formula.assignment.Assignment;
import de.featjar.formula.io.textual.ExpressionSerializer;
import de.featjar.formula.io.textual.JavaSymbols;
import de.featjar.formula.io.textual.ShortSymbols;
import de.featjar.formula.structure.IExpression;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

/***
 * added the unit test
 * Tests {@link Preprocessor#computePresenceConditions(java.util.stream.Stream)}.
 */
public class PreprocessorTest extends Common {

    @BeforeAll
    public static void begin() {
        FeatJAR.testConfiguration().initialize();
    }

    @AfterAll
    public static void end() {
        FeatJAR.deinitialize();
    }

    @Test
    public void nestedAnnotations() {
        List<String> lines = List.of(
                "//#if A",
                "  System.out.println(\"\");",
                "//#if B",
                "  System.out.println(\"\");",
                "  System.out.println(\"\");",
                "//#else",
                "  System.out.println(\"\");",
                "//#endif",
                "  System.out.println(\"\");",
                "//#endif",
                "  System.out.println(\"\");");
        assertEquals(
                List.of("false", "A", "false", "A && B", "A && B", "false", "A && !B", "false", "A", "false", "true"),
                presenceConditions(lines));
    }

    @Test
    public void elifAnnotations() {
        List<String> lines = List.of(
                "//#if A",
                "  System.out.println(\"\");",
                "//#elif B",
                "  System.out.println(\"\");",
                "//#else",
                "  System.out.println(\"\");",
                "//#endif",
                "  System.out.println(\"\");");
        assertEquals(
                List.of(
                        "false", // //#if A
                        "A", // inside #if A
                        "false", // //#elif B
                        "!A && B", // inside #elif B
                        "false", // //#else
                        "!A && !B", // inside #else
                        "false", // //#endif
                        "true"), // outside all blocks
                presenceConditions(lines));
    }

    @Test
    public void noAnnotations() {
        assertEquals(List.of("true", "true"), presenceConditions(List.of("int a;", "int b;")));
    }

    @Test
    public void cppStyle() {
        List<String> lines = List.of("#if A", "a", "#elif B", "b", "#else", "c", "#endif");
        Preprocessor preprocessor = new Preprocessor(Preprocessor.Style.CPP);
        assertEquals(
                List.of("false", "A", "false", "!A && B", "false", "!A && !B", "false"),
                presenceConditions(preprocessor, lines));
        assertEquals(List.of("b"), preprocess(preprocessor, lines, new Assignment("A", false, "B", true)));
        assertEquals(List.of("A", "B"), preprocessor.extractVariableNames(lines.stream()));
    }

    @Test
    public void antennaStyle() {
        List<String> lines = List.of("//#if A && B", "a", "//#else", "b", "//#endif");
        Preprocessor preprocessor = new Preprocessor(Preprocessor.Style.ANTENNA);
        assertEquals(
                List.of("false", "A && B", "false", "!(A && B)", "false"), presenceConditions(preprocessor, lines));
        assertEquals(List.of("a"), preprocess(preprocessor, lines, new Assignment("A", true, "B", true)));
    }

    @Test
    public void mungeStyle() {
        List<String> lines = List.of("/*if[A]*/", "a", "/*else[A]*/", "b", "/*end[A]*/", "c");
        Preprocessor preprocessor = new Preprocessor(Preprocessor.Style.MUNGE);
        assertEquals(List.of("false", "A", "false", "!A", "false", "true"), presenceConditions(preprocessor, lines));
        assertEquals(List.of("b", "c"), preprocess(preprocessor, lines, new Assignment("A", false)));
        assertEquals(List.of("A"), preprocessor.extractVariableNames(lines.stream()));
        assertEquals(
                List.of("/*if[A]*/", "/*else[A]*/", "/*end[A]*/"), preprocessor.extractAnnotations(lines.stream()));
        assertEquals(List.of(), preprocessor.checkStructure(lines.stream()));
    }

    @Test
    public void mungeStyleIgnoresOtherStyles() {
        List<String> lines = List.of("#if A", "/* comment */", "//#endif");
        assertEquals(List.of(), new Preprocessor(Preprocessor.Style.MUNGE).extractAnnotations(lines.stream()));
    }

    @Test
    public void customStyle() {
        Preprocessor.Style style = new Preprocessor.Style(
                "<!--", "-->", "IF", "ELSEIF", "ELSE", "END", " ", "", false, JavaSymbols.INSTANCE);
        List<String> lines = List.of("<!-- IF A -->", "a", "<!-- ELSEIF B -->", "b", "<!-- END -->");
        Preprocessor preprocessor = new Preprocessor(style);
        assertEquals(List.of("false", "A", "false", "!A && B", "false"), presenceConditions(preprocessor, lines));
        assertEquals(List.of("b"), preprocess(preprocessor, lines, new Assignment("A", false, "B", true)));
        assertEquals(List.of(), preprocessor.checkSyntax(lines.stream()));
        assertEquals(List.of(), preprocessor.validate(lines.stream()));
    }

    @Test
    public void styleTextMatchesWhitespaceLiterally() {
        Preprocessor.Style style = new Preprocessor.Style(
                "< start >",
                "< end >",
                "IF FEATURE",
                "ELIF FEATURE",
                "ELSE BRANCH",
                "END BLOCK",
                " WHEN [",
                "] DONE ",
                false,
                JavaSymbols.INSTANCE);
        Preprocessor preprocessor = new Preprocessor(style);
        List<String> annotations = List.of(
                "< start >IF FEATURE WHEN [A] DONE < end >",
                "< start >ELIF FEATURE WHEN [B] DONE < end >",
                "< start >ELSE BRANCH< end >",
                "< start >END BLOCK< end >");
        assertEquals(annotations, preprocessor.extractAnnotations(annotations.stream()));
        assertEquals(List.of("A", "B"), preprocessor.extractVariableNames(annotations.stream()));
        assertEquals(List.of(), preprocessor.checkSyntax(annotations.stream()));
        assertEquals(List.of(), preprocessor.validate(annotations.stream()));

        for (String text : List.of(
                "< start >", "< end >", "IF FEATURE", "ELIF FEATURE", "ELSE BRANCH", "END BLOCK", "WHEN [", "] DONE")) {
            for (String whitespace : List.of("  ", "\t")) {
                List<String> changedAnnotations = annotations.stream()
                        .filter(line -> line.contains(text))
                        .map(line -> line.replace(text, text.replace(" ", whitespace)))
                        .collect(Collectors.toList());
                assertEquals(
                        List.of(),
                        preprocessor.extractAnnotations(changedAnnotations.stream()),
                        changedAnnotations.toString());
            }
        }
    }

    @Test
    public void conditionSeparatorMatchesWhitespaceLiterally() {
        Preprocessor preprocessor = new Preprocessor(Preprocessor.Style.CPP);
        List<ParseProblem> problems = preprocessor.checkSyntax(Stream.of("#if\tA", "#elif\tB"));
        assertEquals(2, problems.size());
        for (int i = 0; i < problems.size(); i++) {
            assertEquals(Severity.ERROR, problems.get(i).getSeverity());
            assertEquals(i + 1, problems.get(i).getLineNumber());
        }
        assertEquals(List.of(), preprocessor.extractAnnotations(Stream.of("#if\tA", "#elif\tB")));
    }

    @Test
    public void wrongStyleRecognizesNoAnnotations() {
        List<Preprocessor.Style> styles =
                List.of(Preprocessor.Style.CPP, Preprocessor.Style.ANTENNA, Preprocessor.Style.MUNGE);
        List<List<String>> files = List.of(
                List.of("#if A", "a", "#else", "b", "#endif"),
                List.of("//#if A", "a", "//#else", "b", "//#endif"),
                List.of("/*if[A]*/", "a", "/*else[A]*/", "b", "/*end[A]*/"));
        for (int i = 0; i < styles.size(); i++) {
            Preprocessor preprocessor = new Preprocessor(styles.get(i));
            for (int j = 0; j < files.size(); j++) {
                if (i == j) continue;
                List<String> lines = files.get(j);
                assertEquals(List.of(), preprocessor.extractAnnotations(lines.stream()));
                assertEquals(List.of(), preprocessor.extractVariableNames(lines.stream()));
                assertEquals(List.of(), preprocessor.checkSyntax(lines.stream()));
                assertEquals(List.of(), preprocessor.validate(lines.stream()));
                assertEquals(List.of(), preprocessor.checkStructure(lines.stream()));
                assertEquals(List.of("true", "true", "true", "true", "true"), presenceConditions(preprocessor, lines));
                assertEquals(lines, preprocess(preprocessor, lines, new Assignment("A", false)));
            }
        }
    }

    @Test
    public void wrongStyleWithSamePrefixReportsSyntaxErrors() {
        List<String> lines = List.of("/*if[A]*/", "a", "/*else[A]*/", "b", "/*end[A]*/");
        Preprocessor preprocessor = new Preprocessor(Preprocessor.Style.CPP.withPrefix("/*"));
        List<ParseProblem> problems = preprocessor.checkSyntax(lines.stream());
        assertEquals(3, problems.size());
        for (int i = 0; i < problems.size(); i++) {
            assertEquals(Severity.ERROR, problems.get(i).getSeverity());
            assertEquals(2 * i + 1, problems.get(i).getLineNumber());
            assertTrue(problems.get(i).getMessage().startsWith("Invalid annotation syntax:"));
        }
        assertEquals(List.of(), preprocessor.extractAnnotations(lines.stream()));
    }

    @Test
    public void wrongStyleReportsUnbalancedAnnotations() {
        List<String> lines = List.of("#if A", "a", "//#endif");
        Preprocessor preprocessor = new Preprocessor(Preprocessor.Style.ANTENNA);
        List<Problem> problems = preprocessor.checkStructure(lines.stream());
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).getMessage().startsWith("#endif without #if"));
        assertEquals(Severity.ERROR, problems.get(0).getSeverity());
        assertEquals(3, ((ParseProblem) problems.get(0)).getLineNumber());
        assertThrows(IllegalArgumentException.class, () -> preprocessor.computePresenceConditions(lines.stream()));
    }

    @Test
    public void wrongStyleReportsMissingEndif() {
        List<String> lines = List.of("#if A", "a", "//#endif");
        Preprocessor preprocessor = new Preprocessor(Preprocessor.Style.CPP);
        List<Problem> problems = preprocessor.checkStructure(lines.stream());
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).getMessage().startsWith("#if has no matching #endif"));
        assertEquals(Severity.ERROR, problems.get(0).getSeverity());
        assertEquals(1, ((ParseProblem) problems.get(0)).getLineNumber());
    }

    @Test
    public void prefixAndSuffixWithSymbolCharacters() {
        Preprocessor.Style style =
                new Preprocessor.Style("(*", "*)", "if", "elif", "else", "endif", " ", "", false, JavaSymbols.INSTANCE);
        List<String> lines = List.of("(* if !(A && B) *)", "a", "(* elif A || B *)", "b", "(* endif *)");
        Preprocessor preprocessor = new Preprocessor(style);
        assertEquals(List.of("A", "B"), preprocessor.extractVariableNames(lines.stream()));
        assertEquals(List.of("a"), preprocess(preprocessor, lines, new Assignment("A", false, "B", false)));
        assertEquals(List.of("b"), preprocess(preprocessor, lines, new Assignment("A", true, "B", true)));

        List<String> negationPrefix = List.of("!if !A", "a", "!endif");
        assertEquals(
                List.of("a"),
                preprocess(new Preprocessor("!", JavaSymbols.INSTANCE), negationPrefix, new Assignment("A", false)));
    }

    @Test
    public void formulaOperatorsAsPrefixAndSuffix() {
        for (String delimiter : List.of("!", "&&", "||", "==", "(", ")")) {
            Preprocessor.Style style = new Preprocessor.Style(
                    delimiter, delimiter, "if", "elif", "else", "endif", " ", "", false, JavaSymbols.INSTANCE);
            Preprocessor preprocessor = new Preprocessor(style);
            List<String> lines = List.of(
                    delimiter + "if !(A && B) || C" + delimiter,
                    "a",
                    delimiter + "else" + delimiter,
                    "b",
                    delimiter + "endif" + delimiter,
                    "c");
            assertEquals(List.of("A", "B", "C"), preprocessor.extractVariableNames(lines.stream()), delimiter);
            assertEquals(List.of(), preprocessor.checkSyntax(lines.stream()), delimiter);
            assertEquals(List.of(), preprocessor.validate(lines.stream()), delimiter);
            assertEquals(List.of(), preprocessor.checkStructure(lines.stream()), delimiter);
            assertEquals(
                    List.of("a", "c"),
                    preprocess(preprocessor, lines, new Assignment("A", false, "B", true, "C", false)),
                    delimiter);
            assertEquals(
                    List.of("b", "c"),
                    preprocess(preprocessor, lines, new Assignment("A", true, "B", true, "C", false)),
                    delimiter);
        }
    }

    @Test
    public void emptyPrefixIsRejected() {
        IllegalArgumentException constructorException =
                assertThrows(IllegalArgumentException.class, () -> new Preprocessor("", JavaSymbols.INSTANCE));
        assertEquals("annotation prefix must not be empty", constructorException.getMessage());
        IllegalArgumentException styleException = assertThrows(
                IllegalArgumentException.class,
                () -> new Preprocessor.Style(
                        "", "", "if", "elif", "else", "endif", " ", "", false, JavaSymbols.INSTANCE));
        assertEquals("annotation prefix must not be empty", styleException.getMessage());
        for (Preprocessor.Style style :
                List.of(Preprocessor.Style.CPP, Preprocessor.Style.ANTENNA, Preprocessor.Style.MUNGE)) {
            IllegalArgumentException prefixException =
                    assertThrows(IllegalArgumentException.class, () -> style.withPrefix(""));
            assertEquals("annotation prefix must not be empty", prefixException.getMessage());
        }
    }

    @Test
    public void featureNotInModelIsReported() {
        List<ParseProblem> problems = new Preprocessor("//#", JavaSymbols.INSTANCE)
                .findUnknownFeatures(
                        Stream.of(
                                "//#if A",
                                "  System.out.println(\"\");",
                                "//#else",
                                "  System.out.println(\"\");",
                                "//#endif"),
                        loadFormula("GPL/model.xml"));

        assertEquals(1, problems.size());
        assertEquals("unknown feature \"A\"", problems.get(0).getMessage());
        assertEquals(Severity.ERROR, problems.get(0).getSeverity());
        assertEquals(1, problems.get(0).getLineNumber());
    }

    @Test
    public void knownFeaturesAreNotReported() {
        assertTrue(unknownFeatures(
                        "//#if Directed && !Weighted",
                        "a();",
                        "//#elif BFS || DFS",
                        "b();",
                        "//#else",
                        "c();",
                        "//#endif")
                .isEmpty());
    }

    @Test
    public void noAnnotationsHaveNoUnknownFeatures() {
        assertTrue(unknownFeatures("int x = 1;", "System.out.println(x);").isEmpty());
    }

    @Test
    public void unknownFeatureInElifIsReported() {
        assertEquals(
                List.of("line 3: unknown feature \"B\""),
                unknownFeatures("//#if Directed", "a();", "//#elif Undirected && B", "b();", "//#endif"));
    }

    @Test
    public void everyUnknownFeatureOfAnAnnotationIsReported() {
        assertEquals(
                List.of("line 1: unknown feature \"A\"", "line 1: unknown feature \"B\""),
                unknownFeatures("//#if A && Base || B", "a();", "//#endif"));
    }

    @Test
    public void unknownFeatureInNestedAnnotationIsReported() {
        assertEquals(
                List.of("line 3: unknown feature \"C\""),
                unknownFeatures("//#if Base", "a();", "//#if C", "b();", "//#endif", "//#endif"));
    }

    @Test
    public void sameUnknownFeatureIsReportedOnEveryLine() {
        assertEquals(
                List.of("line 1: unknown feature \"A\"", "line 4: unknown feature \"A\""),
                unknownFeatures("//#if A", "a();", "//#endif", "//#if !A", "b();", "//#endif"));
    }

    @Test
    public void partialConfigurationReducesUndecidedAnnotations() {
        assertEquals(
                List.of("//#if B", "a();", "//#endif", "c();"),
                preprocess(
                        new Assignment("A", true),
                        "//#if A && B",
                        "a();",
                        "//#endif",
                        "//#if !A",
                        "b();",
                        "//#else",
                        "c();",
                        "//#endif"));
    }

    @Test
    public void partialConfigurationPreservesCustomStyle() {
        Preprocessor preprocessor = new Preprocessor(new Preprocessor.Style(
                "<%", "%>", "WHEN", "ORWHEN", "OTHERWISE", "END", "[", "]", false, JavaSymbols.INSTANCE));
        List<String> output = preprocessor
                .preprocess(
                        Stream.of(
                                "<%WHEN[A]%>",
                                "a();",
                                "<%ORWHEN[B]%>",
                                "b();",
                                "<%ORWHEN[C]%>",
                                "c();",
                                "<%ORWHEN[D]%>",
                                "d();",
                                "<%END%>"),
                        new Assignment("A", false, "D", true),
                        true)
                .collect(Collectors.toList());
        assertEquals(
                List.of("<%WHEN[B]%>", "b();", "<%ORWHEN[C]%>", "c();", "<%OTHERWISE%>", "d();", "<%END%>"), output);
        assertTrue(preprocessor.checkSyntax(output.stream()).isEmpty());
        assertEquals(List.of("d();"), preprocess(preprocessor, output, new Assignment("B", false, "C", false)));
    }

    @Test
    public void partialConfigurationPreservesMungeStyle() {
        Preprocessor preprocessor = new Preprocessor(Preprocessor.Style.MUNGE);
        List<String> output = preprocessor
                .preprocess(
                        Stream.of("/*if[A && B]*/", "a();", "/*else[A && B]*/", "b();", "/*end[A && B]*/"),
                        new Assignment("A", true),
                        true)
                .collect(Collectors.toList());
        assertEquals(List.of("/*if[B]*/", "a();", "/*else[A && B]*/", "b();", "/*end[A && B]*/"), output);
        assertEquals(List.of("B"), preprocessor.extractVariableNames(output.stream()));
        assertEquals(List.of("b();"), preprocess(preprocessor, output, new Assignment("B", false)));
    }

    @Test
    public void partialConfigurationRewritesElifChains() {
        assertEquals(
                List.of("//#if B", "b();", "//#else", "c();", "//#endif"),
                preprocess(
                        new Assignment("A", false, "C", true),
                        "//#if A",
                        "a();",
                        "//#elif B",
                        "b();",
                        "//#elif C || D",
                        "c();",
                        "//#else",
                        "d();",
                        "//#endif"));
    }

    @Test
    public void partialConfigurationKeepsNestedUndecidedAnnotations() {
        assertEquals(
                List.of("//#if B", "//#if !C", "a();", "//#endif", "//#endif"),
                preprocess(
                        new Assignment("A", true),
                        "//#if A",
                        "//#if B",
                        "//#if !C",
                        "a();",
                        "//#endif",
                        "//#endif",
                        "//#endif",
                        "//#if !A",
                        "b();",
                        "//#endif"));
    }

    @Test
    public void partialConfigurationSimplifiesNestedAnnotationsWithTheirContext() {
        assertEquals(
                List.of("//#if A", "//#if B", "a();", "//#endif", "//#else", "c();", "//#endif"),
                preprocess(
                        new Assignment(),
                        "//#if A",
                        "//#if A && B",
                        "a();",
                        "//#endif",
                        "//#if !A",
                        "b();",
                        "//#endif",
                        "//#elif !A",
                        "c();",
                        "//#endif"));
    }

    @Test
    public void partialConfigurationRemovesAnnotationsImpliedByTheirContext() {
        assertEquals(
                List.of("//#if A || B", "a();", "//#if C", "c();", "//#endif", "//#endif"),
                preprocess(
                        new Assignment(),
                        "//#if A || B",
                        "//#if A || B",
                        "a();",
                        "//#endif",
                        "//#if !(A || B) || C",
                        "c();",
                        "//#endif",
                        "//#elif A || B",
                        "b();",
                        "//#endif"));
    }

    @Test
    public void partialConfigurationRestoresCompoundAssumptionsAcrossBranchesAndBlocks() {
        assertEquals(
                List.of("//#if A && B", "a();", "//#else", "b();", "//#endif", "//#if A && B", "c();", "//#endif"),
                preprocess(
                        new Assignment(),
                        "//#if A && B",
                        "a();",
                        "//#elif A && B",
                        "unreachable();",
                        "//#else",
                        "//#if !(A && B)",
                        "b();",
                        "//#endif",
                        "//#endif",
                        "//#if A && B",
                        "c();",
                        "//#endif"));
    }

    @Test
    public void partialConfigurationUsesCompoundConditionsImpliedByConjunctions() {
        assertEquals(
                List.of("//#if A && (B || C)", "a();", "//#endif"),
                preprocess(new Assignment(), "//#if A && (B || C)", "//#if B || C", "a();", "//#endif", "//#endif"));
    }

    @Test
    public void partialConfigurationInfersOperandsOnlyFromFalseImplications() {
        Preprocessor preprocessor = new Preprocessor("//#", ShortSymbols.INSTANCE);
        assertEquals(
                List.of("//#if A => B", "//#if A", "a();", "//#endif", "//#else", "b();", "//#endif"),
                preprocessor
                        .preprocess(
                                Stream.of(
                                        "//#if A => B",
                                        "//#if A",
                                        "a();",
                                        "//#endif",
                                        "//#elif A => B",
                                        "unreachable();",
                                        "//#else",
                                        "//#if A & -B",
                                        "b();",
                                        "//#endif",
                                        "//#endif"),
                                new Assignment(),
                                true)
                        .collect(Collectors.toList()));
        assertEquals(
                List.of("//#if -(A | B => C & D)", "c();", "//#endif"),
                preprocessor
                        .preprocess(
                                Stream.of(
                                        "//#if -((A | B) => (C & D))",
                                        "//#if (A | B) & -(C & D)",
                                        "c();",
                                        "//#endif",
                                        "//#endif"),
                                new Assignment(),
                                true)
                        .collect(Collectors.toList()));
    }

    @Test
    public void partialConfigurationPreservesEveryCompletionForBooleanOperators() {
        Preprocessor preprocessor = new Preprocessor("//#", ShortSymbols.INSTANCE);
        ExpressionParser parser = new ExpressionParser();
        parser.setSymbols(ShortSymbols.INSTANCE);
        List<String> conditions = List.of(
                "A & B",
                "A | B",
                "-(A | -B)",
                "A => B",
                "A <=> B",
                "-(A => B)",
                "-(A <=> B)",
                "(A | B) => (A & B)",
                "(A & B) | A");
        for (String outer : conditions) {
            IExpression outerFormula = parser.parse(outer).orElseThrow();
            for (String inner : conditions) {
                IExpression innerFormula = parser.parse(inner).orElseThrow();
                List<String> source = List.of(
                        "//#if " + outer,
                        "//#if " + inner,
                        "both();",
                        "//#else",
                        "outer();",
                        "//#endif",
                        "//#elif " + inner,
                        "inner();",
                        "//#else",
                        "neither();",
                        "//#endif");
                for (Assignment partial :
                        List.of(new Assignment(), new Assignment("A", true), new Assignment("A", false))) {
                    List<String> output = preprocessor
                            .preprocess(source.stream(), partial, true)
                            .toList();
                    for (boolean a : List.of(false, true)) {
                        if (partial.getValue("A").isPresent()
                                && !partial.getValue("A").get().equals(a)) {
                            continue;
                        }
                        for (boolean b : List.of(false, true)) {
                            Assignment complete = new Assignment("A", a, "B", b);
                            boolean outerValue =
                                    (Boolean) outerFormula.evaluate(complete).orElseThrow();
                            boolean innerValue =
                                    (Boolean) innerFormula.evaluate(complete).orElseThrow();
                            String expected = outerValue
                                    ? (innerValue ? "both();" : "outer();")
                                    : (innerValue ? "inner();" : "neither();");
                            assertEquals(
                                    List.of(expected),
                                    preprocess(preprocessor, output, complete),
                                    outer + " / " + inner + " / A=" + a + ", B=" + b + " / " + output);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void partialConfigurationInfersVariablesFromNegatedDisjunctions() {
        assertEquals(
                List.of("//#if !(A || B)", "a();", "//#if C", "b();", "//#endif", "//#endif"),
                preprocess(
                        new Assignment(),
                        "//#if !(A || B)",
                        "//#if !A && !B",
                        "a();",
                        "//#endif",
                        "//#if !A && C",
                        "b();",
                        "//#endif",
                        "//#endif"));
    }

    @Test
    public void partialConfigurationRestoresAssignmentsAcrossBranches() {
        assertEquals(
                List.of(
                        "//#if A",
                        "a();",
                        "//#elif B",
                        "b();",
                        "//#else",
                        "c();",
                        "//#endif",
                        "//#if A || B",
                        "d();",
                        "//#endif"),
                preprocess(
                        new Assignment(),
                        "//#if A",
                        "//#if A",
                        "a();",
                        "//#endif",
                        "//#elif B",
                        "//#if !A && B",
                        "b();",
                        "//#endif",
                        "//#else",
                        "//#if !A && !B",
                        "c();",
                        "//#endif",
                        "//#endif",
                        "//#if A || B",
                        "d();",
                        "//#endif"));
    }

    @Test
    public void partialConfigurationSimplifiesEquivalence() {
        assertEquals(
                List.of("//#if B", "a();", "//#endif"),
                preprocess(new Assignment("A", true), "//#if A == B", "a();", "//#endif"));
        assertEquals(
                List.of("//#if !B", "a();", "//#endif"),
                preprocess(new Assignment("A", false), "//#if A == B", "a();", "//#endif"));
    }

    @Test
    public void undecidedAnnotationsFailWithoutPartialProcessing() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> new Preprocessor("//#", JavaSymbols.INSTANCE)
                        .preprocess(Stream.of("//#if A", "a();", "//#endif"), new Assignment())
                        .collect(Collectors.toList()));
        assertEquals("Line 1: could not evaluate annotation: //#if A", error.getMessage());
    }

    @Test
    public void undecidedAnnotationsInExcludedBranchesAreSkipped() {
        assertEquals(
                List.of("b();"),
                new Preprocessor("//#", JavaSymbols.INSTANCE)
                        .preprocess(
                                Stream.of("//#if A", "//#if B", "a();", "//#endif", "//#else", "b();", "//#endif"),
                                new Assignment("A", false))
                        .collect(Collectors.toList()));
    }

    @Test
    public void processModeAllowsPartialConfigurationsWithFlag(@TempDir Path directory) throws IOException {
        Path input = Files.write(directory.resolve("input.java"), List.of("#if A && B", "a();", "#endif"));
        Path configuration = Files.writeString(directory.resolve("config.h"), "#define A\n");
        Path output = directory.resolve("output.java");
        PreprocessorCommand command = new PreprocessorCommand();
        for (boolean allowPartial : List.of(false, true)) {
            List<String> arguments = new ArrayList<>(List.of(
                    "--input", input.toString(),
                    "--configuration", configuration.toString(),
                    "--output", output.toString()));
            if (allowPartial) {
                arguments.add("--allow-partial");
            }
            OptionList options = new OptionList(command.getOptions(), arguments);
            assertTrue(options.parseArguments().isEmpty());
            assertEquals(allowPartial ? 0 : 1, command.run(options));
        }
        assertEquals(List.of("#if B", "a();", "#endif"), Files.readAllLines(output));
    }

    @Test
    public void commandClosesInputStreamsInEveryMode(@TempDir Path directory) throws IOException {
        Path input = Files.write(directory.resolve("input.java"), List.of("#if A", "a();", "#endif"));
        Path configuration = Files.writeString(directory.resolve("config.h"), "#define A\n");
        Path featureModel = Files.writeString(directory.resolve("model.dimacs"), "p cnf 1 1\n1 0\n");
        Path output = directory.resolve("output.txt");
        for (PreprocessorCommand.Mode mode : PreprocessorCommand.Mode.values()) {
            for (PreprocessorCommand.MissingVariables missing : mode == PreprocessorCommand.Mode.PROCESS
                    ? List.of(PreprocessorCommand.MissingVariables.values())
                    : List.of(PreprocessorCommand.MissingVariables.IGNORE)) {
                PreprocessorCommand command = new PreprocessorCommand();
                OptionList options = new OptionList(
                        command.getOptions(),
                        List.of(
                                "--input", input.toString(),
                                "--output", output.toString(),
                                "--configuration", configuration.toString(),
                                "--feature-model", featureModel.toString(),
                                "--mode", mode.name(),
                                "--missing-variables", missing.name()));
                assertTrue(options.parseArguments().isEmpty());
                AtomicInteger opened = new AtomicInteger();
                AtomicInteger closed = new AtomicInteger();
                try (MockedStatic<Files> files = mockInputLines(input, () -> {
                    opened.incrementAndGet();
                    return Stream.of("#if A", "a();", "#endif").onClose(closed::incrementAndGet);
                })) {
                    assertEquals(0, command.run(options), mode.name());
                }
                assertEquals(missing == PreprocessorCommand.MissingVariables.IGNORE ? 1 : 2, opened.get());
                assertEquals(opened.get(), closed.get(), mode + " / " + missing);
            }
        }
    }

    @Test
    public void commandClosesInputStreamsOnFailures(@TempDir Path directory) throws IOException {
        Path input = Files.writeString(directory.resolve("input.java"), "");
        Path configuration = Files.writeString(directory.resolve("config.h"), "#define A\n");
        for (String failure : List.of("read", "write", "undecided")) {
            for (PreprocessorCommand.Mode mode :
                    List.of(PreprocessorCommand.Mode.PROCESS, PreprocessorCommand.Mode.PRINT_VARIABLES)) {
                PreprocessorCommand command = new PreprocessorCommand();
                OptionList options = new OptionList(
                        command.getOptions(),
                        List.of(
                                "--input", input.toString(),
                                "--output",
                                        (failure.equals("write") ? directory : directory.resolve("output.txt"))
                                                .toString(),
                                "--configuration", configuration.toString(),
                                "--mode", mode.name()));
                assertTrue(options.parseArguments().isEmpty());
                AtomicInteger closed = new AtomicInteger();
                try (MockedStatic<Files> files = mockInputLines(input, () -> {
                    Stream<String> lines = failure.equals("read")
                            ? Stream.generate(() -> {
                                throw new UncheckedIOException(new IOException("input failure"));
                            })
                            : Stream.of("#if B", "b();", "#endif");
                    return lines.onClose(closed::incrementAndGet);
                })) {
                    int expected = failure.equals("undecided") && mode != PreprocessorCommand.Mode.PROCESS ? 0 : 1;
                    assertEquals(expected, command.run(options), failure + " / " + mode);
                }
                assertEquals(1, closed.get(), failure + " / " + mode);
            }
        }
    }

    @Test
    public void partialConfigurationKeepsUnparsableAnnotations() {
        assertEquals(
                List.of("//#if a > 0", "a();", "//#else", "b();", "//#endif"),
                preprocess(
                        new Assignment("a", 1, "A", false),
                        "//#if A",
                        "x();",
                        "//#elif a > 0",
                        "a();",
                        "//#else",
                        "b();",
                        "//#endif"));
    }

    @Test
    public void completeConfigurationRemovesAllAnnotations() {
        assertEquals(
                List.of("b();"),
                preprocess(new Assignment("A", false, "B", true), "//#if A", "a();", "//#elif B", "b();", "//#endif"));
    }

    @Test
    public void completeConfigurationKeepsLinesAfterElifBlock() {
        assertEquals(
                List.of("a();", "c();"),
                new Preprocessor("//#", JavaSymbols.INSTANCE)
                        .preprocess(
                                Stream.of("//#if A", "a();", "//#elif B", "b();", "//#endif", "c();"),
                                new Assignment("A", true, "B", false))
                        .collect(Collectors.toList()));
    }

    private static MockedStatic<Files> mockInputLines(Path input, Supplier<Stream<String>> lines) {
        return mockStatic(Files.class, invocation -> {
            if (invocation.getMethod().getName().equals("lines") && input.equals(invocation.getArgument(0))) {
                return lines.get();
            }
            return invocation.callRealMethod();
        });
    }

    private static List<String> preprocess(Assignment assignment, String... lines) {
        return new Preprocessor("//#", JavaSymbols.INSTANCE)
                .preprocess(Stream.of(lines), assignment, true)
                .collect(Collectors.toList());
    }

    private static List<String> unknownFeatures(String... lines) {
        return new Preprocessor("//#", JavaSymbols.INSTANCE)
                .findUnknownFeatures(Stream.of(lines), loadFormula("GPL/model.xml")).stream()
                        .map(p -> String.format("line %d: %s", p.getLineNumber(), p.getMessage()))
                        .collect(Collectors.toList());
    }

    private static List<String> presenceConditions(List<String> lines) {
        return presenceConditions(new Preprocessor("//#", JavaSymbols.INSTANCE), lines);
    }

    private static List<String> presenceConditions(Preprocessor preprocessor, List<String> lines) {
        ExpressionSerializer serializer = new ExpressionSerializer();
        serializer.setSymbols(JavaSymbols.INSTANCE);
        return preprocessor.computePresenceConditions(lines.stream()).stream()
                .map(pc -> Trees.traverse(pc, serializer).orElseThrow())
                .collect(Collectors.toList());
    }

    private static List<String> preprocess(Preprocessor preprocessor, List<String> lines, Assignment assignment) {
        return preprocessor.preprocess(lines.stream(), assignment).collect(Collectors.toList());
    }
}
