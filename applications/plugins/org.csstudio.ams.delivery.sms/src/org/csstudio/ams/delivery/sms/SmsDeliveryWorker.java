
/*
 * Copyright (c) 2011 Stiftung Deutsches Elektronen-Synchrotron,
 * Member of the Helmholtz Association, (DESY), HAMBURG, GERMANY.
 *
 * THIS SOFTWARE IS PROVIDED UNDER THIS LICENSE ON AN "../AS IS" BASIS.
 * WITHOUT WARRANTY OF ANY KIND, EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE. SHOULD THE SOFTWARE PROVE DEFECTIVE
 * IN ANY RESPECT, THE USER ASSUMES THE COST OF ANY NECESSARY SERVICING, REPAIR OR
 * CORRECTION. THIS DISCLAIMER OF WARRANTY CONSTITUTES AN ESSENTIAL PART OF THIS LICENSE.
 * NO USE OF ANY SOFTWARE IS AUTHORIZED HEREUNDER EXCEPT UNDER THIS DISCLAIMER.
 * DESY HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS,
 * OR MODIFICATIONS.
 * THE FULL LICENSE SPECIFYING FOR THE SOFTWARE THE REDISTRIBUTION, MODIFICATION,
 * USAGE AND OTHER RIGHTS AND OBLIGATIONS IS INCLUDED WITH THE DISTRIBUTION OF THIS
 * PROJECT IN THE FILE LICENSE.HTML. IF THE LICENSE IS NOT INCLUDED YOU MAY FIND A COPY
 * AT HTTP://WWW.DESY.DE/LEGAL/LICENSE.HTM
 *
 * $Id: DesyKrykCodeTemplates.xml,v 1.7 2010/04/20 11:43:22 bknerr Exp $
 */

package org.csstudio.ams.delivery.sms;

import java.util.ArrayList;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageListener;
import org.csstudio.ams.AmsActivator;
import org.csstudio.ams.AmsConstants;
import org.csstudio.ams.delivery.AbstractDeliveryWorker;
import org.csstudio.ams.delivery.action.AmsUserAction;
import org.csstudio.ams.delivery.jms.JmsAsyncConsumer;
import org.csstudio.ams.delivery.jms.JmsProperties;
import org.csstudio.ams.delivery.jms.JmsSender;
import org.csstudio.ams.delivery.message.BaseAlarmMessage.State;
import org.csstudio.ams.delivery.message.BaseIncomingMessage;
import org.csstudio.ams.delivery.queue.IncomingQueue;
import org.csstudio.ams.delivery.sms.internal.SmsConnectorPreferenceKey;
import org.csstudio.ams.internal.AmsPreferenceKey;
import org.csstudio.platform.utility.jms.JmsSimpleProducer;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smslib.AGateway;
import org.smslib.IInboundMessageNotification;
import org.smslib.InboundBinaryMessage;
import org.smslib.InboundMessage;
import org.smslib.Message.MessageTypes;

/**
 * @author mmoeller
 * @version 1.0
 * @since 17.12.2011
 */
public class SmsDeliveryWorker extends AbstractDeliveryWorker implements MessageListener,
                                                                         IInboundMessageNotification {
    
    private static final Logger LOG = LoggerFactory.getLogger(SmsDeliveryWorker.class);
    
    /** Name of the management topic for sending commands */
    private static final String MANAGE_COMMAND_TOPIC = "T_AMS_CON_MANAGE";
    private static final String MANAGE_COMMAND_TOPIC_SUB = "T_AMS_TSUB_CON_MANAGE";

    private JmsAsyncConsumer amsConsumer;
    
    private JmsSimpleProducer amsPublisherReply;
    
    private OutgoingSmsQueue outgoingQueue;
    
    private IncomingQueue<BaseIncomingMessage> incomingQueue;
    
    private SmsDeliveryDevice smsDevice;
    
    private ModemInfoContainer modemInfo;
    
    /** Status information of the current modem test */
    private ModemTestStatus testStatus;
    
    /** Reading period (in ms) for the modem */
    private long readWaitingPeriod;
    
    private boolean running;
    
    private boolean workerCheckFlag;
    
    /**
     * Constructor.
     */
    public SmsDeliveryWorker() {
        workerName = this.getClass().getSimpleName();
        outgoingQueue = new OutgoingSmsQueue();
        incomingQueue = new IncomingQueue<BaseIncomingMessage>();
        
        // First create the JMS connections
        initJms();
        
        modemInfo = new ModemInfoContainer();
        
        IPreferencesService prefs = Platform.getPreferencesService();
        String factoryClass = prefs.getString(AmsActivator.PLUGIN_ID,
                                              AmsPreferenceKey.P_JMS_AMS_CONNECTION_FACTORY_CLASS,
                                              "org.apache.activemq.jndi.ActiveMQInitialContextFactory",
                                              null);
        String url = prefs.getString(AmsActivator.PLUGIN_ID,
                                     AmsPreferenceKey.P_JMS_AMS_SENDER_PROVIDER_URL,
                                     "failover:(tcp://localhost:62616,tcp://localhost:64616)",
                                     null);
        String topic = prefs.getString(AmsActivator.PLUGIN_ID,
                                       AmsPreferenceKey.P_JMS_AMS_TOPIC_MONITOR,
                                       "T_AMS_SYSTEM_MONITOR",
                                       null);
        
        smsDevice = new SmsDeliveryDevice(modemInfo, new JmsProperties(factoryClass, url, topic));
        smsDevice.setInboundMessageNotification(this);
        
        readWaitingPeriod = prefs.getLong(SmsDeliveryActivator.PLUGIN_ID,
                                          SmsConnectorPreferenceKey.P_MODEM_READ_WAITING_PERIOD,
                                          10000L,
                                          null);
        LOG.info("readWaitingPeriod: {}", readWaitingPeriod);
        
        testStatus = new ModemTestStatus();
        running = true;
        workerCheckFlag = false;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        
        LOG.info(workerName + " is running.");
                
        while(running) {
            synchronized (outgoingQueue) {
                try {
                    if (outgoingQueue.isEmpty() && (!testStatus.isActive())) {
                        outgoingQueue.wait();
                    } else {
                        outgoingQueue.wait(readWaitingPeriod);
                    }
                    workerCheckFlag = false;
                } catch (InterruptedException ie) {
                    LOG.error("I have been interrupted.");
                }
            }
            
            if (outgoingQueue.hasContent()) {
                ArrayList<SmsAlarmMessage> outgoing = outgoingQueue.getCurrentContent();
                LOG.info("Zu senden: " + outgoing.size());
                for (SmsAlarmMessage o : outgoing) {
                    if (smsDevice.sendMessage(o) == false) {
                        if (o.getMessageState() == State.FAILED) {
                            LOG.error("Cannot send message: {}", o);
                            outgoingQueue.addMessage(o);
                            LOG.error("Re-Insert it into the message queue.");
                        } else {
                            // TODO: Handle the messages with the state BAD!
                            LOG.warn("Dicarding message: {}", o);
                        }
                    }
                }
                
                outgoing.clear();
                outgoing = null;
            }
            
            while (incomingQueue.hasContent()) {
                final BaseIncomingMessage inMsg = incomingQueue.nextMessage();
                if (processIncomingMessages(inMsg) == false) {
                    checkDeviceTest(inMsg);
                }
            }
        }

        smsDevice.stopDevice();
        closeJms();
        
        LOG.info("{} is leaving.", workerName);
    }
    
    private boolean processIncomingMessages(BaseIncomingMessage o) {
        
        boolean processed = false;
        
        if (!(o.getOriginalMessage() instanceof InboundBinaryMessage)) {
            InboundMessage msg = (InboundMessage) o.getOriginalMessage();
            AmsUserAction userAction = new AmsUserAction(msg.getText());
            if (userAction.hasValidFormat()) {
                MapMessage mapMsg = null;
                try {
                    mapMsg = amsPublisherReply.createMapMessage();
                    if (userAction.isReplyAlarmAction()) {
                        mapMsg.setString(AmsConstants.MSGPROP_MESSAGECHAINID_AND_POS,
                                         userAction.getChainIdAsString());
                        
                        mapMsg.setString(AmsConstants.MSGPROP_CONFIRMCODE,
                                         userAction.getConfirmCode());
                        
                        mapMsg.setString(AmsConstants.MSGPROP_REPLY_TYPE,
                                         AmsConstants.MSG_REPLY_TYPE_SMS);
                        
                        mapMsg.setString(AmsConstants.MSGPROP_REPLY_ADRESS,
                                         msg.getOriginator());
                        
                        LOG.info("Message parsed as alarm reply, start internal jms send");
                    } else if (userAction.isChangeGroupAction()
                               || userAction.isChangeUserAction()) {
                        
                        if(userAction.isChangeGroupAction()) {
                            mapMsg.setString(AmsConstants.MSGPROP_CHANGESTAT_ACTION, "group");
                        } else {
                            mapMsg.setString(AmsConstants.MSGPROP_CHANGESTAT_ACTION, "user");
                        }
                        
                        mapMsg.setString(AmsConstants.MSGPROP_CHANGESTAT_GROUPNUM,
                                         userAction.getGroupIdAsString());
                        
                        mapMsg.setString(AmsConstants.MSGPROP_CHANGESTAT_USERNUM,
                                         userAction.getUserIdAsString());
                        
                        mapMsg.setString(AmsConstants.MSGPROP_CHANGESTAT_STATUS,
                                         userAction.getStatusAsString());
                        
                        mapMsg.setString(AmsConstants.MSGPROP_CHANGESTAT_STATUSCODE,
                                         userAction.getConfirmCode());

                        mapMsg.setString(AmsConstants.MSGPROP_CHANGESTAT_REASON,
                                         userAction.getReason());
                        
                        mapMsg.setString(AmsConstants.MSGPROP_REPLY_TYPE,
                                         AmsConstants.MSG_REPLY_TYPE_SMS);
                        mapMsg.setString(AmsConstants.MSGPROP_REPLY_ADRESS,
                                         msg.getOriginator());
                        
                        LOG.info("Message parsed as change status, start internal jms send");
                    }
                    
                    amsPublisherReply.sendMessage(mapMsg);
                    LOG.info("Send internal jms message done");
                    processed = true;
                } catch(Exception e) {
                    LOG.error("[*** {} ***]: {}", e.getClass().getSimpleName(), e.getMessage());
                }
            } else {
                LOG.warn("Incoming message is NOT a user action: {}", msg.getText());
            }
        }
        
        return processed;
    }
    
    private boolean checkDeviceTest(BaseIncomingMessage o) {
        
        boolean checked = false;
        LOG.debug("Check for device test.");
        
        // Check modem test status first
        if(testStatus.isActive()) {
            LOG.info("Self test is active");
            if(testStatus.isTimeOut()) {
                LOG.warn("Current test timed out.");
                LOG.debug("Remaining gateways: " + testStatus.getGatewayCount());
                LOG.debug("Bad gateways before moving: " + testStatus.getBadModemCount());
                testStatus.moveGatewayIdToBadModems();
                LOG.debug("Remaining gateways after moving: " + testStatus.getGatewayCount());
                LOG.debug("Bad gateways after moving: " + testStatus.getBadModemCount());
                if(testStatus.getBadModemCount() == modemInfo.getModemCount()) {
                    LOG.error("No modem is working properly.");
                    smsDevice.sendTestAnswer(testStatus.getCheckId(), "No modem is working properly.", "MAJOR", "ERROR");
                } else {
                    String list = "";
                    for(String name : testStatus.getBadModems()) {
                        list = list + name + " ";
                    }
                    
                    LOG.warn("Modems not working properly: " + list);
                    smsDevice.sendTestAnswer(testStatus.getCheckId(), "Modems not working properly: " + list, "MINOR", "WARN");
                }
                
                LOG.info("Reset current test.");
                testStatus.reset();
            }
        }
        
        if (!(o.getOriginalMessage() instanceof InboundBinaryMessage)) {
            
            InboundMessage msg = (InboundMessage) o.getOriginalMessage();
            if (testStatus.isTestAnswer(msg.getText())) {
                // Have a look at the current check status
                if(testStatus.isActive()) {
                    if(testStatus.isTimeOut() == false) {
                        
                        LOG.info("Self test SMS");
                        LOG.info("Gateways waiting for answer: " + testStatus.getGatewayCount());
                        testStatus.checkAndRemove(msg.getText());
                        LOG.info("Gateways waiting for answer after remove: " + testStatus.getGatewayCount());
                        if((testStatus.getGatewayCount() == 0)) {
                            if(testStatus.getBadModemCount() == 0) {
                                LOG.info("All modems are working fine.");
                                smsDevice.sendTestAnswer(testStatus.getCheckId(),
                                                         "All modems are working fine.",
                                                         "NO_ALARM",
                                                         "OK");
                            } else {
                                String list = "";
                                for(String name : testStatus.getBadModems()) {
                                    list = list + name + " ";
                                }
                                
                                LOG.warn("Modems not working properly: " + list);
                                smsDevice.sendTestAnswer(testStatus.getCheckId(),
                                                         "Modems not working properly: " + list,
                                                         "MINOR",
                                                         "WARN");
                            }
                            
                            LOG.info("Reset current test.");
                            testStatus.reset();
                        }
                    }
                }
            }
            checked = true;
        } else {
            checked = true;
        }
        
        return checked;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void onMessage(Message msg) {
        
        LOG.debug("JMS message received: {}", msg);
        if (msg instanceof MapMessage) {
            DeviceTestMessageContent content =
                    new DeviceTestMessageContent((MapMessage) msg);
            if (content.isDeviceTestRequest()) {
                String dest = content.getDestination();
                if (dest.contains(workerName) || (dest.compareTo("*") == 0)) {
                    if((testStatus.isActive() == false) || testStatus.isTimeOut()) {
                        String checkId = content.getCheckId();
                        testStatus.reset();
                        testStatus.setCheckId(checkId);
                        smsDevice.sendDeviceTestMessage(testStatus);
                        synchronized (outgoingQueue) {
                            outgoingQueue.notify();
                        }
                    } else {
                        LOG.info("A modem check is still active. Ignoring the new modem check.");
                    }
                } else {
                    LOG.info("This message is not for me.");
                }
            }
        }
        
        acknowledge(msg);
    }

    @Override
    public void process(AGateway gateway, MessageTypes msgType, InboundMessage msg) {
        Object[] param = { msg.getText(), msg.getOriginator(), gateway.getGatewayId() };
        LOG.info("Incoming message: {} from phone number {} received by gateway {}", param);
        BaseIncomingMessage inMsg = new BaseIncomingMessage(msg);
        incomingQueue.addMessage(inMsg);
        if (smsDevice.deleteMessage(msg)) {
            LOG.info("Message is deleted.");
        } else {
            LOG.warn("Message cannot be deleted.");
        }
        synchronized (outgoingQueue) {
            outgoingQueue.notify();
        }
    }

    private void acknowledge(Message message) {
        try {
            message.acknowledge();
        } catch (JMSException jmse) {
            LOG.warn("Cannot acknowledge message: {}", message.toString());
        }
    }
    
    private boolean initJms() {
        
        boolean success = false;
        
        IPreferencesService prefs = Platform.getPreferencesService();
        String factoryClass = prefs.getString(AmsActivator.PLUGIN_ID,
                                              AmsPreferenceKey.P_JMS_AMS_CONNECTION_FACTORY_CLASS,
                                              "org.apache.activemq.jndi.ActiveMQInitialContextFactory",
                                              null);
        String url = prefs.getString(AmsActivator.PLUGIN_ID,
                                     AmsPreferenceKey.P_JMS_AMS_SENDER_PROVIDER_URL,
                                     "failover:(tcp://localhost:62616,tcp://localhost:64616)",
                                     null);
        String topic = prefs.getString(AmsActivator.PLUGIN_ID,
                                       AmsPreferenceKey.P_JMS_AMS_TOPIC_REPLY,
                                       "T_AMS_CON_REPLY",
                                       null);
        
        amsPublisherReply = new JmsSimpleProducer("SmsConnectorWorkSenderInternal",
                                                  url,
                                                  factoryClass,
                                                  topic);
        if (amsPublisherReply == null) {
            LOG.error("Could not create amsPublisherReply");
            return false;
        }

        try {
            
            final boolean durable = prefs.getBoolean(AmsActivator.PLUGIN_ID,
                                                     AmsPreferenceKey.P_JMS_AMS_CREATE_DURABLE,
                                                     false,
                                                     null);

            // Create the redundant receiver
            amsConsumer = new JmsAsyncConsumer("SmsConnectorWorkReceiverInternal",
                                               prefs.getString(AmsActivator.PLUGIN_ID,
                                                               AmsPreferenceKey.P_JMS_AMS_PROVIDER_URL_1,
                                                               "failover:(tcp://localhost:62616)",
                                                               null),
                                               prefs.getString(AmsActivator.PLUGIN_ID,
                                                               AmsPreferenceKey.P_JMS_AMS_PROVIDER_URL_2,
                                                               "failover:(tcp://localhost:64616)",
                                                               null));
           
            // Create first subscriber (default topic for the connector) 
            success = amsConsumer.createRedundantSubscriber(
                    "amsSubscriberSms",
                    prefs.getString(AmsActivator.PLUGIN_ID,
                                    AmsPreferenceKey.P_JMS_AMS_TOPIC_SMS_CONNECTOR,
                                    "T_AMS_CON_SMS",
                                    null),
                    prefs.getString(AmsActivator.PLUGIN_ID,
                                    AmsPreferenceKey.P_JMS_AMS_TSUB_SMS_CONNECTOR,
                                    "SUB_AMS_CON_SMS",
                                    null),
                    durable);
            if(success == false) {
                LOG.error("Could not create amsSubscriberSms");
                return false;
            }
            
            amsConsumer.addMessageListener("amsSubscriberSms", outgoingQueue);
            
            // Create second subscriber (topic for the device test) 
            success = amsConsumer.createRedundantSubscriber(
                    "amsSubscriberSmsDeviceTest",
                    prefs.getString(AmsActivator.PLUGIN_ID,
                                    AmsPreferenceKey.P_JMS_AMS_TOPIC_CONNECTOR_DEVICETEST,
                                    "T_AMS_CON_DEVICETEST",
                                    null),
                    prefs.getString(AmsActivator.PLUGIN_ID,
                                    AmsPreferenceKey.P_JMS_AMS_TSUB_SMS_CONNECTOR_DEVICETEST,
                                    "SUB_AMS_CON_SMS_DEVICETEST",
                                    null),
                    durable);
            if(success) {
                amsConsumer.addMessageListener("amsSubscriberSmsDeviceTest", this);
            } else {
                LOG.error("Could not create amsSubscriberSmsDeviceTest");
            }

            // Create third subscriber (topic for message management)
            // TODO: Replace constant with preference entry 
            success = amsConsumer.createRedundantSubscriber(
                    "amsConnectorManager",
                    MANAGE_COMMAND_TOPIC,
                    MANAGE_COMMAND_TOPIC_SUB,
                    durable);
            if(success == false)  {
                LOG.error("Could not create amsConnectorManager");
                return false;
            }

            return true;
        } catch(Exception e) {
            LOG.error("Could not init internal Jms", e);
            
            JmsSender sender = new JmsSender("SmsConnectorAlarmSender",
                                             prefs.getString(AmsActivator.PLUGIN_ID,
                                                             AmsPreferenceKey.P_JMS_AMS_SENDER_PROVIDER_URL,
                                                             "failover:(tcp://localhost:62616,tcp://localhost:64616)",
                                                             null),
                                             "ALARM");
            if(sender.isConnected()) {
                if(sender.sendMessage("alarm",
                                      "SmsConnectorWork: Cannot init internal Jms [" + e.getMessage() + "]",
                                      "MAJOR") == false) {
                    LOG.error("Cannot send alarm message.");
                }  else {
                    LOG.info("Alarm message sent.");
                }
            } else {
                LOG.warn("Alarm message sender is NOT connected.");
            }
            
            sender.closeAll();
            sender = null;
        }
        
        return false;
    }

    private void closeJms() {
        if (amsConsumer != null) {
            amsConsumer.closeAll();
        }
        
        if (amsPublisherReply != null) {
            amsPublisherReply.closeAll();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopWorking() {
        running = false;
        synchronized (outgoingQueue) {
            outgoingQueue.notify();
        }
    }

    @Override
    public boolean isWorking() {
        workerCheckFlag = true;
        synchronized (outgoingQueue) {
            outgoingQueue.notify();
        }
        Object localLock = new Object();
        synchronized (localLock) {
            try {
                localLock.wait(250);
            } catch (InterruptedException e) {
                // Ignore me
            }
        }
        return !workerCheckFlag;
    }
}
