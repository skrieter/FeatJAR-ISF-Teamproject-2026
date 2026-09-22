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

import de.featjar.base.FeatJAR;
import de.featjar.base.tree.Trees;
import de.featjar.formula.assignment.Assignment;
import de.featjar.formula.io.textual.ExpressionSerializer;
import de.featjar.formula.io.textual.JavaSymbols;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/***
 * added the unit test
 * Tests {@link Preprocessor#computePresenceConditions(java.util.stream.Stream)}.
 */
public class PreprocessorTest {

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
        assertEquals(
                List.of("false", "A", "false", "!A && B", "false"), presenceConditions(new Preprocessor(style), lines));
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
