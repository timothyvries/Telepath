/**
 * Copyright (C) 2016-2017 - All rights reserved.
 * This file is part of the telepathdb project which is released under the GPLv3 license.
 * See file LICENSE.txt or go to http://www.gnu.org/licenses/gpl.txt for full license details.
 * You may use, distribute and modify this code under the terms of the GPLv3 license.
 */

package com.github.giedomak.telepathdb

import com.github.giedomak.telepathdb.cardinalityestimation.KPathIndexCardinalityEstimation
import com.github.giedomak.telepathdb.datamodels.PathTest
import com.github.giedomak.telepathdb.datamodels.Query
import com.github.giedomak.telepathdb.datamodels.parsetree.ParseTree
import com.github.giedomak.telepathdb.datamodels.parsetree.ParseTreeTest
import com.github.giedomak.telepathdb.datamodels.parsetree.PhysicalPlan
import com.github.giedomak.telepathdb.datamodels.parsetree.PhysicalPlanTest
import com.github.giedomak.telepathdb.datamodels.stores.PathIdentifierStore
import com.github.giedomak.telepathdb.evaluationengine.EvaluationEngine
import com.github.giedomak.telepathdb.planner.Planner
import com.github.giedomak.telepathdb.staticparser.StaticParserRPQ
import com.nhaarman.mockito_kotlin.*
import org.hamcrest.CoreMatchers.containsString
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class TelepathDBTest {

    // Need to test console output (System.out)
    private val outContent = ByteArrayOutputStream()
    private val stdout = System.out

    @Before
    fun setUpStreams() {
        System.setOut(PrintStream(outContent))
    }

    @After
    fun cleanUpStreams() {
        System.setOut(stdout)
    }

    @Test
    fun producesResults() {

        // Mock TelepathDB
        val telepathDB = spy<TelepathDB>()

        // Let's do an end-to-end test for:
        //        CONCATENATION
        //           /   \
        //          a     b
        val input = ParseTreeTest.create1LevelParseTree(ParseTree.CONCATENATION, listOf("a", "b"), Query(telepathDB, ""))
        val physicalPlan = PhysicalPlanTest.generatePhysicalPlan(PhysicalPlan.INDEXLOOKUP, listOf("a", "b"))
        // Catch the pathId of `a - b`
        val pathId = PathIdentifierStore.getPathIdByEdgeLabel(listOf("a", "b"))

        // Our expected results
        val expectedPaths = listOf(
                PathTest.simplePath(pathId, 3, 42),
                PathTest.simplePath(pathId, 3, 45)
        )

        // Mocking all the modules we'll use
        val staticParser = mock<StaticParserRPQ> {
            on { parse(any()) }.doReturn(input)
        }
        val evaluationEngine = mock<EvaluationEngine> {
            on { evaluate(physicalPlan) }.doReturn(expectedPaths.stream())
        }
        val cardinalityEstimationMock = mock<KPathIndexCardinalityEstimation> {
            on { getCardinality(any<PhysicalPlan>()) }.doReturn(20)
        }

        // Set all the mocks
        telepathDB.staticParser = staticParser
        telepathDB.evaluationEngine = evaluationEngine
        telepathDB.cardinalityEstimation = cardinalityEstimationMock
        // Don't wait for a second user input --> throw exception
        doReturn("a/b").doThrow(IllegalArgumentException()).whenever(telepathDB).getUserInput(any())

        // Since our second input will throw an exception, we'll catch it.
        try {
            telepathDB.start()
        } catch (e: IllegalArgumentException) {
        }

        // Assert that we have both paths printed in the results
        for (path in expectedPaths) {
            assertThat(outContent.toString(), containsString(path.toString()))
        }
    }
}
