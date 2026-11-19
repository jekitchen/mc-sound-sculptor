package soundsculptor.core.zones;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Distance from outside a zone to its nearest point (outline culling, FR-10.3). Offsets are from the anchor. */
class ShapeDistanceTest {
	@Test
	void boxDistanceOutsideMeasuresToTheNearestPoint() {
		Shape b = new Shape.Box(2, 2, 2);
		assertEquals(3, b.distanceOutside(4, 0, 0), 1e-9);
		assertEquals(Math.sqrt(2), b.distanceOutside(2, 2, 0), 1e-9, "diagonal from a corner edge");
		assertEquals(0, b.distanceOutside(0.5, 0.5, 0.5), 1e-9);
	}
}
