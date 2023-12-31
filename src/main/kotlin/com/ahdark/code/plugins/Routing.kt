package com.ahdark.code.plugins

import com.ahdark.code.controllers.GitHubWebhookController
import com.ahdark.code.controllers.HealthController
import com.ahdark.code.entities.BaseResponse
import com.ahdark.code.entities.StatusCode
import com.ahdark.code.entities.github.webhook.EventType
import com.ahdark.code.services.EventHandleFactoryService
import com.ahdark.code.services.SignatureService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    install(Resources)
    install(AutoHeadResponse)

    val signatureService: SignatureService by inject()
    val eventHandleFactoryService: EventHandleFactoryService by inject()

    routing {
        get<HealthController> {
            call.respond(BaseResponse.success())
        }

        post<GitHubWebhookController> {
            val event = call.request.header("X-GitHub-Event")?.let { EventType.fromString(it) }
            val signature = call.request.header("X-Hub-Signature-256")

            if (event == null || signature == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    BaseResponse.error(
                        code = StatusCode.BAD_REQUEST,
                        msg = "Missing required headers"
                    )
                )
                return@post
            }

            val requestBody = call.receiveText().toByteArray(charset = Charsets.UTF_8)

            if (!signatureService.verifySignature(signature, requestBody)) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    BaseResponse.error(
                        code = StatusCode.FORBIDDEN,
                        msg = "Signature verification failed"
                    )
                )
                return@post
            }

            try {
                eventHandleFactoryService.handleEvent(event, requestBody)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    BaseResponse.error(
                        code = StatusCode.BAD_REQUEST,
                        msg = e.message ?: "Unknown error"
                    )
                )

                return@post
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    BaseResponse.error(
                        code = StatusCode.INTERNAL_SERVER_ERROR,
                        msg = e.message ?: "Unknown error"
                    )
                )

                return@post
            }

            call.respond(BaseResponse.success())
            return@post
        }
    }
}
