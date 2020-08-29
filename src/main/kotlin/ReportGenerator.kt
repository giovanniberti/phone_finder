import de.neuland.jade4j.Jade4J
import kotlinx.serialization.*
import kotlinx.serialization.builtins.list
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.io.File
import java.nio.charset.Charset
import java.nio.file.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter


@Serializer(forClass = LocalDate::class)
object LocalDateSerializer : KSerializer<LocalDate> {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    override val descriptor: SerialDescriptor
        get() = PrimitiveDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.format(formatter))
    }

    override fun deserialize(decoder: Decoder): LocalDate {
        return LocalDate.parse(decoder.decodeString(), formatter)
    }
}


@Serializable
data class Phone(
        val model: String,
        val releaseDate: @Serializable(with = LocalDateSerializer::class) LocalDate,
        val grade: Float,
        val jack: Boolean,
        val url: String,
        val price: Double
)

fun generateReport(reportPath: Path, reportViewModel: Map<String, Any>) {
    val uri = Phone::class.java.getResource("/report").toURI()

    val outputFile = reportPath.toFile()
    val tmpDirectory = createTempDir().toPath()

    if (uri.scheme == "jar") {
        copyFolderFromJar("/report", tmpDirectory)
    } else {
        val templateDirectory = if (uri.scheme == "jar") {
            val fileSystem = FileSystems.newFileSystem(uri, emptyMap<String, Any>())
            fileSystem.getPath("/report")
        } else {
            Paths.get(uri)
        }

        templateDirectory.toFile().copyRecursively(tmpDirectory.toFile())
    }

    outputFile.bufferedWriter(Charset.forName("UTF-8")).use {
        Jade4J.render(File(tmpDirectory.toFile(), "report.template.jade").absolutePath, reportViewModel, it)
    }
}

fun main(args: Array<String>) {
    val cachePath = FileSystems.getDefault().getPath("cached_data.json").toFile()
    val json = Json(JsonConfiguration.Stable)

    val cachedPhones = if (cachePath.exists()) {
        println("Loading from cache...")

        cachePath.reader().use {
            val result = json.parse(Phone.serializer().list, it.readText())
            println("Loaded ${result.size} models")

            result
        }
    } else {
        listOf()
    }

    val newPhones = (1..0).flatMap { pageNumber -> extractPhonesData(pageNumber, cachedPhones.map { it.url }) }
    val allPhones = cachedPhones + newPhones

    println("Saving phone data...")
    val serializedData = json.stringify(Phone.serializer().list, allPhones)

    cachePath.writer().use {
        it.write(serializedData)
    }

    val makerBlacklist = listOf("huawei", "oppo", "honor", "xiaomi", "redmi", "realme", "poco",
            "nubia", "red ?magic")

    val reportPhones = allPhones
            .asSequence()
            .filter { it.jack }
            .filter { !it.model.toLowerCase().contains(Regex(makerBlacklist.joinToString("|"))) }
            .filter { it.grade >= 8.0 }
            .filter { it.releaseDate.year == 2020 || it.releaseDate.year == 2019 && it.releaseDate.monthValue >= 4 }
            .filter { it.price in 300.0..750.0 }
            .sortedWith(
                    Comparator.comparing<Phone, LocalDate> { it.releaseDate.withDayOfMonth(1) }.reversed()
                            .thenByDescending { it.grade }
                            .thenByDescending { it.price }
            )
            .toList()

    val reportPath = FileSystems.getDefault().getPath("report.html")

    println("Generating report into: ${reportPath.toAbsolutePath()}...")
    generateReport(reportPath, mapOf("phones" to reportPhones))

    println("Finished!")
}
