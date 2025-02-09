package juuxel.adorn.gui.controller

import io.github.cottonmc.cotton.gui.client.BackgroundPainter
import io.github.cottonmc.cotton.gui.widget.WGridPanel
import io.github.cottonmc.cotton.gui.widget.WItemSlot
import io.github.cottonmc.cotton.gui.widget.WLabel
import juuxel.adorn.block.entity.TradingStation
import juuxel.adorn.gui.painter.Painters
import juuxel.adorn.gui.widget.WCenteredLabel
import juuxel.adorn.gui.widget.WDisplayOnlySlot
import juuxel.adorn.lib.AdornNetworking
import juuxel.adorn.trading.Trade
import juuxel.adorn.trading.TradeInventory
import juuxel.adorn.util.Colors
import juuxel.adorn.util.color
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.network.ServerSidePacketRegistry
import net.fabricmc.fabric.api.server.PlayerStream
import net.minecraft.container.BlockContext
import net.minecraft.container.SlotActionType
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.text.TranslatableText
import org.apache.logging.log4j.LogManager

class TradingStationController(
    syncId: Int,
    playerInv: PlayerInventory,
    private val context: BlockContext,
    private val forOwner: Boolean
) : BaseAdornController(
    syncId,
    playerInv,
    context,
    getStorage(context),
    getBlockPropertyDelegate(context)
) {
    private val slots: List<WItemSlot>

    init {
        (rootPanel as WGridPanel).apply {
            add(WLabel(TranslatableText(getBlock(context).translationKey), Colors.WHITE), 0, 0)

            val tradeInv = getTrade(context).createInventory()

            val mutableSlots = ArrayList<WItemSlot>()
            fun WItemSlot.addToSlots() = apply { mutableSlots += this }

            add(WDisplayOnlySlot(tradeInv, 0).addToSlots(), 1, 2)
            add(WDisplayOnlySlot(tradeInv, 1).addToSlots(), 1, 4)

            add(WCenteredLabel(TranslatableText("block.adorn.trading_station.selling"), Colors.WHITE), 1, 1)
            add(WCenteredLabel(TranslatableText("block.adorn.trading_station.price"), Colors.WHITE), 1, 3)

            add(WItemSlot.of(blockInventory, 0, 4, 3).addToSlots(), 3, 2)

            add(playerInvPanel, 0, 6)
            validate(this@TradingStationController)

            slots = mutableSlots
        }
    }

    override fun onSlotClick(slotNumber: Int, button: Int, action: SlotActionType, player: PlayerEntity): ItemStack {
        val slot = slotList.getOrNull(slotNumber)
        val cursorStack = player.inventory.cursorStack

        return if (forOwner && slot?.inventory is TradeInventory) {
            when (action) {
                SlotActionType.PICKUP -> {
                    slot.stack = cursorStack.copy()
                    slot.markDirty()

                    if (!world.isClient) {
                        context.run { world, pos ->
                            PlayerStream.watching(world, pos).forEach {
                                ServerSidePacketRegistry.INSTANCE.sendToPlayer(
                                    it,
                                    AdornNetworking.createTradeSyncPacket(pos, getTrade(context))
                                )
                            }
                        }
                    }

                    cursorStack
                }

                else -> cursorStack
            }
        } else if (forOwner || (slot?.inventory is PlayerInventory && action != SlotActionType.QUICK_MOVE)) {
            super.onSlotClick(slotNumber, button, action, player)
        } else cursorStack
    }

    @Environment(EnvType.CLIENT)
    override fun addPainters() {
        super.addPainters()
        rootPanel.setBackgroundPainter(BackgroundPainter.createColorful(color(0x359668)))
        slots.forEach { it.setBackgroundPainter(Painters.LIBGUI_STYLE_SLOT) }
    }

    override fun getTitleColor() = Colors.WHITE

    companion object {
        private val LOGGER = LogManager.getLogger()

        /**
         * Gets the [juuxel.adorn.block.entity.TradingStationBlockEntity] at the [context]'s location.
         * If it's not present, creates an empty trading station using [TradingStation.createEmpty].
         */
        private fun getTradingStation(context: BlockContext) =
            getBlockEntity(context) as? TradingStation ?: run {
                LOGGER.warn("[Adorn] Trading station not found, creating fake one")
                TradingStation.createEmpty()
            }

        /**
         * Gets the [TradingStation.storage] of the trading station at the [context]'s location.
         * Uses [getTradingStation] for finding a trading station.
         */
        private fun getStorage(context: BlockContext): Inventory = getTradingStation(context).storage

        /**
         * Gets the [TradingStation.trade] of the trading station at the [context]'s location.
         * Uses [getTradingStation] for finding a trading station.
         */
        private fun getTrade(context: BlockContext): Trade = getTradingStation(context).trade
    }
}
