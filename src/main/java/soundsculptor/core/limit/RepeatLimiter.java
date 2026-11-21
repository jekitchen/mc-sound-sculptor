package soundsculptor.core.limit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soundsculptor.core.diag.MemoryEstimate;
import soundsculptor.core.sets.Limit;

/**
 * Repeat limiting (PRD FR-6): caps how often the sounds a rule decides may start.
 *
 * <p>Call {@link #tryStart} once when a sound starts, with the rule that won the limit setting (from
 * {@code SoundDecision.limitRule()}). It returns false when the play should be dropped. Looping and long sounds count
 * once, when they start, and a sound already playing is never cut off (FR-6.6): both are the caller's side. Each rule
 * has its own counters, which only count sounds that rule decided (FR-6.7). Time is real time, not game ticks, so lag
 * does not change the cap.
 *
 * <p><b>Memory caps (PRD 9.1).</b> A per-source rule keeps up to {@value #MAX_SOURCES_PER_RULE} positions in each
 * place (FR-6.5; a rule ID names its place), forgetting its own least recently used one. All rules together keep at
 *  most {@value #MAX_COUNTERS} counters in one {@link CounterPool}, sized to the 8 MB budget with room to spare: at the highest count,
 * three per-source rules filled to 1,024 positions each. When it is full, the rule holding the most counters gives one up,
 * so a busy rule recycles its own positions and never pushes out another's (decided 2026-11-21). At most
 * {@value #MAX_RULES} rules are remembered, least recently used forgotten first. Forgetting only means that counter
 * starts afresh, so in the worst case a few more sounds play than the limit allows, never fewer.
 *
 * <p>After the first sight of each rule, {@link #tryStart} allocates nothing. Not thread-safe: call it from the
 * thread that starts sounds.
 */
public final class RepeatLimiter {
	/** Per-source positions remembered per rule (FR-6.5). */
	public static final int MAX_SOURCES_PER_RULE = 1024;
	/** Counters remembered across all rules together: the limiter is at most about 3.4 MB when every one counts 100 plays. */
	public static final int MAX_COUNTERS = 3072;
	/** Rules whose counters and dropped counts are remembered. */
	public static final int MAX_RULES = 4096;

	private final Map<String, RuleCounters> rules = new HashMap<>();
	private final CounterPool pool = new CounterPool(MAX_COUNTERS);
	private long serials;
	private long tick;

	/**
	 * @param ruleId the deciding rule's ID (stable across profile saves)
	 * @param limit  that rule's limit
	 * @param x      sound position, used for per-source counting
	 * @param nowMs  real time in milliseconds from a clock that never goes backwards
	 * @return true if the sound may start; false if it is dropped
	 */
	public boolean tryStart(String ruleId, Limit limit, double x, double y, double z, long nowMs) {
		RuleCounters counters = rules.get(ruleId);
		if (counters == null) {
			counters = add(ruleId);
		}
		counters.lastUsed = ++tick;
		if (!limit.equals(counters.limit)) {
			// An edited limit starts afresh: its old counts and busiest window belong to the old limit.
			counters.limit = limit;
			counters.peak = 0;
			pool.removeOwner(counters);
		}
		SlidingWindow window = limit.perSource()
				? pool.get(counters, blockKey(x, y, z), limit.count(), limit.windowMs(), MAX_SOURCES_PER_RULE)
				: pool.get(counters, 0, limit.count(), limit.windowMs(), 1);
		boolean allowed = window.tryStart(nowMs);
		if (allowed) {
			counters.allowed++;
			if (counters.peak < limit.count()) {
				// Once a counter has been full, the peak cannot rise any more, so the walk is skipped from then on.
				counters.peak = Math.max(counters.peak, window.inWindow(nowMs));
			}
		} else {
			counters.dropped++;
		}
		return allowed;
	}

	/**
	 * Every counter and every rule's numbers forgotten, e.g. when the player joins another world or server: its places
	 * have their own counters (FR-6.4), and a limit must not carry plays over from a world the player left.
	 */
	public void clear() {
		for (RuleCounters c : rules.values()) {
			c.removed = true;
		}
		pool.removeMarked();
		rules.clear();
	}

	/**
	 * What the limits did since they were last cleared, for Settings › Diagnostics (FR-6 AC: "never more than 3 starting
	 * in any one second"). Walks every rule: call it on demand, not per sound.
	 */
	public Stats stats() {
		long allowed = 0;
		long dropped = 0;
		List<RuleCounters> used = new ArrayList<>();
		for (RuleCounters c : rules.values()) {
			allowed += c.allowed;
			dropped += c.dropped;
			if (c.limit != null && c.peak > 0) {
				used.add(c);
			}
		}
		// Fullest first: how close each limit came to its count, then the higher count, then the most recently used.
		used.sort(Comparator.comparingDouble((RuleCounters c) -> -(double) c.peak / c.limit.count())
				.thenComparingInt(c -> -c.peak).thenComparingLong(c -> -c.lastUsed));
		List<Fullest> fullest = new ArrayList<>();
		for (int i = 0; i < Math.min(FULLEST_SHOWN, used.size()); i++) {
			fullest.add(new Fullest(used.get(i).peak, used.get(i).limit));
		}
		return new Stats(allowed, dropped, fullest);
	}

	/** How many limits {@link #stats} names, fullest first. */
	public static final int FULLEST_SHOWN = 3;

	/**
	 * What the repeat limits did (Diagnostics).
	 *
	 * @param allowed plays a limit let through
	 * @param dropped plays a limit dropped
	 * @param fullest up to {@value #FULLEST_SHOWN} limits that came closest to their count, fullest first
	 */
	public record Stats(long allowed, long dropped, List<Fullest> fullest) {
		public static final Stats NONE = new Stats(0, 0, List.of());

		public Stats {
			fullest = List.copyOf(fullest);
		}
	}

	/**
	 * One limit's busiest window.
	 *
	 * @param most  the most plays one of its counters let start within the window; never more than {@code limit.count()}
	 * @param limit the limit it was counted against
	 */
	public record Fullest(int most, Limit limit) {
	}

	/** A new rule's counters, forgetting the least recently used rule first if {@value #MAX_RULES} are remembered. */
	private RuleCounters add(String ruleId) {
		if (rules.size() >= MAX_RULES) {
			RuleCounters oldest = null;
			for (RuleCounters c : rules.values()) {
				if (oldest == null || c.lastUsed < oldest.lastUsed) {
					oldest = c;
				}
			}
			rules.remove(oldest.ruleId);
			pool.removeOwner(oldest);
		}
		RuleCounters counters = new RuleCounters(ruleId, ++serials);
		rules.put(ruleId, counters);
		return counters;
	}

	/**
	 * The block a position is in, rounded down (FR-6.5), packed into one {@code long} the same way as Minecraft's
	 * {@code BlockPos.asLong}: 26 bits x, 26 bits z, 12 bits y.
	 */
	public static long blockKey(double x, double y, double z) {
		long bx = (long) Math.floor(x);
		long by = (long) Math.floor(y);
		long bz = (long) Math.floor(z);
		return ((bx & 0x3FFFFFFL) << 38) | ((bz & 0x3FFFFFFL) << 12) | (by & 0xFFFL);
	}

	/** Plays this rule has dropped since it was first used, for diagnostics and the "Limited" count (FR-6.8). */
	public long droppedCount(String ruleId) {
		RuleCounters c = rules.get(ruleId);
		return c == null ? 0 : c.dropped;
	}

	/** Positions currently remembered for a per-source rule. */
	public int sourceCount(String ruleId) {
		RuleCounters c = rules.get(ruleId);
		return c == null || c.limit == null || !c.limit.perSource() ? 0 : c.entries;
	}

	/** Counters remembered across all rules, at most {@value #MAX_COUNTERS}. */
	public int counterCount() {
		return pool.size();
	}

	/** Rules that have counters. */
	public Set<String> ruleIds() {
		return Set.copyOf(rules.keySet());
	}

	/**
	 * Estimated bytes of all counters, for the Diagnostics memory readout (PRD 9.1). It walks every rule, so call it on
	 * demand through {@code MemoryMeter}, not per sound. Limits belong to the profile and are not counted.
	 */
	public long estimatedBytes() {
		long bytes = MemoryEstimate.object(2 * MemoryEstimate.REFERENCE + 2 * 8) + MemoryEstimate.HASH_MAP
				+ MemoryEstimate.hashTable(rules.size()) + rules.size() * MemoryEstimate.HASH_NODE + pool.estimatedBytes();
		for (Map.Entry<String, RuleCounters> entry : rules.entrySet()) {
			bytes += MemoryEstimate.string(entry.getKey()) + RuleCounters.BYTES;
		}
		return bytes;
	}

	/** Forgets counters of rules that no longer exist, e.g. after the profile is saved. */
	public void retain(Set<String> ruleIds) {
		boolean any = false;
		for (Iterator<RuleCounters> it = rules.values().iterator(); it.hasNext();) {
			RuleCounters c = it.next();
			if (!ruleIds.contains(c.ruleId)) {
				c.removed = true;
				any = true;
				it.remove();
			}
		}
		if (any) {
			pool.removeMarked();
		}
	}

	private static final class RuleCounters extends CounterPool.Owner {
		static final long BYTES = MemoryEstimate.object(2 * MemoryEstimate.REFERENCE + 4 * 8 + 2 * 4 + 1);

		final String ruleId;
		Limit limit;
		long allowed;
		long dropped;
		long lastUsed;
		/** The most plays one of this rule's counters let start within the window, under its current limit. */
		int peak;

		RuleCounters(String ruleId, long serial) {
			super(serial);
			this.ruleId = ruleId;
		}
	}
}
