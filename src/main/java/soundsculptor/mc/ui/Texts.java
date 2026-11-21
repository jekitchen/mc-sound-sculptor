package soundsculptor.mc.ui;

import java.util.Collection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Text built from parts, each joined by a pattern from the language file (PRD 9.3), so a translation decides the order
 * and the marks between them: a percentage, two details side by side, a list of names, sentences in a row.
 */
public final class Texts {
	private Texts() {
	}

	/** "50%" ({@code soundsculptor.ui.percent}). */
	public static Component percent(int value) {
		return Component.translatable("soundsculptor.ui.percent", value);
	}

	/** "3 entries · Also used in 2 places" ({@code soundsculptor.ui.pair}); just one of them when the other is empty. */
	public static String pair(String first, String second) {
		if (first.isEmpty()) {
			return second;
		}
		if (second.isEmpty()) {
			return first;
		}
		return Component.translatable("soundsculptor.ui.pair", first, second).getString();
	}

	/** "Trading hall, Mob farm, Base" ({@code soundsculptor.ui.list_separator} between the names). */
	public static String list(Collection<String> names) {
		return String.join(Component.translatable("soundsculptor.ui.list_separator").getString(), names);
	}

	/** Whole sentences one after another ({@code soundsculptor.ui.sentence_separator} between them: a space in English). */
	public static MutableComponent sentences(Collection<? extends Component> sentences) {
		MutableComponent text = Component.empty();
		Component separator = Component.translatable("soundsculptor.ui.sentence_separator");
		for (Component sentence : sentences) {
			if (!text.getSiblings().isEmpty()) {
				text.append(separator);
			}
			text.append(sentence);
		}
		return text;
	}
}
