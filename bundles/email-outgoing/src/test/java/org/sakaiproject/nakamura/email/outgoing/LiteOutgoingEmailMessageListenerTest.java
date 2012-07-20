package org.sakaiproject.nakamura.email.outgoing;


import static org.mockito.Mockito.when;

import org.apache.commons.mail.EmailException;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.activemq.ConnectionFactoryService;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.message.MessageConstants;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.jcr.Session;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;


@RunWith(MockitoJUnitRunner.class)
public class LiteOutgoingEmailMessageListenerTest {

  @Mock
  ComponentContext componentContext;
  @Mock
  ConnectionFactoryService connFactoryService;
  @Mock
  ConnectionFactory connectionFactory;
  @Mock
  Connection connection;
  @Mock
  Message message;
  @Mock
  ContentManager cm; 
  @Mock
  Content messageContent;
  @Mock
  SlingRepository repository;
  @Mock
  org.sakaiproject.nakamura.api.lite.Session sparseSession;

  @Test
  public void testEmailServerDown() throws Exception {

    LiteOutgoingEmailMessageListener liteOutgoingEmailMessageListener = new LiteOutgoingEmailMessageListener();

    //activate
    Dictionary<String, Object> properties = new Hashtable<String, Object>();
    properties.put("sakai.email.maxRetries", 100);
    properties.put("sakai.email.retryIntervalMinutes", 100);
    when(componentContext.getProperties()).thenReturn(properties);
    when(connFactoryService.getDefaultConnectionFactory()).thenReturn(connectionFactory);
    when(connectionFactory.createConnection()).thenReturn(connection);
    when(connection.createSession(false, javax.jms.Session.AUTO_ACKNOWLEDGE)).thenThrow(new JMSException("test"));
    liteOutgoingEmailMessageListener.connFactoryService = connFactoryService;
    liteOutgoingEmailMessageListener.activate(componentContext);

    //mock jcr session
    Session adminSession = Mockito.mock(javax.jcr.Session.class, Mockito.withSettings().extraInterfaces(SessionAdaptable.class));
    //mock sparse session
    when(((SessionAdaptable) adminSession).getSession()).thenReturn(sparseSession); 
    liteOutgoingEmailMessageListener.repository = repository;
    when(message.getStringProperty("nodePath")).thenReturn("/fake/path");
    when(message.getStringProperty("contentPath")).thenReturn("/fake/path");
    when(message.getObjectProperty("recipients")).thenReturn( new ArrayList<String>());
    when(cm.get("/fake/path")).thenReturn(messageContent);
    when(repository.loginAdministrative(null)).thenReturn(adminSession);
    when(sparseSession.getContentManager()).thenReturn(cm);
    when(messageContent.hasProperty(MessageConstants.PROP_SAKAI_MESSAGEBOX)).thenReturn(true);
    when(messageContent.getProperty(MessageConstants.PROP_SAKAI_MESSAGEBOX)).thenReturn(MessageConstants.BOX_OUTBOX);
    when(messageContent.hasProperty(MessageConstants.PROP_SAKAI_TO)).thenReturn(true);
    when(messageContent.hasProperty(MessageConstants.PROP_SAKAI_FROM)).thenReturn(true);
    LiteOutgoingEmailMessageListener spyLiteOutgoingEmailMessageListener = Mockito.spy(liteOutgoingEmailMessageListener);
    Mockito.doThrow(new EmailException("Server is down", new ConnectException("The email server is down"))).when(spyLiteOutgoingEmailMessageListener).constructMessage(Mockito.any(Content.class), Mockito.anyList(), Mockito.any(Session.class), Mockito.any(org.sakaiproject.nakamura.api.lite.Session.class));
    spyLiteOutgoingEmailMessageListener.onMessage(message);
    Mockito.verify(spyLiteOutgoingEmailMessageListener).scheduleRetry(messageContent);
  }
}
