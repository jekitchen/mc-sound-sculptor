package soundsculptor.mc;

/**
 * Whether the mouse pointer is outside the game window (PRD FR-1.3). The window gets no pointer moves once the pointer
 * has left it, so a widget it last pointed at still counts as hovered; the Sounds list must not stay held because of
 * that. Told by the window's own enter and leave events ({@code WindowMixin}) and by pointer moves, which only come
 * from inside the window ({@code MouseHandlerMixin}), on the render thread.
 */
public final class PointerPresence {
	private static boolean outside;

	private PointerPresence() {
	}

	/** Whether the pointer left the window and has not come back. */
	public static boolean outside() {
		return outside;
	}

	/** The pointer left the window. */
	public static void left() {
		outside = true;
	}

	/** The pointer came back into the window. */
	public static void entered() {
		outside = false;
	}
}
