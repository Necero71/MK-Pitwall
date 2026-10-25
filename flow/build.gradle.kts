plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}

// Windows caps paths at 260 characters. When this project sits deep in the
// filesystem, keep Gradle's build output in a short temp folder instead.
if (rootDir.absolutePath.length > 100) {
    allprojects {
        layout.buildDirectory.set(File(System.getProperty("java.io.tmpdir"), "flowtv-build/$name"))
    }
}
