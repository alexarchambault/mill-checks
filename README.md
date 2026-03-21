# mill-checks

Check that your Mill build is well configured by ensuring
- all modules have at least one source file
- there are no "orphan" sources in your workspace (sources that are not inputs of a module)
- all test modules have at least one test class

Useful when letting agents refactor Mill builds. This ensures that
they don't accidentally make your tests pass by ignoring their sources
for example.

## Usage

### Simple use
```
./mill \
  --import io.github.alexarchambault.mill::mill-checks:0.1.0 \
  io.github.alexarchambault.millchecks.MillChecks/allChecks
```

### Advanced use

#### Run individual checks

```
./mill \
  --import io.github.alexarchambault.mill::mill-checks:0.1.0 \
  io.github.alexarchambault.millchecks.MillChecks/noEmptySources
```

```
./mill \
  --import io.github.alexarchambault.mill::mill-checks:0.1.0 \
  io.github.alexarchambault.millchecks.MillChecks/noOrphanSources
```

```
./mill \
  --import io.github.alexarchambault.mill::mill-checks:0.1.0 \
  io.github.alexarchambault.millchecks.MillChecks/noEmptyDiscoveredTestClasses
```

#### Run individual checks and pass custom tasks

```
./mill \
  --import io.github.alexarchambault.mill::mill-checks:0.1.0 \
  io.github.alexarchambault.millchecks.MillChecks/noEmptySources \
  --tasks __.allSourceFiles
```

```
./mill \
  --import io.github.alexarchambault.mill::mill-checks:0.1.0 \
  io.github.alexarchambault.millchecks.MillChecks/noOrphanSources \
  --sourceTasks '__.{allSourceFiles,compileResources}'
```

```
./mill \
  --import io.github.alexarchambault.mill::mill-checks:0.1.0 \
  io.github.alexarchambault.millchecks.MillChecks/noEmptyDiscoveredTestClasses \
  --tasks __.discoveredTestClasses
```

#### Allow some modules to have no sources

```
MILL_CHECKS_ALLOW_EMPTY_SOURCES="first,other,thing" ./mill \
  --import io.github.alexarchambault.mill::mill-checks:0.1.0 \
  io.github.alexarchambault.millchecks.MillChecks/allChecks
```

#### Ignore some sources in orphan source checks

```
MILL_CHECKS_ORPHAN_SOURCES_IGNORE="some-dir/,a/Thing.scala" ./mill \
  --import io.github.alexarchambault.mill::mill-checks:0.1.0 \
  io.github.alexarchambault.millchecks.MillChecks/allChecks
```

#### Configure source extensions when listing workspace source files to check for orphans

```
MILL_CHECKS_SOURCES_EXTENSIONS="java,scala,foo" ./mill \
  --import io.github.alexarchambault.mill::mill-checks:0.1.0 \
  io.github.alexarchambault.millchecks.MillChecks/allChecks
```
