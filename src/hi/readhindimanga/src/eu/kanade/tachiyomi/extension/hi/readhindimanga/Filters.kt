package eu.kanade.tachiyomi.extension.hi.readhindimanga

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

internal class SortFilter :
    SelectFilter(
        "Sort",
        "sort",
        arrayOf(
            "Library Order" to "popular",
            "Highest Rating" to "highest_rated",
            "Most Chapters" to "chapters",
            "Recently Synced" to "latest",
            "Title A-Z" to "az",
            "Title Z-A" to "za",
        ),
    )

internal open class SelectFilter(
    label: String,
    private val queryParameter: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>(label, options.map { it.first }.toTypedArray()) {

    fun addToUri(builder: HttpUrl.Builder) {
        val value = options[state].second
        if (value.isNotEmpty()) builder.addQueryParameter(queryParameter, value)
    }
}

internal class TypeFilter :
    SelectFilter(
        "Type",
        "type",
        arrayOf("All" to "", "Manhwa" to "manhwa", "Manhua" to "manhua"),
    )

internal class StatusFilter :
    SelectFilter(
        "Status",
        "status",
        arrayOf("All" to "", "Ongoing" to "ongoing", "Completed" to "completed"),
    )

internal class GenreFilter(genres: List<String>) :
    SelectFilter(
        "Genre",
        "genre",
        arrayOf("All" to "") + genres.map { it to it.lowercase() }.toTypedArray(),
    )
