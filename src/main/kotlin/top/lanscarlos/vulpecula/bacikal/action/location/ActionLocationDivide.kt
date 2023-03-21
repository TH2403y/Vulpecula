package top.lanscarlos.vulpecula.bacikal.action.location

/**
 * Vulpecula
 * top.lanscarlos.vulpecula.bacikal.action.location
 *
 * @author Lanscarlos
 * @since 2023-03-21 15:03
 */
object ActionLocationDivide : ActionLocation.Resolver {

    override val name: Array<String> = arrayOf("div", "times")

    /**
     * loc div &loc with &target
     * loc div &loc with x,y,z
     * loc div &loc &x &y &z -yaw &yaw ...
     * loc div &loc -x &x -y &y -z &z -yaw &yaw ...
     * */
    override fun resolve(reader: ActionLocation.Reader): ActionLocation.Handler<out Any?> {
        val source = reader.source().accept(reader)

        return reader.transfer {
            if (expectToken("with")) {
                /*
                * 坐标对象
                * loc div &loc with &target
                * loc div &loc with 0,0,0
                * */
                combine(
                    source,
                    location()
                ) { location, target ->
                    location.x /= target.x
                    location.y /= target.y
                    location.z /= target.z
                    location.yaw /= target.yaw
                    location.pitch /= target.pitch
                    location
                }
            } else if (peekToken().matches("-?\\d+(.\\d+)?".toRegex())) {
                /*
                * 数字
                * loc div &loc x y z -world &world ...
                * */
                combine(
                    source,
                    double(), // x
                    double(), // y
                    double(), // z
                    argument("yaw", then = float()),
                    argument("pitch", "p", then = float())
                ) { location, x, y, z, yaw, pitch ->
                    location.x /= x
                    location.x /= y
                    location.x /= z
                    location.yaw /= yaw ?: 1f
                    location.pitch /= pitch ?: 1f
                    location
                }
            } else {
                /*
                * 参数
                * loc div &loc -x &x -y &y -z &z -world &world ...
                * */
                combine(
                    source,
                    argument("x", then = double()),
                    argument("y", then = double()),
                    argument("z", then = double()),
                    argument("yaw", then = float()),
                    argument("pitch", "p", then = float())
                ) { location, x, y, z, yaw, pitch ->
                    location.x /= x ?: 1.0
                    location.x /= y ?: 1.0
                    location.x /= z ?: 1.0
                    location.yaw /= yaw ?: 1f
                    location.pitch /= pitch ?: 1f
                    location
                }
            }
        }
    }
}