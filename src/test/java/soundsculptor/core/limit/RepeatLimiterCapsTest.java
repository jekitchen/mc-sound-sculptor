package soundsculptor.core.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import soundsculptor.core.Allocations;
import soundsculptor.core.sets.Limit;

import org.junit.jupiter.api.Test;

/**
 * PRD 9.1 ("Memory used by recent log, linker and limiters: under 8 MB") and FR-6.5: the limiter's caps across all rules
 * together. Each per-source rule keeps up to 1,024 positions of its own, forgetting its own least recently used one; all
 * rules together keep at most {@link RepeatLimiter#MAX_COUNTERS} counters. When those are full, the rule holding the
 * most gives one up, so a busy rule recycles its own and never pushes out another rule's. A forgotten counter only
 * starts afresh, the same as FR-6.5's per-rule forgetting.
 */
class RepeatLimiterCapsTest {
	/** One play per long window: a start is dropped exactly when its counter is still remembered. */
	private static final Limit ONCE_PER_SOURCE = new Limit(1, 10_000, true);
	private static final Limit ONCE_SHARED = new Limit(1, 10_000, false);
	private static final int FULL = RepeatLimiter.MAX_SOURCES_PER_RULE;

	private final RepeatLimiter limiter = new RepeatLimiter();

	private boolean start(String ruleId, Limit limit, double x, long nowMs) {
		return limiter.tryStart(ruleId, limit, x, 64, 0, nowMs);
	}

	@Test
	void threeFullPerSourceRulesFitAndAFourthSharesEvenly() {
		long t = 0;
		for (String rule : List.of("a", "b", "c")) {
			for (int i = 0; i < FULL; i++) {
				start(rule, ONCE_PER_SOURCE, i, t++);
			}
		}
		assertEquals(3 * FULL, RepeatLimiter.MAX_COUNTERS, "the pool holds three full per-source rules");
		assertEquals(RepeatLimiter.MAX_COUNTERS, limiter.counterCount());
		for (int i = 0; i < FULL; i++) {
			start("d", ONCE_PER_SOURCE, i, t++);
		}
		assertEquals(RepeatLimiter.MAX_COUNTERS, limiter.counterCount());
		int[] counts = {limiter.sourceCount("a"), limiter.sourceCount("b"), limiter.sourceCount("c"), limiter.sourceCount("d")};
		for (int count : counts) {
			assertEquals(RepeatLimiter.MAX_COUNTERS / 4, count, "more busy rules than the pool holds share it evenly");
		}
	}

	@Test
	void aBusyPerSourceRuleNeverPushesOutAnotherRulesPositions() {
		// Review of #129, FR-6.5: each rule keeps its own 1,024 positions in its place.
		long t = 0;
		for (int i = 0; i < FULL; i++) {
			start("quiet", ONCE_PER_SOURCE, i, t++);
		}
		start("shared", ONCE_SHARED, 0, t++);
		for (int i = 0; i < 20 * FULL; i++) {
			start("busy", ONCE_PER_SOURCE, 100_000 + i, 0);
		}
		assertEquals(FULL, limiter.sourceCount("busy"), "the busy rule keeps its own 1,024");
		assertEquals(FULL, limiter.sourceCount("quiet"));
		for (int i = 0; i < FULL; i++) {
			assertFalse(start("quiet", ONCE_PER_SOURCE, i, 0), "the quiet rule still remembers position " + i);
		}
		assertFalse(start("shared", ONCE_SHARED, 0, 0), "and a shared counter is kept too");
	}

	@Test
	void whenFullTheRuleHoldingTheMostGivesUpItsLeastRecentlyUsed() {
		long t = 0;
		for (String rule : List.of("a", "b", "c")) {
			for (int i = 0; i < FULL; i++) {
				start(rule, ONCE_PER_SOURCE, i, t++);
			}
		}
		assertTrue(start("d", ONCE_PER_SOURCE, 0, t++), "a fourth rule's first position");
		assertEquals(FULL - 1, limiter.sourceCount("a"), "a, b and c hold the most; a's oldest position is the oldest of them");
		assertTrue(start("a", ONCE_PER_SOURCE, 0, t++), "a's position 0 was forgotten, so it starts afresh");
		assertEquals(FULL - 1, limiter.sourceCount("b"), "a holds fewer now, so b (oldest of b and c) gave one up");
		assertFalse(start("c", ONCE_PER_SOURCE, 0, t++), "c's position 0 is still remembered");
	}
	@Test
	void sharedCountersUseTheSameCounters() {
		for (int r = 0; r <= RepeatLimiter.MAX_COUNTERS; r++) {
			assertTrue(start("rule" + r, ONCE_SHARED, 0, r));
		}
		assertEquals(RepeatLimiter.MAX_COUNTERS, limiter.counterCount());
		assertTrue(start("rule0", ONCE_SHARED, 0, 5000), "rule0's counter was the least recently used, so it was forgotten");
		assertFalse(start("rule" + RepeatLimiter.MAX_COUNTERS, ONCE_SHARED, 0, 5001), "the newest counter is kept");
	}

	@Test
	void atMostMaxRulesAreRemembered() {
		for (int r = 0; r <= RepeatLimiter.MAX_RULES; r++) {
			start("rule" + r, ONCE_SHARED, 0, r);
		}
		Set<String> ids = limiter.ruleIds();
		assertEquals(RepeatLimiter.MAX_RULES, ids.size());
		assertFalse(ids.contains("rule0"), "the least recently used rule is forgotten");
		assertTrue(ids.contains("rule" + RepeatLimiter.MAX_RULES));
		assertTrue(limiter.counterCount() <= RepeatLimiter.MAX_COUNTERS);
	}

	@Test
	void forgettingARuleFreesItsCounters() {
		for (int i = 0; i < 100; i++) {
			start("gone", ONCE_PER_SOURCE, i, i);
		}
		start("keep", ONCE_SHARED, 0, 0);
		assertEquals(101, limiter.counterCount());
		limiter.retain(Set.of("keep"));
		assertEquals(1, limiter.counterCount());
		assertFalse(start("keep", ONCE_SHARED, 0, 1), "the kept rule still counts");
		assertTrue(start("gone", ONCE_PER_SOURCE, 0, 2), "a deleted rule that comes back starts afresh");
	}

	@Test
	void switchingToASharedCountFreesThePositions() {
		for (int i = 0; i < 100; i++) {
			start("r", ONCE_PER_SOURCE, i, i);
		}
		assertTrue(start("r", ONCE_SHARED, 0, 200), "a new limit starts afresh");
		assertEquals(1, limiter.counterCount());
		assertEquals(0, limiter.sourceCount("r"));
	}

	/**
	 * Random plays of four per-source rules against a simple reference: per-rule LRU at 1,024, then, when all
	 * {@link RepeatLimiter#MAX_COUNTERS} are in use, the least recently used of the rule holding the most (the playing
	 * rule's own when it holds the most). Checks the hash table never loses or keeps a counter it should not, including
	 * the moves that keep counters packed.
	 */
	@Test
	void matchesAReferenceUnderRandomChurn() {
		Map<String, Boolean> reference = new LinkedHashMap<>(16, 0.75F, true);
		Map<String, Integer> counts = new java.util.HashMap<>();
		Random random = new Random(9);
		String[] rules = {"a", "b", "c", "d"};
		for (int step = 0; step < 200_000; step++) {
			String rule = rules[random.nextInt(rules.length)];
			int x = random.nextInt(1400);
			String key = rule + "@" + x;
			boolean expected = !reference.containsKey(key);
			if (expected) {
				evictForNew(reference, counts, rule);
				counts.merge(rule, 1, Integer::sum);
			}
			reference.put(key, Boolean.TRUE);
			assertEquals(expected, start(rule, ONCE_PER_SOURCE, x, 0), "step " + step);
		}
		assertEquals(reference.size(), limiter.counterCount());
		for (String rule : rules) {
			assertEquals(counts.getOrDefault(rule, 0), limiter.sourceCount(rule), rule);
		}
	}

	private static void evictForNew(Map<String, Boolean> reference, Map<String, Integer> counts, String rule) {
		int own = counts.getOrDefault(rule, 0);
		String from;
		if (own >= RepeatLimiter.MAX_SOURCES_PER_RULE) {
			from = rule;
		} else if (reference.size() >= RepeatLimiter.MAX_COUNTERS) {
			int most = counts.values().stream().max(Integer::compare).orElse(0);
			from = own > 0 && own >= most ? rule : null;
			if (from == null) {
				for (String key : reference.keySet()) {
					String owner = key.substring(0, key.indexOf('@'));
					if (counts.getOrDefault(owner, 0) == most) {
						from = owner;
						break;
					}
				}
			}
		} else {
			return;
		}
		for (String key : reference.keySet()) {
			if (key.startsWith(from + "@")) {
				reference.remove(key);
				counts.merge(from, -1, Integer::sum);
				return;
			}
		}
	}

	@Test
	void churnAcrossRulesAllocatesNothing() {
		Limit perSource = new Limit(3, 1000, true);
		List<String> rules = new ArrayList<>();
		for (int r = 0; r < 4; r++) {
			rules.add("per" + r);
		}
		// 4 rules x 900 positions: more than MAX_COUNTERS, so counters are forgotten and reused all the time.
		for (int i = 0; i < 200_000; i++) {
			limiter.tryStart(rules.get(i & 3), perSource, i % 900, 64, 0, i);
		}
		long allocated = Allocations.fewestBytes(round -> {
			long base = 200_000L + round * 50_000L;
			for (int i = 0; i < 50_000; i++) {
				limiter.tryStart(rules.get(i & 3), perSource, i % 900, 64, 0, base + i);
			}
		});
		assertEquals(0, allocated);
	}
}
