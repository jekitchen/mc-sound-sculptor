package soundsculptor.core.diag;

import java.util.Locale;

import soundsculptor.core.limit.RepeatLimiter;

/**
 * The text the Diagnostics page's "Copy report" button puts on the clipboard (PRD FR-13.3). It is meant for bug
 * reports, so it is plain English with fixed number formats regardless of the game language.
 */
public final class DiagnosticsReport {
	/**
	 * Everything the report shows.
	 *
	 * @param librarySets     Sound Sets in the library
	 * @param activeSets      sets applying in some place: enabled attachments, Quick changes included, once per place
	 * @param activeEntries   enabled entries in those sets, once per place
	 * @param activeZonesHere zones in the current world and dimension
	 * @param sounds          per sound start; its count is sounds per second
	 * @param peakSoundMicros worst single sound start since the last reset
	 * @param tick            per tick re-check of playing sounds
	 * @param outlines        zone outline drawing per frame
	 * @param cacheHitRate    0 to 1, or {@code NaN} when nothing has been looked up yet
	 * @param memoryBytes     estimated bytes of Sound Sculptor's data ({@link MemoryMeter#totalBytes}), or -1 if not
	 *                        measured
	 * @param limits          what the repeat limits did (FR-6), since joining this world or server
	 */
	public record Data(String modVersion, String minecraftVersion, boolean bypass, int librarySets, int activeSets,
			int activeEntries, int zones, int activeZonesHere, TimingWindow.Stats sounds, double peakSoundMicros,
			TimingWindow.Stats tick, TimingWindow.Stats outlines, double cacheHitRate, long errors, long memoryBytes,
			RepeatLimiter.Stats limits) {
		/** Without repeat-limit numbers. */
		public Data(String modVersion, String minecraftVersion, boolean bypass, int librarySets, int activeSets,
				int activeEntries, int zones, int activeZonesHere, TimingWindow.Stats sounds, double peakSoundMicros,
				TimingWindow.Stats tick, TimingWindow.Stats outlines, double cacheHitRate, long errors, long memoryBytes) {
			this(modVersion, minecraftVersion, bypass, librarySets, activeSets, activeEntries, zones, activeZonesHere, sounds,
					peakSoundMicros, tick, outlines, cacheHitRate, errors, memoryBytes, RepeatLimiter.Stats.NONE);
		}

		/** Without a memory figure; the report then says n/a. */
		public Data(String modVersion, String minecraftVersion, boolean bypass, int librarySets, int activeSets,
				int activeEntries, int zones, int activeZonesHere, TimingWindow.Stats sounds, double peakSoundMicros,
				TimingWindow.Stats tick, TimingWindow.Stats outlines, double cacheHitRate, long errors) {
			this(modVersion, minecraftVersion, bypass, librarySets, activeSets, activeEntries, zones, activeZonesHere, sounds,
					peakSoundMicros, tick, outlines, cacheHitRate, errors, -1);
		}
	}

	/**
	 * The repeat-limit line (FR-6): plays let through and dropped, and the most plays one limit let start within its
	 * window, against that limit ("at most 3 in 1 s, limit 3"), so "never more than 3 in any one second" can be read off.
	 */
	public static String limitsLine(RepeatLimiter.Stats s) {
		if (s.fullest().isEmpty() && s.dropped() == 0) {
			return "Repeat limits: none used yet";
		}
		StringBuilder fullest = new StringBuilder();
		for (RepeatLimiter.Fullest f : s.fullest()) {
			fullest.append(fullest.isEmpty() ? ". Most in one window: " : "; ").append(String.format(Locale.ROOT, "%d (limit %d per %s s)",
					f.most(), f.limit().count(), soundsculptor.core.lists.Numbers.plain(f.limit().windowMs() / 1000.0, 3)));
		}
		return String.format(Locale.ROOT, "Repeat limits: %d played, %d dropped%s", s.allowed(), s.dropped(), fullest);
	}

	private DiagnosticsReport() {
	}

	public static String format(Data d) {
		StringBuilder out = new StringBuilder();
		line(out, "Sound Sculptor %s, Minecraft %s", d.modVersion(), d.minecraftVersion());
		line(out, "Bypass: %s", d.bypass() ? "on" : "off");
		line(out, "Sound Sets: %d in the library, %d active", d.librarySets(), d.activeSets());
		line(out, "Entries active: %d", d.activeEntries());
		line(out, "Zones: %d (%d active here)", d.zones(), d.activeZonesHere());
		line(out, "Sounds per second: %d", d.sounds().count());
		line(out, "Per sound: avg %.1f µs, max %.1f µs (peak %.1f µs)", d.sounds().averageMicros(), d.sounds().maxMicros(),
				d.peakSoundMicros());
		line(out, "Per tick re-check: avg %.1f µs, max %.1f µs", d.tick().averageMicros(), d.tick().maxMicros());
		line(out, "Outlines per frame: avg %.1f µs, max %.1f µs", d.outlines().averageMicros(), d.outlines().maxMicros());
		if (Double.isNaN(d.cacheHitRate())) {
			line(out, "Pattern cache hit rate: n/a");
		} else {
			line(out, "Pattern cache hit rate: %.1f%%", d.cacheHitRate() * 100);
		}
		if (d.memoryBytes() < 0) {
			line(out, "Memory (estimate): n/a");
		} else {
			line(out, "Memory (estimate): %s MB of %s MB", MemoryEstimate.megabytes(d.memoryBytes()),
					MemoryEstimate.megabytes(MemoryEstimate.BUDGET_BYTES));
		}
		out.append(limitsLine(d.limits())).append('\n');
		line(out, "Errors this session: %d", d.errors());
		return out.toString();
	}

	private static void line(StringBuilder out, String format, Object... args) {
		out.append(String.format(Locale.ROOT, format, args)).append('\n');
	}
}
