/**
 * Copyright (C) 2016-2017 - All rights reserved.
 * This file is part of the telepathdb project which is released under the GPLv3 license.
 * See file LICENSE.txt or go to http: *www.gnu.org/licenses/gpl.txt for full license details.
 * You may use, distribute and modify this code under the terms of the GPLv3 license.
 */

package com.telepathdb.datamodels.utilities

import com.telepathdb.datamodels.ParseTree

/**
 * Flatten ParseTrees into multi-trees.
 *
 * We escalate the children from a node to its parent if both the node and its parent have the same operator.
 *
 * Example input:
 *
 *            CONCATENATION
 *          /       |        \
 *         a  CONCATENATION   f
 *              /      \
 *          UNION       e
 *         /    \
 *        b    UNION
 *             /  \
 *            c    d
 *
 * Output:
 * 
 *        CONCATENATION
 *        /   |    |  \
 *       a  UNION  e   f
 *          / | \
 *         b  c  d
 */
object ParseTreeFlattener {

    /**
     * Flatten a given ParseTree recursively into a multi-tree.
     *
     * @param parseTree The given input ParseTree.
     * @return A flattened ParseTree from the given input.
     */
    fun flatten(parseTree: ParseTree): ParseTree {

        // Break recursion once we've found a leaf.
        if (parseTree.isLeaf) return parseTree

        // Since we could be escalating multiple children from the same parent, we have to track these escalations.
        var offset = 0

        for (index in parseTree.children.indices) {

            // Flatten each child recursively
            val child = parseTree.children[index + offset]
            val flattenedChild = flatten(child)

            // The subtree rooted at flattenedChild, should all be flattened now.
            // So escalate its children if the operators match.
            if (child.operatorId == parseTree.operatorId) {

                parseTree.children.removeAt(index + offset)
                parseTree.children.addAll(index + offset, flattenedChild.children)

                offset += flattenedChild.children.size - 1
            }
        }

        // We're done
        return parseTree
    }
}
