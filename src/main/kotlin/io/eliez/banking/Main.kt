package io.eliez.banking

import com.fasterxml.jackson.databind.SerializationFeature
import io.eliez.banking.common.serializeAsString
import io.eliez.banking.service.BankService
import io.eliez.banking.service.DatabaseFactory
import io.eliez.banking.web.account
import io.eliez.banking.web.onlineApiDoc
import io.eliez.banking.web.transfer
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.jackson.jackson
import io.ktor.routing.Routing
import org.slf4j.event.Level
import java.math.BigDecimal

fun Application.module() {
    install(DefaultHeaders)
    install(CallLogging) {
        level = Level.INFO
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
