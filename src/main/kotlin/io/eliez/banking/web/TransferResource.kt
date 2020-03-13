package io.eliez.banking.web

import com.fasterxml.jackson.core.JsonProcessingException
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

        post {
            val (statusCode: HttpStatusCode, message: String?) = try {
                val transfer = call.receive<NewTransfer>()
                bankService.createTransfer(transfer)
                HttpStatusCode.Created to null
            } catch (e: JsonProcessingException) {
                HttpStatusCode.BadRequest to e.message
            } catch (e: IllegalArgumentException) {
                HttpStatusCode.BadRequest to e.message
            } catch (e: IllegalStateException) {
                HttpStatusCode.Conflict to e.message
            }
            call.response.status(statusCode)
            message?.let {
                call.respond(mapOf("message" to it))
            }
        }
    }
}
