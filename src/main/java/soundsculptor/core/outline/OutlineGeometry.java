package soundsculptor.core.outline;

import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

/**
 * Builds the line segments for a zone's outline (PRD FR-10.2, FR-10.3): a box's 12 edges.
 *
 * <p>Build once per zone and cache: outlines only change when the zone is edited.
 */
public final class OutlineGeometry {
	private OutlineGeometry() {
	}

	public static Outline of(Zone zone) {
		return new Outline(lines(zone.shape(), zone.anchor()));
	}

	private static double[] lines(Shape shape, Point a) {
		return switch (shape) {
			case Shape.Box b -> box(a, b.width() / 2, b.height() / 2, b.depth() / 2);
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
}
