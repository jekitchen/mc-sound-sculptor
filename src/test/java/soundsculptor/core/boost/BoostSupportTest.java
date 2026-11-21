package soundsculptor.core.boost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import soundsculptor.core.Fixtures;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.Attachment;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

/**
 * PRD FR-5.4 and section 7.2: when a level above 100% plays louder, and when the boost dialog must ask first. It asks
 * only for a change that adds boost (a new or louder level above 100%), never for one that keeps, lowers or removes
 * boost, even in a profile that already has an unconfirmed boost.
 */
class BoostSupportTest {
	private static final SoundSet LOUD = new SoundSet("loud", "Loud bells", List.of(SetEntry.of("e", "*bell*").withVolume(150)));
	private static final SoundSet QUIET = new SoundSet("quiet", "Quiet cows", List.of(SetEntry.of("e", "*cow*").withVolume(100)));
	private static final SetLibrary LIBRARY = new SetLibrary(List.of(LOUD, QUIET));

	@AfterEach
	void reset() {
		BoostSupport.reset();
	}

	private static Profile attached(String name, String... setIds) {
		PlaceSets place = PlaceSets.EMPTY;
		for (String id : setIds) {
			place = place.attach(id);
		}
		return Profile.empty(name).withEverywhere(place);
	}

	private static boolean asks(Profile before, Profile after) {
		return BoostSupport.needsConfirm(before, LIBRARY, after, LIBRARY);
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
		BoostSupport.reset();
		assertEquals(BoostSupport.Device.UNKNOWN, BoostSupport.device(), "a new audio device is asked again");
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
	void attachingASetThatAddsBoostAsksOncePerProfile() {
		Profile empty = Profile.empty("A");
		assertTrue(asks(empty, attached("A", "loud")), "attaching a set with a 150% entry");
		assertFalse(asks(empty, attached("A", "quiet")), "100% is not a boost");
		assertFalse(asks(empty.withBoostConfirmed(true), attached("A", "loud").withBoostConfirmed(true)), "asked once per profile");
		BoostSupport.report(false);
		assertFalse(asks(empty, attached("A", "loud")), "never on a device that cannot boost");
	}

	@Test
	void anUnconfirmedProfileWithABoostAsksOnlyForMore() {
		// Review of #130: a profile that already has an unconfirmed boosted set (made louder in another world).
		Profile before = attached("B", "loud");
		assertFalse(asks(before, attached("B", "loud", "quiet")), "attaching a quiet set");
		assertFalse(asks(before, before.withName("B renamed")), "a change elsewhere");
		SetLibrary renamed = LIBRARY.put(new SoundSet("loud", "Bells", LOUD.entries()));
		assertFalse(BoostSupport.needsConfirm(before, LIBRARY, before, renamed), "renaming the loud set");
		SetLibrary lower = LIBRARY.put(new SoundSet("loud", "Loud bells", List.of(SetEntry.of("e", "*bell*").withVolume(145))));
		assertFalse(BoostSupport.needsConfirm(before, LIBRARY, before, lower), "lowering 150% to 145%");
		SetLibrary higher = LIBRARY.put(new SoundSet("loud", "Loud bells", List.of(SetEntry.of("e", "*bell*").withVolume(160))));
		assertTrue(BoostSupport.needsConfirm(before, LIBRARY, before, higher), "raising 150% to 160%");
		SetLibrary another = LIBRARY.put(new SoundSet("loud", "Loud bells", List.of(SetEntry.of("e", "*bell*").withVolume(150),
				SetEntry.of("f", "*horn*").withVolume(120))));
		assertTrue(BoostSupport.needsConfirm(before, LIBRARY, before, another), "a second boosted entry");
		assertFalse(asks(before, attached("B")), "detaching the loud set");
		assertTrue(BoostSupport.hasUnconfirmedBoost(before, LIBRARY), "it still asks when the screen opens");
	}

	@Test
	void theSameSetInASecondPlaceIsANewUse() {
		Profile here = attached("A", "loud");
		Profile zoneToo = here.withZones(List.of(new Zone("z", "Zone z", true, "world:1", "minecraft:overworld", new Point(0, 64, 0),
				new Shape.Box(20, 20, 20), PlaceSets.EMPTY.attach("loud"))));
		assertTrue(asks(here, zoneToo));
	}

	@Test
	void aLinkedSetAsksOnceInEachProfile() {
		// FR-5.4: the same library set attached in two worlds; confirming in the first does not confirm the second.
		assertFalse(asks(Profile.empty("W1").withBoostConfirmed(true), attached("W1", "loud").withBoostConfirmed(true)));
		assertTrue(asks(Profile.empty("W2"), attached("W2", "loud")));
	}

	@Test
	void onlyEntriesThatPlayLouderCount() {
		// On, not muted, above 100%, in a set that is attached and switched on.
		SoundSet mixed = new SoundSet("mixed", "Mixed", List.of(
				SetEntry.of("off", "*a*").withVolume(150).withEnabled(false),
				SetEntry.of("muted", "*b*").withVolume(150).withMute(true),
				SetEntry.of("full", "*c*").withVolume(100),
				SetEntry.of("loud", "*d*").withVolume(120),
				SetEntry.of("unmuted", "*e*").withVolume(130).withMute(false)));
		SetLibrary library = new SetLibrary(List.of(mixed, LOUD));
		Profile p = Profile.empty("A").withEverywhere(PlaceSets.EMPTY.attach("mixed"));
		assertEquals(Map.of("everywhere\nmixed\nloud", 120, "everywhere\nmixed\nunmuted", 130), p.boostedUses(library));
		Profile switchedOff = Profile.empty("A").withEverywhere(new PlaceSets(List.of(new Attachment("loud", false)), null));
		assertFalse(switchedOff.hasBoost(library), "an attachment that is switched off");
		assertFalse(Profile.empty("A").hasBoost(library), "a set attached nowhere");
	}

	@Test
	void popUpStepsAskOnlyWhenTheyRaiseABoost() {
		assertTrue(BoostSupport.raises(null, 105));
		assertTrue(BoostSupport.raises(100, 105));
		assertTrue(BoostSupport.raises(150, 155));
		assertFalse(BoostSupport.raises(150, 145), "lowering");
		assertFalse(BoostSupport.raises(150, 150), "keeping");
		assertFalse(BoostSupport.raises(150, 100));
		assertFalse(BoostSupport.raises(90, 95));
		assertFalse(BoostSupport.raises(150, null), "clearing the level");
	}

	@Test
	void aBoostedQuickChangesEntryCounts() {
		Profile p = Profile.empty("A").withEverywhere(Fixtures.quick(SetEntry.of("q", "*bell*").withVolume(200)));
		assertTrue(asks(Profile.empty("A"), p));
		assertTrue(p.hasBoost(SetLibrary.EMPTY));
	}
}
