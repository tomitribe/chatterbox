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
package org.tomitribe.chatterbox.twitter.adapter;

import org.junit.Test;
import org.superbiz.StatusBean;
import org.tomitribe.chatterbox.twitter.api.InvokeAllMatches;
import org.tomitribe.chatterbox.twitter.api.Tweet;
import org.tomitribe.chatterbox.twitter.api.TweetParam;
import org.tomitribe.chatterbox.twitter.api.UserParam;
import twitter4j.User;

import javax.resource.ResourceException;
import javax.resource.spi.endpoint.MessageEndpoint;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public class TwitterResourceAdapterTest {

    @Test
    public void testShouldInvokeMdb() throws Exception {

        final Class<?> clazz = TweetBean.class;
        final TwitterResourceAdapter.EndpointTarget endpointTarget = new TwitterResourceAdapter.EndpointTarget(new MyTweetBean(), clazz);
        endpointTarget.invoke(new StatusAdaptor() {
            @Override
            public String getText() {
                return "Testing connectors on #TomEE for #JavaOne";
            }

            @Override
            public User getUser() {
                return new UserAdaptor() {
                    @Override
                    public String getScreenName() {
                        return "jongallimore";
                    }
                };
            }
        });

    }

    private static class MyTweetBean extends TweetBean implements MessageEndpoint {

        @Override
        public void beforeDelivery(final Method method) throws NoSuchMethodException, ResourceException {

        }

        @Override
        public void afterDelivery() throws ResourceException {

        }

        @Override
        public void release() {

        }
    }

    @InvokeAllMatches
    private static class TweetBean {

        private final static Logger LOGGER = Logger.getLogger(StatusBean.class.getName());

        @Tweet(".*#TomEE.*")
        public void tomeeStatus(@TweetParam final String status, @UserParam final String user) {
            LOGGER.info(String.format("New status: %s, by %s", status, user));
        }

        @Tweet(".*#JavaOne.*")
        public void javaoneStatus(@TweetParam final String status, @UserParam final String user) {
            LOGGER.info(String.format("New JavaOne status: %s, by %s", status, user));
        }

        @org.tomitribe.chatterbox.twitter.api.User(".*jongallimore.*")
        public void tomitribeStatus(@TweetParam final String status, @UserParam final String user) {
            LOGGER.info(String.format("New Tomitribe status: %s, by %s", status, user));
        }
    }

}