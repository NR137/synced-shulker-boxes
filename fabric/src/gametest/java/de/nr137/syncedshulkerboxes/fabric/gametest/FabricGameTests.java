package de.nr137.syncedshulkerboxes.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import de.nr137.syncedshulkerboxes.gametest.GameTests;

/** Registers the shared {@link GameTests} with Fabric's game test API, one method per test. */
public class FabricGameTests {
	private static final int MAX_TICKS = 200;

	@GameTest(maxTicks = MAX_TICKS)
	public void modIsRunning(GameTestHelper helper) {
		GameTests.all().get("mod_is_running").accept(helper);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void smithingMakesASyncedBox(GameTestHelper helper) {
		GameTests.all().get("smithing_makes_a_synced_box").accept(helper);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void smithingRefusesFullOrSyncedBoxes(GameTestHelper helper) {
		GameTests.all().get("smithing_refuses_full_or_synced_boxes").accept(helper);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void anvilLinksAndJoins(GameTestHelper helper) {
		GameTests.all().get("anvil_links_and_joins").accept(helper);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void membersShareContentsAndTheAnchorHoldsTheCopy(GameTestHelper helper) {
		GameTests.all().get("members_share_contents_and_the_anchor_holds_the_copy").accept(helper);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void noAutomationAndComparatorsReadTheGroup(GameTestHelper helper) {
		GameTests.all().get("no_automation_and_comparators_read_the_group").accept(helper);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void destroyedMembers(GameTestHelper helper) {
		GameTests.all().get("destroyed_members").accept(helper);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aMinedMemberKeepsItsLink(GameTestHelper helper) {
		GameTests.all().get("a_mined_member_keeps_its_link").accept(helper);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void clientsSeeTheContentsButCreativeCannotSetThem(GameTestHelper helper) {
		GameTests.all().get("clients_see_the_contents_but_creative_cannot_set_them").accept(helper);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void packetsCarryGlintNameAndContents(GameTestHelper helper) {
		GameTests.all().get("packets_carry_glint_name_and_contents").accept(helper);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void realHoppersNeitherTakeNorAdd(GameTestHelper helper) {
		GameTests.all().get("real_hoppers_neither_take_nor_add").accept(helper);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void anvilNeverDuplicates(GameTestHelper helper) {
		GameTests.all().get("anvil_never_duplicates").accept(helper);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void lostMembersLeaveAndTheLastTakesTheItems(GameTestHelper helper) {
		GameTests.all().get("lost_members_leave_and_the_last_takes_the_items").accept(helper);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void hopperMinecartsAndDroppersNeither(GameTestHelper helper) {
		GameTests.all().get("hopper_minecarts_and_droppers_neither").accept(helper);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void groupsAndPlayerChoicesSurviveSaving(GameTestHelper helper) {
		GameTests.all().get("groups_and_player_choices_survive_saving").accept(helper);
	}
}
