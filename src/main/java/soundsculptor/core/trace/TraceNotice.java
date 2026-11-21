package soundsculptor.core.trace;

/**
 * Special cases stated plainly at the top of the trace panel (PRD FR-14.1, FR-14.3). Declared in display order:
 * an {@code EnumSet} of these iterates with the most important first.
 */
public enum TraceNotice {
	/** "Bypass is on: nothing is changed." */
	BYPASS("bypass"),
	/** "Not heard yet: predicted for where you are." */
	NOT_HEARD_YET("not_heard_yet"),
	/** "Above 100%: boost applied." */
	BOOSTED("boosted"),
	/**
	 * "Above 100%: boost is off here, so it plays at 100%." The audio device cannot boost, or this profile has not
	 * confirmed boost yet (FR-5.4).
	 */
	BOOST_OFF("boost_off"),
	/** Sets or zones changed since the last play, so the result shown differs from what was heard. */
	CHANGED_SINCE_PLAY("changed_since_play");

	private final String key;

	TraceNotice(String key) {
		this.key = key;
	}

	/** Key in {@code assets/soundsculptor/lang/en_us.json}. */
	public String translationKey() {
		return "soundsculptor.trace.notice." + key;
	}
}
