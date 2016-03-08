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

import javax.ejb.MessageDriven;

@MessageDriven(name = "Status")
public class KnockKnock implements TwitterUpdates {

    @Tweet(".*do you like {thing}?")
    public String like(@TweetParam("thing") String thing) {
        return "I'm not sure if I like "+thing;
    }

    @Tweet(".*KNOCK KNOCK.*")
    public String loudKnock() {
        return "Not so loud, you're giving me a headache!";
    }

    @Tweet(".*[Kk]nock(,? |-)[Kk]nock.*")
    public Response knockKnock() {

        return Response.message("Who's there?")
                .dialog(new WhosThere())
                .build();
    }

    public class WhosThere {

        @Tweet(".* {who}")
        public Response who(@TweetParam("who") final String who) {
            return Response.message(who + " who?")
                    .dialog(new Who())
                    .build();
        }
    }

    public class Who {

        public String punchline() {
            return "Haha, lol. That's a good one, I'll have to remember that.";
        }
    }

}
