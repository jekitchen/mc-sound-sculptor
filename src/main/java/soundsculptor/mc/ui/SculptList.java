package soundsculptor.mc.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import soundsculptor.mc.compat.Keys;

/**
 * Scrolling list (PRD 6.2, 6.3) on top of vanilla's container list, whose rows hold their own buttons.
 *
 * <ul>
 *   <li>Only visible rows are drawn (vanilla skips rows outside the list).</li>
 *   <li>Mouse wheel scrolls one row per notch; the scrollbar can be dragged.</li>
 *   <li>The whole list is one Tab stop: Tab enters it on the last focused row, Tab again leaves it. Inside, Up/Down
 *   move between rows, Left/Right between a row's buttons, Page Up/Down by a screenful.</li>
 *   <li>The narrator reads the row's text, the focused button and "Selected list row 3 out of 40".</li>
 *   <li>{@link #setRows} keeps the scroll position and the focused row (matched by key) when the list refreshes.</li>
 * </ul>
 */
public class SculptList extends ContainerObjectSelectionList<SculptList.Row> {
	public static final int ROW_HEIGHT = 24;

	private final Component label;
	private Supplier<Component> emptyText;
	private Object rememberedKey;
	private int rememberedButton;
	private int rowsDrawnThisFrame;
	private int rowsDrawnLastFrame;
	/** The rows' heights added up, and how many rows that counts, so adding a row stays one addition. */
	private int rowsHeight;
	private int rowsCounted;
	/** The content width the rows' heights were worked out for; a different width works them out again. */
	private int heightsWidth = -1;

	public SculptList(int width, int height, Component label) {
		super(Minecraft.getInstance(), width, height, 0, ROW_HEIGHT);
		this.label = label;
		setMessage(label);
	}

	// ---- Rows ----

	/** Replaces all rows, keeping the scroll position and the focused row (matched by {@link Row#key()}). */
	public void setRows(List<Row> rows) {
		double scroll = scrollAmount();
		Row focusedRow = getFocused();
		int focusedButton = focusedRow == null ? rememberedButton : focusedRow.focusedButtonIndex();
		Object focusedKey = focusedRow == null ? rememberedKey : focusedRow.key();
		boolean hadFocus = focusedRow != null;

		replaceEntries(rows);
		setScrollAmount(scroll);

		Row match = findRow(focusedKey);
		if (hadFocus && match != null && Minecraft.getInstance().gui.screen() instanceof SculptScreen screen) {
			screen.focusPath(pathTo(match, focusedButton));
			setScrollAmount(scroll);
		}
	}

	/** Text shown in the middle of the list when it has no rows. */
	public void setEmptyText(Supplier<Component> text) {
		this.emptyText = text;
	}

	public Row findRow(Object key) {
		for (Row row : children()) {
			if (Objects.equals(row.key(), key)) {
				return row;
			}
		}
		return null;
	}

	public int indexOf(Row row) {
		return children().indexOf(row);
	}

	/** How many rows were drawn in the last frame (only visible ones are). */
	public int rowsDrawnLastFrame() {
		return rowsDrawnLastFrame;
	}

	public int visibleRowCount() {
		return Math.max(1, getHeight() / ROW_HEIGHT);
	}

	/** The width rows lay out their buttons and text in (vanilla's row inset by 2 px each side). */
	private int contentWidth() {
		return getRowWidth() - 4;
	}

	/**
	 * Where {@code row}'s buttons and columns go in this list now, without drawing it: for the layout check (A-3). The
	 * content area is vanilla's (the row inset by 2 px each side, as {@code Entry.getContentX} and
	 * {@code getContentWidth}).
	 */
	public Row.Layout layout(Row row) {
		return row.layout(getRowLeft() + 2, getRowWidth() - 4);
	}

	// ---- Layout and drawing ----

	@Override
	public int getRowWidth() {
		return getWidth() - scrollbarWidth() - 4;
	}

	/**
	 * Where the next added row goes. Vanilla adds up the height of every row already in the list, so filling a list of
	 * n rows took n² steps (about 6 ms for the 2,000 rows of the Sounds tab's full list, PRD 9.1). Here the sum is kept
	 * as rows are added, so it is one addition.
	 */
	@Override
	public int getNextY() {
		if (rowsCounted != children().size()) {
			rowsHeight = 0;
			for (Row row : children()) {
				rowsHeight += row.getHeight();
			}
			rowsCounted = children().size();
		}
		return getY() + 2 - (int) scrollAmount() + rowsHeight;
	}

	@Override
	protected void clearEntries() {
		super.clearEntries();
		rowsHeight = 0;
		rowsCounted = 0;
	}

	/** Works the rows' heights out again after the list's width changed, keeping the scroll position. */
	private void refreshHeights() {
		heightsWidth = contentWidth();
		rowsHeight = 0;
		for (Row row : children()) {
			row.setHeight(row.heightFor(heightsWidth));
			rowsHeight += row.getHeight();
		}
		rowsCounted = children().size();
		setScrollAmount(scrollAmount());
	}

	@Override
	protected double scrollRate() {
		return ROW_HEIGHT;
	}

	@Override
	protected void extractListBackground(GuiGraphicsExtractor g) {
		Draw.fill(g, getX(), getY(), getWidth(), getHeight(), Theme.LIST_BACKGROUND);
	}

	@Override
	protected void extractListSeparators(GuiGraphicsExtractor g) {
		Draw.outline(g, getX(), getY(), getWidth(), getHeight(), Theme.SEPARATOR);
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		rowsDrawnThisFrame = 0;
		if (contentWidth() != heightsWidth && !children().isEmpty()) {
			refreshHeights();
		}
		super.extractWidgetRenderState(g, mouseX, mouseY, partialTick);
		rowsDrawnLastFrame = rowsDrawnThisFrame;
		if (children().isEmpty() && emptyText != null) {
			Font font = Minecraft.getInstance().font;
			Draw.centeredText(g, font, emptyText.get(), getX() + getWidth() / 2, getY() + (getHeight() - font.lineHeight) / 2, Theme.TEXT_DIM);
		}
	}

	// ---- Focus and keys ----

	/**
	 * Scrolls {@code row} into view and gives the path to its button {@code buttonIndex}, for
	 * {@link SculptScreen#focusPath}: e.g. an editor that opens at one of its rows.
	 */
	public ComponentPath revealRow(Row row, int buttonIndex) {
		scrollToEntry(row);
		return pathTo(row, buttonIndex);
	}

	/**
	 * Moves keyboard focus to the row's first button (or its focus target), e.g. when the list opens in an overlay.
	 *
	 * @param host the screen the list is on, which need not be the screen showing yet (a pop-up opened while building)
	 */
	public void focusRow(SculptScreen host, Row row) {
		host.focusPath(pathTo(row, 0));
	}

	private ComponentPath pathTo(Row row, int buttonIndex) {
		List<? extends GuiEventListener> buttons = row.children();
		GuiEventListener target = buttons.get(Math.max(0, Math.min(buttonIndex, buttons.size() - 1)));
		return ComponentPath.path(this, ComponentPath.path(row, ComponentPath.leaf(target)));
	}

	/** One Tab stop: entering goes to the remembered row, Tab inside leaves the list. Arrows stay vanilla. */
	@Override
	public ComponentPath nextFocusPath(FocusNavigationEvent event) {
		if (event instanceof FocusNavigationEvent.TabNavigation) {
			if (getFocused() != null || children().isEmpty()) {
				return null;
			}
			Row target = findRow(rememberedKey);
			if (target == null) {
				target = children().get(0);
			}
			return pathTo(target, rememberedButton);
		}
		return super.nextFocusPath(event);
	}

	@Override
	public void setFocused(GuiEventListener listener) {
		super.setFocused(listener);
		if (listener instanceof Row row) {
			rememberedKey = row.key();
			rememberedButton = Math.max(0, row.focusedButtonIndex());
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if ((key == Keys.PAGE_UP || key == Keys.PAGE_DOWN) && getFocused() != null) {
			Row current = getFocused();
			Row target = pageFrom(current, key == Keys.PAGE_DOWN ? 1 : -1);
			if (target != current && Minecraft.getInstance().gui.screen() instanceof SculptScreen screen) {
				screen.focusPath(pathTo(target, current.focusedButtonIndex()));
				scrollToEntry(target);
			}
			return true;
		}
		return super.keyPressed(event);
	}

	/**
	 * The row a screenful away from {@code from} ({@code step} 1 down, -1 up): as many rows as fit in the list's height
	 * (taller rows make a page fewer rows), at least one, and never past the first or last row.
	 */
	private Row pageFrom(Row from, int step) {
		int index = indexOf(from);
		int used = 0;
		int moved = 0;
		while (index + step >= 0 && index + step < children().size()) {
			used += children().get(index + step).getHeight();
			if (moved > 0 && used > getHeight()) {
				break;
			}
			index += step;
			moved++;
		}
		return children().get(index);
	}

	/**
	 * Narrates the focused row: its focused button (which reads the row's text first) and "Selected list row 3 out
	 * of 40". Vanilla 26.2 would narrate the row under the mouse instead, which is wrong for keyboard users whose
	 * mouse rests over the list, so this is built here and behaves the same in every version.
	 */
	@Override
	public void updateWidgetNarration(NarrationElementOutput output) {
		Row focused = getFocused();
		if (focused == null) {
			output.add(NarratedElementType.TITLE, Component.translatable("soundsculptor.ui.list", label, children().size()));
			output.add(NarratedElementType.USAGE, Component.translatable("soundsculptor.ui.list.usage"));
			return;
		}
		if (focused.getFocused() instanceof AbstractWidget button) {
			button.updateNarration(output.nest());
		}
		narrateListElementPosition(output, focused);
	}

	/**
	 * A row: text columns on one or two lines (cut with "…" when too long, full text in a tooltip on hover) and inline
	 * icon buttons on the right. Its buttons narrate the row's text before their own label. A row without buttons
	 * (read-only) gets an invisible focus target instead, so it can still be reached with the keyboard and narrated.
	 *
	 * <p>At a narrow width (a small window, a large GUI scale, a longer language) a row that does not fit first hides
	 * its {@linkplain Column#optional optional} columns, then moves columns that still do not fit onto the next line,
	 * and grows taller for each line it adds. Hidden text stays in the row's narration, in the tooltip over the row's
	 * text, and in its buttons' tooltips, which show on keyboard focus (PRD 9.3, 11.5).
	 */
	public static class Row extends ContainerObjectSelectionList.Entry<Row> {
		static final int SWATCH_SIZE = 12;

		/** The least the rest column (width 0) gets while a {@linkplain Column#shrinks shrinking} column can give way. */
		public static final int REST_MIN_WIDTH = 80;
		/** The least a {@linkplain Column#shrinks shrinking} column gives way to. */
		public static final int SHRINK_MIN_WIDTH = 40;
		/** How far apart two text lines are. */
		public static final int LINE_STEP = 11;
		/** How much taller a row grows for each line past the second. */
		public static final int EXTRA_LINE_HEIGHT = 12;

		/**
		 * A text column. {@code width} 0 means "take the remaining space" (at most one such column per line): the rest
		 * column, which holds the row's long text (a name, an ID, a pattern) and is cut with "…" when it does not fit.
		 * A fixed width should fit every text the column can show ({@link #widthFor}), so rows line up and nothing is cut.
		 * {@code line} 0 is the main line; line 1 is a second, dimmer line underneath.
		 *
		 * @param shrinks the column shows something the player named (a zone), which may be longer than its width: it is
		 *                cut with "…" like the rest column, and in a narrow row it gives way (down to
		 *                {@value #SHRINK_MIN_WIDTH} px) so the rest column keeps {@value #REST_MIN_WIDTH} px
		 * @param hint    shown while the mouse is over this column, or {@code null}; asked for every frame, so it can
		 *                change while shown (a live timer) without the row being rebuilt. The narrator reads it after
		 *                the column's text, so keyboard users get it too
		 * @param optional less important text (a profile's world key, a zone's size), hidden when the row does not fit;
		 *                never anything that tells a state apart (On, Off, Muted)
		 */
		public record Column(Supplier<String> text, int width, int line, boolean shrinks, Supplier<Component> hint, boolean optional) {
			public Column(Supplier<String> text, int width, int line, boolean shrinks, Supplier<Component> hint) {
				this(text, width, line, shrinks, hint, false);
			}

			public Column(Supplier<String> text, int width, int line, boolean shrinks) {
				this(text, width, line, shrinks, null);
			}

			public Column(Supplier<String> text, int width, int line) {
				this(text, width, line, false);
			}

			public Column(Supplier<String> text, int width) {
				this(text, width, 0);
			}

			/** The width a fixed column needs for the widest of {@code texts}: e.g. every status it can show. */
			/** This column, hidden when its row does not fit. */
			public Column hiddenWhenNarrow() {
				return new Column(text, width, line, shrinks, hint, true);
			}

			public static int widthFor(Component... texts) {
				Font font = Minecraft.getInstance().font;
				int widest = 0;
				for (Component text : texts) {
					widest = Math.max(widest, font.width(text));
				}
				return widest;
			}
		}

		private final Object key;
		private final List<Column> columns;
		private final List<SculptButton> buttons;
		private final RowFocus focusTarget;
		/** Color square at the start of the row (e.g. a zone's color), as 0xRRGGBB; -1 for none. */
		private int swatch = -1;
		/** Shown when the mouse is over the row's text, or {@code null}. */
		private Component tooltip;

		public Row(Object key, List<Column> columns, List<SculptButton> buttons) {
			this.key = key;
			this.columns = List.copyOf(columns);
			this.buttons = List.copyOf(buttons);
			for (SculptButton button : this.buttons) {
				button.narrationContext(this::narrationText);
			}
			this.focusTarget = this.buttons.isEmpty() ? new RowFocus(this) : null;
		}

		public Object key() {
			return key;
		}

		/**
		 * Shows a color square before the text. The color is decoration only: the row's text must say everything
		 * (A-5).
		 */
		public Row swatch(int rgb) {
			this.swatch = rgb & 0xFFFFFF;
			return this;
		}

		public int swatch() {
			return swatch;
		}

		/**
		 * A tooltip for the row's text, shown on hover (a cut-off column's full text, or a column's own
		 * {@linkplain Column#hint hint}, takes precedence). Keyboard and narrator users need the same text on one of the
		 * row's buttons, e.g. as that button's tooltip.
		 */
		public Row tooltip(Component text) {
			this.tooltip = text;
			return this;
		}

		public Component tooltip() {
			return tooltip;
		}

		public List<SculptButton> buttons() {
			return buttons;
		}

		public Component narrationText() {
			List<String> parts = new ArrayList<>();
			for (Column column : columns) {
				String text = column.text().get();
				if (!text.isEmpty()) {
					parts.add(text);
				}
				if (column.hint() != null) {
					parts.add(column.hint().get().getString());
				}
			}
			return Component.literal(Texts.list(parts));
		}

		/** The visible text of every column, for tests and diagnostics. */
		public List<String> columnTexts() {
			List<String> texts = new ArrayList<>();
			for (Column column : columns) {
				texts.add(column.text().get());
			}
			return texts;
		}

		int focusedButtonIndex() {
			GuiEventListener focused = getFocused();
			return focused == null || focusTarget != null ? 0 : buttons.indexOf(focused);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return focusTarget != null ? List.of(focusTarget) : buttons;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return focusTarget != null ? List.of(focusTarget) : buttons;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float partialTick) {
			if (owner != null) {
				owner.rowsDrawnThisFrame++;
			}
			int x = getContentX();
			int y = getContentY();
			int height = getContentHeight();
			if (getFocused() != null) {
				Draw.fill(g, getX(), getY(), getWidth(), getHeight(), Theme.ROW_FOCUSED);
			} else if (hovered) {
				Draw.fill(g, getX(), getY(), getWidth(), getHeight(), Theme.ROW_HOVER);
			}
			Layout layout = layout(x, getContentWidth());
			Component hiddenText = hiddenText(layout);
			for (SculptButton button : buttons) {
				button.tooltipExtra(hiddenText);
			}

			// Buttons, right-aligned.
			for (int i = 0; i < buttons.size(); i++) {
				SculptButton button = buttons.get(i);
				int buttonY = layout.buttonsBelow() ? y + layout.buttonsTop() : y + (height - button.getHeight()) / 2;
				button.setPosition(layout.buttonX().get(i), buttonY);
				button.extractRenderState(g, mouseX, mouseY, partialTick);
			}

			if (focusTarget != null) {
				focusTarget.setRectangle(getWidth(), getHeight(), getX(), getY());
				focusTarget.extractRenderState(g, mouseX, mouseY, partialTick);
			}

			// Color square, then text columns, line by line.
			Font font = Minecraft.getInstance().font;
			if (swatch >= 0) {
				int size = SWATCH_SIZE;
				int sy = y + (height - size) / 2;
				Draw.fill(g, x, sy, size, size, Theme.SEPARATOR);
				Draw.fill(g, x + 1, sy + 1, size - 2, size - 2, 0xFF000000 | swatch);
			}
			boolean severalLines = layout.lines() > 1 || layout.buttonsBelow();
			boolean columnTooltipShown = false;
			for (Placed placed : layout.columns()) {
				int line = placed.line();
				int textY = severalLines
						? y + 1 + line * LINE_STEP
						: y + (height - font.lineHeight) / 2 + 1;
				int color = line == 0 ? Theme.TEXT : Theme.TEXT_DIM;
				int cx = placed.x();
				String text = placed.column().text().get();
				boolean cut = Draw.truncatedText(g, font, text, cx, textY, placed.width(), color);
				boolean over = hovered && !columnTooltipShown && mouseX >= cx && mouseX < cx + placed.width()
						&& mouseY >= textY && mouseY < textY + font.lineHeight;
				if (over && cut) {
					g.setTooltipForNextFrame(font, Tooltip.splitTooltip(Minecraft.getInstance(),
							withHidden(Component.literal(text), hiddenText)), mouseX, mouseY);
					columnTooltipShown = true;
				} else if (over && placed.column().hint() != null) {
					g.setTooltipForNextFrame(font, Tooltip.splitTooltip(Minecraft.getInstance(),
							withHidden(placed.column().hint().get(), hiddenText)), mouseX, mouseY);
					columnTooltipShown = true;
				}
			}
			Component rowTooltip = tooltip == null ? hiddenText : withHidden(tooltip, hiddenText);
			if (rowTooltip != null && !columnTooltipShown && hovered && mouseX >= layout.textLeft() && mouseX < layout.textRight()) {
				g.setTooltipForNextFrame(font, Tooltip.splitTooltip(Minecraft.getInstance(), rowTooltip), mouseX, mouseY);
			}
		}

		/** The text of the columns {@code layout} hides, one per line, or {@code null} when it hides none. */
		private static Component hiddenText(Layout layout) {
			if (layout.hidden().isEmpty()) {
				return null;
			}
			List<String> texts = new ArrayList<>();
			for (Column column : layout.hidden()) {
				String text = column.text().get();
				if (!text.isEmpty()) {
					texts.add(text);
				}
			}
			return texts.isEmpty() ? null : Component.literal(String.join("\n", texts));
		}

		private static Component withHidden(Component text, Component hidden) {
			return hidden == null ? text : Component.empty().append(text).append("\n").append(hidden);
		}

		/**
		 * How tall this row is in a list whose rows are {@code contentWidth} wide inside: {@link #ROW_HEIGHT} for up to two
		 * lines beside the buttons, a line taller for each line past two, and room for the buttons' own line when they go
		 * under the text.
		 */
		int heightFor(int contentWidth) {
			Layout layout = layout(0, contentWidth);
			if (layout.buttonsBelow()) {
				return 4 + layout.buttonsTop() + Theme.WIDGET_HEIGHT;
			}
			return ROW_HEIGHT + Math.max(0, layout.lines() - 2) * EXTRA_LINE_HEIGHT;
		}

		/**
		 * Where the row's parts go in a row whose content starts at {@code x} and is {@code width} wide: the buttons
		 * right-aligned, then the color square and the text columns in the space left of them. A fixed column gets its
		 * width while there is room and less when there is not; the rest column (width 0) gets what its line has left,
		 * and a shrinking column gives way to keep it {@value #REST_MIN_WIDTH} px wide.
		 *
		 * <p>When a line does not fit (its rest column would get less than {@value #REST_MIN_WIDTH} px, or its fixed
		 * columns less than their width), the row's {@linkplain Column#optional optional} columns are hidden. Then, line
		 * by line, the last column of a line that still does not fit (never its rest column or its only column) moves to
		 * the end of the next line, adding a line when there is none. If a line still does not fit (its rest column alone
		 * is too narrow beside the buttons), the buttons go on a line of their own under the text and the text is laid out
		 * again across the row's whole width. Drawing and the layout check (A-3) both use this, so the check sees what the
		 * player sees.
		 */
		public Layout layout(int x, int width) {
			Layout inline = arrange(x, width, false);
			return inline.fits() || buttons.isEmpty() ? inline : arrange(x, width, true);
		}

		/**
		 * {@link #layout} with the buttons on the right of the text ({@code below} false) or on a line of their own under
		 * it, right-aligned, so the text gets the row's whole width.
		 */
		private Layout arrange(int x, int width, boolean below) {
			int buttonsWidth = 0;
			for (SculptButton button : buttons) {
				buttonsWidth += button.getWidth() + Theme.GAP;
			}
			List<Integer> buttonX = new ArrayList<>(buttons.size());
			int bx = x + width - buttonsWidth + Theme.GAP;
			for (SculptButton button : buttons) {
				buttonX.add(bx);
				bx += button.getWidth() + Theme.GAP;
			}
			int textLeft = swatch >= 0 ? x + SWATCH_SIZE + Theme.GAP : x;
			int textRight = below ? x + width : x + width - buttonsWidth - Theme.GAP;
			int room = textRight - textLeft;

			List<List<Integer>> lines = new ArrayList<>();
			for (int i = 0; i < columns.size(); i++) {
				int line = columns.get(i).line();
				while (lines.size() <= line) {
					lines.add(new ArrayList<>());
				}
				lines.get(line).add(i);
			}
			List<Column> hidden = new ArrayList<>();
			if (!allFit(lines, room)) {
				for (List<Integer> line : lines) {
					for (int k = line.size() - 1; k >= 0; k--) {
						if (columns.get(line.get(k)).optional()) {
							hidden.add(0, columns.get(line.remove(k)));
						}
					}
				}
			}
			for (int l = 0; l < lines.size(); l++) {
				List<Integer> line = lines.get(l);
				int moving;
				while (!fits(line, room) && (moving = movable(line)) >= 0) {
					if (l + 1 == lines.size()) {
						lines.add(new ArrayList<>());
					}
					lines.get(l + 1).add(line.remove(moving));
				}
			}
			lines.removeIf(List::isEmpty);
			boolean fits = allFit(lines, room);

			List<Placed> placed = new ArrayList<>(columns.size());
			for (int l = 0; l < lines.size(); l++) {
				List<Integer> line = lines.get(l);
				int[] widths = new int[line.size()];
				int fixed = 0;
				boolean hasRest = false;
				for (int k = 0; k < line.size(); k++) {
					Column column = columns.get(line.get(k));
					widths[k] = column.width();
					fixed += column.width() + Theme.GAP;
					hasRest |= column.width() == 0;
				}
				int deficit = hasRest ? REST_MIN_WIDTH - (room - fixed) : 0;
				for (int k = 0; k < line.size() && deficit > 0; k++) {
					Column column = columns.get(line.get(k));
					if (column.shrinks()) {
						int give = Math.min(deficit, Math.max(0, column.width() - SHRINK_MIN_WIDTH));
						widths[k] -= give;
						fixed -= give;
						deficit -= give;
					}
				}
				int cx = textLeft;
				for (int k = 0; k < line.size(); k++) {
					Column column = columns.get(line.get(k));
					int columnWidth = column.width() > 0 ? widths[k] : Math.max(0, room - fixed);
					columnWidth = Math.min(columnWidth, Math.max(0, textRight - cx));
					placed.add(new Placed(column, l, cx, columnWidth));
					cx += columnWidth + Theme.GAP;
				}
			}
			int buttonsTop = below ? 1 + lines.size() * LINE_STEP + 1 : -1;
			return new Layout(textLeft, textRight, List.copyOf(buttonX), buttonsTop, List.copyOf(placed), lines.size(),
					List.copyOf(hidden), fits);
		}

		private boolean allFit(List<List<Integer>> lines, int room) {
			for (List<Integer> line : lines) {
				if (!fits(line, room)) {
					return false;
				}
			}
			return true;
		}

		/**
		 * Whether a line's columns fit in {@code room} px: its rest column keeps {@value #REST_MIN_WIDTH} px (shrinking
		 * columns giving way down to {@value #SHRINK_MIN_WIDTH} px), and its fixed columns get their whole width.
		 */
		private boolean fits(List<Integer> line, int room) {
			int fixed = 0;
			int giveWay = 0;
			boolean hasRest = false;
			for (int i : line) {
				Column column = columns.get(i);
				// Every column takes its width and a gap, as placed: the rest column's width is what is left after that.
				fixed += column.width() + Theme.GAP;
				hasRest |= column.width() == 0;
				if (column.shrinks()) {
					giveWay += Math.max(0, column.width() - SHRINK_MIN_WIDTH);
				}
			}
			return hasRest ? room - fixed + giveWay >= REST_MIN_WIDTH : fixed - Theme.GAP <= room;
		}

		/** Where in {@code line} the column to move to the next line is: its last one that is not its rest column. */
		private int movable(List<Integer> line) {
			if (line.size() < 2) {
				return -1;
			}
			for (int k = line.size() - 1; k >= 0; k--) {
				if (columns.get(line.get(k)).width() > 0) {
					return k;
				}
			}
			return -1;
		}

		/**
		 * A row's parts as {@link #layout} places them.
		 *
		 * @param buttonX    the left edge of each button, in {@link #buttons()} order
		 * @param buttonsTop how far below the top of the row's content the buttons go when they are on a line of their
		 *                   own under the text; -1 when they are beside it, centred
		 * @param columns    each shown text column with its line, where it starts and the width it gets, line 0 first
		 * @param lines      how many text lines the row has
		 * @param hidden     the optional columns hidden because the row does not fit
		 * @param fits       every line keeps its rest column {@value #REST_MIN_WIDTH} px and its fixed columns whole
		 */
		public record Layout(int textLeft, int textRight, List<Integer> buttonX, int buttonsTop, List<Placed> columns, int lines,
				List<Column> hidden, boolean fits) {
			/** Whether the buttons are on a line of their own under the text. */
			public boolean buttonsBelow() {
				return buttonsTop >= 0;
			}
		}

		/** A text column as placed: its line, its left edge and the width it gets (less than it asks for in a narrow row). */
		public record Placed(Column column, int line, int x, int width) {
		}

		/** The list this row is in (set when the row is added), for the drawn-rows counter. */
		private SculptList owner;
	}

	/** Invisible, focusable stand-in for a read-only row: shows the focus ring and narrates the row. */
	private static final class RowFocus extends AbstractWidget {
		private final Row row;

		RowFocus(Row row) {
			super(0, 0, 0, 0, Component.empty());
			this.row = row;
		}

		@Override
		public Component getMessage() {
			return row.narrationText();
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
			if (isFocused()) {
				Draw.outline(g, getX(), getY(), getWidth(), getHeight(), Theme.FOCUS);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE, row.narrationText());
		}
	}

	@Override
	protected int addEntry(Row row) {
		row.owner = this;
		if (children().isEmpty()) {
			heightsWidth = contentWidth();
		}
		int height = row.heightFor(heightsWidth);
		int index = super.addEntry(row, height);
		rowsHeight += height;
		rowsCounted++;
		return index;
	}

	@Override
	public void replaceEntries(java.util.Collection<Row> rows) {
		for (Row row : rows) {
			row.owner = this;
		}
		super.replaceEntries(rows);
	}
}
