package org.sakaiproject.nakamura.email.outgoing;


import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.sakaiproject.nakamura.api.message.MessageConstants.BOX_OUTBOX;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_FROM;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_MESSAGEBOX;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_TO;
import static org.sakaiproject.nakamura.email.outgoing.LiteOutgoingEmailMessageListener.MAX_RETRIES;
import static org.sakaiproject.nakamura.email.outgoing.LiteOutgoingEmailMessageListener.OPERATION_MODE;
import static org.sakaiproject.nakamura.email.outgoing.LiteOutgoingEmailMessageListener.OP_DISABLED;
import static org.sakaiproject.nakamura.email.outgoing.LiteOutgoingEmailMessageListener.OP_LOG;
import static org.sakaiproject.nakamura.email.outgoing.LiteOutgoingEmailMessageListener.OP_NOOP;
import static org.sakaiproject.nakamura.email.outgoing.LiteOutgoingEmailMessageListener.REPLY_AS_ADDRESS;
import static org.sakaiproject.nakamura.email.outgoing.LiteOutgoingEmailMessageListener.REPLY_AS_NAME;
import static org.sakaiproject.nakamura.email.outgoing.LiteOutgoingEmailMessageListener.RETRY_INTERVAL;
import static org.sakaiproject.nakamura.email.outgoing.LiteOutgoingEmailMessageListener.SMTP_PORT;
import static org.sakaiproject.nakamura.email.outgoing.LiteOutgoingEmailMessageListener.SMTP_SERVER;

import com.google.common.collect.ImmutableMap;

import org.apache.commons.mail.EmailException;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.activemq.ConnectionFactoryService;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.subethamail.wiser.Wiser;

import java.net.ConnectException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;


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
  SlingRepository repository;
  @Mock
  org.sakaiproject.nakamura.api.lite.Session sparseSession;
  @Mock
  javax.jms.Session session;
  @Mock
  MessageConsumer consumer;
  @Mock
  AuthorizableManager authzMgr;

  Session adminSession;
  LiteOutgoingEmailMessageListener listener;
  Wiser smtpServer;
  int smtpPort;
  Content messageContent;

  @Before
  public void setUp() throws Exception {
    listener = new LiteOutgoingEmailMessageListener();

    // mock jcr session
    adminSession = mock(javax.jcr.Session.class,
        withSettings().extraInterfaces(SessionAdaptable.class));

    // mock sparse session
    when(((SessionAdaptable) adminSession).getSession()).thenReturn(sparseSession);
    when(sparseSession.getAuthorizableManager()).thenReturn(authzMgr);

    // find a local open port to start the smtp test server on
    ServerSocket ss = new ServerSocket(0);
    smtpPort = ss.getLocalPort();
    ss.close();
    
    smtpServer = new Wiser();
    smtpServer.setPort(smtpPort);
    smtpServer.start();
  }

  @After
  public void tearDown() throws Exception {
    if (smtpServer != null) {
      smtpServer.stop();
    }
  }

  @Test
  public void testActivate() throws Exception {
    activateListener(listener, null);
    verify(connection, never()).close();
  }

  @Test
  public void testDeactivate() throws Exception {
    // really just for coverage. should cause no issue but that's the point.
    listener.deactivate(null);

    // setup the listener then verify that the connection gets closed.
    activateListener(listener, null);
    listener.deactivate(null);
    verify(connection).close();
  }

  @Test(expected = JMSException.class)
  public void testJmsConnectionFail() throws Exception {
    Map<String, Object> props = setupListener(listener, null);
    Mockito.reset(connection);
    when(connection.createSession(false, javax.jms.Session.AUTO_ACKNOWLEDGE)).thenThrow(
        new JMSException("mock exception from unit test"));
    listener.activate(props);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testEmailServerDown() throws Exception {
    activateListener(listener, null);

    setupMessageContent();

    // spy on the concrete object so we can force EmailException
    LiteOutgoingEmailMessageListener listenerSpy = spy(listener);
    doThrow(
        new EmailException("Server is down", new ConnectException(
            "The email server is down"))).when(listenerSpy).constructMessage(
        any(Content.class), anyList(), any(Session.class),
        any(org.sakaiproject.nakamura.api.lite.Session.class));

    // kill the mail server
    smtpServer.stop();
    listenerSpy.onMessage(message);
    verify(listenerSpy).scheduleRetry(messageContent);
  }

  @Test
  public void testOpModeDisabled() throws Exception {
    activateListener(listener,
        ImmutableMap.of(OPERATION_MODE, (Object) OP_DISABLED));
    // make sure we don't even try to connect to JMS
    verify(connFactoryService, never()).getDefaultConnectionFactory();
    assertEquals(0, smtpServer.getMessages().size());
  }

  @Test
  public void testOpModeNoop() throws Exception {
    activateListener(listener,
        ImmutableMap.of(OPERATION_MODE, (Object) OP_NOOP));
    // verify connecting to JMS
    verify(connFactoryService).getDefaultConnectionFactory();

    listener.onMessage(message);
    // verify that the message isn't touched
    verifyZeroInteractions(message);
    assertEquals(0, smtpServer.getMessages().size());
  }

  @Test
  public void testOpModeLog() throws Exception {
    activateListener(listener,
        ImmutableMap.of(OPERATION_MODE, (Object) OP_LOG));
    // verify connecting to JMS
    verify(connFactoryService).getDefaultConnectionFactory();

    LiteOutgoingEmailMessageListener listenerSpy = spy(listener);
    listenerSpy.onMessage(message);

    // verify that a retry is not scheduled.
    verify(listenerSpy, never()).scheduleRetry(any(Content.class));
    verify(listenerSpy, never()).scheduleRetry(anyInt(), any(Content.class));
    assertEquals(0, smtpServer.getMessages().size());
  }

  @Test
  public void testOpModeSend() throws Exception {
    setupMessageContent();
    activateListener(listener, null);
    listener.onMessage(message);
    assertEquals(1, smtpServer.getMessages().size());
  }

  // ---------- Helper methods -------------------------------------------------
  /**
   * @throws JMSException
   * @throws StorageClientException
   * @throws AccessDeniedException
   * @throws RepositoryException
   */
  private void setupMessageContent() throws JMSException, StorageClientException,
      AccessDeniedException, RepositoryException {
    when(message.getStringProperty("nodePath")).thenReturn("/fake/path");
    when(message.getStringProperty("contentPath")).thenReturn("/fake/path");
    when(message.getObjectProperty("recipients")).thenReturn("unittestto@localhost.localdomain");
    when(repository.loginAdministrative(null)).thenReturn(adminSession);
    when(sparseSession.getContentManager()).thenReturn(cm);

    messageContent = new Content("/fake/path", null);
    messageContent.setProperty(PROP_SAKAI_MESSAGEBOX, BOX_OUTBOX);
    messageContent.setProperty(PROP_SAKAI_TO, "unittestto@localhost.localdomain");
    messageContent.setProperty(PROP_SAKAI_FROM, "unittestfrom@localhost.localdomain");
    when(cm.get("/fake/path")).thenReturn(messageContent);
  }

  /**
   * @param listener
   * @throws JMSException
   */
  private void activateListener(LiteOutgoingEmailMessageListener listener,
      Map<String, Object> extraProps) throws JMSException {
    Map<String, Object> props = setupListener(listener, extraProps);
    listener.activate(props);
  }

  /**
   * @param listener
   * @return
   * @throws JMSException
   */
  private Map<String, Object> setupListener(LiteOutgoingEmailMessageListener listener,
      Map<String, Object> extraProps) throws JMSException {
    HashMap<String, Object> properties = new HashMap<String, Object>();
    properties.put(SMTP_SERVER, "localhost");
    properties.put(REPLY_AS_NAME, "OAE Unit Test");
    properties.put(REPLY_AS_ADDRESS, "no-reply@localhost.localdomain");
    properties.put(SMTP_PORT, smtpPort);
    properties.put(MAX_RETRIES, 100);
    properties.put(RETRY_INTERVAL, 100);
    if (extraProps != null) {
      properties.putAll(extraProps);
    }
    when(connFactoryService.getDefaultConnectionFactory()).thenReturn(connectionFactory);
    when(connectionFactory.createConnection()).thenReturn(connection);
    when(connection.createSession(false, javax.jms.Session.AUTO_ACKNOWLEDGE)).thenReturn(
        session);
    when(session.createConsumer(any(Destination.class))).thenReturn(consumer);
    listener.connFactoryService = connFactoryService;
    listener.repository = repository;
    return properties;
  }
}
