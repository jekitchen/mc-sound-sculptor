package soundsculptor.core.lists;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.TestEnglish;
import soundsculptor.core.zones.FeetBlock;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD 6.1: the Zones tab row: shape, mode, and "Here / 120 blocks away / Other world". */
class ZoneSummaryTest {
	private static final TestEnglish EN = new TestEnglish();
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";

	private static Zone cylinder(double radius) {
		return new Zone("z", "Hall", true, WORLD, OVERWORLD, new Point(0, 64, 0), new Shape.Cylinder(radius, 2 * radius), PlaceSets.EMPTY);
	}

	/** With the player's feet in block {@code (x, 64, 0)}. */
	private static String where(Zone zone, String world, String dimension, int x) {
		return ZoneSummary.location(zone, world, dimension, new FeetBlock(x, 64, 0)).text(EN);
	}

	@Test
	void hereWhenThePlayerIsInside() {
		assertEquals("Here", where(cylinder(10), WORLD, OVERWORLD, 3));
	}

	@Test
	void roundedBlocksFromTheFeetBlockCenterToTheAnchorWhenOutside() {
		assertEquals("120 blocks away", where(cylinder(10), WORLD, OVERWORLD, 119));
		assertEquals("121 blocks away", where(cylinder(10), WORLD, OVERWORLD, 120));
		assertEquals(LocationKind.AWAY, ZoneSummary.location(cylinder(10), WORLD, OVERWORLD, new FeetBlock(50, 64, 0)).kind());
	}

	@Test
	void theEdgeBlockIsHereAndTheNextIsNot() {
		// Radius 10 from x = 0: block 9's center (9.5) is inside, block 10's (10.5) is not.
		assertEquals("Here", where(cylinder(10), WORLD, OVERWORLD, 9));
		assertEquals("11 blocks away", where(cylinder(10), WORLD, OVERWORLD, 10));
	}

	@Test
	void aZoneThatIsOffIsStillHere() {
		assertEquals("Here", where(cylinder(10).withEnabled(false), WORLD, OVERWORLD, 3));
	}

	@Test
	void otherWorldBeforeOtherDimension() {
		assertEquals("Other world", where(cylinder(10), "other.example.net", "minecraft:the_nether", 0));
		assertEquals("Other dimension", where(cylinder(10), WORLD, "minecraft:the_nether", 0));
	}


	@Test
	void shapeAndWhereChips() {
		assertEquals(List.of("Cylinder, radius 10, height 20", "Here"), chips(cylinder(10)));
		Zone box = new Zone("b", "Farm", true, WORLD, OVERWORLD, new Point(0, 64, 0), new Shape.Box(20, 8, 30.5), PlaceSets.EMPTY);
		assertEquals(List.of("Box 20 × 8 × 30.5", "51 blocks away"), chipsAt(box, 50));
		Zone tower = new Zone("c", "Tower", true, WORLD, OVERWORLD, new Point(0, 64, 0), new Shape.Cylinder(6, 40), PlaceSets.EMPTY);
		assertEquals(List.of("Cylinder, radius 6, height 40", "Here"), chips(tower));
	}

	private static List<String> chips(Zone zone) {
		return chipsAt(zone, 0);
	}

	private static List<String> chipsAt(Zone zone, int x) {
		return ZoneSummary.parts(zone, WORLD, OVERWORLD, new FeetBlock(x, 64, 0)).stream().map(p -> p.text(EN)).toList();
	}
}
