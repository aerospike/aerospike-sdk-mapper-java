package com.aerospike.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReadmeSnippetSyncTests {
    @TempDir
    Path temp;

    @Test
    void staleWriteCheckAndSecondWriteAreDeterministic() throws Exception {
        Fixture fixture = fixture("""
                class Example {
                    // @start region="sample"
                    void run() {}
                    // @end region="sample"
                }
                """, placeholder("sample", "stale"));

        Result stale = run(fixture, "--check");
        assertNotEquals(0, stale.code);
        assertTrue(stale.output.contains(
                "README snippets are stale; run manually with --write and commit the changes."
                        + " For command details see /examples/sync_readme"));
        assertEquals(0, run(fixture, "--write").code);
        String written = Files.readString(fixture.readme);
        assertTrue(written.contains("```java\nvoid run() {}\n```"));
        assertTrue(written.contains("[Region source](src/Example.java)"));
        assertEquals(0, run(fixture, "--check").code);
        assertEquals(0, run(fixture, "--write").code);
        assertEquals(written, Files.readString(fixture.readme));
    }

    @Test
    void rejectsDuplicateAndMalformedSourceRegions() throws Exception {
        Fixture duplicate = fixture("""
                // @start region="sample"
                one
                // @end region="sample"
                // @start region="sample"
                two
                // @end region="sample"
                """, placeholder("sample", ""));
        assertFailure(duplicate, "Duplicate source region");

        Fixture mismatch = fixture("""
                // @start region="sample"
                one
                // @end region="other"
                """, placeholder("sample", ""));
        assertFailure(mismatch, "Mismatched end region");

        Fixture nested = fixture("""
                // @start region="sample"
                // @start region="nested"
                // @end region="nested"
                // @end region="sample"
                """, placeholder("sample", "") + placeholder("nested", ""));
        assertFailure(nested, "Overlapping regions");
    }

    @Test
    void rejectsMissingDuplicateAndUnusedPlaceholders() throws Exception {
        Fixture missing = fixture("""
                // @start region="sample"
                one
                // @end region="sample"
                """, placeholder("other", ""));
        assertFailure(missing, "Unused source region");

        Fixture duplicate = fixture("""
                // @start region="sample"
                one
                // @end region="sample"
                """, placeholder("sample", "") + placeholder("sample", ""));
        assertFailure(duplicate, "Duplicate README placeholder");

        Fixture unused = fixture("""
                // @start region="sample"
                one
                // @end region="sample"
                // @start region="unused"
                two
                // @end region="unused"
                """, placeholder("sample", ""));
        assertFailure(unused, "Unused source region");
    }

    @Test
    void rejectsUnknownAndConflictingArguments() throws Exception {
        Fixture fixture = fixture("""
                // @start region="sample"
                one
                // @end region="sample"
                """, placeholder("sample", ""));
        assertNotEquals(0, run(fixture, "--unknown").code);
        assertNotEquals(0, run(fixture, "--write", "--check").code);
    }

    private Fixture fixture(String source, String readme) throws IOException {
        Path root = Files.createTempDirectory(temp, "fixture");
        Path sourceRoot = Files.createDirectories(root.resolve("src"));
        Files.writeString(sourceRoot.resolve("Example.java"), source, StandardCharsets.UTF_8);
        Path readmePath = root.resolve("README.md");
        Files.writeString(readmePath, readme, StandardCharsets.UTF_8);
        return new Fixture(sourceRoot, readmePath);
    }

    private Result run(Fixture fixture, String... mode) throws Exception {
        Path tool = Path.of(System.getProperty("user.dir"),
                "examples/tools/ReadmeSnippetSync.java").toAbsolutePath();
        List<String> command = new ArrayList<>(List.of(
                "java", tool.toString(),
                "--source-root", fixture.sourceRoot.toString(),
                "--readme", fixture.readme.toString()));
        command.addAll(Arrays.asList(mode));
        if (mode.length == 0) command.add("--check");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(process.waitFor(), output);
    }

    private void assertFailure(Fixture fixture, String message) throws Exception {
        Result result = run(fixture, "--check");
        assertNotEquals(0, result.code);
        assertTrue(result.output.contains(message), result.output);
    }

    private String placeholder(String name, String body) {
        return "<!-- example-region:" + name + ":start -->\n" + body
                + "\n<!-- example-region:" + name + ":end -->\n";
    }

    private record Fixture(Path sourceRoot, Path readme) {}
    private record Result(int code, String output) {}
}
