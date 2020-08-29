import it.skrape.core.htmlDocument
import it.skrape.exceptions.ElementNotFoundException
import it.skrape.extract
import it.skrape.selects.eachHrefAsAbsoluteLink
import it.skrape.selects.html5.span
import it.skrape.skrape
import java.net.SocketTimeoutException
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

fun generatePageURL(pageNumber: Int): String {
    return "https://www.androidworld.it/recensioni/page/${pageNumber}"
}

fun extractPhonesData(pageNumber: Int, cacheUrls: List<String> = listOf()): List<Phone> {
    val phoneReviewLinks = extractReviewLinks(pageNumber).toSet().minus(cacheUrls)
    val phonesExtracted = mutableListOf<Phone>()

    for (link in phoneReviewLinks) {
        println("Processing: $link")
        val reportLink = link.replace("recensioni", "schede")

        val (jackPresent, price) = try {
            skrape {
                url = reportLink

                extract {
                    htmlDocument {
                        if (titleText.contains("non trovata")) {
                            return@htmlDocument Pair(false, -1.0)
                        }

                        val jack = selection("div.el") {
                            findAll {
                                this.filter { div ->
                                    div.select("span").any { span -> span.text == "Jack audio" }
                                }.map {
                                    it.text.contains("Sì")
                                }
                            }.any { it }
                        }

                        val price = selection("div.el") {
                            findAll {
                                this.filter { div ->
                                    div.select("span").any { span -> span.text == "Prezzo di lancio" }
                                }.map {
                                    val numberFormat = NumberFormat.getInstance(Locale.ITALY)
                                    val rawPrice = it.text.replace("Prezzo di lancio", "").replace("EUR", "").trim()

                                    numberFormat.parse(rawPrice).toDouble()
                                }.firstOrNull()
                            }
                        }

                        Pair(jack, price ?: -1.0)
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            eprintln("URL $reportLink timed out")
            Pair(false, -1.0)
        }

        skrape {
            url = link
            followRedirects = true

            try {
                val phone = extract {
                    htmlDocument {
                        val dateText = span {
                            withClass = "date"

                            findFirst {
                                Regex("""\d{2}/\d{2}/\d{4}""").find(text)?.value
                            }
                        }

                        val releaseDate = dateText?.let {
                            val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                            return@let LocalDate.parse(it, dateTimeFormatter)
                        }

                        val model = Regex("""^Recensione (.*):""").find(titleText)?.groupValues?.get(1)

                        val grade = selection("div[data-name='Voto Finale'] p.voto-text") {
                            findFirst {
                                this.text.toFloat()
                            }
                        }

                        if (model == null) {
                            eprintln("Invalid title model found: $titleText")
                            return@htmlDocument null
                        }

                        if (releaseDate == null) {
                            eprintln("[${this@skrape.url}] Date not found")
                            return@htmlDocument null
                        }

                        return@htmlDocument Phone(model, releaseDate, grade, jackPresent, this@skrape.url, price)
                    }
                }

                phone?.let(phonesExtracted::add)
            } catch (e: ElementNotFoundException) {
                eprintln("Caught exception: ${e.message}. Skipping URL: $url")
            }
        }
    }

    return phonesExtracted
}

private fun extractReviewLinks(pageNumber: Int): List<String> {
    var reviewLinks = listOf<String>()
    skrape {
        url = generatePageURL(pageNumber)

        extract {
            htmlDocument {
                "h2.entry-title a" {
                    findAll {
                        reviewLinks = eachHrefAsAbsoluteLink()
                    }
                }
            }
        }
    }

    return reviewLinks.filter { it.startsWith("https://www.androidworld.it") }
}