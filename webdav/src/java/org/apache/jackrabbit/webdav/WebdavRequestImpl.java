/*
 * Copyright 2005 The Apache Software Foundation.
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
package org.apache.jackrabbit.webdav;

import org.apache.jackrabbit.webdav.header.CodedUrlHeader;
import org.apache.jackrabbit.webdav.header.DepthHeader;
import org.apache.jackrabbit.webdav.header.IfHeader;
import org.apache.jackrabbit.webdav.header.LabelHeader;
import org.apache.jackrabbit.webdav.header.OverwriteHeader;
import org.apache.jackrabbit.webdav.header.TimeoutHeader;
import org.apache.jackrabbit.webdav.lock.LockInfo;
import org.apache.jackrabbit.webdav.lock.Scope;
import org.apache.jackrabbit.webdav.lock.Type;
import org.apache.jackrabbit.webdav.observation.ObservationConstants;
import org.apache.jackrabbit.webdav.observation.SubscriptionInfo;
import org.apache.jackrabbit.webdav.ordering.OrderPatch;
import org.apache.jackrabbit.webdav.ordering.OrderingConstants;
import org.apache.jackrabbit.webdav.ordering.Position;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.transaction.TransactionConstants;
import org.apache.jackrabbit.webdav.transaction.TransactionInfo;
import org.apache.jackrabbit.webdav.version.LabelInfo;
import org.apache.jackrabbit.webdav.version.MergeInfo;
import org.apache.jackrabbit.webdav.version.OptionsInfo;
import org.apache.jackrabbit.webdav.version.UpdateInfo;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.ElementIterator;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * <code>WebdavRequestImpl</code>...
 */
public class WebdavRequestImpl implements WebdavRequest, DavConstants {

    private static Logger log = Logger.getLogger(WebdavRequestImpl.class);
    private static final DocumentBuilderFactory BUILDER_FACTORY = DocumentBuilderFactory.newInstance();

    private final HttpServletRequest httpRequest;
    private final DavLocatorFactory factory;
    private final IfHeader ifHeader;
    private final String hrefPrefix;

    private DavSession session;

    private int propfindType = PROPFIND_ALL_PROP;
    private DavPropertyNameSet propfindProps;
    private DavPropertySet proppatchSet;
    private DavPropertyNameSet proppatchRemove;

    /**
     * Creates a new <code>DavServletRequest</code> with the given parameters.
     *
     * @param httpRequest
     * @param factory
     */
    public WebdavRequestImpl(HttpServletRequest httpRequest, DavLocatorFactory factory) {
        this.httpRequest = httpRequest;
        this.factory = factory;
        this.ifHeader = new IfHeader(httpRequest);

        String host = getHeader("Host");
        String scheme = getScheme();
        hrefPrefix = scheme + "://" + host + getContextPath();
    }

    /**
     * Sets the session field and adds all lock tokens present with either the
     * Lock-Token header or the If header to the given session object.
     *
     * @param session
     * @see DavServletRequest#setDavSession(DavSession)
     */
    public void setDavSession(DavSession session) {
        this.session = session;
        // set lock-tokens from header to the current session
        if (session != null && session.getRepositorySession() != null) {
            String lt = getLockToken();
            if (lt != null) {
                session.addLockToken(lt);
            }
            // add all token present in the the If header to the session as well.
            Iterator it = ifHeader.getAllTokens();
            while (it.hasNext()) {
                String ifHeaderToken = (String) it.next();
                session.addLockToken(ifHeaderToken);
            }
        }
    }

    /**
     * @see DavServletRequest#getDavSession()
     */
    public DavSession getDavSession() {
        return session;
    }

    /**
     * Return a <code>DavResourceLocator</code> representing the request handle.
     *
     * @return locator of the requested resource
     * @see DavServletRequest#getRequestLocator()
     */
    public DavResourceLocator getRequestLocator() {
        String path = getRequestURI();
        String ctx = getContextPath();
        if (path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }
        return factory.createResourceLocator(hrefPrefix, path);
    }

    /**
     * Parse the destination header field and return the path of the destination
     * resource.
     *
     * @return path of the destination resource.
     * @see #HEADER_DESTINATION
     * @see DavServletRequest#getDestinationLocator
     */
    public DavResourceLocator getDestinationLocator() {
        String destination = httpRequest.getHeader(HEADER_DESTINATION);
        if (destination != null) {
            try {
                URI uri = new URI(destination);
                if (uri.getAuthority().equals(httpRequest.getHeader("Host"))) {
                    destination = uri.getRawPath();
                }
            } catch (URISyntaxException e) {
                log.debug("Destination is path is not a valid URI (" + e.getMessage() + ".");
                int pos = destination.lastIndexOf(":");
                if (pos > 0) {
                    destination = destination.substring(destination.indexOf("/", pos));
                    log.debug("Tried to retrieve resource destination path from invalid URI: " + destination);
                }
            }
            // cut off the context path
            String contextPath = httpRequest.getContextPath();
            if (destination.startsWith(contextPath)) {
                destination = destination.substring(contextPath.length());
            }
        }
        return factory.createResourceLocator(hrefPrefix, destination);
    }

    /**
     * Return true if the overwrite header does not inhibit overwriting.
     *
     * @return true if the overwrite header requests 'overwriting'
     * @see #HEADER_OVERWRITE
     * @see DavServletRequest#isOverwrite()
     */
    public boolean isOverwrite() {
        return new OverwriteHeader(httpRequest).isOverwrite();
    }

    /**
     * @see DavServletRequest#getDepth(int)
     */
    public int getDepth(int defaultValue) {
        return DepthHeader.parse(httpRequest, defaultValue).getDepth();
    }

    /**
     * @see DavServletRequest#getDepth()
     */
    public int getDepth() {
        return getDepth(DEPTH_INFINITY);
    }

    /**
     * Parse the Timeout header and return a long representing the value.
     * {@link #UNDEFINED_TIMEOUT} is used as default value if no header
     * is available or if the parsing fails.
     *
     * @return milliseconds indicating length of the timeout.
     * @see DavServletRequest#getTimeout()
     * @see TimeoutHeader#parse(javax.servlet.http.HttpServletRequest, long)
     */
    public long getTimeout() {
        return TimeoutHeader.parse(httpRequest, UNDEFINED_TIMEOUT).getTimeout();
    }

    /**
     * Retrive the lock token from the 'Lock-Token' header.
     *
     * @return String representing the lock token sent in the Lock-Token header.
     * @throws IllegalArgumentException If the value has not the correct format.
     * @see #HEADER_LOCK_TOKEN
     * @see DavServletRequest#getLockToken()
     */
    public String getLockToken() {
        return CodedUrlHeader.parse(httpRequest, HEADER_LOCK_TOKEN).getCodedUrl();
    }

    /**
     * @return Xml document
     * @see DavServletRequest#getRequestDocument()
     */
    public Document getRequestDocument() {
        Document requestDocument = null;
        /*
        Don't attempt to parse the body if the contentlength header is 0
        NOTE: a value of -1 indicates that the length is unknown, thus we have to parse the body.
        NOTE that http1.1 request using chunked transfer coding will therefore not be detected here
        */
        if (httpRequest.getContentLength() == 0) {
            return requestDocument;
        }
            // try to parse the request body
            try {
                InputStream in = httpRequest.getInputStream();
                if (in != null) {
                BUILDER_FACTORY.setNamespaceAware(true);
                DocumentBuilder docBuilder = BUILDER_FACTORY.newDocumentBuilder();
                requestDocument = docBuilder.parse(in);
                }
            } catch (IOException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Unable to build an XML Document from the request body: " + e.getMessage());
                }
        } catch (ParserConfigurationException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Unable to build an XML Document from the request body: " + e.getMessage());
            }
        } catch (SAXException e) {
            log.debug("Unable to build an XML Document from the request body: " + e.getMessage());
        }
        return requestDocument;
    }

    /**
     * Returns the type of PROPFIND as indicated by the request body.
     *
     * @return type of the PROPFIND request. Default value is {@link #PROPFIND_ALL_PROP allprops}
     * @see DavServletRequest#getPropFindType()
     */
    public int getPropFindType() throws DavException {
        if (propfindProps == null) {
            parsePropFindRequest();
        }
        return propfindType;
    }

    /**
     * Returns the set of properties requested by the PROPFIND body or an
     * empty set if the {@link #getPropFindType type} is either 'allprop' or
     * 'propname'.
     *
     * @return set of properties requested by the PROPFIND body or an empty set.
     * @see DavServletRequest#getPropFindProperties()
     */
    public DavPropertyNameSet getPropFindProperties() throws DavException {
        if (propfindProps == null) {
            parsePropFindRequest();
        }
        return propfindProps;
    }

    /**
     * Parse the propfind request body in order to determine the type of the propfind
     * and the set of requested property.
     * NOTE: An empty 'propfind' request body will be treated as request for all
     * property according to the specification.
     */
    private void parsePropFindRequest() throws DavException {
        propfindProps = new DavPropertyNameSet();
        Document requestDocument = getRequestDocument();
        // propfind httpRequest with empty body or invalid Xml >> retrieve all property
        // TODO: invalid XML -> spec requires a 'BAD REQUEST' error code
        if (requestDocument == null) {
            return;
        }

        // propfind httpRequest with invalid body
        Element root = requestDocument.getDocumentElement();
        if (!XML_PROPFIND.equals(root.getLocalName())) {
            log.info("PropFind-Request has no <profind> tag.");
            throw new DavException(DavServletResponse.SC_BAD_REQUEST, "PropFind-Request has no <profind> tag.");
        }

        ElementIterator it = DomUtil.getChildren(root);
        while (it.hasNext()) {
            Element child = it.nextElement();
            String nodeName = child.getLocalName();
            if (XML_PROP.equals(nodeName)) {
                propfindType = PROPFIND_BY_PROPERTY;
                propfindProps = new DavPropertyNameSet(child);
                break;
            } else if (XML_PROPNAME.equals(nodeName)) {
                propfindType = PROPFIND_PROPERTY_NAMES;
                break;
            } else if (XML_ALLPROP.equals(nodeName)) {
                propfindType = PROPFIND_ALL_PROP;
                break;
            }
        }
    }

    /**
     * Return the list of 'set' entries in the PROPPATCH request body. The list
     * is empty if the request body could not be parsed or if the request body did
     * not contain any 'set' elements.
     *
     * @return the list of 'set' entries in the PROPPATCH request body
     * @see DavServletRequest#getPropPatchSetProperties()
     */
    public DavPropertySet getPropPatchSetProperties() throws DavException {
        if (proppatchSet == null) {
            parsePropPatchRequest();
        }
        return proppatchSet;
    }

    /**
     * Return the list of 'remove' entries in the PROPPATCH request body. The list
     * is empty if the request body could not be parsed or if the request body did
     * not contain any 'remove' elements.
     *
     * @return the list of 'remove' entries in the PROPPATCH request body
     * @see DavServletRequest#getPropPatchRemoveProperties()
     */
    public DavPropertyNameSet getPropPatchRemoveProperties() throws DavException {
        if (proppatchRemove == null) {
            parsePropPatchRequest();
        }
        return proppatchRemove;
    }

    /**
     * Parse the PROPPATCH request body.
     */
    private void parsePropPatchRequest() throws DavException {

        proppatchSet = new DavPropertySet();
        proppatchRemove = new DavPropertyNameSet();
        Document requestDocument = getRequestDocument();

        if (requestDocument == null) {
            throw new DavException(DavServletResponse.SC_BAD_REQUEST, "Invalid request body.");
        }

        Element root = requestDocument.getDocumentElement();
        if (!DomUtil.matches(root, XML_PROPERTYUPDATE, NAMESPACE)) {
            // we should also check for correct namespace
            log.warn("PropPatch-Request has no <propertyupdate> tag.");
            throw new DavException(DavServletResponse.SC_BAD_REQUEST, "PropPatch-Request has no <propertyupdate> tag.");
        }

        ElementIterator it = DomUtil.getChildren(root, XML_SET, NAMESPACE);
                while (it.hasNext()) {
            Element propEl = DomUtil.getChildElement(it.nextElement(), XML_PROP, NAMESPACE);
            if (propEl != null) {
                ElementIterator properties = DomUtil.getChildren(propEl);
                while (properties.hasNext()) {
                   proppatchSet.add(DefaultDavProperty.createFromXml(properties.nextElement()));
                }
            }
        }

        // get <remove> properties
        it = DomUtil.getChildren(root, XML_REMOVE, NAMESPACE);
                while (it.hasNext()) {
            Element propEl = DomUtil.getChildElement(it.nextElement(), XML_PROP, NAMESPACE);
            if (propEl != null) {
                ElementIterator names = DomUtil.getChildren(propEl);
                while (names.hasNext()) {
                    proppatchRemove.add(DavPropertyName.createFromXml(names.nextElement()));
                }
            }
        }
    }

    /**
     * {@link LockInfo} object encapsulating the information passed with a LOCK
     * request if the LOCK request body was valid. If the request body is
     * missing a 'refresh lock' request is assumed. The {@link LockInfo}
     * then only provides timeout and isDeep property and returns true on
     * {@link org.apache.jackrabbit.webdav.lock.LockInfo#isRefreshLock()}
     *
     * @return lock info object or <code>null</code> if an error occured while
     *         parsing the request body.
     * @throws DavException throws a 400 (Bad Request) DavException if a request
     * body is present but does not start with a DAV:lockinfo element. Note however,
     * that a non-existing request body is a valid request used to refresh
     * an existing lock.
     * @see DavServletRequest#getLockInfo()
     */
    public LockInfo getLockInfo() throws DavException {
        LockInfo lockInfo;
        boolean isDeep = (getDepth(DEPTH_INFINITY) == DEPTH_INFINITY);
        Document requestDocument = getRequestDocument();
        // check if XML request body is present. It SHOULD have one for
        // 'create Lock' request and missing for a 'refresh Lock' request
        if (requestDocument != null) {
            Element root = requestDocument.getDocumentElement();
            if (root.getLocalName().equals(XML_LOCKINFO)) {
                lockInfo = new LockInfo(root, getTimeout(), isDeep);
            } else {
                log.debug("Lock request body must start with a DAV:lockinfo element.");
                throw new DavException(DavServletResponse.SC_BAD_REQUEST);
            }
        } else {
            lockInfo = new LockInfo(null, getTimeout(), isDeep);
        }
        return lockInfo;
    }

    /**
     * Test if the if header matches the given resource. The comparison is
     * made with the {@link DavResource#getHref()
     * resource href} and the token returned from an exclusive write lock present on
     * the resource.<br>
     * NOTE: If either the If header or the resource is <code>null</code> or if
     * the resource has not applied an exclusive write lock the preconditions are met.
     * If in contrast the lock applied to the given resource returns a
     * <code>null</code> lock token (e.g. for security reasons) or a lock token
     * that does not match, the method will return false.
     *
     * @param resource Webdav resources being operated on
     * @return true if the test is successful and the preconditions for the
     *         request processing are fulfilled.
     * @see DavServletRequest#matchesIfHeader(DavResource)
     * @see IfHeader#matches(String, String, String)
     * @see DavResource#hasLock(org.apache.jackrabbit.webdav.lock.Type, org.apache.jackrabbit.webdav.lock.Scope)
     * @see org.apache.jackrabbit.webdav.lock.ActiveLock#getToken()
     */
    public boolean matchesIfHeader(DavResource resource) {
        // no ifheader, no resource or no write lock on resource
        // >> preconditions ok so far
        if (!ifHeader.hasValue() || resource == null || !resource.hasLock(Type.WRITE, Scope.EXCLUSIVE)) {
            return true;
        }

        boolean isMatching = false;
        String lockToken = resource.getLock(Type.WRITE, Scope.EXCLUSIVE).getToken();
        if (lockToken != null) {
            isMatching = matchesIfHeader(resource.getHref(), lockToken, getStrongETag(resource));
        } // else: lockToken is null >> the if-header will not match.

        return isMatching;
    }

    /**
     * @see DavServletRequest#matchesIfHeader(String, String, String)
     * @see IfHeader#matches(String, String, String)
     */
    public boolean matchesIfHeader(String href, String token, String eTag) {
        return ifHeader.matches(href, token, isStrongETag(eTag) ?  eTag : "");
    }

    /**
     * Returns the strong etag present on the given resource or empty string
     * if either the resource does not provide any etag or if the etag is weak.
     *
     * @param resource
     * @return strong etag or empty string.
     */
    private String getStrongETag(DavResource resource) {
        DavProperty prop = resource.getProperty(DavPropertyName.GETETAG);
        if (prop != null && prop.getValue() != null) {
            String etag = prop.getValue().toString();
            if (isStrongETag(etag)) {
                return etag;
            }
        }
        // no strong etag available
        return "";
    }

    /**
     * Returns true if the given string represents a strong etag.
     *
     * @param eTag
     * @return true, if its a strong etag
     */
    private boolean isStrongETag(String eTag) {
        return eTag != null && eTag.length() > 0 && !eTag.startsWith("W\\");
    }

    //-----------------------------< TransactionDavServletRequest Interface >---
    /**
     * @see org.apache.jackrabbit.webdav.transaction.TransactionDavServletRequest#getTransactionId()
     */
    public String getTransactionId() {
        return CodedUrlHeader.parse(httpRequest, TransactionConstants.HEADER_TRANSACTIONID).getCodedUrl();
    }

    /**
     * @see org.apache.jackrabbit.webdav.transaction.TransactionDavServletRequest#getTransactionInfo()
     */
    public TransactionInfo getTransactionInfo() throws DavException {
        Document requestDocument = getRequestDocument();
        if (requestDocument != null) {
                return new TransactionInfo(requestDocument.getDocumentElement());
        }
        return null;
    }

    //-----------------------------< ObservationDavServletRequest Interface >---
    /**
     * @see org.apache.jackrabbit.webdav.observation.ObservationDavServletRequest#getSubscriptionId()
     */
    public String getSubscriptionId() {
        return CodedUrlHeader.parse(httpRequest, ObservationConstants.HEADER_SUBSCRIPTIONID).getCodedUrl();
    }

    /**
     * @see org.apache.jackrabbit.webdav.observation.ObservationDavServletRequest#getSubscriptionInfo()
     */
    public SubscriptionInfo getSubscriptionInfo() throws DavException {
        Document requestDocument = getRequestDocument();
        if (requestDocument != null) {
            Element root = requestDocument.getDocumentElement();
            if (ObservationConstants.XML_SUBSCRIPTIONINFO.equals(root.getLocalName())) {
                int depth = getDepth(DEPTH_0);
                return new SubscriptionInfo(root, getTimeout(), depth == DEPTH_INFINITY);
            }
        }
        return null;
    }

    //--------------------------------< OrderingDavServletRequest Interface >---
    /**
     * @see org.apache.jackrabbit.webdav.ordering.OrderingDavServletRequest#getOrderingType()
     */
    public String getOrderingType() {
        return getHeader(OrderingConstants.HEADER_ORDERING_TYPE);
    }

    /**
     * @see org.apache.jackrabbit.webdav.ordering.OrderingDavServletRequest#getPosition()
     */
    public Position getPosition() {
        String h = getHeader(OrderingConstants.HEADER_POSITION);
        Position pos = null;
        if (h != null) {
            String[] typeNSegment = h.split("\\s");
            if (typeNSegment.length == 2) {
                try {
                    pos = new Position(typeNSegment[0], typeNSegment[1]);
                } catch (IllegalArgumentException e) {
                    log.error("Cannot parse Position header: " + e.getMessage());
                }
            }
        }
        return pos;
    }

    /**
     * @return <code>OrderPatch</code> object representing the orderpatch request
     *         body or <code>null</code> if the
     * @see org.apache.jackrabbit.webdav.ordering.OrderingDavServletRequest#getOrderPatch()
     */
    public OrderPatch getOrderPatch() throws DavException {
        OrderPatch op = null;
        Document requestDocument = getRequestDocument();
        if (requestDocument != null) {
            Element root = requestDocument.getDocumentElement();
                op = OrderPatch.createFromXml(root);
        } else {
            log.error("Error while building xml document from ORDERPATH request body.");
        }
        return op;
    }

    //-------------------------------------< DeltaVServletRequest interface >---
    /**
     * @see org.apache.jackrabbit.webdav.version.DeltaVServletRequest#getLabel()
     */
    public String getLabel() {
        LabelHeader label = LabelHeader.parse(this);
        if (label != null) {
            return label.getLabel();
        }
        return null;
    }

    /**
     * @see org.apache.jackrabbit.webdav.version.DeltaVServletRequest#getLabelInfo()
     */
    public LabelInfo getLabelInfo() throws DavException {
        LabelInfo lInfo = null;
        Document requestDocument = getRequestDocument();
        if (requestDocument != null) {
            Element root = requestDocument.getDocumentElement();
            int depth = getDepth(DEPTH_0);
                lInfo = new LabelInfo(root, depth);
        }
        return lInfo;
    }

    /**
     * @see org.apache.jackrabbit.webdav.version.DeltaVServletRequest#getMergeInfo()
     */
    public MergeInfo getMergeInfo()  throws DavException {
        MergeInfo mInfo = null;
        Document requestDocument = getRequestDocument();
        if (requestDocument != null) {
                mInfo = new MergeInfo(requestDocument.getDocumentElement());
        }
        return mInfo;
    }

    /**
     * @see org.apache.jackrabbit.webdav.version.DeltaVServletRequest#getUpdateInfo()
     */
    public UpdateInfo getUpdateInfo() throws DavException  {
        UpdateInfo uInfo = null;
        Document requestDocument = getRequestDocument();
        if (requestDocument != null) {
                uInfo = new UpdateInfo(requestDocument.getDocumentElement());
        }
        return uInfo;
    }

    /**
     * @see org.apache.jackrabbit.webdav.version.DeltaVServletRequest#getReportInfo()
     */
    public ReportInfo getReportInfo() throws DavException  {
        ReportInfo rInfo = null;
        Document requestDocument = getRequestDocument();
        if (requestDocument != null) {
            rInfo = new ReportInfo(requestDocument.getDocumentElement(), getDepth(DEPTH_0));
        }
        return rInfo;
    }

    /**
     * @see org.apache.jackrabbit.webdav.version.DeltaVServletRequest#getOptionsInfo()
     */
    public OptionsInfo getOptionsInfo() throws DavException {
        OptionsInfo info = null;
        Document requestDocument = getRequestDocument();
        if (requestDocument != null) {
            info = OptionsInfo.createFromXml(requestDocument.getDocumentElement());
        }
        return info;
    }

    //---------------------------------------< HttpServletRequest interface >---
    public String getAuthType() {
        return httpRequest.getAuthType();
    }

    public Cookie[] getCookies() {
        return httpRequest.getCookies();
    }

    public long getDateHeader(String s) {
        return httpRequest.getDateHeader(s);
    }

    public String getHeader(String s) {
        return httpRequest.getHeader(s);
    }

    public Enumeration getHeaders(String s) {
        return httpRequest.getHeaders(s);
    }

    public Enumeration getHeaderNames() {
        return httpRequest.getHeaderNames();
    }

    public int getIntHeader(String s) {
        return httpRequest.getIntHeader(s);
    }

    public String getMethod() {
        return httpRequest.getMethod();
    }

    public String getPathInfo() {
        return httpRequest.getPathInfo();
    }

    public String getPathTranslated() {
        return httpRequest.getPathTranslated();
    }

    public String getContextPath() {
        return httpRequest.getContextPath();
    }

    public String getQueryString() {
        return httpRequest.getQueryString();
    }

    public String getRemoteUser() {
        return httpRequest.getRemoteUser();
    }

    public boolean isUserInRole(String s) {
        return httpRequest.isUserInRole(s);
    }

    public Principal getUserPrincipal() {
        return httpRequest.getUserPrincipal();
    }

    public String getRequestedSessionId() {
        return httpRequest.getRequestedSessionId();
    }

    public String getRequestURI() {
        return httpRequest.getRequestURI();
    }

    public StringBuffer getRequestURL() {
        return httpRequest.getRequestURL();
    }

    public String getServletPath() {
        return httpRequest.getServletPath();
    }

    public HttpSession getSession(boolean b) {
        return httpRequest.getSession(b);
    }

    public HttpSession getSession() {
        return httpRequest.getSession();
    }

    public boolean isRequestedSessionIdValid() {
        return httpRequest.isRequestedSessionIdValid();
    }

    public boolean isRequestedSessionIdFromCookie() {
        return httpRequest.isRequestedSessionIdFromCookie();
    }

    public boolean isRequestedSessionIdFromURL() {
        return httpRequest.isRequestedSessionIdFromURL();
    }

    public boolean isRequestedSessionIdFromUrl() {
        return httpRequest.isRequestedSessionIdFromUrl();
    }

    public Object getAttribute(String s) {
        return httpRequest.getAttribute(s);
    }

    public Enumeration getAttributeNames() {
        return httpRequest.getAttributeNames();
    }

    public String getCharacterEncoding() {
        return httpRequest.getCharacterEncoding();
    }

    public void setCharacterEncoding(String s) throws UnsupportedEncodingException {
        httpRequest.setCharacterEncoding(s);
    }

    public int getContentLength() {
        return httpRequest.getContentLength();
    }

    public String getContentType() {
        return httpRequest.getContentType();
    }

    public ServletInputStream getInputStream() throws IOException {
        return httpRequest.getInputStream();
    }

    public String getParameter(String s) {
        return httpRequest.getParameter(s);
    }

    public Enumeration getParameterNames() {
        return httpRequest.getParameterNames();
    }

    public String[] getParameterValues(String s) {
        return httpRequest.getParameterValues(s);
    }

    public Map getParameterMap() {
        return httpRequest.getParameterMap();
    }

    public String getProtocol() {
        return httpRequest.getProtocol();
    }

    public String getScheme() {
        return httpRequest.getScheme();
    }

    public String getServerName() {
        return httpRequest.getServerName();
    }

    public int getServerPort() {
        return httpRequest.getServerPort();
    }

    public BufferedReader getReader() throws IOException {
        return httpRequest.getReader();
    }

    public String getRemoteAddr() {
        return httpRequest.getRemoteAddr();
    }

    public String getRemoteHost() {
        return httpRequest.getRemoteHost();
    }

    public void setAttribute(String s, Object o) {
        httpRequest.setAttribute(s, o);
    }

    public void removeAttribute(String s) {
        httpRequest.removeAttribute(s);
    }

    public Locale getLocale() {
        return httpRequest.getLocale();
    }

    public Enumeration getLocales() {
        return httpRequest.getLocales();
    }

    public boolean isSecure() {
        return httpRequest.isSecure();
    }

    public RequestDispatcher getRequestDispatcher(String s) {
        return httpRequest.getRequestDispatcher(s);
    }

    public String getRealPath(String s) {
        return httpRequest.getRealPath(s);
    }
}