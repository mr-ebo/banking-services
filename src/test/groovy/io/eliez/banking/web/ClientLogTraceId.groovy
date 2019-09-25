package io.eliez.banking.web

import org.apache.log4j.MDC

@Singleton
class ClientLogTraceId {

    def setup(String traceId) {
        MDC.put('traceId', traceId)
    }

    def cleanup() {
        MDC.clear()
    }
}
