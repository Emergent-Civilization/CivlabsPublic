package com.minecraftcivilizations.specialization.Player;

import com.minecraftcivilizations.specialization.Combat.BlacksmithArmorTrim;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * @author Jfrogy
 */

public class LocalNameGenerator implements Listener {

    private List<String> firstNames;
    private List<String> lastNames;
    private final Set<String> usedNames = new java.util.HashSet<>();

    private Map<String, List<String>> grouping = new HashMap<String, List<String>>();
    private final Pattern pattern = Pattern.compile("\\[([^\\]]+)\\]");
    private final Random random = new Random();
    private static File fnFile;
    private static File lnFile;

    BlacksmithArmorTrim blacksmithArmorTrim; //we'll reference this to access things like applyFirstName, etc.

    public LocalNameGenerator(Specialization specialization) throws IOException {
        if (specialization != null) {
            specialization.getServer().getPluginManager().registerEvents(this, specialization);
            blacksmithArmorTrim = specialization.getArmorTrimSystem();
        }
        fnFile = new File(Specialization.getInstance().getDataFolder(), "first_names.txt");
        lnFile = new File(Specialization.getInstance().getDataFolder(), "last_names.txt");
        firstNames = new ArrayList<>();
        lastNames = new ArrayList<>();
        for (String rawLine : Files.readAllLines(fnFile.toPath())) {
            if (rawLine.isEmpty() || rawLine.startsWith("#")) continue;

            // detect optional "(TAG)" and remove it for storage/processing
            java.util.regex.Matcher pm = Pattern.compile("\\(([^)]+)\\)").matcher(rawLine);
            String cleanedLine = rawLine;
            String tag = null;
            if (pm.find()) {
                tag = pm.group(1).trim();
                // remove the (TAG) so it isn't baked into names
                cleanedLine = cleanedLine.replaceAll("\\s*\\([^)]*\\)\\s*", "").trim();
            }

            // store the cleaned line for generation (no (TAG) inside)
            this.firstNames.add(cleanedLine);

            // Now parse and auto-apply mapping when tag is present
            if (tag != null && blacksmithArmorTrim != null) {
                String withoutGroups = cleanedLine.replaceAll("\\s*\\[[^\\]]+\\]\\s*", "").trim();
                List<String> variants = expandBraces(withoutGroups);

                // try as TrimPattern first (first-names likely map to patterns)
                TrimPattern pattern = BlacksmithArmorTrim.getPatternOf(tag);
                if (pattern != null && !variants.isEmpty()) {
                    blacksmithArmorTrim.applyFirstName(pattern, variants.toArray(new String[0]));
                    continue;
                }

                // fallback: try as TrimMaterial
                TrimMaterial material = BlacksmithArmorTrim.getMaterialOf(tag);
                if (material != null && !variants.isEmpty()) {
                    blacksmithArmorTrim.applyLastName(material, variants.toArray(new String[0]));
                }
            }
        }



        for (String line : Files.readAllLines(lnFile.toPath())) {
            if (line.isEmpty() || line.startsWith("#")) continue;

            List<String> matches = getGroupPattern(line);
            // Remove group tags for cleaned processing (we keep original line in grouping or defaults)
            String cleanedLine = line.replaceAll("\\s*\\[[^\\]]+\\]\\s*", "").trim();

            // detect optional (TAG) at end or anywhere
            java.util.regex.Matcher pm = Pattern.compile("\\(([^)]+)\\)").matcher(line);
            String tag = null;
            if (pm.find()) {
                tag = pm.group(1).trim();
                // remove the (TAG) from cleaned form so expandBraces works cleanly
                cleanedLine = cleanedLine.replaceAll("\\s*\\([^)]*\\)\\s*", "").trim();
            }

            if (matches.isEmpty()) {
                // Default (ungrouped) last name
                lastNames.add(cleanedLine);

                // If there's a tag, try to map it
                if (tag != null && blacksmithArmorTrim != null) {
                    List<String> variants = expandBraces(cleanedLine);
                    if (!variants.isEmpty()) {
                        // Prefer material mapping for last names
                        try {
                            TrimMaterial material = BlacksmithArmorTrim.getMaterialOf(tag);
                            blacksmithArmorTrim.applyLastName(material, variants.toArray(new String[0]));
                        } catch (IllegalArgumentException ignored) {
                            // Not a material — try pattern as fallback
                            try {
                                TrimPattern pattern = BlacksmithArmorTrim.getPatternOf(tag);
                                blacksmithArmorTrim.applyFirstName(pattern, variants.toArray(new String[0]));
                            } catch (IllegalArgumentException ignored2) {
                                // unknown tag — ignore
                            }
                        }
                    }
                }

            } else {
                // Grouped entries
                // add cleaned (without [tags] and without (TAG)) into grouping under each group key
                for (String key : matches) {
                    grouping.computeIfAbsent(key, k -> new ArrayList<>());

                    String cleaned = cleanedLine; // already removed [tags] and (TAG)
                    grouping.get(key).add(cleaned);

                    // If there's a (TAG) present, apply mapping for these variants now
                    if (tag != null && blacksmithArmorTrim != null) {
                        List<String> variants = expandBraces(cleaned);
                        if (!variants.isEmpty()) {
                            // Prefer material mapping for last names
                            try {
                                TrimMaterial material = BlacksmithArmorTrim.getMaterialOf(tag);
                                blacksmithArmorTrim.applyLastName(material, variants.toArray(new String[0]));
                            } catch (IllegalArgumentException ignored) {
                                // Not a material — try pattern
                                try {
                                    TrimPattern pattern = BlacksmithArmorTrim.getPatternOf(tag);
                                    blacksmithArmorTrim.applyFirstName(pattern, variants.toArray(new String[0]));
                                } catch (IllegalArgumentException ignored2) {
                                    // unknown tag — ignore
                                }
                            }
                        }
                    }
                }
            }
        }

// Debug output
//        Specialization.logger.info("[Groups]:");
//        for (Map.Entry<String, List<String>> entry : grouping.entrySet()) {
//            Specialization.logger.info(entry.getKey() + ": " + entry.getValue());
//        }
        Specialization.logger.info("[LocalNameGenerator] Loaded " + firstNames.size() + " first names and " + lastNames.size() + " last names");

        // Load existing names from world playerdata to prevent duplicates
        loadExistingNamesFromWorld();
        generateAllNameCombinations();
        loadTotalFromGeneratedFile();

        Specialization.logger.info("[LocalNameGenerator] Initialization complete. " + usedNames.size() + " existing names loaded.");
    }
    private void generateAllNameCombinations() {
        File outputFile = new File(Specialization.getInstance().getDataFolder(), "GeneratedNames.txt");
        if (outputFile.exists()) {
            Specialization.logger.info("[LocalNameGenerator] GeneratedNames.txt already exists, skipping generation.");
            return;
        }

        try {
            List<String> allNames = new ArrayList<>();
            int totalCount = 0;

            // --- Parse first names ---
            List<NameLine> firstLines = new ArrayList<>();
            for (String line : firstNames) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String group = extractGroupTag(line);
                String clean = line.replaceAll("\\s*\\[[^\\]]+\\]\\s*", "").trim();
                List<String> variants = expandBraces(clean);

                firstLines.add(new NameLine(variants, group));
//                System.out.println("[DEBUG FIRST] line=" + line + " | group=" + group + " | variants=" + variants);
            }

            // --- Parse last names (defaults + grouped) ---
            List<NameLine> lastLines = new ArrayList<>();

            // Default ungrouped last names
            for (String line : lastNames) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                List<String> variants = expandBraces(line);
                lastLines.add(new NameLine(variants, null));
            }

            // Grouped last names from grouping map
            for (Map.Entry<String, List<String>> entry : grouping.entrySet()) {
                String group = entry.getKey();
                for (String val : entry.getValue()) {
                    val = val.trim();
                    if (val.isEmpty()) continue;
                    List<String> variants = expandBraces(val);
                    lastLines.add(new NameLine(variants, group));
//                    System.out.println("[DEBUG GROUPED LAST] " + group + " -> " + variants);
                }
            }

            // --- Separate for convenience ---
            List<String> defaultFirsts = firstLines.stream()
                    .filter(l -> l.group == null)
                    .flatMap(l -> l.variants.stream())
                    .toList();

            List<String> defaultLasts = lastLines.stream()
                    .filter(l -> l.group == null)
                    .flatMap(l -> l.variants.stream())
                    .toList();

            // --- Default-first + default-last ---
            for (String f : defaultFirsts) {
                for (String l : defaultLasts) {
                    String name = f + "_" + l;
                    if (name.length() <= 16) {
                        allNames.add(name);
                        totalCount++;
                    }
                }
            }

            // --- Grouped names ---
            Map<String, List<NameLine>> lastLinesByGroup = new HashMap<>();
            for (NameLine l : lastLines) {
                if (l.group != null)
                    lastLinesByGroup.computeIfAbsent(l.group, k -> new ArrayList<>()).add(l);
            }

            for (NameLine firstLine : firstLines) {
                if (firstLine.group == null) continue;

                List<NameLine> matchingLasts = lastLinesByGroup.getOrDefault(firstLine.group, List.of());
                for (String f : firstLine.variants) {
                    for (NameLine lastLine : matchingLasts) {
                        for (String l : lastLine.variants) {
                            String name = f + "_" + l;
                            if (name.length() <= 16) {
                                allNames.add(name);
                                totalCount++;
                            }
                        }
                    }
                }
            }

            // --- Write output ---
            Path path = outputFile.toPath();
            Files.createDirectories(outputFile.getParentFile().toPath());
            allNames.add("Total names: " + totalCount);
            Files.write(path, allNames);

            Specialization.logger.info("[LocalNameGenerator] Generated " + totalCount + " possible name combinations in " + outputFile.getName());
        } catch (IOException e) {
            Specialization.logger.severe("[LocalNameGenerator] Failed to generate all name combinations: " + e.getMessage());
            e.printStackTrace();
        }
    }


    // Helper class for first/last lines
    private static class NameLine {
        List<String> variants;
        String group;

        public NameLine(List<String> variants, String group) {
            this.variants = variants;
            this.group = group;
        }
    }


    /**
     * Extract the group tag inside [ ] for a line, or null if none.
     */
    private String extractGroupTag(String line) {
        int start = line.indexOf('[');
        int end = line.indexOf(']');
        if (start == -1 || end == -1 || end <= start) return null;
        return line.substring(start + 1, end).trim();
    }

    /**
     * Expands a string with {option1|option2|option3} into all options.
     * Example: "{Velvety|Plush|Warm}" => ["Velvety","Plush","Warm"]
     */
    private List<String> expandBraces(String input) {
        List<String> results = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\{([^}]+)\\}").matcher(input);

        if (!matcher.find()) {
            results.add(input);
            return results; // no braces
        }

        String before = input.substring(0, matcher.start());
        String after = input.substring(matcher.end());
        String[] options = matcher.group(1).split("\\|");

        for (String option : options) {
            results.addAll(expandBraces(before + option + after)); // recursive for nested braces
        }

        return results;
    }



    private List<String> getGroupPattern(String string) {
        Matcher matcher = pattern.matcher(string);
        List<String> matches = new ArrayList<>();

        while (matcher.find()) {
            matches.add(matcher.group(1)); // capture content inside []
        }

        return matches;
    }

    /**
     * Loads existing player names from world playerdata files to prevent duplicates
     */
    private void loadExistingNamesFromWorld() {
        try {
            // Get the world's playerdata directory
            File worldContainer = Bukkit.getWorldContainer();

            File[] worldDirs = worldContainer.listFiles(File::isDirectory);

            if (worldDirs != null) {
                for (File worldDir : worldDirs) {
                    File playerdataDir = new File(worldDir, "playerdata");
                    if (playerdataDir.exists() && playerdataDir.isDirectory()) {
                        // Read existing player names from CustomPlayer data
                        loadNamesFromPlayerdata(playerdataDir);
                    }
                }
            }
        } catch (Exception e) {
            Specialization.logger.warning("[LocalNameGenerator] Failed to load existing names from world playerdata: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Loads names from MinecraftCivilizationsCore directory by checking all UUID files
     */
    private void loadNamesFromPlayerdata(File playerdataDir) {
        try {
            // Look for MinecraftCivilizationsCore plugin directory
            File pluginDataDir = new File(Bukkit.getWorldContainer(), "plugins/MinecraftCivilizationsCore");

            if (pluginDataDir.exists() && pluginDataDir.isDirectory()) {
                Specialization.logger.info("[LocalNameGenerator] Scanning MinecraftCivilizationsCore directory: " + pluginDataDir.getAbsolutePath());

                // Get all files in the directory except db.properties
                File[] allFiles = pluginDataDir.listFiles((dir, name) ->
                        !name.equals("db.properties") && !name.startsWith("."));

                if (allFiles != null) {
                    Specialization.logger.info("[LocalNameGenerator] Found " + allFiles.length + " files to scan");

                    int filesProcessed = 0;
                    int namesFound = 0;

                    for (File file : allFiles) {
                        if (file.isFile()) {
                            try {
                                String content = Files.readString(file.toPath());
                                int namesInThisFile = 0;
                                // Look for the name field with nested JSON: "name": "{...}"
                                String namePattern = "\"name\":\\s*\"\\{";
                                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(namePattern);
                                java.util.regex.Matcher matcher = pattern.matcher(content);

                                while (matcher.find()) {
                                    int nameStart = matcher.end() - 1; // Start at the opening brace

                                    // Find the matching closing brace and quote
                                    int braceCount = 0;
                                    int pos = nameStart;
                                    boolean inString = false;
                                    boolean escaped = false;

                                    while (pos < content.length()) {
                                        char c = content.charAt(pos);

                                        if (escaped) {
                                            escaped = false;
                                        } else if (c == '\\') {
                                            escaped = true;
                                        } else if (c == '"' && !escaped) {
                                            inString = !inString;
                                        } else if (!inString) {
                                            if (c == '{') {
                                                braceCount++;
                                            } else if (c == '}') {
                                                braceCount--;
                                                if (braceCount == 0) {
                                                    // Found the end of the JSON object
                                                    pos++; // Include the closing brace
                                                    break;
                                                }
                                            }
                                        }
                                        pos++;
                                    }

                                    if (braceCount == 0 && pos < content.length()) {
                                        // Extract the JSON string
                                        String jsonString = content.substring(nameStart, pos);

                                        // Parse the nested JSON to extract the "text" field
                                        String extractedName = extractTextFromNestedJson(jsonString);

                                        if (extractedName != null && !extractedName.isEmpty()) {
                                            // Convert to internal format
                                            String internalName = extractInternalName(extractedName);
                                            if (internalName != null && !internalName.isEmpty()) {
                                                boolean wasNew = usedNames.add(internalName);
                                                if (wasNew) {
                                                    namesInThisFile++;
                                                    namesFound++;
//                                                    Specialization.logger.info("[LocalNameGenerator] Found name in " + file.getName() + ": " + internalName);
                                                }
                                            }
                                        }
                                    }
                                }

                                filesProcessed++;
                                if (namesInThisFile == 0) {
                                    Specialization.logger.fine("[LocalNameGenerator] No names found in file: " + file.getName());
                                }

                            } catch (Exception e) {
                                Specialization.logger.warning("[LocalNameGenerator] Failed to read file " + file.getName() + ": " + e.getMessage());
                            }
                        }
                    }

                    Specialization.logger.info("[LocalNameGenerator] Scan complete - Processed " + filesProcessed + " files, found " + namesFound + " unique names");

                } else {
                    Specialization.logger.warning("[LocalNameGenerator] No files found in MinecraftCivilizationsCore directory");
                }
            } else {
                Specialization.logger.warning("[LocalNameGenerator] MinecraftCivilizationsCore directory not found: " + pluginDataDir.getAbsolutePath());
            }
        } catch (Exception e) {
            Specialization.logger.warning("[LocalNameGenerator] Failed to load names from MinecraftCivilizationsCore: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Extracts the "text" field value from a nested JSON string
     *
     * @param jsonString The JSON string like {"italic":false,"color":"white","text":"Participant_973"}
     * @return The text value or null if extraction fails
     */
    private String extractTextFromNestedJson(String jsonString) {
        try {
//            Specialization.logger.info("[LocalNameGenerator] Raw JSON string to parse: " + jsonString);

            String textPattern = "\\\\\"text\\\\\"\\s*:\\s*\\\\\"([^\\\\\"]+)\\\\\"";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(textPattern);
            java.util.regex.Matcher matcher = pattern.matcher(jsonString);

            if (matcher.find()) {
                String textValue = matcher.group(1);
//                Specialization.logger.info("[LocalNameGenerator] Extracted text from JSON: " + textValue);
                return textValue;
            } else {
                String altPattern = "\"text\"\\s*:\\s*\"([^\"]+)\"";
                java.util.regex.Pattern altRegex = java.util.regex.Pattern.compile(altPattern);
                java.util.regex.Matcher altMatcher = altRegex.matcher(jsonString);

                if (altMatcher.find()) {
                    String textValue = altMatcher.group(1);
//                    Specialization.logger.info("[LocalNameGenerator] Extracted text using alternative pattern: " + textValue);
                    return textValue;
                }

                Specialization.logger.warning("[LocalNameGenerator] No 'text' field found in JSON: " + jsonString);
                return null;
            }
        } catch (Exception e) {
            Specialization.logger.warning("[LocalNameGenerator] Failed to extract text from JSON: " + jsonString + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the internal name format (FirstName_LastName) from a display name
     *
     * @param displayName The display name that might contain formatting
     * @return The internal name format or null if extraction fails
     */
    private String extractInternalName(String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            Specialization.logger.warning("[LocalNameGenerator] Display name is null or empty");
            return null;
        }

        // Remove any JSON formatting or color codes
        String cleanName = displayName.replaceAll("\\{[^}]*\\}", "").trim();

        // If it already contains underscore, it might be in internal format
        if (cleanName.contains("_")) {
            return cleanName;
        }

        // If it contains space, convert to underscore format
        if (cleanName.contains(" ")) {
            return cleanName.replace(" ", "_");
        }

        return cleanName;
    }

    /**
     * @return a randomly-picked "FirstName LastName"
     */
    public String nextName() throws NoSuchElementException {
        Specialization.logger.info("[LocalNameGenerator] Generating new name...");

        int totalUpper = (totalPossible > 0) ? totalPossible : Math.max(1, firstNames.size() * lastNames.size());


        if (usedNames.size() >= totalUpper) {
            Specialization.logger.severe("[LocalNameGenerator] All possible name combinations have been used (exact="
                    + (totalPossible > 0 ? totalPossible : "unknown") + ", used=" + usedNames.size() + ")!");
            throw new NoSuchElementException("All possible name combinations have been used");
        }

        int attempts = 0;
        int maxAttempts = Math.max(1000, totalUpper); // safety limit tuned to actual size

        while (attempts < maxAttempts) {
            attempts++;
            try {
                NameRoll first = generateNameRoll(firstNames, null);
                NameRoll last = generateNameRoll(lastNames, first);
                String name = first.getName() + "_" + last.getName();

             if (usedNames.contains(name) || tempUsedNames.contains(name) ||
                tempNames.values().stream().anyMatch(t -> t.initialName.equals(name) || t.options.contains(name))) {
                    continue; // name already reserved somewhere
                  }

                if (name.length() > 16) continue;

                if (usedNames.add(name)) return name;
                // duplicate -> continue
            } catch (NoSuchElementException e) {
                // No valid candidate for this roll (exhausted candidate pool) — count attempt and continue
                Specialization.logger.fine("[LocalNameGenerator] generateNameRoll failed on attempt " + attempts + ": " + e.getMessage());
            } catch (Exception e) {
                Specialization.logger.warning("[LocalNameGenerator] Unexpected error generating name: " + e.getMessage());
            }
        }

        Specialization.logger.severe("[LocalNameGenerator] FAILED: Could not generate a unique valid name after " + maxAttempts + " attempts");
        throw new NoSuchElementException("Could not generate a unique valid name after " + maxAttempts + " attempts");
    }


    private int totalPossible = -1;

    private void loadTotalFromGeneratedFile() {
        File outputFile = new File(Specialization.getInstance().getDataFolder(), "GeneratedNames.txt");
        if (!outputFile.exists()) return;

        try {
            List<String> lines = Files.readAllLines(outputFile.toPath());
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("Total names:")) {
                    String num = line.replace("Total names:", "").trim();
                    try {
                        totalPossible = Integer.parseInt(num);
                        Specialization.logger.info("[LocalNameGenerator] Total possible names loaded: " + totalPossible);
                    } catch (NumberFormatException nfe) {
                        Specialization.logger.warning("[LocalNameGenerator] Could not parse total from GeneratedNames.txt: '" + num + "'");
                    }
                    break;
                }
            }
        } catch (IOException e) {
            Specialization.logger.warning("[LocalNameGenerator] Failed to read GeneratedNames.txt: " + e.getMessage());
        }
    }

/**
 * @author Alectriciti
 */


    /**
     * A helper method that captures the nested name candidates within brackets
     * for example: {car|bicycle|truck} will have a 33% chance of drawing any of those
     * tags: [nature] [abyss] adding this anywhere to a name entry will assign it to that group.
     * If a last name has this tag, then it is compatible. Not adding a tag will be compatible with other non tag names.
     */
    private NameRoll generateNameRoll(List<String> list, NameRoll roll_context) {
        NameRoll new_roll = new NameRoll();
        List<String> candidates;

        // --- Decide candidate pool ---
        if (roll_context == null) {
            // First name: all options
            candidates = new ArrayList<>(list);
        } else {
            String group = roll_context.getRandomGroup();
            if (group != null && grouping.containsKey(group) && !grouping.get(group).isEmpty()) {
                candidates = new ArrayList<>(grouping.get(group)); // grouped last names
            } else {
                // fallback: ungrouped last names only
                candidates = list.stream()
                        .filter(l -> getGroupPattern(l).isEmpty())
                        .collect(Collectors.toList());
            }
        }

        // Shuffle once — pseudo-random order ensures full coverage
        Collections.shuffle(candidates, random);

        for (String raw : candidates) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // Expand braces {A|B|C}
            List<String> expanded = expandBraces(line);
            if (expanded.isEmpty()) continue;

            String variant = expanded.get(random.nextInt(expanded.size()));

            // Remove [group] tags
            variant = variant.replaceAll("\\s*\\[[^\\]]+\\]\\s*", "").trim();
            if (variant.isEmpty()) continue;

            new_roll.name = variant;
            new_roll.groups = getGroupPattern(raw);
            return new_roll;
        }

        // If we reach here, no valid name was found — throw, don’t loop forever
        throw new NoSuchElementException("No valid names left after checking all candidates.");
    }



    protected static class NameRoll {
        @Getter
        String name;
        @Getter
        List<String> groups;

        public String getRandomGroup() {
            if (groups == null || groups.isEmpty()) return null;
            return groups.get(new Random().nextInt(groups.size()));
        }
    }


    //------------------Temp name/optional names helpers---------------------//

    private final Set<String> tempUsedNames = new HashSet<>();
    public final Map<UUID, TempNameData> tempNames = new ConcurrentHashMap<>();

    public static class TempNameData {
        public String initialName;
        public List<String> options;
        public long expirationTime;
        public boolean confirmed;
    }

    /** Assign current player name as base + 2 temporary options */
    public List<String> assignTempNames(UUID playerUUID, String currentName) {
        String main = currentName;

        // generate 2 temporary names using nextName (handles uniqueness internally)
        List<String> tempOptions = new ArrayList<>();
        tempOptions.add(nextName());
        tempOptions.add(nextName());
        tempUsedNames.addAll(tempOptions);

        TempNameData data = new TempNameData();
        data.initialName = main;
        data.options = tempOptions;
        data.expirationTime = System.currentTimeMillis() + 10 * 60 * 1000; // 10 minutes
        data.confirmed = false;
        tempNames.put(playerUUID, data);

        List<String> result = new ArrayList<>();
        result.add(main);
        result.addAll(tempOptions);
        return result;
    }

    /** Clean up expired temporary names and sets initial name */
    public void cleanupExpiredTemps() {
        long now = System.currentTimeMillis();
        tempNames.forEach((uuid, data) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;


            boolean isPermanent = player.getPersistentDataContainer().has(PERMANENT_NAME_KEY, PersistentDataType.BYTE);

            if (!data.confirmed && data.expirationTime <= now && !isPermanent) {
                PlayerUtil.message(player,"Name choice time has <red>expired</red>. Using initial name: <gold>" + player.getName());
                Debug.broadcast("name", "unconfirmed name setting initial name to: " + data.initialName + "expTime: " + data.expirationTime + "now:" + now + "Perm & confirmed: " + !data.confirmed + !isPermanent);
                confirmNameChoice(uuid, data.initialName);
            } else if (!isPermanent) {
                PlayerUtil.message(player, "Remaining time to pick other names:<red> " + getRemainingTime(player) + "<gray>min(s)");
            }
        });
    }




    // Key for permanent name selection
    private final NamespacedKey PERMANENT_NAME_KEY = new NamespacedKey(Specialization.getInstance(), "permanent_name");


    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Check PDC for permanent name
        boolean hasPermanentName = player.getPersistentDataContainer().has(PERMANENT_NAME_KEY, PersistentDataType.BYTE);
        if (hasPermanentName) return; // Already chosen → skip everything

        // Block if player has already played >10min
        if (!canSelectTempName(player)) {
            return;
        }

        Component mainTitle = MiniMessage.miniMessage().deserialize("Your name is: <gold>" + player.getName() + "</gold>");
        Component subTitle = MiniMessage.miniMessage().deserialize("<gray>You have <red>" + getRemainingTime(player) + " </red>minutes to reroll name.</gray>");

        Title t = Title.title(mainTitle, subTitle, 20, 150, 50);

        player.showTitle(t);
        // Retrieve temp data
        TempNameData data = tempNames.get(uuid);

        List<String> names;
        if (data == null) {
            // First time or no temp data → assign new temp names
            names = assignTempNames(uuid, player.getName());
        } else {
            // Reuse previous temp names
            names = new ArrayList<>();
            names.add(data.initialName);
            names.addAll(data.options);
        }
        PlayerUtil.message(player, MiniMessage.miniMessage().deserialize("<gradient:#0D1B2A:#1B263B>=====================================</gradient>"));

        // Display current name
        player.sendMessage(
                Component.text("Your current name is: ", NamedTextColor.GRAY)
                        .append(Component.text(names.getFirst(), NamedTextColor.GOLD)
                                .decoration(TextDecoration.ITALIC, false))
        );

        // Build clickable name options (suggest command)
        Component message = Component.text("Rerolled Names: ", NamedTextColor.GRAY);

        for (int i = 1; i < names.size(); i++) {
            String temp = names.get(i);

            // Name selection button (suggest command)
            Component nameComponent = MiniMessage.miniMessage().deserialize("<gray>[<#687AB9>" + temp + "</#687AB9>]</gray>")
                    .hoverEvent(HoverEvent.showText(Component.text("Click to select " + temp)))
                    .clickEvent(ClickEvent.runCommand("/setnameoption " + temp));

            message = message.append(nameComponent);

            if (i < names.size() - 1) {
                message = message.append(Component.text(" || ", NamedTextColor.GRAY));
            }
        }
        player.sendMessage( message);
        player.sendMessage( MiniMessage.miniMessage().deserialize("<gray>You have <red>" + getRemainingTime(player) + "</red> minute(s) to select a rerolled name.</gray>"));
        PlayerUtil.message(player, MiniMessage.miniMessage().deserialize("<gradient:#3E4C7F:#2A3253>=====================================</gradient>"));
    }


    //this handles cleanup when a name is confirmed using namechoicecommand
    public boolean confirmNameChoice(UUID playerUUID, String selectedName) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null) return false;



        TempNameData data = tempNames.get(playerUUID);
        if (data == null || data.confirmed) {
//            PlayerUtil.message(player,"Your name has already been confirmed: <gold>" + selectedName);
            return false;
        }

        if (!selectedName.equals(data.initialName) && !data.options.contains(selectedName)) return false;

        usedNames.add(selectedName);
        for (String temp : data.options) {
            if (!temp.equals(selectedName)) tempUsedNames.remove(temp);
        }

        data.initialName = selectedName;
        data.confirmed = true;
        tempNames.remove(playerUUID);

        // Mark in PDC
        player.getPersistentDataContainer().set(PERMANENT_NAME_KEY, PersistentDataType.BYTE, (byte) 1);

        return true;
    }

        long timeLimitMinute = 10;

    public long getRemainingTime(Player player){
        int ticksPlayed = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        return timeLimitMinute - (ticksPlayed / 1200L);
    }

    public boolean canSelectTempName(Player player){
        long remaining = getRemainingTime(player);
        return remaining > 0; // still under 10 minutes
    }

}
