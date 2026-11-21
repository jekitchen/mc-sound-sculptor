package soundsculptor.core.recent;

/**
 * One row of the recently played list (PRD FR-1.2): every play of one sound ID in the last 3 minutes. Band and zone
 * come from the latest play. The screen adds the subtitle text and "2m ago" from {@code lastPlayedMs}.
 *
 * @param count    plays in the last 3 minutes that started (played, muted or turned down); plays a repeat limit dropped
 *                 are counted in {@code dropped} instead
 * @param status   the latest play's status, or {@link PlayStatus#LIMITED} while a repeat limit dropped a play in the
 *                 last {@link RecentSounds#LIMITED_SHOWN_MS} ms, so a limited stream does not flicker between
 *                 Playing and Limited (FR-6.8)
 * @param zoneName the zone that decided the latest play, or {@code null} if Everywhere or nothing did
 * @param dropped  plays a repeat limit dropped in the last 3 minutes (FR-6.8: "Limited" with a dropped count)
 */
public record RecentRow(String id, String category, int count, long lastPlayedMs, DistanceBand band, PlayStatus status,
		String zoneName, int dropped) {
	/** A row with no dropped plays. */
	public RecentRow(String id, String category, int count, long lastPlayedMs, DistanceBand band, PlayStatus status,
			String zoneName) {
		this(id, category, count, lastPlayedMs, band, status, zoneName, 0);
	}
}
