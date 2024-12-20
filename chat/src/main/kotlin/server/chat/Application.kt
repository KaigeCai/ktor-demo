package server.chat

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// 数据模型
@Serializable
data class Message(val sender: String, val receiver: String, val content: String)

// 存储 WebSocket 连接的容器
class UserConnection(@Suppress("unused") val id: String, val session: DefaultWebSocketSession)

// 存储当前所有用户的连接
val activeUsers = mutableMapOf<String, UserConnection>()

fun Application.module() {

    install(WebSockets) // 安装 WebSocket 插件

    install(ContentNegotiation) {
        json(Json { prettyPrint = true }) // 安装 JSON 序列化
    }

    routing {
        webSocket("/chat/{userId}") {
            val userId = call.parameters["userId"] ?: return@webSocket close(
                CloseReason(
                    CloseReason.Codes.VIOLATED_POLICY, "No userId provided"
                )
            )

            activeUsers[userId] = UserConnection(userId, this)

            try {
                send(Frame.Text("Welcome to the chat, $userId!"))

                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val messageText = frame.readText()
                            val message = try {
                                Json.decodeFromString<Message>(messageText)
                            } catch (e: Exception) {
                                continue
                            }

                            // 点对点消息转发
                            val receiverConnection = activeUsers[message.receiver]
                            if (receiverConnection != null) {
                                receiverConnection.session.send(
                                    Frame.Text(
                                        "server.chat.Message from ${message.sender}: ${message.content}"
                                    )
                                )
                            } else {
                                send(
                                    Frame.Text("User ${message.receiver} is not connected.")
                                )
                            }
                        }

                        else -> Unit // 处理其他类型的 frame
                    }
                }
            } catch (e: Exception) {
                println("User $userId disconnected: ${e.message}")
            } finally {
                // 清理连接
                activeUsers.remove(userId)
            }
        }
    }
}


fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}
