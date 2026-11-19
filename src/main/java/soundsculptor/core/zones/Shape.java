package soundsculptor.core.zones;

/**
 * A zone's shape, centred on its anchor (PRD FR-8.3): a box aligned with the world grid, the only shape (spheres and
 * cylinders were dropped before release). Positions are given as offsets from the anchor. Points on the boundary count
 * as inside.
 */
public sealed interface Shape {
	/** Whether the offset {@code (dx, dy, dz)} from the anchor is inside the shape. */
	boolean contains(double dx, double dy, double dz);

	/** Geometric volume in cubic blocks, used to put zones smallest first (FR-9 step 1). */
	double volume();

	/** Distance from an outside point to the nearest point of the shape; 0 for points inside (outline culling). */
	double distanceOutside(double dx, double dy, double dz);

	/** Axis-aligned box; width along x, height along y, depth along z, each 1 to 512 blocks. */
	record Box(double width, double height, double depth) implements Shape {
		public static final double MIN_SIDE = 1;
		public static final double MAX_SIDE = 512;

		public Box {
			requireRange("width", width, MIN_SIDE, MAX_SIDE);
			requireRange("height", height, MIN_SIDE, MAX_SIDE);
			requireRange("depth", depth, MIN_SIDE, MAX_SIDE);
		}

		@Override
		public boolean contains(double dx, double dy, double dz) {
			return Math.abs(dx) <= width / 2 && Math.abs(dy) <= height / 2 && Math.abs(dz) <= depth / 2;
		}

		@Override
		public double volume() {
			return width * height * depth;
		}

		@Override
		public double distanceOutside(double dx, double dy, double dz) {
			double ox = Math.max(0, Math.abs(dx) - width / 2);
			double oy = Math.max(0, Math.abs(dy) - height / 2);
			double oz = Math.max(0, Math.abs(dz) - depth / 2);
			return Math.sqrt(ox * ox + oy * oy + oz * oz);
		}
	}

	private static void requireRange(String name, double value, double min, double max) {
		if (!(value >= min && value <= max)) {
			throw new IllegalArgumentException(name + " " + value + " is outside " + min + ".." + max);
		}
	}
}
