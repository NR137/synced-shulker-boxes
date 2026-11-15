package de.nr137.syncedshulkerboxes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TitleFitTest {
	@Test
	void shortTitlesStayAsTheyAre() {
		assertEquals("Base · K7QF", TitleFit.fit("Base", " · K7QF", TitleFit.SHULKER_SCREEN_WIDTH));
		assertEquals("Synced Shulker Box · K7QF", TitleFit.fit("Synced Shulker Box", " · K7QF", TitleFit.SHULKER_SCREEN_WIDTH));
	}

	@Test
	void longTitlesAreShortenedAndKeepTheCode() {
		String title = TitleFit.fit("A very long name for my storage of everything", " · K7QF", TitleFit.SHULKER_SCREEN_WIDTH);
		assertTrue(title.endsWith("… · K7QF"), title);
		assertTrue(TitleFit.width(title) <= TitleFit.SHULKER_SCREEN_WIDTH, title);
	}
}
