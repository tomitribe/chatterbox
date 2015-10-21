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

import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

@Singleton
@Lock(LockType.WRITE)
public class Messages {

    private List<String> messagesReceieved = new ArrayList<String>();

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/messages")
    public String getMessages() {
        final StringBuilder sb = new StringBuilder();

        for (String message : messagesReceieved) {
            if (sb.length() > 0) {
                sb.append(",\n");
            }

            sb.append(message);
        }

        return sb.toString();
    }

    public void addMessage(final String message) {
        messagesReceieved.add(message);
    }


}
