package soundsculptor.core.recent;

/**
 * The time since a sound last played, as a row shows it (PRD FR-1.2): "&lt;1m ago" under a minute, then whole
 * minutes ("1m ago", "2m ago"), then "&gt;1h ago". The label changes at most once a minute, so it stays
 * still while the player reads the row; hovering it shows the {@link #exact} time, to the second.
 *
 * @param value whole minutes for {@link Unit#MINUTES}, otherwise 0
 */
public record TimeAgo(Unit unit, long value) {
	private static final long MINUTE_MS = 60_000;
	private static final long HOUR_MS = 60 * MINUTE_MS;

	public enum Unit {
		UNDER_MINUTE("under_minute"),
		MINUTES("minutes"),
		OVER_HOUR("over_hour");

		private final String key;

		Unit(String key) {
			this.key = key;
		}

		public String translationKey() {
			return "soundsculptor.sounds.ago." + key;
		}
	}

	/** Hours, minutes (0–59) and seconds (0–59) since the play. */
	public record Exact(long hours, long minutes, long seconds) {
	}

	/** The label for {@code millis} since the play; a negative time (the clock went back) counts as just now. */
	public static TimeAgo of(long millis) {
		long ms = Math.max(0, millis);
		if (ms < MINUTE_MS) {
			return new TimeAgo(Unit.UNDER_MINUTE, 0);
		}
		if (ms < HOUR_MS) {
			return new TimeAgo(Unit.MINUTES, ms / MINUTE_MS);
		}
		return new TimeAgo(Unit.OVER_HOUR, 0);
	}

	/** The exact time since the play, to the second, for the tooltip. */
	public static Exact exact(long millis) {
		long seconds = Math.max(0, millis) / 1000;
		return new Exact(seconds / 3600, seconds / 60 % 60, seconds % 60);
	}

	/**
	 * The translation key for this label. "1m ago" has its own key, so a language can say "1 minute" apart from
	 * "2 minutes".
	 */
	public String translationKey() {
		return unit == Unit.MINUTES && value == 1 ? "soundsculptor.sounds.ago.minute" : unit.translationKey();
	}
}
