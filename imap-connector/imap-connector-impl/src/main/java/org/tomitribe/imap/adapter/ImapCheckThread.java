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

import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.search.FlagTerm;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public class ImapCheckThread extends Thread {
    private final ImapResourceAdapter resourceAdapter;
    private final Session session;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public ImapCheckThread(ImapResourceAdapter resourceAdapter) {
        this.resourceAdapter = resourceAdapter;
        final Properties properties = System.getProperties();
        session = Session.getDefaultInstance(properties, null);
    }

    @Override
    public void run() {
        while (! stopped.get()) {
            try {
                final Store store = session.getStore(resourceAdapter.getProtocol());
                store.connect(resourceAdapter.getHost(), resourceAdapter.getPort(), resourceAdapter.getUsername(), resourceAdapter.getPassword());
                processFolder(store, "inbox");
            } catch (MessagingException e) {
                // ignore
            }

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    private void processFolder(Store store, String folderName) throws MessagingException {
        final Folder folder = store.getFolder(folderName);
        folder.open(Folder.READ_WRITE);

        final Message[] messages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));

        final FetchProfile fp = new FetchProfile();
        fp.add(FetchProfile.Item.ENVELOPE);
        fp.add(FetchProfile.Item.CONTENT_INFO);
        folder.fetch(messages, fp);

        for (final Message message : messages) {
            message.setFlag(Flags.Flag.SEEN, true);
            resourceAdapter.process(message);
        }
    }

    public void cancel() {
        stopped.set(true);
    }
}
