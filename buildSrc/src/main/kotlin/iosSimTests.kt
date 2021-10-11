import org.gradle.api.Project
import java.io.ByteArrayOutputStream


private fun Project.getSimIdAndStatus(): Pair<String, Boolean> {
    val deviceName = "iPhone 12"
    val output = ByteArrayOutputStream()
    exec {
        standardOutput = output
        executable = "xcrun"
        args("simctl", "list")
    }
    return output.toString()
        .split("\n")
        .first { it.contains(" $deviceName (") }
        .substringAfter('(')
        .run {
            Pair(
                substringBefore(')'),
                substringAfterLast('(').startsWith("booted", true)
            )
        }
}

private fun Project.runSimctlCommand(vararg args: String) {
    exec {
        executable = "xcrun"
        args("simctl")
        args(*args)
    }
}

fun Project.bootSimulator() {
    val (simId, booted) = getSimIdAndStatus()
    if (!booted) {
        runCatching { runSimctlCommand("boot", simId) }
        repeat(10) {
            if (getSimIdAndStatus().second) {
                return@repeat
            } else {
                check(it < 9) { "Failed to boot iOS simulator" }
                Thread.sleep(500)
            }
        }
    }
}

fun Project.shutdownSimulator() {
    val (simId, booted) = getSimIdAndStatus()
    if (booted) {
        runCatching { runSimctlCommand("shutdown", simId) }
    }
}

fun Project.runSimulatorTests(filePath: String) {
    bootSimulator()
    runSimctlCommand("spawn", "booted", filePath)
}