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
package org.superbiz;

import org.tomitribe.chatterbox.twitter.api.Response;
import org.tomitribe.chatterbox.twitter.api.Tweet;
import org.tomitribe.chatterbox.twitter.api.TweetParam;
import org.tomitribe.chatterbox.twitter.api.TwitterUpdates;
import org.tomitribe.chatterbox.twitter.api.UserParam;

import javax.ejb.MessageDriven;

@MessageDriven(name = "Status")
public class StatusBean implements TwitterUpdates {

    @Tweet("(?i).*Knock knock.*")
    public Response knockKnock(@TweetParam final String status, @UserParam final String user) {

        return Response.message("Who's there?")
                .dialog(new WhosThere())
                .build();
    }

    public class WhosThere {
        public Response who(@TweetParam final String status, @UserParam final String user) {
            String who = status;
            if (status != null && status.length() > 0) {
                who = status.replaceAll("@?(\\w){1,15}(\\s+)", ""); // strip off any referenced usernames
            }

            return Response.message(who + " who?")
                    .dialog(new Who())
                    .build();
        }
    }

    public class Who {

        public String punchline(@TweetParam final String status, @UserParam final String user) {
            return "Haha, lol. That's a good one, I'll have to remember that.";
        }
    }

}
