package io.eliez.banking.web

import com.fasterxml.jackson.core.JsonProcessingException
import io.eliez.banking.model.NewAccount
import io.eliez.banking.service.BankService
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route

fun Route.account(bankService: BankService) {

    route("/api/v1/accounts") {

        get("{iban}") {
            val iban = call.parameters["iban"]!!
            val account = bankService.getAccount(iban)
            if (account != null) {
                call.respond(account)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        post {
            val (statusCode: HttpStatusCode, message: String?) = try {
                val account = call.receive<NewAccount>()
                bankService.createAccount(account)
                HttpStatusCode.Created to null
            } catch (e: JsonProcessingException) {
                HttpStatusCode.BadRequest to e.message
            }
            call.response.status(statusCode)
            message?.let {
                call.respond(mapOf("message" to it))
            }
        }
    }
}
