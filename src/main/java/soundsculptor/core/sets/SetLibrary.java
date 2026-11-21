package soundsculptor.core.sets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

import soundsculptor.core.Message;

/**
 * The shared library of Sound Sets (PRD FR-11.5a). Immutable; edits return a new library.
 */
public final class SetLibrary {
	public static final int MAX_SETS = 256;
	public static final SetLibrary EMPTY = new SetLibrary(List.of());

	private final Map<String, SoundSet> byId;
	private final List<SoundSet> sorted;
	private final LongSupplier random;

	public SetLibrary(Collection<SoundSet> sets) {
		this(sets, () -> ThreadLocalRandom.current().nextLong());
	}

	/** For tests: {@code random} supplies the bits for {@link #newId()}. */
	SetLibrary(Collection<SoundSet> sets, LongSupplier random) {
		if (sets.size() > MAX_SETS) {
			throw new IllegalArgumentException("the library can hold at most " + MAX_SETS + " Sound Sets");
		}
		Map<String, SoundSet> map = new LinkedHashMap<>();
		for (SoundSet s : sets) {
			if (s.isQuick()) {
				throw new IllegalArgumentException("Quick changes cannot be in the library");
			}
			if (map.put(s.id(), s) != null) {
				throw new IllegalArgumentException("duplicate set ID " + s.id());
			}
		}
		this.byId = Map.copyOf(map);
		List<SoundSet> list = new ArrayList<>(map.values());
		list.sort(Comparator.comparing((SoundSet s) -> s.name().toLowerCase(Locale.ROOT)).thenComparing(SoundSet::id));
		this.sorted = List.copyOf(list);
		this.random = random;
	}

	/** The set with this ID, or {@code null}. */
	public SoundSet get(String id) {
		return byId.get(id);
	}

	public boolean contains(String id) {
		return byId.containsKey(id);
	}

	public int size() {
		return byId.size();
	}

	/** All sets, sorted by name ignoring case. */
	public List<SoundSet> sets() {
		return sorted;
	}

	public Set<String> ids() {
		return byId.keySet();
	}

	/** {@code null} if another set fits, else the "library full" problem. */
	public Message canCreate() {
		return size() >= MAX_SETS ? Message.of("soundsculptor.sets.error.library_full", MAX_SETS) : null;
	}

	/** Adds the set, or replaces the one with the same ID. */
	public SetLibrary put(SoundSet s) {
		Map<String, SoundSet> map = new LinkedHashMap<>(byId);
		map.put(s.id(), s);
		return new SetLibrary(map.values(), random);
	}

	public SetLibrary remove(String id) {
		Map<String, SoundSet> map = new LinkedHashMap<>(byId);
		map.remove(id);
		return new SetLibrary(map.values(), random);
	}

	/**
	 * A name no other set uses, ignoring case (the same rule the list is sorted by): {@code wanted}, else
	 * "wanted (2)", "wanted (3)" and so on ({@link SetNames.Forms#numbered}, in the player's language). The set
	 * {@code exceptId} (the one being renamed) does not count as a clash. When the name grows, {@code wanted} is cut
	 * short so the result still fits in {@link SoundSet#MAX_NAME_LENGTH} characters.
	 */
	/** More numbered tries than any library needs ({@link #uniqueName} stops there whatever the language file says). */
	static final int MAX_NAME_TRIES = 10_000;

	public String uniqueName(String wanted, String exceptId) {
		return uniqueName(wanted, exceptId, null, Set.of());
	}

	/**
	 * Like {@link #uniqueName(String, String)}, but a name that clashes first takes the form {@code first} (for example
	 * {@link SetNames.Forms#copy}: "Farm (copy)"), and only then "Farm (copy) (2)", "Farm (copy) (3)" and so on. The
	 * names in {@code alsoTaken} (ignoring case) clash too, for names that are about to be added, such as the other sets
	 * of an import.
	 *
	 * @param first the first form, or {@code null} to try {@code wanted} itself first
	 */
	public String uniqueName(String wanted, String exceptId, UnaryOperator<String> first, Collection<String> alsoTaken) {
		Set<String> taken = new HashSet<>();
		for (String name : alsoTaken) {
			taken.add(key(name));
		}
		UnaryOperator<String> start = first == null ? UnaryOperator.identity() : first;
		String candidate = fitted(wanted, start);
		SetNames.Forms forms = SetNames.forms();
		// A library holds at most a few hundred sets, so a free number is always found well before this many tries.
		boolean english = false;
		for (int n = 2; (clashes(candidate, exceptId) || taken.contains(key(candidate))) && n < MAX_NAME_TRIES; n++) {
			int number = n;
			SetNames.Forms use = english ? SetNames.ENGLISH : forms;
			String next = fitted(wanted, name -> use.numbered(start.apply(name), number));
			if (!english && next.equals(candidate)) {
				// The translated pattern leaves the number out, so every try would be the same name: use the English one.
				english = true;
				next = fitted(wanted, name -> SetNames.ENGLISH.numbered(start.apply(name), number));
			}
			candidate = next;
		}
		return candidate;
	}

	/**
	 * {@code form} applied to {@code wanted}, with {@code wanted} cut short (never inside a surrogate pair) until the
	 * result has at most the maximum name length.
	 */
	private static String fitted(String wanted, UnaryOperator<String> form) {
		String name = wanted;
		String result = form.apply(name);
		while (result.length() > SoundSet.MAX_NAME_LENGTH && !name.isEmpty()) {
			int end = Math.max(0, name.length() - (result.length() - SoundSet.MAX_NAME_LENGTH));
			if (end > 0 && Character.isHighSurrogate(name.charAt(end - 1))) {
				end--;
			}
			name = name.substring(0, end).stripTrailing();
			result = form.apply(name);
		}
		return result;
	}

	/** "set-" and 12 lowercase hex digits that no set in the library uses. */
	public String newId() {
		while (true) {
			String id = String.format("set-%012x", random.getAsLong() & 0xFFFFFFFFFFFFL);
			if (!byId.containsKey(id)) {
				return id;
			}
		}
	}

	private boolean clashes(String name, String exceptId) {
		for (SoundSet s : byId.values()) {
			if (!s.id().equals(exceptId) && key(s.name()).equals(key(name))) {
				return true;
			}
		}
		return false;
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}
}
