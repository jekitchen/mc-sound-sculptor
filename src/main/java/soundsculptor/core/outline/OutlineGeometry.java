package soundsculptor.core.outline;

import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

/**
 * Builds the line segments for a zone's outline (PRD FR-10.2, FR-10.3):
 * <ul>
 *   <li>box: its 12 edges;</li>
 *   <li>cylinder: top and bottom circles plus four vertical lines.</li>
 * </ul>
 *
 * <p>Build once per zone and cache: outlines only change when the zone is edited.
 */
public final class OutlineGeometry {
	/** Segments per circle for small shapes, so they still look round. */
	public static final int MIN_CIRCLE_SEGMENTS = 16;
	/** Segments per circle for big shapes, to bound drawing cost (FR-10.6: 64 zones under 0.3 ms a frame). */
	public static final int MAX_CIRCLE_SEGMENTS = 96;

	private OutlineGeometry() {
	}

	public static Outline of(Zone zone) {
		return new Outline(lines(zone.shape(), zone.anchor()));
	}

	private static double[] lines(Shape shape, Point a) {
		return switch (shape) {
			case Shape.Box b -> box(a, b.width() / 2, b.height() / 2, b.depth() / 2);
			case Shape.Cylinder c -> cylinder(a, c.radius(), c.height() / 2);
		};
	}

	private static double[] box(Point a, double hx, double hy, double hz) {
		double x0 = a.x() - hx;
		double x1 = a.x() + hx;
		double y0 = a.y() - hy;
		double y1 = a.y() + hy;
		double z0 = a.z() - hz;
		double z1 = a.z() + hz;
		return new double[] {
				// Bottom rectangle
				x0, y0, z0, x1, y0, z0,
				x1, y0, z0, x1, y0, z1,
				x1, y0, z1, x0, y0, z1,
				x0, y0, z1, x0, y0, z0,
				// Top rectangle
				x0, y1, z0, x1, y1, z0,
				x1, y1, z0, x1, y1, z1,
				x1, y1, z1, x0, y1, z1,
				x0, y1, z1, x0, y1, z0,
				// Vertical edges
				x0, y0, z0, x0, y1, z0,
				x1, y0, z0, x1, y1, z0,
				x1, y0, z1, x1, y1, z1,
				x0, y0, z1, x0, y1, z1,
		};
	}

	private static double[] cylinder(Point a, double r, double halfHeight) {
		int n = circleSegments(r);
		double[] coords = new double[(2 * n + 4) * 6];
		int k = circle(coords, 0, a, r, -halfHeight, n);
		k = circle(coords, k, a, r, halfHeight, n);
		double[][] sides = {{r, 0}, {0, r}, {-r, 0}, {0, -r}};
		for (double[] s : sides) {
			coords[k++] = a.x() + s[0];
			coords[k++] = a.y() - halfHeight;
			coords[k++] = a.z() + s[1];
			coords[k++] = a.x() + s[0];
			coords[k++] = a.y() + halfHeight;
			coords[k++] = a.z() + s[1];
		}
		return coords;
	}

	private static int circleSegments(double r) {
		return Math.max(MIN_CIRCLE_SEGMENTS, Math.min(MAX_CIRCLE_SEGMENTS, (int) Math.ceil(r * 2)));
	}

	/**
	 * Writes a closed horizontal circle of {@code n} segments around the anchor, {@code dy} above it (a cylinder's top
	 * or bottom).
	 */
	private static int circle(double[] coords, int k, Point a, double r, double dy, int n) {
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n; // closes the circle on the exact first point
			k = put(coords, k, a, r, i, n, dy);
			k = put(coords, k, a, r, j, n, dy);
		}
		return k;
	}

	private static int put(double[] coords, int k, Point a, double r, int step, int n, double dy) {
		double angle = 2 * Math.PI * step / n;
		coords[k] = a.x() + Math.cos(angle) * r;
		coords[k + 1] = a.y() + dy;
		coords[k + 2] = a.z() + Math.sin(angle) * r;
		return k + 3;
	}
}
