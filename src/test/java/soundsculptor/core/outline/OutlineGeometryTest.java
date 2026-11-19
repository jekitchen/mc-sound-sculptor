package soundsculptor.core.outline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD FR-10.2: a box's 12 edges. Coordinates are world coordinates. */
class OutlineGeometryTest {
	private static Zone zone(Point anchor, Shape shape) {
		return new Zone("z", "Z", true, "w", "minecraft:overworld", anchor, shape, PlaceSets.EMPTY);
	}

	@Test
	void boxHasItsTwelveEdgesAroundTheAnchor() {
		Outline o = OutlineGeometry.of(zone(new Point(10, 64, -5), new Shape.Box(4, 2, 6)));
		assertEquals(12, o.segmentCount());
		Set<String> corners = new HashSet<>();
		for (int i = 0; i < o.segmentCount(); i++) {
			corners.add(o.x1(i) + "," + o.y1(i) + "," + o.z1(i));
			corners.add(o.x2(i) + "," + o.y2(i) + "," + o.z2(i));
			// Each edge runs along exactly one axis.
			int changed = (o.x1(i) != o.x2(i) ? 1 : 0) + (o.y1(i) != o.y2(i) ? 1 : 0) + (o.z1(i) != o.z2(i) ? 1 : 0);
			assertEquals(1, changed, "edge " + i);
		}
		assertEquals(8, corners.size());
		assertTrue(corners.contains("8.0,63.0,-8.0"));
		assertTrue(corners.contains("12.0,65.0,-2.0"));
	}
}
