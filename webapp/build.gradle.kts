// The web app is built by bun, not Gradle; these tasks only expose its scripts to the root build.
fun bunTask(taskName: String, script: String, desc: String, vararg deps: String) =
    tasks.register<Exec>(taskName) {
        group = "webapp"
        description = desc
        workingDir = projectDir
        commandLine("bun", "run", script)
        dependsOn(*deps)
    }

val install = tasks.register<Exec>("bunInstall") {
    group = "webapp"
    description = "Installs the web app's dependencies."
    workingDir = projectDir
    commandLine("bun", "install", "--frozen-lockfile")
    inputs.files("package.json", "bun.lock")
    outputs.dir("node_modules")
}

val typecheck = bunTask("typecheck", "typecheck", "Type-checks the web app and its API.", "bunInstall")
val test = bunTask("test", "test", "Runs the web app and API tests.", "bunInstall")
val build = bunTask("build", "build", "Builds the web app into dist/.", "bunInstall")

tasks.register("check") {
    group = "verification"
    description = "Type-checks the web app and runs its tests."
    dependsOn(typecheck, test)
}
tasks.register("assemble") {
    group = "build"
    description = "Builds the web app."
    dependsOn(build)
}
