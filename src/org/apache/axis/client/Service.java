/*
* The Apache Software License, Version 1.1
*
*
* Copyright (c) 2001 The Apache Software Foundation.  All rights
* reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions
* are met:
*
* 1. Redistributions of source code must retain the above copyright
*    notice, this list of conditions and the following disclaimer.
*
* 2. Redistributions in binary form must reproduce the above copyright
*    notice, this list of conditions and the following disclaimer in
*    the documentation and/or other materials provided with the
*    distribution.
*
* 3. The end-user documentation included with the redistribution,
*    if any, must include the following acknowledgment:
*       "This product includes software developed by the
*        Apache Software Foundation (http://www.apache.org/)."
*    Alternately, this acknowledgment may appear in the software itself,
*    if and wherever such third-party acknowledgments normally appear.
*
* 4. The names "Axis" and "Apache Software Foundation" must
*    not be used to endorse or promote products derived from this
*    software without prior written permission. For written
*    permission, please contact apache@apache.org.
*
* 5. Products derived from this software may not be called "Apache",
*    nor may "Apache" appear in their name, without prior written
*    permission of the Apache Software Foundation.
*
* THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
* OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
* ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
* SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
* LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
* USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
* ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
* OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
* OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
* SUCH DAMAGE.
* ====================================================================
*
* This software consists of voluntary contributions made by many
* individuals on behalf of the Apache Software Foundation.  For more
* information on the Apache Software Foundation, please see
* <http://www.apache.org/>.
*/

package org.apache.axis.client ;

import org.apache.axis.AxisEngine;
import org.apache.axis.EngineConfiguration;
import org.apache.axis.configuration.EngineConfigurationFactoryFinder;
import org.apache.axis.utils.ClassUtils;
import org.apache.axis.utils.Messages;
import org.apache.axis.utils.WSDLUtils;
import org.apache.axis.utils.XMLUtils;
import org.apache.axis.wsdl.gen.Parser;
import org.apache.axis.wsdl.symbolTable.ServiceEntry;
import org.w3c.dom.Document;

import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import javax.wsdl.Binding;
import javax.wsdl.Port;
import javax.wsdl.PortType;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.xml.namespace.QName;
import javax.xml.rpc.ServiceException;
import javax.xml.rpc.encoding.TypeMappingRegistry;
import javax.xml.rpc.handler.HandlerRegistry;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.Remote;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * Axis' JAXRPC Dynamic Invoation Interface implementation of the Service
 * interface.
 *
 * The Service class should be used a the starting point for access
 * SOAP Web Services.  Typically, a Service will be created with a WSDL
 * document and along with a serviceName you can then ask for a Call
 * object that will allow you to invoke a Web Service.
 *
 * @author Doug Davis (dug@us.ibm.com)
 */

public class Service implements javax.xml.rpc.Service, Serializable, Referenceable {
    private transient AxisEngine          engine = null;
    private transient EngineConfiguration config = null;

    private QName               serviceName     = null ;
    private URL                 wsdlLocation    = null ;
    private javax.wsdl.Service  wsdlService     = null ;
    private boolean             maintainSession = false ;
    private HandlerRegistryImpl registry = new HandlerRegistryImpl();
    private Parser              wsdlParser      = null;

    /**
     * Thread local storage used for storing the last call object
     */
    private static ThreadLocal previousCall = new ThreadLocal();
    private static HashMap      cachedWSDL  = new HashMap();
    private static boolean      cachingWSDL = true ;

    /**
     * A Hashtable mapping addresses (URLs) to Transports (objects)
     */
    private Hashtable transportImpls = new Hashtable();

    
    protected javax.wsdl.Service getWSDLService() {
        return( wsdlService );
    }

    public Parser getWSDLParser() {
        return( wsdlParser );
    }
    
    protected AxisClient getAxisClient()
    {
        return new AxisClient(getEngineConfiguration());
    }

    /**
     * Constructs a new Service object - this assumes the caller will set
     * the appropriate fields by hand rather than getting them from the
     * WSDL.
     */
    public Service() {
        engine = getAxisClient();
    }

    /**
     * Constructs a new Service object - this assumes the caller will set
     * the appropriate fields by hand rather than getting them from the
     * WSDL.
     */
    public Service(QName serviceName) {
        this.serviceName = serviceName;
        engine = getAxisClient();
    }

    /**
     * Constructs a new Service object as above, but also passing in
     * the EngineConfiguration which should be used to set up the
     * AxisClient.
     */
    public Service(EngineConfiguration config) {
        this.config = config;
        engine = getAxisClient();
    }

    /**
     * Constructs a new Service object for the service in the WSDL document
     * pointed to by the wsdlDoc URL and serviceName parameters.
     *
     * @param wsdlDoc          URL of the WSDL document
     * @param serviceName      Qualified name of the desired service
     * @throws ServiceException If there's an error finding or parsing the WSDL
     */
    public Service(URL wsdlDoc, QName serviceName) throws ServiceException {
        this.serviceName = serviceName;
        engine = getAxisClient();
        this.wsdlLocation = wsdlDoc;
        Parser parser = null ;

        if ( cachingWSDL &&
             (parser = (Parser) cachedWSDL.get(this.wsdlLocation.toString())) != null ){
          initService( parser, serviceName );
        }
        else {
            Document doc = null;
            try {
                doc = XMLUtils.newDocument(wsdlDoc.toString());
            } catch (Exception exp ) {
                throw new ServiceException(
                   Messages.getMessage("wsdlError00", "" + "", "\n" + exp) );
            }
            initService(doc, serviceName);
        }
    }

    /**
     * Constructs a new Service object for the service in the WSDL document
     *
     * @param parser          Parser for this service
     * @param serviceName      Qualified name of the desired service
     * @throws ServiceException If there's an error 
     */
    public Service(Parser parser, QName serviceName) throws ServiceException {
        this.serviceName = serviceName;
        engine = getAxisClient();
        initService( parser, serviceName );
    }

    /**
     * Constructs a new Service object for the service in the WSDL document
     * pointed to by the wsdlLocation and serviceName parameters.  This is
     * just like the previous constructor but instead of URL the
     * wsdlLocation parameter points to a file on the filesystem relative
     * to the current directory.
     *
     * @param  wsdlLocation    Location of the WSDL relative to the current dir
     * @param  serviceName     Qualified name of the desired service
     * @throws ServiceException If there's an error finding or parsing the WSDL
     */
    public Service(String wsdlLocation, QName serviceName)
                           throws ServiceException {
        this.serviceName = serviceName;
        engine = getAxisClient();
        try {
            this.wsdlLocation = new URL(wsdlLocation);
        }
        catch (MalformedURLException mue) {
        }
        // Start by reading in the WSDL using Parser
        Parser parser = null ;
        if ( cachingWSDL &&
             (parser = (Parser) cachedWSDL.get(this.wsdlLocation.toString())) != null ) {
          initService( parser, serviceName );
        }
        else {
            Document doc = null;
            try {
                doc = XMLUtils.newDocument(this.wsdlLocation.toString());
            } catch (Exception exp ) {
                throw new ServiceException(
                   Messages.getMessage("wsdlError00", "" + "", "\n" + exp) );
            }
          initService(doc, serviceName);
        }
    }

    /**
     * Constructs a new Service object for the service in the WSDL document
     * in the wsdlInputStream and serviceName parameters.  This is
     * just like the previous constructor but instead of reading the WSDL
     * from a file (or from a URL) it is in the passed in InputStream.
     *
     * @param  wsdlInputStream InputStream containing the WSDL
     * @param  serviceName     Qualified name of the desired service
     * @throws ServiceException If there's an error finding or parsing the WSDL
     */
    public Service(InputStream wsdlInputStream, QName serviceName)
                           throws ServiceException {
        engine = getAxisClient();
        Document doc = null;
        try {
            doc = XMLUtils.newDocument(wsdlInputStream);
        } catch (Exception exp ) {
            throw new ServiceException(
               Messages.getMessage("wsdlError00", "" + "", "\n" + exp) );
        }
        initService(doc, serviceName);
    }

    /**
     * Common code for building up the Service from a WSDL document
     *
     * @param doc               A DOM document containing WSDL
     * @param serviceName       Qualified name of the desired service
     * @throws ServiceException  If there's an error finding or parsing the WSDL
     */
    private void initService(Document doc, QName serviceName)
            throws ServiceException {
        try {
            // Start by reading in the WSDL using Parser
            Parser parser = new Parser();
            parser.run(this.wsdlLocation.toString());

            if ( cachingWSDL && this.wsdlLocation != null )
              cachedWSDL.put( this.wsdlLocation.toString(), parser );

            initService( parser, serviceName );
        }
        catch( Exception exp ) {
            throw new ServiceException(
                    Messages.getMessage("wsdlError00", "" + "", "\n" + exp) );
        }
    }

    private void initService(Parser parser, QName serviceName)
            throws ServiceException {
        try {
            this.wsdlParser = parser ;
            ServiceEntry serviceEntry = parser.getSymbolTable().getServiceEntry(serviceName);
            if ( serviceEntry != null)
                this.wsdlService    = serviceEntry.getService(); 
            if ( this.wsdlService == null )
                throw new ServiceException(
                        Messages.getMessage("noService00", "" + serviceName));
        }
        catch( Exception exp ) {
            throw new ServiceException(
                    Messages.getMessage("wsdlError00", "" + "", "\n" + exp) );
        }
    }

    /**
     * Return either an instance of a generated stub, if it can be
     * found, or a dynamic proxy for the given proxy interface.
     *
     * @param  portName        The name of the service port
     * @param  proxyInterface  The Remote object returned by this
     *         method will also implement the given proxyInterface
     * @return java.rmi.Remote The stub implementation.
     * @throws ServiceException If there's an error
     */
    public Remote getPort(QName portName, Class proxyInterface)
                           throws ServiceException {

        if (wsdlService == null)
            throw new ServiceException(Messages.getMessage("wsdlMissing00"));

        Port port = wsdlService.getPort( portName.getLocalPart() );
        if ( port == null )
            throw new ServiceException( Messages.getMessage("noPort00", "" + portName) );

        // First, try to find a generated stub.  If that
        // returns null, then find a dynamic stub.
        Remote stub = getGeneratedStub(portName, proxyInterface);
        return stub != null ? stub : getPort(null, portName, proxyInterface);
    }

    /**
     * With the proxyInterface and the service's portName, we have
     * ALMOST enough info to find a generated stub.  The generated
     * stub is named after the binding, which we can get from the
     * service's port.  This binding is likely in the same namespace
     * (ie, package) that the proxyInterface is in.  So try to find
     * and instantiate <proxyInterfacePackage>.<bindingName>Stub.
     * If it doesn't exist, return null.
     */
    private Remote getGeneratedStub(QName portName, Class proxyInterface) {
        try {
            String pkg = proxyInterface.getName();
            pkg = pkg.substring(0, pkg.lastIndexOf('.'));
            Port port = wsdlService.getPort(portName.getLocalPart());
            String binding = port.getBinding().getQName().getLocalPart();
            Class stubClass = ClassUtils.forName(
                    pkg + "." + binding + "Stub");
            if (proxyInterface.isAssignableFrom(stubClass)) {
                Class[] formalArgs = {javax.xml.rpc.Service.class};
                Object[] actualArgs = {this};
                Constructor ctor = stubClass.getConstructor(formalArgs);
                Stub stub = (Stub) ctor.newInstance(actualArgs);
                stub._setProperty(
                        Stub.ENDPOINT_ADDRESS_PROPERTY,
                        WSDLUtils.getAddressFromPort(port));
                stub.setPortName(portName);
                return (Remote) stub;
            }
            else {
                return null;
            }
        }
        catch (Throwable t) {
            return null;
        }
    } // getGeneratedStub

    /**
     * Return a dynamic proxy for the given proxy interface.
     *
     * @param  proxyInterface  The Remote object returned by this
     * method will also implement the given proxyInterface
     * @return java.rmi.Remote The stub implementation
     * @throws ServiceException If there's an error
     */
    public Remote getPort(Class proxyInterface) throws ServiceException {
        if (wsdlService == null)
            throw new ServiceException(Messages.getMessage("wsdlMissing00"));

        return getPort(null, null, proxyInterface);
    }

    /**
     * Return an object which acts as a dynamic proxy for the passed
     * interface class.  This is a more "dynamic" version in that it
     * doesn't actually require WSDL, simply an endpoint address.
     *
     * Note: Not part of the JAX-RPC spec.
     *
     * @param endpoint the URL which will be used as the SOAP endpoint
     * @param proxyInterface the interface class which we wish to mimic
     *                       via a dynamic proxy
     * @throws ServiceException
     */
    public Remote getPort(String endpoint, Class proxyInterface)
        throws ServiceException
    {
        return getPort(endpoint, null, proxyInterface);
    }

    private Remote getPort(String endpoint, QName portName,
            Class proxyInterface) throws ServiceException {
        if (!proxyInterface.isInterface()) {
            throw new ServiceException(Messages.getMessage("mustBeIface00"));
        }

        if (!(Remote.class.isAssignableFrom(proxyInterface))) {
            throw new ServiceException(
                            Messages.getMessage("mustExtendRemote00"));
        }

        try {
            Call call = null;
            if (portName == null) {
                call = (org.apache.axis.client.Call) createCall();
                if (endpoint != null) {
                    call.setTargetEndpointAddress(new URL(endpoint));
                }
            }
            else {
                call = (org.apache.axis.client.Call) createCall(portName);
            }
            ClassLoader classLoader =
                    Thread.currentThread().getContextClassLoader();
            return (Remote)Proxy.newProxyInstance(classLoader,
                    new Class[] { proxyInterface, javax.xml.rpc.Stub.class },
                    new AxisClientProxy(call));
        } catch (Exception e) {
            throw new ServiceException(e.toString());
        }
    } // getPort

    /**
     * Creates a new Call object - will prefill as much info from the WSDL
     * as it can.  Right now it's just the target URL of the Web Service.
     *
     * @param  portName        PortName in the WSDL doc to search for
     * @return Call            Used for invoking the Web Service
     * @throws ServiceException If there's an error
     */
    public javax.xml.rpc.Call createCall(QName portName)
                            throws ServiceException {
        Call call = (org.apache.axis.client.Call)createCall();
        call.setPortName( portName );

        // We can't prefill information if WSDL is not specified,
        // So just return the call that we just created.
        if ( wsdlParser == null )
            return call;

        Port port = wsdlService.getPort( portName.getLocalPart() );
        if ( port == null )
            throw new ServiceException( Messages.getMessage("noPort00", "" + portName) );

        Binding   binding  = port.getBinding();
        PortType  portType = binding.getPortType();
        if ( portType == null )
            throw new ServiceException( Messages.getMessage("noPortType00", "" + portName) );

        // Get the URL
        ////////////////////////////////////////////////////////////////////
        List list = port.getExtensibilityElements();
        for ( int i = 0 ; list != null && i < list.size() ; i++ ) {
            Object obj = list.get(i);
            if ( obj instanceof SOAPAddress ) {
                try {
                    SOAPAddress addr = (SOAPAddress) obj ;
                    URL         url  = new URL(addr.getLocationURI());
                    call.setTargetEndpointAddress(url);
                }
                catch(Exception exp) {
                    throw new ServiceException(
                            Messages.getMessage("cantSetURI00", "" + exp) );
                }
            }
        }

        return( call );
    }

    /**
     * Creates a new Call object - will prefill as much info from the WSDL
     * as it can.  Right now it's target URL, SOAPAction, Parameter types,
     * and return type of the Web Service.
     *
     * @param  portName        PortName in the WSDL doc to search for
     * @param  operationName   Operation(method) that's going to be invoked
     * @return Call            Used for invoking the Web Service
     * @throws ServiceException If there's an error
     */
    public javax.xml.rpc.Call createCall(QName portName,
                                         String operationName)
                           throws ServiceException {

        Call call = (org.apache.axis.client.Call)createCall();
        call.setOperation( portName, operationName );
        return( call );
    }

    /**
     * Creates a new Call object - will prefill as much info from the WSDL
     * as it can.  Right now it's target URL, SOAPAction, Parameter types,
     * and return type of the Web Service.
     *
     * @param  portName        PortName in the WSDL doc to search for
     * @param  operationName   Operation(method) that's going to be invoked
     * @return Call            Used for invoking the Web Service
     * @throws ServiceException If there's an error
     */
    public javax.xml.rpc.Call createCall(QName portName,
                                         QName operationName)
                           throws ServiceException {

        Call call = (org.apache.axis.client.Call)createCall();
        call.setOperation( portName, operationName.getLocalPart() );
        return( call );
    }

    /**
     * Creates a new Call object with no prefilled data.  This assumes
     * that the caller will set everything manually - no checking of
     * any kind will be done against the WSDL.
     *
     * @return Call            Used for invoking the Web Service
     * @throws ServiceException If there's an error
     */
    public javax.xml.rpc.Call createCall() throws ServiceException {
        Call call = new org.apache.axis.client.Call(this);
        previousCall.set(call);
        return call;
    }

    /**
     * Gets an array of preconfigured Call objects for invoking operations
     * on the specified port. There is one Call object per operation that
     * can be invoked on the specified port. Each Call object is
     * pre-configured and does not need to be configured using the setter
     * methods on Call interface.
     *
     * This method requires the Service implementation class to have access
     * to the WSDL related metadata.
     *
     * Not implemented.
     *
     * @throws ServiceException - If this Service class does not have access
     * to the required WSDL metadata or if an illegal portName is specified.
     */
    public javax.xml.rpc.Call[] getCalls(QName portName) throws ServiceException {
        if (portName == null)
            throw new ServiceException(Messages.getMessage("badPort00"));

        if (wsdlService == null) 
            throw new ServiceException(Messages.getMessage("wsdlMissing00"));

        javax.xml.rpc.Call[] array = new javax.xml.rpc.Call[]{createCall(portName)};
        return array;
    }

    /**
     * Returns the configured HandlerRegistry instance for this Service
     * instance.
     *
     * NOTE:  This Service currently does not support the configuration
     *        of a HandlerRegistry!  It will throw a
     *        java.lang.UnsupportedOperationException.
     *
     * @return HandlerRegistry
     * @throws java.lang.UnsupportedOperationException - if the Service
     *         class does not support the configuration of a
     *         HandlerRegistry.
     */
    public HandlerRegistry getHandlerRegistry() {
        return registry;
    }

    /**
     * Returns the location of the WSDL document used to prefill the data
     * (if one was used at all).
     *
     * @return URL URL pointing to the WSDL doc
     */
    public URL getWSDLDocumentLocation() {
        return wsdlLocation ;
    }

    /**
     * Returns the qualified name of the service (if one is set).
     *
     * @return QName Fully qualified name of this service.
     */
    public QName getServiceName() {
        if ( serviceName != null ) return serviceName;
        if ( wsdlService == null ) return( null );
        QName  qn = wsdlService.getQName();
        return( new QName( qn.getNamespaceURI(), qn.getLocalPart() ) );
    }

    /**
     * Returns an <code>Iterator</code> for the list of
     * <code>QName</code>s of service endpoints grouped by this
     * service
     *
     * @return Returns <code>java.util.Iterator</code> with elements
     *     of type <code>javax.xml.namespace.QName</code>
     * @throws ServiceException If this Service class does not
     *     have access to the required WSDL metadata
     */
    public Iterator getPorts() throws ServiceException {
        if (wsdlService == null || wsdlService.getPorts() == null){
            // Return an empty iterator;
            return new Vector().iterator();
        }
        return wsdlService.getPorts().keySet().iterator();
    }

    /**
     * Defines the current Type Mappig Registry.
     *
     * @param  registry The TypeMappingRegistry
     * @throws ServiceException if there's an error
     */
    public void setTypeMappingRegistry(TypeMappingRegistry registry)
                    throws ServiceException  {
    }

    /**
     * Returns the current TypeMappingRegistry or null.
     *
     * @return TypeMappingRegistry The registry
     */
    public TypeMappingRegistry getTypeMappingRegistry() {
        return( engine.getTypeMappingRegistry() );
    }

    /**
     * Returns a reference to this object.
     *
     * @return Reference ...
     */
    public Reference getReference() {
        String classname = this.getClass().getName();
        Reference reference = new Reference(classname,
                "org.apache.axis.client.ServiceFactory", null);
        StringRefAddr addr = null;
        if (!classname.equals("org.apache.axis.client.Service")) {
            // This is a generated derived class.  Don't bother with
            // all the Service instance variables.
            addr = new StringRefAddr(
                    ServiceFactory.SERVICE_CLASSNAME, classname);
            reference.add(addr);
        }
        else {
            if (wsdlLocation != null) {
                addr = new StringRefAddr(
                        ServiceFactory.WSDL_LOCATION, wsdlLocation.toString());
                reference.add(addr);
            }
            QName serviceName = getServiceName();
            if (serviceName != null) {
                addr = new StringRefAddr(ServiceFactory.SERVICE_NAMESPACE,
                        serviceName.getNamespaceURI());
                reference.add(addr);
                addr = new StringRefAddr(ServiceFactory.SERVICE_LOCAL_PART,
                        serviceName.getLocalPart());
                reference.add(addr);
            }
        }
        if (maintainSession) {
            addr = new StringRefAddr(ServiceFactory.MAINTAIN_SESSION, "true");
            reference.add(addr);
        }
        return reference;
    }

    /**
     * Sets this Service's AxisEngine.  This engine will be shared by all
     * Call objects created from this Service object.
     *
     * Note: Not part of the JAX-RPC spec.
     *
     * @param engine  Sets this Service's AxisEngine to the passed in one
     */
    public void setEngine(AxisEngine engine) {
        this.engine = engine ;
    }

    /**
     * Returns the current AxisEngine used by this Service and all of the
     * Call objects created from this Service object.
     *
     * Note: Not part of the JAX-RPC spec.
     *
     * @return AxisEngine  the engine
     */
    public AxisEngine getEngine() {
        return( engine );
    }

    /**
     * Set this Service's engine configuration.
     *
     * Note that since all of the constructors create the AxisClient right
     * now, this is basically a no-op.  Putting it in now so that we can make
     * lazy engine instantiation work, and not have to duplicate every single
     * Service constructor with a EngineConfiguration argument.
     *
     * @param config the EngineConfiguration we want to use.
     */
    public void setEngineConfiguration(EngineConfiguration config) {
        this.config = config;
    }

    /**
     * Constructs a EngineConfig if one is not available.
     */ 
    protected EngineConfiguration getEngineConfiguration() {
        if (this.config == null) {
            this.config = EngineConfigurationFactoryFinder.newFactory().getClientEngineConfig(); 
        }
        return config;
    }
    
    /**
     * Determine whether we'd like to track sessions or not.
     * This information is passed to all Call objects created
     * from this service.  Calling setMaintainSession will
     * only affect future instantiations of the Call object,
     * not those that already exist.
     *
     * Note: Not part of JAX-RPC specification.
     *
     * @param yesno true if session state is desired, false if not.
     */
    public void setMaintainSession(boolean yesno) {
        maintainSession = yesno;
    }

    /**
     * If true, this service wants to track sessions.
     */
    public boolean getMaintainSession() {
        return maintainSession;
    }

    public Call getCall() throws ServiceException {
        Call call = (Call) previousCall.get();
        return call;
    }

    /**
     * Tells whether or not we're caching WSDL
     */
    public boolean getCacheWSDL() {
      return cachingWSDL ;
    }

    /**
     * Allows users to turn caching of WSDL documents on or off.
     * Default is 'true' (on).
     */
    public void setCacheWSDL(boolean flag) {
      cachingWSDL = flag ;
    }

    protected static class HandlerRegistryImpl implements HandlerRegistry {
        Map map = new HashMap();

        public List getHandlerChain(QName portName) {
            List list = (List)map.get(portName);
            if(list == null) {
                list = new java.util.ArrayList();
                setHandlerChain(portName, list);
            }
            return list;
        }

        public void setHandlerChain(QName portName, List chain) {
            map.put(portName, chain);
        }
    }

    /**
     * Register a Transport for a particular URL.
     */
    void registerTransportForURL(URL url, Transport transport) {
        transportImpls.put(url, transport);
    }

    /**
     * Get any registered Transport object for a given URL.
     */
    Transport getTransportForURL(URL url) {
        return (Transport)transportImpls.get(url);
    }

}
