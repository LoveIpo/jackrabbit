/*
 * Copyright 2002-2004 The Apache Software Foundation.
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
package org.apache.jackrabbit.jcr.core;

import org.apache.commons.collections.ReferenceMap;
import org.apache.log4j.Logger;
import org.apache.jackrabbit.jcr.core.nodetype.NodeTypeRegistry;
import org.apache.jackrabbit.jcr.core.state.*;
import org.apache.jackrabbit.jcr.core.version.FrozenNode;
import org.apache.jackrabbit.jcr.core.version.VersionHistoryImpl;
import org.apache.jackrabbit.jcr.core.version.VersionImpl;
import org.apache.jackrabbit.jcr.util.IteratorHelper;

import javax.jcr.*;
import javax.jcr.access.AccessDeniedException;
import javax.jcr.access.Permission;
import javax.jcr.nodetype.NodeDef;
import javax.jcr.nodetype.PropertyDef;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * There's one <code>ItemManager</code> instance per <code>Session</code>
 * instance. It is the factory for <code>Node</code> and <code>Property</code>
 * instances.
 * <p/>
 * The <code>ItemManager</code>'s responsabilities are:
 * <ul>
 * <li>providing access to <code>Item</code> instances by <code>ItemId</code>
 * whereas <code>Node</code> and <code>Item</code> are only providing relative access.
 * <li>returning the instance of an existing <code>Node</code> or <code>Property</code>,
 * given its absolute path.
 * <li>creating the per-session instance of a <code>Node</code>
 * or <code>Property</code> that doesn't exist yet and needs to be created first.
 * <li>guaranteeing that there aren't multiple instances representing the same
 * <code>Node</code> or <code>Property</code> associated with the same
 * <code>Session</code> instance.
 * <li>maintaining a cache of the item instances it created.
 * <li>checking access rights of associated <code>Session</code> in all methods.
 * </ul>
 *
 * @author Stefan Guggisberg
 * @version $Revision: 1.91 $, $Date: 2004/09/10 15:23:37 $
 */
public class ItemManager implements ItemLifeCycleListener {

    private static Logger log = Logger.getLogger(ItemManager.class);

    private final NodeDef rootNodeDef;
    private final NodeId rootNodeId;

    private final SessionImpl session;

    private final ItemStateProvider itemStateProvider;
    private final HierarchyManager hierMgr;

    private NodeImpl root;

    /**
     * A cache for item instances created by this <code>ItemManager</code>
     */
    private Map itemCache;

    /**
     * Creates a new per-workspace instance <code>ItemManager</code> instance.
     *
     * @param itemStateProvider the item state provider associated with
     *                          the new instance
     * @param session           the session associated with the new instance
     * @param rootNodeDef       the definition of the root node
     * @param rootNodeUUID      the UUID of the root node
     */
    ItemManager(ItemStateProvider itemStateProvider, HierarchyManager hierMgr,
		SessionImpl session, NodeDef rootNodeDef, String rootNodeUUID) {
	this.itemStateProvider = itemStateProvider;
	this.hierMgr = hierMgr;
	this.session = session;
	this.rootNodeDef = rootNodeDef;
	rootNodeId = new NodeId(rootNodeUUID);
	// setup item cache with soft references to items
	itemCache = new ReferenceMap(ReferenceMap.HARD, ReferenceMap.SOFT);
    }

    /**
     * Returns the root node instance of the repository.
     *
     * @return the root node.
     * @throws RepositoryException
     */
    private synchronized NodeImpl getRoot() throws RepositoryException {
	// lazy instantiation of root node
	// to avoid chicken & egg kind of problems
	if (root == null) {
	    try {
		NodeState rootState = (NodeState) itemStateProvider.getItemState(rootNodeId);
		// keep a hard reference to root node
		root = createNodeInstance(rootState, rootNodeDef);
	    } catch (ItemStateException ise) {
		String msg = "failed to retrieve state of root node";
		log.error(msg, ise);
		throw new RepositoryException(msg, ise);
	    }
	}
	return root;
    }

    /**
     * Dumps the state of this <code>ItemManager</code> instance
     * (used for diagnostic purposes).
     *
     * @param ps
     * @throws RepositoryException
     */
    void dump(PrintStream ps) throws RepositoryException {
	ps.println("ItemManager (" + this + ")");
	ps.println();
	ps.println("Items in cache:");
	ps.println();
	Iterator iter = itemCache.keySet().iterator();
	while (iter.hasNext()) {
	    ItemId id = (ItemId) iter.next();
	    ItemImpl item = (ItemImpl) itemCache.get(id);
	    ps.print(item.isNode() ? "Node: " : "Prop: ");
	    ps.print(item.isTransient() ? "transient " : "          ");
	    ps.println(id + "\t" + item.getPath() + " (" + item + ")");
	}
	ps.println();
    }

    //--------------------------------------------------< item access methods >
    /**
     * @param path
     * @return
     */
    boolean itemExists(Path path) {
	try {
	    getItem(path);
	    return true;
	} catch (PathNotFoundException pnfe) {
	    return false;
	} catch (AccessDeniedException ade) {
	    return true;
	} catch (RepositoryException re) {
	    return false;
	}
    }

    /**
     * @param id
     * @return
     */
    boolean itemExists(ItemId id) {
	try {
	    getItem(id);
	    return true;
	} catch (ItemNotFoundException infe) {
	    return false;
	} catch (AccessDeniedException ade) {
	    return true;
	} catch (RepositoryException re) {
	    return false;
	}
    }

    /**
     * @return
     * @throws RepositoryException
     */
    NodeImpl getRootNode() throws RepositoryException {
	return getRoot();
    }

    /**
     * @param path
     * @return
     * @throws PathNotFoundException
     * @throws AccessDeniedException
     * @throws RepositoryException
     */
    synchronized ItemImpl getItem(Path path)
	    throws PathNotFoundException, AccessDeniedException, RepositoryException {
	ItemId id = hierMgr.resolvePath(path);
	try {
	    return getItem(id);
	} catch (ItemNotFoundException infe) {
	    throw new PathNotFoundException(safeGetJCRPath(path));
	}
    }

    /**
     * @param id
     * @return
     * @throws RepositoryException
     */
    public synchronized ItemImpl getItem(ItemId id)
	    throws ItemNotFoundException, AccessDeniedException, RepositoryException {
	// check privileges
	if (!session.getAccessManager().isGranted(id, Permission.READ_ITEM)) {
	    // clear cache
	    if (isCached(id)) {
		evictItem(id);
	    }
	    throw new AccessDeniedException("cannot read item " + id);
	}

	// check cache
	if (isCached(id)) {
	    return retrieveItem(id);
	}

	// shortcut
	if (id.denotesNode() && ((NodeId) id).equals(rootNodeId)) {
	    return getRoot();
	}

	// create instance of item using its state object
	return createItemInstance(id);
    }

    /**
     * @param parentId
     * @return
     * @throws ItemNotFoundException
     * @throws AccessDeniedException
     * @throws RepositoryException
     */
    synchronized NodeIterator getChildNodes(NodeId parentId)
	    throws ItemNotFoundException, AccessDeniedException, RepositoryException {
	// check privileges
	if (!session.getAccessManager().isGranted(parentId, Permission.READ_ITEM)) {
	    // clear cache
	    ItemImpl item = retrieveItem(parentId);
	    if (item != null) {
		evictItem(parentId);
	    }
	    throw new AccessDeniedException("cannot read item " + parentId);
	}

	ArrayList children = new ArrayList();

	ItemState state = null;
	try {
	    state = itemStateProvider.getItemState(parentId);
	} catch (NoSuchItemStateException nsise) {
	    String msg = "no such item: " + parentId;
	    log.error(msg);
	    throw new ItemNotFoundException(msg);
	} catch (ItemStateException ise) {
	    String msg = "failed to retrieve item state of node " + parentId;
	    log.error(msg);
	    throw new RepositoryException(msg);
	}

	if (!state.isNode()) {
	    String msg = "can't list child nodes of property " + parentId;
	    log.error(msg);
	    throw new RepositoryException(msg);
	}
	NodeState nodeState = (NodeState) state;
	Iterator iter = nodeState.getChildNodeEntries().iterator();

	while (iter.hasNext()) {
	    NodeState.ChildNodeEntry entry = (NodeState.ChildNodeEntry) iter.next();
	    try {
		Item item = getItem(new NodeId(entry.getUUID()));
		children.add(item);
	    } catch (AccessDeniedException ade) {
		// ignore
		continue;
	    }
	}

	return new IteratorHelper(Collections.unmodifiableList(children));
    }

    /**
     * @param parentId
     * @return
     * @throws ItemNotFoundException
     * @throws AccessDeniedException
     * @throws RepositoryException
     */
    synchronized PropertyIterator getChildProperties(NodeId parentId)
	    throws ItemNotFoundException, AccessDeniedException, RepositoryException {
	// check privileges
	if (!session.getAccessManager().isGranted(parentId, Permission.READ_ITEM)) {
	    ItemImpl item = retrieveItem(parentId);
	    if (item != null) {
		evictItem(parentId);
	    }
	    throw new AccessDeniedException("cannot read item " + parentId);
	}

	ArrayList children = new ArrayList();

	ItemState state = null;
	try {
	    state = itemStateProvider.getItemState(parentId);
	} catch (NoSuchItemStateException nsise) {
	    String msg = "no such item: " + parentId;
	    log.error(msg);
	    throw new ItemNotFoundException(msg);
	} catch (ItemStateException ise) {
	    String msg = "failed to retrieve item state of node " + parentId;
	    log.error(msg);
	    throw new RepositoryException(msg);
	}

	if (!state.isNode()) {
	    String msg = "can't list child properties of property " + parentId;
	    log.error(msg);
	    throw new RepositoryException(msg);
	}
	NodeState nodeState = (NodeState) state;
	Iterator iter = nodeState.getPropertyEntries().iterator();

	while (iter.hasNext()) {
	    NodeState.PropertyEntry entry = (NodeState.PropertyEntry) iter.next();
	    try {
		Item item = getItem(new PropertyId(parentId.getUUID(), entry.getName()));
		children.add(item);
	    } catch (AccessDeniedException ade) {
		// ignore
		continue;
	    }
	}

	return new IteratorHelper(Collections.unmodifiableList(children));
    }

    //-------------------------------------------------< item factory methods >
    private ItemImpl createItemInstance(ItemId id) throws ItemNotFoundException, RepositoryException {
	// create instance of item using its state object
	ItemImpl item = null;
	ItemState state = null;
	try {
	    state = itemStateProvider.getItemState(id);
	} catch (NoSuchItemStateException ise) {
	    throw new ItemNotFoundException(id.toString());
	} catch (ItemStateException ise) {
	    String msg = "failed to retrieve item state of item " + id;
	    log.error(msg);
	    throw new RepositoryException(msg);
	}

	if (state.isNode()) {
	    item = createNodeInstance((NodeState) state);
	} else {
	    item = createPropertyInstance((PropertyState) state);
	}
	return item;
    }

    NodeImpl createNodeInstance(NodeState state, NodeDef def)
	    throws RepositoryException {
	NodeId id = new NodeId(state.getUUID());
	// we want to be informed on life cycle changes of the new node object
	// in order to maintain item cache consistency
	ItemLifeCycleListener[] listeners = new ItemLifeCycleListener[]{this};

	// create node object; create specialized nodes for nodes of specific
	// primary types (i.e. nt:version & nt:versionHistory)
	if (state.getNodeTypeName().equals(NodeTypeRegistry.NT_VERSION_HISTORY)) {
	    return new VersionHistoryImpl(this, session, id, state, def, listeners);
	} else if (state.getNodeTypeName().equals(NodeTypeRegistry.NT_FROZEN)) {
	    return new FrozenNode(this, session, id, state, def, listeners);
	} else if (state.getNodeTypeName().equals(NodeTypeRegistry.NT_VERSION)) {
	    return new VersionImpl(this, session, id, state, def, listeners);
	} else {
	    return new NodeImpl(this, session, id, state, def, listeners);
	}
    }

    NodeImpl createNodeInstance(NodeState state) throws RepositoryException {
	// 1. get definition of the specified node
	NodeDef def = session.getNodeTypeManager().getNodeDef(state.getDefinitionId());
	if (def == null) {
	    String msg = "internal error: no definition found for node " + safeGetJCRPath(state.getId());
	    log.error(msg);
	    throw new RepositoryException(msg);
	}
	// 2. create instance
	return createNodeInstance(state, def);
    }

    PropertyImpl createPropertyInstance(PropertyState state, PropertyDef def) {
	PropertyId id = new PropertyId(state.getParentUUID(), state.getName());
	// we want to be informed on life cycle changes of the new property object
	// in order to maintain item cache consistency
	ItemLifeCycleListener[] listeners = new ItemLifeCycleListener[]{this};
	// create property object
	PropertyImpl prop = new PropertyImpl(this, session, id, state, def, listeners);
	return prop;
    }

    PropertyImpl createPropertyInstance(PropertyState state) throws RepositoryException {
	// 1. get definition for the specified property
	PropertyDef def = session.getNodeTypeManager().getPropDef(state.getDefinitionId());
	if (def == null) {
	    String msg = "internal error: no definition found for property " + safeGetJCRPath(state.getId());
	    log.error(msg);
	    throw new RepositoryException(msg);
	}
	// 2. create instance
	return createPropertyInstance(state, def);
    }

    /**
     * Removes the specified item from the cache and renders it
     * 'invalid'.
     *
     * @param id
     * @throws ItemNotFoundException
     * @throws RepositoryException
     */
    void removeItem(ItemId id)
	    throws ItemNotFoundException, RepositoryException {
	// the removed instance is not directly removed from the cache;
	// it will be removed when the instance notifies the item manager
	// that it has been invalidated (see itemInvalidated method)
	ItemImpl item = retrieveItem(id);
	if (item == null) {
	    // need to instantiate item first
	    item = createItemInstance(id);
	}
	item.setRemoved();
    }

    //---------------------------------------------------< item cache methods >
    /**
     * Checks if there's a cache entry for the specified id.
     *
     * @param id the id to be checked
     * @return true if there's a corresponding cache entry, otherwise false.
     */
    private boolean isCached(ItemId id) {
	return itemCache.containsKey(id);
    }

    /**
     * Returns an item reference from the cache.
     *
     * @param id id of the item that should be retrieved.
     * @return the item reference stored in the corresponding cache entry
     *         or <code>null</code> if there's no corresponding cache entry.
     */
    private ItemImpl retrieveItem(ItemId id) {
	return (ItemImpl) itemCache.get(id);
    }

    /**
     * Puts the reference of an item in the cache with
     * the item's path as the key.
     *
     * @param item the item to cache
     */
    private void cacheItem(ItemImpl item) {
	ItemId id = item.getId();
	if (itemCache.containsKey(id)) {
	    log.warn("overwriting cached item " + id);
	}
	log.debug("caching item " + id);
	itemCache.put(id, item);
    }

    /**
     * Removes a cache entry for a specific item.
     *
     * @param id id of the item to remove from the cache
     */
    private void evictItem(ItemId id) {
	log.debug("removing item " + id + " from cache");
	itemCache.remove(id);
    }

    //-------------------------------------------------< misc. helper methods >
    /**
     * Failsafe conversion of internal <code>Path</code> to JCR path for use in
     * error messages etc.
     *
     * @param path path to convert
     * @return JCR path
     */
    String safeGetJCRPath(Path path) {
	try {
	    return path.toJCRPath(session.getNamespaceResolver());
	} catch (NoPrefixDeclaredException npde) {
	    log.error("failed to convert " + path.toString() + " to JCR path.");
	    // return string representation of internal path as a fallback
	    return path.toString();
	}
    }

    /**
     * Failsafe translation of internal <code>ItemId</code> to JCR path for use in
     * error messages etc.
     *
     * @param id path to convert
     * @return JCR path
     */
    String safeGetJCRPath(ItemId id) {
	try {
	    return safeGetJCRPath(hierMgr.getPath(id));
	} catch (RepositoryException re) {
	    log.error(id + ": failed to determine path to");
	    // return string representation if id as a fallback
	    return id.toString();
	}
    }

    //------------------------------------------------< ItemLifeCycleListener >
    /**
     * @see ItemLifeCycleListener#itemCreated
     */
    public void itemCreated(ItemImpl item) {
	log.debug("created item " + item.getId());
	// add instance to cache
	cacheItem(item);
    }

    /**
     * @see ItemLifeCycleListener#itemInvalidated
     */
    public void itemInvalidated(ItemId id, ItemImpl item) {
	log.debug("invalidated item " + id);
	// remove instance from cache
	evictItem(id);
    }

    /**
     * @see ItemLifeCycleListener#itemResurrected
     */
    public void itemResurrected(ItemImpl item) {
	log.debug("resurrected item " + item.getId());
	// add instance to cache
	cacheItem(item);
    }

    /**
     * @see ItemLifeCycleListener#itemDestroyed
     */
    public void itemDestroyed(ItemId id, ItemImpl item) {
	log.debug("destroyed item " + id);
	// we're no longer interested in this item
	item.removeLifeCycleListener(this);
	// remove instance from cache
	evictItem(id);
    }
}
