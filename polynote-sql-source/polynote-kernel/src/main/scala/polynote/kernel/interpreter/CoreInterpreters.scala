package polynote.kernel.interpreter

import polynote.kernel.interpreter.python.PythonInterpreter
import polynote.kernel.interpreter.scal.ScalaInterpreter
import polynote.kernel.interpreter.python.SQLInterpreter

class CoreInterpreters extends Loader {
  override def factories: Map[String, Interpreter.Factory] = Map(
    "scala" -> ScalaInterpreter.Factory,
    "python" -> PythonInterpreter.Factory,
    "sql" -> SQLInterpreter.Factory
  )
}
