package soundsculptor.core.profile;

import soundsculptor.core.Message;
import soundsculptor.core.cue.PlaceCues;
import soundsculptor.core.outline.OutlineMode;
import soundsculptor.core.outline.ZoneColors;
import soundsculptor.core.sets.Attachment;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetCodec;
import soundsculptor.core.sets.SetFormatException;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and writes the profile file (PRD section 7.5). Human-readable JSON; unknown fields are ignored, with a
 * warning, so newer optional fields do not break older readers.
 *
 * <p>Each place (Everywhere and every zone) lists its attached Sound Sets in {@code sets}, top first, by library set
 * ID; {@code "quick"}, when present, is first and refers to the place's own {@code quickChanges} set, held inline with
 * its entries written as in a Sound Set file ({@link SetCodec}).
 *
 * <p>Reading checks every value (PRD FR-11.6) and names the problem and its place in plain words, for example
 * "Zone 3, field width: 900 is above the maximum 512." Schema 1 and 2 files come from earlier test builds and are
 * refused ({@link ProfileMigrations#OLD_BUILD}); later schemas will be migrated first (PRD 7.6).
 */
public final class ProfileCodec {
	/** Coordinates beyond the world border are refused. */
	static final double MAX_COORDINATE = 30_000_000;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "id", "name", "worldKey", "settings", "everywhere",
			"zones", "lastJoined", "boostConfirmed");
	/**
	 * {@code edge} in the settings, and {@code mode} and {@code edge} on a zone, were retired with Outside mode and fade
	 * edges (owner decision 2026-11-14): still accepted without a warning, and ignored. {@code cueDefault} in the settings
	 * moved to Everywhere's cue settings as {@code newMutesGetCue} (2026-11-21): still read, once, to fill that in for a
	 * file saved before, and no longer written.
	 */
	private static final Set<String> SETTINGS_FIELDS = Set.of("showDistance", "cueDefault", "outlines", "edge");
	private static final Set<String> PLACE_FIELDS = Set.of("sets", "quickChanges", "cues");
	/** A place's cue settings (FR-7, decided 2026-11-21); a setting left out is Unchanged (a zone) or its default. */
	private static final Set<String> CUE_FIELDS = Set.of("showCues", "cuesWithoutSubtitles", "newMutesGetCue", "color", "textScale",
			"boxOffset");
	private static final Set<String> OFFSET_FIELDS = Set.of("x", "y");
	private static final Set<String> ATTACHMENT_FIELDS = Set.of("set", "enabled");
	private static final Set<String> ZONE_FIELDS = Set.of("id", "name", "enabled", "color", "anchor", "shape", "mode", "edge",
			"sets", "quickChanges", "cues");
	private static final Set<String> ANCHOR_FIELDS = Set.of("type", "x", "y", "z", "dimension", "worldKey");
	private static final Map<String, Set<String>> SHAPE_FIELDS = Map.of(
			"box", Set.of("type", "width", "height", "depth"));

	private ProfileCodec() {
	}

	/**
	 * A profile read from a file, and the unknown fields and unusable cue settings that were ignored.
	 *
	 * @param everywhereCuesSaved whether the file holds Everywhere's cue settings; a file saved before they moved from
	 *                            the app-wide settings does not (decided 2026-11-21), and gets them from there once
	 */
	public record Read(Profile profile, List<Message> warnings, boolean everywhereCuesSaved) {
		public Read {
			warnings = List.copyOf(warnings);
		}

		/** A read of a file that holds Everywhere's cue settings. */
		public Read(Profile profile, List<Message> warnings) {
			this(profile, warnings, true);
		}
	}

	public static String toJson(Profile profile) {
		return toJson(profile, null);
	}

	/**
	 * The profile file's text, recording its world key (FR-11.3): file names are made safe for the file system, so the
	 * key cannot be read back from the name. The key is not part of {@link Profile}; it is ignored when reading.
	 *
	 * @param worldKey the key to record, or null for none (exports and older callers)
	 */
	public static String toJson(Profile profile, String worldKey) {
		return write(toJsonObject(profile, worldKey));
	}

	/** The profile file's JSON object; {@link ExportCodec} adds the exported sets to it. */
	static JsonObject toJsonObject(Profile profile, String worldKey) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", Profile.SCHEMA_VERSION);
		root.addProperty("name", profile.name());
		if (worldKey != null) {
			root.addProperty("worldKey", worldKey);
		}
		if (profile.lastJoined() != null) {
			root.addProperty("lastJoined", profile.lastJoined().toString());
		}
		root.addProperty("boostConfirmed", profile.boostConfirmed());
		root.add("settings", settingsToJson(profile.settings()));
		JsonObject everywhere = new JsonObject();
		placeToJson(everywhere, profile.everywhere());
		everywhere.add("cues", cuesToJson(profile.everywhereCues()));
		root.add("everywhere", everywhere);
		JsonArray zones = new JsonArray();
		for (Zone zone : profile.zones()) {
			zones.add(zoneToJson(zone, profile.colorOf(zone.id())));
		}
		root.add("zones", zones);
		return root;
	}

	/** A profile or export file's text: pretty-printed JSON ending in a line break. */
	static String write(JsonObject root) {
		return GSON.toJson(root) + "\n";
	}

	private static JsonObject settingsToJson(ProfileSettings s) {
		JsonObject json = new JsonObject();
		json.addProperty("showDistance", s.showDistance());
		json.addProperty("outlines", s.outlines().name().toLowerCase(Locale.ROOT));
		return json;
	}

	/** Adds a place's {@code sets} and, if it has one, its {@code quickChanges} to {@code json}. */
	private static void placeToJson(JsonObject json, PlaceSets place) {
		JsonArray sets = new JsonArray();
		for (Attachment attachment : place.attached()) {
			JsonObject a = new JsonObject();
			a.addProperty("set", attachment.setId());
			a.addProperty("enabled", attachment.enabled());
			sets.add(a);
		}
		json.add("sets", sets);
		if (place.quickChanges() != null) {
			json.add("quickChanges", SetCodec.quickToJson(place.quickChanges()));
		}
	}

	private static JsonObject zoneToJson(Zone zone, Integer color) {
		JsonObject json = new JsonObject();
		json.addProperty("id", zone.id());
		if (zone.name() != null) {
			json.addProperty("name", zone.name());
		}
		json.addProperty("enabled", zone.enabled());
		if (color != null) {
			json.addProperty("color", ZoneColors.format(color));
		}
		JsonObject anchor = new JsonObject();
		anchor.addProperty("type", "fixed");
		anchor.addProperty("x", zone.anchor().x());
		anchor.addProperty("y", zone.anchor().y());
		anchor.addProperty("z", zone.anchor().z());
		anchor.addProperty("dimension", zone.dimension());
		anchor.addProperty("worldKey", zone.worldKey());
		json.add("anchor", anchor);
		JsonObject shape = new JsonObject();
		switch (zone.shape()) {
			case Shape.Box b -> {
				shape.addProperty("type", "box");
				shape.addProperty("width", b.width());
				shape.addProperty("height", b.height());
				shape.addProperty("depth", b.depth());
			}
		}
		json.add("shape", shape);
		placeToJson(json, zone.sets());
		if (!zone.cues().isUnchanged()) {
			json.add("cues", cuesToJson(zone.cues()));
		}
		return json;
	}

	/**
	 * A place's cue settings (FR-7): only those it sets, so a zone's Unchanged ones are left out. Everywhere sets every
	 * one, so all are written for it.
	 */
	private static JsonObject cuesToJson(PlaceCues cues) {
		JsonObject json = new JsonObject();
		if (cues.showCues() != null) {
			json.addProperty("showCues", cues.showCues());
		}
		if (cues.withoutSubtitles() != null) {
			json.addProperty("cuesWithoutSubtitles", cues.withoutSubtitles());
		}
		if (cues.newMutesGetCue() != null) {
			json.addProperty("newMutesGetCue", cues.newMutesGetCue());
		}
		if (cues.color() != null) {
			json.addProperty("color", ZoneColors.format(cues.color()));
		}
		if (cues.textScale() != null) {
			json.addProperty("textScale", cues.textScale());
		}
		if (cues.boxOffset() != null) {
			JsonObject offset = new JsonObject();
			offset.addProperty("x", cues.boxOffset().x());
			offset.addProperty("y", cues.boxOffset().y());
			json.add("boxOffset", offset);
		}
		return json;
	}

	/**
	 * The world key recorded in a profile file's text, without reading the rest. Empty if the file does not record one
	 * (files saved before keys were recorded) or cannot be parsed.
	 */
	public static Optional<String> worldKeyOf(String text) {
		try {
			JsonElement key = parse(text).get("worldKey");
			if (key != null && key.isJsonPrimitive() && key.getAsJsonPrimitive().isString() && !key.getAsString().isEmpty()) {
				return Optional.of(key.getAsString());
			}
		} catch (ProfileFormatException e) {
			// Unreadable: the Profiles tab shows the file without a key.
		}
		return Optional.empty();
	}

	/** @throws ProfileFormatException if the text is not a valid profile of a known schema version */
	public static Profile fromJson(String text) throws ProfileFormatException {
		return read(text, version -> { }).profile();
	}

	/**
	 * Reads a profile file, migrating an older schema first.
	 *
	 * @param backup called once with the file's version before an older file is migrated; if it throws, nothing is read
	 */
	public static Read read(String text, ProfileMigrations.Backup backup) throws ProfileFormatException {
		return read(parse(text), backup);
	}

	/** Parses the text into a JSON object, with plain-language errors. */
	static JsonObject parse(String text) throws ProfileFormatException {
		try {
			JsonElement parsed = JsonParser.parseString(text);
			if (!parsed.isJsonObject()) {
				throw ProfileText.problem(ProfileText.NOT_PROFILE);
			}
			return parsed.getAsJsonObject();
		} catch (JsonParseException e) {
			throw new ProfileFormatException(jsonProblem(e), e);
		}
	}

	static Read read(JsonObject original, ProfileMigrations.Backup backup) throws ProfileFormatException {
		try {
			return readChecked(original, backup);
		} catch (IllegalArgumentException | IllegalStateException e) {
			// Every value is checked above; this is a last guard so a bad file can never crash the game.
			throw new ProfileFormatException(Message.of(ProfileText.NOT_ALLOWED), e);
		}
	}

	private static Read readChecked(JsonObject original, ProfileMigrations.Backup backup) throws ProfileFormatException {
		JsonObject root = ProfileMigrations.CURRENT.migrate(original, backup);
		List<Message> warnings = new ArrayList<>();
		Fields top = new Fields(root, null, warnings, ROOT_FIELDS);
		String name = top.has("name") ? top.string("name") : "Default";
		Instant lastJoined = top.has("lastJoined") ? top.instant("lastJoined") : null;
		boolean boostConfirmed = top.has("boostConfirmed") && top.bool("boostConfirmed");
		ProfileSettings settings = ProfileSettings.DEFAULTS;
		Boolean oldCueDefault = null;
		if (top.has("settings")) {
			Fields s = top.object("settings", Message.of(ProfileText.PLACE_SETTINGS), SETTINGS_FIELDS);
			settings = readSettings(s);
			oldCueDefault = s.optionalBool("cueDefault");
		}
		PlaceSets everywhere = PlaceSets.EMPTY;
		PlaceCues everywhereCues = null;
		if (top.has("everywhere")) {
			Fields e = top.object("everywhere", Message.of(ProfileText.PLACE_EVERYWHERE), PLACE_FIELDS);
			everywhere = readPlace(e, warnings);
			everywhereCues = readCues(e);
		}
		boolean cuesSaved = everywhereCues != null;
		if (everywhereCues == null) {
			// Saved before cue settings moved to the places (2026-11-21): the profile's "New mutes get Cue" moves here; the
			// rest were app-wide, and the caller fills them in (Read.everywhereCuesSaved).
			everywhereCues = oldCueDefault == null ? PlaceCues.EVERYWHERE_DEFAULTS
					: PlaceCues.EVERYWHERE_DEFAULTS.withNewMutesGetCue(oldCueDefault);
		}
		everywhereCues = everywhereCues.orElse(PlaceCues.EVERYWHERE_DEFAULTS);
		JsonArray zoneArray = top.has("zones") ? top.array("zones") : new JsonArray();
		if (zoneArray.size() > Precedence.MAX_ZONES) {
			throw ProfileText.problem(ProfileText.TOO_MANY_ZONES, zoneArray.size(), Precedence.MAX_ZONES);
		}
		List<Zone> zones = new ArrayList<>();
		Map<String, Integer> colors = new LinkedHashMap<>();
		Map<String, Integer> firstUse = new HashMap<>();
		for (int i = 0; i < zoneArray.size(); i++) {
			Fields z = top.element(zoneArray, i, Message.of(ProfileText.PLACE_ZONE, i + 1), ZONE_FIELDS);
			Zone zone = readZone(z, warnings);
			Integer earlier = firstUse.putIfAbsent(zone.id(), i + 1);
			if (earlier != null) {
				throw z.problem("id", Message.of(ProfileText.ID_USED, quote(zone.id()), earlier));
			}
			if (z.has("color")) {
				colors.put(zone.id(), z.color("color"));
			}
			zones.add(zone);
		}
		return new Read(new Profile(name, settings, everywhere, zones, colors, lastJoined, boostConfirmed, everywhereCues), warnings,
				cuesSaved);
	}

	/**
	 * A place's attached sets and Quick changes. Checked as the editor keeps them: {@code "quick"} only first and only
	 * with a {@code quickChanges} set, no set twice, at most {@value PlaceSets#MAX_SETS} library sets, and the Quick
	 * changes entries checked like a Sound Set file's (at most {@value SoundSet#MAX_ENTRIES}).
	 */
	private static PlaceSets readPlace(Fields p, List<Message> warnings) throws ProfileFormatException {
		List<Attachment> attached = new ArrayList<>();
		if (p.has("sets")) {
			JsonArray array = p.array("sets");
			Set<String> seen = new HashSet<>();
			int librarySets = 0;
			for (int i = 0; i < array.size(); i++) {
				Fields a = p.element(array, i, Message.of(ProfileText.PLACE_ATTACHMENT, p.where(), i + 1), ATTACHMENT_FIELDS);
				String id = a.string("set");
				boolean quick = id.equals(SoundSet.QUICK_ID);
				if (quick && i > 0) {
					throw p.problem("sets", ProfileText.QUICK_NOT_FIRST);
				}
				if (!quick) {
					// The same rules as a Sound Set file's ID, so the reference can name a set file (FR-11.6, FR-11.8).
					if (!SetCodec.ID_PATTERN.matcher(id).matches()) {
						throw a.problem("set", ProfileText.BAD_SET_ID, quote(id));
					}
					if (SetCodec.isReservedId(id)) {
						throw a.problem("set", ProfileText.RESERVED_SET_ID, quote(id));
					}
				}
				if (!seen.add(id)) {
					throw p.problem("sets", ProfileText.SET_TWICE, quote(id));
				}
				if (!quick) {
					librarySets++;
				}
				attached.add(new Attachment(id, !a.has("enabled") || a.bool("enabled")));
			}
			if (librarySets > PlaceSets.MAX_SETS) {
				throw p.problem("sets", ProfileText.TOO_MANY_SETS, librarySets, PlaceSets.MAX_SETS);
			}
		}
		boolean quickListed = !attached.isEmpty() && attached.get(0).setId().equals(SoundSet.QUICK_ID);
		SoundSet quick = null;
		if (p.has("quickChanges")) {
			if (!quickListed) {
				throw p.problem("quickChanges", ProfileText.QUICK_UNLISTED);
			}
			JsonElement raw = p.require("quickChanges");
			if (!raw.isJsonObject()) {
				throw p.problem("quickChanges", ProfileText.NOT_OBJECT, describe(raw));
			}
			try {
				quick = SetCodec.readQuick(raw.getAsJsonObject(), Message.of(ProfileText.PLACE_QUICK, p.where()), warnings);
			} catch (SetFormatException e) {
				throw new ProfileFormatException(e.problem(), e);
			}
		} else if (quickListed) {
			throw p.problem("quickChanges", ProfileText.MISSING);
		}
		return new PlaceSets(attached, quick);
	}

	/** Profile settings; a missing value takes its default. The old {@code cueDefault} is read by the caller. */
	private static ProfileSettings readSettings(Fields s) throws ProfileFormatException {
		ProfileSettings settings = ProfileSettings.DEFAULTS;
		if (s.has("showDistance")) {
			settings = settings.withShowDistance(s.bool("showDistance"));
		}
		if (s.has("outlines")) {
			String mode = s.choice("outlines", "outlines", "always", "editing", "never");
			settings = settings.withOutlines(OutlineMode.valueOf(mode.toUpperCase(Locale.ROOT)));
		}
		return settings;
	}

	private static Zone readZone(Fields z, List<Message> warnings) throws ProfileFormatException {
		String id = z.string("id");
		String name = z.has("name") ? z.string("name") : null;
		boolean enabled = !z.has("enabled") || z.bool("enabled");
		Fields a = z.object("anchor", z.where(), ANCHOR_FIELDS);
		// Every zone is fixed (FR-8.1). Personal bubbles were dropped before release (owner decision 2026-11-13), so a file
		// that still has one is refused like any other value out of spec.
		a.refuse("type", "anchor type", "bubble", ProfileText.UNSUPPORTED_ANCHOR);
		a.choice("type", "anchor type", "fixed");
		Point anchor = new Point(a.number("x", -MAX_COORDINATE, MAX_COORDINATE), a.number("y", -MAX_COORDINATE, MAX_COORDINATE),
				a.number("z", -MAX_COORDINATE, MAX_COORDINATE));
		String dimension = a.string("dimension");
		String worldKey = a.string("worldKey");
		Shape shape = readShape(z);
		PlaceSets sets = readPlace(z, warnings);
		PlaceCues cues = readCues(z);
		return new Zone(id, name, enabled, worldKey, dimension, anchor, shape, sets, cues == null ? PlaceCues.UNCHANGED : cues);
	}

	/**
	 * A place's {@code cues}, or {@code null} if it has none. Read forgivingly, as they are only preferences (like the
	 * app-wide settings file): a value that cannot be used is left Unchanged with a warning, so it never stops the
	 * profile from loading, and an unknown field is ignored with a warning.
	 */
	private static PlaceCues readCues(Fields p) {
		if (!p.has("cues")) {
			return null;
		}
		JsonElement raw = p.json.get("cues");
		if (!raw.isJsonObject()) {
			p.ignore("cues", Message.of(ProfileText.NOT_OBJECT, describe(raw)));
			return PlaceCues.UNCHANGED;
		}
		Fields c = new Fields(raw.getAsJsonObject(), Message.of(ProfileText.PLACE_CUES, p.where()), p.warnings, CUE_FIELDS);
		return PlaceCues.UNCHANGED.withShowCues(c.optionalBool("showCues"))
				.withWithoutSubtitles(c.optionalBool("cuesWithoutSubtitles"))
				.withNewMutesGetCue(c.optionalBool("newMutesGetCue"))
				.withColor(c.optionalColor("color"))
				.withTextScale(c.optionalWhole("textScale", PlaceCues.MIN_TEXT_SCALE, PlaceCues.MAX_TEXT_SCALE))
				.withBoxOffset(c.optionalOffset("boxOffset"));
	}

	private static Shape readShape(Fields z) throws ProfileFormatException {
		JsonElement raw = z.require("shape");
		if (!raw.isJsonObject()) {
			throw z.problem("shape", Message.of(ProfileText.NOT_OBJECT, describe(raw)));
		}
		JsonObject json = raw.getAsJsonObject();
		Fields probe = new Fields(json, z.where(), null, null);
		// Zones are boxes only: spheres and cylinders were dropped before release (owner decisions 2026-11-13 and
		// 2026-11-18). Any other shape name, those included, is "not a supported shape".
		probe.refuseAllBut("type", "shape type", "box", ProfileText.UNSUPPORTED_SHAPE);
		String type = probe.choice("type", "shape type", "box");
		Fields s = z.object("shape", z.where(), SHAPE_FIELDS.get(type));
		return new Shape.Box(s.number("width", Shape.Box.MIN_SIDE, Shape.Box.MAX_SIDE),
				s.number("height", Shape.Box.MIN_SIDE, Shape.Box.MAX_SIDE),
				s.number("depth", Shape.Box.MIN_SIDE, Shape.Box.MAX_SIDE));
	}

	/** Plain words with a location, e.g. "The file is not valid JSON (line 1, column 44).", instead of parser jargon. */
	static Message jsonProblem(JsonParseException e) {
		String detail = e.getMessage() == null ? "" : e.getMessage();
		Matcher where = LOCATION.matcher(detail);
		return where.find()
				? Message.of(ProfileText.NOT_JSON_AT, where.group(1), where.group(2))
				: Message.of(ProfileText.NOT_JSON);
	}

	private static final Pattern LOCATION = Pattern.compile("line ([0-9]+) column ([0-9]+)");

	/** A number as a person writes it: 900, 0.5, 12.25. */
	static String format(double value) {
		if (value == Math.rint(value) && Math.abs(value) < 1e15) {
			return Long.toString((long) value);
		}
		return Double.toString(value);
	}

	static String quote(String text) {
		return "\"" + text + "\"";
	}

	static String describe(JsonElement value) {
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
			return format(value.getAsDouble());
		}
		String text = value.toString();
		return text.length() > 40 ? text.substring(0, 37) + "..." : text;
	}

	/** One JSON object being read, with its place in the file for messages ("Zone 3", "Rule 2"; null at the top). */
	private static final class Fields {
		private final JsonObject json;
		private final Message where;
		private final List<Message> warnings;

		Fields(JsonObject json, Message where, List<Message> warnings, Set<String> known) {
			this.json = json;
			this.where = where;
			this.warnings = warnings;
			if (warnings != null && known != null) {
				for (String key : json.keySet()) {
					if (!known.contains(key)) {
						warnings.add(where == null ? Message.of(ProfileText.UNKNOWN_FIELD, quote(key))
								: Message.of(ProfileText.UNKNOWN_FIELD_AT, where, quote(key)));
					}
				}
			}
		}

		Message where() {
			return where;
		}

		boolean has(String field) {
			return json.has(field) && !json.get(field).isJsonNull();
		}

		/** {@code field}'s problem at this place; the field is named as in the file (or a short label). */
		ProfileFormatException problem(String field, Message what) {
			return new ProfileFormatException(where == null ? Message.of(ProfileText.TOP_FIELD, field, what)
					: Message.of(ProfileText.FIELD, where, field, what));
		}

		private ProfileFormatException problem(String field, String key, Object... args) {
			return problem(field, Message.of(key, args));
		}

		JsonElement require(String field) throws ProfileFormatException {
			if (!has(field)) {
				throw problem(field, ProfileText.MISSING);
			}
			return json.get(field);
		}

		private JsonPrimitive primitive(String field) throws ProfileFormatException {
			JsonElement value = require(field);
			return value.isJsonPrimitive() ? value.getAsJsonPrimitive() : null;
		}

		String string(String field) throws ProfileFormatException {
			JsonPrimitive p = primitive(field);
			if (p == null || !p.isString()) {
				throw problem(field, ProfileText.NOT_TEXT, describe(json.get(field)));
			}
			return p.getAsString();
		}

		/** A moment in ISO-8601 form, e.g. {@code 2026-11-09T13:37:46Z}. */
		Instant instant(String field) throws ProfileFormatException {
			String text = string(field);
			try {
				return Instant.parse(text);
			} catch (DateTimeParseException e) {
				throw problem(field, ProfileText.NOT_DATE, quote(text));
			}
		}

		boolean bool(String field) throws ProfileFormatException {
			JsonPrimitive p = primitive(field);
			if (p == null || !p.isBoolean()) {
				throw problem(field, ProfileText.NOT_BOOLEAN, describe(json.get(field)));
			}
			return p.getAsBoolean();
		}

		double number(String field, double min, double max) throws ProfileFormatException {
			JsonPrimitive p = primitive(field);
			if (p == null || !p.isNumber()) {
				throw problem(field, ProfileText.NOT_NUMBER, describe(json.get(field)));
			}
			double value = p.getAsDouble();
			if (!Double.isFinite(value)) {
				throw problem(field, ProfileText.NOT_NUMBER, describe(p));
			}
			if (value > max) {
				throw problem(field, ProfileText.ABOVE_MAX, format(value), format(max));
			}
			if (value < min) {
				throw problem(field, ProfileText.BELOW_MIN, format(value), format(min));
			}
			return value;
		}

		/**
		 * Refuses a word the format no longer has, e.g. a removed shape, with its own message {@code key} instead of
		 * the list of choices. Does nothing for any other value.
		 */
		void refuse(String field, String label, String removed, String key) throws ProfileFormatException {
			JsonElement value = json.get(field);
			if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
					&& removed.equals(value.getAsString())) {
				throw problem(label, key, describe(value));
			}
		}

		/**
		 * Refuses every word but {@code kept}, with its own message {@code key} instead of the list of choices (one
		 * choice reads badly as a list). Does nothing for a value that is not a word, which {@link #choice} reports.
		 */
		void refuseAllBut(String field, String label, String kept, String key) throws ProfileFormatException {
			JsonElement value = json.get(field);
			if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
					&& !kept.equals(value.getAsString())) {
				throw problem(label, key, describe(value));
			}
		}

		/** One of the allowed words, e.g. "box"; {@code label} names the field in the message ("shape type"). */
		String choice(String field, String label, String... allowed) throws ProfileFormatException {
			if (!has(field)) {
				throw problem(label, ProfileText.MISSING);
			}
			JsonPrimitive p = primitive(field);
			if (p != null && p.isString()) {
				for (String a : allowed) {
					if (a.equals(p.getAsString())) {
						return a;
					}
				}
			}
			// The allowed values are words in the file, so they are listed as written.
			throw problem(label, ProfileText.NOT_CHOICE, describe(json.get(field)), String.join(", ", allowed));
		}

		int color(String field) throws ProfileFormatException {
			String text = string(field);
			ZoneColors.Parsed parsed = ZoneColors.parseHex(text);
			if (parsed.error() != null) {
				throw problem(field, ProfileText.NOT_COLOR, quote(text));
			}
			return parsed.rgb();
		}

		Fields object(String field, Message childWhere, Set<String> known) throws ProfileFormatException {
			JsonElement value = require(field);
			if (!value.isJsonObject()) {
				throw problem(field, ProfileText.NOT_OBJECT, describe(value));
			}
			return new Fields(value.getAsJsonObject(), childWhere, warnings, known);
		}

		JsonArray array(String field) throws ProfileFormatException {
			JsonElement value = require(field);
			if (!value.isJsonArray()) {
				throw problem(field, ProfileText.NOT_LIST, describe(value));
			}
			return value.getAsJsonArray();
		}

		/** Item {@code i} of a list, which must be an object; {@code itemWhere} is its place ("Rule 3"). */
		Fields element(JsonArray array, int i, Message itemWhere, Set<String> known) throws ProfileFormatException {
			JsonElement value = array.get(i);
			if (!value.isJsonObject()) {
				throw ProfileText.problem(ProfileText.ITEM_NOT_OBJECT, itemWhere, describe(value));
			}
			return new Fields(value.getAsJsonObject(), itemWhere, warnings, known);
		}

		// ---- Forgiving reads, for cue settings: a value that cannot be used is ignored with a warning ----

		/** Adds "Place, field f: what, so it was ignored." to the warnings. */
		void ignore(String field, Message what) {
			if (warnings != null) {
				warnings.add(Message.of(ProfileText.IGNORED, where == null ? Message.of(ProfileText.PLACE_SETTINGS) : where, field, what));
			}
		}

		/** True or false, or {@code null} if the field is missing or not true or false (then with a warning). */
		Boolean optionalBool(String field) {
			if (!has(field)) {
				return null;
			}
			JsonElement value = json.get(field);
			if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
				return value.getAsBoolean();
			}
			ignore(field, Message.of(ProfileText.NOT_BOOLEAN, describe(value)));
			return null;
		}

		/** A color like {@code "#A9BCD0"}, or {@code null} if missing or not a color (then with a warning). */
		Integer optionalColor(String field) {
			if (!has(field)) {
				return null;
			}
			JsonElement value = json.get(field);
			if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
				ZoneColors.Parsed parsed = ZoneColors.parseHex(value.getAsString());
				if (parsed.error() == null) {
					return parsed.rgb();
				}
			}
			ignore(field, Message.of(ProfileText.NOT_COLOR, describe(value)));
			return null;
		}

		/** A whole number from {@code min} to {@code max}, or {@code null} if missing or not one (then with a warning). */
		Integer optionalWhole(String field, int min, int max) {
			if (!has(field)) {
				return null;
			}
			JsonElement value = json.get(field);
			Message problem = wholeProblem(value, min, max);
			if (problem == null) {
				return value.getAsInt();
			}
			ignore(field, problem);
			return null;
		}

		/** Why {@code value} is not a whole number from {@code min} to {@code max}, or {@code null} if it is. */
		private static Message wholeProblem(JsonElement value, int min, int max) {
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !Double.isFinite(value.getAsDouble())) {
				return Message.of(ProfileText.NOT_NUMBER, describe(value));
			}
			double number = value.getAsDouble();
			if (number != Math.rint(number)) {
				return Message.of(ProfileText.NOT_WHOLE, describe(value));
			}
			if (number > max) {
				return Message.of(ProfileText.ABOVE_MAX, format(number), max);
			}
			if (number < min) {
				return Message.of(ProfileText.BELOW_MIN, format(number), min);
			}
			return null;
		}

		/**
		 * The cue box position, {@code {"x": -20, "y": 10}}: both parts, each a whole number within
		 * ±{@value PlaceCues#MAX_OFFSET}, or {@code null} if missing or unusable (then with a warning).
		 */
		PlaceCues.Offset optionalOffset(String field) {
			if (!has(field)) {
				return null;
			}
			JsonElement value = json.get(field);
			if (!value.isJsonObject()) {
				ignore(field, Message.of(ProfileText.NOT_OBJECT, describe(value)));
				return null;
			}
			Fields o = new Fields(value.getAsJsonObject(), Message.of(ProfileText.FIELD_IN, where, field), warnings, OFFSET_FIELDS);
			Integer x = o.optionalWhole("x", -PlaceCues.MAX_OFFSET, PlaceCues.MAX_OFFSET);
			Integer y = o.optionalWhole("y", -PlaceCues.MAX_OFFSET, PlaceCues.MAX_OFFSET);
			if (x == null || y == null) {
				if (!o.has("x") || !o.has("y")) {
					ignore(field, Message.of(ProfileText.OFFSET_PART_MISSING));
				}
				return null;
			}
			return new PlaceCues.Offset(x, y);
		}
	}
}
