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
package org.apache.jackrabbit.test.search;

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import java.util.Calendar;

/**
 * Performs various query test cases.
 */
public class SimpleQueryTest extends AbstractQueryTest {


    public void testSimpleQuerySQL1() throws Exception {
        Node foo = testRootNode.addNode("foo", NT_UNSTRUCTURED);
        foo.setProperty("bla", new String[]{"bla"});

        testRootNode.save();

        String sql = "SELECT * FROM nt:base"
                + " WHERE jcr:path LIKE '" + testRoot + "/foo'"
                + " AND bla = 'bla'";
        Query q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        QueryResult result = q.execute();
        checkResult(result, 1);
    }

    public void testSimpleQuerySQL2() throws Exception {
        Node foo = testRootNode.addNode("foo", NT_UNSTRUCTURED);
        foo.setProperty("bla", new String[]{"bla"});
        Node bla = testRootNode.addNode("bla", NT_UNSTRUCTURED);
        bla.setProperty("bla", new String[]{"bla"});

        superuser.getRootNode().save();

        String sql = "SELECT * FROM nt:file" +
                " WHERE jcr:path LIKE '" + testRoot + "/%'"
                + " AND bla = 'bla'";
        Query q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        QueryResult result = q.execute();
        checkResult(result, 0);
    }

    public void testSimpleQuerySQL3() throws Exception {
        Node foo = testRootNode.addNode("foo", NT_UNSTRUCTURED);
        foo.setProperty("bla", new String[]{"bla"});
        Node bla = testRootNode.addNode("bla", NT_UNSTRUCTURED);
        bla.setProperty("bla", new String[]{"bla"});

        testRootNode.save();

        String sql = "SELECT * FROM nt:unstructured" +
                " WHERE jcr:path LIKE '" + testRoot + "/%'"
                + " AND bla = 'bla'";
        Query q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        QueryResult result = q.execute();
        checkResult(result, 2);
    }

    public void testSimpleQuerySQL4() throws Exception {
        Node foo = testRootNode.addNode("foo", NT_UNSTRUCTURED);
        foo.setProperty("bla", new String[]{"bla"});
        Node bla = testRootNode.addNode("bla", NT_UNSTRUCTURED);
        bla.setProperty("bla", new String[]{"bla"});

        testRootNode.save();

        String sql = "SELECT * FROM nt:unstructured WHERE jcr:path LIKE '" + testRoot + "/%'";
        Query q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        QueryResult result = q.execute();
        checkResult(result, 2);
    }

    public void testDateField1() throws Exception {
        Node n = testRootNode.addNode("marcel", NT_UNSTRUCTURED);
        Calendar marcel = Calendar.getInstance();
        marcel.set(1976, 4, 20, 15, 40);
        n.setProperty("birth", new Value[]{new DateValue(marcel)});

        n = testRootNode.addNode("vanessa", NT_UNSTRUCTURED);
        Calendar vanessa = Calendar.getInstance();
        vanessa.set(1975, 4, 10, 13, 30);
        n.setProperty("birth", new Value[]{new DateValue(vanessa)});

        testRootNode.save();

        String sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND birth > TIMESTAMP '1976-01-01T00:00:00.000+01:00'";
        Query q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        QueryResult result = q.execute();
        checkResult(result, 1);

        sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND birth > TIMESTAMP '1975-01-01T00:00:00.000+01:00'";
        q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        result = q.execute();
        checkResult(result, 2);
    }

    public void testDoubleField() throws Exception {
        Node n = testRootNode.addNode("node1", NT_UNSTRUCTURED);
        n.setProperty("value", new Value[]{new DoubleValue(1.9928375d)});
        n = testRootNode.addNode("node2", NT_UNSTRUCTURED);
        n.setProperty("value", new Value[]{new DoubleValue(0.0d)});
        n = testRootNode.addNode("node3", NT_UNSTRUCTURED);
        n.setProperty("value", new Value[]{new DoubleValue(-1.42982475d)});

        testRootNode.save();

        String sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value > 0.1e-0";
        Query q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        QueryResult result = q.execute();
        checkResult(result, 1);

        sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value > -0.1";
        q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        result = q.execute();
        checkResult(result, 2);

        sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value > -1.5";
        q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        result = q.execute();
        checkResult(result, 3);
    }

    public void testLongField() throws Exception {
        Node n = testRootNode.addNode("node1", NT_UNSTRUCTURED);
        n.setProperty("value", new Value[]{new LongValue(1)});
        n = testRootNode.addNode("node2", NT_UNSTRUCTURED);
        n.setProperty("value", new Value[]{new LongValue(0)});
        n = testRootNode.addNode("node3", NT_UNSTRUCTURED);
        n.setProperty("value", new Value[]{new LongValue(-1)});

        testRootNode.save();

        String sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value > 0";
        Query q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        QueryResult result = q.execute();
        checkResult(result, 1);

        sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value > -1";
        q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        result = q.execute();
        checkResult(result, 2);

        sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value > -2";
        q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        result = q.execute();
        checkResult(result, 3);
    }

    public void testLikePattern() throws Exception {
        Node n = testRootNode.addNode("node1", NT_UNSTRUCTURED);
        n.setProperty("value", new String[]{"king"});
        n = testRootNode.addNode("node2", NT_UNSTRUCTURED);
        n.setProperty("value", new String[]{"ping"});
        n = testRootNode.addNode("node3", NT_UNSTRUCTURED);
        n.setProperty("value", new String[]{"ching"});

        testRootNode.save();

        String sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value LIKE 'ping'";
        Query q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        QueryResult result = q.execute();
        checkResult(result, 1);

        sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value LIKE '_ing'";
        q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        result = q.execute();
        checkResult(result, 2);

        sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value LIKE '%ing'";
        q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        result = q.execute();
        checkResult(result, 3);
    }

    public void testLikePatternBetween() throws Exception {
        Node n = testRootNode.addNode("node1", NT_UNSTRUCTURED);
        n.setProperty("value", new String[]{"ping"});
        n = testRootNode.addNode("node2", NT_UNSTRUCTURED);
        n.setProperty("value", new String[]{"pong"});
        n = testRootNode.addNode("node3", NT_UNSTRUCTURED);
        n.setProperty("value", new String[]{"puung"});

        testRootNode.save();

        String sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value LIKE 'ping'";
        Query q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        QueryResult result = q.execute();
        checkResult(result, 1);

        sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value LIKE 'p_ng'";
        q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        result = q.execute();
        checkResult(result, 2);

        sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value LIKE 'p%ng'";
        q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        result = q.execute();
        checkResult(result, 3);
    }

    public void testLikePatternEnd() throws Exception {
        Node n = testRootNode.addNode("node1", NT_UNSTRUCTURED);
        n.setProperty("value", new String[]{"bli"});
        n = testRootNode.addNode("node2", NT_UNSTRUCTURED);
        n.setProperty("value", new String[]{"bla"});
        n = testRootNode.addNode("node3", NT_UNSTRUCTURED);
        n.setProperty("value", new String[]{"blub"});

        testRootNode.save();

        String sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value LIKE 'bli'";
        Query q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        QueryResult result = q.execute();
        checkResult(result, 1);

        sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value LIKE 'bl_'";
        q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        result = q.execute();
        checkResult(result, 2);

        sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value LIKE 'bl%'";
        q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        result = q.execute();
        checkResult(result, 3);
    }

    public void testLikePatternEscaped() throws Exception {
        Node n = testRootNode.addNode("node1", NT_UNSTRUCTURED);
        n.setProperty("value", new String[]{"foo\\_bar"});
        n = testRootNode.addNode("node2", NT_UNSTRUCTURED);
        n.setProperty("value", new String[]{"foobar"});
        n = testRootNode.addNode("node3", NT_UNSTRUCTURED);
        n.setProperty("value", new String[]{"foo_bar"});
        n = testRootNode.addNode("node4", NT_UNSTRUCTURED);
        n.setProperty("value", new String[]{"foolbar"});

        testRootNode.save();

        String sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value LIKE 'foo\\_bar' ESCAPE '\\'"; // matches node3
        Query q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        QueryResult result = q.execute();
        checkResult(result, 1);

        sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value LIKE 'foo_bar'";    // matches node3 and node4
        q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        result = q.execute();
        checkResult(result, 2);

        sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value LIKE 'foo%bar'";  // matches all nodes
        q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        result = q.execute();
        checkResult(result, 4);

        sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value LIKE 'foo\\\\\\_bar' ESCAPE '\\'";  // matches node1
        q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        result = q.execute();
        checkResult(result, 1);

        sql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value LIKE 'foo\\_bar'";  // matches node1
        q = superuser.getWorkspace().getQueryManager().createQuery(sql, "sql");
        result = q.execute();
        checkResult(result, 1);
    }

    public void testNotEqual() throws Exception {
        Node n = testRootNode.addNode("node1", NT_UNSTRUCTURED);
        n.setProperty("value", new String[]{"foo"});
        n = testRootNode.addNode("node2", NT_UNSTRUCTURED);
        n.setProperty("value", new String[]{"bar"});
        n = testRootNode.addNode("node3", NT_UNSTRUCTURED);
        n.setProperty("value", new String[]{"foobar"});

        testRootNode.save();

        String jcrql = "SELECT * FROM nt:base WHERE jcr:path LIKE '" + testRoot + "/%' AND value <> 'bar'";
        Query q = superuser.getWorkspace().getQueryManager().createQuery(jcrql, "sql");
        QueryResult result = q.execute();
        checkResult(result, 2);

    }

}
