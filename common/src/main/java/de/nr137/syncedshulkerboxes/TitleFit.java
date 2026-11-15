package de.nr137.syncedshulkerboxes;

/**
 * Shortens a screen title so it fits: the server can't measure text on the client, but Minecraft's default font has
 * fixed widths per character, most 6 pixels (5 and a space), a few narrower. Characters it doesn't know count as 8,
 * so a guess errs on the short side.
 */
final class TitleFit {
	/** The room for the title in a shulker box's screen: 176 pixels wide, the title starts 8 in. */
	static final int SHULKER_SCREEN_WIDTH = 160;

	private TitleFit() {
	}

	/** The name, shortened with "…" if needed, so that name + suffix fits {@code maxWidth}. */
	static String fit(String name, String suffix, int maxWidth) {
		int room = maxWidth - width(suffix);
		if (width(name) <= room) {
			return name + suffix;
		}
		int ellipsis = width("…");
		StringBuilder shortened = new StringBuilder();
		int used = 0;
		for (int i = 0; i < name.length(); ) {
			int codePoint = name.codePointAt(i);
			int next = advance(codePoint);
			if (used + next + ellipsis > room) {
				break;
			}
			shortened.appendCodePoint(codePoint);
			used += next;
			i += Character.charCount(codePoint);
		}
		return shortened.toString().stripTrailing() + "…" + suffix;
	}

	static int width(String text) {
		return text.codePoints().map(TitleFit::advance).sum();
	}

	/** A character's advance in pixels in the default font (glyph width plus one pixel of spacing). */
	static int advance(int codePoint) {
		return switch (codePoint) {
			case 'i', '!', ',', '.', ':', ';', '|', '\'', '·' -> 2;
			case 'l', '`' -> 3;
			case 't', 'I', ' ', '[', ']', '(', ')', '{', '}', '*', '"' -> 4;
			case 'f', 'k', '<', '>' -> 5;
			case '@', '~', '…' -> 7;
			default -> codePoint < 128 ? 6 : 8;
		};
	}
}
