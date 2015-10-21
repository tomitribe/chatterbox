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

package org.tomitribe.xmpp.connector.test;

import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.vysper.xmpp.server.XMPPServer;
import org.codehaus.swizzle.stream.StreamUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.ResourceAdapterArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.tomitribe.xmpp.connector.MessageException;
import org.tomitribe.xmpp.connector.XMPPConnection;
import org.tomitribe.xmpp.connector.XMPPConnectionFactory;
import org.tomitribe.xmpp.connector.inflow.XMPPMessageListener;
import org.tomitribe.xmpp.connector.lifecycle.Deployed;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;

/**
 * Arquillian will start the container, deploy all @Deployment bundles, then run all the @Test methods.
 *
 * A strong value-add for Arquillian is that the test is abstracted from the server.
 * It is possible to rerun the same test against multiple adapters or server configurations.
 *
 * A second value-add is it is possible to build WebArchives that are slim and trim and therefore
 * isolate the functionality being tested.  This also makes it easier to swap out one implementation
 * of a class for another allowing for easy mocking.
 *
 */
@RunWith(Arquillian.class)
public class XMPPInflowTest extends Assert {

    @Deployment(testable = false)
    public static EnterpriseArchive createDeployment() {

        final JavaArchive raJar = ShrinkWrap.create(JavaArchive.class)
                .addPackages(true, "org.tomitribe.xmpp.connector");

        final JavaArchive apiJar = ShrinkWrap.create(JavaArchive.class)
                .addClasses(MessageException.class,
                        XMPPConnection.class,
                        XMPPConnectionFactory.class,
                        XMPPMessageListener.class);

        final Collection<String> dependencies = Arrays.asList(
                "org.igniterealtime.smack:smack-core",
                "org.igniterealtime.smack:smack-tcp");

        final File[] libs = Maven.resolver()
                .loadPomFromFile(Basedir.basedir("pom.xml")).resolve(dependencies)
                .withTransitivity().asFile();

        final ResourceAdapterArchive rar = ShrinkWrap.create(ResourceAdapterArchive.class, "test.rar")
                .addAsLibraries(raJar)
                .addAsLibraries(libs)
                .addAsManifestResource(new FileAsset(Basedir.basedir("src/test/resources/ra.xml")), "ra.xml");

        final WebArchive war = ShrinkWrap.create(WebArchive.class, "test.war")
                .addClass(TestXMPPMDB.class)
                .addClass(Messages.class);

        final EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, "test.ear")
                .addAsLibraries(apiJar)
                .addAsModule(rar)
                .addAsModule(war);

        return ear;
    }

    /**
     * This URL will contain the following URL data
     *
     *  - http://<host>:<port>/<webapp>/
     *
     * This allows the test itself to be agnostic of server information or even
     * the name of the webapp
     *
     */
    @ArquillianResource
    private URL webappUrl;


    @Deployed
    public static void sendMessage(final XMPPServer server, final XMPPTCPConnection connection) throws Exception {
        final ChatManager chatManager = ChatManager.getInstanceFor(connection);
        Chat newChat = chatManager.createChat("user1@myembeddedjabber.com", new MessageListener() {
            @Override
            public void processMessage(Chat chat, Message message) {
            }
        });

        newChat.sendMessage("Test message");
    }

    @Test
    public void testEndpoint() throws Exception {
        System.out.println(webappUrl.toURI());

        final WebClient webClient = WebClient.create(webappUrl.toURI());
        webClient.accept(MediaType.TEXT_PLAIN_TYPE);

        final Response response = webClient.type(MediaType.TEXT_PLAIN_TYPE)
                .path("messages")
                .get();

        final String expected = "user1@myembeddedjabber.com => Test message";
        final InputStream stream = InputStream.class.cast(response.getEntity());
        final String actual = StreamUtils.streamToString(stream);

        System.out.println(actual);

        Assert.assertTrue(actual.startsWith("user2@myembeddedjabber.com/"));
        Assert.assertTrue(actual.endsWith(" => Test message"));
    }
}
