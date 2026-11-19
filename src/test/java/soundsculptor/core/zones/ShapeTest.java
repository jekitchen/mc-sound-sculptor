package soundsculptor.core.zones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** PRD FR-8.3: a box (the only shape), centred on the anchor, with size limits. Offsets are from the anchor. */
class ShapeTest {
	@Test
	void boxExtendsHalfItsSizeEachWay() {
		Shape box = new Shape.Box(20, 8, 30);
		assertTrue(box.contains(10, 4, 15), "corner is inside");
		assertTrue(box.contains(-10, -4, -15));
		assertFalse(box.contains(10.01, 0, 0));
		assertFalse(box.contains(0, 4.01, 0));
		assertFalse(box.contains(0, 0, -15.01));
	}

	@Test
	void volumeIsTheGeometricVolume() {
		assertEquals(20 * 8 * 30, new Shape.Box(20, 8, 30).volume(), 1e-9);
	}

	@Test
	void boxSidesMustBeOneTo512() {
		assertThrows(IllegalArgumentException.class, () -> new Shape.Box(0.5, 10, 10));
		assertThrows(IllegalArgumentException.class, () -> new Shape.Box(10, 513, 10));
		assertThrows(IllegalArgumentException.class, () -> new Shape.Box(10, 10, 600));
		new Shape.Box(1, 1, 1);
		new Shape.Box(512, 512, 512);
	}

	@Test
	void nanSizesAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> new Shape.Box(Double.NaN, 1, 1));
	}
}
