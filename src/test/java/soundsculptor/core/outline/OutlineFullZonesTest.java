package soundsculptor.core.outline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD FR-10.2 and FR-10.3 for v1 zones: cylinder outlines and culling. */
class OutlineFullZonesTest {
	private static final String WORLD = "w";
	private static final String OVERWORLD = "minecraft:overworld";

	private static Zone zone(Shape shape) {
		return new Zone("z", "Z", true, WORLD, OVERWORLD, new Point(0, 64, 0), shape, PlaceSets.EMPTY);
	}

	@Test
	void cylinderIsTopAndBottomCirclesPlusFourVerticalLines() {
		Outline o = OutlineGeometry.of(zone(new Shape.Cylinder(5, 10)));
		int vertical = 0;
		for (int i = 0; i < o.segmentCount(); i++) {
			if (o.y1(i) != o.y2(i)) {
				vertical++;
				assertEquals(59, Math.min(o.y1(i), o.y2(i)), 1e-9);
				assertEquals(69, Math.max(o.y1(i), o.y2(i)), 1e-9);
			} else {
				assertTrue(o.y1(i) == 59 || o.y1(i) == 69, "circle points at top or bottom");
			}
			assertEquals(5, Math.hypot(o.x1(i), o.z1(i)), 1e-9, "on the curved surface");
			assertEquals(5, Math.hypot(o.x2(i), o.z2(i)), 1e-9);
		}
		assertEquals(4, vertical);
		assertEquals(0, (o.segmentCount() - 4) % 2, "two circles of equal size");
	}

	@Test
	void cylinderDistanceCullingUsesItsNearestPoint() {
		Zone c = new Zone("c", "C", true, WORLD, OVERWORLD, new Point(200, 0, 0), new Shape.Cylinder(80, 10), PlaceSets.EMPTY);
		assertTrue(OutlineVisibility.shouldDraw(c, OutlineMode.ALWAYS, null, WORLD, OVERWORLD, 0, 0, 0), "rim is 120 away");
	}
}
