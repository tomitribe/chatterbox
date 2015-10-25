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
package org.superbiz.imap;

import org.tomitribe.imap.api.ImapMailFilter;
import org.tomitribe.imap.api.MailListener;

import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

@MessageDriven(activationConfig = { @ActivationConfigProperty(propertyName = "folder", propertyValue = "inbox") })
public class InboxReader implements MailListener {

    @Resource(name = "OutgoingMail")
    private Session session;


    @ImapMailFilter
    public void autoReply(final Message message) {
        final String sender = System.getProperty("autoreply.from");

        try {
            final Message outgoingMessage = new MimeMessage(session);
            outgoingMessage.setFrom(new InternetAddress(sender));
            outgoingMessage.setRecipients(Message.RecipientType.TO, message.getFrom());

            outgoingMessage.setSubject("[Auto reply] " + message.getSubject());
            message.setText("This is an automated response");

            Transport.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

}
