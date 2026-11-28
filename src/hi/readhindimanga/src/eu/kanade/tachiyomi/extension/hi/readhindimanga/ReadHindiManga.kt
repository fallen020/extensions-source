package eu.kanade.tachiyomi.extension.hi.readhindimanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class ReadHindiManga : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = browse(browseUrl())

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(browseUrl().newBuilder().addQueryParameter("sort", "latest").build())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = browseUrl().newBuilder()
        if (query.isNotBlank()) url.addQueryParameter("q", query.trim())
        filters.filterIsInstance<SelectFilter>().forEach { it.addToUri(url) }
        return browse(url.build())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (segments.firstOrNull() != "manga" || segments.size !in listOf(2, 4)) return null
        if (segments.size == 4 && segments[2] != "chapter") return null
        return fetchSeries(segments[1])
    }

    private suspend fun browse(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("a[href^=/manga/]:has(h3)").map { card ->
            val href = card.absUrl("href").toHttpUrl()
            SManga.create().apply {
                setUrlWithoutDomain(href.toString())
                title = card.selectFirst("h3")!!.text()
                thumbnail_url = "$baseUrl/api/vault/stream/cover/${href.pathSegments.last()}"
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(
        manga = { if (fetchDetails) getMangaDetails(manga) else manga },
        chapters = { if (fetchChapters) getChapterList(manga) else chapters },
    )

    private suspend fun getMangaDetails(manga: SManga): SManga = fetchSeries(manga.url.substringAfterLast("/")) ?: error("Series payload not found")

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val slug = manga.url.substringAfterLast("/")
        return client.get("$baseUrl/api/vault/manga/$slug", Fetch.XHR)
            .parseAs<MangaDetailDto>().data.chapters
            .map { it.toSChapter(slug) }
            .reversed()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val pages = document.extractNextJs<ChapterProps> {
            it is JsonObject && it["initialData"] is JsonObject
        }?.initialData?.pages.orEmpty()
        return pages.mapIndexed { index, url ->
            Page(index, imageUrl = if (url.startsWith("/")) "$baseUrl$url" else url)
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(browseUrl(), Fetch.DOCUMENT).asJsoup()
        val genres = document.extractNextJs<CatalogProps> {
            it is JsonObject && it["initialManga"] is JsonArray
        }?.initialManga.orEmpty()
            .flatMap { it.genres.orEmpty() }
            .withoutTypeTags()
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
        return genres.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        GenreFilter(data?.parseAs<List<String>>().orEmpty()),
    )

    private suspend fun fetchSeries(slug: String): SManga? {
        val document = client.get("$baseUrl/manga/$slug", Fetch.DOCUMENT).asJsoup()
        val props = document.extractNextJs<SeriesProps> {
            it is JsonObject && it["manga"] is JsonObject && it.containsKey("firstChapter")
        }
        return props?.manga?.toSManga(baseUrl)
    }

    private fun browseUrl() = "$baseUrl/browse".toHttpUrl()
}
