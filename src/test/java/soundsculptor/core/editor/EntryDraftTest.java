package soundsculptor.core.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import soundsculptor.core.editor.EntryDraft.Field;
import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.PatternError;
import soundsculptor.core.sets.SetEntry;

import org.junit.jupiter.api.Test;

/** PRD FR-12.4, FR-12.6, FR-12.7 and section 6.2 for the rule editor (FR-4, FR-5, FR-6 limits). */
class EntryDraftTest {
	private static final boolean BOOST = true;

	@Test
	void blankDraftCannotSaveButShowsNoErrorUntilEditedOrSaveIsPressed() {
		// 6.2: Save stays disabled from the start; the error shows once the field has been edited, or Save is pressed.
		EntryDraft d = EntryDraft.blank("r1", BOOST);
		assertFalse(d.canSave());
		assertEquals(PatternError.EMPTY.translationKey(), d.error(Field.TARGET).translationKey());
		assertNull(d.shownError(Field.TARGET));
		assertFalse(d.attemptSave());
		assertEquals(PatternError.EMPTY.translationKey(), d.shownError(Field.TARGET).translationKey());
	}

	@Test
	void editingAFieldShowsItsErrorAtOnce() {
		EntryDraft d = EntryDraft.blank("r1", BOOST);
		d.set(Field.TARGET, "entity.cow.#");
		assertEquals(PatternError.INVALID_CHARACTER.translationKey(), d.shownError(Field.TARGET).translationKey());
		assertNull(d.shownError(Field.VOLUME), "untouched fields stay quiet");
	}

	@Test
	void uppercasePatternIsAcceptedAndSavedLowercase() {
		EntryDraft d = EntryDraft.blank("r1", BOOST);
		d.set(Field.TARGET, "Minecraft:Entity.Cow.*");
		d.setMute(true);
		assertTrue(d.canSave());
		SetEntry rule = d.build();
		assertEquals("minecraft:entity.cow.*", rule.pattern().text());
		assertEquals(Boolean.TRUE, rule.mute());
		assertEquals("r1", rule.id());
	}

	@Test
	void volumeIsCheckedAgainstTheSliderRange() {
		EntryDraft d = draft("*villager*");
		d.set(Field.VOLUME, "250");
		assertEquals(new FieldError(FieldError.OUT_OF_RANGE, List.of("0", "200")), d.error(Field.VOLUME));
		d.set(Field.VOLUME, "150");
		assertNull(d.error(Field.VOLUME));
		assertTrue(d.showsBoostWarning());

		EntryDraft noBoost = EntryDraft.blank("r2", false);
		noBoost.set(Field.TARGET, "*");
		noBoost.set(Field.VOLUME, "150");
		assertEquals(new FieldError(FieldError.OUT_OF_RANGE, List.of("0", "100")), noBoost.error(Field.VOLUME));
	}

	@Test
	void volumeMustBeAWholeNumberOrEmpty() {
		EntryDraft d = draft("*");
		d.set(Field.VOLUME, "50.5");
		assertEquals(FieldError.NOT_WHOLE_NUMBER, d.error(Field.VOLUME).translationKey());
		d.set(Field.VOLUME, "loud");
		assertEquals(FieldError.NOT_A_NUMBER, d.error(Field.VOLUME).translationKey());
		d.set(Field.VOLUME, " 60 ");
		assertEquals(60, d.build().volume());
		d.set(Field.VOLUME, "");
		assertNull(d.build().volume(), "an empty field means the rule does not set volume");
		assertFalse(d.showsBoostWarning());
	}

	@Test
	void limitIsSetByItsToggleAndNeedsAWindow() {
		EntryDraft d = draft("minecraft:entity.villager.ambient");
		d.set(Field.LIMIT_WINDOW, "nonsense");
		d.set(Field.LIMIT_COUNT, "3");
		assertTrue(d.canSave(), "with the limit off, its fields are not checked");
		assertNull(d.build().limit(), "typing a count does not turn the limit on");

		d.setLimitOn(true);
		assertEquals(FieldError.NOT_A_NUMBER, d.error(Field.LIMIT_WINDOW).translationKey());
		d.set(Field.LIMIT_WINDOW, "");
		assertEquals(FieldError.REQUIRED, d.error(Field.LIMIT_WINDOW).translationKey());
		d.resetToDefault(Field.LIMIT_WINDOW);
		d.setPerSource(true);
		assertEquals(new Limit(3, 1000, true), d.build().limit());
	}

	@Test
	void anEmptyCountIsAnErrorNotOff() {
		// Review of #129: deleting the count to type another must not turn the limit off (or save anything).
		EntryDraft d = draft("*");
		d.setLimitOn(true);
		d.set(Field.LIMIT_COUNT, "");
		assertTrue(d.hasLimit(), "only the toggle turns the limit off");
		assertEquals(FieldError.REQUIRED, d.error(Field.LIMIT_COUNT).translationKey());
		assertFalse(d.limitValid());
		assertFalse(d.canSave());
		d.set(Field.LIMIT_COUNT, "5");
		assertEquals(new Limit(5, 1000, false), d.limit());
	}

	@Test
	void limitRangesComeFromFr6() {
		EntryDraft d = draft("*");
		d.setLimitOn(true);
		d.set(Field.LIMIT_COUNT, "0");
		assertEquals(new FieldError(FieldError.OUT_OF_RANGE, List.of("1", "100")), d.error(Field.LIMIT_COUNT));
		d.set(Field.LIMIT_COUNT, "5");
		d.set(Field.LIMIT_WINDOW, "0.02");
		assertEquals(new FieldError(FieldError.OUT_OF_RANGE, List.of("0.05", "10")), d.error(Field.LIMIT_WINDOW), "FR-6.1: 50 ms to 10 s, typed in seconds");
		d.set(Field.LIMIT_WINDOW, "99999999999999999999");
		assertEquals(FieldError.OUT_OF_RANGE, d.error(Field.LIMIT_WINDOW).translationKey());
		d.set(Field.LIMIT_WINDOW, "10.001");
		assertEquals(FieldError.OUT_OF_RANGE, d.error(Field.LIMIT_WINDOW).translationKey(), "10 s is the most");
		d.set(Field.LIMIT_COUNT, "101");
		assertEquals(FieldError.OUT_OF_RANGE, d.error(Field.LIMIT_COUNT).translationKey(), "100 plays is the most");
		d.set(Field.LIMIT_COUNT, "2.5");
		assertEquals(FieldError.NOT_WHOLE_NUMBER, d.error(Field.LIMIT_COUNT).translationKey());
	}

	@Test
	void theWindowIsTypedInSecondsAndSavedInMilliseconds() {
		EntryDraft d = draft("*");
		d.setLimitOn(true);
		for (String[] typed : new String[][] {{"0.05", "50"}, {"0.25", "250"}, {"1", "1000"}, {"1.5", "1500"}, {"10", "10000"},
				{"0.1234", "123"}, {".5", "500"}}) {
			d.set(Field.LIMIT_WINDOW, typed[0]);
			assertEquals(Integer.parseInt(typed[1]), d.build().limit().windowMs(), typed[0]);
		}
		assertEquals("0.25", EntryDraft.of(SetEntry.of("r", "*").withLimit(new Limit(3, 250, false)), BOOST).text(Field.LIMIT_WINDOW));
		assertEquals("0.05", EntryDraft.of(SetEntry.of("r", "*").withLimit(new Limit(3, 50, false)), BOOST).text(Field.LIMIT_WINDOW));
		assertEquals("10", EntryDraft.of(SetEntry.of("r", "*").withLimit(new Limit(3, 10_000, false)), BOOST).text(Field.LIMIT_WINDOW));
	}

	@Test
	void theLimitIsOffByDefaultAndTurnsOnWithThreePerSecond() {
		EntryDraft d = draft("minecraft:entity.villager.ambient");
		assertFalse(d.hasLimit(), "FR-6: off by default");
		assertTrue(d.isLimitDefault());
		d.setLimitOn(true);
		assertEquals(new Limit(3, 1000, false), d.build().limit(), "3 per 1 s, shared, unless changed");
		d.set(Field.LIMIT_COUNT, "7");
		d.set(Field.LIMIT_WINDOW, "2");
		d.setPerSource(true);
		d.setLimitOn(false);
		assertNull(d.build().limit());
		d.setLimitOn(true);
		assertEquals(new Limit(7, 2000, true), d.build().limit(), "turning it back on brings back what was typed");
		assertFalse(d.isLimitDefault());
		d.resetLimit();
		assertTrue(d.isLimitDefault());
		assertNull(d.build().limit());
	}

	@Test
	void mutingARuleWithNoCueSetStartsItWithTheCueDefault() {
		// FR-7.6: new mute rules have Cue on by default; the default can be changed in Settings.
		for (boolean cueDefault : new boolean[] {true, false}) {
			EntryDraft d = EntryDraft.blank("r1", true).useCueDefault(cueDefault);
			d.setMute(true);
			assertEquals(cueDefault, d.cue());
		}
	}

	@Test
	void theCueDefaultLeavesAChosenCueAndOtherEditsAlone() {
		EntryDraft chosen = EntryDraft.blank("r1", true).useCueDefault(true);
		chosen.setCue(false);
		chosen.setMute(true);
		assertEquals(Boolean.FALSE, chosen.cue(), "a Cue the player set is kept");
		EntryDraft notMuting = EntryDraft.blank("r1", true).useCueDefault(true);
		notMuting.setMute(false);
		assertNull(notMuting.cue(), "only muting brings the default");
		EntryDraft without = EntryDraft.blank("r1", true);
		without.setMute(true);
		assertNull(without.cue(), "without useCueDefault (as before cues) nothing changes");
	}

	@Test
	void anExistingRuleRoundTrips() {
		SetEntry rule = new SetEntry("r7", false, soundsculptor.core.sets.SoundPattern.of("*:block.*.step"), false, 40,
				new Limit(2, 500, true), true);
		EntryDraft d = EntryDraft.of(rule, BOOST);
		assertEquals("40", d.text(Field.VOLUME));
		assertFalse(d.enabled());
		assertEquals(Boolean.FALSE, d.mute());
		assertEquals(Boolean.TRUE, d.cue());
		assertTrue(d.canSave());
		assertEquals(rule, d.build());
		assertEquals(SetEntry.of("r8", "*"), EntryDraft.of(SetEntry.of("r8", "*"), BOOST).build());
	}

	@Test
	void dirtyMeansAValueReallyChanged() {
		// FR-12.4: Esc with unsaved changes opens Save / Discard / Keep editing.
		EntryDraft d = EntryDraft.of(SetEntry.of("r1", "*villager*").withVolume(50), BOOST);
		assertFalse(d.isDirty());
		d.set(Field.VOLUME, "050");
		assertFalse(d.isDirty(), "the same number typed differently");
		d.set(Field.VOLUME, "20");
		assertTrue(d.isDirty());
		d.set(Field.VOLUME, "50");
		assertFalse(d.isDirty());
		d.set(Field.LIMIT_WINDOW, "2");
		assertFalse(d.isDirty(), "the window does nothing without a count");
		d.set(Field.TARGET, "*VILLAGER*");
		assertFalse(d.isDirty(), "same pattern after lowercasing");
		d.setCue(false);
		assertTrue(d.isDirty());
	}

	@Test
	void resetToDefaultPerValue() {
		// FR-12.6. Values with no app-wide default (the pattern) go back to what they were when the editor opened.
		EntryDraft d = EntryDraft.of(new SetEntry("r1", true, soundsculptor.core.sets.SoundPattern.of("*cow*"), true, 30,
				new Limit(4, 2000, true), true), BOOST);
		d.set(Field.TARGET, "*pig*");
		d.resetToDefault(Field.TARGET);
		assertEquals("*:*cow*", d.text(Field.TARGET));
		for (Field f : List.of(Field.MUTE, Field.VOLUME, Field.LIMIT_ON, Field.LIMIT_COUNT, Field.LIMIT_WINDOW, Field.PER_SOURCE, Field.CUE)) {
			assertFalse(d.isDefault(f), f.name());
			d.resetToDefault(f);
			assertTrue(d.isDefault(f), f.name());
		}
		SetEntry reset = d.build();
		assertNull(reset.mute());
		assertNull(reset.volume());
		assertNull(reset.limit());
		assertNull(reset.cue());
		assertEquals("1", d.text(Field.LIMIT_WINDOW), "the window is shown in seconds");
		assertFalse(d.perSource());
	}

	@Test
	void togglesAreSetThroughTheirOwnMethods() {
		EntryDraft d = draft("*");
		assertThrows(IllegalArgumentException.class, () -> d.set(Field.MUTE, "maybe"));
		d.setMute(null);
		assertNull(d.mute());
		d.setEnabled(false);
		assertFalse(d.build().enabled());
	}

	@Test
	void buildRefusesAnInvalidDraft() {
		EntryDraft d = draft("*");
		d.set(Field.VOLUME, "999");
		assertThrows(IllegalStateException.class, d::build);
	}

	private static EntryDraft draft(String pattern) {
		EntryDraft d = EntryDraft.blank("r1", BOOST);
		d.set(Field.TARGET, pattern);
		return d;
	}
}
