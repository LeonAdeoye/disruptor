package com.leon.service;

// This is the biggest behavioural difference between queues and the Disruptor. When you have multiple consumers listening on the same Disruptor
// all events are published to all consumers in contrast to a queue where a single event will only be sent to a single consumer.
// The behaviour of the Disruptor is intended to be used in cases where you need to independent multiple parallel operations on the same data.
// The canonical example from LMAX is where we have three operations, journal writing (writing the input data to a persistent journal file),
// replication (sending the input data to another machine to ensure that there is a remote copy of the data), and business logic (the real processing work).
// 3 Event Handlers listening (JournalConsumer, ReplicationConsumer and ApplicationConsumer) to the Disruptor,
// each of these Event Handlers will receive all of the messages available in the Disruptor (in the same order).

import com.leon.model.DisruptorEvent;
import com.leon.model.DisruptorEventFactory;
import com.leon.model.DisruptorPayload;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.lmax.disruptor.EventHandler;
import java.time.Duration;
import java.time.Instant;

@Service
public class DisruptorServiceImpl implements DisruptorService
{
    private static final Logger logger = LoggerFactory.getLogger(DisruptorServiceImpl.class);
    private int counter;
    private long timeTaken = 0;
    private Disruptor<DisruptorEvent> disruptor;

    @Autowired
    ConfigurationServiceImpl configurationService;

    @Autowired
    MessageService messageService;

    @Override
    public void start(String name, EventHandler<DisruptorEvent> journalHandler, EventHandler<DisruptorEvent> actionEventHandler)
    {
        counter = 0;
        // The factory for the event
        DisruptorEventFactory factory = new DisruptorEventFactory();

        // Construct the Disruptor
        Disruptor<DisruptorEvent> disruptor = new Disruptor<DisruptorEvent>(factory, configurationService.getBufferSize(),
                DaemonThreadFactory.INSTANCE, ProducerType.SINGLE, new BlockingWaitStrategy());

        disruptor.handleEventsWith(journalHandler).then(actionEventHandler);

        // Start the Disruptor, starts all threads running
        disruptor.start();

        // Get the ring buffer from the Disruptor to be used for publishing.
        RingBuffer<DisruptorEvent> ringBuffer = disruptor.getRingBuffer();
        Instant currentTimeStamp = Instant.now();

        timeTaken = Duration.between(currentTimeStamp, Instant.now()).toMillis();
    }

    @Override
    public void stop()
    {
        System.out.println(String.format("Time taken to process %d events is %dms.", counter, timeTaken));
        disruptor.halt();
        disruptor.shutdown();
    }

    @Override
    public void push(DisruptorPayload payLoad)
    {

    }
}
