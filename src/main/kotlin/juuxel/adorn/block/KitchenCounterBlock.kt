package juuxel.adorn.block

import juuxel.adorn.api.block.BlockVariant
import juuxel.adorn.block.property.FrontConnection
import net.fabricmc.fabric.api.block.FabricBlockSettings
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.item.ItemPlacementContext
import net.minecraft.state.StateFactory
import net.minecraft.state.property.EnumProperty
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.IWorld

open class KitchenCounterBlock : BaseKitchenCounterBlock {
    constructor() : super()
    constructor(variant: BlockVariant) : super(FabricBlockSettings.copyOf(variant.createSettings()).sounds(SOUND_GROUP).build())

    init {
        defaultState = defaultState.with(FRONT, FrontConnection.None)
    }

    override fun appendProperties(builder: StateFactory.Builder<Block, BlockState>) {
        super.appendProperties(builder)
        builder.add(FRONT)
    }

    override fun getPlacementState(context: ItemPlacementContext): BlockState {
        return getStateForNeighborUpdate(
            super.getPlacementState(context),
            null, null, context.world, context.blockPos, null
        )
    }

    override fun getStateForNeighborUpdate(
        state: BlockState, direction: Direction?, neighborState: BlockState?,
        world: IWorld, pos: BlockPos, neighborPos: BlockPos?
    ): BlockState {
        val facing = state[FACING]
        val frontState = world.getBlockState(pos.offset(facing))
        val frontConnection =
            if (frontState.block is BaseKitchenCounterBlock) {
                when (frontState[FACING]) {
                    facing.rotateYClockwise() -> FrontConnection.Left
                    facing.rotateYCounterclockwise() -> FrontConnection.Right
                    else -> FrontConnection.None
                }
            } else FrontConnection.None

        return state.with(FRONT, frontConnection)
    }

    companion object {
        val FRONT = EnumProperty.of("front", FrontConnection::class.java)
    }
}
