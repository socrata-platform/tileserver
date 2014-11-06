import dispatch._
import dispatch.Defaults._
import scala.util.{Try, Success, Failure}

object implicits {
  implicit class EnrichedInt(val i: Int) extends AnyVal {
    def ** (exp: Int): Int = scala.math.pow(i, exp).toInt
  }
}
