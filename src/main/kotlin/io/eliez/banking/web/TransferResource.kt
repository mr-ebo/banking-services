package io.eliez.banking.web

import io.eliez.banking.model.NewTransfer
import io.eliez.banking.service.BankService
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.route

fun Route.transfer(bankService: BankService) {

    route("/api/v1/transfers") {

        post("/") {
            val transfer = call.receive<NewTransfer>()
            try {
                bankService.createTransfer(transfer)
                call.respond(HttpStatusCode.Created)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest)
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.Conflict)
            }
        }
    }
}
