package soundsculptor.core.boost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import soundsculptor.core.Fixtures;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;

/** PRD FR-5.4 and section 7.2: when a level above 100% plays louder, and when the boost dialog must ask first. */
class BoostSupportTest {
	private static final SoundSet LOUD = new SoundSet("loud", "Loud bells", List.of(SetEntry.of("e", "*bell*").withVolume(150)));
	private static final SoundSet QUIET = new SoundSet("quiet", "Quiet cows", List.of(SetEntry.of("e", "*cow*").withVolume(100)));
	private static final SetLibrary LIBRARY = new SetLibrary(List.of(LOUD, QUIET));

	@AfterEach
	void reset() {
		BoostSupport.reset();
	}

	private static Profile attached(String name, String setId) {
		return Profile.empty(name).withEverywhere(PlaceSets.EMPTY.attach(setId));
	}

	@Test
	void deviceStateStartsUnknownAndReportsChanges() {
		assertEquals(BoostSupport.Device.UNKNOWN, BoostSupport.device());
		assertTrue(BoostSupport.deviceAllows(), "nothing known yet: a device that cannot boost plays at 100% anyway");
		assertTrue(BoostSupport.report(true));
		assertFalse(BoostSupport.report(true), "no change");
		assertTrue(BoostSupport.report(false));
		assertEquals(BoostSupport.Device.UNSUPPORTED, BoostSupport.device());
		assertFalse(BoostSupport.deviceAllows());
	}

	@Test
	void boostPlaysOnlyWhenTheDeviceAllowsAndTheProfileConfirmed() {
		Profile unconfirmed = attached("A", "loud");
		Profile confirmed = unconfirmed.withBoostConfirmed(true);
		BoostSupport.report(true);
		assertFalse(BoostSupport.allows(unconfirmed));
		assertTrue(BoostSupport.allows(confirmed));
		BoostSupport.report(false);
		assertFalse(BoostSupport.allows(confirmed), "a device that ignores the raised cap plays at 100%");
	}

	@Test
	void asksOnlyWhenTheProfileWouldUseALevelAbove100() {
		assertTrue(BoostSupport.needsConfirm(attached("A", "loud"), LIBRARY), "attaching a set with a 150% entry");
		assertFalse(BoostSupport.needsConfirm(attached("A", "quiet"), LIBRARY), "100% is not a boost");
		assertFalse(BoostSupport.needsConfirm(attached("A", "loud").withBoostConfirmed(true), LIBRARY), "asked once per profile");
		assertFalse(BoostSupport.needsConfirm(Profile.empty("A"), LIBRARY), "a set attached nowhere does not count");
	}

	@Test
	void aLinkedSetAsksOnceInEachProfile() {
		// FR-5.4: the same library set attached in two worlds; confirming in the first does not confirm the second.
		Profile first = attached("World 1", "loud").withBoostConfirmed(true);
		Profile second = attached("World 2", "loud");
		assertFalse(BoostSupport.needsConfirm(first, LIBRARY));
		assertTrue(BoostSupport.needsConfirm(second, LIBRARY));
		assertFalse(BoostSupport.needsConfirm(second.withBoostConfirmed(true), LIBRARY));
	}

	@Test
	void aBoostedQuickChangesEntryCounts() {
		Profile p = Profile.empty("A").withEverywhere(Fixtures.quick(SetEntry.of("q", "*bell*").withVolume(200)));
		assertTrue(BoostSupport.needsConfirm(p, SetLibrary.EMPTY));
		assertFalse(BoostSupport.needsConfirm(p.withBoostConfirmed(true), SetLibrary.EMPTY));
	}
}
