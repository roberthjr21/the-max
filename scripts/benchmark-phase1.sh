#!/usr/bin/env bash
# Ejecutar desde la raíz con un JDK 17, SDK Android 35, Python y taskset.
set -euo pipefail
root=$(pwd)
work=$(mktemp -d)
baseline=ed67823803cd84143c8e3481f5f6f3b042ad2e44
git worktree add --quiet --detach "$work/base" "$baseline"
git worktree add --quiet --detach "$work/candidate" HEAD
cleanup() {
  git worktree remove --force "$work/base"
  git worktree remove --force "$work/candidate"
  rm -rf "$work"
}
trap cleanup EXIT
cpu=$(python3 -c 'import os; print(min(os.sched_getaffinity(0)))')
for size in 1 4 15; do python3 scripts/generate-chat.py "$size" "$work/$size.txt"; done
cat > "$work/classpath.gradle" <<'GRADLE'
gradle.projectsEvaluated {
    def app = gradle.rootProject.project(':app')
    app.tasks.register('benchmarkClasspath') {
        dependsOn app.tasks.named('compileDebugUnitTestKotlin'), app.tasks.named('compileDebugKotlin')
        doLast {
            def tests = app.tasks.named('testDebugUnitTest').get()
            def kotlinMain = app.tasks.named('compileDebugKotlin').get().destinationDirectory
            new File(System.getenv('BENCH_CP')).text = app.files(kotlinMain, tests.classpath).asPath
        }
    }
}
GRADLE
for phase in base candidate; do
  cd "$work/$phase"
  cat > app/src/test/java/co/themax/chat/ColdBenchmark.kt <<'KOTLIN'
package co.themax.chat
import java.io.File
fun main(args: Array<String>) {
    val input = File(args.single()).readText()
    val start = System.nanoTime()
    val report = analyze(input)
    println("messages=" + report.counts.values.sum() + " ms=" + (System.nanoTime() - start) / 1_000_000)
}
KOTLIN
  export BENCH_CP="$work/$phase.cp"
  ./gradlew -I "$work/classpath.gradle" :app:benchmarkClasspath assembleRelease
  stat -c "BENCH %s bytes $phase unsigned-release" app/build/outputs/apk/release/*unsigned.apk
  for size in 1 4 15; do
    for run in 1 2 3; do
      printf 'BENCH %s %s MiB run=%s ' "$phase" "$size" "$run"
      taskset -c "$cpu" java -XX:ActiveProcessorCount=1 -Xmx256m -cp "$(cat "$BENCH_CP")" co.themax.chat.ColdBenchmarkKt "$work/$size.txt"
    done
  done
  cd "$root"
done
