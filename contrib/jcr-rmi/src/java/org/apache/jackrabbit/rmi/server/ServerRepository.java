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
package org.apache.jackrabbit.rmi.server;

import java.rmi.RemoteException;

import javax.jcr.Credentials;
import javax.jcr.LoginException;
import javax.jcr.NoSuchWorkspaceException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.rmi.remote.RemoteRepository;
import org.apache.jackrabbit.rmi.remote.RemoteSession;

/**
 * Remote adapter for the JCR {@link javax.jcr.Repository Repository}
 * interface. This class makes a local repository available as an RMI service
 * using the
 * {@link org.apache.jackrabbit.rmi.remote.RemoteRepository RemoteRepository}
 * interface.
 * 
 * @author Jukka Zitting
 * @see javax.jcr.Repository
 * @see org.apache.jackrabbit.rmi.remote.RemoteRepository
 */
public class ServerRepository extends ServerObject implements RemoteRepository {
    
    /** The adapted local repository. */
    protected Repository repository;
    
    /**
     * Creates a remote adapter for the given local repository.
     * 
     * @param repository local repository
     * @param factory remote adapter factory
     * @throws RemoteException on RMI errors
     */
    public ServerRepository(Repository repository,
            RemoteAdapterFactory factory) throws RemoteException {
        super(factory);
        this.repository = repository;
    }
    
    /** {@inheritDoc} */
    public String getDescriptor(String name) throws RemoteException {
        return repository.getDescriptor(name);
    }
    
    /** {@inheritDoc} */
    public String[] getDescriptorKeys() throws RemoteException {
        return repository.getDescriptorKeys();
    }
    
    /** {@inheritDoc} */
    public RemoteSession login() throws LoginException,
            NoSuchWorkspaceException, RepositoryException, RemoteException {
        try {
            Session session = repository.login();
            return factory.getRemoteSession(session);
        } catch (RepositoryException ex) {
            throw getRepositoryException(ex);
        }
    }
    
    /** {@inheritDoc} */
    public RemoteSession login(String workspace) throws LoginException,
            NoSuchWorkspaceException, RepositoryException, RemoteException {
        try {
            Session session = repository.login(workspace);
            return factory.getRemoteSession(session);
        } catch (RepositoryException ex) {
            throw getRepositoryException(ex);
        }
    }

    /** {@inheritDoc} */
    public RemoteSession login(Credentials credentials) throws LoginException,
            NoSuchWorkspaceException, RepositoryException, RemoteException {
        try {
            Session session = repository.login(credentials);
            return factory.getRemoteSession(session);
        } catch (RepositoryException ex) {
            throw getRepositoryException(ex);
        }
    }
    
    /** {@inheritDoc} */
    public RemoteSession login(Credentials credentials, String workspace)
            throws LoginException, NoSuchWorkspaceException,
            RepositoryException, RemoteException {
        try {
            Session session = repository.login(credentials, workspace);
            return factory.getRemoteSession(session);
        } catch (RepositoryException ex) {
            throw getRepositoryException(ex);
        }
    }

}
