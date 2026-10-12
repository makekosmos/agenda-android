package com.mundus.agenda

import org.junit.Assert.assertEquals
import org.junit.Test

class ListOptionsTest {
    private val tasks =
        listOf(
            Task("a", "Бета", priority = 1, projectId = "p2", sortOrder = 0),
            Task("b", "альфа", priority = 3, sortOrder = 1),
            Task("c", "Гамма", priority = 3, projectId = "p1", sortOrder = 2),
            Task("d", "Дельта", projectId = "gone", sortOrder = 3),
            Task("e", "Альфа старая", isTrashed = true, sortOrder = 4),
        )
    private val board =
        Board(
            todos = tasks,
            projects = listOf(Project("p1", "Дом"), Project("p2", "Работа альфа")),
            dateViews =
                mapOf(
                    "a" to TaskDateView("2026-10-14", "14 окт"),
                    "b" to TaskDateView("2026-10-12", "Сегодня"),
                    "c" to TaskDateView(null, "Без даты"),
                    "d" to TaskDateView("2026-10-14", "14 окт"),
                ),
        )
    private val live = tasks.filter { !it.isTrashed }

    private fun ids(list: List<Task>) = list.joinToString("") { it.id }

    @Test
    fun sortsLikeDesktop() {
        assertEquals("abcd", ids(sortTasks(board, live, SortKey.DEFAULT)))
        assertEquals("badc", ids(sortTasks(board, live, SortKey.DATE)))
        assertEquals("bcad", ids(sortTasks(board, live, SortKey.PRIORITY)))
        assertEquals("bacd", ids(sortTasks(board, live, SortKey.TITLE)))
    }

    @Test
    fun groupsByProjectWithNoProjectFirstAndOrphansLast() {
        val groups = groupTasks(board, live, GroupKey.PROJECT)
        assertEquals(listOf("Без проекта", "Дом", "Работа альфа", "Проект"), groups.map { it.label })
        assertEquals(listOf("b", "c", "a", "d"), groups.map { ids(it.tasks) })
    }

    @Test
    fun groupsByDateAscendingWithUndatedLast() {
        val groups = groupTasks(board, live, GroupKey.DATE)
        assertEquals(listOf("Сегодня", "14 окт", "Без даты"), groups.map { it.label })
        assertEquals(listOf("b", "ad", "c"), groups.map { ids(it.tasks) })
    }

    @Test
    fun noGroupingKeepsOneHeaderlessRun() {
        assertEquals(listOf(TaskGroup(null, live)), groupTasks(board, live, GroupKey.NONE))
    }

    @Test
    fun searchMatchesTitlesCaseInsensitivelyAndSkipsTrash() {
        val (found, projects) = searchBoard(board, "  АЛЬФА ")
        assertEquals("b", ids(found))
        assertEquals(listOf("p2"), projects.map { it.id })
        assertEquals(emptyList<Task>() to emptyList<Project>(), searchBoard(board, " "))
    }
}
