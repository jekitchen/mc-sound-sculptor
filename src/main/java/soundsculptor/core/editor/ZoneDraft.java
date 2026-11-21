package soundsculptor.core.editor;

import java.util.EnumMap;
import java.util.List;

import soundsculptor.core.cue.PlaceCues;
import soundsculptor.core.outline.ColorError;
import soundsculptor.core.outline.ZoneColors;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.zones.BoxEdit;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

/**
 * The zone editor's values (PRD 6.1): name, color, coordinates and the box's size (zones are boxes only, FR-8.3). The
 * zone's attached sets are edited in their own editors and kept here as they are; its cue settings (FR-7) are edited in
 * its Cues pop-up and saved with the zone.
 *
 * <p>The color is kept beside the zone (it belongs to the profile model): read it with {@link #color()}.
 */
public final class ZoneDraft extends Draft<ZoneDraft.Field, Zone> {
	/** The zone editor's fields, for mapping them to widgets. */
	public enum Field implements DraftField {
		NAME(true),
		ENABLED(false),
		/** Hex, e.g. {@code #E69F00}. */
		COLOR(true),
		X(true),
		Y(true),
		Z(true),
		/** Set with the anchor by {@link #setPosition}. */
		WORLD_KEY(false),
		DIMENSION(false),
		WIDTH(true),
		HEIGHT(true),
		DEPTH(true);

		private final boolean text;

		Field(boolean text) {
			this.text = text;
		}

		@Override
		public boolean isText() {
			return text;
		}
	}

	/** Size of a new zone, and what a size's reset button puts back: a 10-block box. */
	public static final double DEFAULT_SIZE = 10;

	private final String id;
	private final PlaceSets sets;

	/** The zone's cue settings as edited in its Cues pop-up (FR-7), and as they were when the editor opened. */
	private PlaceCues cues;
	private final PlaceCues originalCues;

	private ZoneDraft(String id, PlaceSets sets, PlaceCues cues, EnumMap<Field, String> values, EnumMap<Field, String> defaults) {
		super(Field.class, values, defaults);
		this.id = id;
		this.sets = sets;
		this.cues = cues;
		this.originalCues = cues;
	}

	/**
	 * A new fixed zone (FR-8.1a, FR-8.3): a {@value #DEFAULT_SIZE}-block box around {@code position} (normally
	 * "my position"), with no sets yet. The fields show the position as given; the
	 * saved box is snapped to the block grid like every box.
	 *
	 * @param rgb the next palette color ({@link ZoneColors#next}), also the color's reset value
	 */
	public static ZoneDraft blank(String id, String name, String worldKey, String dimension, Point position, int rgb) {
		Shape.Box box = new Shape.Box(DEFAULT_SIZE, DEFAULT_SIZE, DEFAULT_SIZE);
		Zone zone = new Zone(id, name, true, worldKey, dimension, position, box, PlaceSets.EMPTY);
		return of(zone, rgb, rgb);
	}

	/**
	 * Editing an existing zone.
	 *
	 * @param rgb        the zone's color
	 * @param defaultRgb what the color's reset button puts back
	 */
	public static ZoneDraft of(Zone zone, int rgb, int defaultRgb) {
		EnumMap<Field, String> values = new EnumMap<>(Field.class);
		values.put(Field.NAME, zone.name() == null ? "" : zone.name());
		values.put(Field.ENABLED, Boolean.toString(zone.enabled()));
		values.put(Field.COLOR, ZoneColors.format(rgb));
		values.put(Field.X, Numbers.format(zone.anchor().x()));
		values.put(Field.Y, Numbers.format(zone.anchor().y()));
		values.put(Field.Z, Numbers.format(zone.anchor().z()));
		values.put(Field.WORLD_KEY, zone.worldKey());
		values.put(Field.DIMENSION, zone.dimension());
		String size = Numbers.format(DEFAULT_SIZE);
		switch (zone.shape()) {
			case Shape.Box box -> {
				values.put(Field.WIDTH, Numbers.format(box.width()));
				values.put(Field.HEIGHT, Numbers.format(box.height()));
				values.put(Field.DEPTH, Numbers.format(box.depth()));
			}
		}

		// Name, coordinates, world and dimension have no app-wide default: reset puts back the value at opening.
		EnumMap<Field, String> defaults = new EnumMap<>(values);
		defaults.put(Field.ENABLED, "true");
		defaults.put(Field.COLOR, ZoneColors.format(defaultRgb));
		for (Field f : List.of(Field.WIDTH, Field.HEIGHT, Field.DEPTH)) {
			defaults.put(f, size);
		}
		return new ZoneDraft(zone.id(), zone.sets(), zone.cues(), values, defaults);
	}

	/** The zone's cue settings (FR-7), each one set or Unchanged. */
	public PlaceCues cues() {
		return cues;
	}

	/** From the zone's Cues pop-up; saved with the zone. */
	public void setCues(PlaceCues value) {
		cues = java.util.Objects.requireNonNull(value, "cues");
	}

	/** Changed cue settings count as unsaved changes too (FR-12.4). */
	@Override
	public boolean isDirty() {
		return super.isDirty() || !cues.equals(originalCues);
	}

	public boolean enabled() {
		return Boolean.parseBoolean(text(Field.ENABLED));
	}

	public void setEnabled(boolean value) {
		put(Field.ENABLED, Boolean.toString(value));
	}

	/** FR-8.1 "Set to my position" or "Set to the block I'm looking at": the anchor and where it is. */
	public void setPosition(Point position, String worldKey, String dimension) {
		put(Field.X, Numbers.format(position.x()));
		put(Field.Y, Numbers.format(position.y()));
		put(Field.Z, Numbers.format(position.z()));
		put(Field.WORLD_KEY, worldKey);
		put(Field.DIMENSION, dimension);
	}

	/** A box zone at {@code center} with {@code box}'s size, e.g. from marking corners or moving a side. */
	public void setBox(Point center, Shape.Box box, String worldKey, String dimension) {
		setPosition(center, worldKey, dimension);
		put(Field.WIDTH, Numbers.format(box.width()));
		put(Field.HEIGHT, Numbers.format(box.height()));
		put(Field.DEPTH, Numbers.format(box.depth()));
	}

	/** The color as {@code 0xRRGGBB}. Valid only while the color field has no error. */
	public int color() {
		return ZoneColors.parseHex(text(Field.COLOR)).rgb();
	}

	@Override
	protected FieldError validate(Field field, String text) {
		return switch (field) {
			case NAME -> text.isBlank() ? FieldError.of(FieldError.REQUIRED) : null;
			case COLOR -> {
				ColorError error = ZoneColors.parseHex(text).error();
				yield error == null ? null : FieldError.of(error.translationKey());
			}
			case X, Y, Z -> Numbers.checkNumber(text);
			// Boxes line up with the block grid: whole-block sizes (M8 review).
			case WIDTH, HEIGHT, DEPTH -> Numbers.checkWhole(text, (int) Shape.Box.MIN_SIDE, (int) Shape.Box.MAX_SIDE);
			default -> null;
		};
	}

	@Override
	protected boolean same(Field field, String a, String b) {
		return switch (field) {
			case NAME -> a.trim().equals(b.trim());
			case COLOR -> {
				ZoneColors.Parsed pa = ZoneColors.parseHex(a);
				ZoneColors.Parsed pb = ZoneColors.parseHex(b);
				yield pa.error() == null && pb.error() == null ? pa.rgb() == pb.rgb() : a.trim().equals(b.trim());
			}
			case X, Y, Z, WIDTH, HEIGHT, DEPTH -> Numbers.same(a, b);
			default -> a.equals(b);
		};
	}

	@Override
	protected Zone create() {
		Shape.Box shape = new Shape.Box(number(Field.WIDTH), number(Field.HEIGHT), number(Field.DEPTH));
		// Every edge on a block boundary, however the center was set (typed, My position, Looking at).
		Point anchor = BoxEdit.snap(new Point(number(Field.X), number(Field.Y), number(Field.Z)), shape).center();
		return new Zone(id, text(Field.NAME).trim(), enabled(), text(Field.WORLD_KEY), text(Field.DIMENSION), anchor, shape,
				sets, cues);
	}

	private double number(Field field) {
		return Numbers.decimal(text(field));
	}
}
