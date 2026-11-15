package de.nr137.syncedshulkerboxes.client;

/**
 * Only on clients that have the mod installed: synced boxes get the enchantment glint, which Minecraft doesn't draw on
 * shulker boxes (MC-69683). Items carry the glint flag the server sends; placed boxes carry the mark, which the server
 * sends along with the block. Vanilla clients see neither and work all the same.
 *
 * <p>Rendering happens on the render thread only, so a plain field passes "draw with glint" from the place that knows
 * (the item or the placed box) to the shared drawing code (mixin.client.ShulkerBoxRendererMixin).
 */
public final class ClientGlint {
	private static boolean active;

	private ClientGlint() {
	}

	/** Whether the box being drawn gets the glint: it is synced, and this client hasn't turned the glint off. */
	public static boolean active() {
		return active && ClientSettings.glint();
	}

	public static void set(boolean glint) {
		active = glint;
	}

	/** Implemented by the mixin into the placed box's render state. */
	public interface Holder {
		boolean syncedShulkerBoxes$glint();

		void syncedShulkerBoxes$setGlint(boolean glint);
	}
}
