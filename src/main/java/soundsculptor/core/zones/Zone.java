package soundsculptor.core.zones;

import java.util.Objects;

import soundsculptor.core.cue.PlaceCues;
import soundsculptor.core.sets.PlaceSets;

/**
 * A zone (PRD FR-8): a place the player draws, with its own attached Sound Sets. While the player's feet block is
 * inside it, its sets decide how every sound plays, wherever the sound comes from (FR-8.4, FR-8.7).
 *
 * <ul>
 *   <li>Every zone is fixed (FR-8.1): its {@code anchor} is the shape's geometric center, and it is active only in its
 *       own server or world ({@code worldKey}) and dimension.</li>
 *   <li>Edges are hard (FR-8.5): a block is in the zone or it is not.</li>
 * </ul>
 * Color and display settings belong to the profile model; cue settings belong to the zone (FR-7, decided 2026-11-21).
 *
 * @param sets the Sound Sets attached to the zone, and its Quick changes, as for Everywhere (FR-8.6)
 * @param cues the zone's cue settings (FR-7); each one left Unchanged passes on to the next bigger zone, then Everywhere
 */
public record Zone(String id, String name, boolean enabled, String worldKey, String dimension, Point anchor, Shape shape,
		PlaceSets sets, PlaceCues cues) {
	public Zone {
		if (id == null || worldKey == null || dimension == null || anchor == null || shape == null) {
			throw new IllegalArgumentException("zone id, world key, dimension, anchor and shape are required");
		}
		Objects.requireNonNull(sets, "sets");
		Objects.requireNonNull(cues, "cues");
	}

	/** A zone whose cue settings are all Unchanged. */
	public Zone(String id, String name, boolean enabled, String worldKey, String dimension, Point anchor, Shape shape,
			PlaceSets sets) {
		this(id, name, enabled, worldKey, dimension, anchor, shape, sets, PlaceCues.UNCHANGED);
	}

	/** The same zone with other attached sets (FR-8.6). */
	public Zone withSets(PlaceSets value) {
		return new Zone(id, name, enabled, worldKey, dimension, anchor, shape, value, cues);
	}

	/** The same zone with other cue settings (FR-7). */
	public Zone withCues(PlaceCues value) {
		return new Zone(id, name, enabled, worldKey, dimension, anchor, shape, sets, value);
	}

	/** The same zone turned on or off. */
	public Zone withEnabled(boolean value) {
		return new Zone(id, name, value, worldKey, dimension, anchor, shape, sets, cues);
	}

	/** The same zone in another world (used when a copied world gets its own profile, FR-11.1). */
	public Zone withWorldKey(String value) {
		return new Zone(id, name, enabled, value, dimension, anchor, shape, sets, cues);
	}

	/** Whether the zone is on and in this world and dimension. */
	public boolean isActiveIn(String worldKey, String dimension) {
		return enabled && this.worldKey.equals(worldKey) && this.dimension.equals(dimension);
	}

	/**
	 * Whether the block at {@code (bx, by, bz)} is inside the shape (FR-8.4): its center is, boundary included. Ignores
	 * world, dimension and on/off; see {@link #isActiveIn}. Allocation-free.
	 */
	public boolean containsBlock(int bx, int by, int bz) {
		return shape.contains(bx + 0.5 - anchor.x(), by + 0.5 - anchor.y(), bz + 0.5 - anchor.z());
	}
}
