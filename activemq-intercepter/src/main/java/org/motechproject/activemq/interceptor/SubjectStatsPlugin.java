package org.motechproject.activemq.interceptor;

import org.apache.activemq.broker.BrokerPluginSupport;
import org.apache.activemq.broker.ConnectionContext;
import org.apache.activemq.broker.ConsumerBrokerExchange;
import org.apache.activemq.broker.ProducerBrokerExchange;
import org.apache.activemq.broker.region.MessageReference;
import org.apache.activemq.command.*;
import org.apache.activemq.state.ProducerState;
import org.apache.activemq.util.IdGenerator;
import org.apache.activemq.util.LongSequenceGenerator;
import org.motechproject.event.MotechEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import java.util.HashMap;
import java.util.Map;


public class SubjectStatsPlugin extends BrokerPluginSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SubjectStatsPlugin.class);

    // I'm not sure about threading in a plugin.  I may need to wrap map access in a synchronize block
    private static Map<String, Integer> map = new HashMap<String, Integer>();
    private static Map<String, String> subjectMap = new HashMap<String, String>();

    private static final IdGenerator ID_GENERATOR = new IdGenerator();
    private final LongSequenceGenerator messageIdGenerator = new LongSequenceGenerator();
    protected final ProducerId advisoryProducerId = new ProducerId();

    private static final String QUEUE_NAME = "QueueForEvents";

    @Override
    public void start() throws Exception {
        LOG.debug("Starting {}", getBrokerName());
        this.advisoryProducerId.setConnectionId(ID_GENERATOR.generateId());
        super.start();
    }

    @Override
    public void stop() throws Exception {
        LOG.debug("Stopping {}", getBrokerName());
        super.stop();
    }

    private static synchronized void enqueue(Message msg) {
        ActiveMQDestination msgDest = msg.getDestination();
        String physicalName = msgDest.getPhysicalName();

        LOG.debug("Message sent to {}", physicalName);
        if (physicalName.endsWith(QUEUE_NAME)) {
            LOG.debug("Attempt to cast to MotechEvent");
            String subject;

            try {
                MotechEvent event = (MotechEvent) ((ActiveMQObjectMessage) msg).getObject();
                subject = event.getSubject();
            } catch (JMSException e) {
                subject = "Exception: " + e.getMessage();
            }

            LOG.debug("ENQUEUE Subject: {}", subject);
            Integer key = map.get(subject);
            if (null == key) {
                map.put(subject, 1);
                LOG.debug("ENQUEUE: New subject setting value to 1");
            } else {
                key = key + 1;
                map.put(subject, key);
                LOG.debug("ENQUEUE: repeat subject current queue depth {}", key);
            }

            subjectMap.put(msg.getMessageId().toString(), subject);
        }
    }

    private static synchronized void dequeue(Message msg) {
        if (null == msg) return;

        // If the destination is 'QueueForEvents' then record subject counts
        ActiveMQDestination msgDest = msg.getDestination();
        String physicalName = msgDest.getPhysicalName();

        if (physicalName.endsWith(QUEUE_NAME)) {
            try {
                MotechEvent event = (MotechEvent) ((ActiveMQObjectMessage) msg).getObject();
                String subject = event.getSubject();

                    dequeue(subject);
            }
            catch (JMSException e) {
                LOG.error("Exception converting to MotechEvent: {}", e);
            }
        }
    }

    private static synchronized void dequeue(String subject) {
        LOG.debug("DEQUEUE Subject: {}", subject);
        Integer key = map.get(subject);
        if (null == key) {
            LOG.error("DEQUEUE: Exception delivery of a missing subject: {}", subject);
            map.put(subject, 0);
        } else {
            key = key - 1;
            map.put(subject, key);
            LOG.debug("DEQUEUE: repeat subject current queue depth {}", key);
        }
    }

    /*
    @Override
    public void messageDelivered(ConnectionContext context, MessageReference messageReference) {
        Message msg = messageReference.getMessage();

        LOG.debug("ENQUEUE: messageDelivered");
        enqueue(msg);

        super.messageDelivered(context, messageReference);
    }

    @Override
    public void messageConsumed(ConnectionContext context, MessageReference messageReference) {
        Message msg = messageReference.getMessage();

        LOG.debug("DEQUEUE: messageConsumed");
        dequeue(msg);

        super.messageConsumed(context, messageReference);
    }

    @Override
    public void postProcessDispatch(MessageDispatch messageDispatch) {
        Message msg = messageDispatch.getMessage();

        LOG.debug("DEQUEUE: postProcessDispatch");
        dequeue(msg);

        super.postProcessDispatch(messageDispatch);
    }
    */

    @Override
    public void acknowledge(ConsumerBrokerExchange consumerExchange, MessageAck ack) throws Exception {
        // From the ack I can get the messageId.  I'll need to store a mapping of that to the message's subject
        String subject = subjectMap.get(ack.getLastMessageId().toString());

        if (subject != null) {
            subjectMap.remove(ack.getLastMessageId().toString());
            dequeue(subject);
        }

        super.acknowledge(consumerExchange, ack);
    }

    @Override
    public void send(ProducerBrokerExchange producerExchange, Message msg) throws Exception {

        // If the destination is 'QueueForEvents' then record subject counts
        ActiveMQDestination replyTo = msg.getReplyTo();

        enqueue(msg);

        if (replyTo != null) {
            sendStats(producerExchange.getConnectionContext(), replyTo);
        }

        super.send(producerExchange, msg);
    }


    private void sendStats(ConnectionContext context, ActiveMQDestination replyTo) {
        //        ActiveMQMapMessage statsMessage = new ActiveMQMapMessage();
        ActiveMQTextMessage statsMessage = new ActiveMQTextMessage();

        statsMessage.setDestination(replyTo);
        statsMessage.setJMSTimestamp(System.currentTimeMillis());
        statsMessage.setMessageId(new MessageId(this.advisoryProducerId, this.messageIdGenerator.getNextSequenceId()));

        boolean originalFlowControl = context.isProducerFlowControl();
        try {
            // I should use a json library.  I just didn't want to create a map of map and
            // then immediatly convert it to json.  Actually I could probably not use json
            // and just put key=value pairs, each on their own line.
            String body = "{\"timestamp\": " + System.currentTimeMillis() + ", \"subjects\": [";
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                body += "{\"subject\": \"" + entry.getKey() + "\", \"value\": " + entry.getValue().intValue() + "},";
            }

            if (!map.isEmpty()) {
                body = body.substring(0, body.length() - 1);
            }

            body += "] }";
            statsMessage.setText(body);

            /*
            statsMessage.setLong("timestamp", System.currentTimeMillis());
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                statsMessage.setInt(entry.getKey(), entry.getValue().intValue());
            }
            */

            context.setProducerFlowControl(false);
            ProducerInfo info = new ProducerInfo();
            ProducerState state = new ProducerState(info);
            ProducerBrokerExchange producerExchange = new ProducerBrokerExchange();
            producerExchange.setProducerState(state);
            producerExchange.setMutable(true);
            producerExchange.setConnectionContext(context);
            context.getBroker().send(producerExchange, statsMessage);
        } catch (Exception e) {
            LOG.error("Exception sending stats message: {}", e);
        } finally {
            context.setProducerFlowControl(originalFlowControl);
        }
    }

/*
    @Override
    public void postProcessDispatch(MessageDispatch messageDispatch) {
        Message msg = messageDispatch.getMessage();

        LOG.debug("DEQUEUE: postProcessDispatch");
        dequeue(msg);

        super.postProcessDispatch(messageDispatch);
    }


    @Override
    public void messageExpired(ConnectionContext context, MessageReference message, Subscription subscription) {

        if (isLogAll() || isLogInternalEvents()) {
            String msg = "Unable to display message.";

            msg = message.getMessage().toString();

            LOG.info("Message has expired: {}", msg);
        }
        super.messageExpired(context, message, subscription);
    }

    @Override
    public boolean sendToDeadLetterQueue(ConnectionContext context, MessageReference messageReference,
                                         Subscription subscription, Throwable poisonCause) {
        if (isLogAll() || isLogInternalEvents()) {
            String msg = "Unable to display message.";

            msg = messageReference.getMessage().toString();

            LOG.info("Sending to DLQ: {}", msg);
        }
        return super.sendToDeadLetterQueue(context, messageReference, subscription, poisonCause);
    }

    @Override
    public void messageConsumed(ConnectionContext context, MessageReference messageReference) {
        if (isLogAll() || isLogConsumerEvents() || isLogInternalEvents()) {
            String msg = "Unable to display message.";

            msg = messageReference.getMessage().toString();

            LOG.info("Message consumed: {}", msg);
        }
        super.messageConsumed(context, messageReference);
    }

    @Override
    public void messageDiscarded(ConnectionContext context, Subscription sub, MessageReference messageReference) {
        if (isLogAll() || isLogInternalEvents()) {
            String msg = "Unable to display message.";

            msg = messageReference.getMessage().toString();

            LOG.info("Message discarded: {}", msg);
        }
        super.messageDiscarded(context, sub, messageReference);
    }

    @Override
    public Response messagePull(ConnectionContext context, MessagePull pull) throws Exception {
        if (isLogAll() || isLogConsumerEvents()) {
            LOG.info("Message Pull from: {} on {}", context.getClientId(), pull.getDestination().getPhysicalName());
        }
        return super.messagePull(context, pull);
    }
*/
}
