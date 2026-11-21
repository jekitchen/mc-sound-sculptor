package soundsculptor.core.trace;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

import soundsculptor.core.sets.EntryResolver;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundPattern;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.SoundDecision;
import soundsculptor.core.zones.WhereYouAre;
import soundsculptor.core.zones.Zone;

/**
 * Builds the sound trace (PRD FR-14) when the "Why?" panel opens, in Sound Set terms: for Everywhere and each zone the
 * player stood in, the attached sets with entries that match the sound, the winning entry per setting and the set it
 * won in. Not on the hot path: it allocates freely. Entries are listed and picked in the order the place's resolver
 * uses, the zones in {@link WhereYouAre}'s order, and the final result comes from {@link Precedence#decide}, so the
 * trace can never disagree with what was played.
 */
public final class SoundTracer {
	private static final float SAME_VOLUME = 0.01F;

	private SoundTracer() {
	}

	/** {@link #trace(Precedence, SetLibrary, ToIntFunction, String, LastPlay, Standing, boolean)} with no place counts. */
	public static SoundTrace trace(Precedence p, SetLibrary lib, String soundId, LastPlay play, Standing now, boolean bypass) {
		return trace(p, lib, setId -> 0, soundId, play, now, bypass);
	}

	/**
	 * @param p           the places, built from {@code lib}
	 * @param lib         the library the places' set IDs refer to, for set names
	 * @param placesUsing for a library set ID, how many places use the set ("used in 3 places", FR-14.4)
	 * @param play        the sound's last play, or {@code null} if it has not played this session
	 * @param now         where the player stands now: the trace of a sound not heard yet is predicted for there
	 * @param bypass      whether Bypass Sound Sculptor is on
	 */
	public static SoundTrace trace(Precedence p, SetLibrary lib, ToIntFunction<String> placesUsing, String soundId,
			LastPlay play, Standing now, boolean bypass) {
		return trace(p, lib, placesUsing, soundId, play, now, bypass, true);
	}

	/**
	 * As above; {@code boostAllowed} says whether a level above 100% plays louder (the audio device can boost and the
	 * profile confirmed it, FR-5.4). Above 100% the panel then says "boost applied", or else that it plays at 100%.
	 */
	public static SoundTrace trace(Precedence p, SetLibrary lib, ToIntFunction<String> placesUsing, String soundId,
			LastPlay play, Standing now, boolean bypass, boolean boostAllowed) {
		Set<TraceNotice> notices = EnumSet.noneOf(TraceNotice.class);
		Boolean playAllowed = play == null ? null : !play.dropped();
		Boolean cueShown = play == null ? null : play.cueShown();
		if (bypass) {
			notices.add(TraceNotice.BYPASS);
		}
		if (play == null) {
			notices.add(TraceNotice.NOT_HEARD_YET);
		}

		PlaceTrace everywhere = explain(p.everywhere(), soundId, lib, placesUsing);
		WhereYouAre where = (play != null ? Standing.of(play) : now).locate(p);
		List<ZoneLine> lines = new ArrayList<>(where.count());
		for (int k = 0; k < where.count(); k++) {
			int i = where.zone(k);
			lines.add(new ZoneLine(p.zoneAt(i), explain(p.zoneResolver(i), soundId, lib, placesUsing)));
		}
		Map<TraceProperty, ZoneWinner> zoneWinners = new EnumMap<>(TraceProperty.class);
		for (TraceProperty property : TraceProperty.values()) {
			ZoneWinner winner = zoneWinner(p, where, lines, property);
			if (winner != null) {
				zoneWinners.put(property, winner);
			}
		}

		SoundDecision d = new SoundDecision();
		p.decide(soundId, where, d);
		float volume = bypass ? 100 : d.volumePercent();
		if (!bypass && volume > 100) {
			notices.add(boostAllowed ? TraceNotice.BOOSTED : TraceNotice.BOOST_OFF);
		}
		if (play != null && !bypass && Math.abs(volume - play.volumePercent()) > SAME_VOLUME) {
			notices.add(TraceNotice.CHANGED_SINCE_PLAY);
		}
		return new SoundTrace(soundId, notices, everywhere, lines, zoneWinners, volume, bypass ? null : d.limit(),
				!bypass && d.cueOn(), playAllowed, cueShown);
	}

	/**
	 * The sets of one place with entries that match the sound, and the winner per setting with the step that decided
	 * it. Works on the resolver's positions, so each entry stays with the set it is listed in even when a set and its
	 * duplicate share entry instances.
	 */
	static PlaceTrace explain(EntryResolver place, String soundId, SetLibrary lib, ToIntFunction<String> placesUsing) {
		List<SetEntry> entries = place.rulesInOrder();
		List<String> setIds = place.setIdsInOrder();
		List<Integer> matched = new ArrayList<>();
		Map<String, List<SetEntry>> bySet = new LinkedHashMap<>();
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).pattern().matches(soundId)) {
				matched.add(i);
				bySet.computeIfAbsent(setIds.get(i), id -> new ArrayList<>()).add(entries.get(i));
			}
		}
		List<SetLine> sets = new ArrayList<>(bySet.size());
		bySet.forEach((setId, list) -> sets.add(setLine(setId, list, lib, placesUsing)));

		Map<TraceProperty, EntryWinner> winners = new EnumMap<>(TraceProperty.class);
		for (TraceProperty property : TraceProperty.values()) {
			int winner = -1;
			int runnerUp = -1;
			for (int i : matched) {
				if (!property.setBy(entries.get(i))) {
					continue;
				}
				if (winner < 0) {
					winner = i;
				} else {
					runnerUp = i;
					break;
				}
			}
			if (winner >= 0) {
				EntryWinReason reason = runnerUp < 0 ? EntryWinReason.ONLY_MATCH
						: !setIds.get(winner).equals(setIds.get(runnerUp)) ? EntryWinReason.HIGHER_SET
						: specificityReason(entries.get(winner), entries.get(runnerUp));
				winners.put(property, new EntryWinner(entries.get(winner), setIds.get(winner), reason));
			}
		}
		return new PlaceTrace(sets, winners);
	}

	private static SetLine setLine(String setId, List<SetEntry> matched, SetLibrary lib, ToIntFunction<String> placesUsing) {
		if (setId.equals(SoundSet.QUICK_ID)) {
			return new SetLine(setId, "", true, matched, 1);
		}
		SoundSet set = lib.get(setId);
		return new SetLine(setId, set == null ? setId : set.name(), false, matched, placesUsing.applyAsInt(setId));
	}

	/** Why {@code winner} beat {@code runnerUp} within one set: the FR-4.5 specificity step that decided. */
	private static EntryWinReason specificityReason(SetEntry winner, SetEntry runnerUp) {
		SoundPattern w = winner.pattern();
		SoundPattern r = runnerUp.pattern();
		if (w.isExact() && !r.isExact()) {
			return EntryWinReason.EXACT_ID;
		}
		if (w.literalCount() > r.literalCount()) {
			return EntryWinReason.MORE_LITERAL;
		}
		if (w.starCount() < r.starCount()) {
			return EntryWinReason.FEWER_STARS;
		}
		return EntryWinReason.LIST_ORDER;
	}

	/**
	 * The first zone in where-you-are order that sets the property (FR-9 step 2), and why: the only one, the smallest,
	 * or as small as the next one that sets it and higher in the zone list.
	 */
	private static ZoneWinner zoneWinner(Precedence p, WhereYouAre where, List<ZoneLine> lines, TraceProperty property) {
		int best = -1;
		int runnerUp = -1;
		for (int k = 0; k < lines.size(); k++) {
			if (lines.get(k).sets().winners().containsKey(property)) {
				if (best < 0) {
					best = k;
				} else {
					runnerUp = k;
					break;
				}
			}
		}
		if (best < 0) {
			return null;
		}
		Zone zone = lines.get(best).zone();
		if (runnerUp < 0) {
			return new ZoneWinner(zone, ZoneWinReason.ONLY_ZONE);
		}
		boolean sameSize = p.zoneVolume(where.zone(best)) == p.zoneVolume(where.zone(runnerUp));
		return new ZoneWinner(zone, sameSize ? ZoneWinReason.SAME_SIZE_LIST_ORDER : ZoneWinReason.SMALLEST);
	}
}
