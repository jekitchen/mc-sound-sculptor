package soundsculptor.core.zones;

/**
 * Where the player is, for zones (PRD FR-8.4, FR-9 step 1): the enabled zones of one {@link Precedence} snapshot, in
 * the current world and dimension, that contain the player's feet block, smallest shape volume first and zones of equal
 * volume in zone-list order. Everywhere is not in the list: it always comes last.
 *
 * <p>Worked out again only when the snapshot, world, dimension or feet block changes ({@link #update}); a sound start
 * just reads it (PRD 7.4). {@link #version()} changes only when the list itself changes, so moving inside one zone, or
 * a new snapshot with the same zones in the same place, does not re-decide playing loops.
 *
 * <p>Allocates nothing. Not thread-safe: one instance per thread that decides sounds.
 */
public final class WhereYouAre {
	private final int[] zones = new int[Precedence.MAX_ZONES];
	private final int[] scratch = new int[Precedence.MAX_ZONES];
	private int count;
	private long version;

	private Precedence precedence;
	private String worldKey;
	private String dimension;
	private boolean known;
	private int bx;
	private int by;
	private int bz;

	/**
	 * The player's feet are in block {@code (bx, by, bz)} of this world and dimension.
	 *
	 * @return whether the list changed (and {@link #version()} with it)
	 */
	public boolean update(Precedence p, String worldKey, String dimension, int bx, int by, int bz) {
		if (p == precedence && known && bx == this.bx && by == this.by && bz == this.bz && same(worldKey, this.worldKey)
				&& same(dimension, this.dimension)) {
			return false;
		}
		this.precedence = p;
		this.worldKey = worldKey;
		this.dimension = dimension;
		this.known = true;
		this.bx = bx;
		this.by = by;
		this.bz = bz;
		int n = 0;
		for (int i = 0; i < p.zoneCount(); i++) {
			Zone zone = p.zoneAt(i);
			if (zone.isActiveIn(worldKey, dimension) && zone.containsBlock(bx, by, bz)) {
				// Insertion sort: zones arrive in list order, so equal volumes stay in list order.
				double volume = p.zoneVolume(i);
				int k = n++;
				while (k > 0 && p.zoneVolume(scratch[k - 1]) > volume) {
					scratch[k] = scratch[k - 1];
					k--;
				}
				scratch[k] = i;
			}
		}
		return replaceWith(n);
	}

	/**
	 * Where the player is is not known (no player yet, or outside a world): no zone applies, only Everywhere.
	 *
	 * @return whether the list changed
	 */
	public boolean updateNowhere(Precedence p) {
		if (p == precedence && !known) {
			return false;
		}
		this.precedence = p;
		this.known = false;
		this.worldKey = null;
		this.dimension = null;
		return replaceWith(0);
	}

	private boolean replaceWith(int n) {
		boolean changed = n != count;
		for (int k = 0; k < n && !changed; k++) {
			changed = scratch[k] != zones[k];
		}
		System.arraycopy(scratch, 0, zones, 0, n);
		count = n;
		if (changed) {
			version++;
		}
		return changed;
	}

	private static boolean same(String a, String b) {
		return a == b || (a != null && a.equals(b));
	}

	/** The snapshot the list was worked out for; {@link Precedence#decide} needs it to be its own. */
	public Precedence precedence() {
		return precedence;
	}

	/** Zones the player stands in. */
	public int count() {
		return count;
	}

	/** The {@code k}-th zone, smallest first, as an index into {@link Precedence#zones()}. */
	public int zone(int k) {
		if (k < 0 || k >= count) {
			throw new IndexOutOfBoundsException(k);
		}
		return zones[k];
	}

	/** Changes whenever the list changes; starts at 0. */
	public long version() {
		return version;
	}

	/** Whether a feet block is known; false after {@link #updateNowhere}. */
	public boolean known() {
		return known;
	}

	public int x() {
		return bx;
	}

	public int y() {
		return by;
	}

	public int z() {
		return bz;
	}
}
