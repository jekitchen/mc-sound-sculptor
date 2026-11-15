package soundsculptor.core.lists;

import java.util.ArrayList;
import java.util.List;

import soundsculptor.core.zones.FeetBlock;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

/**
 * The Zones tab row (PRD 6.1): shape, and where the zone is. The color swatch and name come straight from the
 * zone.
 *
 * <p>These describe only the player's own zones, so the exact distance to a zone's anchor is fine (it is a point the
 * player chose).
 */
public final class ZoneSummary {
	public static final String BOX = "soundsculptor.summary.shape.box";
	public static final String CYLINDER = "soundsculptor.summary.shape.cylinder";

	private ZoneSummary() {
	}

	/**
	 * @param worldKey    the world the player is in now
	 * @param dimension   the player's dimension now
	 * @param feet        the block the player's feet are in
	 */
	public static List<SummaryPart> parts(Zone zone, String worldKey, String dimension, FeetBlock feet) {
		List<SummaryPart> parts = new ArrayList<>();
		parts.add(shapePart(zone.shape()));
		parts.add(location(zone, worldKey, dimension, feet).part());
		return List.copyOf(parts);
	}

	public static SummaryPart shapePart(Shape shape) {
		return switch (shape) {
			case Shape.Box b -> SummaryPart.of(BOX, Numbers.plain(b.width()), Numbers.plain(b.height()), Numbers.plain(b.depth()));
			case Shape.Cylinder c -> SummaryPart.of(CYLINDER, Numbers.plain(c.radius()), Numbers.plain(c.height()));
		};
	}

	/**
	 * Here if the player's feet block is in the zone (FR-8.4), else the rounded distance from that block to the anchor.
	 * A zone in another world or dimension says so instead. On/off does not change it.
	 */
	public static ZoneLocation location(Zone zone, String worldKey, String dimension, FeetBlock feet) {
		if (!zone.worldKey().equals(worldKey)) {
			return new ZoneLocation(LocationKind.OTHER_WORLD, 0);
		}
		if (!zone.dimension().equals(dimension)) {
			return new ZoneLocation(LocationKind.OTHER_DIMENSION, 0);
		}
		Point player = feet.center();
		double dx = player.x() - zone.anchor().x();
		double dy = player.y() - zone.anchor().y();
		double dz = player.z() - zone.anchor().z();
		if (zone.containsBlock(feet.x(), feet.y(), feet.z())) {
			return new ZoneLocation(LocationKind.HERE, 0);
		}
		return new ZoneLocation(LocationKind.AWAY, Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)));
	}
}
