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
package org.apache.jackrabbit.core;

import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.core.persistence.PersistenceManager;
import org.apache.jackrabbit.core.persistence.check.ConsistencyChecker;
import org.apache.jackrabbit.core.persistence.check.ConsistencyReport;
import org.apache.jackrabbit.test.NotExecutableException;

/**
 * <code>TestHelper</code> provides test utility methods.
 */
public class TestHelper {

    /**
     * Shuts down the workspace with the given <code>name</code>.
     *
     * @param name the name of the workspace to shut down.
     * @param repo the repository.
     * @throws RepositoryException if the shutdown fails or there is no
     *                             workspace with the given name.
     */
    public static void shutdownWorkspace(String name, RepositoryImpl repo)
            throws RepositoryException {
        repo.getWorkspaceInfo(name).dispose();
    }

    /**
     * Runs a consistency check on the workspace used by the specified session.
     *
     * @param session the Session accessing the workspace to be checked
     * @param runFix whether to attempt fixup
     * @throws RepositoryException if an error occurs while getting the
     * workspace with the given name.
     * @throws NotExecutableException if the {@link PersistenceManager} does
     * not implement {@link ConsistencyChecker}, or if the associated
     * {@link Repository} is not a {@link RepositoryImpl}.
     */
    public static ConsistencyReport checkConsistency(Session session, boolean runFix)
            throws NotExecutableException, RepositoryException {
        Repository r = session.getRepository();
        if (!(r instanceof RepositoryImpl)) {
            throw new NotExecutableException();
        } else {
            RepositoryImpl ri = (RepositoryImpl) r;
            PersistenceManager pm = ri.getWorkspaceInfo(
                    session.getWorkspace().getName()).getPersistenceManager();
            if (!(pm instanceof ConsistencyChecker)) {
                throw new NotExecutableException();
            } else {
                return ((ConsistencyChecker) pm).check(null, true, runFix);
            }
        }
    }

    /**
     * Runs a consistency check on the versioning store used by the specified session.
     *
     * @param session the Session accessing the workspace to be checked
     * @param runFix whether to attempt fixup
     * @throws RepositoryException
     * @throws NotExecutableException if the {@link PersistenceManager} does
     * not implement {@link ConsistencyChecker}, or if the associated
     * {@link Repository} is not a {@link RepositoryImpl}.
     */
    public static ConsistencyReport checkVersionStoreConsistency(Session session, boolean runFix)
            throws NotExecutableException, RepositoryException {
        Repository r = session.getRepository();
        if (!(r instanceof RepositoryImpl)) {
            throw new NotExecutableException();
        } else {
            RepositoryImpl ri = (RepositoryImpl) r;
            PersistenceManager pm = ri.getRepositoryContext()
                    .getInternalVersionManager().getPersistenceManager();
            if (!(pm instanceof ConsistencyChecker)) {
                throw new NotExecutableException();
            } else {
                return ((ConsistencyChecker) pm).check(null, true, runFix);
            }
        }
    }
}
