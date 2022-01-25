package com.leon.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.ErrorHandler;

@Service
public class JmsErrorHandler implements ErrorHandler
{
	private static final Logger logger = LoggerFactory.getLogger(JmsErrorHandler.class);

	@Override
	public void handleError(Throwable t)
	{
		logger.error("Within default jms error handler, error thrown : {}", t.getMessage());
	}
}
