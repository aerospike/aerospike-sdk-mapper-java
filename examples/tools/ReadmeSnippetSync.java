import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ReadmeSnippetSync {
    private static final Pattern MARKER = Pattern.compile(
            "^\\s*(?://|#)\\s*@(?<kind>start|end)\\s+region=\"(?<name>[^\"]+)\"\\s*$");
    private static final Pattern ANY_MARKER = Pattern.compile("@(?:start|end)\\b");
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "(?s)<!-- example-region:(?<name>[^:]+):start -->.*?"
                    + "<!-- example-region:\\k<name>:end -->");

    private record Region(String name, String content, Path source) {}

    public static void main(String[] args) {
        try {
            int result = run(args);
            if (result != 0) System.exit(result);
        } catch (Exception e) {
            System.err.println("README snippet sync failed: " + e.getMessage());
            System.exit(1);
        }
    }

    static int run(String[] args) throws IOException {
        boolean write = false;
        boolean explicitCheck = false;
        Path readme = Path.of("README.md");
        List<Path> roots = new ArrayList<>();
        boolean customRoots = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--write" -> write = true;
                case "--check" -> explicitCheck = true;
                case "--readme" -> readme = Path.of(value(args, ++i, "--readme"));
                case "--source-root" -> {
                    if (!customRoots) {
                        roots.clear();
                        customRoots = true;
                    }
                    roots.add(Path.of(value(args, ++i, "--source-root")));
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        if (write && explicitCheck) {
            throw new IllegalArgumentException("--write and --check are mutually exclusive");
        }
        if (!customRoots) {
            roots.add(Path.of("examples/src/main/java"));
            roots.add(Path.of("examples/src/main/resources"));
        }
        if (roots.isEmpty()) throw new IllegalArgumentException("At least one source root is required");

        Map<String, Region> regions = discover(roots);
        String original = Files.readString(readme, StandardCharsets.UTF_8);
        Map<String, Integer> placeholderCounts = placeholderCounts(original);
        for (String name : regions.keySet()) {
            int count = placeholderCounts.getOrDefault(name, 0);
            if (count == 0) throw new IllegalStateException("Unused source region: " + name);
            if (count > 1) throw new IllegalStateException("Duplicate README placeholder: " + name);
        }
        for (Map.Entry<String, Integer> entry : placeholderCounts.entrySet()) {
            if (entry.getValue() > 1) {
                throw new IllegalStateException("Duplicate README placeholder: " + entry.getKey());
            }
            if (!regions.containsKey(entry.getKey())) {
                throw new IllegalStateException("Missing source region: " + entry.getKey());
            }
        }

        String updated = replace(original, regions, readme);
        if (original.equals(updated)) {
            System.out.println("README snippets are current");
            return 0;
        }
        if (!write) {
            System.err.println("README snippets are stale; run manually with --write and commit the changes."
                    + " For command details see /examples/sync_readme");
            return 1;
        }
        Files.writeString(readme, updated, StandardCharsets.UTF_8);
        System.out.println("Updated " + readme);
        return 0;
    }

    private static Map<String, Region> discover(List<Path> roots) throws IOException {
        Map<String, Region> regions = new LinkedHashMap<>();
        for (Path configuredRoot : roots) {
            Path root = configuredRoot.toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                throw new IllegalArgumentException("Source root is not a directory: " + configuredRoot);
            }
            List<Path> files;
            try (Stream<Path> stream = Files.walk(root)) {
                files = stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java")
                                || path.toString().endsWith(".yaml")
                                || path.toString().endsWith(".yml"))
                        .sorted()
                        .toList();
            }
            for (Path file : files) {
                Path normalized = file.toAbsolutePath().normalize();
                if (!normalized.startsWith(root)) {
                    throw new IllegalStateException("Source is outside configured roots: " + file);
                }
                for (Region region : regionsIn(normalized)) {
                    Region prior = regions.putIfAbsent(region.name(), region);
                    if (prior != null) {
                        throw new IllegalStateException("Duplicate source region: " + region.name());
                    }
                }
            }
        }
        return regions;
    }

    private static List<Region> regionsIn(Path source) throws IOException {
        List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
        List<Region> regions = new ArrayList<>();
        String active = null;
        int contentStart = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher marker = MARKER.matcher(line);
            if (!marker.matches()) {
                if (ANY_MARKER.matcher(line).find()) {
                    throw new IllegalStateException("Malformed marker at " + source + ":" + (i + 1));
                }
                continue;
            }
            String kind = marker.group("kind");
            String name = marker.group("name");
            if ("start".equals(kind)) {
                if (active != null) {
                    throw new IllegalStateException("Overlapping regions at " + source + ":" + (i + 1));
                }
                active = name;
                contentStart = i + 1;
            } else {
                if (active == null || !active.equals(name)) {
                    throw new IllegalStateException("Mismatched end region at " + source + ":" + (i + 1));
                }
                regions.add(new Region(name, normalize(lines.subList(contentStart, i)), source));
                active = null;
            }
        }
        if (active != null) throw new IllegalStateException("Unclosed region " + active + " in " + source);
        return regions;
    }

    private static String normalize(List<String> input) {
        int first = 0;
        int last = input.size();
        while (first < last && input.get(first).isBlank()) first++;
        while (last > first && input.get(last - 1).isBlank()) last--;
        int indent = Integer.MAX_VALUE;
        for (String line : input.subList(first, last)) {
            if (!line.isBlank()) indent = Math.min(indent, leadingWhitespace(line));
        }
        if (indent == Integer.MAX_VALUE) return "";
        List<String> normalized = new ArrayList<>();
        for (String line : input.subList(first, last)) {
            normalized.add(line.length() >= indent ? line.substring(indent) : "");
        }
        return String.join("\n", normalized);
    }

    private static int leadingWhitespace(String line) {
        int result = 0;
        while (result < line.length() && Character.isWhitespace(line.charAt(result))) result++;
        return result;
    }

    private static Map<String, Integer> placeholderCounts(String markdown) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile(
                "<!-- example-region:([^:]+):(start|end) -->").matcher(markdown);
        Map<String, int[]> pairs = new LinkedHashMap<>();
        while (matcher.find()) {
            int[] count = pairs.computeIfAbsent(matcher.group(1), ignored -> new int[2]);
            count["start".equals(matcher.group(2)) ? 0 : 1]++;
        }
        for (Map.Entry<String, int[]> entry : pairs.entrySet()) {
            if (entry.getValue()[0] != entry.getValue()[1]) {
                throw new IllegalStateException("Unpaired README placeholder: " + entry.getKey());
            }
            counts.put(entry.getKey(), entry.getValue()[0]);
        }
        return counts;
    }

    private static String replace(String markdown, Map<String, Region> regions, Path readme) {
        List<Region> ordered = regions.values().stream()
                .sorted(Comparator.comparing(Region::name))
                .toList();
        String result = markdown;
        Path base = readme.toAbsolutePath().normalize().getParent();
        for (Region region : ordered) {
            String name = Pattern.quote(region.name());
            Pattern span = Pattern.compile("(?s)<!-- example-region:" + name + ":start -->.*?"
                    + "<!-- example-region:" + name + ":end -->");
            String language = region.source().toString().endsWith(".java") ? "java" : "yaml";
            String link = base.relativize(region.source().toAbsolutePath().normalize())
                    .toString().replace('\\', '/');
            String replacement = "<!-- example-region:" + region.name() + ":start -->\n"
                    + "```" + language + "\n" + region.content() + "\n```\n\n"
                    + "[Region source](" + link + ")\n"
                    + "<!-- example-region:" + region.name() + ":end -->";
            result = span.matcher(result).replaceFirst(Matcher.quoteReplacement(replacement));
        }
        return result;
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) throw new IllegalArgumentException("Missing value for " + option);
        return args[index];
    }
}
