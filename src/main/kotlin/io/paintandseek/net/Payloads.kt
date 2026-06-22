package io.paintandseek.net

import io.paintandseek.PaintAndSeek
import io.paintandseek.skin.SkinImage
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID

/**
 * A rectangular region of changed skin pixels. Used for live, incremental
 * "paint stroke" syncing - only the dirty rect travels the wire.
 */
data class SkinRect(val x: Int, val y: Int, val w: Int, val h: Int, val pixels: IntArray) {
    fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(x)
        buf.writeVarInt(y)
        buf.writeVarInt(w)
        buf.writeVarInt(h)
        buf.writeByteArray(SkinImage.toBytes(pixels))
    }

    // data class generates equals/hashCode but IntArray uses identity; override for correctness.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkinRect) return false
        return x == other.x && y == other.y && w == other.w && h == other.h && pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = x
        result = 31 * result + y
        result = 31 * result + w
        result = 31 * result + h
        result = 31 * result + pixels.contentHashCode()
        return result
    }

    companion object {
        fun read(buf: FriendlyByteBuf): SkinRect {
            val x = buf.readVarInt()
            val y = buf.readVarInt()
            val w = buf.readVarInt()
            val h = buf.readVarInt()
            val pixels = SkinImage.fromBytes(buf.readByteArray())
            require(pixels.size == w * h) { "SkinRect pixel count ${pixels.size} != ${w * h}" }
            return SkinRect(x, y, w, h, pixels)
        }
    }
}

/** Client -> Server: "I painted this stroke." */
data class SubmitSkinPatch(val rect: SkinRect) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<SubmitSkinPatch>(PaintAndSeek.id("submit_skin_patch"))
        val CODEC: StreamCodec<FriendlyByteBuf, SubmitSkinPatch> = CustomPacketPayload.codec(
            { value, buf -> value.rect.write(buf) },
            { buf -> SubmitSkinPatch(SkinRect.read(buf)) },
        )
    }
}

/** Server -> Clients: "<uuid>'s skin changed in this region." */
data class SkinPatch(val uuid: UUID, val rect: SkinRect) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<SkinPatch>(PaintAndSeek.id("skin_patch"))
        val CODEC: StreamCodec<FriendlyByteBuf, SkinPatch> = CustomPacketPayload.codec(
            { value, buf ->
                buf.writeUUID(value.uuid)
                value.rect.write(buf)
            },
            { buf -> SkinPatch(buf.readUUID(), SkinRect.read(buf)) },
        )
    }
}

/** Client -> Server: full skin upload (on open / commit). */
data class SubmitSkinSnapshot(val pixels: IntArray) : CustomPacketPayload {
    override fun type() = TYPE

    override fun equals(other: Any?) =
        this === other || (other is SubmitSkinSnapshot && pixels.contentEquals(other.pixels))

    override fun hashCode() = pixels.contentHashCode()

    companion object {
        val TYPE = CustomPacketPayload.Type<SubmitSkinSnapshot>(PaintAndSeek.id("submit_skin_snapshot"))
        val CODEC: StreamCodec<FriendlyByteBuf, SubmitSkinSnapshot> = CustomPacketPayload.codec(
            { value, buf -> buf.writeByteArray(SkinImage.toBytes(value.pixels)) },
            { buf -> SubmitSkinSnapshot(SkinImage.fromBytes(buf.readByteArray())) },
        )
    }
}

/** Server -> Clients: full skin for a player (initial state / late joiners). */
data class SkinSnapshot(val uuid: UUID, val pixels: IntArray) : CustomPacketPayload {
    override fun type() = TYPE

    override fun equals(other: Any?) =
        this === other || (other is SkinSnapshot && uuid == other.uuid && pixels.contentEquals(other.pixels))

    override fun hashCode() = 31 * uuid.hashCode() + pixels.contentHashCode()

    companion object {
        val TYPE = CustomPacketPayload.Type<SkinSnapshot>(PaintAndSeek.id("skin_snapshot"))
        val CODEC: StreamCodec<FriendlyByteBuf, SkinSnapshot> = CustomPacketPayload.codec(
            { value, buf ->
                buf.writeUUID(value.uuid)
                buf.writeByteArray(SkinImage.toBytes(value.pixels))
            },
            { buf -> SkinSnapshot(buf.readUUID(), SkinImage.fromBytes(buf.readByteArray())) },
        )
    }
}

/** Server -> Clients: this player's painted skin was cleared (reset to vanilla). */
data class ClearSkin(val uuid: UUID) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ClearSkin>(PaintAndSeek.id("clear_skin"))
        val CODEC: StreamCodec<FriendlyByteBuf, ClearSkin> = CustomPacketPayload.codec(
            { value, buf -> buf.writeUUID(value.uuid) },
            { buf -> ClearSkin(buf.readUUID()) },
        )
    }
}

/** Client -> Server: "I selected this pose." (poseId = PaintPose ordinal) */
data class SubmitPose(val poseId: Int) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<SubmitPose>(PaintAndSeek.id("submit_pose"))
        val CODEC: StreamCodec<FriendlyByteBuf, SubmitPose> = CustomPacketPayload.codec(
            { value, buf -> buf.writeVarInt(value.poseId) },
            { buf -> SubmitPose(buf.readVarInt()) },
        )
    }
}

/** Server -> Clients: "<uuid> is now in this pose." */
data class PoseSync(val uuid: UUID, val poseId: Int) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<PoseSync>(PaintAndSeek.id("pose_sync"))
        val CODEC: StreamCodec<FriendlyByteBuf, PoseSync> = CustomPacketPayload.codec(
            { value, buf ->
                buf.writeUUID(value.uuid)
                buf.writeVarInt(value.poseId)
            },
            { buf -> PoseSync(buf.readUUID(), buf.readVarInt()) },
        )
    }
}
