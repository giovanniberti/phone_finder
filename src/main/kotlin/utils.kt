import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption


fun eprintln(x: Any?) = System.err.println(x)

@Throws(IOException::class)
fun copyFolderFromJar(src: String, dest: Path) {
    val resource = Phone::class.java.getResource(src).toURI()
    val fileSystem = FileSystems.newFileSystem(
            resource,
            emptyMap<String, String>()
    )

    val jarPath: Path = fileSystem.getPath(src)
    val stream = Files.walk(jarPath)
    stream.forEach { source: Path ->
        copy(source, dest.resolve(dest.fileSystem.getPath(jarPath.relativize(source).toString())))
    }
    stream.close()
}

fun copy(source: Path, dest: Path) {
    try {
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING)
    } catch (e: Exception) {
        throw RuntimeException(e.message, e)
    }
}