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
package org.tomitribe.twitter.adapter;

import com.twitter.hbc.httpclient.ControlStreamException;
import org.tomitribe.twitter.api.InvokeAllMatches;
import org.tomitribe.twitter.api.Tweet;
import org.tomitribe.twitter.api.TweetParam;
import org.tomitribe.twitter.api.User;
import org.tomitribe.twitter.api.UserParam;
import org.tomitribe.util.editor.Converter;
import twitter4j.Status;

import javax.resource.ResourceException;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.BootstrapContext;
import javax.resource.spi.ConfigProperty;
import javax.resource.spi.Connector;
import javax.resource.spi.ResourceAdapter;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.resource.spi.endpoint.MessageEndpoint;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.transaction.xa.XAResource;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Connector(description = "Twitter Resource Adapter", displayName = "Twitter Resource Adapter", eisType = "Twitter Resource Adapter", version = "1.0")
public class TwitterResourceAdapter implements ResourceAdapter, StatusChangeListener {

    final Map<TwitterActivationSpec, EndpointTarget> targets = new ConcurrentHashMap<TwitterActivationSpec, EndpointTarget>();
    private TwitterStreamingClient client;

    @ConfigProperty
    @NotNull
    private String consumerKey;

    @ConfigProperty
    @NotNull
    private String consumerSecret;

    @ConfigProperty
    @NotNull
    private String accessToken;

    @ConfigProperty
    @NotNull
    private String accessTokenSecret;

    public void start(final BootstrapContext bootstrapContext) throws ResourceAdapterInternalException {
        client = new TwitterStreamingClient(this, consumerKey, consumerSecret, accessToken, accessTokenSecret);
        try {
            client.run();
        } catch (InterruptedException | ControlStreamException | IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        client.stop();
    }

    public void endpointActivation(final MessageEndpointFactory messageEndpointFactory, final ActivationSpec activationSpec)
            throws ResourceException {
        final TwitterActivationSpec twitterActivationSpec = (TwitterActivationSpec) activationSpec;
        final MessageEndpoint messageEndpoint = messageEndpointFactory.createEndpoint(null);

        final Class<?> endpointClass = twitterActivationSpec.getBeanClass() != null ? twitterActivationSpec
                .getBeanClass() : messageEndpointFactory.getEndpointClass();

        final EndpointTarget target = new EndpointTarget(messageEndpoint, endpointClass);
        targets.put(twitterActivationSpec, target);

    }

    public void endpointDeactivation(final MessageEndpointFactory messageEndpointFactory, final ActivationSpec activationSpec) {
        final TwitterActivationSpec twitterActivationSpec = (TwitterActivationSpec) activationSpec;

        final EndpointTarget endpointTarget = targets.get(twitterActivationSpec);
        if (endpointTarget == null) {
            throw new IllegalStateException("No EndpointTarget to undeploy for ActivationSpec " + activationSpec);
        }

        endpointTarget.messageEndpoint.release();
    }

    public XAResource[] getXAResources(final ActivationSpec[] activationSpecs) throws ResourceException {
        return new XAResource[0];
    }

    @Override
    public void onStatus(final Status status) {
        for (final EndpointTarget endpointTarget : this.targets.values()) {
            endpointTarget.invoke(status);
        }
    }

    public static class EndpointTarget {
        private final MessageEndpoint messageEndpoint;
        private final Class<?> clazz;

        public EndpointTarget(final MessageEndpoint messageEndpoint, final Class<?> clazz) {
            this.messageEndpoint = messageEndpoint;
            this.clazz = clazz;
        }

        public void invoke(final Status status) {

            // find matching method(s)

            final List<Method> matchingMethods =
                    Arrays.asList(clazz.getDeclaredMethods())
                            .stream()
                            .sorted((m1, m2) -> m1.toString().compareTo(m2.toString()))
                            .filter(this::isPublic)
                            .filter(this::isNotFinal)
                            .filter(this::isNotAbstract)
                            .filter(m -> filterTweet(status, m))
                            .filter(m -> filterUser(status, m))
                            .collect(Collectors.toList());

            if (matchingMethods == null || matchingMethods.size() == 0) {
                // log this
                return;
            }

            if (this.clazz.isAnnotationPresent(InvokeAllMatches.class)) {
                for (final Method method : matchingMethods) {
                    invoke(method, status);
                }
            } else {
                invoke(matchingMethods.get(0), status);
            }
        }

        private boolean filterUser(final Status status, final Method m) {
            return ! m.isAnnotationPresent(User.class) || "".equals(m.getAnnotation(User.class).value())
                    || Pattern.matches(m.getAnnotation(User.class).value(), status.getUser().getScreenName());
        }

        private boolean filterTweet(final Status status, final Method m) {
            return !m.isAnnotationPresent(Tweet.class) || "".equals(m.getAnnotation(Tweet.class).value())
                    || Pattern.matches(m.getAnnotation(Tweet.class).value(), status.getText());
        }

        private boolean isPublic(final Method m) {
            return Modifier.isPublic(m.getModifiers());
        }

        private boolean isNotAbstract(final Method m) {
            return !Modifier.isAbstract(m.getModifiers());
        }

        private boolean isNotFinal(final Method m) {
            return !Modifier.isFinal(m.getModifiers());
        }

        private void invoke(final Method method, final Status status) {
            try {
                try {
                    messageEndpoint.beforeDelivery(method);
                    final Object[] values = getValues(method, status);
                    method.invoke(messageEndpoint, values);
                } finally {
                    messageEndpoint.afterDelivery();
                }
            } catch (final NoSuchMethodException | ResourceException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static Object[] getValues(final Method method, final Status status) {

        if (method == null) {
            return null;
        }

        final Parameter[] parameters = method.getParameters();
        if (parameters.length == 0) {
            return new Object[0];
        }

        final Template tweetTemplate = getTemplate(method.getAnnotation(Tweet.class));
        final Map<String, List<String>> tweetParamValues = new HashMap<>();
        if (tweetTemplate != null) {
            tweetTemplate.match(status.getText(), tweetParamValues);
        }

        final Template userTemplate = getTemplate(method.getAnnotation(User.class));
        final Map<String, List<String>> userParamValues = new HashMap<>();
        if (userTemplate != null) {
            userTemplate.match(status.getUser().getScreenName(), userParamValues);
        }

        final Object[] values = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            final Parameter parameter = parameters[i];

            values[i] = null;

            if (parameter.isAnnotationPresent(TweetParam.class)) {
                final TweetParam tweetParam = parameter.getAnnotation(TweetParam.class);
                if (tweetParam.value() == null || tweetParam.value().length() == 0) {
                    values[i] = Converter.convert(status.getText(), parameter.getType(), null);
                } else {
                    final List<String> paramValues = tweetParamValues.get(tweetParam.value());
                    final String paramValue = paramValues == null || paramValues.size() == 0 ? null : paramValues.get(0);
                    values[i] = Converter.convert(paramValue, parameter.getType(), null);
                }
            }

            if (parameter.isAnnotationPresent(UserParam.class)) {
                final UserParam userParam = parameter.getAnnotation(UserParam.class);
                if (userParam.value() == null || userParam.value().length() == 0) {
                    values[i] = Converter.convert(status.getUser().getScreenName(), parameter.getType(), null);
                } else {
                    final List<String> paramValues = userParamValues.get(userParam.value());
                    final String paramValue = paramValues == null || paramValues.size() == 0 ? null : paramValues.get(0);
                    values[i] = Converter.convert(paramValue, parameter.getType(), null);
                }
            }
        }

        return values;
    }


    private static Template getTemplate(final Annotation annotation) {
        if (annotation == null) {
            return null;
        }

        try {

            final Method patternMethod = annotation.getClass().getMethod("value");
            if (patternMethod == null) {
                return null;
            }

            if (!String.class.equals(patternMethod.getReturnType())) {
                return null;
            }

            final String pattern = (String) patternMethod.invoke(annotation);
            return new Template(pattern);
        } catch (final Exception e) {
            // ignore
        }

        return null;
    }

}
