package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.items.magic.ItemMediaHolder
import at.petrak.hexcasting.common.lib.HexItems
import at.petrak.hexcasting.xplat.IXplatAbstractions
import com.mirabiliahexica.casting.actions.spells.PhialHelper
import com.mirabiliahexica.dream.Dream
import com.mirabiliahexica.dream.DreamDanger
import com.mirabiliahexica.dream.DreamSpawner
import com.mirabiliahexica.dream.DreamTicker
import com.mirabiliahexica.dream.DreamVisits
import com.mirabiliahexica.dream.GlassManSpawner
import com.mirabiliahexica.dream.Possession
import com.mirabiliahexica.entity.GlassMan
import com.mirabiliahexica.registry.MirabiliaEffects
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import com.mirabiliahexica.advancement.MirabiliaAdvancements

/** The Glass Man and the villagers it moves into. Dreamers leave their plots, so these run with the dream tests. */
class GlassManTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun itCannotBeKilledButWillNotOutstayItsDreamer(helper: GameTestHelper) {
        val player = dreamer(helper)
        val glass = summonBeside(player)
        glass.hurt(player.damageSources().playerAttack(player), 1000f)
        check(glass.isAlive && glass.health == glass.maxHealth) { "the Glass Man was hurt" }
        // Only one per dreamer: it's found again rather than doubled
        check(GlassManSpawner.find(player) === glass) { "lost track of the Glass Man" }

        Dream.wake(player, Dream.Waking.PATTERN)
        check(!glass.isAlive) { "the Glass Man stayed in the dream without its dreamer" }

        // /kill still works on one
        val again = dreamer(helper)
        val other = summonBeside(again)
        other.kill()
        check(!other.isAlive) { "/kill didn't remove it" }
        Dream.wake(again, Dream.Waking.PATTERN)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun itHoldsDreamersInTheDream(helper: GameTestHelper) {
        val player = dreamer(helper)
        player.setGameMode(GameType.CREATIVE)
        summonBeside(player)
        player.isShiftKeyDown = true
        repeat(DreamTicker.STILL_TICKS + 20) { DreamTicker.tick(player.server) }
        check(Dream.isDreaming(player)) { "crouching still woke a dreamer the Glass Man was holding" }
        player.isShiftKeyDown = false
        Dream.wake(player, Dream.Waking.PATTERN)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 200)
    fun itsTouchTakesAndCanThrowYouOut(helper: GameTestHelper) {
        val player = dreamer(helper)
        player.setGameMode(GameType.SURVIVAL)
        // A real client confirms the dimension change; players can't be hurt until then, nor for a few seconds
        player.hasChangedDimension()
        val phial = ItemMediaHolder.withMedia(ItemStack(HexItems.BATTERY), 100 * DUST, 100 * DUST)
        player.inventory.setItem(0, phial) // (add() would move it, leaving this reference empty)
        // Past the few seconds of protection after the dimension change; summoned only then, so it can't get
        // a touch in on its own while we wait
        helper.runAfterDelay(80) {
            val glass = summonBeside(player)
            player.health = 20f
            val coldBefore = DreamVisits.get(player.server)[player.uuid]!!.cold
            glass.touch(player)
            check(PhialHelper.media(phial) == 90 * DUST) { "the touch took ${(100 * DUST - PhialHelper.media(phial)) / DUST} dust" }
            check(DreamVisits.get(player.server)[player.uuid]!!.cold >= coldBefore + GlassMan.TOUCH_COLD) { "the touch didn't chill" }
            check(player.health == 18f) { "the touch did ${20f - player.health} damage, expected 2" }

            // Weak: worn like a mask, and thrown out wrong
            player.health = 5f
            glass.touch(player)
            check(!Dream.isDreaming(player)) { "a weak dreamer wasn't thrown out" }
            check(player.hasEffect(MirabiliaEffects.CAME_BACK_WRONG.value)) { "thrown out without the curse" }
            check(player.isAlive) { "the mask killed them" }
            helper.succeed()
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun itFollowsTheWakerIntoAnEmptyVillager(helper: GameTestHelper) {
        // An empty villager (mind taken with Flay Mind) and an ordinary one, both by the bed
        val empty = helper.spawnWithNoFreeWill(EntityType.VILLAGER, Vec3(3.5, 2.0, 1.5))
        IXplatAbstractions.INSTANCE.setBrainsweepAddlData(empty)
        val ordinary = helper.spawnWithNoFreeWill(EntityType.VILLAGER, Vec3(1.5, 2.0, 3.5))

        val player = dreamer(helper)
        summonBeside(player)
        Dream.wake(player, Dream.Waking.PATTERN)
        check(Possession.isPossessed(empty)) { "the Glass Man didn't move into the empty villager" }
        check(!Possession.isPossessed(ordinary)) { "it took a villager with a mind of its own" }
        // Saved and re-created (as a pickup mod does), with a new UUID and without Hex's brainswept mark, it stays possessed
        val saved = net.minecraft.nbt.CompoundTag().also { empty.saveWithoutId(it) }
        saved.remove("UUID")
        val copy = EntityType.VILLAGER.create(helper.level)!!
        copy.load(saved)
        check(copy.uuid != empty.uuid && Possession.isPossessed(copy)) { "a re-created possessed villager lost its possession" }
        for (name in listOf("glass_dream", "someone_watching", "just_in_time", "act_of_will", "new_shell")) {
            check(MirabiliaAdvancements.has(player, name)) { "no $name advancement" }
        }

        // Its wares: two or three Foci of ready-made hexes, for 4 to 12 Charged Amethyst, the same all day
        val offers = Possession.offers(empty)
        check(offers.size in Possession.WARES_MIN..Possession.WARES_MAX) { "offered ${offers.size} wares" }
        for (offer in offers) {
            check(offer.costA.`is`(HexItems.CHARGED_AMETHYST) && offer.costA.count in 4..12) { "price ${offer.costA}" }
            val hex = HexItems.FOCUS.readIota(offer.result, helper.level) as? ListIota
            check(hex != null && hex.list.size() > 0) { "a Focus without a hex: ${offer.result}" }
            check(offer.result.hasCustomHoverName()) { "an unnamed Focus" }
        }
        check(Possession.offers(empty).map { it.result.hoverName.string } == offers.map { it.result.hoverName.string }) {
            "the wares changed within the day"
        }
        // Its trading screen opens and stays open (it closes itself unless the merchant knows who's trading)
        Possession.openTrades(player, empty)
        check(player.containerMenu is net.minecraft.world.inventory.MerchantMenu) { "no trading screen opened: ${player.containerMenu}" }
        check(player.containerMenu.stillValid(player)) { "the trading screen would close at once" }
        player.closeContainer()
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun itComesLaterRatherThanSooner(helper: GameTestHelper) {
        check(GlassManSpawner.chance(2 * 1200, false) == 0f) { "came during the grace minutes" }
        check(GlassManSpawner.chance(9 * 1200, false) in 0.05f..0.15f) { "halfway chance off" }
        check(GlassManSpawner.chance(30 * 1200, false) == GlassManSpawner.MAX_CHANCE) { "the chance didn't level off" }
        check(GlassManSpawner.chance(9 * 1200, true) > GlassManSpawner.chance(9 * 1200, false)) { "no likelier in a listening post" }

        // Carried media brings it sooner and likelier
        check(DreamDanger.factorFor(0) == 1f && DreamDanger.factorFor(400L * DUST) == 2f && DreamDanger.factorFor(100_000L * DUST) == 4f) {
            "danger factors off"
        }
        check(GlassManSpawner.chance(1 * 1200, false, 4f) > 0f) { "a laden dreamer still had the full grace" }
        check(GlassManSpawner.chance(9 * 1200, false, 2f) > GlassManSpawner.chance(9 * 1200, false)) { "media didn't raise the chance" }
        check(GlassManSpawner.chance(30 * 1200, false, 4f) == GlassManSpawner.CARRYING_MAX) { "the laden chance isn't capped" }
        check(DreamSpawner.chance(5 * 1200, 2f) > DreamSpawner.chance(5 * 1200)) { "media didn't bring Lost Dreamers faster" }
        check(DreamSpawner.cap(100 * 1200, 4f) == 9) { "the laden cap isn't 9" }

        // What a dreamer carries is counted: a Phial of 100 dust and 10 dust in amethyst dust
        val laden = dreamer(helper)
        laden.inventory.setItem(0, ItemMediaHolder.withMedia(ItemStack(HexItems.BATTERY), 100 * DUST, 100 * DUST))
        laden.inventory.setItem(1, ItemStack(HexItems.AMETHYST_DUST, 10))
        check(DreamDanger.carried(laden) == 110 * DUST) { "counted ${DreamDanger.carried(laden) / DUST} dust carried" }
        Dream.wake(laden, Dream.Waking.PATTERN)

        // The warning comes first; it appears only after
        val player = dreamer(helper)
        GlassManSpawner.announce(player, player.position().add(10.0, 0.0, 0.0))
        check(GlassManSpawner.find(player) == null) { "it came with no warning" }
        val visit = DreamVisits.get(player.server)[player.uuid]!!
        repeat(GlassManSpawner.WARNING + 1) { GlassManSpawner.tick(player, visit) }
        check(GlassManSpawner.find(player) != null) { "it never came after the warning" }
        Dream.wake(player, Dream.Waking.PATTERN)
        helper.succeed()
    }

    private fun dreamer(helper: GameTestHelper): ServerPlayer {
        val player = helper.makeMockServerPlayerInLevel()
        val pos = helper.absoluteVec(Vec3(1.5, 2.0, 1.5))
        player.moveTo(pos.x, pos.y, pos.z)
        Dream.enter(player, null)
        // Real players keep the chunks round them loaded and ticking; mock ones don't
        val centre = player.chunkPosition()
        for (dx in -2..2) for (dz in -2..2) player.serverLevel().setChunkForced(centre.x + dx, centre.z + dz, true)
        return player
    }

    private fun summonBeside(player: ServerPlayer): GlassMan =
        GlassManSpawner.summon(player, player.position().add(2.0, 0.0, 0.0)) ?: throw GameTestAssertException("couldn't summon it")

    private companion object {
        const val BATCH = "mirabilia_dream"
        const val DUST = MediaConstants.DUST_UNIT
    }

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
