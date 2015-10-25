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
package org.tomitribe.imap.adapter;

import org.tomitribe.imap.api.Body;
import org.tomitribe.imap.api.From;
import org.tomitribe.imap.api.ImapMailFilter;
import org.tomitribe.imap.api.Subject;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.resource.ResourceException;
import javax.resource.spi.*;
import javax.resource.spi.endpoint.MessageEndpoint;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.resource.spi.work.Work;
import javax.resource.spi.work.WorkManager;
import javax.transaction.xa.XAResource;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Connector(description = "IMAP ResourceAdapter", displayName = "IMAP ResourceAdapter", eisType = "IMAP Adapter", version = "1.0")
public class ImapResourceAdapter implements ResourceAdapter {

    private WorkManager workManager;
    final Map<ImapActivationSpec, MessageEndpoint> endpoints = new ConcurrentHashMap<>();
    final Map<ImapActivationSpec, Set<EndpointTarget>> specTargets = new ConcurrentHashMap<>();
    final Map<String, Set<ImapActivationSpec>> folderSpecs = new ConcurrentHashMap<>();
    private ImapCheckThread worker;

    @ConfigProperty
    private String host;

    @ConfigProperty(defaultValue = "993")
    private Integer port;

    @ConfigProperty
    private String username;

    @ConfigProperty
    private String password;

    @ConfigProperty(defaultValue = "imaps")
    private String protocol;

    public void start(BootstrapContext bootstrapContext) throws ResourceAdapterInternalException {
        workManager = bootstrapContext.getWorkManager();
        worker = new ImapCheckThread(this);
        worker.start();
    }

    public void stop() {
        worker.cancel();
    }

    public void endpointActivation(final MessageEndpointFactory messageEndpointFactory, final ActivationSpec activationSpec)
            throws ResourceException
    {
        final ImapActivationSpec imapActivationSpec = (ImapActivationSpec) activationSpec;

        workManager.scheduleWork(new Work() {

            @Override
            public void run() {
                try {
                    final MessageEndpoint messageEndpoint = messageEndpointFactory.createEndpoint(null);
                    endpoints.put(imapActivationSpec, messageEndpoint);

                    final Class<?> endpointClass = imapActivationSpec.getBeanClass() != null ? imapActivationSpec
                            .getBeanClass() : messageEndpointFactory.getEndpointClass();

                    final List<EndpointTarget> targets = findTargets(messageEndpoint, endpointClass);

                    if (specTargets.get(imapActivationSpec) == null) {
                        specTargets.put(imapActivationSpec, new HashSet<>());
                    }

                    specTargets.get(imapActivationSpec).addAll(targets);

                    final String folder = imapActivationSpec.getFolder();
                    if (folderSpecs.get(folder) == null) {
                        folderSpecs.put(folder, new HashSet<>());
                    }

                    folderSpecs.get(folder).add(imapActivationSpec);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void release() {
            }

        });

    }

    private List<EndpointTarget> findTargets(final MessageEndpoint messageEndpoint, final Class<?> endpointClass) {

        final List<EndpointTarget> results = new ArrayList<>();

        final Method[] methods = endpointClass.getMethods();
        for (final Method method : methods) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }

            if (method.getAnnotation(ImapMailFilter.class) == null) {
                continue;
            }

            results.add(new EndpointTarget(messageEndpoint, method));
        }

        return results;
    }

    public void endpointDeactivation(MessageEndpointFactory messageEndpointFactory, ActivationSpec activationSpec) {
        final ImapActivationSpec imapActivationSpec = (ImapActivationSpec) activationSpec;
        final MessageEndpoint messageEndpoint = endpoints.get(imapActivationSpec);

        if (messageEndpoint == null) {
            return;
        }

        messageEndpoint.release();
        specTargets.remove(imapActivationSpec);

        for (final String folder : folderSpecs.keySet()) {
            final Set<ImapActivationSpec> imapActivationSpecs = folderSpecs.get(folder);
            imapActivationSpecs.remove(imapActivationSpec);

            if (imapActivationSpecs.isEmpty()) {
                folderSpecs.remove(folder);
            }
        }
    }

    public XAResource[] getXAResources(ActivationSpec[] activationSpecs) throws ResourceException {
        return new XAResource[0];
    }

    public static class EndpointTarget {
        private final MessageEndpoint messageEndpoint;
        private final Method method;

        public EndpointTarget(final MessageEndpoint messageEndpoint, final Method method) {
            this.messageEndpoint = messageEndpoint;
            this.method = method;
        }

        public Object invoke(final Message message) throws InvocationTargetException, IllegalAccessException {
            try {
                try {
                    messageEndpoint.beforeDelivery(method);

                    final Parameter[] parameters = method.getParameters();
                    final Object[] values = new Object[parameters.length];

                    for (int i = 0; i < parameters.length; i++) {
                        final Parameter parameter = parameters[i];

                        if (!String.class.equals(parameter.getType())) {
                            values[i] = null;
                        }

                        if (String.class.equals(parameter.getType())
                                && parameter.getAnnotation(Subject.class) != null) {
                            try {
                                values[i] = message.getSubject();
                            } catch (MessagingException e) {
                                // ignore
                            }
                        }

                        try {
                            if (String.class.equals(parameter.getType())
                                    && parameter.getAnnotation(From.class) != null
                                    && message.getFrom() != null
                                    && message.getFrom().length > 0) {
                                values[i] = message.getFrom()[0].toString();
                            }
                        } catch (MessagingException e) {
                            // ignore
                        }

                        if (parameter.getAnnotation(Body.class) != null) {
                            try {
                                values[i] = message.getContent();
                            } catch (IOException | MessagingException e) {
                                // ignore
                            }
                        }

                        if (Message.class.equals(parameter.getType())) {
                            values[i] = message;
                        }
                    }

                    return method.invoke(messageEndpoint, values);
                } finally {
                    messageEndpoint.afterDelivery();
                }
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (ResourceException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
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

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
}
