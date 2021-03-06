/**
 * Copyright (C) 2016-2017 - All rights reserved.
 * This file is part of the telepath project which is released under the GPLv3 license.
 * See file LICENSE.txt or go to http://www.gnu.org/licenses/gpl.txt for full license details.
 * You may use, distribute and modify this code under the terms of the GPLv3 license.
 */

package com.github.giedomak.telepath.staticparser.rpq

import com.github.giedomak.telepath.datamodels.Query
import com.github.giedomak.telepath.datamodels.plans.LogicalPlan
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.misc.NotNull
import rpq.RPQBaseVisitor
import rpq.RPQLexer
import rpq.RPQParser

/**
 * Parse the RPQ ANTLR4 AST into our internal LogicalPlan model.
 *
 * Auto-generated ANTLR files are stored in `target/generated-sources/antlr4`.
 *
 * We use the Visitor approach instead of the Listener approach.
 * Inspiration: http://jakubdziworski.github.io/java/2016/04/01/antlr_visitor_vs_listener.html
 */
class RPQVisitorParser : RPQBaseVisitor<LogicalPlan>() {

    /**
     * This is the main method which we need to call if we want to parse a RPQ.
     *
     * Here we define to start parsing the input using the query() rule.
     *
     * @param RPQSourceCode The complete RPQ formatted as a String
     * @return LogicalPlan The complete LogicalPlan after recursively traversing the complete ANTLR AST.
     */
    fun parse(query: Query): LogicalPlan {

        // Setup the lexer and parser
        val lexer = RPQLexer(ANTLRInputStream(query.input))
        val parser = RPQParser(CommonTokenStream(lexer))

        // Here we define to start parsing our query with the query() rule
        val RPQQueryVisitor = RPQQueryVisitor(query)
        return RPQQueryVisitor.visit(parser.query())
    }

    /**
     * Our query visitor inline class.
     *
     * This class extends the RPQBaseVisitor class and contains the
     * appropriate visitor methods, "listeners". These methods will be "fired" if we visit one of the
     * rules from the RPQ grammar while visiting the ANTLR LogicalPlan. The overridden methods from
     * RPQBaseVisitor allow us to check if we are dealing with unary operators like Kleene star or
     * Plus, or binary operators like Union or Concatenation for example.
     */
    private class RPQQueryVisitor(val query: Query) : RPQBaseVisitor<LogicalPlan>() {

        override fun visitUnaryExpression(@NotNull ctx: RPQParser.UnaryExpressionContext): LogicalPlan {

            val result = LogicalPlan(query)

            if (ctx.unaryOperator().PLUS() != null) {
                // PLUS
                result.operator = LogicalPlan.PLUS
            } else if (ctx.unaryOperator().KLEENE_STAR() != null) {
                // KLEENE STAR
                result.operator = LogicalPlan.KLEENE_STAR
            }

            // Recurse on the left-side for which this operator was intended
            result.setChild(0, visit(ctx.query()))

            return result
        }

        override fun visitBinaryExpression(@NotNull ctx: RPQParser.BinaryExpressionContext): LogicalPlan {

            val result = LogicalPlan(query)

            if (ctx.binaryOperator().UNION() != null) {
                // UNION
                result.operator = LogicalPlan.UNION
            } else if (ctx.binaryOperator().CONJUNCTION() != null) {
                // CONCATENATION
                result.operator = LogicalPlan.CONCATENATION
            }

            // Recurse on the left-side and the right-side for which this operator was intended
            result.setChild(0, visit(ctx.query(0))) // First occurence of query
            result.setChild(1, visit(ctx.query(1))) // Second occurence of query

            return result
        }

        override fun visitLeaf(@NotNull ctx: RPQParser.LeafContext): LogicalPlan {

            val result = LogicalPlan(query, LogicalPlan.LEAF)
            result.setLeaf(ctx.LABEL().text)

            return result
        }

        override fun visitParenthesis(@NotNull ctx: RPQParser.ParenthesisContext): LogicalPlan {
            return visit(ctx.query())
        }
    }
}
