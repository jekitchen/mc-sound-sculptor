package soundsculptor.core.trace;

import soundsculptor.core.zones.Zone;

/**
 * One zone the player's feet were in when the sound started (PRD FR-14.2 item 2), listed smallest first.
 *
 * @param sets how the zone's sets treated the sound
 */
public record ZoneLine(Zone zone, PlaceTrace sets) {
}
