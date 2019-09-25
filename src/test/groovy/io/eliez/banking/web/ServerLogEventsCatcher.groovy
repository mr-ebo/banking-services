package io.eliez.banking.web

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

class ServerLogEventsCatcher {

    private ListAppender<ILoggingEvent> appender = new ListAppender<>()

    ServerLogEventsCatcher hook() {
        def rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        rootLogger.addAppender(appender)
        appender.start()
        return this
    }

    void unhook() {
        appender.stop()
        def rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        rootLogger.detachAppender(appender)
    }

    void assertMdc(String key, String expectedValue) {
        def events = appender.list
        assert !events.isEmpty()
        events.forEach { evt ->
            assert evt.getMDCPropertyMap().get(key) == expectedValue
        }
    }
}
