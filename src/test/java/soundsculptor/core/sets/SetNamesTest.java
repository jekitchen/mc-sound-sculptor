package soundsculptor.core.sets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** PRD 9.3: a translation can put the set's name anywhere in "(copy)", "(imported)" and the numbered names. */
class SetNamesTest {
	/** Forms that put the name last, as a French translation might. */
	private static final SetNames.Forms NAME_LAST = new SetNames.Forms() {
		@Override
		public String copy(String name) {
			return "Copie de " + name;
		}

		@Override
		public String imported(String name) {
			return "Importé : " + name;
		}

		@Override
		public String numbered(String name, int n) {
			return n + "e " + name;
		}
	};

	@AfterEach
	void english() {
		SetNames.use(SetNames.ENGLISH);
	}

	@Test
	void englishIsTheDefault() {
		SetLibrary lib = SetLibrary.EMPTY.put(new SoundSet("a", "Farm", List.of()));
		assertEquals("Farm (copy)", lib.uniqueName("Farm", null, SetNames.forms()::copy, Set.of()));
		assertEquals("Farm (2)", lib.uniqueName("Farm", null));
	}

	@Test
	void duplicateAndImportUseTheTranslatedForms() {
		SetNames.use(NAME_LAST);
		Workspace ws = new Workspace(soundsculptor.core.profile.Profile.empty("Test"),
				SetLibrary.EMPTY.put(new SoundSet("set-farm", "Farm", List.of(SetEntry.of("e1", "*cow*")))));
		NewSet copy = LibraryOps.duplicate(ws, "set-farm");
		assertEquals("Copie de Farm", copy.workspace().library().get(copy.setId()).name());
		NewSet again = LibraryOps.duplicate(copy.workspace(), "set-farm");
		assertEquals("2e Copie de Farm", again.workspace().library().get(again.setId()).name());
		assertEquals("Importé : Farm", ws.library().uniqueName("Farm", null, SetNames.forms()::imported, Set.of()));
	}

	@Test
	void aNumberedPatternWithoutTheNumberFallsBackToEnglish() {
		// A translation that leaves out %2$s would give the same name at every try.
		SetNames.use(new SetNames.Forms() {
			@Override
			public String copy(String name) {
				return name + " (copie)";
			}

			@Override
			public String imported(String name) {
				return name + " (importé)";
			}

			@Override
			public String numbered(String name, int n) {
				return name + " (encore)";
			}
		});
		SetLibrary lib = SetLibrary.EMPTY.put(new SoundSet("a", "Farm", List.of())).put(new SoundSet("b", "Farm (encore)", List.of()));
		// The third try would repeat the second, so the English pattern takes over.
		assertEquals("Farm (3)", lib.uniqueName("Farm", null));
		lib = lib.put(new SoundSet("c", "Farm (3)", List.of()));
		assertEquals("Farm (4)", lib.uniqueName("Farm", null));
	}

	@Test
	void aLongNameIsCutSoTheFormFits() {
		SetNames.use(NAME_LAST);
		String name = "x".repeat(SoundSet.MAX_NAME_LENGTH);
		SetLibrary lib = SetLibrary.EMPTY.put(new SoundSet("a", name, List.of()));
		String copy = lib.uniqueName(name, null, SetNames.forms()::copy, Set.of());
		assertEquals(SoundSet.MAX_NAME_LENGTH, copy.length());
		assertTrue(copy.startsWith("Copie de x"), copy);
	}
}
