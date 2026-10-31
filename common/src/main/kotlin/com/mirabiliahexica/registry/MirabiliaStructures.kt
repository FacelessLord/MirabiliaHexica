package com.mirabiliahexica.registry

import com.mirabiliahexica.worldgen.ListeningPost
import com.mirabiliahexica.worldgen.ListeningPostPiece
import com.mirabiliahexica.worldgen.MarbleCastle
import com.mirabiliahexica.worldgen.MarbleCastlePiece
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.levelgen.structure.StructureType
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType

/** Kinds of structure built in code; where and how often they appear is in `data/mirabilia/worldgen`. */
object MirabiliaStructures : MirabiliaRegistrar<StructureType<*>>(Registries.STRUCTURE_TYPE, { BuiltInRegistries.STRUCTURE_TYPE }) {
    val MARBLE_CASTLE = register<StructureType<MarbleCastle>>("marble_castle") { StructureType { MarbleCastle.CODEC } }
    val LISTENING_POST = register<StructureType<ListeningPost>>("listening_post") { StructureType { ListeningPost.CODEC } }
}

/** How those structures' pieces are saved with the chunks they span. */
object MirabiliaStructurePieces : MirabiliaRegistrar<StructurePieceType>(Registries.STRUCTURE_PIECE, { BuiltInRegistries.STRUCTURE_PIECE }) {
    val MARBLE_CASTLE = register("marble_castle") { StructurePieceType { _, tag -> MarbleCastlePiece(tag) } }
    val LISTENING_POST = register("listening_post") { StructurePieceType { _, tag -> ListeningPostPiece(tag) } }
}
