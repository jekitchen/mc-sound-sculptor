package soundsculptor.core.profile;

import soundsculptor.core.AtomicFiles;
import soundsculptor.core.Message;
import soundsculptor.core.cue.PlaceCues;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Profile files on disk (PRD FR-11.8): one human-readable JSON file per profile in {@code config/soundsculptor/profiles/},
 * named from its key. Writes are atomic (temp file, then rename) and the previous version is kept as {@code .bak}.
 * A file that fails to load is renamed {@code .broken} and an empty profile is used instead.
 *
 * <p>Not thread-safe; callers save from one background thread.
 */
public final class ProfileStore {
	/** Files larger than this are not loaded (PRD FR-11.6 uses the same limit for imports). */
	public static final long MAX_FILE_BYTES = 1024 * 1024;

	private final Path directory;

	public ProfileStore(Path directory) {
		this.directory = directory;
	}

	public Path directory() {
		return directory;
	}

	/**
	 * The outcome of loading: the profile to use, and, if the file was unusable, why and where it was moved.
	 *
	 * @param everywhereCuesSaved whether the file held Everywhere's cue settings; false for a new or broken profile and
	 *                            for a file saved before they moved to the places (FR-7, decided 2026-11-21)
	 */
	public record LoadResult(Profile profile, Message problem, Path brokenCopy, boolean everywhereCuesSaved) {
		/** A loaded profile whose cue settings are its own. */
		public LoadResult(Profile profile, Message problem, Path brokenCopy) {
			this(profile, problem, brokenCopy, true);
		}

		public boolean failed() {
			return problem != null;
		}
	}

	/** The file for a profile key, e.g. "play.example.net" or "world:1a2b" (made safe for file names). */
	public Path fileFor(String key) {
		return directory.resolve(safeName(key) + ".json");
	}

	static String safeName(String key) {
		String lower = key.toLowerCase(Locale.ROOT);
		StringBuilder out = new StringBuilder(lower.length());
		for (int i = 0; i < lower.length(); i++) {
			char c = lower.charAt(i);
			out.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_' ? c : '_');
		}
		String name = out.toString();
		return name.isEmpty() || name.chars().allMatch(c -> c == '.') ? "_" + name : name;
	}

	/**
	 * A profile file in the folder.
	 *
	 * @param file     the file
	 * @param worldKey the key recorded in it, or null if it records none (saved before keys were recorded) or cannot be
	 *                 read; such a file's key is unknown, because file names are made safe and cannot be turned back
	 */
	public record Stored(Path file, String worldKey) {
	}

	/**
	 * Every profile file in the folder, sorted by file name, for the Profiles tab (FR-11.3). Backups ({@code .bak}),
	 * {@code .broken} copies and temp files are not profiles and are left out. Only each file's key is read; load a
	 * profile with {@link #load} to show its details.
	 */
	public List<Stored> list() throws IOException {
		if (!Files.isDirectory(directory)) {
			return List.of();
		}
		List<Path> files;
		try (Stream<Path> entries = Files.list(directory)) {
			files = entries.filter(f -> f.getFileName().toString().endsWith(".json") && Files.isRegularFile(f))
					.sorted(Comparator.comparing(f -> f.getFileName().toString()))
					.toList();
		}
		List<Stored> stored = new ArrayList<>(files.size());
		for (Path file : files) {
			String key = null;
			try {
				if (Files.size(file) <= MAX_FILE_BYTES) {
					key = ProfileCodec.worldKeyOf(Files.readString(file, StandardCharsets.UTF_8)).orElse(null);
				}
			} catch (IOException e) {
				// Unreadable: listed without a key.
			}
			stored.add(new Stored(file, key));
		}
		return stored;
	}

	/** Loads the profile for {@code key}; a missing file gives an empty profile named {@code defaultName}. */
	public LoadResult load(String key, String defaultName) {
		return load(key, defaultName, null);
	}

	/**
	 * Loads the profile for {@code key}, migrated ({@link ProfileCodec#read(String, ProfileMigrations.Backup, PlaceCues)}).
	 * A missing or broken file gives an empty profile named {@code defaultName}, whose Everywhere also starts from
	 * {@code appWide}: a profile with no cue settings of its own starts from the old app-wide ones (decided 2026-11-21).
	 *
	 * @param appWide the old app-wide cue settings, or {@code null} for none
	 */
	public LoadResult load(String key, String defaultName, PlaceCues appWide) {
		Path file = fileFor(key);
		if (!Files.exists(file)) {
			return new LoadResult(Profile.empty(defaultName, appWide), null, null, false);
		}
		Message problem;
		try {
			if (Files.size(file) > MAX_FILE_BYTES) {
				problem = Message.of(ProfileText.TOO_LARGE);
			} else {
				// An older file is saved as <name>.v<n>.bak before it is migrated (PRD 7.6).
				String text = Files.readString(file, StandardCharsets.UTF_8);
				ProfileCodec.Read read = ProfileCodec.read(text, version -> ProfileMigrations.backup(file, version), appWide);
				return new LoadResult(read.profile(), null, null, read.everywhereCuesSaved());
			}
		} catch (ProfileFormatException e) {
			problem = e.problem();
		} catch (IOException e) {
			problem = Message.of(ProfileText.UNREADABLE, String.valueOf(e.getMessage()));
		}
		Path broken = file.resolveSibling(file.getFileName() + ".broken");
		try {
			Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			broken = null;
		}
		return new LoadResult(Profile.empty(defaultName, appWide), problem, broken, false);
	}

	/**
	 * Reads a stored profile file without changing it, migrated like {@link #load(String, String, PlaceCues)}: for the
	 * Profiles tab, whose Rename, Copy from and Export then work on the same profile a join would load. An older file is
	 * shown migrated but not backed up or rewritten.
	 *
	 * @param appWide the old app-wide cue settings, or {@code null} for none
	 * @throws ProfileFormatException if the file is larger than {@value #MAX_FILE_BYTES} bytes or not a valid profile
	 */
	public Profile readStored(Path file, PlaceCues appWide) throws IOException, ProfileFormatException {
		if (Files.size(file) > MAX_FILE_BYTES) {
			throw ProfileText.problem(ProfileText.TOO_LARGE);
		}
		return ProfileCodec.read(Files.readString(file, StandardCharsets.UTF_8), version -> { }, appWide).profile();
	}

	/**
	 * Deletes the profile for {@code key} (PRD FR-11.3); a new empty one is created on the next join. The deleted file
	 * is kept as {@code .bak}, so it can still be recovered by hand.
	 *
	 * @return whether there was a profile to delete
	 */
	public boolean delete(String key) throws IOException {
		Path file = fileFor(key);
		if (!Files.exists(file)) {
			return false;
		}
		Files.move(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
		return true;
	}

	/** Saves atomically, keeping the previous file as {@code .bak}. The file records {@code key} (see {@link #list}). */
	public void save(String key, Profile profile) throws IOException {
		save(key, profile, true);
	}

	/** Like {@link #save(String, Profile)}; see {@link AtomicFiles#write(Path, String, boolean)} for {@code waitOutLock}. */
	public void save(String key, Profile profile, boolean waitOutLock) throws IOException {
		AtomicFiles.write(fileFor(key), ProfileCodec.toJson(profile, key), waitOutLock);
	}
}
