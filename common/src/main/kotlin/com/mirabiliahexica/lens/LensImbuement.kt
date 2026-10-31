package com.mirabiliahexica.lens

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.EntityIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.IotaType
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes
import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.imbue.Imbuement
import com.mirabiliahexica.network.MirabiliaNetworking
import com.mirabiliahexica.network.MirabiliaNetworking.Label
import java.util.UUID
import net.minecraft.ChatFormatting
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import com.mirabiliahexica.config.Features
import com.mirabiliahexica.config.Tuning

/**
 * An imbued lens (Scrying or Greater) holds a hex. While it is worn (head, either hand, Trinkets/Curios slot), the
 * server runs that hex as the wearer every [PERIOD] ticks. Whatever list it leaves on top of the stack, as
 * `[position or entity, value]` pairs, becomes labels drawn over the wearer's screen at those places (a list
 * value gives one line per item; `LensLabels` on the client), until the next run replaces them. They're only
 * drawn over places and creatures the wearer can see, unless the lens has Deepsight ([LensVisibility]).
 *
 * Each run costs [UPKEEP] on top of whatever the hex spends, all from the lens's own store of media, made from the
 * amethyst it was imbued with (or its wellspring, see [com.mirabiliahexica.imbue.Imbuement]); never the wearer's.
 * If it can't pay, or the hex mishaps, the labels go away until a run succeeds again.
 */
object LensImbuement {
    val PERIOD get() = Tuning.Imbuing.lensPeriod
    /** A tenth of a dust per run, so 0.2 dust a second. */
    val UPKEEP get() = Tuning.Imbuing.lensUpkeep
    const val MAX_LABELS = 64
    const val MAX_LINES = 16

    /** Where an imbued item keeps its hex (lenses, armor and tools alike). */
    const val TAG_HEX = "${Mirabilia.MODID}:hex"

    /**
     * Last labels sent to each player (as serialized text: Components rebuilt each run don't compare equal),
     * and whether their lens sees through walls.
     */
    private val lastSent = HashMap<UUID, Pair<List<Pair<Label.Anchor, List<String>>>, Boolean>>()

    fun isImbued(stack: ItemStack) = stack.tag?.contains(TAG_HEX, Tag.TAG_COMPOUND.toInt()) == true

    /** Stores [hex] on the lens; an empty hex removes the imbuement. */
    fun imbue(stack: ItemStack, hex: List<Iota>) {
        if (hex.isEmpty()) stack.removeTagKey(TAG_HEX)
        else stack.orCreateTag.put(TAG_HEX, IotaType.serialize(ListIota(hex)))
    }

    /** How many iotas the stored hex has, without deserializing it (works on the client, for the tooltip). */
    fun hexSize(stack: ItemStack): Int =
        stack.tag?.getCompound(TAG_HEX)?.getList(HexIotaTypes.KEY_DATA, Tag.TAG_COMPOUND.toInt())?.size ?: 0

    private fun storedHex(stack: ItemStack, level: ServerLevel): List<Iota>? {
        val tag = stack.tag?.getCompound(TAG_HEX)?.takeIf { !it.isEmpty } ?: return null
        return (IotaType.deserialize(tag, level) as? ListIota)?.list?.toList()
    }

    fun tick(server: MinecraftServer) {
        if (server.tickCount % PERIOD != 0) return
        for (player in server.playerList.players) {
            val labels = run(player)
            val throughWalls = wornLens(player)?.let(LensVisibility::seesThroughWalls) ?: false
            val key = labels.map { label -> label.anchor to label.lines.map(Component.Serializer::toJson) } to throughWalls
            if (lastSent[player.uuid] == key) continue
            lastSent[player.uuid] = key
            MirabiliaNetworking.sendLensLabels(player, labels, throughWalls)
        }
        lastSent.keys.retainAll(server.playerList.players.map { it.uuid }.toSet())
    }

    /** The imbued lens that runs: head first, then hands, then bauble slots. */
    fun wornLens(player: ServerPlayer): ItemStack? = LensSight.lenses(player).firstOrNull(::isImbued)

    /** Runs the worn lens's hex once and returns the labels it produced (empty if none, unpaid or mishapped). */
    fun run(player: ServerPlayer): List<Label> {
        if (!Features.imbueLens) return emptyList()
        val lens = wornLens(player) ?: return emptyList()
        val hex = storedHex(lens, player.serverLevel()) ?: return emptyList()

        if (hex.isEmpty()) return emptyList()
        // Paid from the lens's own store, like any imbued item
        val (env, vm) = Imbuement.run(player, lens, InteractionHand.MAIN_HAND, UPKEEP) ?: return emptyList()
        if (env.mishapped) return emptyList()
        val top = vm.image.stack.lastOrNull()
        val labels = labelsFrom(top, env)
        if (labels.isEmpty()) explainNothingShown(player, top)
        return labels
    }

    /**
     * Tells the wearer (on the action bar) why nothing is drawn, when the hex ran fine but its result can't be shown.
     * Only when the reason changes, so it doesn't flicker every run.
     */
    private fun explainNothingShown(player: ServerPlayer, top: Iota?) {
        val pairs = (top as? ListIota)?.list?.toList()
        val message = when {
            top == null -> Component.translatable("mirabilia.lens.result.empty_stack")
            pairs == null -> Component.translatable("mirabilia.lens.result.not_a_list", top.display())
            pairs.isEmpty() -> null // an empty list is a legitimate "nothing to show right now"
            else -> Component.translatable("mirabilia.lens.result.no_pairs", pairs.first().display())
        }
        val key = message?.string
        if (lastExplained[player.uuid] == key) return
        lastExplained[player.uuid] = key
        if (message != null) player.displayClientMessage(message.withStyle(ChatFormatting.GOLD), true)
    }

    private val lastExplained = HashMap<UUID, String?>()

    /**
     * Reads `[position or entity, value]` pairs, skipping anything malformed or out of ambit. Hidden ones are
     * kept: whether they're drawn is decided while drawing ([LensVisibility]).
     */
    fun labelsFrom(result: Iota?, env: CastingEnvironment): List<Label> {
        val pairs = (result as? ListIota)?.list?.toList() ?: return emptyList()
        return pairs.mapNotNull { pair ->
            val parts = (pair as? ListIota)?.list?.toList()?.takeIf { it.size == 2 } ?: return@mapNotNull null
            val anchor = when (val where = parts[0]) {
                is Vec3Iota -> where.vec3.takeIf(env::isVecInAmbit)?.let { Label.Anchor.Fixed(it) }
                is EntityIota -> where.entity.takeIf(env::isEntityInRange)?.let { Label.Anchor.Following(it.id) }
                else -> null
            }
            anchor?.let { Label(it, linesOf(parts[1])) }
        }.filter { it.lines.isNotEmpty() }.take(MAX_LABELS)
    }

    /** A list value becomes one line per item (up to [MAX_LINES]); anything else is a single line. */
    private fun linesOf(value: Iota): List<Component> {
        val items = (value as? ListIota)?.list?.toList() ?: return listOf(value.display())
        return items.take(MAX_LINES).map { it.display() }
    }
}
