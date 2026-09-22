package de.featjar.analysis.sat4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.featjar.base.FeatJAR;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class SuperfluousCommandTest {

    @Test
    public void readsModelAndWritesResult() throws IOException {
        Path input = Files.createTempFile("annotations", ".txt");
        Path output = Files.createTempFile("superfluous", ".txt");
        Files.writeString(input, "//#if Base\nalways\n//#endif\n");

        int exitCode = FeatJAR.runTest(
                "preprocessor-sat4j",
                "--input", input.toString(),
                "--feature-model", "../formula/src/testFixtures/resources/GPL/model.xml",
                "--annotation-prefix", "//#",
                "--mode", "PRINT_SUPERFLUOUS_ANNOTATIONS",
                "--output", output.toString());

        assertEquals(0, exitCode);
        assertEquals("Line 1: //#if Base\n", Files.readString(output));
    }
}
