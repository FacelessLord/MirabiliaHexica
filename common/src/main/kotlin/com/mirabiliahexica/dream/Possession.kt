package com.mirabiliahexica.dream

import at.petrak.hexcasting.api.casting.iota.IotaType
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.common.lib.HexItems
import at.petrak.hexcasting.xplat.IXplatAbstractions
import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.entity.GlassMan
import com.mirabiliahexica.worldgen.DreamLibrary
import dev.architectury.event.EventResult
import dev.architectury.event.events.common.InteractionEvent
import dev.architectury.event.events.common.TickEvent
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.trading.Merchant
import net.minecraft.world.item.trading.MerchantOffer
import net.minecraft.world.item.trading.MerchantOffers
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID
import kotlin.random.Random
import com.mirabiliahexica.advancement.MirabiliaAdvancements
import com.mirabiliahexica.config.Features
import com.mirabiliahexica.config.Tuning

/**
 * Glass-possessed villagers. A dreamer who wakes with the Glass Man close behind brings it out with them: it moves
 * into the nearest *empty* villager (one whose mind was taken with Flay Mind) within [GlassManSpawner.FOLLOW_RANGE]
 * blocks of where they wake, if there is one. Possessed villagers stay so for good, never attack, shimmer faintly,
 * and trade: each day [WARES_MIN]–[WARES_MAX] Foci holding ready-made hexes ([DreamLibrary]: ours and Hex
 * Casting's cypher hexes), for Charged Amethyst, each sold once a day.
 */
object Possession {
    val WARES_MIN get() = Tuning.GlassMan.waresMin
    val WARES_MAX get() = Tuning.GlassMan.waresMax
    /** What a possessed villager asks for one of Hex Casting's cypher hexes, in Charged Amethyst. */
    val CYPHER_PRICE get() = Tuning.GlassMan.cypherPrice

    fun init() {
        InteractionEvent.INTERACT_ENTITY.register { player, entity, _ ->
            if (entity is Villager && player is ServerPlayer && Features.isOn("glass_man") && isPossessed(entity)) {
                openTrades(player, entity)
                EventResult.interruptTrue()
            } else {
                EventResult.pass()
            }
        }
        TickEvent.SERVER_POST.register(::shimmer)
    }

    /**
     * The mark a possessed villager carries in its own saved data, so it stays possessed through anything that
     * saves and re-creates it (being carried by a pickup mod, moved between worlds), whatever its new UUID.
     */
    const val TAG = "mirabilia.possessed"

    fun isPossessed(villager: Villager): Boolean {
        if (TAG in villager.tags) return true
        // Possessed before the mark existed: listed by UUID in the world's data; marked now
        val listed = villager.level().server?.let { Data.get(it).possessed.contains(villager.uuid) } == true
        if (listed) villager.addTag(TAG)
        return listed
    }

    /** Moves the Glass Man into the nearest empty villager near [player]. Returns it, or null if there was none. */
    fun possessNear(player: ServerPlayer): Villager? {
        val level = player.serverLevel()
        val data = Data.get(player.server)
        val villager = level.getEntitiesOfClass(Villager::class.java, player.boundingBox.inflate(GlassManSpawner.FOLLOW_RANGE)) {
            IXplatAbstractions.INSTANCE.isBrainswept(it) && !isPossessed(it)
        }.minByOrNull { it.distanceToSqr(player) } ?: return null
        villager.addTag(TAG)
        data.possessed += villager.uuid
        data.setDirty()
        Mirabilia.LOGGER.info("The Glass Man followed {} out into the villager at {}", player.gameProfile.name, villager.blockPosition().toShortString())
        GlassMan.shimmer(level, villager.x, villager.y, villager.z)
        player.displayClientMessage(Component.translatable("mirabilia.glass_man.possessed"), true)
        MirabiliaAdvancements.possessed(player)
        return villager
    }

    /** Today's wares of [villager]: the same all day, different the next. Sold ones are used up. */
    fun offers(villager: Villager): MerchantOffers {
        val level = villager.level() as ServerLevel
        val day = Math.floorDiv(level.dayTime, 24000L)
        val random = Random(villager.uuid.leastSignificantBits xor (day * 0x9E3779B97F4AL))
        val ours = DreamLibrary.ENTRIES.map { Ware(Component.translatable("mirabilia.hex.${it.name}"), it.price) { IotaType.serialize(DreamLibrary.asList(it)) } }
        val theirs = DreamLibrary.cypherHexes(random.nextLong(), 4).map { Ware(Component.translatable(it.name), CYPHER_PRICE) { it.iotaTag } }
        val wares = (ours + theirs).shuffled(random).take(WARES_MIN + random.nextInt(WARES_MAX - WARES_MIN + 1))
        val sold = Data.get(level.server).soldToday(villager.uuid, day)
        return MerchantOffers().apply {
            wares.forEachIndexed { i, ware ->
                val focus = ItemStack(HexItems.FOCUS)
                (IotaType.deserialize(ware.hex(), level) as? ListIota)?.let { HexItems.FOCUS.writeDatum(focus, it) }
                focus.setHoverName(ware.name)
                add(MerchantOffer(ItemStack(HexItems.CHARGED_AMETHYST, ware.price), ItemStack.EMPTY, focus, if (i in sold) 1 else 0, 1, 0, 0f))
            }
        }
    }

    private class Ware(val name: Component, val price: Int, val hex: () -> CompoundTag)

    /** Opens the possessed [villager]'s wares to [player]. */
    fun openTrades(player: ServerPlayer, villager: Villager) {
        val level = villager.level() as ServerLevel
        val day = Math.floorDiv(level.dayTime, 24000L)
        val offers = offers(villager)
        val merchant = GlassMerchant(offers) { index -> Data.get(level.server).markSold(villager.uuid, day, index) }
        // The trading screen stays open only while its merchant says this player is trading, as a villager does
        merchant.setTradingPlayer(player)
        merchant.openTradingScreen(player, villager.displayName, 0)
    }

    /** A faint glint over possessed villagers near players. */
    private fun shimmer(server: MinecraftServer) {
        if (server.tickCount % 40 != 0 || !Features.isOn("glass_man")) return
        val data = Data.get(server)
        for (player in server.playerList.players) {
            for (villager in player.serverLevel().getEntitiesOfClass(Villager::class.java, player.boundingBox.inflate(32.0)) { isPossessed(it) }) {
                player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, villager.x, villager.y + 1.2, villager.z, 2, 0.3, 0.5, 0.3, 0.01)
            }
        }
    }

    /** The trading side of a possessed villager: a fixed set of Foci for the day. */
    private class GlassMerchant(private val offers: MerchantOffers, private val onSold: (Int) -> Unit) : Merchant {
        private var trader: Player? = null
        override fun setTradingPlayer(player: Player?) { trader = player }
        override fun getTradingPlayer() = trader
        override fun getOffers() = offers
        override fun overrideOffers(offers: MerchantOffers) {}
        override fun notifyTrade(offer: MerchantOffer) {
            offer.increaseUses()
            onSold(offers.indexOf(offer))
            (trader as? ServerPlayer)?.let { MirabiliaAdvancements.award(it, "a_deal") }
        }
        override fun notifyTradeUpdated(stack: ItemStack) {}
        override fun getVillagerXp() = 0
        override fun overrideXp(xp: Int) {}
        override fun showProgressBar() = false
        override fun getNotifyTradeSound(): SoundEvent = SoundEvents.AMETHYST_BLOCK_CHIME
        override fun isClientSide() = false
    }

    /** Which villagers are possessed, and what each has sold today. Saved with the overworld. */
    class Data : SavedData() {
        val possessed = mutableSetOf<UUID>()
        private val sold = HashMap<UUID, Pair<Long, MutableSet<Int>>>()

        fun soldToday(villager: UUID, day: Long): Set<Int> = sold[villager]?.takeIf { it.first == day }?.second.orEmpty()

        fun markSold(villager: UUID, day: Long, index: Int) {
            val today = sold[villager]?.takeIf { it.first == day } ?: (day to mutableSetOf<Int>()).also { sold[villager] = it }
            today.second += index
            setDirty()
        }

        override fun save(tag: CompoundTag): CompoundTag {
            tag.put("possessed", ListTag().apply { possessed.forEach { add(NbtUtils.createUUID(it)) } })
            tag.put("sold", ListTag().apply {
                sold.forEach { (villager, today) ->
                    add(CompoundTag().apply {
                        putUUID("villager", villager)
                        putLong("day", today.first)
                        putIntArray("sold", today.second.toIntArray())
                    })
                }
            })
            return tag
        }

        companion object {
            private const val DATA_NAME = "${Mirabilia.MODID}_possessed"

            fun get(server: MinecraftServer): Data = server.overworld().dataStorage.computeIfAbsent(::load, ::Data, DATA_NAME)

            private fun load(tag: CompoundTag) = Data().apply {
                for (entry in tag.getList("possessed", Tag.TAG_INT_ARRAY.toInt())) possessed += NbtUtils.loadUUID(entry)
                for (entry in tag.getList("sold", Tag.TAG_COMPOUND.toInt())) {
                    val c = entry as CompoundTag
                    sold[c.getUUID("villager")] = c.getLong("day") to c.getIntArray("sold").toMutableSet()
                }
            }
        }
    }
}
