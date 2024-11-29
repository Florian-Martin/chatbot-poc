import java.io.File

// Define the new values here
val newProjectName = "ChatBotPocAndroid"
val newPackageName = "chatbot.poc.androidapp"
val newBundleIdPrefix = "com.example"
	
// Define the old values here
val oldProjectName = "ChatBot"
val oldPackageName = "com.example.chatbot"
val oldBundleIdPrefix = "ttt.uuu"

// Define the paths of the files that need to be modified
val rootDir = File(".")
val gradleSettingsFile = File(rootDir, "settings.gradle.kts")
val androidManifestFile = File(rootDir, "androidApp/src/main/AndroidManifest.xml")
val iosAppDir = File(rootDir, "iosApp")
val projectYmlFile = File(iosAppDir, "project.yml")

// Function to replace text in a file
fun replaceInFile(file: File, oldValue: String, newValue: String) {
    if (file.exists()) {
        val content = file.readText()
        val updatedContent = content.replace(oldValue, newValue)
        file.writeText(updatedContent)
    }
}

// Function to rename directories by component, starting with the second component, then the first
fun renameDirectoriesByComponent(oldPackageParts: List<String>, newPackageParts: List<String>, sourceRoot: File) {
    if (oldPackageParts.size != newPackageParts.size) {
        println("Error: The old and new package names must have the same number of components.")
        return
    }

    for (i in listOf(2, 1, 0)) {
        val oldComponent = oldPackageParts[i]
        val newComponent = newPackageParts[i]

        // Walk through all directories to find and rename matching ones
        sourceRoot.walkTopDown()
            .filter { it.isDirectory && it.name == oldComponent }
            .forEach { dir ->
                val targetDir = File(dir.parentFile, newComponent)

                if (targetDir.exists()) {
                    println("Warning: Target directory ${targetDir.absolutePath} already exists. Skipping rename.")
                } else if (dir.renameTo(targetDir)) {
                    println("Renamed directory ${dir.absolutePath} to ${targetDir.absolutePath}")
                } else {
                    println("Failed to rename directory ${dir.absolutePath}. Trying manual copy and delete.")
                    try {
                        dir.copyRecursively(targetDir, overwrite = true)
                        dir.deleteRecursively()
                        println("Copied and deleted directory ${dir.absolutePath} to ${targetDir.absolutePath}")
                    } catch (e: Exception) {
                        println("Error during manual copy and delete: ${e.message}")
                    }
                }
            }
    }
}

// Split package names into their components
val oldPackageParts = oldPackageName.split(".")
val newPackageParts = newPackageName.split(".")

// 1. Modify the project name in settings.gradle.kts
replaceInFile(gradleSettingsFile, "rootProject.name = \"$oldProjectName\"", "rootProject.name = \"$newProjectName\"")

// 2. Modify the Android package name in AndroidManifest.xml
replaceInFile(androidManifestFile, "package=\"$oldPackageName\"", "package=\"$newPackageName\"")

// 3. Modify files across the project
rootDir.walkTopDown()
    .filter { it.isFile && it.extension in listOf("kt", "kts", "xml", "SymbolProcessorProvider", "swift") && it.name != "renameProject.kts" }
    .forEach { file ->
        replaceInFile(file, oldPackageName, newPackageName)
    }

// 4. Rename directories by component
renameDirectoriesByComponent(oldPackageParts, newPackageParts, rootDir)

// 5. Rename the `AtipikTemplate` directory in iosApp
val oldIosAppDir = File(iosAppDir, oldProjectName)
if (oldIosAppDir.exists()) {
    val newIosAppDir = File(iosAppDir, newProjectName)
    if (oldIosAppDir.renameTo(newIosAppDir)) {
        println("Renamed iosApp subdirectory from $oldProjectName to $newProjectName")
    } else {
        println("Failed to rename iosApp subdirectory. Please check permissions.")
    }
}

// 6. Modify project.yml to reflect the new project name and package name
if (projectYmlFile.exists()) {
    var projectYmlContent = projectYmlFile.readText()

    // Replace the name
    projectYmlContent = projectYmlContent.replace("$oldProjectName", "$newProjectName")

    // Replace occurrences of the old package name with the new package name
    projectYmlContent = projectYmlContent.replace(oldPackageName, newPackageName)

	// Replace the bundle id prefix
	projectYmlContent = projectYmlContent.replace("bundleIdPrefix: $oldBundleIdPrefix", "bundleIdPrefix: $newBundleIdPrefix")

    projectYmlFile.writeText(projectYmlContent)
    println("Modified project.yml successfully.")
} else {
    println("project.yml not found in iosApp directory.")
}

println("Project renaming completed successfully.")
