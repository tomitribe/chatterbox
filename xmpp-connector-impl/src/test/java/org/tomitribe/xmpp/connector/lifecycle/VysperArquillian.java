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
package org.tomitribe.xmpp.connector.lifecycle;

import org.tomitribe.xmpp.connector.test.Basedir;
import org.tomitribe.xmpp.connector.test.SimpleMessageListener;
import org.apache.vysper.mina.TCPEndpoint;
import org.apache.vysper.storage.StorageProviderRegistry;
import org.apache.vysper.storage.inmemory.MemoryStorageProviderRegistry;
import org.apache.vysper.xmpp.addressing.EntityImpl;
import org.apache.vysper.xmpp.authorization.AccountManagement;
import org.apache.vysper.xmpp.server.XMPPServer;
import org.jboss.arquillian.container.spi.event.container.AfterDeploy;
import org.jboss.arquillian.container.spi.event.container.AfterUnDeploy;
import org.jboss.arquillian.container.spi.event.container.BeforeDeploy;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.TestClass;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;

import java.lang.reflect.Method;

public class VysperArquillian {

    private XMPPServer server;
    private XMPPTCPConnection connection;

    private void addUser(final AccountManagement accountManagement, final String username, final String password) throws Exception {
        if(!accountManagement.verifyAccountExists(EntityImpl.parse(username))) {
            accountManagement.addUser(EntityImpl.parse(username), password);
        }
    }

    public void executeAfterDeploy(@Observes final AfterDeploy event, final TestClass testClass) throws Exception {

        final Method[] methods = testClass.getMethods(Deployed.class);
        for (Method method : methods) {

            Object[] params = new Object[method.getParameterTypes().length];
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                Class paramClass = parameterTypes[i];
                if (XMPPServer.class.isAssignableFrom(paramClass)) {
                    params[i] = server;
                } else if (XMPPTCPConnection.class.isAssignableFrom(paramClass)) {
                    params[i] = connection;
                } else {
                    params[i] = null;
                }
            }

            method.invoke(null, params);
        }
    }

    public void executeBeforeDeploy(@Observes final BeforeDeploy event, final TestClass testClass) throws Exception {
        System.setProperty("javax.net.ssl.trustStore", "" +
                Basedir.basedir("src/test/config/bogus_mina_tls.cert"));

        System.setProperty("javax.net.ssl.trustStorePassword", "boguspw");

        // choose the storage you want to use
        StorageProviderRegistry providerRegistry = new MemoryStorageProviderRegistry();

        final AccountManagement accountManagement = (AccountManagement) providerRegistry.retrieve(AccountManagement.class);

        addUser(accountManagement, "user1@myembeddedjabber.com", "password1");
        addUser(accountManagement, "user2@myembeddedjabber.com", "password1");

        server = new XMPPServer("myembeddedjabber.com");
        server.addEndpoint(new TCPEndpoint());
        server.setStorageProviderRegistry(providerRegistry);

        server.setTLSCertificateInfo(Basedir.basedir("src/test/config/bogus_mina_tls.cert"), "boguspw");
        server.start();

        ConnectionConfiguration connConfig = new ConnectionConfiguration("localhost", 5222);
        connection = new XMPPTCPConnection(connConfig);

        connection.connect();
        connection.login("user2@myembeddedjabber.com", "password1");
        System.out.println("Logged in as " + connection.getUser());

        Presence presence = new Presence(Presence.Type.available);
        connection.sendPacket(presence);

        ChatManager chatmanager = ChatManager.getInstanceFor(connection);
        chatmanager.addChatListener(new ChatManagerListener() {
            @Override
            public void chatCreated(Chat chat, boolean b) {
                chat.addMessageListener(SimpleMessageListener.getInstance());
            }
        });
    }

    public void executeAfterUnDeploy(@Observes final AfterUnDeploy event, final TestClass testClass) throws Exception {
        connection.disconnect();
        server.stop();
    }
}
