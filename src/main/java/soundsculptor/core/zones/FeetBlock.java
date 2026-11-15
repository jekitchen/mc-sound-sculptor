package soundsculptor.core.zones;

/**
 * The block the local player's feet are in (PRD FR-8.4): where you are, for zones. The player's own block position,
 * never the camera's, so third person does not change it.
 */
public record FeetBlock(int x, int y, int z) {
	/** Whether {@code zone} is on, in this world and dimension, and contains this block. */
	public boolean isIn(Zone zone, String worldKey, String dimension) {
		return zone.isActiveIn(worldKey, dimension) && zone.containsBlock(x, y, z);
	}

	/** The center of the block, for distances. */
	public Point center() {
		return new Point(x + 0.5, y + 0.5, z + 0.5);
	}
}
