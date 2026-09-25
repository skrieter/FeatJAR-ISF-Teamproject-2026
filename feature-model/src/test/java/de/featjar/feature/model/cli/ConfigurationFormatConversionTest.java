/*
 * Copyright (C) 2026 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-feature-model.
 *
 * feature-model is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * feature-model is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with feature-model. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatureIDE/FeatJAR-feature-model> for further information.
 */
package de.featjar.feature.model.cli;

import de.featjar.base.FeatJAR;
import de.featjar.base.data.Result;
import de.featjar.base.io.IO;
import de.featjar.base.io.format.IFormat;
import de.featjar.formula.assignment.BooleanAssignmentGroups;
import de.featjar.formula.io.binary.BooleanAssignmentGroupsGroupedBinaryFormat;
import de.featjar.formula.io.binary.BooleanAssignmentGroupsSimpleBinaryFormat;
import de.featjar.formula.io.csv.BooleanAssignmentGroupsCSVFormat;
import de.featjar.formula.io.csv.BooleanAssignmentGroupsGroupedCSVFormat;
import de.featjar.formula.io.dimacs.BooleanAssignmentGroupsDimacsFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author Kilian Hüppe
 * @author Knut Köhnlein
 */
public class ConfigurationFormatConversionTest {

    @Test
    void convertToSimpleBinary() throws IOException {
        writeAndCompare("solutions_dimacs.dimacs", "solutions_simple_binary.bin", "SimpleBinary");
        writeAndCompare("solutions_grouped_binary.bin", "solutions_simple_binary.bin", "SimpleBinary");
        writeAndCompare("solutions_grouped_csv.csv", "solutions_simple_binary.bin", "SimpleBinary");
        writeAndCompare("solutions_groups_grouped_csv.csv", "solutions_simple_binary.bin", "SimpleBinary");
        writeAndCompare("solutions_simple_binary.bin", "solutions_simple_binary.bin", "SimpleBinary");
        writeAndCompare("solutions_simple_csv.csv", "solutions_simple_binary.bin", "SimpleBinary");
    }

    @Test
    void convertToGroupedBinary() throws IOException {
        writeAndCompare("solutions_dimacs.dimacs", "solutions_grouped_binary.bin", "GroupedBinary");
        writeAndCompare("solutions_grouped_binary.bin", "solutions_grouped_binary.bin", "GroupedBinary");
        writeAndCompare("solutions_grouped_csv.csv", "solutions_grouped_binary.bin", "GroupedBinary");
        writeAndCompare("solutions_simple_binary.bin", "solutions_grouped_binary.bin", "GroupedBinary");
        writeAndCompare("solutions_simple_csv.csv", "solutions_grouped_binary.bin", "GroupedBinary");
    }

    @Test
    void convertToSimpleCSV() throws IOException {
        writeAndCompare("solutions_dimacs.dimacs", "solutions_simple_csv.csv", "SimpleCSV");
        writeAndCompare("solutions_grouped_binary.bin", "solutions_simple_csv.csv", "SimpleCSV");
        writeAndCompare("solutions_grouped_csv.csv", "solutions_simple_csv.csv", "SimpleCSV");
        writeAndCompare("solutions_groups_grouped_csv.csv", "solutions_simple_csv.csv", "SimpleCSV");
        writeAndCompare("solutions_simple_binary.bin", "solutions_simple_csv.csv", "SimpleCSV");
        writeAndCompare("solutions_simple_csv.csv", "solutions_simple_csv.csv", "SimpleCSV");
    }

    @Test
    void convertToGroupedCSV() throws IOException {
        writeAndCompare("solutions_dimacs.dimacs", "solutions_grouped_csv.csv", "GroupedCSV");
        writeAndCompare("solutions_grouped_binary.bin", "solutions_grouped_csv.csv", "GroupedCSV");
        writeAndCompare("solutions_grouped_csv.csv", "solutions_grouped_csv.csv", "GroupedCSV");
        writeAndCompare("solutions_groups_grouped_csv.csv", "solutions_groups_grouped_csv.csv", "GroupedCSV");
        writeAndCompare("solutions_simple_binary.bin", "solutions_grouped_csv.csv", "GroupedCSV");
        writeAndCompare("solutions_simple_csv.csv", "solutions_grouped_csv.csv", "GroupedCSV");
    }

    @Test
    void convertToDIMACS() throws IOException {
        writeAndCompare("solutions_dimacs.dimacs", "solutions_dimacs.dimacs", "DIMACS");
        writeAndCompare("solutions_grouped_binary.bin", "solutions_dimacs.dimacs", "DIMACS");
        writeAndCompare("solutions_grouped_csv.csv", "solutions_dimacs.dimacs", "DIMACS");
        writeAndCompare("solutions_groups_grouped_csv.csv", "solutions_groups_dimacs.dimacs", "DIMACS");
        writeAndCompare("solutions_simple_binary.bin", "solutions_dimacs.dimacs", "DIMACS");
        writeAndCompare("solutions_simple_csv.csv", "solutions_dimacs.dimacs", "DIMACS");
    }

    private void writeAndCompare(String input, String output, String format) throws IOException {
        Path tempFile = Files.createTempFile("featJarTest", "");
        int exitCode = FeatJAR.runTest(
                "convert-configuration",
                "--input",
                "src/test/resources/de/featjar/feature/configuration/" + input,
                "--output-format",
                format,
                "--overwrite",
                "--output",
                tempFile.toString());
        Assertions.assertEquals(0, exitCode);
        IFormat<BooleanAssignmentGroups> outputFormat = getFormat(format);
        Result<BooleanAssignmentGroups> expected =
                IO.load(Path.of("src/test/resources/de/featjar/feature/configuration/" + output), outputFormat);
        Result<BooleanAssignmentGroups> actual = IO.load(tempFile, outputFormat);
        Assertions.assertTrue(expected.isPresent(), expected::printProblems);
        Assertions.assertTrue(actual.isPresent(), actual::printProblems);
        // AI-generated: compare parsed configurations, not platform-dependent serialized bytes.
        Assertions.assertEquals(
                expected.get().getVariableMap().getVariableNames(), actual.get().getVariableMap().getVariableNames());
        Assertions.assertEquals(expected.get().getGroups().size(), actual.get().getGroups().size());
        for (int groupIndex = 0; groupIndex < expected.get().getGroups().size(); groupIndex++) {
            var expectedGroup = expected.get().getGroups().get(groupIndex);
            var actualGroup = actual.get().getGroups().get(groupIndex);
            Assertions.assertEquals(expectedGroup.size(), actualGroup.size());
            for (int assignmentIndex = 0; assignmentIndex < expectedGroup.size(); assignmentIndex++) {
                Assertions.assertArrayEquals(
                        expectedGroup.getAll().get(assignmentIndex).get(),
                        actualGroup.getAll().get(assignmentIndex).get());
            }
        }
    }

    private IFormat<BooleanAssignmentGroups> getFormat(String name) {
        return switch (name) {
            case "SimpleBinary" -> new BooleanAssignmentGroupsSimpleBinaryFormat();
            case "GroupedBinary" -> new BooleanAssignmentGroupsGroupedBinaryFormat();
            case "SimpleCSV" -> new BooleanAssignmentGroupsCSVFormat();
            case "GroupedCSV" -> new BooleanAssignmentGroupsGroupedCSVFormat();
            case "DIMACS" -> new BooleanAssignmentGroupsDimacsFormat();
            default -> throw new IllegalArgumentException("Unsupported format: " + name);
        };
    }
}
