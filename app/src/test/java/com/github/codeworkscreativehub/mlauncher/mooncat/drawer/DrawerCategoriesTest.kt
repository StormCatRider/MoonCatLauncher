package com.github.codeworkscreativehub.mlauncher.mooncat.drawer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerCategoriesTest {

    @Test
    fun automaticCategoryComesFromTheAppDeclaration() {
        val data = CategoryData()
        assertEquals(AutoCategory.GAMES.id, DrawerCategories.categoryOf(data, "a.game", 0))
        assertEquals(AutoCategory.MAPS.id, DrawerCategories.categoryOf(data, "a.map", 6))
        assertEquals(AutoCategory.OTHER.id, DrawerCategories.categoryOf(data, "a.app", -1))
        assertEquals(AutoCategory.OTHER.id, DrawerCategories.categoryOf(data, "a.app", 99))
    }

    @Test
    fun manualCategoriesOverrideAndDeletingReturnsAppsToAutomatic() {
        val (withWork, work) = DrawerCategories.add(CategoryData(), "  Work  ", 1)!!
        assertEquals("Work", work.name)
        assertNull(DrawerCategories.add(withWork, "work", 2)) // names are unique ignoring case
        assertNull(DrawerCategories.add(withWork, "   ", 2))

        val assigned = DrawerCategories.setMembers(withWork, DrawerCategories.userId(work), setOf("a.game", "a.mail"))
        assertEquals(DrawerCategories.userId(work), DrawerCategories.categoryOf(assigned, "a.game", 0))
        assertTrue(DrawerCategories.isValid(assigned))

        val shrunk = DrawerCategories.setMembers(assigned, DrawerCategories.userId(work), setOf("a.mail"))
        assertEquals(AutoCategory.GAMES.id, DrawerCategories.categoryOf(shrunk, "a.game", 0))

        val deleted = DrawerCategories.delete(assigned, work.id)
        assertTrue(deleted.assignments.isEmpty())
        assertEquals(AutoCategory.GAMES.id, DrawerCategories.categoryOf(deleted, "a.game", 0))
    }

    @Test
    fun assignmentsToMissingCategoriesAreIgnoredOrRejected() {
        val dangling = CategoryData(assignments = mapOf("a.app" to "user:42"))
        assertFalse(DrawerCategories.isValid(dangling))
        assertEquals(AutoCategory.OTHER.id, DrawerCategories.categoryOf(dangling, "a.app", -1))
        assertEquals(CategoryData(), DrawerCategories.assign(CategoryData(), "a.app", "user:42"))
        assertEquals(CategoryData(), DrawerCategories.assign(CategoryData(), "a.app", "nonsense"))

        val moved = DrawerCategories.assign(CategoryData(), "a.app", AutoCategory.SOCIAL.id)
        assertEquals(AutoCategory.SOCIAL.id, DrawerCategories.categoryOf(moved, "a.app", -1))
        assertEquals(CategoryData(), DrawerCategories.assign(moved, "a.app", null))
    }

    @Test
    fun renameKeepsNamesUniqueAndCompactDropsRedundantChoices() {
        var data = DrawerCategories.add(CategoryData(), "Work", 1)!!.first
        data = DrawerCategories.add(data, "Study", 2)!!.first
        assertNull(DrawerCategories.rename(data, 2, "WORK"))
        assertEquals("Reading", DrawerCategories.rename(data, 2, "Reading")!!.categories[1].name)

        val redundant = DrawerCategories.assign(data, "a.game", AutoCategory.GAMES.id)
        assertTrue(DrawerCategories.compact(redundant, mapOf("a.game" to 0)).assignments.isEmpty())
    }

    @Test
    fun localSearchIgnoresCaseAndAccentsAndNeedsEveryWord() {
        assertTrue(LocalSearch.matches("cafe", "Comprar CAFÉ molido"))
        assertTrue(LocalSearch.matches("molido cafe", "Comprar café molido"))
        assertFalse(LocalSearch.matches("cafe leche", "Comprar café molido"))
        assertFalse(LocalSearch.matches("c", "Comprar café"))
        assertEquals(LocalSearch.MAX_RESULTS, LocalSearch.search("ta", List(80) { "task $it" }) { it }.size)
    }
}
