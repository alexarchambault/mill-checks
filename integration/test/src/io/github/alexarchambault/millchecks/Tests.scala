package io.github.alexarchambault.millchecks

import utest.*

object Tests extends TestSuite {

  lazy val extraRepo = sys.env
    .get("MILL_CHECKS_INTEGRATION_REPO")
    .map(os.Path(_))
    .map(_.toNIO.toUri.toASCIIString)
    .getOrElse {
      sys.error("MILL_CHECKS_INTEGRATION_REPO not set")
    }

  lazy val version = sys.env.getOrElse(
    "MILL_CHECKS_INTEGRATION_VERSION",
    sys.error("MILL_CHECKS_INTEGRATION_VERSION not set")
  )

  lazy val millScript = sys.env
    .get("MILL_CHECKS_INTEGRATION_MILL_SCRIPT")
    .map(os.Path(_))
    .getOrElse {
      sys.error("MILL_CHECKS_INTEGRATION_MILL_SCRIPT not set")
    }

  lazy val resourceDir = sys.env
    .get("MILL_CHECKS_INTEGRATION_RESOURCE_DIR")
    .map(os.Path(_))
    .getOrElse {
      sys.error("MILL_CHECKS_INTEGRATION_RESOURCE_DIR not set")
    }

  lazy val extraEnv =
    Map(
      "COURSIER_REPOSITORIES" -> {
        val current = sys.env.getOrElse("COURSIER_REPOSITORIES", "ivy2Local|central")
        s"$extraRepo|$current"
      }
    )

  def withTestWorkspace[T](name: os.SubPath)(f: os.Path => T): T = {
    assert(os.isDir(resourceDir))
    val resourceDir0 = resourceDir / name
    assert(os.isDir(resourceDir0))
    val dir = os.temp.dir(prefix = "mill-checks-test-")
    try {
      for (elem <- os.list(resourceDir0))
        os.copy.into(elem, dir)
      os.copy(millScript, dir / "mill")
      f(dir)
    }
    finally
      os.remove.all(dir)
  }

  val tests = utest.Tests {

    test("valid") {
      withTestWorkspace("valid") { ws =>
        os.proc(ws / "mill", "--import", s"io.github.alexarchambault.mill::mill-checks:$version", "io.github.alexarchambault.millchecks.MillChecks/allChecks")
          .call(cwd = ws, env = extraEnv)

        ()
      }
    }

    test("empty-sources") {
      withTestWorkspace("empty-sources") { ws =>
        val res = os.proc(ws / "mill", "--import", s"io.github.alexarchambault.mill::mill-checks:$version", "io.github.alexarchambault.millchecks.MillChecks/allChecks")
          .call(cwd = ws, env = extraEnv, stdout = os.Pipe, mergeErrIntoOut = true, check = false)

        assert(res.exitCode != 0)

        val output = res.out.text()
        assert(output.contains("Found modules with no sources:"))
        assert(output.contains("  foo.allSourceFiles"))
      }
    }

    test("empty-test-sources") {
      withTestWorkspace("empty-test-sources") { ws =>
        val res = os.proc(ws / "mill", "--import", s"io.github.alexarchambault.mill::mill-checks:$version", "io.github.alexarchambault.millchecks.MillChecks/allChecks")
          .call(cwd = ws, env = extraEnv, stdout = os.Pipe, mergeErrIntoOut = true, check = false)

        assert(res.exitCode != 0)

        val output = res.out.text()
        assert(output.contains("Found modules with no sources:"))
        assert(output.contains("  foo.test.allSourceFiles"))
      }
    }

    test("no-test-classes") {
      withTestWorkspace("no-test-classes") { ws =>
        val res = os.proc(ws / "mill", "--import", s"io.github.alexarchambault.mill::mill-checks:$version", "io.github.alexarchambault.millchecks.MillChecks/allChecks")
          .call(cwd = ws, env = extraEnv, stdout = os.Pipe, mergeErrIntoOut = true, check = false)

        assert(res.exitCode != 0)

        val output = res.out.text()
        assert(output.contains("foo.test.discoveredTestClasses is empty"))
      }
    }

    test("orphan-sources") {
      withTestWorkspace("orphan-sources") { ws =>
        val res = os.proc(ws / "mill", "--import", s"io.github.alexarchambault.mill::mill-checks:$version", "io.github.alexarchambault.millchecks.MillChecks/allChecks")
          .call(cwd = ws, env = extraEnv, stdout = os.Pipe, mergeErrIntoOut = true, check = false)

        assert(res.exitCode != 0)

        val output = res.out.text()
        assert(output.contains("Found orphan sources:"))
        assert(output.contains("  bar/Thing.scala"))
      }
    }

    test("validBsp") {
      withTestWorkspace("valid") { ws =>
        os.proc(ws / "mill", "--import", s"io.github.alexarchambault.mill::mill-checks:$version", "io.github.alexarchambault.millchecks.MillChecks/bspChecks")
          .call(cwd = ws, env = extraEnv)

        ()
      }
    }

    test("bsp-source-triggers-compile") {
      withTestWorkspace("bsp-source-triggers-compile") { ws =>
        val res = os.proc(ws / "mill", "--import", s"io.github.alexarchambault.mill::mill-checks:$version", "io.github.alexarchambault.millchecks.MillChecks/bspChecks")
          .call(cwd = ws, env = extraEnv, stdout = os.Pipe, mergeErrIntoOut = true, check = false)

        assert(res.exitCode != 0)

        val output = res.out.text()
        assert(output.contains("__.allSources or __.allSourceFiles depend on compile tasks: "))
        assert(output.contains("foo.compile"))
      }
    }

  }

}
