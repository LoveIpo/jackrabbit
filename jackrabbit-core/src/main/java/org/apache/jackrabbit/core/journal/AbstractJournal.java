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
package org.apache.jackrabbit.core.journal;

import EDU.oswego.cs.dl.util.concurrent.ReadWriteLock;
import EDU.oswego.cs.dl.util.concurrent.WriterPreferenceReadWriteLock;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.jackrabbit.name.NamespaceResolver;

/**
 * Base implementation for a journal.
 */
public abstract class AbstractJournal implements Journal {

    /**
     * Logger.
     */
    private static Logger log = LoggerFactory.getLogger(AbstractJournal.class);

    /**
     * Journal id.
     */
    private String id;

    /**
     * Namespace resolver.
     */
    private NamespaceResolver resolver;

    /**
     * Map of registered consumers.
     */
    private final Map consumers = new HashMap();

    /**
     * Map of registered producers.
     */
    private final Map producers = new HashMap();

    /**
     * Read
     */
    private final ReadWriteLock rwLock = new WriterPreferenceReadWriteLock();

    /**
     * {@inheritDoc}
     */
    public void init(String id, NamespaceResolver resolver) throws JournalException {
        this.id = id;
        this.resolver = resolver;
    }

    /**
     * {@inheritDoc}
     */
    public void register(RecordConsumer consumer) throws JournalException {
        synchronized (consumers) {
            String consumerId = consumer.getId();
            if (consumers.containsKey(consumerId)) {
                String msg = "Record consumer with identifier '" +
                        consumerId + "' already registered.";
                throw new JournalException(msg);
            }
            consumers.put(consumerId, consumer);
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean unregister(RecordConsumer consumer) {
        synchronized (consumers) {
            String consumerId = consumer.getId();
            return consumers.remove(consumerId) != null;
        }
    }

    /**
     * Return the consumer given its identifier.
     *
     * @param identifier identifier
     * @return consumer associated with identifier;
     *         <code>null</code> if no consumer is associated with identifier
     */
    public RecordConsumer getConsumer(String identifier) {
        synchronized (consumers) {
            return (RecordConsumer) consumers.get(identifier);
        }
    }

    /**
     * {@inheritDoc}
     */
    public RecordProducer getProducer(String identifier) {
        synchronized (producers) {
            RecordProducer producer = (RecordProducer) producers.get(identifier);
            if (producer == null) {
                producer = createProducer(identifier);
                producers.put(identifier, producer);
            }
            return producer;
        }
    }

    /**
     * Create the record producer for a given identifier. May be overridden
     * by subclasses.
     *
     * @param identifier producer identifier
     */
    protected RecordProducer createProducer(String identifier) {
        return new DefaultRecordProducer(this, identifier);
    }

    /**
     * Return the minimal revision of all registered consumers.
     */
    private long getMinimalRevision() {
        long minimalRevision = Long.MAX_VALUE;

        synchronized (consumers) {
            Iterator iter = consumers.values().iterator();
            while (iter.hasNext()) {
                RecordConsumer consumer = (RecordConsumer) iter.next();
                if (consumer.getRevision() < minimalRevision) {
                    minimalRevision = consumer.getRevision();
                }
            }
        }
        return minimalRevision;
    }

    /**
     * {@inheritDoc}
     */
    public void sync() throws JournalException {
        try {
            rwLock.readLock().acquire();
        } catch (InterruptedException e) {
            String msg = "Unable to acquire read lock.";
            throw new JournalException(msg, e);
        }
        try {
            doSync(getMinimalRevision());
        } finally {
            rwLock.readLock().release();
        }
    }

    /**
     * Synchronize contents from journal. May be overridden by subclasses.
     *
     * @param startRevision start point (exlusive)
     * @throws JournalException if an error occurs
     */
    protected void doSync(long startRevision) throws JournalException {
        RecordIterator iterator = getRecords(startRevision);
        long stopRevision = Long.MIN_VALUE;

        try {
            while (iterator.hasNext()) {
                Record record = iterator.nextRecord();
                if (record.getJournalId().equals(id)) {
                    log.info("Record with revision '" + record.getRevision() +
                            "' created by this journal, skipped.");
                } else {
                    RecordConsumer consumer = getConsumer(record.getProducerId());
                    if (consumer != null) {
                        consumer.consume(record);
                    }
                }
                stopRevision = record.getRevision();
            }
        } finally {
            iterator.close();
        }

        if (stopRevision > 0) {
            Iterator iter = consumers.values().iterator();
            while (iter.hasNext()) {
                RecordConsumer consumer = (RecordConsumer) iter.next();
                consumer.setRevision(stopRevision);
            }
            log.info("Synchronized to revision: " + stopRevision);
        }
    }

    /**
     * Return an iterator over all records after the specified revision.
     *
     * @param startRevision start point (exlusive)
     * @throws JournalException if an error occurs
     */
    protected abstract RecordIterator getRecords(long startRevision)
            throws JournalException;

    /**
     * Lock the journal revision, disallowing changes from other sources until
     * {@link #unlock has been called, and synchronizes to the latest change.
     *
     * @throws JournalException if an error occurs
     */
    public void lockAndSync() throws JournalException {
        try {
            rwLock.writeLock().acquire();
        } catch (InterruptedException e) {
            String msg = "Unable to acquire write lock.";
            throw new JournalException(msg, e);
        }

        boolean succeeded = false;

        try {
            // lock
            doLock();
            try {
                // and sync
                doSync(getMinimalRevision());
                succeeded = true;
            } finally {
                if (!succeeded) {
                    doUnlock(false);
                }
            }
        } finally {
            if (!succeeded) {
                rwLock.writeLock().release();
            }
        }
    }

    /**
     * Unlock the journal revision.
     *
     * @param successful flag indicating whether the update process was
     *                   successful
     */
    public void unlock(boolean successful) {
        doUnlock(successful);

        rwLock.writeLock().release();
    }

    /**
     * Lock the journal revision. Subclass responsibility.
     *
     * @throws JournalException if an error occurs
     */
    protected abstract void doLock() throws JournalException;

    /**
     * Append a record backed by a file. Subclass responsibility.
     *
     * @param producerId producer identifier
     * @param in input stream
     * @param length number of bytes in input stream
     * @return the new record's revision
     *
     * @throws JournalException if an error occurs
     */
    protected abstract long append(String producerId, InputStream in, int length)
            throws JournalException;

    /**
     * Unlock the journal revision. Subclass responsibility.
     *
     * @param successful flag indicating whether the update process was
     *                   successful
     */
    protected abstract void doUnlock(boolean successful);

    /**
     * Return this journal's identifier.
     *
     * @return journal identifier
     */
    public String getId() {
        return id;
    }

    /**
     * Return this journal's namespace resolver.
     *
     * @return namespace resolver
     */
    public NamespaceResolver getResolver() {
        return resolver;
    }
}