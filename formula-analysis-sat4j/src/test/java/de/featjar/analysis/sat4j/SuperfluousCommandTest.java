package de.featjar.analysis.sat4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.featjar.base.FeatJAR;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class SuperfluousCommandTest {

    @Test
    public void readsModelAndWritesResult() throws IOException {
        Path input = Files.createTempFile("annotations", ".txt");
        Path output = Files.createTempFile("superfluous", ".txt");
        Path featureModel = Files.createTempFile("model", ".xml");

        Files.writeString(input, "//#if Base\nalways\n//#endif\n");

        try (InputStream modelStream = getClass().getResourceAsStream("/GPL/model.xml")) {
            if (modelStream == null) {
                throw new IOException("Could not find GPL/model.xml");
            }

            Files.copy(
                    modelStream,
                    featureModel,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        int exitCode = FeatJAR.runTest(
                "preprocessor-sat4j",
                "--input", input.toString(),
                "--feature-model", featureModel.toString(),
                "--annotation-prefix", "//#",
                "--mode", "PRINT_SUPERFLUOUS_ANNOTATIONS",
                "--output", output.toString());

        assertEquals(0, exitCode);
        assertEquals("Line 1: //#if Base" + System.lineSeparator(), Files.readString(output));    }
}
