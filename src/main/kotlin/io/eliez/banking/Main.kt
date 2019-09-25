package io.eliez.banking

import com.fasterxml.jackson.databind.SerializationFeature
import io.eliez.banking.common.TraceIdGenerator
import io.eliez.banking.common.serializeAsString
import io.eliez.banking.service.BankService
import io.eliez.banking.service.DatabaseFactory
import io.eliez.banking.web.account
import io.eliez.banking.web.onlineApiDoc
import io.eliez.banking.web.transfer
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.*
import io.ktor.jackson.jackson
import io.ktor.routing.Routing
import org.slf4j.event.Level
import java.math.BigDecimal

fun Application.module() {
    install(DefaultHeaders)
    install(CallId) {
        // Following ZipKin convention
        header("X-B3-TraceId")
        generate { TraceIdGenerator.newRequestId() }
    }
    install(CallLogging) {
        level = Level.INFO
        callIdMdc("traceId")
    }
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
            serializeAsString(BigDecimal::class)
        }
    }

    DatabaseFactory.init()

    val bankService = BankService()

    install(Routing) {
        onlineApiDoc()
        account(bankService)
        transfer(bankService)
    }
}
