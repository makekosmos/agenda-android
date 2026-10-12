package com.mundus.agenda

import org.junit.Assert.assertEquals
import org.junit.Test

class ListOptionsTest {
    private val tasks =
        listOf(
            Task("a", "Бета", priority = 1, projectId = "p2", sortOrder = 0),
            Task("b", "альфа", priority = 3, isCompleted = true, sortOrder = 1),
            Task("c", "Гамма", priority = 3, projectId = "p1", sortOrder = 2),
            Task("d", "Дельта альфа", projectId = "gone", sortOrder = 3),
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
        assertEquals("bcad", ids(groupTasks(board, live, GroupKey.PROJECT)))
    }

    @Test
    fun groupsByDateAscendingWithUndatedLast() {
        assertEquals("badc", ids(groupTasks(board, live, GroupKey.DATE)))
    }

    @Test
    fun noGroupingKeepsOrder() {
        assertEquals("abcd", ids(groupTasks(board, live, GroupKey.NONE)))
    }

    @Test
    fun searchFindsAllMatchingTitlesOpenFirstAndSkipsTrash() {
        assertEquals("db", ids(searchTasks(board, "  АЛЬФА ")))
    }

    @Test
    fun emptySearchListsEveryLiveTaskOpenFirst() {
        assertEquals("acdb", ids(searchTasks(board, " ")))
    }

    @Test
    fun searchForgivesOneTypoAndYo() {
        assertEquals("db", ids(searchTasks(board, "алфа")))
        val yo = board.copy(todos = listOf(Task("y", "Купить ёлку", sortOrder = 0)))
        assertEquals("y", ids(searchTasks(yo, "елку")))
        assertEquals("y", ids(searchTasks(yo, "ЁЛКУ")))
    }
}
