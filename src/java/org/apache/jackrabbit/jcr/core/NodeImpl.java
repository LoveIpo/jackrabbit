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

import org.apache.log4j.Logger;
import org.apache.jackrabbit.jcr.core.nodetype.*;
import org.apache.jackrabbit.jcr.core.state.*;
import org.apache.jackrabbit.jcr.core.version.FrozenNode;
import org.apache.jackrabbit.jcr.core.version.GenericVersionSelector;
import org.apache.jackrabbit.jcr.core.version.VersionImpl;
import org.apache.jackrabbit.jcr.core.version.VersionSelector;
import org.apache.jackrabbit.jcr.util.ChildrenCollector;
import org.apache.jackrabbit.jcr.util.IteratorHelper;
import org.apache.jackrabbit.jcr.util.uuid.UUID;

import javax.jcr.*;
import javax.jcr.access.AccessDeniedException;
import javax.jcr.lock.Lock;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.*;
import javax.jcr.util.TraversingItemVisitor;
import javax.jcr.version.OnParentVersionAction;
import javax.jcr.version.Version;
import javax.jcr.version.VersionException;
import javax.jcr.version.VersionHistory;
import java.io.InputStream;
import java.util.*;

/**
 * <code>NodeImpl</code> implements the <code>Node</code> interface.
 *
 * @author Stefan Guggisberg
 * @author Tobias Strasser
 * @version $Revision: 1.175 $, $Date: 2004/09/14 12:48:59 $
 */
public class NodeImpl extends ItemImpl implements Node {

    private static Logger log = Logger.getLogger(NodeImpl.class);

    protected final NodeTypeImpl nodeType;

    protected NodeDef definition;

    /**
     * Package private constructor.
     *
     * @param itemMgr    the <code>ItemManager</code> that created this <code>Node</code> instance
     * @param session    the <code>Session</code> through which this <code>Node</code> is acquired
     * @param id         id of this <code>Node</code>
     * @param state      state associated with this <code>Node</code>
     * @param definition definition of <i>this</i> <code>Node</code>
     * @param listeners  listeners on life cylce changes of this <code>NodeImpl</code>
     * @throws RepositoryException
     */
    protected NodeImpl(ItemManager itemMgr, SessionImpl session, NodeId id,
		       NodeState state, NodeDef definition,
		       ItemLifeCycleListener[] listeners)
	    throws RepositoryException {
	super(itemMgr, session, id, state, listeners);
	nodeType = session.getNodeTypeManager().getNodeType(state.getNodeTypeName());
	this.definition = definition;
    }

    protected synchronized ItemState getOrCreateTransientItemState()
	    throws RepositoryException {
	if (!isTransient()) {
	    try {
		// make transient (copy-on-write)
		NodeState transientState =
			itemStateMgr.createTransientNodeState((NodeState) state, ItemState.STATUS_EXISTING_MODIFIED);
		// remove listener on persistent state
		state.removeListener(this);
		// add listener on transient state
		transientState.addListener(this);
		// replace persistent with transient state
		state = transientState;
	    } catch (ItemStateException ise) {
		String msg = "failed to create transient state";
		log.error(msg, ise);
		throw new RepositoryException(msg, ise);
	    }
	}
	return state;
    }

    protected InternalValue[] computeSystemGeneratedPropertyValues(QName name,
								   PropertyDefImpl def)
	    throws RepositoryException {
	InternalValue[] genValues = null;

	/**
	 * todo: need to come up with some callback mechanism for applying system generated values
	 * (e.g. using a NodeTypeInstanceHandler interface)
	 */

	NodeState thisState = (NodeState) state;

	// compute/apply system generated values
	NodeTypeImpl nt = (NodeTypeImpl) def.getDeclaringNodeType();
	if (nt.getQName().equals(NodeTypeRegistry.MIX_REFERENCEABLE)) {
	    // mix:referenceable node type
	    if (name.equals(PROPNAME_UUID)) {
		// jcr:uuid property
		genValues = new InternalValue[]{InternalValue.create(((NodeState) state).getUUID())};
	    }
/*
	todo centralize version history creation code (currently in NodeImpl.addMixin & ItemImpl.initVersionHistories
	} else if (nt.getQName().equals(NodeTypeRegistry.MIX_VERSIONABLE)) {
	    // mix:versionable node type
	    VersionHistory hist = rep.getVersionManager().getOrCreateVersionHistory(this);
	    if (name.equals(VersionImpl.PROPNAME_VERSION_HISTORY)) {
		// jcr:versionHistory property
		genValues = new InternalValue[]{InternalValue.create(new UUID(hist.getUUID()))};
	    } else if (name.equals(VersionImpl.PROPNAME_BASE_VERSION)) {
		// jcr:baseVersion property
		genValues = new InternalValue[]{InternalValue.create(new UUID(hist.getRootVersion().getUUID()))};
	    } else if (name.equals(VersionImpl.PROPNAME_IS_CHECKED_OUT)) {
		// jcr:isCheckedOut property
		genValues = new InternalValue[]{InternalValue.create(true)};
	    } else if (name.equals(VersionImpl.PROPNAME_PREDECESSORS)) {
		// jcr:predecessors property
		genValues = new InternalValue[]{InternalValue.create(new UUID(hist.getRootVersion().getUUID()))};
	    }
*/
	} else if (nt.getQName().equals(NodeTypeRegistry.NT_HIERARCHYNODE)) {
	    // nt:hierarchyNode node type
	    if (name.equals(PROPNAME_CREATED)) {
		// jcr:created property
		genValues = new InternalValue[]{InternalValue.create(Calendar.getInstance())};
	    }
	} else if (nt.getQName().equals(NodeTypeRegistry.NT_MIME_RESOURCE)) {
	    // nt:mimeResource node type
	    if (name.equals(PROPNAME_LAST_MODIFIED)) {
		// jcr:lastModified property
		genValues = new InternalValue[]{InternalValue.create(Calendar.getInstance())};
	    }
	} else if (nt.getQName().equals(NodeTypeRegistry.NT_VERSION)) {
	    // nt:hierarchyNode node type
	    if (name.equals(PROPNAME_CREATED)) {
		// jcr:created property
		genValues = new InternalValue[]{InternalValue.create(Calendar.getInstance())};
	    }
	} else if (nt.getQName().equals(NodeTypeRegistry.NT_BASE)) {
	    // nt:base node type
	    if (name.equals(PROPNAME_PRIMARYTYPE)) {
		// jcr:primaryType property
		genValues = new InternalValue[]{InternalValue.create(nodeType.getQName())};
	    } else if (name.equals(PROPNAME_MIXINTYPES)) {
		// jcr:mixinTypes property
		Set mixins = thisState.getMixinTypeNames();
		ArrayList values = new ArrayList(mixins.size());
		Iterator iter = mixins.iterator();
		while (iter.hasNext()) {
		    values.add(InternalValue.create((QName) iter.next()));
		}
		genValues = (InternalValue[]) values.toArray(new InternalValue[values.size()]);
	    }
	}

	return genValues;
    }

    protected PropertyImpl getOrCreateProperty(String name, int type)
	    throws RepositoryException {
	try {
	    return (PropertyImpl) getProperty(name);
	} catch (PathNotFoundException pnfe) {
	    // fall through
	}
	// property does not exist yet...
	QName qName;
	try {
	    qName = QName.fromJCRName(name, session.getNamespaceResolver());
	} catch (IllegalNameException ine) {
	    throw new RepositoryException("invalid property name: " + name, ine);
	} catch (UnknownPrefixException upe) {
	    throw new RepositoryException("invalid property name: " + name, upe);
	}
	// find definition for the specified property and create property
	PropertyDefImpl def = getApplicablePropertyDef(qName, type);
	return createChildProperty(qName, type, def);
    }

    protected PropertyImpl getOrCreateProperty(QName name, int type)
	    throws RepositoryException {
	try {
	    return (PropertyImpl) getProperty(name);
	} catch (ItemNotFoundException e) {
	    // does not exist yet:
	    // find definition for the specified property and create property
	    PropertyDefImpl def = getApplicablePropertyDef(name, type);
	    return createChildProperty(name, type, def);
	}
    }

    protected synchronized PropertyImpl createChildProperty(QName name, int type, PropertyDefImpl def)
	    throws RepositoryException {
	// check for name collisions with existing child nodes
	if (((NodeState) state).hasChildNodeEntry(name)) {
	    String msg = "there's already a child node with name " + name;
	    log.error(msg);
	    throw new RepositoryException(msg);
	}

	String parentUUID = ((NodeState) state).getUUID();

	// create a new property state
	PropertyState propState = null;
	try {
	    propState = itemStateMgr.createTransientPropertyState(parentUUID, name, ItemState.STATUS_NEW);
	    propState.setType(type);
	    propState.setDefinitionId(new PropDefId(def.unwrap()));
	    // compute system generated values if necessary
	    InternalValue[] genValues = computeSystemGeneratedPropertyValues(name, def);
	    if (genValues != null) {
		propState.setValues(genValues);
	    } else if (def.getDefaultValues() != null) {
		Value[] vals = def.getDefaultValues();
		int length = (def.isMultiple() ? vals.length : 1);
		InternalValue[] defVals = new InternalValue[length];
		for (int i = 0; i < length; i++) {
		    defVals[i] = InternalValue.create(vals[i], session.getNamespaceResolver());
		}
		propState.setValues(defVals);
	    }
	} catch (ItemStateException ise) {
	    String msg = "failed to add property " + name + " to " + safeGetJCRPath();
	    log.error(msg, ise);
	    throw new RepositoryException(msg, ise);
	}

	// create Property instance wrapping new property state
	PropertyImpl prop = itemMgr.createPropertyInstance(propState, def);

	// modify the state of 'this', i.e. the parent node
	NodeState thisState = (NodeState) getOrCreateTransientItemState();
	// add new property entry
	thisState.addPropertyEntry(name);

	return prop;
    }

    protected synchronized NodeImpl createChildNode(QName name, NodeDefImpl def,
						    NodeTypeImpl nodeType, String uuid)
	    throws RepositoryException {
	String parentUUID = ((NodeState) state).getUUID();
	// create a new node state
	NodeState nodeState = null;
	try {
	    if (uuid == null) {
		uuid = UUID.randomUUID().toString();	// version 4 uuid
	    }
	    nodeState = itemStateMgr.createTransientNodeState(uuid, nodeType.getQName(), parentUUID, ItemState.STATUS_NEW);
	    nodeState.setDefinitionId(new NodeDefId(def.unwrap()));
	} catch (ItemStateException ise) {
	    String msg = "failed to add child node " + name + " to " + safeGetJCRPath();
	    log.error(msg, ise);
	    throw new RepositoryException(msg, ise);
	}

	// create Node instance wrapping new node state
	NodeImpl node;
	try {
	    node = itemMgr.createNodeInstance(nodeState, def);
	} catch (RepositoryException re) {
	    // something went wrong
	    itemStateMgr.disposeTransientItemState(nodeState);
	    // re-throw
	    throw re;
	}

	// modify the state of 'this', i.e. the parent node
	NodeState thisState = (NodeState) getOrCreateTransientItemState();
	// add new child node entry
	thisState.addChildNodeEntry(name, nodeState.getUUID());

	// add 'auto-create' properties defined in node type
	PropertyDef[] pda = nodeType.getAutoCreatePropertyDefs();
	for (int i = 0; i < pda.length; i++) {
	    PropertyDefImpl pd = (PropertyDefImpl) pda[i];
	    node.createChildProperty(pd.getQName(), pd.getRequiredType(), pd);
	}

	// recursively add 'auto-create' child nodes defined in node type
	NodeDef[] nda = nodeType.getAutoCreateNodeDefs();
	for (int i = 0; i < nda.length; i++) {
	    NodeDefImpl nd = (NodeDefImpl) nda[i];
	    node.createChildNode(nd.getQName(), nd, (NodeTypeImpl) nd.getDefaultPrimaryType(), null);
	}

	return node;
    }

    protected NodeImpl createChildNodeLink(QName nodeName, String targetUUID)
	    throws RepositoryException {
	// modify the state of 'this', i.e. the parent node
	NodeState thisState = (NodeState) getOrCreateTransientItemState();

	NodeId targetId = new NodeId(targetUUID);
	NodeImpl targetNode = (NodeImpl) itemMgr.getItem(targetId);
	// notify target of link
	targetNode.onLink(thisState);

	thisState.addChildNodeEntry(nodeName, targetUUID);

	return (NodeImpl) itemMgr.getItem(targetId);
    }

    protected void removeChildProperty(QName propName) throws RepositoryException {
	// modify the state of 'this', i.e. the parent node
	NodeState thisState = (NodeState) getOrCreateTransientItemState();

	// remove property
	PropertyId propId = new PropertyId(thisState.getUUID(), propName);
	itemMgr.removeItem(propId);

	// remove the property entry
	if (!thisState.removePropertyEntry(propName)) {
	    String msg = "failed to remove property " + propName + " of " + safeGetJCRPath();
	    log.error(msg);
	    throw new RepositoryException(msg);
	}
    }

    protected void removeChildNode(QName nodeName, int index) throws RepositoryException {
	// modify the state of 'this', i.e. the parent node
	NodeState thisState = (NodeState) getOrCreateTransientItemState();
	index = (index == 0) ? 1 : index;
	NodeState.ChildNodeEntry entry = thisState.getChildNodeEntry(nodeName, index);
	if (entry == null) {
	    String msg = "failed to remove child " + nodeName + " of " + safeGetJCRPath();
	    log.error(msg);
	    throw new RepositoryException(msg);
	}

	NodeId childId = new NodeId(entry.getUUID());
	NodeImpl childNode = (NodeImpl) itemMgr.getItem(childId);
	// notify target of removal/unlink
	childNode.onUnlink(thisState);

	// remove child entry
	if (!thisState.removeChildNodeEntry(nodeName, index)) {
	    String msg = "failed to remove child " + nodeName + " of " + safeGetJCRPath();
	    log.error(msg);
	    throw new RepositoryException(msg);
	}
    }

    protected void onRedefine(NodeDefId defId) throws RepositoryException {
	NodeDefImpl newDef = session.getNodeTypeManager().getNodeDef(defId);
	// modify the state of 'this', i.e. the target node
	NodeState thisState = (NodeState) getOrCreateTransientItemState();
	// set id of new definition
	thisState.setDefinitionId(defId);
	definition = newDef;
    }

    protected void onLink(NodeState parentState) throws RepositoryException {
	// modify the state of 'this', i.e. the target node
	NodeState thisState = (NodeState) getOrCreateTransientItemState();
	// add uuid of this node to target's parent list
	thisState.addParentUUID(parentState.getUUID());
    }

    protected void onUnlink(NodeState parentState) throws RepositoryException {
	// modify the state of 'this', i.e. the target node
	NodeState thisState = (NodeState) getOrCreateTransientItemState();

	// check if this node would be orphaned after unlinking it from parent
	ArrayList parentUUIDs = new ArrayList(thisState.getParentUUIDs());
	parentUUIDs.remove(parentState.getUUID());
	boolean orphaned = parentUUIDs.isEmpty();

	if (orphaned) {
	    // remove child nodes (recursive)
	    // use temp array to avoid ConcurrentModificationException
	    ArrayList tmp = new ArrayList(thisState.getChildNodeEntries());
	    // remove from tail to avoid problems with same-name siblings
	    for (int i = tmp.size() - 1; i >= 0; i--) {
		NodeState.ChildNodeEntry entry = (NodeState.ChildNodeEntry) tmp.get(i);
		removeChildNode(entry.getName(), entry.getIndex());
	    }

	    // remove properties
	    // use temp array to avoid ConcurrentModificationException
	    tmp = new ArrayList(thisState.getPropertyEntries());
	    // remove from tail to avoid problems with same-name siblings
	    for (int i = tmp.size() - 1; i >= 0; i--) {
		NodeState.PropertyEntry entry = (NodeState.PropertyEntry) tmp.get(i);
		removeChildProperty(entry.getName());
	    }
	}

	// now actually do unlink this node from specified parent node
	// (i.e. remove uuid of parent node from this node's parent list)
	thisState.removeParentUUID(parentState.getUUID());

	if (orphaned) {
	    // remove this node
	    itemMgr.removeItem(id);
	}
    }

    protected NodeImpl internalAddNode(String relPath, NodeTypeImpl nodeType)
	    throws ItemExistsException, PathNotFoundException,
	    ConstraintViolationException, RepositoryException {
	return internalAddNode(relPath, nodeType, null);
    }

    protected NodeImpl internalAddNode(String relPath, NodeTypeImpl nodeType,
				       String uuid)
	    throws ItemExistsException, PathNotFoundException,
	    ConstraintViolationException, RepositoryException {
	Path nodePath;
	QName nodeName;
	Path parentPath;
	try {
	    nodePath = Path.create(getPrimaryPath(), relPath, session.getNamespaceResolver(), true);
	    if (nodePath.getNameElement().getIndex() != 0) {
		String msg = "illegal subscript specified: " + nodePath;
		log.error(msg);
		throw new RepositoryException(msg);
	    }
	    nodeName = nodePath.getNameElement().getName();
	    parentPath = nodePath.getAncestor(1);
	} catch (MalformedPathException e) {
	    String msg = "failed to resolve path " + relPath + " relative to " + safeGetJCRPath();
	    log.error(msg, e);
	    throw new RepositoryException(msg, e);
	}

	NodeImpl parentNode;
	try {
	    Item parent = itemMgr.getItem(parentPath);
	    if (!parent.isNode()) {
		String msg = "cannot add a node to property " + parentPath;
		log.error(msg);
		throw new ConstraintViolationException(msg);
	    }
	    parentNode = (NodeImpl) parent;
	} catch (AccessDeniedException ade) {
	    throw new PathNotFoundException(relPath);
	}

	// delegate the creation of the child node to the parent node
	return parentNode.internalAddChildNode(nodeName, nodeType, uuid);
    }

    protected NodeImpl internalAddChildNode(QName nodeName, NodeTypeImpl nodeType)
	    throws ItemExistsException, ConstraintViolationException, RepositoryException {
	return internalAddChildNode(nodeName, nodeType, null);
    }

    protected NodeImpl internalAddChildNode(QName nodeName, NodeTypeImpl nodeType, String uuid)
	    throws ItemExistsException, ConstraintViolationException, RepositoryException {
	Path nodePath;
	try {
	    nodePath = Path.create(getPrimaryPath(), nodeName, true);
	} catch (MalformedPathException e) {
	    // should never happen
	    String msg = "internal error: invalid path " + safeGetJCRPath();
	    log.error(msg, e);
	    throw new RepositoryException(msg, e);
	}

	NodeDefImpl def;
	try {
	    def = getApplicableChildNodeDef(nodeName, nodeType == null ? null : nodeType.getQName());
	} catch (RepositoryException re) {
	    String msg = "no definition found in parent node's node type for new node";
	    log.error(msg, re);
	    throw new ConstraintViolationException(msg, re);
	}
	if (nodeType == null) {
	    // use default node type
	    nodeType = (NodeTypeImpl) def.getDefaultPrimaryType();
	}

	// check for name collisions
	try {
	    ItemImpl item = itemMgr.getItem(nodePath);
	    if (!item.isNode()) {
		// there's already a property with that name
		throw new ItemExistsException(item.safeGetJCRPath());
	    } else {
		// there's already a node with that name
		// check same-name sibling setting of both new and existing node
		if (!def.allowSameNameSibs() ||
			!((NodeImpl) item).getDefinition().allowSameNameSibs()) {
		    throw new ItemExistsException(item.safeGetJCRPath());
		}
	    }
	} catch (PathNotFoundException pnfe) {
	    // no name collision
	}

	// check if versioning allows write
	if (!safeIsCheckedOut()) {
	    String msg = safeGetJCRPath() + ": cannot add a child to a checked-in node";
	    log.error(msg);
	    throw new VersionException(msg);
	}

	// check protected flag of parent (i.e. this) node
	if (getDefinition().isProtected()) {
	    String msg = safeGetJCRPath() + ": cannot add a child to a protected node";
	    log.error(msg);
	    throw new ConstraintViolationException(msg);
	}

	// now do create the child node
	return createChildNode(nodeName, def, nodeType, uuid);
    }

    private void setMixinTypesProperty(Set mixinNames) throws RepositoryException {
	NodeState thisState = (NodeState) state;
	// get or create jcr:mixinTypes property
	PropertyImpl prop = null;
	if (thisState.hasPropertyEntry(PROPNAME_MIXINTYPES)) {
	    prop = (PropertyImpl) itemMgr.getItem(new PropertyId(thisState.getUUID(), PROPNAME_MIXINTYPES));
	} else {
	    // find definition for the jcr:mixinTypes property and create property
	    PropertyDefImpl def = getApplicablePropertyDef(PROPNAME_MIXINTYPES, PropertyType.NAME);
	    prop = createChildProperty(PROPNAME_MIXINTYPES, PropertyType.NAME, def);
	}

	if (mixinNames.isEmpty()) {
	    // purge empty jcr:mixinTypes property
	    removeChildProperty(PROPNAME_MIXINTYPES);
	    return;
	}

	// call internalSetValue for setting the jcr:mixinTypes property
	// to avoid checking of the 'protected' flag
	InternalValue[] vals = new InternalValue[mixinNames.size()];
	Iterator iter = mixinNames.iterator();
	int cnt = 0;
	while (iter.hasNext()) {
	    vals[cnt++] = InternalValue.create((QName) iter.next());
	}
	prop.internalSetValue(vals, PropertyType.NAME);
    }

    /**
     * Returns the effective (i.e. merged and resolved) node type representation
     * of this node's primary and mixin node types.
     *
     * @return the effective node type
     * @throws RepositoryException
     */
    protected EffectiveNodeType getEffectiveNodeType() throws RepositoryException {
	// build effective node type of mixins & primary type
	NodeTypeRegistry ntReg = session.getNodeTypeManager().getNodeTypeRegistry();
	// existing mixin's
	HashSet set = new HashSet(((NodeState) state).getMixinTypeNames());
	// primary type
	set.add(nodeType.getQName());
	try {
	    return ntReg.buildEffectiveNodeType((QName[]) set.toArray(new QName[set.size()]));
	} catch (NodeTypeConflictException ntce) {
	    String msg = "internal error: failed to build effective node type for node " + safeGetJCRPath();
	    log.error(msg, ntce);
	    throw new RepositoryException(msg, ntce);
	}
    }

    /**
     * Returns the applicable child node definition for a child node with the
     * specified name and node type.
     *
     * @param nodeName
     * @param nodeTypeName
     * @return
     * @throws RepositoryException if no applicable child node definition
     *                             could be found
     */
    protected NodeDefImpl getApplicableChildNodeDef(QName nodeName, QName nodeTypeName)
	    throws RepositoryException {
	ChildNodeDef cnd = getEffectiveNodeType().getApplicableChildNodeDef(nodeName, nodeTypeName);
	return session.getNodeTypeManager().getNodeDef(new NodeDefId(cnd));
    }

    /**
     * Returns the applicable property definition for a property with the
     * specified name and type.
     *
     * @param propertyName
     * @param type
     * @return
     * @throws RepositoryException if no applicable property definition
     *                             could be found
     */
    protected PropertyDefImpl getApplicablePropertyDef(QName propertyName, int type)
	    throws RepositoryException {
	PropDef pd = getEffectiveNodeType().getApplicablePropertyDef(propertyName, type);
	return session.getNodeTypeManager().getPropDef(new PropDefId(pd));
    }

    protected void makePersistent() throws RepositoryException {
	if (!isTransient()) {
	    log.debug(safeGetJCRPath() + " (" + id + "): there's no transient state to persist");
	    return;
	}

	try {
	    NodeState transientState = (NodeState) state;

	    PersistentNodeState persistentState = (PersistentNodeState) transientState.getOverlayedState();
	    if (persistentState == null) {
		// this node is 'new'
		persistentState = itemStateMgr.createPersistentNodeState(transientState.getUUID(), transientState.getNodeTypeName(), transientState.getParentUUID());
	    }
	    // copy state from transient state:
	    // parent uuid's
	    persistentState.setParentUUID(transientState.getParentUUID());
	    persistentState.setParentUUIDs(transientState.getParentUUIDs());
	    // mixin types
	    persistentState.setMixinTypeNames(transientState.getMixinTypeNames());
	    // id of definition
	    persistentState.setDefinitionId(transientState.getDefinitionId());
	    // child node entries
	    persistentState.setChildNodeEntries(transientState.getChildNodeEntries());
	    // property entries
	    persistentState.setPropertyEntries(transientState.getPropertyEntries());

	    // make state persistent
	    persistentState.store();
	    // remove listener from transient state
	    transientState.removeListener(this);
	    // add listener to persistent state
	    persistentState.addListener(this);
	    // swap transient state with persistent state
	    state = persistentState;
	    // reset status
	    status = STATUS_NORMAL;
	} catch (ItemStateException ise) {
	    String msg = "failed to persist transient state of " + safeGetJCRPath() + " (" + id + ")";
	    log.error(msg, ise);
	    throw new RepositoryException(msg, ise);
	}
    }

    protected boolean isRepositoryRoot() {
	return ((NodeState) state).getUUID().equals(rep.getRootNodeUUID());
    }

    /**
     * Sets the internal value of a property without checking any constraints.
     * <p/>
     * Note that no type conversion is being performed, i.e. it's the caller's
     * responsibility to make sure that the type of the given value is compatible
     * with the specified property's definition.
     *
     * @param name
     * @param value
     * @return
     * @throws ValueFormatException
     * @throws RepositoryException
     */
    public Property internalSetProperty(QName name, InternalValue value)
	    throws ValueFormatException, RepositoryException {
	if (value == null) {
	    return internalSetProperty(name, (InternalValue[]) null);
	} else {
	    return internalSetProperty(name, new InternalValue[]{value});
	}
    }

    /**
     * Sets the internal value of a property without checking any constraints.
     * <p/>
     * Note that no type conversion is being performed, i.e. it's the caller's
     * responsibility to make sure that the type of the given values is compatible
     * with the specified property's definition.
     *
     * @param name
     * @param values
     * @return
     * @throws ValueFormatException
     * @throws RepositoryException
     */
    protected Property internalSetProperty(QName name, InternalValue[] values)
	    throws ValueFormatException, RepositoryException {
	// check state of this instance
	checkItemState();

	int type;
	if (values == null || values.length == 0) {
	    type = PropertyType.STRING;
	} else {
	    type = values[0].getType();
	}
	PropertyImpl prop = getOrCreateProperty(name, type);
	prop.internalSetValue(values, type);
	return prop;
    }

    /**
     * Returns the child node of <code>this</code> node with the specified
     * <code>name</code>.
     *
     * @param name The qualified name of the child node to retrieve.
     * @return The child node with the specified <code>name</code>.
     * @throws ItemNotFoundException If no child node exists with the
     *                               specified name.
     * @throws RepositoryException   If another error occurs.
     */
    public NodeImpl getNode(QName name) throws ItemNotFoundException, RepositoryException {
	return getNode(name, 1);
    }

    /**
     * Returns the child node of <code>this</code> node with the specified
     * <code>name</code>.
     *
     * @param name  The qualified name of the child node to retrieve.
     * @param index The index of the child node to retrieve (in the case of same-name siblings).
     * @return The child node with the specified <code>name</code>.
     * @throws ItemNotFoundException If no child node exists with the
     *                               specified name.
     * @throws RepositoryException   If another error occurs.
     */
    public NodeImpl getNode(QName name, int index) throws ItemNotFoundException, RepositoryException {
	// check state of this instance
	checkItemState();

	Path nodePath;
	try {
	    nodePath = Path.create(getPrimaryPath(), name, index, true);
	} catch (MalformedPathException e) {
	    // should never happen
	    String msg = "internal error: invalid path " + safeGetJCRPath();
	    log.error(msg, e);
	    throw new RepositoryException(msg, e);
	}

	try {
	    Item item = itemMgr.getItem(nodePath);
	    if (item.isNode()) {
		return (NodeImpl) item;
	    } else {
		// there's a property with that name, no child node though
		throw new ItemNotFoundException();
	    }
	} catch (PathNotFoundException pnfe) {
	    throw new ItemNotFoundException();
	} catch (AccessDeniedException ade) {
	    throw new ItemNotFoundException();
	}
    }

    /**
     * Indicates whether a child node with the specified <code>name</code> exists.
     * Returns <code>true</code> if the child node exists and <code>false</code>
     * otherwise.
     *
     * @param name The qualified name of the child node.
     * @return <code>true</code> if the child node exists; <code>false</code> otherwise.
     * @throws RepositoryException If an unspecified error occurs.
     */
    public boolean hasNode(QName name) throws RepositoryException {
	try {
	    getNode(name, 1);
	    return true;
	} catch (ItemNotFoundException pnfe) {
	    return false;
	}
    }

    /**
     * Indicates whether a child node with the specified <code>name</code> exists.
     * Returns <code>true</code> if the child node exists and <code>false</code>
     * otherwise.
     *
     * @param name  The qualified name of the child node.
     * @param index The index of the child node (in the case of same-name siblings).
     * @return <code>true</code> if the child node exists; <code>false</code> otherwise.
     * @throws RepositoryException If an unspecified error occurs.
     */
    public boolean hasNode(QName name, int index) throws RepositoryException {
	try {
	    getNode(name, index);
	    return true;
	} catch (ItemNotFoundException pnfe) {
	    return false;
	}
    }

    /**
     * Returns the property of <code>this</code> node with the specified
     * <code>name</code>.
     *
     * @param name The qualified name of the property to retrieve.
     * @return The property with the specified <code>name</code>.
     * @throws ItemNotFoundException If no property exists with the
     *                               specified name.
     * @throws RepositoryException   If another error occurs.
     */
    public PropertyImpl getProperty(QName name)
	    throws ItemNotFoundException, RepositoryException {
	// check state of this instance
	checkItemState();

	PropertyId propId = new PropertyId(((NodeState) state).getUUID(), name);
	try {
	    return (PropertyImpl) itemMgr.getItem(propId);
	} catch (AccessDeniedException ade) {
	    throw new ItemNotFoundException(name.toString());
	}
    }

    /**
     * Indicates whether a property with the specified <code>name</code> exists.
     * Returns <code>true</code> if the property exists and <code>false</code>
     * otherwise.
     *
     * @param name The qualified name of the property.
     * @return <code>true</code> if the property exists; <code>false</code> otherwise.
     * @throws RepositoryException If an unspecified error occurs.
     */
    public boolean hasProperty(QName name) throws RepositoryException {
	try {
	    getProperty(name);
	    return true;
	} catch (ItemNotFoundException pnfe) {
	    return false;
	}
    }

    /**
     * @see Node#addNode(String)
     */
    synchronized public NodeImpl addNode(QName nodeName)
	    throws ItemExistsException, ConstraintViolationException,
	    RepositoryException {
	// check state of this instance
	checkItemState();

	return internalAddChildNode(nodeName, null);
    }

    /**
     * @see Node#addNode(String, String)
     */
    synchronized public NodeImpl addNode(QName nodeName, QName nodeTypeName)
	    throws ItemExistsException, NoSuchNodeTypeException,
	    ConstraintViolationException, RepositoryException {
	// check state of this instance
	checkItemState();

	NodeTypeImpl nt = session.getNodeTypeManager().getNodeType(nodeTypeName);
	return internalAddChildNode(nodeName, nt);
    }

    /**
     * Same as <code>{@link Node#setProperty(String, String)}</code> except that
     * this method takes a <code>QName</code> instead of a <code>String</code>
     * value.
     *
     * @param name
     * @param value
     * @return
     * @throws ValueFormatException
     * @throws RepositoryException
     */
    public PropertyImpl setProperty(String name, QName value) throws ValueFormatException, RepositoryException {
	// check state of this instance
	checkItemState();

	PropertyImpl prop = getOrCreateProperty(name, PropertyType.NAME);
	prop.setValue(value);
	return prop;
    }

    /**
     * Same as <code>{@link Node#setProperty(String, String[])}</code> except that
     * this method takes an array of  <code>QName</code> instead of a
     * <code>String</code> values.
     *
     * @param name
     * @param values
     * @return
     * @throws ValueFormatException
     * @throws RepositoryException
     */
    public PropertyImpl setProperty(String name, QName[] values) throws ValueFormatException, RepositoryException {
	// check state of this instance
	checkItemState();

	PropertyImpl prop = getOrCreateProperty(name, PropertyType.NAME);
	prop.setValue(values);
	return prop;
    }

    /**
     * Same as <code>{@link Node#setProperty(String, Value[])}</code> except that
     * this method takes a <code>QName</code> name argument instead of a
     * <code>String</code>.
     *
     * @param name
     * @param values
     * @return
     * @throws ValueFormatException
     * @throws RepositoryException
     */
    public PropertyImpl setProperty(QName name, Value[] values)
	    throws ValueFormatException, RepositoryException {
	// check state of this instance
	checkItemState();

	int type;
	if (values == null || values.length == 0) {
	    type = PropertyType.STRING;
	} else {
	    type = values[0].getType();
	}
	PropertyImpl prop = getOrCreateProperty(name, type);
	prop.setValue(values);
	return prop;
    }

    /**
     * @see ItemImpl#getQName()
     */
    public QName getQName() throws RepositoryException {
	return session.getHierarchyManager().getName(id);
    }

    //-----------------------------------------------------------------< Item >
    /**
     * @see Item#isNode()
     */
    public boolean isNode() {
	return true;
    }

    /**
     * @see Item#getName
     */
    public String getName() throws RepositoryException {
	if (state.getParentUUID() == null) {
	    // this is the root node
	    return "";
	}

	//QName name = getPrimaryPath().getNameElement().getName();
	QName name = session.getHierarchyManager().getName(id);
	try {
	    return name.toJCRName(session.getNamespaceResolver());
	} catch (NoPrefixDeclaredException npde) {
	    // should never get here...
	    String msg = "internal error: encountered unregistered namespace " + name.getNamespaceURI();
	    log.error(msg, npde);
	    throw new RepositoryException(msg, npde);
	}
    }

    /**
     * @see Item#accept(ItemVisitor)
     */
    public void accept(ItemVisitor visitor) throws RepositoryException {
	// check state of this instance
	checkItemState();

	visitor.visit(this);
    }

    /**
     * @see Item#getParent
     */
    public Node getParent()
	    throws ItemNotFoundException, AccessDeniedException, RepositoryException {
	// check state of this instance
	checkItemState();

	// check if root node
	NodeState thisState = (NodeState) state;
	if (thisState.getParentUUID() == null) {
	    String msg = "root node doesn't have a parent";
	    log.error(msg);
	    throw new ItemNotFoundException(msg);
	}

	Path path = getPrimaryPath();
	return (Node) getAncestor(path.getAncestorCount() - 1);
    }

    //-----------------------------------------------------------------< Node >
    /**
     * @see Node#remove(String)
     */
    synchronized public void remove(String relPath)
	    throws PathNotFoundException, RepositoryException {
	// check state of this instance
	checkItemState();

	Path targetPath;
	Path.PathElement targetName;
	Path parentPath;
	try {
	    targetPath = Path.create(getPrimaryPath(), relPath, session.getNamespaceResolver(), true);
	    targetName = targetPath.getNameElement();
	    parentPath = targetPath.getAncestor(1);
	} catch (MalformedPathException e) {
	    String msg = "failed to resolve path " + relPath + " relative to " + safeGetJCRPath();
	    log.error(msg, e);
	    throw new RepositoryException(msg, e);
	}

	// check if the specified item exists and if it is protected
	ItemImpl targetItem;
	try {
	    targetItem = itemMgr.getItem(targetPath);
	    if (targetItem.isNode()) {
		NodeImpl node = (NodeImpl) targetItem;
		NodeDef def = node.getDefinition();
		// check protected flag
		if (def.isProtected()) {
		    String msg = targetItem.safeGetJCRPath() + ": cannot remove a protected node";
		    log.error(msg);
		    throw new ConstraintViolationException(msg);
		}
	    } else {
		PropertyImpl prop = (PropertyImpl) targetItem;
		PropertyDef def = prop.getDefinition();
		// check protected flag
		if (def.isProtected()) {
		    String msg = targetItem.safeGetJCRPath() + ": cannot remove a protected property";
		    log.error(msg);
		    throw new ConstraintViolationException(msg);
		}
	    }
	} catch (AccessDeniedException ade) {
	    throw new PathNotFoundException(relPath);
	}

	NodeImpl parentNode;
	try {
	    ItemImpl parent = itemMgr.getItem(parentPath);
	    if (!parent.isNode()) {
		// should never get here
		String msg = "cannot remove an item from a property " + parent.safeGetJCRPath();
		log.error(msg);
		throw new RepositoryException(msg);
	    }
	    parentNode = (NodeImpl) parent;
	} catch (AccessDeniedException ade) {
	    // should never get here because we already checked
	    // the existence of the child...
	    throw new PathNotFoundException(relPath);
	}

	// check if versioning allows write
	if (!parentNode.safeIsCheckedOut()) {
	    String msg = parentNode.safeGetJCRPath() + ": cannot remove a child of a checked-in node";
	    log.error(msg);
	    throw new VersionException(msg);
	}

	// check protected flag of parent node
	if (parentNode.getDefinition().isProtected()) {
	    String msg = parentNode.safeGetJCRPath() + ": cannot remove a child of a protected node";
	    log.error(msg);
	    throw new ConstraintViolationException(msg);
	}

	// delegate the removal of the child item to the parent node
	if (targetItem.isNode()) {
	    parentNode.removeChildNode(targetName.getName(), targetName.getIndex());
	} else {
	    parentNode.removeChildProperty(targetName.getName());
	}
    }

    /**
     * @see Node#addNode(String)
     */
    synchronized public Node addNode(String relPath)
	    throws ItemExistsException, PathNotFoundException,
	    ConstraintViolationException, RepositoryException {
	// check state of this instance
	checkItemState();

	return internalAddNode(relPath, null);
    }

    /**
     * @see Node#addNode(String, String)
     */
    synchronized public Node addNode(String relPath, String nodeTypeName)
	    throws ItemExistsException, PathNotFoundException,
	    NoSuchNodeTypeException, ConstraintViolationException,
	    RepositoryException {
	// check state of this instance
	checkItemState();

	NodeTypeImpl nt = (NodeTypeImpl) session.getNodeTypeManager().getNodeType(nodeTypeName);
	return internalAddNode(relPath, nt);
    }

    /**
     * @see Node#orderBefore(String, String)
     */
    public void orderBefore(String srcName, String destName)
	    throws UnsupportedRepositoryOperationException, ConstraintViolationException,
	    ItemNotFoundException, RepositoryException {
	if (!nodeType.hasOrderableChildNodes()) {
	    throw new UnsupportedRepositoryOperationException("child node ordering not supported on node " + safeGetJCRPath());
	}

	// check arguments
	if (srcName.equals(destName)) {
	    throw new ConstraintViolationException("source and destination have to be different");
	}

	Path.PathElement insertName;
	try {
	    Path p = Path.create(srcName, session.getNamespaceResolver(), false);
	    if (p.getAncestorCount() > 0) {
		throw new RepositoryException("invalid name: " + srcName);
	    }
	    insertName = p.getNameElement();
	} catch (MalformedPathException e) {
	    String msg = "invalid name: " + srcName;
	    log.error(msg, e);
	    throw new RepositoryException(msg, e);
	}

	Path.PathElement beforeName;
	if (destName != null) {
	    try {
		Path p = Path.create(destName, session.getNamespaceResolver(), false);
		if (p.getAncestorCount() > 0) {
		    throw new RepositoryException("invalid name: " + destName);
		}
		beforeName = p.getNameElement();
	    } catch (MalformedPathException e) {
		String msg = "invalid name: " + destName;
		log.error(msg, e);
		throw new RepositoryException(msg, e);
	    }
	} else {
	    beforeName = null;
	}

	// check existence
	if (!hasNode(srcName)) {
	    throw new ItemNotFoundException(safeGetJCRPath() + " has no child node with name " + srcName);
	}
	if (destName != null && !hasNode(destName)) {
	    throw new ItemNotFoundException(safeGetJCRPath() + " has no child node with name " + destName);
	}

	// check if versioning allows write
	if (!safeIsCheckedOut()) {
	    String msg = safeGetJCRPath() + ": cannot change child node ordering of a checked-in node";
	    log.error(msg);
	    throw new VersionException(msg);
	}

	// check protected flag
	if (getDefinition().isProtected()) {
	    String msg = safeGetJCRPath() + ": cannot change child node ordering of a protected node";
	    log.error(msg);
	    throw new ConstraintViolationException(msg);
	}

	ArrayList list = new ArrayList(((NodeState) state).getChildNodeEntries());
	int srcInd = -1, destInd = -1;
	for (int i = 0; i < list.size(); i++) {
	    NodeState.ChildNodeEntry entry = (NodeState.ChildNodeEntry) list.get(i);
	    if (srcInd == -1) {
		if (entry.getName().equals(insertName.getName()) &&
			(entry.getIndex() == insertName.getIndex() ||
			insertName.getIndex() == 0 && entry.getIndex() == 1)) {
		    srcInd = i;
		}
	    }
	    if (destInd == -1 && beforeName != null) {
		if (entry.getName().equals(beforeName.getName()) &&
			(entry.getIndex() == beforeName.getIndex() ||
			beforeName.getIndex() == 0 && entry.getIndex() == 1)) {
		    destInd = i;
		    if (srcInd != -1) {
			break;
		    }
		}
	    } else {
		if (srcInd != -1) {
		    break;
		}
	    }
	}

	// check if resulting order would be different to current order
	if (destInd == -1) {
	    if (srcInd == list.size() - 1) {
		// no change, we're done
		return;
	    }
	} else {
	    if ((destInd - srcInd) == 1) {
		// no change, we're done
		return;
	    }
	}

	// reorder list
	if (destInd == -1) {
	    list.add(list.remove(srcInd));
	} else {
	    if (srcInd < destInd) {
		list.add(destInd, list.get(srcInd));
		list.remove(srcInd);
	    } else {
		list.add(destInd, list.remove(srcInd));
	    }
	}

	// modify the state of 'this', i.e. the parent node
	NodeState thisState = (NodeState) getOrCreateTransientItemState();
	thisState.setChildNodeEntries(list);
    }

    /**
     * @see Node#setProperty(String, Value[])
     */
    public Property setProperty(String name, Value[] values)
	    throws ValueFormatException, RepositoryException {
	// check state of this instance
	checkItemState();

	int type;
	if (values == null || values.length == 0) {
	    type = PropertyType.STRING;
	} else {
	    type = values[0].getType();
	}
	PropertyImpl prop = getOrCreateProperty(name, type);
	prop.setValue(values);
	return prop;
    }

    /**
     * @see Node#setProperty(String, String[])
     */
    public Property setProperty(String name, String[] values)
	    throws ValueFormatException, RepositoryException {
	// check state of this instance
	checkItemState();

	PropertyImpl prop = getOrCreateProperty(name, PropertyType.STRING);
	prop.setValue(values);
	return prop;
    }

    /**
     * @see Node#setProperty(String, String)
     */
    public Property setProperty(String name, String value) throws ValueFormatException, RepositoryException {
	// check state of this instance
	checkItemState();

	PropertyImpl prop = getOrCreateProperty(name, PropertyType.STRING);
	prop.setValue(value);
	return prop;
    }

    /**
     * @see Node#setProperty(String, Value)
     */
    public Property setProperty(String name, Value value)
	    throws ValueFormatException, RepositoryException {
	// check state of this instance
	checkItemState();

	int type = (value == null) ? PropertyType.STRING : value.getType();
	PropertyImpl prop = getOrCreateProperty(name, type);
	prop.setValue(value);
	return prop;
    }

    /**
     * @see Node#setProperty(String, InputStream)
     */
    public Property setProperty(String name, InputStream value)
	    throws ValueFormatException, RepositoryException {
	// check state of this instance
	checkItemState();

	PropertyImpl prop = getOrCreateProperty(name, PropertyType.BINARY);
	prop.setValue(value);
	return prop;
    }

    /**
     * @see Node#setProperty(String, boolean)
     */
    public Property setProperty(String name, boolean value)
	    throws ValueFormatException, RepositoryException {
	// check state of this instance
	checkItemState();

	PropertyImpl prop = getOrCreateProperty(name, PropertyType.BOOLEAN);
	prop.setValue(value);
	return prop;
    }

    /**
     * @see Node#setProperty(String, double)
     */
    public Property setProperty(String name, double value)
	    throws ValueFormatException, RepositoryException {
	// check state of this instance
	checkItemState();

	PropertyImpl prop = getOrCreateProperty(name, PropertyType.DOUBLE);
	prop.setValue(value);
	return prop;
    }

    /**
     * @see Node#setProperty(String, long)
     */
    public Property setProperty(String name, long value)
	    throws ValueFormatException, RepositoryException {
	// check state of this instance
	checkItemState();

	PropertyImpl prop = getOrCreateProperty(name, PropertyType.LONG);
	prop.setValue(value);
	return prop;
    }

    /**
     * @see Node#setProperty(String, Calendar)
     */
    public Property setProperty(String name, Calendar value)
	    throws ValueFormatException, RepositoryException {
	// check state of this instance
	checkItemState();

	PropertyImpl prop = getOrCreateProperty(name, PropertyType.DATE);
	prop.setValue(value);
	return prop;
    }

    /**
     * @see Node#setProperty(String, Node)
     */
    public Property setProperty(String name, Node value)
	    throws ValueFormatException, RepositoryException {
	// check state of this instance
	checkItemState();

	PropertyImpl prop = getOrCreateProperty(name, PropertyType.REFERENCE);
	prop.setValue(value);
	return prop;
    }

    /**
     * @see Node#getNode(String)
     */
    public Node getNode(String relPath)
	    throws PathNotFoundException, RepositoryException {
	// check state of this instance
	checkItemState();

	Path nodePath;
	try {
	    nodePath = Path.create(getPrimaryPath(), relPath, session.getNamespaceResolver(), true);
	} catch (MalformedPathException e) {
	    String msg = "failed to resolve path " + relPath + " relative to " + safeGetJCRPath();
	    log.error(msg, e);
	    throw new RepositoryException(msg, e);
	}

	try {
	    Item item = itemMgr.getItem(nodePath);
	    if (item.isNode()) {
		return (Node) item;
	    } else {
		// there's a property with that name, no child node though
		throw new PathNotFoundException(relPath);
	    }
	} catch (AccessDeniedException ade) {
	    throw new PathNotFoundException(relPath);
	}
    }

    /**
     * @see Node#getNodes()
     */
    public NodeIterator getNodes() throws RepositoryException {
	// check state of this instance
	checkItemState();

	/**
	 * IMPORTANT:
	 * an implementation of Node.getNodes()
	 * must not use a class derived from TraversingElementVisitor
	 * to traverse the hierarchy because this would lead to an infinite
	 * recursion!
	 */
	try {
	    return itemMgr.getChildNodes((NodeId) id);
	} catch (ItemNotFoundException infe) {
	    String msg = "failed to list the child nodes of " + safeGetJCRPath();
	    log.error(msg, infe);
	    throw new RepositoryException(msg, infe);
	} catch (AccessDeniedException ade) {
	    String msg = "failed to list the child nodes of " + safeGetJCRPath();
	    log.error(msg, ade);
	    throw new RepositoryException(msg, ade);
	}
    }

    /**
     * @see Node#getProperties()
     */
    public PropertyIterator getProperties() throws RepositoryException {
	// check state of this instance
	checkItemState();

	/**
	 * IMPORTANT:
	 * an implementation of Node.getProperties()
	 * must not use a class derived from TraversingElementVisitor
	 * to traverse the hierarchy because this would lead to an infinite
	 * recursion!
	 */
	try {
	    return itemMgr.getChildProperties((NodeId) id);
	} catch (ItemNotFoundException infe) {
	    String msg = "failed to list the child properties of " + safeGetJCRPath();
	    log.error(msg, infe);
	    throw new RepositoryException(msg, infe);
	} catch (AccessDeniedException ade) {
	    String msg = "failed to list the child properties of " + safeGetJCRPath();
	    log.error(msg, ade);
	    throw new RepositoryException(msg, ade);
	}
    }

    /**
     * @see Node#getProperty(String)
     */
    public Property getProperty(String relPath)
	    throws PathNotFoundException, RepositoryException {
	// check state of this instance
	checkItemState();

	Path propPath;
	try {
	    propPath = Path.create(getPrimaryPath(), relPath, session.getNamespaceResolver(), true);
	} catch (MalformedPathException e) {
	    String msg = "failed to resolve path " + relPath + " relative to " + safeGetJCRPath();
	    log.error(msg, e);
	    throw new RepositoryException(msg, e);
	}

	try {
	    Item item = itemMgr.getItem(propPath);
	    if (!item.isNode()) {
		return (Property) item;
	    } else {
		// there's a child node with that name, no property though
		throw new PathNotFoundException(relPath);
	    }
	} catch (AccessDeniedException ade) {
	    throw new PathNotFoundException(relPath);
	}
    }

    /**
     * @see Node#hasNode(String)
     */
    public boolean hasNode(String relPath) throws RepositoryException {
	try {
	    return getNode(relPath) != null;
	} catch (PathNotFoundException pnfe) {
	    return false;
	}
    }

    /**
     * @see Node#hasNodes()
     */
    public boolean hasNodes() throws RepositoryException {
	// check state of this instance
	checkItemState();

	/**
	 * hasNodes respects the access rights
	 * of this node's session, i.e. it will
	 * return false if child nodes exist
	 * but the session is not granted read-access
	 */
	ArrayList nodes = new ArrayList();
	// traverse children using a 'collector'
	accept(new ChildrenCollector(nodes, true, false, 1));
	return (nodes.size() > 0);
    }

    /**
     * @see Node#hasProperties()
     */
    public boolean hasProperties() throws RepositoryException {
	// check state of this instance
	checkItemState();

	/**
	 * hasProperties respects the access rights
	 * of this node's session, i.e. it will
	 * return false if properties exist
	 * but the session is not granted read-access
	 */
	ArrayList props = new ArrayList();
	// traverse children using a 'collector'
	accept(new ChildrenCollector(props, false, true, 1));
	return (props.size() > 0);
    }

    /**
     * @see Node#isNodeType(String)
     */
    public boolean isNodeType(String nodeTypeName) throws RepositoryException {
	QName ntName;
	try {
	    ntName = QName.fromJCRName(nodeTypeName, session.getNamespaceResolver());
	} catch (IllegalNameException ine) {
	    throw new RepositoryException("invalid node type name: " + nodeTypeName, ine);
	} catch (UnknownPrefixException upe) {
	    throw new RepositoryException("invalid node type name: " + nodeTypeName, upe);
	}
	return isNodeType(ntName);
    }

    /**
     * @see Node#isNodeType(String)
     */
    public boolean isNodeType(QName ntName) throws RepositoryException {
	// check state of this instance
	checkItemState();

	if (ntName.equals(nodeType.getQName())) {
	    return true;
	}

	if (nodeType.isDerivedFrom(ntName)) {
	    return true;
	}

	// check mixin types
	Set mixinNames = ((NodeState) state).getMixinTypeNames();
	if (mixinNames.isEmpty()) {
	    return false;
	}
	NodeTypeRegistry ntReg = session.getNodeTypeManager().getNodeTypeRegistry();
	try {
	    EffectiveNodeType ent = ntReg.buildEffectiveNodeType((QName[]) mixinNames.toArray(new QName[mixinNames.size()]));
	    return ent.includesNodeType(ntName);
	} catch (NodeTypeConflictException ntce) {
	    String msg = "internal error: invalid mixin node type(s)";
	    log.error(msg, ntce);
	    throw new RepositoryException(msg, ntce);
	}
    }


    /**
     * @see Node#getPrimaryNodeType()
     */
    public NodeType getPrimaryNodeType() throws RepositoryException {
	return nodeType;
    }

    /**
     * @see Node#getMixinNodeTypes()
     */
    public NodeType[] getMixinNodeTypes() throws RepositoryException {
	Set mixinNames = ((NodeState) state).getMixinTypeNames();
	if (mixinNames.isEmpty()) {
	    return new NodeType[0];
	}
	NodeType[] nta = new NodeType[mixinNames.size()];
	Iterator iter = mixinNames.iterator();
	int i = 0;
	while (iter.hasNext()) {
	    nta[i++] = session.getNodeTypeManager().getNodeType((QName) iter.next());
	}
	return nta;
    }

    /**
     * @see Node#addMixin(String)
     */
    public void addMixin(String mixinName)
	    throws NoSuchNodeTypeException, ConstraintViolationException,
	    RepositoryException {
	// check state of this instance
	checkItemState();

	// check if versioning allows write
	if (!safeIsCheckedOut()) {
	    String msg = safeGetJCRPath() + ": cannot add a mixin node type to a checked-in node";
	    log.error(msg);
	    throw new ConstraintViolationException(msg);
	}

	// check protected flag
	if (definition.isProtected()) {
	    String msg = safeGetJCRPath() + ": cannot add a mixin node type to a protected node";
	    log.error(msg);
	    throw new ConstraintViolationException(msg);
	}

	QName ntName;
	try {
	    ntName = QName.fromJCRName(mixinName, session.getNamespaceResolver());
	} catch (IllegalNameException ine) {
	    throw new RepositoryException("invalid mixin type name: " + mixinName, ine);
	} catch (UnknownPrefixException upe) {
	    throw new RepositoryException("invalid mixin type name: " + mixinName, upe);
	}

	NodeTypeManagerImpl ntMgr = session.getNodeTypeManager();
	NodeTypeImpl mixin = ntMgr.getNodeType(ntName);
	if (!mixin.isMixin()) {
	    throw new RepositoryException(mixinName + ": not a mixin node type");
	}
	if (nodeType.isDerivedFrom(ntName)) {
	    throw new RepositoryException(mixinName + ": already contained in primary node type");
	}

	// build effective node type of mixin's & primary type in order to detect conflicts
	NodeTypeRegistry ntReg = ntMgr.getNodeTypeRegistry();
	EffectiveNodeType entExisting;
	try {
	    // existing mixin's
	    HashSet set = new HashSet(((NodeState) state).getMixinTypeNames());
	    // primary type
	    set.add(nodeType.getQName());
	    // build effective node type representing primary type including existing mixin's
	    entExisting = ntReg.buildEffectiveNodeType((QName[]) set.toArray(new QName[set.size()]));
	    if (entExisting.includesNodeType(ntName)) {
		throw new RepositoryException(mixinName + ": already contained in mixin types");
	    }
	    // add new mixin
	    set.add(ntName);
	    // try to build new effective node type (will throw in case of conflicts)
	    ntReg.buildEffectiveNodeType((QName[]) set.toArray(new QName[set.size()]));
	} catch (NodeTypeConflictException ntce) {
	    throw new ConstraintViolationException(ntce.getMessage());
	}

	// modify the state of this node
	NodeState thisState = (NodeState) getOrCreateTransientItemState();
	// add mixin name
	Set mixins = new HashSet(thisState.getMixinTypeNames());
	mixins.add(ntName);
	thisState.setMixinTypeNames(mixins);

	// set jcr:mixinTypes property
	setMixinTypesProperty(mixins);

	// add 'auto-create' properties defined in mixin type
	PropertyDef[] pda = mixin.getAutoCreatePropertyDefs();
	for (int i = 0; i < pda.length; i++) {
	    PropertyDefImpl pd = (PropertyDefImpl) pda[i];
	    // make sure that the property is not already defined by primary type
	    // or existing mixin's
	    NodeTypeImpl declaringNT = (NodeTypeImpl) pd.getDeclaringNodeType();
	    if (!entExisting.includesNodeType(declaringNT.getQName())) {
		createChildProperty(pd.getQName(), pd.getRequiredType(), pd);
	    }
	}

	// recursively add 'auto-create' child nodes defined in mixin type
	NodeDef[] nda = mixin.getAutoCreateNodeDefs();
	for (int i = 0; i < nda.length; i++) {
	    NodeDefImpl nd = (NodeDefImpl) nda[i];
	    // make sure that the child node is not already defined by primary type
	    // or existing mixin's
	    NodeTypeImpl declaringNT = (NodeTypeImpl) nd.getDeclaringNodeType();
	    if (!entExisting.includesNodeType(declaringNT.getQName())) {
		createChildNode(nd.getQName(), nd, (NodeTypeImpl) nd.getDefaultPrimaryType(), null);
	    }
	}

	// check for special node types
	// todo centralize version history creation code (currently in NodeImpl.addMixin & ItemImpl.initVersionHistories
	if (ntName.equals(NodeTypeRegistry.MIX_VERSIONABLE)) {
	    VersionHistory hist = rep.getVersionManager().createVersionHistory(this);
	    internalSetProperty(VersionImpl.PROPNAME_VERSION_HISTORY, InternalValue.create(new UUID(hist.getUUID())));
	    internalSetProperty(VersionImpl.PROPNAME_BASE_VERSION, InternalValue.create(new UUID(hist.getRootVersion().getUUID())));
	    internalSetProperty(VersionImpl.PROPNAME_IS_CHECKED_OUT, InternalValue.create(true));
	    internalSetProperty(VersionImpl.PROPNAME_PREDECESSORS, new InternalValue[]{InternalValue.create(new UUID(hist.getRootVersion().getUUID()))});
	}
    }

    /**
     * @see Node#removeMixin(String)
     */
    public void removeMixin(String mixinName)
	    throws NoSuchNodeTypeException, ConstraintViolationException,
	    RepositoryException {
	// check state of this instance
	checkItemState();

	// check if versioning allows write
	if (!safeIsCheckedOut()) {
	    String msg = safeGetJCRPath() + ": cannot remove a mixin node type from a checked-in node";
	    log.error(msg);
	    throw new ConstraintViolationException(msg);
	}

	// check protected flag
	if (definition.isProtected()) {
	    String msg = safeGetJCRPath() + ": cannot remove a mixin node type from a protected node";
	    log.error(msg);
	    throw new ConstraintViolationException(msg);
	}

	QName ntName;
	try {
	    ntName = QName.fromJCRName(mixinName, session.getNamespaceResolver());
	} catch (IllegalNameException ine) {
	    throw new RepositoryException("invalid mixin type name: " + mixinName, ine);
	} catch (UnknownPrefixException upe) {
	    throw new RepositoryException("invalid mixin type name: " + mixinName, upe);
	}

	// check if mixin is assigned
	if (!((NodeState) state).getMixinTypeNames().contains(ntName)) {
	    throw new NoSuchNodeTypeException(mixinName);
	}

	if (NodeTypeRegistry.MIX_REFERENCEABLE.equals(ntName)) {
	    /**
	     * mix:referenceable needs special handling because it has
	     * special semantics:
	     * it can only be removed if there no more references to this node
	     */
	    PropertyIterator iter = getReferences();
	    if (iter.hasNext()) {
		throw new ConstraintViolationException(mixinName + " can not be removed: the node is being referenced through at least one property of type REFERENCE");
	    }
	}

	// modify the state of this node
	NodeState thisState = (NodeState) getOrCreateTransientItemState();
	// remove mixin name
	Set mixins = new HashSet(thisState.getMixinTypeNames());
	mixins.remove(ntName);
	thisState.setMixinTypeNames(mixins);

	// set jcr:mixinTypes property
	setMixinTypesProperty(mixins);

	// build effective node type of remaining mixin's & primary type
	NodeTypeManagerImpl ntMgr = session.getNodeTypeManager();
	NodeTypeRegistry ntReg = ntMgr.getNodeTypeRegistry();
	EffectiveNodeType entRemaining;
	try {
	    // remaining mixin's
	    HashSet set = new HashSet(mixins);
	    // primary type
	    set.add(nodeType.getQName());
	    // build effective node type representing primary type including remaining mixin's
	    entRemaining = ntReg.buildEffectiveNodeType((QName[]) set.toArray(new QName[set.size()]));
	} catch (NodeTypeConflictException ntce) {
	    throw new ConstraintViolationException(ntce.getMessage());
	}

	NodeTypeImpl mixin = session.getNodeTypeManager().getNodeType(ntName);

	// shortcut
	if (mixin.getChildNodeDefs().length == 0 &&
		mixin.getPropertyDefs().length == 0) {
	    // the node type has neither property nor child node definitions,
	    // i.e. we're done
	    return;
	}

	// walk through properties and child nodes and remove those that have been
	// defined by the specified mixin type

	// use temp array to avoid ConcurrentModificationException
	ArrayList tmp = new ArrayList(thisState.getPropertyEntries());
	Iterator iter = tmp.iterator();
	while (iter.hasNext()) {
	    NodeState.PropertyEntry entry = (NodeState.PropertyEntry) iter.next();
	    PropertyImpl prop = (PropertyImpl) itemMgr.getItem(new PropertyId(thisState.getUUID(), entry.getName()));
	    // check if property has been defined by mixin type (or one of its supertypes)
	    NodeTypeImpl declaringNT = (NodeTypeImpl) prop.getDefinition().getDeclaringNodeType();
	    if (!entRemaining.includesNodeType(declaringNT.getQName())) {
		// the remaining effective node type doesn't include the
		// node type that declared this property, it is thus safe
		// to remove it
		removeChildProperty(entry.getName());
	    }
	}
	// use temp array to avoid ConcurrentModificationException
	tmp = new ArrayList(thisState.getChildNodeEntries());
	iter = tmp.iterator();
	while (iter.hasNext()) {
	    NodeState.ChildNodeEntry entry = (NodeState.ChildNodeEntry) iter.next();
	    NodeImpl node = (NodeImpl) itemMgr.getItem(new NodeId(entry.getUUID()));
	    // check if node has been defined by mixin type (or one of its supertypes)
	    NodeTypeImpl declaringNT = (NodeTypeImpl) node.getDefinition().getDeclaringNodeType();
	    if (!entRemaining.includesNodeType(declaringNT.getQName())) {
		// the remaining effective node type doesn't include the
		// node type that declared this child node, it is thus safe
		// to remove it
		removeChildNode(entry.getName(), entry.getIndex());
	    }
	}
    }

    /**
     * @see Node#canAddMixin(String)
     */
    public boolean canAddMixin(String mixinName) throws RepositoryException {
	// check state of this instance
	checkItemState();

	// check if versioning allows write
	if (!safeIsCheckedOut()) {
	    return false;
	}

	// check protected flag
	if (definition.isProtected()) {
	    return false;
	}

	QName ntName;
	try {
	    ntName = QName.fromJCRName(mixinName, session.getNamespaceResolver());
	} catch (IllegalNameException ine) {
	    throw new RepositoryException("invalid mixin type name: " + mixinName, ine);
	} catch (UnknownPrefixException upe) {
	    throw new RepositoryException("invalid mixin type name: " + mixinName, upe);
	}

	NodeTypeManagerImpl ntMgr = session.getNodeTypeManager();
	NodeTypeImpl mixin = ntMgr.getNodeType(ntName);
	if (!mixin.isMixin()) {
	    return false;
	}
	if (nodeType.isDerivedFrom(ntName)) {
	    return false;
	}

	// build effective node type of mixins & primary type in order to detect conflicts
	NodeTypeRegistry ntReg = ntMgr.getNodeTypeRegistry();
	EffectiveNodeType entExisting;
	try {
	    // existing mixin's
	    HashSet set = new HashSet(((NodeState) state).getMixinTypeNames());
	    // primary type
	    set.add(nodeType.getQName());
	    // build effective node type representing primary type including existing mixin's
	    entExisting = ntReg.buildEffectiveNodeType((QName[]) set.toArray(new QName[set.size()]));
	    if (entExisting.includesNodeType(ntName)) {
		return false;
	    }
	    // add new mixin
	    set.add(ntName);
	    // try to build new effective node type (will throw in case of conflicts)
	    ntReg.buildEffectiveNodeType((QName[]) set.toArray(new QName[set.size()]));
	} catch (NodeTypeConflictException ntce) {
	    return false;
	}

	return true;
    }

    /**
     * @see Node#hasProperty(String)
     */
    public boolean hasProperty(String relPath) throws RepositoryException {
	try {
	    return getProperty(relPath) != null;
	} catch (PathNotFoundException pnfe) {
	    return false;
	}
    }

    /**
     * @see Node#getReferences()
     */
    public PropertyIterator getReferences() throws RepositoryException {
	WorkspaceImpl wsp = (WorkspaceImpl) session.getWorkspace();
	ReferenceManager refMgr = wsp.getReferenceManager();
	synchronized (refMgr) {
	    NodeReferences refs = refMgr.get((NodeId) id);
	    Iterator iter = refs.getReferences().iterator();
	    ArrayList list = new ArrayList();
	    while (iter.hasNext()) {
		PropertyId propId = (PropertyId) iter.next();
		list.add(itemMgr.getItem(propId));
	    }
	    return new IteratorHelper(list);
	}
    }

    /**
     * @see Node#getDefinition()
     */
    public NodeDef getDefinition() throws RepositoryException {
	return definition;
    }

    /**
     * @see Node#getNodes(String)
     */
    public NodeIterator getNodes(String namePattern) throws RepositoryException {
	// check state of this instance
	checkItemState();

	ArrayList nodes = new ArrayList();
	// traverse children using a special filtering 'collector'
	accept(new ChildrenCollectorFilter(namePattern, nodes, true, false, 1));
	return new IteratorHelper(Collections.unmodifiableList(nodes));
    }

    /**
     * @see Node#getProperties(String)
     */
    public PropertyIterator getProperties(String namePattern) throws RepositoryException {
	// check state of this instance
	checkItemState();

	ArrayList properties = new ArrayList();
	// traverse children using a special filtering 'collector'
	accept(new ChildrenCollectorFilter(namePattern, properties, false, true, 1));
	return new IteratorHelper(Collections.unmodifiableList(properties));
    }

    /**
     * @see Node#getPrimaryItem()
     */
    public Item getPrimaryItem() throws ItemNotFoundException, RepositoryException {
	// check state of this instance
	checkItemState();

	PropertyIterator propIter = getProperties();
	// check properties first
	while (propIter.hasNext()) {
	    Property p = propIter.nextProperty();
	    PropertyDef pd = p.getDefinition();
	    if (pd.isPrimaryItem()) {
		// found a 'primary' property
		return p;
	    }
	}
	// check child nodes
	NodeIterator nodeIter = getNodes();
	while (nodeIter.hasNext()) {
	    Node n = nodeIter.nextNode();
	    NodeDef nd = n.getDefinition();
	    if (nd.isPrimaryItem()) {
/*
		// found a 'primary' child node,
		// recursively check if it has a
		// primary child item
		try {
		    return n.getPrimaryItem();
		} catch (ItemNotFoundException infe) {
		    return n;
		}
*/
		// don't recurse
		return n;
	    }
	}
	throw new ItemNotFoundException();
    }

    /**
     * @see Node#getUUID()
     */
    public String getUUID() throws UnsupportedRepositoryOperationException, RepositoryException {
	// check state of this instance
	checkItemState();

	if (!isNodeType(NodeTypeRegistry.MIX_REFERENCEABLE)) {
	    throw new UnsupportedRepositoryOperationException();
	}

	NodeState thisState = (NodeState) state;
	return thisState.getUUID();
    }

    //-------------------------------------------------< versioning support >---
    /**
     * Checks if this node is versionable, i.e. has 'mix:versionable'.
     *
     * @throws UnsupportedRepositoryOperationException
     *          if this node is not versionable
     */
    private void checkVersionable()
	    throws UnsupportedRepositoryOperationException, RepositoryException {
	if (!isNodeType(NodeTypeRegistry.MIX_VERSIONABLE)) {
	    String msg = "Unable to perform versioning operation on non versionable node: " + safeGetJCRPath();
	    log.debug(msg);
	    throw new UnsupportedRepositoryOperationException(msg);
	}
    }

    /**
     * @see Node#checkin()
     */
    public Version checkin()
	    throws UnsupportedRepositoryOperationException, RepositoryException {

	if (!isCheckedOut()) {
	    String msg = "Unable to checkin node. Is not checked-out. " + safeGetJCRPath();
	    log.debug(msg);
	    throw new VersionException(msg);
	}
	// check transient state
	if (isModified()) {
	    String msg = "Unable to checkin node. Not allowed on transient node. " + safeGetJCRPath();
	    log.debug(msg);
	    throw new IllegalStateException(msg);
	}
	Version v = rep.getVersionManager().checkin(this);
	Property prop = internalSetProperty(VersionImpl.PROPNAME_IS_CHECKED_OUT, InternalValue.create(false));
	prop.save();
	prop = internalSetProperty(VersionImpl.PROPNAME_BASE_VERSION, InternalValue.create(new UUID(v.getUUID())));
	prop.save();
	prop = internalSetProperty(VersionImpl.PROPNAME_PREDECESSORS, new InternalValue[0]);
	prop.save();
	return v;
    }

    /**
     * @see Node#checkout()
     */
    public void checkout()
	    throws UnsupportedRepositoryOperationException, RepositoryException {
	if (isCheckedOut()) {
	    String msg = "Unable to checkout node. Is not checked-in. " + safeGetJCRPath();
	    log.debug(msg);
	    throw new VersionException(msg);
	}
	Property prop = internalSetProperty(VersionImpl.PROPNAME_IS_CHECKED_OUT, InternalValue.create(true));
	prop.save();
	prop = internalSetProperty(VersionImpl.PROPNAME_PREDECESSORS,
		new InternalValue[]{
		    InternalValue.create(new UUID(getBaseVersion().getUUID()))
		});
	prop.save();

    }

    /**
     * @see Node#addPredecessor(Version)
     */
    public void addPredecessor(Version v)
	    throws VersionException, UnsupportedRepositoryOperationException,
	    RepositoryException {
	if (!isCheckedOut()) {
	    throw new VersionException("Unable to add predecessor. Node not checked-out.");
	}

	// can only add predecessor of same version history
	if (!v.getParent().getUUID().equals(getVersionHistory().getUUID())) {
	    String msg = "Unable to add predecessor. Not same version history " + safeGetJCRPath();
	    log.debug(msg);
	    throw new VersionException(msg);
	}
	Value[] values = getProperty(VersionImpl.PROPNAME_PREDECESSORS).getValues();
	InternalValue[] preds = new InternalValue[values.length + 1];
	for (int i = 0; i < values.length; i++) {
	    if (values[i].getString().equals(v.getUUID())) {
		// ignore duplicates
		return;
	    }
	    preds[i + 1] = InternalValue.create(new UUID(values[i].getString()));
	}
	preds[0] = InternalValue.create(new UUID(v.getUUID()));
	Property prop = internalSetProperty(VersionImpl.PROPNAME_PREDECESSORS, preds);
	prop.save();
    }

    /**
     * @see Node#removePredecessor(Version)
     */
    public void removePredecessor(Version v)
	    throws VersionException, UnsupportedRepositoryOperationException,
	    RepositoryException {
	if (!isCheckedOut()) {
	    throw new VersionException("Unable to remove predecessor. Node not checked-out.");
	}

	Value[] values = getProperty(VersionImpl.PROPNAME_PREDECESSORS).getValues();
	if (values.length > 0) {
	    boolean found = false;
	    InternalValue[] preds = new InternalValue[values.length - 1];
	    for (int i = 0, j = 0; i < values.length; i++) {
		if (!values[i].getString().equals(v.getUUID())) {
		    if (j < preds.length) {
			preds[j++] = InternalValue.create(new UUID(values[i].getString()));
		    }
		} else {
		    found = true;
		}
	    }
	    if (found) {
		Property prop = internalSetProperty(VersionImpl.PROPNAME_PREDECESSORS, preds);
		prop.save();
		return;
	    }
	}

	String msg = "Unable to remove predecessor. Does not exist in version " + safeGetJCRPath();
	log.debug(msg);
	throw new VersionException(msg);
    }

    /**
     * @see Version#getPredecessors()
     */
    public Version[] getPredecessors() throws RepositoryException {
	if (hasProperty(VersionImpl.PROPNAME_PREDECESSORS)) {
	    Value[] values = getProperty(VersionImpl.PROPNAME_PREDECESSORS).getValues();
	    Version[] preds = new Version[values.length];
	    for (int i = 0; i < values.length; i++) {
		preds[i] = (Version) session.getNodeByUUID(values[i].getString());
	    }
	    return preds;
	}
	return new Version[0];
    }

    /**
     * @see Node#update(String)
     */
    public void update(String srcWorkspaceName)
	    throws NoSuchWorkspaceException, AccessDeniedException,
	    RepositoryException {

	// @todo Node.update has changed semantics; check with current spec...

	SessionImpl srcSession = rep.getSystemSession(srcWorkspaceName);
	// get src node either by uuid or path. since all nodes in the RI do
	// have UUIDs, should we always take the internal uuid?
	NodeImpl srcNode;
	if (isNodeType(NodeTypeRegistry.MIX_REFERENCEABLE)) {
	    srcNode = (NodeImpl) srcSession.getNodeByUUID(getUUID());
	} else {
	    srcNode = (NodeImpl) srcSession.getItem(getPath());
	}

	internalUpdate(srcNode, false, false);
	save();
    }

    /**
     * @see Node#merge(String, boolean)
     */
    public void merge(String srcWorkspace, boolean bestEffort)
	    throws UnsupportedRepositoryOperationException, NoSuchWorkspaceException,
	    AccessDeniedException, MergeException, RepositoryException {

	checkVersionable();
	SessionImpl srcSession = rep.getSystemSession(srcWorkspace);
	NodeImpl srcNode = (NodeImpl) srcSession.getNodeByUUID(getUUID());
	srcNode.checkVersionable();

	internalUpdate(srcNode, bestEffort, true);
	save();
    }

    /**
     * Internal helper that combines the functionalities of update and merge.
     *
     * @see Node#update(String)
     * @see Node#merge(String, boolean)
     */
    private void internalUpdate(NodeImpl srcNode, boolean bestEffort, boolean merge)
	    throws RepositoryException {

	boolean ignore = false;
	if (merge) {
	    if (!getVersionHistory().isSame(srcNode.getVersionHistory())) {
		String msg = "Unable to merge nodes. They have differen version histories " + safeGetJCRPath();
		log.debug(msg);
		throw new MergeException(msg);
	    }
	    VersionImpl v = (VersionImpl) getBaseVersion();
	    VersionImpl srcV = (VersionImpl) srcNode.getBaseVersion();
	    // check if version in src is newer
	    if (srcV.isMoreRecent(v)) {
		// src version is newer than this version
		ignore = false;
	    } else if (v.isMoreRecent(srcV)) {
		// this version is newer than src version, ignore
		ignore = true;
	    } else {
		// versions are same. according to spec -> merge exception
		// but 'ignore' seems to be better
		ignore = true;
	    }
	}
	if (!ignore) {
	    // copy the proerties
	    PropertyIterator piter = srcNode.getProperties();
	    while (piter.hasNext()) {
		PropertyImpl prop = (PropertyImpl) piter.nextProperty();
		switch (prop.getDefinition().getOnParentVersion()) {
		    case OnParentVersionAction.ABORT:
			throw new RepositoryException("Update aborted due to OPV in " + prop.safeGetJCRPath());
		    case OnParentVersionAction.COMPUTE:
		    case OnParentVersionAction.IGNORE:
		    case OnParentVersionAction.INITIALIZE:
			break;
		    case OnParentVersionAction.VERSION:
		    case OnParentVersionAction.COPY:
			Value[] values = prop.getValues();
			InternalValue[] ivalues = new InternalValue[values.length];
			for (int i = 0; i < values.length; i++) {
			    ivalues[i] = InternalValue.create(values[i], session.getNamespaceResolver());
			}
			internalSetProperty(prop.getQName(), ivalues);
			break;
		}
	    }
	}

	// copy the childnodes
	NodeIterator niter = srcNode.getNodes();
	while (niter.hasNext()) {
	    NodeImpl srcChild = (NodeImpl) niter.nextNode();
	    NodeImpl ownChild = null;
	    if (srcChild.isNodeType(NodeTypeRegistry.MIX_REFERENCEABLE)) {
		ownChild = (NodeImpl) srcChild.session.getNodeByUUID(srcChild.getUUID());
		if (!ownChild.getParent().isSame(this)) {
		    // source child is not at same location?
		    ownChild = null;
		}
	    } else {
		try {
		    ownChild = (NodeImpl) srcChild.session.getItem(srcChild.getPath());
		} catch (PathNotFoundException e) {
		    // ignore
		}
	    }

	    if (!ignore) {
		switch (srcChild.getDefinition().getOnParentVersion()) {
		    case OnParentVersionAction.ABORT:
			throw new RepositoryException("Update aborted due to OPV in " + srcChild.safeGetJCRPath());
		    case OnParentVersionAction.COMPUTE:
		    case OnParentVersionAction.IGNORE:
		    case OnParentVersionAction.INITIALIZE:
			break;
		    case OnParentVersionAction.VERSION:
			// todo: implement
			break;
		    case OnParentVersionAction.COPY:
			// todo: implement
			break;
		}
	    }

	    // If isDeep is set to true then every node with a UUID in the
	    // subtree rooted at this node is updated.

	    // @todo Node.merge has changed semantics; check with current spec...
	    if (bestEffort && ownChild != null && srcChild.isNodeType(NodeTypeRegistry.MIX_REFERENCEABLE)) {
		ownChild.internalUpdate(srcChild, true, merge);
	    }
	}
    }

    /**
     * @see Node#isCheckedOut()
     */
    public boolean isCheckedOut()
	    throws UnsupportedRepositoryOperationException, RepositoryException {
	checkVersionable();
	return getProperty(VersionImpl.PROPNAME_IS_CHECKED_OUT).getBoolean();
    }

    /**
     * Same as {@link #isCheckedOut()} but without UnsupportedException.
     */
    public boolean safeIsCheckedOut() throws RepositoryException {
	// what if this node is not versionable but has OPV==Copy?
	// do we need to search ancestors for a verionable node?
	return hasProperty(VersionImpl.PROPNAME_IS_CHECKED_OUT)
		? getProperty(VersionImpl.PROPNAME_IS_CHECKED_OUT).getBoolean()
		: true;
    }

    /**
     * @see Node#restore(String)
     */
    public void restore(String versionName)
	    throws VersionException, UnsupportedRepositoryOperationException,
	    RepositoryException {

	if (!isCheckedOut()) {
	    String msg = "Unable to restore version. Node is not checked-out " + safeGetJCRPath();
	    log.debug(msg);
	    throw new VersionException(msg);
	}

	GenericVersionSelector gvs = new GenericVersionSelector();
	gvs.setName(versionName);
	internalRestore((VersionImpl) getVersionHistory().getVersion(versionName), gvs);
	save();
    }

    /**
     * @see Node#restore(Version)
     */
    public void restore(Version version)
	    throws UnsupportedRepositoryOperationException, RepositoryException {

	if (!isCheckedOut()) {
	    String msg = "Unable to restore version. Node is not checked-out " + safeGetJCRPath();
	    log.debug(msg);
	    throw new VersionException(msg);
	}

	// check if 'own' version
	if (!version.getParent().getUUID().equals(getVersionHistory().getUUID())) {
	    throw new VersionException("Unable to restore version. Not same version history.");
	}
	internalRestore((VersionImpl) version, new GenericVersionSelector(version.getCreated()));
	save();
    }


    /**
     * @see Node#restore(Version, String)
     */
    public void restore(Version version, String relPath)
	    throws PathNotFoundException, ItemExistsException,
	    ConstraintViolationException, UnsupportedRepositoryOperationException,
	    RepositoryException {

	// if node exists, do a 'normal' restore
	if (hasNode(relPath)) {
	    getNode(relPath).restore(version);
	}

	// recreate node from frozen state
	NodeImpl node = addNode(relPath, (VersionImpl) version);
	if (!node.isCheckedOut()) {
	    String msg = "Unable to restore version. Node is not checked-out " + node.safeGetJCRPath();
	    log.debug(msg);
	    throw new VersionException(msg);
	}

	node.internalRestore((VersionImpl) version, new GenericVersionSelector(version.getCreated()));
	node.getParent().save();

    }

    /**
     * @see Node#restoreByLabel(String)
     */
    public void restoreByLabel(String versionLabel)
	    throws UnsupportedRepositoryOperationException, RepositoryException {

	if (!isCheckedOut()) {
	    String msg = "Unable to restore version. Node is not checked-out " + safeGetJCRPath();
	    log.debug(msg);
	    throw new VersionException(msg);
	}

	VersionImpl v = (VersionImpl) getVersionHistory().getVersionByLabel(versionLabel);
	if (v == null) {
	    throw new VersionException("No version for label " + versionLabel + " found.");
	}
	internalRestore(v, new GenericVersionSelector(versionLabel));
	save();
    }

    /**
     * Creates a new node at <code>relPath</code> of the node type, uuid and
     * eventual mixin types stored in the frozen node. The same as
     * <code>{@link #addNode(String relPath)}</code> except that the primary
     * node type type, the uuid and evt. mixin type of the new node is
     * explictly specified in the nt:frozen node.
     * <p/>
     *
     * @param relPath The path of the new <code>Node</code> that is to be created,
     *                the last item of this pathwill be the name of the new <code>Node</code>.
     * @param frozen  The frozen node that contains the creation information
     * @return The node that was added.
     * @throws ItemExistsException          If an item at the
     *                                      specified path already exists(and same-name siblings are not allowed).
     * @throws PathNotFoundException        If specified path implies intermediary
     *                                      <code>Node</code>s that do not exist.
     * @throws NoSuchNodeTypeException      If the specified <code>nodeTypeName</code>
     *                                      is not recognized.
     * @throws ConstraintViolationException If an attempt is made to add a node as the
     *                                      child of a <code>Property</code>
     * @throws RepositoryException          if another error occurs.
     */
    private NodeImpl addNode(String relPath, FrozenNode frozen)
	    throws RepositoryException {

	// get frozen node type
	NodeTypeManagerImpl ntMgr = session.getNodeTypeManager();
	String ntName = frozen.getProperty(VersionImpl.PROPNAME_FROZEN_PRIMARY_TYPE).getString();
	NodeTypeImpl nt = (NodeTypeImpl) ntMgr.getNodeType(ntName);

	// get frozen uuid
	String uuid = frozen.hasProperty(VersionImpl.PROPNAME_FROZEN_UUID)
		? frozen.getProperty(VersionImpl.PROPNAME_FROZEN_UUID).getString()
		: null;

	// get frozen mixin
	NodeImpl node = internalAddNode(relPath, nt, uuid);

	// todo: also respect mixing types on creation?
	if (frozen.hasProperty(VersionImpl.PROPNAME_FROZEN_MIXIN_TYPES)) {
	    Value[] mxNames = frozen.getProperty(VersionImpl.PROPNAME_FROZEN_MIXIN_TYPES).getValues();
	    for (int i = 0; i < mxNames.length; i++) {
		node.addMixin(mxNames[i].getString());
	    }
	}
	return node;
    }

    /**
     * Internal method to restore a version.
     *
     * @param version
     * @param vsel    the version selector that will select the correct version for
     *                OPV=Version childnodes.
     * @throws UnsupportedRepositoryOperationException
     *
     * @throws RepositoryException
     */
    private void internalRestore(VersionImpl version, VersionSelector vsel)
	    throws UnsupportedRepositoryOperationException, RepositoryException {

	// 1. The child node and properties of N will be changed, removed or
	//    added to, depending on their corresponding copies in V and their
	//    own OnParentVersion attributes (see 7.2.8, below, for details).
	restoreFrozenState(version, vsel);

	// 2. N�s jcr:baseVersion property will be changed to point to V.
	internalSetProperty(VersionImpl.PROPNAME_BASE_VERSION, InternalValue.create(new UUID(version.getUUID())));

	// 3. N�s jcr:isCheckedOut property is set to false.
	internalSetProperty(VersionImpl.PROPNAME_IS_CHECKED_OUT, InternalValue.create(false));

	// 4. N's jcr:predecessor property is set to null
	internalSetProperty(VersionImpl.PROPNAME_PREDECESSORS, new InternalValue[0]);
    }

    /**
     * Little helper to retrieve the opv value for a property. depends on the
     * implementaion. if nt:frozen is used, need to lookup prop def.
     *
     * @param prop
     * @return
     */
    private int getOPV(PropertyImpl prop) throws RepositoryException {
	PropertyDefImpl def = getApplicablePropertyDef(prop.getQName(), PropertyType.UNDEFINED);
	return def.getOnParentVersion();
    }

    /**
     * Creates the frozen state from a node
     *
     * @param freeze
     * @throws RepositoryException
     */
    void restoreFrozenState(FrozenNode freeze, VersionSelector vsel)
	    throws RepositoryException {
	PropertyIterator piter = freeze.getProperties();
	while (piter.hasNext()) {
	    PropertyImpl prop = (PropertyImpl) piter.nextProperty();
	    // check for special property
	    if (prop.getQName().equals(VersionImpl.PROPNAME_FROZEN_UUID)) {
		// check if uuid is the same as 'this' one.
		if (!isNodeType(NodeTypeRegistry.MIX_REFERENCEABLE)) {
		    throw new ItemExistsException("Unable to restore version of " + safeGetJCRPath() + ". Not referenceable.");
		}
		if (!prop.getString().equals(this.getUUID())) {
		    throw new ItemExistsException("Unable to restore version of " + safeGetJCRPath() + ". UUID changed.");
		}
	    } else if (prop.getQName().equals(VersionImpl.PROPNAME_FROZEN_PRIMARY_TYPE)) {
		// check if primaryType is the same as 'this' one.
		if (!prop.getString().equals(this.getPrimaryNodeType().getName())) {
		    // todo: check with spec what should happen here
		    throw new ItemExistsException("Unable to restore version of " + safeGetJCRPath() + ". PrimaryType changed.");
		}
	    } else if (prop.getQName().equals(VersionImpl.PROPNAME_FROZEN_MIXIN_TYPES)) {
		// add mixins
		Value[] values = prop.getValues();
		NodeType[] mixins = getMixinNodeTypes();
		for (int i = 0; i < values.length; i++) {
		    String name = values[i].getString();
		    boolean found = false;
		    for (int j = 0; j < mixins.length; j++) {
			if (name.equals(mixins[j].getName())) {
			    // clear
			    mixins[j] = null;
			    found = true;
			    break;
			}
		    }
		    if (!found) {
			addMixin(name);
		    }
		}
		// remove additional
		for (int i = 0; i < mixins.length; i++) {
		    if (mixins[i] != null) {
			removeMixin(mixins[i].getName());
		    }
		}
	    } else if (prop.getQName().equals(VersionImpl.PROPNAME_SUCCESSORS)) {
		// ignore
	    } else if (prop.getQName().equals(VersionImpl.PROPNAME_CREATED)) {
		// ignore
	    } else if (prop.getQName().equals(VersionImpl.PROPNAME_VERSION_LABELS)) {
		// ignore
	    } else if (prop.getQName().equals(VersionImpl.PROPNAME_VERSION_HISTORY)) {
		// ignore
	    } else if (prop.getQName().equals(VersionImpl.PROPNAME_PRIMARYTYPE)) {
		// ignore
	    } else if (prop.getQName().equals(VersionImpl.PROPNAME_MIXINTYPES)) {
		// ignore
	    } else if (prop.getQName().equals(VersionImpl.PROPNAME_UUID)) {
		// ignore
	    } else {
		// normal property
		int opv = getOPV(prop);
		switch (opv) {
		    case OnParentVersionAction.ABORT:
			throw new RepositoryException("Checkin aborted due to OPV in " + prop.safeGetJCRPath());
		    case OnParentVersionAction.COMPUTE:
		    case OnParentVersionAction.IGNORE:
		    case OnParentVersionAction.INITIALIZE:
			break;
		    case OnParentVersionAction.VERSION:
		    case OnParentVersionAction.COPY:
			internalCopyPropertyFrom(prop);
			break;
		}
	    }
	}

	// iterate over the nodes
	NodeIterator niter = freeze.getNodes();
	while (niter.hasNext()) {
	    NodeImpl child = (NodeImpl) niter.nextNode();
	    if (child.isNodeType(NodeTypeRegistry.NT_FROZEN)) {
		FrozenNode f = (FrozenNode) child;
		// if frozen node exist, replace
		// todo: make work for samename siblings
		if (hasNode(child.getName())) {
		    remove(child.getName());
		}
		NodeImpl n = addNode(child.getName(), f);
		n.restoreFrozenState(f, vsel);
	    } else if (child.isNodeType(NodeTypeRegistry.NT_FROZEN_VERSIONABLE_CHILD)) {
		// check if child already exists
		if (hasNode(child.getName())) {
		    // do nothing
		} else {
		    // get desired version from version selector
		    VersionHistory vh = (VersionHistory) child.getProperty(VersionImpl.PROPNAME_VERSION_HISTORY).getNode();
		    VersionImpl v = (VersionImpl) vsel.select(vh);
		    NodeImpl node = addNode(child.getName(), v);
		    node.internalRestore(v, vsel);
		}
	    }
	}
    }

    /**
     * @see Node#getVersionHistory()
     */
    public VersionHistory getVersionHistory()
	    throws UnsupportedRepositoryOperationException, RepositoryException {
	checkVersionable();
	return rep.getVersionManager().getVersionHistory(this);
    }

    /**
     * @see Node#getBaseVersion()
     */
    public Version getBaseVersion()
	    throws UnsupportedRepositoryOperationException, RepositoryException {
	checkVersionable();
	return rep.getVersionManager().getBaseVersion(this);
    }

    /**
     * Copies a property to this node
     *
     * @param prop
     * @throws RepositoryException
     */
    protected void internalCopyPropertyFrom(PropertyImpl prop) throws RepositoryException {
	if (prop.getDefinition().isMultiple()) {
	    Value[] values = prop.getValues();
	    InternalValue[] ivalues = new InternalValue[values.length];
	    for (int i = 0; i < values.length; i++) {
		ivalues[i] = InternalValue.create(values[i], session.getNamespaceResolver());
	    }
	    internalSetProperty(prop.getQName(), ivalues);
	} else {
	    InternalValue value = InternalValue.create(prop.getValue(), session.getNamespaceResolver());
	    internalSetProperty(prop.getQName(), value);
	}
    }


    //----------------------------------------------------< locking support >---
    /**
     * Checks if this node is lockable, i.e. has 'mix:lockable'.
     *
     * @throws UnsupportedRepositoryOperationException
     *          if this node is not lockable
     */
    private void checkLockable()
	    throws UnsupportedRepositoryOperationException, RepositoryException {
	if (!isNodeType(NodeTypeRegistry.MIX_LOCKABLE)) {
	    String msg = "Unable to perform locking operation on non lockable node: " + safeGetJCRPath();
	    log.debug(msg);
	    throw new UnsupportedRepositoryOperationException(msg);
	}
    }

    /**
     * @see Node#lock(boolean, boolean)
     */
    public Lock lock(boolean isDeep, boolean isSessionScoped)
	    throws UnsupportedRepositoryOperationException, LockException,
	    AccessDeniedException, RepositoryException {
	checkLockable();

	// @todo implement locking support
	throw new UnsupportedRepositoryOperationException();
    }

    /**
     * @see Node#getLock()
     */
    public Lock getLock()
	    throws UnsupportedRepositoryOperationException, LockException,
	    AccessDeniedException, RepositoryException {
	checkLockable();

	// @todo implement locking support
	throw new UnsupportedRepositoryOperationException();
    }

    /**
     * @see Node#unlock()
     */
    public void unlock()
	    throws UnsupportedRepositoryOperationException, LockException,
	    AccessDeniedException, RepositoryException {
	checkLockable();

	// @todo implement locking support
	throw new UnsupportedRepositoryOperationException();
    }

    /**
     * @see Node#holdsLock()
     */
    public boolean holdsLock() throws RepositoryException {
	// @todo implement locking support
	return false;
    }

    /**
     * @see Node#isLocked()
     */
    public boolean isLocked() throws RepositoryException {
	// @todo implement locking support
	return false;
    }
}

/**
 * <code>ChildrenCollectorFilter</code> is a utility class
 * which can be used to 'collect' child items of a
 * node whose names match a certain pattern. It implements the
 * <code>ItemVisitor</code> interface.
 *
 * @author Stefan Guggisberg
 */
class ChildrenCollectorFilter extends TraversingItemVisitor.Default {
    static final char WILDCARD_CHAR = '*';
    static final String OR = "|";

    private final Collection children;
    private final boolean collectNodes;
    private final boolean collectProperties;
    private final String namePattern;

    /**
     * Constructs a <code>ChildrenCollectorFilter</code>
     *
     * @param namePattern       the pattern which should be applied to the names
     *                          of the children
     * @param children          where the matching children should be added
     * @param collectNodes      true, if child nodes should be collected; otherwise false
     * @param collectProperties true, if child properties should be collected; otherwise false
     * @param maxLevel          umber of hierarchy levels to traverse
     *                          (e.g. 1 for direct children only, 2 for children and their children, and so on)
     */
    ChildrenCollectorFilter(String namePattern, Collection children, boolean collectNodes, boolean collectProperties, int maxLevel) {
	super(false, maxLevel);
	this.namePattern = namePattern;
	this.children = children;
	this.collectNodes = collectNodes;
	this.collectProperties = collectProperties;
    }

    /**
     * @see TraversingItemVisitor#entering(Node, int)
     */
    protected void entering(Node node, int level)
	    throws RepositoryException {
	if (level > 0 && collectNodes) {
	    if (matches(node.getName(), namePattern)) {
		children.add(node);
	    }
	}
    }

    /**
     * @see TraversingItemVisitor#entering(Property, int)
     */
    protected void entering(Property property, int level)
	    throws RepositoryException {
	if (level > 0 && collectProperties) {
	    if (matches(property.getName(), namePattern)) {
		children.add(property);
	    }
	}
    }

    /**
     * Applies the name pattern against the specified name.
     * <p/>
     * The pattern may be a full name or a partial name with one or more
     * wildcard characters ("*"), or a disjunction (using the "|" character
     * to represent logical <i>OR</i>) of these. For example,
     * <p/>
     * <code>"jcr:*|foo:bar"</code>
     * <p/>
     * would match
     * <p/>
     * <code>"foo:bar"</code>, but also <code>"jcr:whatever"</code>.
     *
     * @param name the name to test the pattern with
     * @return true if the specified name matches the pattern
     */
    static boolean matches(String name, String pattern) {
	// split pattern
	StringTokenizer st = new StringTokenizer(pattern, OR, false);
	while (st.hasMoreTokens()) {
	    if (internalMatches(name, st.nextToken(), 0, 0)) {
		return true;
	    }
	}
	return false;
    }

    /**
     * Internal helper used to recursively match the pattern
     *
     * @param s       The string to be tested
     * @param pattern The pattern
     * @param sOff    offset within <code>s</code>
     * @param pOff    offset within <code>pattern</code>.
     * @return true if <code>s</code> matched pattern, else false.
     */
    private static boolean internalMatches(String s, String pattern, int sOff, int pOff) {
	int pLen = pattern.length();
	int sLen = s.length();

	for (; ;) {
	    if (pOff >= pLen) {
		if (sOff >= sLen) {
		    return true;
		} else if (s.charAt(sOff) == '[') {
		    // check for subscript notation (e.g. "whatever[1]")

		    // the entire pattern matched up to the subscript:
		    // -> ignore the subscript
		    return true;
		} else {
		    return false;
		}
	    }
	    if (sOff >= sLen && pattern.charAt(pOff) != WILDCARD_CHAR) {
		return false;
	    }

	    // check for a '*' as the next pattern char;
	    // this is handled by a recursive call for
	    // each postfix of the name.
	    if (pattern.charAt(pOff) == WILDCARD_CHAR) {
		if (++pOff >= pLen) {
		    return true;
		}

		for (; ;) {
		    if (internalMatches(s, pattern, sOff, pOff)) {
			return true;
		    }
		    if (sOff >= sLen) {
			return false;
		    }
		    sOff++;
		}
	    }

	    if (pOff < pLen && sOff < sLen) {
		if (pattern.charAt(pOff) != s.charAt(sOff)) {
		    return false;
		}
	    }
	    pOff++;
	    sOff++;
	}
    }
}