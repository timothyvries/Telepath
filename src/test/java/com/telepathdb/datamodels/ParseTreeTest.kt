package com.telepathdb.datamodels

import org.junit.Test
import java.util.stream.Collectors
import java.util.stream.IntStream
import org.junit.Assert.assertEquals

class ParseTreeTest {

    @Test
    fun postOrderTreeWalk() {
        // given
        // Root has id 1, its three children will have ids 2, 3 and 4.
        val root = ParseTree(true)
        IntStream.range(0, 3).forEach { root.children!!.add(ParseTree()) }

        // The middle child will get two more childs with ids 5 and 6
        IntStream.range(0, 2).forEach { root.getChild(1)!!.children!!.add(ParseTree()) }

        //          1
        //        / | \
        //       2  3  4
        //         / \
        //        5   6

        // We expect the post-order tree-walk to report 2, 5, 6, 3, 4, 1
        val expected = listOf(2, 5, 6, 3, 4, 1).toString()
        val actual = root.postOrderTreeWalk().map { it.id }.collect(Collectors.toList()).toString()

        // then
        assertEquals(expected, actual)
    }

}
