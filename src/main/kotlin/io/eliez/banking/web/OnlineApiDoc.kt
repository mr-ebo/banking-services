package io.eliez.banking.web

import io.ktor.http.content.defaultResource
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.routing.Routing

fun Routing.onlineApiDoc() {
    static("docs") {
        resources("static/docs")
        defaultResource("static/docs/index.html")
    }
}
