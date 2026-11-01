package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.EntityIota
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.lib.HexItems
import at.petrak.hexcasting.common.lib.hex.HexActions
import at.petrak.hexcasting.xplat.IXplatAbstractions
import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.advancement.MirabiliaAdvancements
import com.mirabiliahexica.ambient.AmbientMedia
import com.mirabiliahexica.config.Features
import com.mirabiliahexica.dream.Dream
import com.mirabiliahexica.dream.DreamDanger
import com.mirabiliahexica.dream.DreamPull
import com.mirabiliahexica.dream.DreamTicker
import com.mirabiliahexica.dream.DreamVisits
import com.mirabiliahexica.dream.Possession
import com.mirabiliahexica.entity.GlassMan
import com.mirabiliahexica.imbue.Imbuement
import com.mirabiliahexica.lens.LensImbuement
import com.mirabiliahexica.machines.MediaDischarger
import com.mirabiliahexica.registry.MirabiliaActions
import com.mirabiliahexica.registry.MirabiliaBlocks
import com.mirabiliahexica.registry.MirabiliaEnchantments
import com.mirabiliahexica.registry.MirabiliaEntities
import com.mirabiliahexica.registry.MirabiliaItems
import com.mirabiliahexica.registry.RegistrarEntry
import com.mirabiliahexica.wards.ArmorWard
import com.mirabiliahexica.wards.Wards
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.advancements.CriteriaTriggers
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.inventory.MerchantMenu
import net.minecraft.world.item.EnchantedBookItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.EnchantmentInstance
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.GameType
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3

/** What the other tests didn't reach: advancements, feature switches, trading, notes in the overworld... */
class AuditTests {
    // region advancements

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun placingAndHoldingEarnTheirAdvancements(helper: GameTestHelper) {
        val player = player(helper)
        // Placing a Media Discharger and a Silence Stone (the placed_block criteria)
        for ((rel, block, name) in listOf(Triple(BlockPos(1, 2, 1), MirabiliaBlocks.MEDIA_DISCHARGER, "lightning_rod"),
                                           Triple(BlockPos(3, 2, 1), MirabiliaBlocks.SILENCE_STONE, "silence"))) {
            helper.setBlock(rel, block.value)
            CriteriaTriggers.PLACED_BLOCK.trigger(player, helper.absolutePos(rel), ItemStack(block.value.asItem()))
            check(MirabiliaAdvancements.has(player, name)) { "placing ${block.id} didn't earn $name" }
        }
        // Holding a Greater Lens, and a lens with Deepsight (inventory_changed)
        player.inventory.setItem(0, ItemStack(MirabiliaItems.GREATER_LENS.value))
        player.inventoryMenu.broadcastChanges()
        check(MirabiliaAdvancements.has(player, "wider_view")) { "no Wider View holding a Greater Lens" }
        check(MirabiliaAdvancements.has(player, "root")) { "holding our item didn't open the tab" }
        val deep = ItemStack(MirabiliaItems.GREATER_LENS.value).apply { enchant(MirabiliaEnchantments.DEEPSIGHT.value, 1) }
        player.inventory.setItem(1, deep)
        player.inventoryMenu.broadcastChanges()
        check(MirabiliaAdvancements.has(player, "see_through")) { "no See Through holding a Deepsight lens" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun imbuingEarnsInscriptionAndWellArmed(helper: GameTestHelper) {
        val player = player(helper)
        player.setGameMode(GameType.CREATIVE)
        GreatSpells.enlighten(player)
        for ((item, entry, name) in listOf(Triple(HexItems.SCRYING_LENS, MirabiliaActions.IMBUE_LENS, "inscription"),
                                            Triple(Items.IRON_HELMET, MirabiliaActions.IMBUE_ARMOR, "well_armed"))) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack(item))
            val at = helper.absoluteVec(Vec3(3.5, 2.0, 3.5))
            val stack = net.minecraft.world.entity.item.ItemEntity(helper.level, at.x, at.y, at.z, ItemStack(HexItems.AMETHYST_DUST, 8))
            stack.setNeverPickUp()
            helper.level.addFreshEntity(stack)
            val hex = ListIota(listOf(PatternIota(HexActions.GET_CASTER.prototype)))
            val vm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
            vm.image = vm.image.copy(stack = listOf(EntityIota(stack), hex))
            vm.queueExecuteAndWrapIota(GreatSpells.patternFor(player, entry), helper.level)
            check(Imbuement.isImbued(player.offhandItem)) { "${entry.id} didn't imbue" }
            check(MirabiliaAdvancements.has(player, name)) { "${entry.id} didn't earn $name" }
        }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mirabilia_dream", timeoutTicks = 200)
    fun theDreamsHardWaysOutEarnTheirAdvancements(helper: GameTestHelper) {
        val player = player(helper)
        player.setGameMode(GameType.SURVIVAL)
        // Thrown out by the cold
        Dream.enter(player, null)
        player.hasChangedDimension()
        helper.runAfterDelay(80) {
            DreamVisits.get(player.server)[player.uuid]!!.cold = DreamTicker.MAX_COLD
            DreamTicker.tick(player.server)
            check(!Dream.isDreaming(player) && MirabiliaAdvancements.has(player, "cold_welcome")) { "no Cold Welcome after the cold" }
            // Coming back wrong (the effects_changed criterion)
            Dream.cameBackWrong(player)
            check(MirabiliaAdvancements.has(player, "came_back_wrong")) { "no Came Back Wrong with the effect" }
            helper.succeed()
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun buyingFromAPossessedVillagerWorksAndEarnsADeal(helper: GameTestHelper) {
        val player = player(helper)
        val villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, Vec3(3.5, 2.0, 1.5))
        IXplatAbstractions.INSTANCE.setBrainsweepAddlData(villager)
        villager.addTag(Possession.TAG)
        Possession.openTrades(player, villager)
        val menu = player.containerMenu as? MerchantMenu ?: throw GameTestAssertException("no trading screen")
        val offer = menu.offers.first()
        // Pay, take the Focus
        menu.slots[0].set(offer.costA.copy())
        menu.slotsChanged(menu.slots[0].container)
        val result = menu.slots[2].item.copy()
        check(result.`is`(HexItems.FOCUS)) { "the trade doesn't give a Focus: $result" }
        menu.slots[2].onTake(player, result)
        check(MirabiliaAdvancements.has(player, "a_deal")) { "no A Deal after buying" }
        player.closeContainer()
        // That ware is sold for the day
        check(Possession.offers(villager).first().isOutOfStock) { "the bought Focus is still for sale today" }
        // Five villagers taken through one player: Glass Village
        repeat(5) { MirabiliaAdvancements.possessed(player) }
        check(MirabiliaAdvancements.has(player, "glass_village")) { "no Glass Village after five" }
        helper.succeed()
    }

    // endregion
    // region the dream

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun carriedMediaBringsDangerSooner(helper: GameTestHelper) {
        val player = player(helper)
        check(DreamDanger.factorFor(0) == 1f) { "no danger carrying nothing" }
        check(DreamDanger.factorFor(DreamDanger.DUST_PER_STEP * MediaConstants.DUST_UNIT.toLong()) == 2f) { "one step of media isn't twice the danger" }
        check(DreamDanger.factorFor(Long.MAX_VALUE / 2) == DreamDanger.MAX_FACTOR) { "danger isn't capped" }
        // 40 Charged Amethyst is 400 dust
        player.inventory.setItem(0, ItemStack(HexItems.CHARGED_AMETHYST, 40))
        check(DreamDanger.carried(player) == 400 * MediaConstants.DUST_UNIT) { "carried media counted as ${DreamDanger.carried(player)}" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun lostDreamersCantBeCured(helper: GameTestHelper) {
        val player = player(helper)
        val dreamer = MirabiliaEntities.LOST_DREAMER.value.spawn(helper.level, helper.absolutePos(BlockPos(2, 2, 2)), MobSpawnType.COMMAND)!!
        dreamer.addEffect(MobEffectInstance(MobEffects.WEAKNESS, 600))
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.GOLDEN_APPLE))
        dreamer.mobInteract(player, InteractionHand.MAIN_HAND)
        check(!dreamer.isConverting) { "a Lost Dreamer began to be cured" }
        check(player.mainHandItem.count == 1) { "the golden apple was used up" }
        dreamer.discard()
        helper.succeed()
    }

    // endregion
    // region feature switches (they change global state, so in their own batch)

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = SWITCHES)
    fun switchedOffFeaturesStopWorking(helper: GameTestHelper) {
        val player = player(helper)
        fun cast(entry: RegistrarEntry<ActionRegistryEntry>) = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
            .queueExecuteAndWrapIota(PatternIota(entry.value.prototype), helper.level).resolutionType

        // Warding stones
        helper.setBlock(BlockPos(4, 2, 4), MirabiliaBlocks.WARDING_STONE.value)
        val near = helper.absoluteVec(Vec3(4.5, 3.0, 5.5))
        val env = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND)).env
        // (the stone spares whoever placed it; nobody did here)
        check(Wards.refuses(env, near)) { "a Warding Stone didn't guard" }
        switched({ Features.wardingStones = false }, { Features.wardingStones = true }) {
            check(!Wards.refuses(env, near)) { "a Warding Stone guarded while switched off" }
        }
        // (gone again, so it can't guard a neighbouring test)
        helper.setBlock(BlockPos(4, 2, 4), net.minecraft.world.level.block.Blocks.AIR)
        // Ward armor
        val wearer = helper.makeMockServerPlayerInLevel()
        wearer.setItemSlot(EquipmentSlot.CHEST, ItemStack(Items.IRON_CHESTPLATE).also(ArmorWard::ward))
        check(ArmorWard.protects(wearer)) { "warded armor didn't hide" }
        switched({ Features.wardArmor = false }, { Features.wardArmor = true }) {
            check(!ArmorWard.protects(wearer)) { "warded armor hid while switched off" }
        }
        // Chat and the mind palace
        switched({ Features.chatReading = false; Features.mindPalace = false }, { Features.chatReading = true; Features.mindPalace = true }) {
            check(cast(MirabiliaActions.PALACE_SIZE!!) == ResolvedPatternType.INVALID) { "Palace Reflection worked switched off" }
        }
        check(cast(MirabiliaActions.PALACE_SIZE!!) == ResolvedPatternType.EVALUATED) { "Palace Reflection failed switched on" }
        // Lens imbuing: a worn lens shows nothing
        val lens = ItemStack(MirabiliaItems.GREATER_LENS.value)
        Imbuement.imbue(lens, listOf(PatternIota(HexActions.GET_CASTER.prototype)), 100 * MediaConstants.DUST_UNIT)
        player.setItemSlot(EquipmentSlot.HEAD, lens)
        switched({ Features.imbueLens = false }, { Features.imbueLens = true }) {
            val before = Imbuement.media(lens)
            check(LensImbuement.run(player).isEmpty() && Imbuement.media(lens) == before) { "an imbued lens ran switched off" }
        }
        // The Media Discharger
        helper.setBlock(BlockPos(1, 2, 6), MirabiliaBlocks.MEDIA_DISCHARGER.value)
        helper.setBlock(BlockPos(1, 3, 6), net.minecraft.world.level.block.Blocks.LIGHTNING_ROD)
        val discharger = helper.absolutePos(BlockPos(1, 2, 6))
        val chunk = ChunkPos(discharger)
        switched({ Features.mediaDischarger = false }, { Features.mediaDischarger = true }) {
            AmbientMedia.set(helper.level, chunk, 200 * MediaConstants.DUST_UNIT)
            MediaDischarger.second(helper.level, discharger)
            check(AmbientMedia.get(helper.level, chunk) == 200 * MediaConstants.DUST_UNIT) { "the Discharger drank switched off" }
        }
        AmbientMedia.set(helper.level, chunk, 0)
        helper.setBlock(BlockPos(1, 3, 6), net.minecraft.world.level.block.Blocks.AIR)
        helper.setBlock(BlockPos(1, 2, 6), net.minecraft.world.level.block.Blocks.AIR)
        // The book follows the switches
        // (Patchouli isn't on the test classpath, so through reflection)
        val patchouli = Class.forName("vazkii.patchouli.api.PatchouliAPI").getMethod("get").invoke(null)
        fun flag(name: String) = patchouli.javaClass.getMethod("getConfigFlag", String::class.java).invoke(patchouli, name) as Boolean
        check(flag("mirabilia:tanakos_world") && !flag("mirabilia:standing_spells")) {
            "the book's flags don't match the switches"
        }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = SWITCHES, timeoutTicks = 60)
    fun theGlassManLeavesWhenSwitchedOff(helper: GameTestHelper) {
        val glass = MirabiliaEntities.GLASS_MAN.value.spawn(helper.level, helper.absolutePos(BlockPos(2, 2, 2)), MobSpawnType.SPAWN_EGG) as GlassMan
        Features.glassMan = false
        helper.runAfterDelay(10) {
            Features.glassMan = true
            check(glass.isRemoved) { "a Glass Man stayed with the Glass Man switched off" }
            helper.succeed()
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = SWITCHES)
    fun withoutTheDreamTheNotesTurnUpInTheOverworld(helper: GameTestHelper) {
        fun dungeonNotes(): Int {
            val table = helper.level.server.lootData.getLootTable(ResourceLocation("minecraft", "chests/simple_dungeon"))
            return (1..200).sumOf {
                val params = LootParams.Builder(helper.level).withParameter(LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO)).create(LootContextParamSets.CHEST)
                table.getRandomItems(params).count { it.`is`(MirabiliaItems.MEDIA_SOURCES_NOTES.value) }
            }
        }
        check(dungeonNotes() == 0) { "the notes turned up in a dungeon while the dream is on" }
        switched({ Features.tanakosWorld = false }, { Features.tanakosWorld = true }) {
            val found = dungeonNotes()
            check(found in 10..60) { "with no dream, 200 dungeon chests held $found notes (expected about 30)" }
        }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = SWITCHES)
    fun withoutCarryingCreaturesStayBehind(helper: GameTestHelper) {
        val player = player(helper)
        val pig = helper.spawn(EntityType.PIG, BlockPos(3, 2, 1))
        val chunk = ChunkPos(player.blockPosition())
        switched({ Features.carryCreatures = false }, { Features.carryCreatures = true }) {
            AmbientMedia.set(helper.level, chunk, AmbientMedia.PULL)
            check(DreamPull.pull(player)) { "couldn't pull" }
            check(!pig.isRemoved && pig.level() == helper.level) { "a pig was carried into the dream with carrying off" }
            Dream.wake(player, Dream.Waking.OTHER)
        }
        AmbientMedia.set(helper.level, chunk, 0)
        pig.discard()
        helper.succeed()
    }

    // endregion

    private fun player(helper: GameTestHelper): ServerPlayer {
        val player = helper.makeMockServerPlayerInLevel()
        helper.absoluteVec(Vec3(1.5, 2.0, 1.5)).let { player.moveTo(it.x, it.y, it.z) }
        return player
    }

    private inline fun switched(off: () -> Unit, on: () -> Unit, body: () -> Unit) {
        off()
        try { body() } finally { on() }
    }

    private companion object {
        const val SWITCHES = "mirabilia_features"
    }

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
