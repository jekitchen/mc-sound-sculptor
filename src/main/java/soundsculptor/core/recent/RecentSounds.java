package soundsculptor.core.recent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import soundsculptor.core.diag.MemoryEstimate;

/**
 * The recently played sounds log behind the Sounds tab's Recently played filter (PRD FR-1.1 to FR-1.3).
 *
 * <p>A play is recorded only if it is non-positional, or positional and within vanilla hearing range: the same set
 * vanilla subtitles would show (fair play). Sounds Sound Sculptor muted or limited are recorded too, with their status.
 * Music, UI and Voice/Speech sounds are recorded like any other (every sound is in scope).
 *
 * <p>Rows are grouped by sound ID and kept for 3 minutes after their last play, at most {@link #MAX_IDS} of them.
 * Play counts use one-second buckets, so a play leaves the count within a second of turning 3 minutes old. The
 * bucket holding a row's most recent play always counts while the row is listed. Plays a repeat limit dropped are
 * counted on their own (FR-6.8: "Limited" with a dropped count), not as plays, and keep the row on Limited for
 * {@link #LIMITED_SHOWN_MS} ms.
 *
 * <p>Not thread-safe: record and snapshot on the same thread (the render thread, where sounds start). Recording a
 * sound already in the list allocates nothing.
 */
public final class RecentSounds {
	public static final long WINDOW_MS = 180_000;
	public static final int MAX_IDS = 200;
	/**
	 * How long a row keeps showing Limited after a repeat limit dropped one of its plays (FR-6.8): the longest window a
	 * limit can have, so a limited stream reads Limited instead of flickering with each allowed play.
	 */
	public static final long LIMITED_SHOWN_MS = soundsculptor.core.sets.Limit.MAX_WINDOW_MS;

	/** Cap for the session-long sets, since servers can send arbitrary sound IDs. */
	static final int MAX_SEEN_IDS = 4096;

	private static final int BUCKETS = (int) (WINDOW_MS / 1000);

	private final Map<String, Row> rows = new HashMap<>();
	private final Map<String, String> seenCategories = new HashMap<>();
	/** Changes whenever what {@link #snapshot} or the session sets would show changes. */
	private long generation;

	/**
	 * Records one sound start.
	 *
	 * @param soundId    e.g. {@code minecraft:entity.villager.ambient}
	 * @param category   the sound source name, e.g. {@code neutral} (null is accepted and listed as unknown)
	 * @param positional whether the sound has a world position (not relative to the listener)
	 * @param distance   distance from the listener in blocks (ignored when not positional)
	 * @param audible    whether vanilla would treat it as heard (range check from the un-boosted volume)
	 * @param status     what Sound Sculptor did with this play
	 * @param zoneName   the zone that decided, or {@code null}
	 * @param nowMs      current time in milliseconds from a clock that never goes backwards, such as Minecraft's
	 *                   {@code Util.getMillis()} (not the wall clock); the same clock must be passed to {@link #snapshot}
	 */
	public void record(String soundId, String category, boolean positional, double distance, boolean audible,
			PlayStatus status, String zoneName, long nowMs) {
		if (positional && !audible) {
			return;
		}
		if (seenCategories.size() < MAX_SEEN_IDS || seenCategories.containsKey(soundId)) {
			seenCategories.put(soundId, category);
		}
		Row row = rows.get(soundId);
		if (row == null) {
			makeRoom(nowMs);
			row = new Row(soundId);
			rows.put(soundId, row);
		}
		row.add(category, DistanceBand.of(positional, distance), status, zoneName, nowMs);
		generation++;
	}

	/**
	 * A number that changes whenever a play is recorded, a row expires or is dropped, or the list is cleared; equal
	 * values mean {@link #snapshot} rows (apart from the time since the last play) and the session sets are unchanged. The Sounds tab uses it to skip a refresh that would change nothing (FR-1.6).
	 * Expired rows are dropped by {@link #snapshot}, so a caller that skips snapshots must still take one when rows may
	 * have expired.
	 */
	public long generation() {
		return generation;
	}

	/** Frees a slot for a new ID: drops expired rows, then the least recently played one. */
	private void makeRoom(long nowMs) {
		if (rows.size() < MAX_IDS) {
			return;
		}
		pruneExpired(nowMs);
		if (rows.size() < MAX_IDS) {
			return;
		}
		Row oldest = null;
		for (Row row : rows.values()) {
			if (oldest == null || row.lastPlayedMs < oldest.lastPlayedMs) {
				oldest = row;
			}
		}
		rows.remove(oldest.id);
		generation++;
	}

	private void pruneExpired(long nowMs) {
		Iterator<Row> it = rows.values().iterator();
		while (it.hasNext()) {
			if (it.next().expired(nowMs)) {
				it.remove();
				generation++;
			}
		}
	}

	/**
	 * The current rows, sorted. The list refreshes at most 4 times a second (FR-1.6), so this may allocate.
	 *
	 * @param displayName subtitle text per ID for {@link SortOrder#NAME}, or {@code null} to sort by ID; may return
	 *                    {@code null} for sounds without a subtitle
	 */
	public List<RecentRow> snapshot(long nowMs, SortOrder order, Function<String, String> displayName) {
		pruneExpired(nowMs);
		List<RecentRow> result = new ArrayList<>(rows.size());
		for (Row row : rows.values()) {
			result.add(row.toRecord(nowMs));
		}
		result.sort(comparator(order, displayName));
		return result;
	}

	private static Comparator<RecentRow> comparator(SortOrder order, Function<String, String> displayName) {
		Comparator<RecentRow> recent = Comparator.comparingLong(RecentRow::lastPlayedMs).reversed();
		return switch (order) {
			case MOST_RECENT -> recent.thenComparing(RecentRow::id);
			case PLAY_COUNT -> Comparator.comparingInt(RecentRow::count).reversed().thenComparing(recent).thenComparing(RecentRow::id);
			case NAME -> Comparator.comparing((RecentRow r) -> nameOf(r.id(), displayName)).thenComparing(RecentRow::id);
		};
	}

	private static String nameOf(String id, Function<String, String> displayName) {
		String name = displayName == null ? null : displayName.apply(id);
		return (name == null ? id : name).toLowerCase(Locale.ROOT);
	}

	/**
	 * The category a sound last played in this session within the player's hearing range, or {@code null} (search's
	 * "Seen as", FR-3.2). Out-of-range plays are deliberately not remembered: showing "Seen as" for a sound the player
	 * could not hear would reveal that it played (fair play).
	 */
	public String seenCategory(String soundId) {
		return seenCategories.get(soundId);
	}

	/**
	 * Estimated bytes this log keeps, with its session sets, for the Diagnostics memory readout (PRD 9.1). It walks
	 * every entry, so call it on demand through {@code MemoryMeter}, not per sound. Category and zone names are
	 * shared constants and not counted.
	 */
	public long estimatedBytes() {
		long bytes = MemoryEstimate.object(3 * MemoryEstimate.REFERENCE)
				+ MemoryEstimate.HASH_MAP + MemoryEstimate.hashTable(rows.size())
				+ rows.size() * (MemoryEstimate.HASH_NODE + Row.BYTES)
				+ MemoryEstimate.HASH_MAP + MemoryEstimate.hashTable(seenCategories.size())
				+ seenCategories.size() * MemoryEstimate.HASH_NODE;
		for (Row row : rows.values()) {
			bytes += MemoryEstimate.string(row.id);
		}
		for (String id : seenCategories.keySet()) {
			bytes += MemoryEstimate.string(id);
		}
		return bytes;
	}

	/** Empties the list. Session memory for search is kept. */
	public void clear() {
		rows.clear();
		generation++;
	}

	/**
	 * Mutable per-ID state. Counts live in a ring of one-second buckets covering the window: plays that started, and
	 * plays a repeat limit dropped (FR-6.8).
	 */
	private static final class Row {
		/** The row and its three bucket arrays, without the ID string. */
		static final long BYTES = MemoryEstimate.object(8 * MemoryEstimate.REFERENCE + 2 * 8)
				+ 2 * MemoryEstimate.array(4, BUCKETS) + MemoryEstimate.array(8, BUCKETS);

		final String id;
		final int[] counts = new int[BUCKETS];
		final int[] drops = new int[BUCKETS];
		final long[] seconds = new long[BUCKETS];
		String category;
		DistanceBand band;
		PlayStatus status;
		String zoneName;
		long lastPlayedMs;
		long lastDroppedMs = Long.MIN_VALUE;

		Row(String id) {
			this.id = id;
			Arrays.fill(seconds, Long.MIN_VALUE);
		}

		void add(String category, DistanceBand band, PlayStatus status, String zoneName, long nowMs) {
			long second = Math.floorDiv(nowMs, 1000);
			int i = (int) Math.floorMod(second, (long) BUCKETS);
			if (seconds[i] != second) {
				seconds[i] = second;
				counts[i] = 0;
				drops[i] = 0;
			}
			if (status == PlayStatus.LIMITED) {
				drops[i]++;
				lastDroppedMs = nowMs;
			} else {
				counts[i]++;
			}
			this.category = category;
			this.band = band;
			this.status = status;
			this.zoneName = zoneName;
			this.lastPlayedMs = nowMs;
		}

		boolean expired(long nowMs) {
			return nowMs - lastPlayedMs >= WINDOW_MS;
		}

		/**
		 * Plays (or, with {@code dropped}, dropped plays) in the last 3 minutes, to the second. While the row is listed,
		 * the bucket of its most recent play counts even at the window's edge, so a listed row never shows 0 for what
		 * its latest play was. (That bucket cannot have been reused: reusing it needs a newer play, which would be the
		 * most recent one.)
		 */
		int count(long nowMs, boolean dropped) {
			long oldestKept = Math.floorDiv(nowMs, 1000) - BUCKETS;
			long lastSecond = Math.floorDiv(lastPlayedMs, 1000);
			boolean listed = !expired(nowMs);
			int[] buckets = dropped ? drops : counts;
			int total = 0;
			for (int i = 0; i < BUCKETS; i++) {
				if (seconds[i] > oldestKept || (listed && seconds[i] == lastSecond)) {
					total += buckets[i];
				}
			}
			return total;
		}

		RecentRow toRecord(long nowMs) {
			// A drop in the last few seconds keeps the row on Limited, so a limited stream does not flicker (FR-6.8).
			boolean limitedLately = lastDroppedMs != Long.MIN_VALUE && nowMs - lastDroppedMs < LIMITED_SHOWN_MS;
			return new RecentRow(id, category, count(nowMs, false), lastPlayedMs, band, limitedLately ? PlayStatus.LIMITED : status,
					zoneName, count(nowMs, true));
		}
	}
}
