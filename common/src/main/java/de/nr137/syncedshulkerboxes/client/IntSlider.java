package de.nr137.syncedshulkerboxes.client;

import java.util.function.IntConsumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A slider for a whole number between min and max (the settings screen's world options). */
final class IntSlider extends AbstractSliderButton {
	private final String label;
	private final int min;
	private final int max;
	private final IntConsumer onChange;

	IntSlider(int x, int y, int width, String label, int min, int max, int value, IntConsumer onChange) {
		super(x, y, width, 20, Component.empty(), (double) (value - min) / (max - min));
		this.label = label;
		this.min = min;
		this.max = max;
		this.onChange = onChange;
		updateMessage();
	}

	private int current() {
		return min + (int) Math.round(value * (max - min));
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.literal(label + ": " + current()));
	}

	@Override
	protected void applyValue() {
		onChange.accept(current());
	}
}
