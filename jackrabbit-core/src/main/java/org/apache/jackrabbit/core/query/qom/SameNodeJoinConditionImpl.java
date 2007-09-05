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
import org.apache.jackrabbit.name.Path;

import org.apache.jackrabbit.core.query.jsr283.qom.SameNodeJoinCondition;

/**
 * <code>SameNodeJoinConditionImpl</code>...
 */
public class SameNodeJoinConditionImpl
        extends JoinConditionImpl
        implements SameNodeJoinCondition {

    /**
     * The name of the first selector.
     */
    private final QName selector1Name;

    /**
     * The name of the second selector.
     */
    private final QName selector2Name;

    /**
     * The path relative to the second selector.
     */
    private final Path selector2Path;

    SameNodeJoinConditionImpl(NamePathResolver resolver,
                              QName selector1Name,
                              QName selector2Name,
                              Path selector2Path) {
        super(resolver);
        this.selector1Name = selector1Name;
        this.selector2Name = selector2Name;
        this.selector2Path = selector2Path;
    }

    /**
     * Gets the name of the first selector.
     *
     * @return the selector name; non-null
     */
    public String getSelector1Name() {
        return getJCRName(selector1Name);
    }

    /**
     * Gets the name of the second selector.
     *
     * @return the selector name; non-null
     */
    public String getSelector2Name() {
        return getJCRName(selector2Name);
    }

    /**
     * Gets the path relative to the second selector.
     *
     * @return the relative path, or null for none
     */
    public String getSelector2Path() {
        return getJCRPath(selector2Path);
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
