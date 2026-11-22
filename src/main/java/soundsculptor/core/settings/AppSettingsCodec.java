package soundsculptor.core.settings;

import soundsculptor.core.cue.PlaceCues;
import soundsculptor.core.ui.Palette;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Reads and writes the app-wide settings file. Human-readable JSON, one value per field.
 *
 * <p>Reading is forgiving, because these are only preferences: a missing value uses its default quietly (so files from
 * older versions load), and a wrong or unknown value uses the default (or the nearest allowed number) and adds a
 * problem in plain words, for the log. Good values are never lost because of one bad one. Only text that is not a
 * JSON object at all cannot be read.
 *
 * <p>The cue settings were app-wide until 2026-11-21 and now belong to each profile's places. A file from before still
 * reads without a problem: its {@code showCues}, {@code cuesWithoutSubtitles}, {@code cueColor}, {@code cueTextScale},
 * {@code cueOffsetX} and {@code cueOffsetY} become {@link AppSettings#legacyCues()}, which is written back as one
 * {@code legacyCues} object (with the same field names) so profiles loaded later still start from them; the old fields
 * themselves are no longer written.
 */
public final class AppSettingsCodec {
	public static final int SCHEMA_VERSION = 1;

	/** Tab names from before the Sounds tab (FR-12.1), read as the Sounds tab. */
	private static final Set<String> RETIRED_TABS = Set.of("recent", "search");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	/** The app-wide cue settings of files saved before 2026-11-21, and the names inside {@code legacyCues}. */
	private static final List<String> OLD_CUE_FIELDS = List.of("showCues", "cuesWithoutSubtitles", "cueColor", "cueTextScale",
			"cueOffsetX", "cueOffsetY");
	private static final String LEGACY_CUES = "legacyCues";
	/** What the app-wide cue settings defaulted to: Everywhere's defaults, but with Show cues on (before 2026-11-22). */
	private static final PlaceCues OLD_DEFAULTS = PlaceCues.EVERYWHERE_DEFAULTS.withShowCues(true);
	private static final Set<String> FIELDS = Set.of("schemaVersion", "showCues", "cuesWithoutSubtitles", "cueColor",
			"cueTextScale", "cueOffsetX", "cueOffsetY", LEGACY_CUES, "outlinesThroughBlocks", "reducedMotion", "lastTab");

	private AppSettingsCodec() {
	}

	/** Settings read from a file, and what was wrong with it, in plain words (empty if nothing). */
	public record Read(AppSettings settings, List<String> problems) {
		public Read {
			problems = List.copyOf(problems);
		}
	}

	public static String toJson(AppSettings s) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", SCHEMA_VERSION);
		root.addProperty("outlinesThroughBlocks", s.outlinesThroughBlocks());
		root.addProperty("reducedMotion", s.reducedMotion());
		root.addProperty("lastTab", s.lastTab());
		PlaceCues legacy = s.legacyCues();
		if (legacy != null) {
			JsonObject cues = new JsonObject();
			if (legacy.showCues() != null) {
				cues.addProperty("showCues", legacy.showCues());
			}
			if (legacy.withoutSubtitles() != null) {
				cues.addProperty("cuesWithoutSubtitles", legacy.withoutSubtitles());
			}
			if (legacy.color() != null) {
				cues.addProperty("cueColor", Palette.formatHex(legacy.color()));
			}
			if (legacy.textScale() != null) {
				cues.addProperty("cueTextScale", legacy.textScale());
			}
			if (legacy.boxOffset() != null) {
				cues.addProperty("cueOffsetX", legacy.boxOffset().x());
				cues.addProperty("cueOffsetY", legacy.boxOffset().y());
			}
			root.add(LEGACY_CUES, cues);
		}
		return GSON.toJson(root) + "\n";
	}

	/** @throws AppSettingsFormatException if {@code text} is not a JSON object */
	public static Read read(String text) throws AppSettingsFormatException {
		JsonElement parsed;
		try {
			parsed = JsonParser.parseString(text);
		} catch (JsonParseException e) {
			throw new AppSettingsFormatException("The file is not valid JSON.");
		}
		if (!parsed.isJsonObject()) {
			throw new AppSettingsFormatException("The file does not hold a settings object.");
		}
		JsonObject root = parsed.getAsJsonObject();
		List<String> problems = new ArrayList<>();
		checkVersion(root, problems);
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (!FIELDS.contains(entry.getKey())) {
				problems.add("Unknown setting \"" + entry.getKey() + "\" was ignored.");
			}
		}
		AppSettings d = AppSettings.DEFAULTS;
		boolean throughBlocks = bool(root, "outlinesThroughBlocks", d.outlinesThroughBlocks(), problems);
		boolean reducedMotion = bool(root, "reducedMotion", d.reducedMotion(), problems);
		String lastTab = tab(root, "lastTab", d.lastTab(), problems);
		PlaceCues legacy = null;
		JsonElement saved = root.get(LEGACY_CUES);
		if (saved != null && saved.isJsonObject()) {
			legacy = legacyCues(saved.getAsJsonObject(), problems);
		} else if (saved != null) {
			problems.add(LEGACY_CUES + ": " + saved + " is not an object, so it was ignored.");
		} else if (OLD_CUE_FIELDS.stream().anyMatch(root::has)) {
			// A file from before 2026-11-21: its cue settings were app-wide.
			legacy = legacyCues(root, problems);
		}
		return new Read(new AppSettings(throughBlocks, reducedMotion, lastTab, legacy), problems);
	}

	/**
	 * The old app-wide cue settings in {@code json}; one that is missing takes its old default. Show cues is kept only as
	 * a record: since 2026-11-22 it no longer seeds a profile ({@link PlaceCues#seededFrom}).
	 */
	private static PlaceCues legacyCues(JsonObject json, List<String> problems) {
		PlaceCues d = OLD_DEFAULTS;
		boolean showCues = bool(json, "showCues", d.showCues(), problems);
		boolean withoutSubtitles = bool(json, "cuesWithoutSubtitles", d.withoutSubtitles(), problems);
		int color = color(json, "cueColor", d.color(), problems);
		int scale = number(json, "cueTextScale", d.textScale(), PlaceCues.MIN_TEXT_SCALE, PlaceCues.MAX_TEXT_SCALE, problems);
		int offsetX = number(json, "cueOffsetX", d.boxOffset().x(), -PlaceCues.MAX_OFFSET, PlaceCues.MAX_OFFSET, problems);
		int offsetY = number(json, "cueOffsetY", d.boxOffset().y(), -PlaceCues.MAX_OFFSET, PlaceCues.MAX_OFFSET, problems);
		return new PlaceCues(showCues, withoutSubtitles, null, color, scale, new PlaceCues.Offset(offsetX, offsetY));
	}

	private static void checkVersion(JsonObject root, List<String> problems) {
		JsonElement value = root.get("schemaVersion");
		String asOne = "; the file is read as schema " + SCHEMA_VERSION + ".";
		if (value == null) {
			problems.add("schemaVersion is missing" + asOne);
			return;
		}
		BigDecimal version = wholeNumber(value);
		if (version == null) {
			problems.add("schemaVersion: " + value + " is not a whole number" + asOne);
		} else if (version.signum() <= 0) {
			problems.add("schemaVersion: " + value + " is not a schema version" + asOne);
		} else if (version.compareTo(BigDecimal.valueOf(SCHEMA_VERSION)) > 0) {
			problems.add("The file is from a newer Sound Sculptor (schema " + value
					+ "); settings this version does not know are ignored.");
		}
	}

	private static boolean bool(JsonObject root, String field, boolean fallback, List<String> problems) {
		JsonElement value = root.get(field);
		if (value == null) {
			return fallback;
		}
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
			return value.getAsBoolean();
		}
		problems.add(field + ": " + value + " is not true or false, so the default is used.");
		return fallback;
	}

	private static int number(JsonObject root, String field, int fallback, int min, int max, List<String> problems) {
		JsonElement value = root.get(field);
		if (value == null) {
			return fallback;
		}
		BigDecimal number = wholeNumber(value);
		if (number == null) {
			problems.add(field + ": " + value + " is not a whole number, so the default is used.");
			return fallback;
		}
		if (number.compareTo(BigDecimal.valueOf(min)) < 0) {
			problems.add(field + ": " + value + " is below the minimum " + min + ", so " + min + " is used.");
			return min;
		}
		if (number.compareTo(BigDecimal.valueOf(max)) > 0) {
			problems.add(field + ": " + value + " is above the maximum " + max + ", so " + max + " is used.");
			return max;
		}
		return number.intValueExact();
	}

	/** The value as a whole number, or {@code null} if it is not a JSON number without a fraction. */
	private static BigDecimal wholeNumber(JsonElement value) {
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		BigDecimal number;
		try {
			number = value.getAsBigDecimal();
		} catch (NumberFormatException e) {
			return null;
		}
		return number.stripTrailingZeros().scale() <= 0 ? number : null;
	}

	private static int color(JsonObject root, String field, int fallback, List<String> problems) {
		JsonElement value = root.get(field);
		if (value == null) {
			return fallback;
		}
		if (value.isJsonPrimitive() && ((JsonPrimitive) value).isString()) {
			OptionalInt rgb = Palette.parseHex(value.getAsString());
			if (rgb.isPresent()) {
				return rgb.getAsInt();
			}
		}
		problems.add(field + ": " + value + " is not a color like #E69F00, so the default is used.");
		return fallback;
	}

	private static String tab(JsonObject root, String field, String fallback, List<String> problems) {
		JsonElement value = root.get(field);
		if (value == null) {
			return fallback;
		}
		if (value.isJsonPrimitive() && ((JsonPrimitive) value).isString() && AppSettings.isValidTab(value.getAsString())) {
			String tab = value.getAsString();
			// FR-12.1: the Sounds tab replaced Recently Played and Search, so a file saved before still opens it.
			return RETIRED_TABS.contains(tab) ? AppSettings.DEFAULT_TAB : tab;
		}
		problems.add(field + ": " + value + " is not a tab name, so the default is used.");
		return fallback;
	}
}
