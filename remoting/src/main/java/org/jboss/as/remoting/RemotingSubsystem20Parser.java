/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.remoting;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.xnio.sasl.SaslQop;
import org.xnio.sasl.SaslStrength;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.parsing.ParseUtils.duplicateAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.duplicateNamedElement;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.readArrayAttributeElement;
import static org.jboss.as.controller.parsing.ParseUtils.readProperty;
import static org.jboss.as.controller.parsing.ParseUtils.readStringAttributeElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.remoting.CommonAttributes.AUTHENTICATION_PROVIDER;
import static org.jboss.as.remoting.CommonAttributes.CONNECTOR;
import static org.jboss.as.remoting.CommonAttributes.CONNECTOR_REF;
import static org.jboss.as.remoting.CommonAttributes.HTTP_CONNECTOR;
import static org.jboss.as.remoting.CommonAttributes.INCLUDE_MECHANISMS;
import static org.jboss.as.remoting.CommonAttributes.PROPERTY;
import static org.jboss.as.remoting.CommonAttributes.QOP;
import static org.jboss.as.remoting.CommonAttributes.SECURITY_REALM;
import static org.jboss.as.remoting.CommonAttributes.SOCKET_BINDING;
import static org.jboss.as.remoting.CommonAttributes.STRENGTH;
import static org.jboss.as.remoting.CommonAttributes.VALUE;
import static org.jboss.as.remoting.RemotingMessages.MESSAGES;

/**
 * Parser for remoting subsystem 2.0 version
 *
 * @author Jaikiran Pai
 */
class RemotingSubsystem20Parser implements XMLStreamConstants, XMLElementReader<List<ModelNode>> {

    static final RemotingSubsystem20Parser INSTANCE = new RemotingSubsystem20Parser();

    @Override
    public void readElement(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {

        final ModelNode address = new ModelNode();
        address.add(SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME);
        address.protect();
        final ModelNode subsystem = Util.getEmptyOperation(ADD, address);
        list.add(subsystem);

        requireAttributes(reader);

        // Handle elements
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            boolean doneWorkerThreadPool = false;
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case WORKER_THREAD_POOL:
                    if (doneWorkerThreadPool) {
                        throw duplicateNamedElement(reader, Element.WORKER_THREAD_POOL.getLocalName());
                    }
                    doneWorkerThreadPool = true;
                    parseWorkerThreadPool(reader, subsystem);
                    break;
                case CONNECTOR: {
                    // Add connector updates
                    parseConnector(reader, address, list);
                    break;
                }
                case HTTP_CONNECTOR: {
                    // Add http connector updates
                    parseHttpConnector(reader, address, list);
                    break;
                }
                case OUTBOUND_CONNECTIONS: {
                    // parse the outbound-connections
                    this.parseOutboundConnections(reader, address, list);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    /**
     * Adds the worker thread pool attributes to the subysystem add method
     */
    void parseWorkerThreadPool(final XMLExtendedStreamReader reader, final ModelNode subsystemAdd) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case WORKER_READ_THREADS:
                    if (subsystemAdd.hasDefined(CommonAttributes.WORKER_READ_THREADS)) {
                        throw duplicateAttribute(reader, CommonAttributes.WORKER_READ_THREADS);
                    }
                    RemotingSubsystemRootResource.WORKER_READ_THREADS.parseAndSetParameter(value, subsystemAdd, reader);
                    break;
                case WORKER_TASK_CORE_THREADS:
                    if (subsystemAdd.hasDefined(CommonAttributes.WORKER_TASK_CORE_THREADS)) {
                        throw duplicateAttribute(reader, CommonAttributes.WORKER_TASK_CORE_THREADS);
                    }
                    RemotingSubsystemRootResource.WORKER_TASK_CORE_THREADS.parseAndSetParameter(value, subsystemAdd, reader);
                    break;
                case WORKER_TASK_KEEPALIVE:
                    if (subsystemAdd.hasDefined(CommonAttributes.WORKER_TASK_KEEPALIVE)) {
                        throw duplicateAttribute(reader, CommonAttributes.WORKER_TASK_KEEPALIVE);
                    }
                    RemotingSubsystemRootResource.WORKER_TASK_KEEPALIVE.parseAndSetParameter(value, subsystemAdd, reader);
                    break;
                case WORKER_TASK_LIMIT:
                    if (subsystemAdd.hasDefined(CommonAttributes.WORKER_TASK_LIMIT)) {
                        throw duplicateAttribute(reader, CommonAttributes.WORKER_TASK_LIMIT);
                    }
                    RemotingSubsystemRootResource.WORKER_TASK_LIMIT.parseAndSetParameter(value, subsystemAdd, reader);
                    break;
                case WORKER_TASK_MAX_THREADS:
                    if (subsystemAdd.hasDefined(CommonAttributes.WORKER_TASK_MAX_THREADS)) {
                        throw duplicateAttribute(reader, CommonAttributes.WORKER_TASK_MAX_THREADS);
                    }
                    RemotingSubsystemRootResource.WORKER_TASK_MAX_THREADS.parseAndSetParameter(value, subsystemAdd, reader);
                    break;
                case WORKER_WRITE_THREADS:
                    if (subsystemAdd.hasDefined(CommonAttributes.WORKER_WRITE_THREADS)) {
                        throw duplicateAttribute(reader, CommonAttributes.WORKER_WRITE_THREADS);
                    }
                    RemotingSubsystemRootResource.WORKER_WRITE_THREADS.parseAndSetParameter(value, subsystemAdd, reader);
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }

        requireNoContent(reader);
    }

    void parseConnector(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {

        String name = null;
        String securityRealm = null;
        String socketBinding = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.SOCKET_BINDING);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                case SECURITY_REALM: {
                    securityRealm = value;
                    break;
                }
                case SOCKET_BINDING: {
                    socketBinding = value;
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        assert name != null;
        assert socketBinding != null;

        final ModelNode connector = new ModelNode();
        connector.get(OP).set(ADD);
        connector.get(OP_ADDR).set(address).add(CONNECTOR, name);
        // requestProperties.get(NAME).set(name); // Name is part of the address
        connector.get(SOCKET_BINDING).set(socketBinding);
        if (securityRealm != null) {
            connector.get(SECURITY_REALM).set(securityRealm);
        }
        list.add(connector);

        // Handle nested elements.
        parseConnectorNestledElements(reader, list, connector);
    }


    void parseHttpConnector(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {

            String name = null;
            String securityRealm = null;
            String connectorRef = null;
            final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.CONNECTOR_REF);
            final int count = reader.getAttributeCount();
            for (int i = 0; i < count; i++) {
                requireNoNamespaceAttribute(reader, i);
                final String value = reader.getAttributeValue(i);
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                required.remove(attribute);
                switch (attribute) {
                    case NAME: {
                        name = value;
                        break;
                    }
                    case SECURITY_REALM: {
                        securityRealm = value;
                        break;
                    }
                    case CONNECTOR_REF: {
                        connectorRef = value;
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
            if (!required.isEmpty()) {
                throw missingRequired(reader, required);
            }
            assert name != null;
            assert connectorRef != null;

            final ModelNode connector = new ModelNode();
            connector.get(OP).set(ADD);
            connector.get(OP_ADDR).set(address).add(HTTP_CONNECTOR, name);
            // requestProperties.get(NAME).set(name); // Name is part of the address
            connector.get(CONNECTOR_REF).set(connectorRef);
            if (securityRealm != null) {
                connector.get(SECURITY_REALM).set(securityRealm);
            }
            list.add(connector);
        parseConnectorNestledElements(reader, list, connector);


    }

    private void parseConnectorNestledElements(final XMLExtendedStreamReader reader, final List<ModelNode> list, final ModelNode connector) throws XMLStreamException {
        // Handle nested elements.
        final EnumSet<Element> visited = EnumSet.noneOf(Element.class);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (visited.contains(element)) {
                throw unexpectedElement(reader);
            }
            visited.add(element);
            switch (element) {
                case SASL: {
                    parseSaslElement(reader, connector.get(OP_ADDR), list);
                    break;
                }
                case PROPERTIES: {
                    parseProperties(reader, connector.get(OP_ADDR), list);
                    break;
                }
                case AUTHENTICATION_PROVIDER: {
                    connector.get(AUTHENTICATION_PROVIDER).set(readStringAttributeElement(reader, "name"));
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    void parseSaslElement(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        final ModelNode saslElement = new ModelNode();
        saslElement.get(OP).set(ADD);
        saslElement.get(OP_ADDR).set(address).add(SaslResource.SASL_CONFIG_PATH.getKey(), SaslResource.SASL_CONFIG_PATH.getValue());
        list.add(saslElement);

        // No attributes
        final int count = reader.getAttributeCount();
        if (count > 0) {
            throw unexpectedAttribute(reader, 0);
        }
        // Nested elements
        final EnumSet<Element> visited = EnumSet.noneOf(Element.class);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (visited.contains(element)) {
                throw unexpectedElement(reader);
            }
            visited.add(element);
            switch (element) {
                case INCLUDE_MECHANISMS: {
                    final ModelNode includes = saslElement.get(INCLUDE_MECHANISMS);
                    for (final String s : readArrayAttributeElement(reader, "value", String.class)) {
                        includes.add().set(s);
                    }
                    break;
                }
                case POLICY: {
                    parsePolicyElement(reader, saslElement.get(OP_ADDR), list);
                    break;
                }
                case PROPERTIES: {
                    parseProperties(reader, saslElement.get(OP_ADDR), list);
                    break;
                }
                case QOP: {
                    String[] qop = readArrayAttributeElement(reader, "value", String.class);
                    for (String q : qop) {
                        try {
                            saslElement.get(QOP).add(SaslQop.fromString(q).getString().toLowerCase(Locale.ENGLISH));
                        } catch (IllegalArgumentException e) {
                            throw MESSAGES.invalidQOPV(q);
                        }
                    }
                    break;
                }
                case REUSE_SESSION: {
                    String value = readStringAttributeElement(reader, "value");
                    SaslResource.REUSE_SESSION_ATTRIBUTE.parseAndSetParameter(value, saslElement, reader);
                    break;
                }
                case SERVER_AUTH: {
                    String value = readStringAttributeElement(reader, "value");
                    SaslResource.SERVER_AUTH_ATTRIBUTE.parseAndSetParameter(value, saslElement, reader);
                    break;
                }
                case STRENGTH: {
                    //FIXME is this really an xml attribute?
                    String[] strength = readArrayAttributeElement(reader, "value", String.class);
                    for (String s : strength) {
                        try {
                            saslElement.get(STRENGTH).add(SaslStrength.valueOf(s.toUpperCase(Locale.ENGLISH)).name().toLowerCase(Locale.ENGLISH));
                        } catch (IllegalArgumentException e) {
                            throw MESSAGES.invalidStrength(s);
                        }
                    }
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    ModelNode parsePolicyElement(XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        final ModelNode policy = new ModelNode();
        policy.get(OP).set(ADD);
        policy.get(OP_ADDR).set(address).add(SaslPolicyResource.SASL_POLICY_CONFIG_PATH.getKey(), SaslPolicyResource.SASL_POLICY_CONFIG_PATH.getValue());
        list.add(policy);

        if (reader.getAttributeCount() > 0) {
            throw ParseUtils.unexpectedAttribute(reader, 0);
        }
        // Handle nested elements.
        final EnumSet<Element> visited = EnumSet.noneOf(Element.class);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (visited.contains(element)) {
                throw ParseUtils.unexpectedElement(reader);
            }
            visited.add(element);
            switch (element) {
                case FORWARD_SECRECY: {
                    SaslPolicyResource.FORWARD_SECRECY.parseAndSetParameter(readStringAttributeElement(reader, "value"), policy, reader);
                    break;
                }
                case NO_ACTIVE: {
                    SaslPolicyResource.NO_ACTIVE.parseAndSetParameter(readStringAttributeElement(reader, "value"), policy, reader);
                    break;
                }
                case NO_ANONYMOUS: {
                    SaslPolicyResource.NO_ANONYMOUS.parseAndSetParameter(readStringAttributeElement(reader, "value"), policy, reader);
                    break;
                }
                case NO_DICTIONARY: {
                    SaslPolicyResource.NO_DICTIONARY.parseAndSetParameter(readStringAttributeElement(reader, "value"), policy, reader);
                    break;
                }
                case NO_PLAIN_TEXT: {
                    SaslPolicyResource.NO_PLAIN_TEXT.parseAndSetParameter(readStringAttributeElement(reader, "value"), policy, reader);
                    break;
                }
                case PASS_CREDENTIALS: {
                    SaslPolicyResource.PASS_CREDENTIALS.parseAndSetParameter(readStringAttributeElement(reader, "value"), policy, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        return policy;
    }

    private void parseProperties(XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        while (reader.nextTag() != END_ELEMENT) {
            reader.require(START_ELEMENT, Namespace.CURRENT.getUriString(), Element.PROPERTY.getLocalName());
            final Property property = readProperty(reader, true);
            ModelNode propertyOp = new ModelNode();
            propertyOp.get(OP).set(ADD);
            propertyOp.get(OP_ADDR).set(address).add(PROPERTY, property.getName());
            propertyOp.get(VALUE).set(property.getValue());
            list.add(propertyOp);
        }
    }

    private void parseOutboundConnections(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> operations) throws XMLStreamException {
        // Handle nested elements.
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case REMOTE_OUTBOUND_CONNECTION: {
                    this.parseRemoteOutboundConnection(reader, address, operations);
                    break;
                }
                case LOCAL_OUTBOUND_CONNECTION: {
                    this.parseLocalOutboundConnection(reader, address, operations);
                    break;
                }
                case OUTBOUND_CONNECTION: {
                    this.parseOutboundConnection(reader, address, operations);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseRemoteOutboundConnection(final XMLExtendedStreamReader reader, final ModelNode parentAddress, final List<ModelNode> operations) throws XMLStreamException {
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.OUTBOUND_SOCKET_BINDING_REF);
        final int count = reader.getAttributeCount();
        String name = null;
        String outboundSocketBindingRef = null;
        String protocol = null;
        ModelNode username = null;
        String securityRealm = null;
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                case OUTBOUND_SOCKET_BINDING_REF: {
                    outboundSocketBindingRef = value;
                    break;
                }
                case USERNAME: {
                    username = RemoteOutboundConnectionResourceDefinition.USERNAME.parse(value, reader);
                    break;
                }
                case SECURITY_REALM: {
                    securityRealm = value;
                    break;
                }
                case PROTOCOL: {
                    protocol = value;
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        final PathAddress address = PathAddress.pathAddress(PathAddress.pathAddress(parentAddress), PathElement.pathElement(CommonAttributes.REMOTE_OUTBOUND_CONNECTION, name));

        // create add operation add it to the list of operations
        operations.add(getConnectionAddOperation(name, outboundSocketBindingRef, username, securityRealm, protocol, address));
        // parse the nested elements
        final EnumSet<Element> visited = EnumSet.noneOf(Element.class);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (visited.contains(element)) {
                throw ParseUtils.unexpectedElement(reader);
            }
            visited.add(element);
            switch (element) {
                case PROPERTIES: {
                    parseProperties(reader, address.toModelNode(), operations);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }

    }

    private void parseLocalOutboundConnection(final XMLExtendedStreamReader reader, final ModelNode parentAddress, final List<ModelNode> operations) throws XMLStreamException {
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.OUTBOUND_SOCKET_BINDING_REF);
        final int count = reader.getAttributeCount();
        String name = null;
        String outboundSocketBindingRef = null;
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                case OUTBOUND_SOCKET_BINDING_REF: {
                    outboundSocketBindingRef = value;
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }

        final PathAddress address = PathAddress.pathAddress(PathAddress.pathAddress(parentAddress), PathElement.pathElement(CommonAttributes.LOCAL_OUTBOUND_CONNECTION, name));
        // add it to the list of operations
        operations.add(getConnectionAddOperation(name, outboundSocketBindingRef, address));
        // create add operation parse the nested elements

        final EnumSet<Element> visited = EnumSet.noneOf(Element.class);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (visited.contains(element)) {
                throw ParseUtils.unexpectedElement(reader);
            }
            visited.add(element);
            switch (element) {
                case PROPERTIES: {
                    parseProperties(reader, address.toModelNode(), operations);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseOutboundConnection(final XMLExtendedStreamReader reader, final ModelNode parentAddress, final List<ModelNode> operations) throws XMLStreamException {
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.URI);
        final int count = reader.getAttributeCount();
        String name = null;
        String uri = null;
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                case URI: {
                    uri = value;
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }

        final PathAddress address = PathAddress.pathAddress(PathAddress.pathAddress(parentAddress), PathElement.pathElement(CommonAttributes.OUTBOUND_CONNECTION, name));
        // create add operation add it to the list of operations
        operations.add(GenericOutboundConnectionAdd.getAddOperation(name, uri, address));
        // parse the nested elements
        final EnumSet<Element> visited = EnumSet.noneOf(Element.class);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (visited.contains(element)) {
                throw ParseUtils.unexpectedElement(reader);
            }
            visited.add(element);
            switch (element) {
                case PROPERTIES: {
                    parseProperties(reader, address.toModelNode(), operations);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }


    }

    static ModelNode getConnectionAddOperation(final String connectionName, final String outboundSocketBindingRef, PathAddress address) {
        return getConnectionAddOperation(connectionName, outboundSocketBindingRef, null, null, null, address);
    }

    static ModelNode getConnectionAddOperation(final String connectionName, final String outboundSocketBindingRef, final ModelNode userName, final String securityRealm, final String protocol, PathAddress address) {
        if (connectionName == null || connectionName.trim().isEmpty()) {
            throw MESSAGES.connectionNameEmpty();
        }
        if (outboundSocketBindingRef == null || outboundSocketBindingRef.trim().isEmpty()) {
            throw MESSAGES.outboundSocketBindingEmpty(connectionName);
        }
        final ModelNode addOperation = new ModelNode();
        addOperation.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.ADD);
        // /subsystem=remoting/local-outbound-connection=<connection-name>
        addOperation.get(ModelDescriptionConstants.OP_ADDR).set(address.toModelNode());

        // set the other params
        addOperation.get(CommonAttributes.OUTBOUND_SOCKET_BINDING_REF).set(outboundSocketBindingRef);
        // optional connection creation options
        if (userName != null) {
            addOperation.get(CommonAttributes.USERNAME).set(userName);
        }

        if (securityRealm != null) {
            addOperation.get(CommonAttributes.SECURITY_REALM).set(securityRealm);
        }

        if(protocol != null) {
            addOperation.get(CommonAttributes.PROTOCOL).set(protocol);
        }

        return addOperation;
    }

}
