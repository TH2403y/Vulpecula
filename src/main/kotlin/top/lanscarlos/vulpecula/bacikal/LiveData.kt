package top.lanscarlos.vulpecula.bacikal

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Entity
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.ProxyPlayer
import taboolib.common.util.Location
import taboolib.common.util.Vector
import taboolib.library.kether.LoadError
import taboolib.library.xseries.XMaterial
import taboolib.module.kether.ScriptFrame
import taboolib.platform.type.BukkitPlayer
import taboolib.platform.util.buildItem
import taboolib.platform.util.toProxyLocation
import java.awt.Color
import java.util.concurrent.CompletableFuture

/**
 * Vulpecula
 * top.lanscarlos.vulpecula.bacikal
 *
 * @author Lanscarlos
 * @since 2023-02-26 21:01
 */
open class LiveData<T>(
    val func: BacikalReader.() -> Bacikal.Action<T>
) {

    var trim: Array<out String> = emptyArray()
    var expect: Array<out String> = emptyArray()
    lateinit var action: Bacikal.Action<T>

    open fun isAccepted(): Boolean {
        return ::action.isInitialized
    }

    open fun accept(reader: BacikalReader): LiveData<T> {
        if (isAccepted()) return this
        if (trim.isNotEmpty()) reader.expectToken(*trim)
        if (expect.isNotEmpty() && !reader.expectToken(*expect)) {
            throw LoadError.NOT_MATCH.create("[${expect.joinToString(", ")}]", reader.peekToken())
        }
        action = func(reader)
        return this
    }

    open fun accept(frame: ScriptFrame): CompletableFuture<T> {
        return action.run(frame)
    }

    fun trim(vararg expect: String): LiveData<T> {
        trim = expect
        return this
    }

    fun expect(vararg expect: String): LiveData<T> {
        this.expect = expect
        return this
    }

    fun optional(vararg expect: String): LiveData<T?> {
        return if (expect.isEmpty()) {
            LiveData {
                Bacikal.Action { frame ->
                    this@LiveData.accept(frame).thenApply { it }
                }
            }
        } else {
            LiveData {
                if (this.expectToken(*expect)) {
                    this@LiveData.accept(reader = this)
                    Bacikal.Action { frame ->
                        this@LiveData.accept(frame).thenApply { it }
                    }
                } else {
                    Bacikal.Action { CompletableFuture.completedFuture(null) }
                }
            }
        }
    }

    fun optional(vararg expect: String, def: T): LiveData<T> {
        return if (expect.isNotEmpty()) {
            LiveData {
                if (this.expectToken(*expect)) {
                    this@LiveData.accept(reader = this)
                    Bacikal.Action { frame ->
                        this@LiveData.accept(frame).thenApply { it }
                    }
                } else {
                    Bacikal.Action { CompletableFuture.completedFuture(def) }
                }
            }
        } else this
    }

    fun <R> map(func: (T) -> R): LiveData<R> {
        return LiveData {
            this@LiveData.accept(reader = this)
            Bacikal.Action { frame ->
                this@LiveData.accept(frame).thenApply(func)
            }
        }
    }

    companion object {

        fun <T> point(value: T): LiveData<T> {
            return LiveData {
                Bacikal.Action { CompletableFuture.completedFuture(value) }
            }
        }

        fun <T> of(func: BacikalReader.() -> T): LiveData<T> {
            return LiveData {
                Bacikal.Action { CompletableFuture.completedFuture(func(this)) }
            }
        }

        fun <T> frame(func: ((Any?) -> T)): LiveData<T> {
            return LiveData {
                val action = this.readAction()
                Bacikal.Action { frame ->
                    frame.newFrame(action).run<Any?>().thenApply { func(it) }
                }
            }
        }

        val Any.liveStringList: List<String>?
            get() {
                return when (this) {
                    is String -> listOf(this)
                    is Array<*> -> {
                        this.mapNotNull { el -> el?.toString() }
                    }
                    is Collection<*> -> {
                        this.mapNotNull { el -> el?.toString() }
                    }
                    else -> null
                }
            }

        val Any.liveVector: Vector?
            get() {
                return when (this) {
                    is Vector -> this
                    is org.bukkit.util.Vector -> Vector(this.x, this.y, this.z)
                    is Location -> this.direction
                    is org.bukkit.Location -> this.toProxyLocation().direction
                    is String -> {
                        // x,y,z
                        if (!this.matches("^\\d+(\\.\\d+)?,\\d+(\\.\\d+)?,\\d+(\\.\\d+)?\$".toRegex())) return null
                        val demand = this.split(",").map { el -> el.toDouble() }
                        Vector(demand[0], demand[1], demand[2])
                    }
                    else -> null
                }
            }

        val Any.liveLocation: Location?
            get() {
                return when (this) {
                    is Location -> this
                    is org.bukkit.Location -> this.toProxyLocation()
                    is ProxyPlayer -> this.location
                    is Entity -> this.location.toProxyLocation()
                    is Vector -> Location(null, this.x, this.y, this.z)
                    is org.bukkit.util.Vector -> Location(null, this.x, this.y, this.z)
                    is String -> {
                        /*
                        * world,x,y,z
                        * world,x,y,z,yaw.pitch
                        * */
                        if (!this.matches("^[A-Za-z0-9_\\- \\u4e00-\\u9fa5]+,-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?(,-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?)?\$".toRegex())) return null
                        val demand = this.split(",")
                        Location(
                            demand[0], demand[1].toDouble(), demand[2].toDouble(), demand[3].toDouble(),
                            demand.getOrNull(4)?.toFloatOrNull() ?: 0f,
                            demand.getOrNull(5)?.toFloatOrNull() ?: 0f
                        )
                    }
                    else -> null
                }
            }

        val Any.liveColor: Color?
            get() {
                return when (this) {
                    is Color -> this
                    is org.bukkit.Color -> Color(this.red, this.green, this.blue)
                    is String -> {
                        if (this.startsWith('#') && this.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})\$".toRegex())) {
                            // hex
                            val hex = this.substring(1)
                            Color.decode(hex)
                        } else if (this.matches("^((2[0-4][0-9]|25[0-5])(,|-)){2}(2[0-4][0-9]|25[0-5])\$".toRegex())) {
                            val demand = this.split(",").map { el -> el.toInt() }
                            Color(demand[0], demand[1], demand[2])
                        } else {
                            val rgb = this.toIntOrNull() ?: return null
                            Color(rgb)
                        }
                    }
                    else -> null
                }
            }

        val Any.liveEntity: Entity?
            get() {
                return when (this) {
                    is Entity -> this
                    is OfflinePlayer -> this.player
                    is ProxyPlayer -> (this as? BukkitPlayer)?.player
                    is String -> Bukkit.getPlayerExact(this)
                    else -> null
                }
            }

        val Any.livePlayer: Player?
            get() {
                return when (this) {
                    is Player -> this
                    is OfflinePlayer -> this.player
                    is ProxyPlayer -> (this as? BukkitPlayer)?.player
                    is String -> Bukkit.getPlayerExact(this)
                    else -> null
                }
            }

        val Any.liveItemStack: ItemStack?
            get() {
                return when (this) {
                    is ItemStack -> this
                    is Item -> this.itemStack
                    is String -> {
                        val material = XMaterial.matchXMaterial(this.uppercase()).let { mat ->
                            if (mat.isPresent) mat.get() else return null
                        }
                        buildItem(material)
                    }
                    else -> null
                }
            }

    }
}