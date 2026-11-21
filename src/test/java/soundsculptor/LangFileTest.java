package soundsculptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * PRD 9.3: en_us.json and the code agree. Every key the code names is in the file, and every key in the file is used.
 *
 * <p>Keys are found in the main source code as string literals. Some are built in code, and are counted as used this way:
 * <ul>
 *   <li>a literal key plus a suffix the code appends: {@code key + ".tip"}, {@code key + ".one"}, {@code key + "_in"};</li>
 *   <li>a prefix literal ending in a dot plus a name: {@code "soundsculptor.sounds.sort." + order.name()}. Each part
 *   of the name must appear somewhere in the code as a word (an enum constant, a string, a field), so a key whose
 *   constant was removed is still caught;</li>
 *   <li>the few keys Minecraft builds itself, listed in {@link #BUILT_BY_MINECRAFT}.</li>
 * </ul>
 * Core enums with a prefix are checked exactly, value by value, in {@code LangKeysTest}.
 */
class LangFileTest {
	private static final Path LANG = Path.of("src/main/resources/assets/soundsculptor/lang/en_us.json");
	private static final Path SOURCES = Path.of("src/main/java");
	/** A string literal that names a key, or a key prefix ending in a dot. */
	private static final Pattern KEY = Pattern.compile("\"((?:key\\.|screen\\.)?soundsculptor\\.[a-z0-9_.]+)\"");
	/** A suffix the code appends to a key: {@code + ".tip"}. */
	private static final Pattern SUFFIX = Pattern.compile("\\+\\s*\"([._][a-z0-9_]+(?:\\.[a-z0-9_]+)*)\"");
	private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_]+");
	/**
	 * Keys made by Minecraft, not named in the code: the key category (from {@code KeyMapping.Category.register} with
	 * the ID {@code soundsculptor:main}) and the names of vanilla's dimensions (from the dimension ID the world reports).
	 */
	private static final Set<String> BUILT_BY_MINECRAFT = Set.of("key.category.soundsculptor.main",
			"soundsculptor.dimension.minecraft.overworld", "soundsculptor.dimension.minecraft.the_nether",
			"soundsculptor.dimension.minecraft.the_end");

	private record Code(Set<String> keys, Set<String> prefixes, Set<String> suffixes, Set<String> words) {
	}

	private static Code code() throws IOException {
		assertTrue(Files.isDirectory(SOURCES), "run from the project folder");
		Set<String> keys = new TreeSet<>();
		Set<String> prefixes = new TreeSet<>();
		Set<String> suffixes = new TreeSet<>();
		Set<String> words = new HashSet<>();
		try (Stream<Path> files = Files.walk(SOURCES)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
				String text = Files.readString(file, StandardCharsets.UTF_8);
				Matcher key = KEY.matcher(text);
				while (key.find()) {
					(key.group(1).endsWith(".") ? prefixes : keys).add(key.group(1));
				}
				Matcher suffix = SUFFIX.matcher(text);
				while (suffix.find()) {
					suffixes.add(suffix.group(1));
				}
				Matcher word = WORD.matcher(text);
				while (word.find()) {
					words.add(word.group().toLowerCase(Locale.ROOT));
				}
			}
		}
		return new Code(keys, prefixes, suffixes, words);
	}

	private static Set<String> langKeys() throws IOException {
		JsonObject lang = JsonParser.parseString(Files.readString(LANG, StandardCharsets.UTF_8)).getAsJsonObject();
		return new TreeSet<>(lang.keySet());
	}

	@Test
	void everyKeyTheCodeNamesIsInTheFile() throws IOException {
		Set<String> lang = langKeys();
		List<String> missing = code().keys().stream().filter(k -> !lang.contains(k)).toList();
		assertEquals(List.of(), missing, "keys named in the code with no text in en_us.json");
	}

	@Test
	void everyKeyInTheFileIsUsed() throws IOException {
		Code code = code();
		List<String> prefixes = code.prefixes().stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
		List<String> unused = new ArrayList<>();
		for (String key : langKeys()) {
			if (code.keys().contains(key) || BUILT_BY_MINECRAFT.contains(key)) {
				continue;
			}
			if (code.suffixes().stream().anyMatch(suffix -> key.endsWith(suffix)
					&& code.keys().contains(key.substring(0, key.length() - suffix.length())))) {
				continue;
			}
			String prefix = prefixes.stream().filter(key::startsWith).findFirst().orElse(null);
			if (prefix != null && namedInCode(key.substring(prefix.length()), code.words())) {
				continue;
			}
			unused.add(key);
		}
		assertEquals(List.of(), unused, "keys in en_us.json that nothing uses: remove them, or name them in the code");
	}

	/** Whether every part of a built key's name ("help.3.example", "do_not_mute") is a word in the code. */
	private static boolean namedInCode(String rest, Set<String> words) {
		for (String part : rest.split("\\.")) {
			if (!part.isEmpty() && !part.chars().allMatch(Character::isDigit) && !words.contains(part)) {
				return false;
			}
		}
		return true;
	}
}
