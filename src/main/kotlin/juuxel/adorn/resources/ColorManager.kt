package juuxel.adorn.resources

import blue.endless.jankson.Jankson
import blue.endless.jankson.JsonElement
import blue.endless.jankson.JsonObject
import blue.endless.jankson.JsonPrimitive
import io.github.cottonmc.cotton.gui.widget.WLabel
import juuxel.adorn.Adorn
import juuxel.adorn.util.color
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
import net.minecraft.resource.ResourceManager
import net.minecraft.resource.SinglePreparationResourceReloadListener
import net.minecraft.util.Identifier
import net.minecraft.util.profiler.Profiler
import org.apache.logging.log4j.LogManager

object ColorManager : SinglePreparationResourceReloadListener<Map<Identifier, List<JsonObject>>>(), IdentifiableResourceReloadListener {
    private val LOGGER = LogManager.getLogger()
    private val JANKSON = Jankson.builder().build()
    private val ID = Adorn.id("color_manager")
    private const val PREFIX = "color_palettes"
    private const val SUFFIX_LENGTH = ".json5".length
    private val COLOR_REGEX = Regex("#(?:[0-9A-Fa-f]{2})?[0-9A-Fa-f]{6}")
    private val map: MutableMap<Identifier, ColorPalette> = HashMap()

    override fun getFabricId() = ID

    override fun prepare(manager: ResourceManager, profiler: Profiler): Map<Identifier, List<JsonObject>> {
        val ids = manager.findResources(PREFIX) { it.endsWith(".json5") }
        return ids.associateWith { id ->
            manager.getAllResources(id).map { resource ->
                resource.inputStream.use { input ->
                    JANKSON.load(input)
                }
            }
        }
    }

    override fun apply(map: Map<Identifier, List<JsonObject>>, manager: ResourceManager, profiler: Profiler) {
        for ((id, jsons) in map) {
            val scheme = HashMap<Identifier, ColorPair>()

            for (json in jsons) {
                for ((key, value) in json) {
                    val keyId = Identifier.tryParse(key)

                    if (keyId == null) {
                        LOGGER.warn(
                            "[Adorn] Invalid key '{}' in color palette {} - must be a valid identifier",
                            key,
                            id
                        )
                        continue
                    }

                    scheme[keyId] = ColorPair.fromJson(value)
                }
            }

            // id without prefix (adorn_colors/) and suffix (.json)
            val newId =
                Identifier(id.namespace, id.path.substring(PREFIX.length + 1, id.path.length - SUFFIX_LENGTH))
            this.map[newId] = ColorPalette(scheme)
        }
    }

    private fun parseHexColor(str: String): Int {
        require(str.matches(COLOR_REGEX)) {
            "Color must be a hex color beginning with '#' - found '$str'"
        }

        val colorStr = str.substring(1)
        return when (val len = colorStr.length) {
            6 -> color(colorStr.toInt(16))
            8 -> colorStr.toInt(16)
            else -> error("Invalid color length: $len")
        }
    }

    fun getColors(id: Identifier): ColorPalette =
        map[id] ?: throw IllegalArgumentException("Unknown palette $id")

    data class ColorPair(val bg: Int, val fg: Int = WLabel.DEFAULT_TEXT_COLOR) {
        companion object {
            private fun JsonElement?.asString() = (this as JsonPrimitive).asString()

            fun fromJson(json: JsonElement) =
                when (json) {
                    is JsonObject -> {
                        if (json.containsKey("fg")) {
                            ColorPair(
                                bg = parseHexColor(json["bg"].asString()),
                                fg = parseHexColor(json["fg"].asString())
                            )
                        } else {
                            ColorPair(bg = parseHexColor(json["bg"].asString()))
                        }
                    }

                    is JsonPrimitive -> ColorPair(bg = parseHexColor(json.asString()))

                    else -> throw IllegalArgumentException("Invalid color value: $json")
                }
        }
    }
}
