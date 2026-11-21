package soundsculptor.core.sets;

/**
 * The names a Sound Set gets when its name is taken (PRD 9.3): "Farm (copy)" for Duplicate, "Farm (imported)" for an
 * import's suggestion, and "Farm (2)", "Farm (copy) (2)" after that. Each is a whole pattern in the language file, so a
 * translation can put the name anywhere in it ("Copie de Farm"). The Minecraft side sets the translated forms when the
 * game starts; until then, and in tests, they are English. Pure Java.
 */
public final class SetNames {
	/** How a taken name is changed. */
	public interface Forms {
		/** Duplicate's first try: "Farm (copy)". */
		String copy(String name);

		/** The import's suggested name for a set whose name is taken: "Farm (imported)". */
		String imported(String name);

		/** The {@code n}th try (2, 3, …): "Farm (2)", "Farm (copy) (2)". */
		String numbered(String name, int n);
	}

	/** The English forms, as in en_us.json. */
	public static final Forms ENGLISH = new Forms() {
		@Override
		public String copy(String name) {
			return name + " (copy)";
		}

		@Override
		public String imported(String name) {
			return name + " (imported)";
		}

		@Override
		public String numbered(String name, int n) {
			return name + " (" + n + ")";
		}
	};

	private static volatile Forms forms = ENGLISH;

	private SetNames() {
	}

	public static Forms forms() {
		return forms;
	}

	/** Uses {@code value} from now on, e.g. forms that read the player's language each time. */
	public static void use(Forms value) {
		forms = value == null ? ENGLISH : value;
	}
}
