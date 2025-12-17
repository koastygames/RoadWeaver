package net.shiroha233.roadweaver.features.path.decoration.text

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.entity.HangingSignBlockEntity

object SignTextService {
    @JvmStatic
    fun writeDistanceSign(level: WorldGenLevel, pos: BlockPos, text: String) {
        val l = level.level
        val sLevel = l as? ServerLevel ?: return
        sLevel.server.execute {
            val be = sLevel.getBlockEntity(pos)
            if (be is HangingSignBlockEntity) {
                var front = be.getText(true)
                front = front.setMessage(0, Component.translatable("gui.roadweaver.sign.next_location"))
                front = front.setMessage(1, Component.literal("$text m"))
                front = front.setMessage(2, Component.literal(""))
                front = front.setMessage(3, Component.literal(""))
                be.setText(front, true)

                var back = be.getText(false)
                back = back.setMessage(0, Component.literal("----------"))
                back = back.setMessage(1, Component.translatable("gui.roadweaver.sign.welcome"))
                back = back.setMessage(2, Component.translatable("gui.roadweaver.sign.traveller"))
                back = back.setMessage(3, Component.literal("----------"))
                be.setText(back, false)

                be.setChanged()
            }
        }
    }

    @JvmStatic
    fun writeSeaQuestionSign(level: WorldGenLevel, pos: BlockPos) {
        val l = level.level
        val sLevel = l as? ServerLevel ?: return
        sLevel.server.execute {
            val be = sLevel.getBlockEntity(pos)
            if (be is HangingSignBlockEntity) {
                var front = be.getText(true)
                front = front.setMessage(0, Component.translatable("gui.roadweaver.sign.sea_question.line1"))
                front = front.setMessage(1, Component.translatable("gui.roadweaver.sign.sea_question.line2"))
                front = front.setMessage(2, Component.literal(""))
                front = front.setMessage(3, Component.literal(""))
                be.setText(front, true)

                var back = be.getText(false)
                back = back.setMessage(0, Component.literal(""))
                back = back.setMessage(1, Component.literal(""))
                back = back.setMessage(2, Component.literal(""))
                back = back.setMessage(3, Component.literal(""))
                be.setText(back, false)

                be.setChanged()
            }
        }
    }
}
