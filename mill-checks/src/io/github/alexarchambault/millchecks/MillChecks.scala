package io.github.alexarchambault.millchecks

import mill.*
import mill.api.*
import mill.constants.OutFiles
import mill.millchecks.EvaluatorHelper
import mill.util.{MainModule, Tasks}

object MillChecks extends ExternalModule {

  def allowEmptySources(env: Map[String, String]) = env
    .get("MILL_CHECKS_ALLOW_EMPTY_SOURCES")
    .map(_.split(",").toSeq)
    .getOrElse(Nil)
    .map(_.trim)
    .filter(_.nonEmpty)

  def sourcesExtensions(env: Map[String, String]) = env
    .get("MILL_CHECKS_SOURCES_EXTENSIONS")
    .map(_.split(",").toSeq)
    .getOrElse(Seq("java", "scala"))
    .map(_.trim)
    .filter(_.nonEmpty)
    .toSet

  def orphanSourcesIgnore(env: Map[String, String]) = env
    .get("MILL_CHECKS_ORPHAN_SOURCES_IGNORE")
    .map(_.split(",").toSeq)
    .getOrElse(Nil)
    .map(_.trim)
    .filter(_.nonEmpty)
    .map(os.SubPath(_))

  def noEmptySources(evaluator: Evaluator, tasks: Tasks[Seq[PathRef]] = Tasks(Nil)): Task.Command[Unit] =
    Task.Command(exclusive = true) {
      val values =
        if (tasks.value.isEmpty) {
          System.err.println("No tasks passed as argument to noEmptySources, assuming __.allSourceFiles")
          val res = evaluator.evaluate(Seq("__.allSourceFiles"))
          res.get.selectedTasks.zip(res.get.values.get.map(_.asInstanceOf[Seq[PathRef]]))
        }
        else
          tasks.value.zip(Task.sequence(tasks.value)())

      val allowEmptySources0 = allowEmptySources(Task.env)
      if (allowEmptySources0.nonEmpty) {
        System.err.println("Empty sources allowed for modules:")
        for (mod <- allowEmptySources0)
          System.err.println(s"- $mod")
      }
      val invalid = values
        .filter(_._2.isEmpty)
        .filter {
          case (m, _) =>
            val moduleName = m.toString.stripSuffix(".allSourceFiles")
            !allowEmptySources0.contains(moduleName)
        }

      if (invalid.nonEmpty) {
        System.err.println("Found modules with no sources:")
        for ((m, _) <- invalid)
          System.err.println(s"  $m")
        Task.fail(s"Found modules with no sources: ${invalid.map(_._1).mkString(", ")}")
      }
    }

  def noOrphanSources(evaluator: Evaluator, tasks: Tasks[Seq[PathRef]] = Tasks(Nil)): Task.Command[Unit] =
    Task.Command(exclusive = true) {
      val input =
        if (tasks.value.isEmpty) {
          System.err.println("No tasks passed as argument to noOrphanSources, assuming __.{allSourceFiles,compileResources}")
          evaluator
            .evaluate(Seq("__.{allSourceFiles,compileResources}"))
            .get
            .values
            .get
            .map(_.asInstanceOf[Seq[PathRef]])
        }
        else
          Task.sequence(tasks.value)()

      val allSources = input
        .flatten
        .map(_.path)
        .filter(_.startsWith(BuildCtx.workspaceRoot))
        .flatMap { path =>
          if (os.isDir(path)) os.walk(path)
          else Seq(path)
        }
        .map(_.subRelativeTo(BuildCtx.workspaceRoot))
        .toSet

      val outDir = BuildCtx.workspaceRoot / OutFiles.OutFiles.out
      val orphanSourcesIgnore0 = orphanSourcesIgnore(Task.env)
      if (orphanSourcesIgnore0.nonEmpty) {
        System.err.println("Ignoring orphan sources from")
        for (elem <- orphanSourcesIgnore0)
          System.err.println(s"- $elem")
      }
      val foundFiles = os.walk(
        BuildCtx.workspaceRoot,
        skip = p =>
          p.startsWith(outDir) || {
            val subPath = p.subRelativeTo(BuildCtx.workspaceRoot)
            (subPath.segments.length == 1 && subPath.segments.head.startsWith(".")) ||
              orphanSourcesIgnore0.exists(subPath.startsWith)
          }
      )
      val sourcesExtensions0 = sourcesExtensions(Task.env)
      System.err.println(s"Source extensions: ${sourcesExtensions0.toVector.sorted.mkString(", ")}")
      val foundSources = foundFiles
        .filter(f => sourcesExtensions0(f.ext))
        .filter(os.isFile)
        .map(_.subRelativeTo(BuildCtx.workspaceRoot))

      val orphanSources = foundSources.filter(!allSources.contains(_))

      if (orphanSources.nonEmpty) {
        System.err.println("Found orphan sources:")
        for (f <- orphanSources)
          System.err.println(s"  $f")
        Task.fail("Found orphan sources")
      }
    }

  def noEmptyDiscoveredTestClasses(evaluator: Evaluator, tasks: Tasks[Seq[String]] = Tasks(Nil)): Task.Command[Unit] =
    Task.Command(exclusive = true) {
      val results =
        if (tasks.value.isEmpty) {
          System.err.println("No tasks passed as argument to noEmptyDiscoveredTestClasses, assuming __.discoveredTestClasses")
          val res = evaluator.evaluate(Seq("__.discoveredTestClasses"))
          res.get.selectedTasks.zip(res.get.values.get.map(_.asInstanceOf[Seq[String]]))
        }
        else
          tasks.value.zip(Task.sequence(tasks.value)())
      val noTestClasses = results.filter(_._2.isEmpty)
      if (noTestClasses.nonEmpty)
        for ((task, _) <- noTestClasses)
          System.err.println(s"$task is empty")
        Task.fail("Found test modules with no test classes")
    }

  private def defaultTasksThatShouldntTriggerCompilation = Seq("__.allSources", "__.allSourceFiles")
  def sourcesDontTriggerCompilation(evaluator: Evaluator, toCheck: String*): Task.Command[Unit] = {
    val mainModule = EvaluatorHelper(evaluator).rootModule match {
      case m: MainModule => m
      case other => sys.error(s"Unexpected root module type: $other")
    }
    val toCheck0 =
      if (toCheck.isEmpty) defaultTasksThatShouldntTriggerCompilation
      else toCheck
    val plansCommand = Task.traverse(toCheck0)(cmd => mainModule.plan(evaluator, cmd))
    Task.Command(exclusive = true) {
      val plans = plansCommand()

      val compileTasksInPlans = plans.map(_.filter(t => t.endsWith(".compile") || t.contains(".super.compile.")))

      if (compileTasksInPlans.exists(_.nonEmpty))
        Task.fail(s"${toCheck0.mkString(" or ")} depend on compile tasks: ${compileTasksInPlans.flatten.mkString(", ")}")

      ()
    }
  }

  def allChecks(
    evaluator: Evaluator,
    emptySourcesTasks: Tasks[Seq[PathRef]] = Tasks(Nil),
    orphanSourcesTasks: Tasks[Seq[PathRef]] = Tasks(Nil),
    discovedTestClassesTasks: Tasks[Seq[String]] = Tasks(Nil)
  ): Task.Command[Unit] =
    Task.Command(exclusive = true) {
      noEmptySources(evaluator, emptySourcesTasks)()
      noOrphanSources(evaluator, orphanSourcesTasks)()
      noEmptyDiscoveredTestClasses(evaluator, discovedTestClassesTasks)()
    }

  def bspChecks(
    evaluator: Evaluator,
    shouldntTriggerCompilation: Seq[String] = Nil
  ): Task.Command[Unit] =
    Task.Command(exclusive = true) {
      sourcesDontTriggerCompilation(evaluator, shouldntTriggerCompilation*)()
    }

  lazy val millDiscover = Discover[this.type]
}
