package com.leon.service;

import com.leon.handler.InboundJournalEventHandler;
import com.leon.handler.InventoryCheckEventHandler;
import com.leon.handler.OutboundJournalEventHandler;
import com.leon.handler.PublishingEventHandler;
import com.leon.io.DisruptorReader;
import com.leon.io.DisruptorWriter;
import com.leon.model.DisruptorPayload;
import com.leon.model.Inventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrchestrationServiceImpl implements OrchestrationService, MessageListener
{
    private static final Logger logger = LoggerFactory.getLogger(OrchestrationServiceImpl.class);
    @Autowired
    private DisruptorService inboundDisruptor;
    @Autowired
    private DisruptorService outboundDisruptor;
    @Autowired
    private ConfigurationServiceImpl configurationService;
    @Autowired
    private InstrumentService instrumentService;
    @Autowired
    private FxService fxService;
    @Autowired
    private BeanFactory beanFactory;

    private InventoryCheckEventHandler inventoryCheckEventHandler;
    private DisruptorReader requestReader;
    private DisruptorWriter responseWriter;

    @Value("${disruptor.reader.class}")
    private String disruptorReaderClass;
    @Value("${disruptor.writer.class}")
    private String disruptorWriterClass;
    @Value("${inbound.journal.recovery.file.path}")
    private String inboundJournalRecoveryFilePath;
    @Value("${chronicle.map.file.path}")
    private String chronicleMapFilePath;

    private boolean hasStarted = false;

    @PostConstruct
    public void initialization()
    {
        inventoryCheckEventHandler = new InventoryCheckEventHandler(outboundDisruptor, instrumentService, fxService);
        inventoryCheckEventHandler.start(chronicleMapFilePath);

        responseWriter = beanFactory.getBean(disruptorWriterClass, DisruptorWriter.class);
        requestReader = beanFactory.getBean(disruptorReaderClass, DisruptorReader.class);

        inboundDisruptor.start("INBOUND", new InboundJournalEventHandler(), inventoryCheckEventHandler);
        outboundDisruptor.start("OUTBOUND", new OutboundJournalEventHandler(), new PublishingEventHandler(responseWriter));

        logger.info("Completed initialization of components with isPrimary mode = " + configurationService.isPrimary());
    }

    @Override
    public void start()
    {
        if(!hasStarted)
        {
            logger.info("Now starting to listen to inbound requests...");
            requestReader.start();
            requestReader.readAll().subscribe((request) -> inboundDisruptor.push(request));
            hasStarted = true;
        }
        else
            logger.error("Cannot start components because they have already been started.");
    }

    @Override
    public void stop()
    {
        if(hasStarted)
        {
            inventoryCheckEventHandler.stop();
            inboundDisruptor.stop();
            outboundDisruptor.stop();
            requestReader.stop();
            logger.info("Shutdown and cleanup completed.");
        }
        else
            logger.error("Cannot stop components because they have not been started correctly.");
    }

    @Override
    public void upload(String sodFilePath)
    {
        if(!hasStarted)
            inventoryCheckEventHandler.uploadSODPositions(sodFilePath);
        else
            logger.error("Cannot upload SOD file because orchestration service is not in the right state.");
    }

    @Override
    public List<Inventory> getInventory()
    {
        return inventoryCheckEventHandler == null ? new ArrayList<>() : inventoryCheckEventHandler.getInventory();
    }

    @Override
    public void clearInventory()
    {
        inventoryCheckEventHandler.clearInventory();
    }

    @Override
    public void updateInventory(Inventory inventory)
    {
        inventoryCheckEventHandler.updateInventory(inventory);
    }

    @Override
    public void deleteInventory(Inventory inventory)
    {
        inventoryCheckEventHandler.deleteInventory(inventory);
    }

    @Override
    public boolean togglePrimary()
    {
        boolean isPrimary = responseWriter.togglePrimary();
        configurationService.setPrimary(isPrimary);
        logger.info("After toggling, the configuration of isPrimary mode is set to: " + isPrimary);
        return isPrimary;
    }

    @Override
	@JmsListener(destination = "${spring.activemq.position.check.request.topic}")
	public void onMessage(Message message)
	{
        if(!hasStarted)
            return;

		try
		{
			if(message instanceof TextMessage)
			{
				TextMessage textMessage = (TextMessage) message;
				String[] splitInput  = textMessage.getText().split("=");
				if (splitInput.length == 2)
                    inboundDisruptor.push(new DisruptorPayload(splitInput[0], splitInput[1]));
				else
					logger.error("Cannot push incorrect message onto disruptor because of format: {}. ", textMessage.getText());
			}
		}
		catch(Exception e)
		{
			logger.error("Received Exception with processing position check request from JMS listener: " + e.getLocalizedMessage());
		}
	}
}
