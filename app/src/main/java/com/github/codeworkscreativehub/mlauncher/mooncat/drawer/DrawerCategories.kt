package com.github.codeworkscreativehub.mlauncher.mooncat.drawer

import com.squareup.moshi.JsonClass

/** A category the user created. Its id in assignments is [ID_PREFIX_USER] + [id]. */
@JsonClass(generateAdapter = true)
data class UserCategory(val id: Long, val name: String)

@JsonClass(generateAdapter = true)
data class CategoryData(
    val schemaVersion: Int = 1,
    val categories: List<UserCategory> = emptyList(),
    /** Package name to category id. A manual choice overrides the automatic category. */
    val assignments: Map<String, String> = emptyMap()
)

/**
 * Automatic categories come from the category each app declares to Android
 * (ApplicationInfo.category), so they are computed on the device without any lookup.
 */
enum class AutoCategory(val androidValue: Int) {
    GAMES(0), AUDIO(1), VIDEO(2), IMAGES(3), SOCIAL(4), NEWS(5), MAPS(6), PRODUCTIVITY(7), ACCESSIBILITY(8), OTHER(-1);

    val id: String get() = DrawerCategories.ID_PREFIX_AUTO + name

    companion object {
        fun fromAndroid(value: Int): AutoCategory = entries.firstOrNull { it.androidValue == value && it != OTHER } ?: OTHER
    }
}

object DrawerCategories {
    const val ID_PREFIX_AUTO = "auto:"
    const val ID_PREFIX_USER = "user:"
    const val MAX_CATEGORIES = 30
    const val MAX_NAME_LENGTH = 30
    const val MAX_ASSIGNMENTS = 3000

    fun userId(category: UserCategory) = ID_PREFIX_USER + category.id

    fun normalizeName(name: String): String = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH)

    fun exists(data: CategoryData, id: String): Boolean = when {
        id.startsWith(ID_PREFIX_AUTO) -> AutoCategory.entries.any { it.id == id }
        id.startsWith(ID_PREFIX_USER) -> data.categories.any { userId(it) == id }
        else -> false
    }

    fun isValid(data: CategoryData): Boolean =
        data.schemaVersion == 1 &&
            data.categories.size <= MAX_CATEGORIES &&
            data.categories.all { it.name.isNotBlank() && it.name.length <= MAX_NAME_LENGTH } &&
            data.categories.map { it.id }.toSet().size == data.categories.size &&
            data.categories.map { it.name.lowercase() }.toSet().size == data.categories.size &&
            data.assignments.size <= MAX_ASSIGNMENTS &&
            data.assignments.all { (pkg, id) -> pkg.isNotBlank() && pkg.length <= 255 && exists(data, id) }

    /** The manual choice when it still exists, otherwise the category the app declares. */
    fun categoryOf(data: CategoryData, packageName: String, androidCategory: Int): String =
        data.assignments[packageName]?.takeIf { exists(data, it) } ?: AutoCategory.fromAndroid(androidCategory).id

    /** Returns the new data and the created category, or null when the name is empty, taken or the limit is reached. */
    fun add(data: CategoryData, name: String, id: Long): Pair<CategoryData, UserCategory>? {
        val clean = normalizeName(name)
        if (clean.isEmpty() || data.categories.size >= MAX_CATEGORIES) return null
        if (data.categories.any { it.name.equals(clean, ignoreCase = true) }) return null
        val uniqueId = generateSequence(id) { it + 1 }.first { candidate -> data.categories.none { it.id == candidate } }
        val created = UserCategory(uniqueId, clean)
        return data.copy(categories = data.categories + created) to created
    }

    fun rename(data: CategoryData, id: Long, name: String): CategoryData? {
        val clean = normalizeName(name)
        if (clean.isEmpty() || data.categories.any { it.id != id && it.name.equals(clean, ignoreCase = true) }) return null
        return data.copy(categories = data.categories.map { if (it.id == id) it.copy(name = clean) else it })
    }

    /** Deleting a category sends its apps back to their automatic category. */
    fun delete(data: CategoryData, id: Long): CategoryData {
        val removed = ID_PREFIX_USER + id
        return data.copy(
            categories = data.categories.filterNot { it.id == id },
            assignments = data.assignments.filterValues { it != removed }
        )
    }

    /**
     * Makes exactly [packages] the members of [categoryId]. Apps removed from it go back to
     * their automatic category.
     */
    fun setMembers(data: CategoryData, categoryId: String, packages: Set<String>): CategoryData {
        if (!exists(data, categoryId)) return data
        val kept = data.assignments.filter { (pkg, id) -> id != categoryId && pkg !in packages }
        val next = kept + packages.associateWith { categoryId }
        return if (next.size > MAX_ASSIGNMENTS) data else data.copy(assignments = next)
    }

    /** Puts one app in [categoryId], or back in its automatic category when it is null. */
    fun assign(data: CategoryData, packageName: String, categoryId: String?): CategoryData = when {
        categoryId == null -> data.copy(assignments = data.assignments - packageName)
        !exists(data, categoryId) -> data
        packageName !in data.assignments && data.assignments.size >= MAX_ASSIGNMENTS -> data
        else -> data.copy(assignments = data.assignments + (packageName to categoryId))
    }

    /** Drops manual choices that now equal the automatic category, keeping the stored data small. */
    fun compact(data: CategoryData, androidCategories: Map<String, Int>): CategoryData = data.copy(
        assignments = data.assignments.filter { (pkg, id) ->
            val auto = androidCategories[pkg]?.let { AutoCategory.fromAndroid(it).id }
            auto == null || auto != id
        }
    )
}
