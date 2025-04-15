package polynote.kernel.interpreter
package python

import cats.syntax.either._
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.yaml.syntax._
import jep.python.{PyCallable, PyObject}
import jep.{Jep, JepConfig, JepException, NamingConventionClassEnquirer, SharedInterpreter}
import polynote.config
import polynote.config.{PolynoteConfig, pip}
import polynote.kernel.dependency.noCacheSentinel
import polynote.kernel.environment.{Config, CurrentNotebook, CurrentRuntime, CurrentTask}
import polynote.kernel.logging.Logging
import polynote.kernel.task.TaskManager
import polynote.kernel.util.DepsParser.flattenDeps
import polynote.kernel.{BaseEnv, CompileErrors, Completion, CompletionType, GlobalEnv, InterpreterEnv, KernelReport, ParameterHint, ParameterHints, ResultValue, ScalaCompiler, Signatures}
import polynote.messages.{CellID, DefinitionLocation, Notebook, NotebookConfig, ShortString, TinyList, TinyString}
import polynote.runtime.python.{PythonFunction, PythonObject, TypedPythonObject}
import zio.ZIO.effect
import zio.blocking.{Blocking, effectBlocking}
import zio.internal.Executor
import zio.{Has, RIO, Runtime, Semaphore, Task, UIO, ZIO}

import java.io.{FileReader, PrintWriter, StringWriter}
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.util
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ConcurrentHashMap, ConcurrentSkipListSet, Executors, ThreadFactory}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.reflect.{ClassTag, classTag}
import scala.util.Try

class SQLInterpreter private[python] (
  compiler: ScalaCompiler,
  jepInstance: Jep,
  jepExecutor: Executor,
  jepThread: AtomicReference[Thread],
  jepBlockingService: Blocking,
  runtime: zio.Runtime[Any],
  pyApi: PythonInterpreter.PythonAPI,
  venvPath: Option[java.nio.file.Path]
) extends PythonInterpreter(
  compiler,
  jepInstance,
  jepExecutor,
  jepThread,
  jepBlockingService,
  runtime,
  pyApi,
  venvPath
) {

  // Python-код для выполнения SQL через SQLAlchemy
  private val sqlalchemySetup: String =
    """
      |import os
      |from sqlalchemy import create_engine
      |from sqlalchemy.engine.url import URL
      |import pandas as pd
      |
      |pd.set_option('display.max_rows', 500)
      |pd.set_option('display.max_columns', 50)
      |pd.set_option('display.width', 0)  # Автоматически подстраивать ширину под содержимое
      |
      |# Переменная для хранения SQLAlchemy engine
      |engine = None
      |
      |# Функция для настройки подключения к базе данных
      |def setup_engine(drivername, user, password, host, port, database):
      |    global engine
      |    connection_params = {
      |        "drivername": drivername,
      |        "username": user,
      |        "password": password,
      |        "host": host,
      |        "port": port,
      |        "database": database
      |    }
      |    connection_url = URL.create(**connection_params)
      |    engine = create_engine(connection_url)
      |
      |# Функция для выполнения SQL-запроса
      |def execute_sql(query):
      |    if engine is None:
      |        raise ValueError("Engine is not initialized. Call setup_engine first.")
      |    with engine.connect() as connection:
      |        result = pd.read_sql(query, connection)
      |    return result
      |""".stripMargin


  // Переопределяем метод run для выполнения SQL-кода
  override def run(code: String, state: State): RIO[InterpreterEnv, State] = {
    for {
      // Загружаем Python-код для SQLAlchemy (если ещё не загружен)
      _ <- exec(sqlalchemySetup)

      // Передаём параметры подключения к базе данных (динамически)
      _ <- exec(
        s"""setup_engine(
           |    drivername=os.environ.get("sql_drivername", None),
           |    user=os.environ.get("sql_user", None),
           |    password=os.environ.get("sql_password", None),
           |    host=os.environ.get("sql_host", None),
           |    port=os.environ.get("sql_port", None),
           |    database=os.environ.get("sql_database", None)
           |)""".stripMargin
      )

      // Выполняем SQL-запрос через заранее определённую функцию execute_sql
      result <- jep { jep =>
        val executeSQL = jep.getValue("execute_sql", classOf[PyCallable])
        val queryResult = executeSQL.callAs(classOf[PyObject], code)
        queryResult
      }

      // Конвертируем результат выполнения SQL-запроса в Scala-объекты
      globals <- populateGlobals(state)
      resultValue <- convertSQLResultToResultValue(result, state.id)
    } yield PythonState(state.id, state.prev, List(resultValue), globals)
  }

  /**
   * Конвертирует результат выполнения SQL-запроса в ResultValue.
   */
  private def convertSQLResultToResultValue(result: PyObject, cellId: CellID): Task[ResultValue] = {
    Task {
      val resultType = compiler.global.typeOf[polynote.runtime.TableOp] // Тип результата в Polynote
      val tableData = new PythonObject(result, runner) // Оборачиваем результат в PythonObject
      new ResultValue("SQLResult", compiler.unsafeFormatType(resultType), Nil, cellId, tableData, resultType, None)
    }
  }

}

object SQLInterpreter {

  def apply(): RIO[BaseEnv with GlobalEnv with ScalaCompiler.Provider with CurrentNotebook with CurrentTask with TaskManager, SQLInterpreter] = {
    for {
      venv    <- VirtualEnvFetcher.fetch()
      interp  <- SQLInterpreter(venv)
    } yield interp
  }

  def apply(venv: Option[java.nio.file.Path]): RIO[ScalaCompiler.Provider, SQLInterpreter] = {
    for {
      (compiler, jep, executor, jepThread, blocking, runtime, api) <- PythonInterpreter.interpreterDependencies(venv)
    } yield new SQLInterpreter(compiler, jep, executor, jepThread, blocking, runtime, api, venv)
  }

  object Factory extends Interpreter.Factory {
    def languageName: String = "Sql"
    def apply(): RIO[BaseEnv with GlobalEnv with ScalaCompiler.Provider with CurrentNotebook with CurrentTask with TaskManager, Interpreter] = SQLInterpreter()
  }

}
