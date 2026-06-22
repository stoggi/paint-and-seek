package io.github.stoggi.paintandseek.item

import io.github.stoggi.paintandseek.PaintAndSeek
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item

/**
 * Items added by paintandseek.
 *
 * The paint brush is how a hider enters paint mode: right-clicking it flips the
 * view to front third-person and lets you paint your skin against the
 * environment. (The right-click handling lives client-side.)
 */
object PaintAndSeekItems {
    val PAINT_BRUSH_KEY: ResourceKey<Item> =
        ResourceKey.create(Registries.ITEM, PaintAndSeek.id("paint_brush"))

    lateinit var paintBrush: Item
        private set

    fun register() {
        paintBrush = Registry.register(
            BuiltInRegistries.ITEM,
            PAINT_BRUSH_KEY,
            Item(Item.Properties().setId(PAINT_BRUSH_KEY).stacksTo(1)),
        )
    }
}
