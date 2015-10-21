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
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.ResourceAdapterArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.tomitribe.xmpp.connector.MessageException;
import org.tomitribe.xmpp.connector.XMPPConnection;
import org.tomitribe.xmpp.connector.XMPPConnectionFactory;
import org.tomitribe.xmpp.connector.inflow.XMPPMessageListener;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
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
public class XMPPRATest extends Assert {

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

        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "test.jar")
            .addClass(TestEJB.class)
            .addClass(Messages.class);

        final WebArchive war = ShrinkWrap.create(WebArchive.class, "test.war")
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");

        final EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, "test.ear")
                .addAsLibraries(apiJar)
                .addAsModule(rar)
                .addAsModule(jar)
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


    @Test
    public void testEndpoint() throws Exception {

        Thread.sleep(2000);
        final WebClient webClient = WebClient.create(webappUrl.toURI());
        webClient.accept(MediaType.APPLICATION_JSON);

        final Response response = webClient.type(MediaType.APPLICATION_JSON_TYPE)
                .path("msg")
                .query("recipient", "user2@myembeddedjabber.com")
                .query("message", "message sent")
                .post(null);

        Assert.assertEquals(204, response.getStatus());
        Assert.assertTrue(SimpleMessageListener.getInstance().getMessagesReceived().contains("message sent"));
    }
}
