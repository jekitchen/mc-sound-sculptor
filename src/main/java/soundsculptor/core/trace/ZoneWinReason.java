package soundsculptor.core.trace;

/** Why a zone won a setting over the other zones the player stood in (PRD FR-14.2 item 3, FR-9 step 2). */
public enum ZoneWinReason {
	/** No other zone the player stood in sets this. */
	ONLY_ZONE("only_zone"),
	/** The smallest of the zones that set this. */
	SMALLEST("smallest"),
	/** As small as another zone that sets this, and higher in the zone list. */
	SAME_SIZE_LIST_ORDER("same_size_list_order");

	private final String key;

	ZoneWinReason(String key) {
		this.key = key;
	}

	/** Key in {@code assets/soundsculptor/lang/en_us.json}. */
	public String translationKey() {
		return "soundsculptor.trace.zone_reason." + key;
	}
}
