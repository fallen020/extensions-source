package eu.kanade.tachiyomi.extension.hi.readhindimanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

internal fun List<String>.withoutTypeTags() = filterNot { it.equals("manga", true) || it.equals("manhua", true) || it.equals("manhwa", true) }

@Serializable
class SeriesProps(val manga: MangaDto)

@Serializable
class MangaDto(
    private val slug: String,
    private val title: String,
    private val altTitle: String?,
    private val description: String?,
    private val genres: List<String>?,
    private val authors: List<String>?,
    private val artists: List<String>?,
    private val status: String?,
    private val type: String?,
    private val year: Int?,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/manga/$slug"
        title = this@MangaDto.title
        thumbnail_url = "$baseUrl/api/vault/stream/cover/$slug"
        author = authors.cleanNames()
        artist = artists.cleanNames()
        genre = genres?.withoutTypeTags()?.joinToString()
        status = when (this@MangaDto.status?.lowercase()) {
            "completed" -> SManga.COMPLETED
            "ongoing" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(this@MangaDto.description)
            .info(year?.toString(), type?.replaceFirstChar(Char::uppercaseChar))
            .altTitles(altTitles())
            .build()
    }

    private fun List<String>?.cleanNames() = this?.flatMap { it.split('\n') }
        ?.map { it.trim().trim(',') }
        ?.filter { it.isNotBlank() }
        ?.joinToString()

    private fun altTitles() = altTitle?.split(" / ")
        ?.map { it.removePrefix("?").trim() }
        .orEmpty()
}

@Serializable
class CatalogProps(val initialManga: List<GenreCatalogDto>)

@Serializable
class GenreCatalogDto(val genres: List<String>?)

@Serializable
class MangaDetailDto(val data: MangaDataDto)

@Serializable
class MangaDataDto(val chapters: List<ApiChapterDto>)

@Serializable
class ApiChapterDto(
    private val id: String,
    private val title: String,
    private val number: Double?,
    private val updatedAt: String?,
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        url = "/manga/$slug/chapter/$id"
        name = title
        chapter_number = number?.toFloat() ?: -1f
        date_upload = Instant.tryParse(updatedAt)
    }
}

@Serializable
class ChapterProps(val initialData: InitialData)

@Serializable
class InitialData(val pages: List<String>)
