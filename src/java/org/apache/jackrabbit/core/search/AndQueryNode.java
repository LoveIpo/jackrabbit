/*
 * Copyright 2004-2005 The Apache Software Foundation or its licensors,
 *                     as applicable.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.search;

import java.util.Iterator;

/**
 * Implements a query node that defines an AND operation between arbitrary
 * other {@link QueryNode}s.
 */
public class AndQueryNode extends NAryQueryNode {

    /**
     * Creates a new <code>AndQueryNode</code> with a <code>parent</code>
     * query node.
     *
     * @param parent the parent of <code>this</code> <code>AndQueryNode</code>.
     */
    public AndQueryNode(QueryNode parent) {
        super(parent);
    }

    /**
     * Creates a new <code>AndQueryNode</code> with a <code>parent</code> query
     * node and <code>operands</code> for <code>this</code>
     * <code>AndQueryNode</code>.
     *
     * @param parent   the parent of <code>this</code> <code>AndQueryNode</code>.
     * @param operands the operands for this AND operation.
     */
    public AndQueryNode(QueryNode parent, QueryNode[] operands) {
        super(parent, operands);
    }

    /**
     * This method can return <code>null</code> to indicate that this
     * <code>AndQueryNode</code> does not contain any operands.
     * @see QueryNode#accept(org.apache.jackrabbit.core.search.QueryNodeVisitor, java.lang.Object)
     */
    public Object accept(QueryNodeVisitor visitor, Object data) {
        return visitor.visit(this, data);
    }

}
