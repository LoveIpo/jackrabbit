/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.query.qom;

import org.apache.jackrabbit.name.NamePathResolver;
import org.apache.jackrabbit.name.QName;

import org.apache.jackrabbit.core.query.jsr283.qom.NodeLocalName;

/**
 * <code>NodeLocalNameImpl</code>...
 */
public class NodeLocalNameImpl
        extends DynamicOperandImpl
        implements NodeLocalName {

    /**
     * The name of the selector against which to evaluate this operand.
     */
    private final QName selectorName;

    NodeLocalNameImpl(NamePathResolver resolver, QName selectorName) {
        super(resolver);
        this.selectorName = selectorName;
    }

    /**
     * Gets the name of the selector against which to evaluate this operand.
     *
     * @return the selector name; non-null
     */
    public String getSelectorName() {
        return getJCRName(selectorName);
    }

    //------------------------< AbstractQOMNode >-------------------------------

    /**
     * Accepts a <code>visitor</code> and calls the appropriate visit method
     * depending on the type of this QOM node.
     *
     * @param visitor the visitor.
     */
    public void accept(QOMTreeVisitor visitor, Object data) {
        visitor.visit(this, data);
    }
}
