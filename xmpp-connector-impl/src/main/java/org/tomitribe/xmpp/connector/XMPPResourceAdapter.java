/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.tomitribe.xmpp.connector;

import org.tomitribe.xmpp.connector.inflow.XMPPActivation;
import org.tomitribe.xmpp.connector.inflow.XMPPActivationSpec;
import org.tomitribe.xmpp.connector.inflow.XMPPMessageListener;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.resource.ResourceException;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.BootstrapContext;
import javax.resource.spi.ConfigProperty;
import javax.resource.spi.Connector;
import javax.resource.spi.ResourceAdapter;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.resource.spi.TransactionSupport;
import javax.resource.spi.endpoint.MessageEndpoint;
import javax.resource.spi.endpoint.MessageEndpointFactory;

import javax.security.sasl.SaslException;
import javax.transaction.xa.XAResource;

@Connector(
        reauthenticationSupport = false,
        transactionSupport = TransactionSupport.TransactionSupportLevel.NoTransaction,
        displayName = "XMPPConnector", vendorName = "Tomitribe", version = "1.0")
public class XMPPResourceAdapter implements ResourceAdapter, Serializable, MessageListener, ChatManagerListener {

    private static final long serialVersionUID = 1L;

    private static Logger log = Logger.getLogger(XMPPResourceAdapter.class.getName());

    private ConcurrentHashMap<XMPPActivationSpec, XMPPActivation> activations;

    @ConfigProperty(defaultValue = "localhost")
    private String host;

    @ConfigProperty(defaultValue = "5222")
    private Integer port;

    @ConfigProperty
    private String username;

    @ConfigProperty
    private String password;

    @ConfigProperty
    private String serviceName;

    private XMPPTCPConnection connection;
    private ChatManager chatmanager;
    private boolean connected = false;

    public XMPPResourceAdapter() {
        this.activations = new ConcurrentHashMap<XMPPActivationSpec, XMPPActivation>();
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public Integer getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }


    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void endpointActivation(MessageEndpointFactory endpointFactory,
                                   ActivationSpec spec) throws ResourceException {
        XMPPActivation activation = new XMPPActivation(this, endpointFactory, (XMPPActivationSpec) spec);
        activations.put((XMPPActivationSpec) spec, activation);
        activation.start();

        log.finest("endpointActivation()");
    }

    public void endpointDeactivation(MessageEndpointFactory endpointFactory,
                                     ActivationSpec spec) {
        XMPPActivation activation = activations.remove(spec);
        if (activation != null) {
            activation.stop();
        }

        log.finest("endpointDeactivation()");
    }

    public void start(BootstrapContext ctx)
            throws ResourceAdapterInternalException {
        log.finest("start()");
        connect();
    }

    public void stop() {
        log.finest("stop()");
        disconnect();
    }

    public void connect() {
        ConnectionConfiguration connConfig = new ConnectionConfiguration(host, port, serviceName);
        connection = new XMPPTCPConnection(connConfig);

        try {
            connection.connect();
            log.finest("Connected to " + host + ":" + port + "/" + serviceName);
        } catch (XMPPException e) {
            log.log(Level.SEVERE, "Unable to connect to " + host + ":" + port + "/" + serviceName, e);
        } catch (SmackException e) {
            log.log(Level.SEVERE, "Unable to connect to " + host + ":" + port + "/" + serviceName, e);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Unable to connect to " + host + ":" + port + "/" + serviceName, e);
        }

        try {
            connection.login(username, password);
            log.finest("Logged in as " + username);

            Presence presence = new Presence(Presence.Type.available);
            connection.sendPacket(presence);

        } catch (XMPPException e) {
            log.log(Level.SEVERE, "Unable to login as " + username, e);
        } catch (SmackException.NotConnectedException e) {
            log.log(Level.SEVERE, "Unable to login as " + username, e);
        } catch (SaslException e) {
            log.log(Level.SEVERE, "Unable to login as " + username, e);
        } catch (SmackException e) {
            log.log(Level.SEVERE, "Unable to login as " + username, e);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Unable to login as " + username, e);
        }

        connected = true;
        chatmanager = ChatManager.getInstanceFor(connection);
        chatmanager.addChatListener(this);
    }

    public void disconnect() {
        try {
            if (connected) {
                chatmanager.removeChatListener(this);
                connection.disconnect(new Presence(Presence.Type.unavailable));
            }
        } catch (SmackException.NotConnectedException e) {
            log.log(Level.SEVERE, "Unable to logout", e);
        }

        connection = null;
        chatmanager = null;
        connected = false;

    }

    public void sendXMPPMessage(String recipient, String message) throws MessageException {
        Chat newChat = chatmanager.createChat(recipient, this);

        try {
            newChat.sendMessage(message);
        } catch (XMPPException e) {
            throw new MessageException(e);
        } catch (SmackException.NotConnectedException e) {
            throw new MessageException(e);
        }
    }



    public XAResource[] getXAResources(ActivationSpec[] specs)
            throws ResourceException {
        log.finest("getXAResources()");
        return null;
    }

    @Override
    public int hashCode() {
        int result = 17;
        if (host != null) {
            result += 31 * result + 7 * host.hashCode();
        } else {
            result += 31 * result + 7;
        }
        if (port != null) {
            result += 31 * result + 7 * port.hashCode();
        } else {
            result += 31 * result + 7;
        }
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (!(other instanceof XMPPResourceAdapter)) {
            return false;
        }
        boolean result = true;
        XMPPResourceAdapter obj = (XMPPResourceAdapter) other;
        if (result) {
            if (host == null) {
                result = obj.getHost() == null;
            } else {
                result = host.equals(obj.getHost());
            }
        }
        if (result) {
            if (port == null) {
                result = obj.getPort() == null;
            } else {
                result = port.equals(obj.getPort());
            }
        }
        return result;
    }

    @Override
    public void processMessage(Chat chat, Message message) {

        for (XMPPActivation next : activations.values()) {
            try {
                final MessageEndpoint endpoint = next.getMessageEndpointFactory().createEndpoint(null);

                final Method onMessage = XMPPMessageListener.class.getDeclaredMethod("onMessage", String.class, String.class);
                endpoint.beforeDelivery(onMessage);
                ((XMPPMessageListener) endpoint).onMessage(chat.getParticipant(), message.getBody());
                endpoint.afterDelivery();
            } catch (NoSuchMethodException e) {
                log.log(Level.SEVERE, "Unable to call MDB endpoint for message", e);
            } catch (ResourceException e) {
                log.log(Level.SEVERE, "Unable to call MDB endpoint for message", e);
            }
        }

        chat.removeMessageListener(this);
        chat.close();
    }

    @Override
    public void chatCreated(Chat chat, boolean b) {
        chat.addMessageListener(this);
    }
}
