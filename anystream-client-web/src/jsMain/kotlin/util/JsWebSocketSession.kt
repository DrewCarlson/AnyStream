/**
 * AnyStream
 * Copyright (C) 2022 AnyStream Maintainers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package anystream.frontend.util

import io.ktor.client.plugins.websocket.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.khronos.webgl.*
import org.w3c.dom.*
import kotlin.coroutines.*

@Suppress("CAST_NEVER_SUCCEEDS")
internal class JsWebSocketSession(
    override val coroutineContext: CoroutineContext,
    private val websocket: WebSocket
) : DefaultWebSocketSession {
    private val _closeReason: CompletableDeferred<CloseReason> = CompletableDeferred()
    private val _incoming: Channel<Frame> = Channel(Channel.UNLIMITED)
    private val _outgoing: Channel<Frame> = Channel(Channel.UNLIMITED)

    override val incoming: ReceiveChannel<Frame> = _incoming
    override val outgoing: SendChannel<Frame> = _outgoing

    override val extensions: List<WebSocketExtension<*>>
        get() = emptyList()

    override val closeReason: Deferred<CloseReason?> = _closeReason

    override var maxFrameSize: Long
        get() = Long.MAX_VALUE
        set(_) {}

    init {
        websocket.binaryType = BinaryType.ARRAYBUFFER

        websocket.addEventListener(
            "message",
            callback = {
                val event = it.unsafeCast<MessageEvent>()

                launch {
                    val frame: Frame = when (val data = event.data) {
                        is ArrayBuffer -> Frame.Binary(false, Int8Array(data).unsafeCast<ByteArray>())
                        is String -> Frame.Text(data)
                        else -> {
                            val error = IllegalStateException("Unknown frame type: ${event.type}")
                            _closeReason.completeExceptionally(error)
                            throw error
                        }
                    }

                    _incoming.trySend(frame).isSuccess
                }
            }
        )

        websocket.addEventListener(
            "error",
            callback = {
                val cause = WebSocketException("$it")
                _closeReason.completeExceptionally(cause)
                _incoming.close(cause)
                _outgoing.cancel()
            }
        )

        websocket.addEventListener(
            "close",
            callback = { event: dynamic ->
                launch {
                    val reason = CloseReason(event.code as Short, event.reason as String)
                    _closeReason.complete(reason)
                    _incoming.send(Frame.Close(reason))
                    _incoming.close()

                    _outgoing.cancel()
                }
            }
        )

        @OptIn(DelicateCoroutinesApi::class)
        launch {
            // Note: To ensure close frame is dispatched outgoing messages
            // will always be consumed, if the parent scope is cancelled
            // only the close frame can be dispatched.
            withContext(NonCancellable) {
                @OptIn(ExperimentalCoroutinesApi::class)
                _outgoing.consumeEach {
                    if (!this@launch.isActive && it !is Frame.Close) {
                        return@consumeEach
                    }
                    when (it.frameType) {
                        FrameType.TEXT -> {
                            val text = it.data
                            websocket.send(String(text))
                        }
                        FrameType.BINARY -> {
                            val source = it.data as Int8Array
                            val frameData = source.buffer.slice(
                                source.byteOffset,
                                source.byteOffset + source.byteLength
                            )

                            websocket.send(frameData)
                        }
                        FrameType.CLOSE -> {
                            val data = buildPacket { writeFully(it.data) }
                            val code = data.readShort()
                            val reason = data.readText()
                            _closeReason.complete(CloseReason(code, reason))
                            if (code.isReservedStatusCode()) {
                                websocket.close()
                            } else {
                                websocket.close(code, reason)
                            }
                        }
                        FrameType.PING, FrameType.PONG -> {
                            // ignore
                        }
                    }
                }
            }
        }

        coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause == null) {
                websocket.close()
            } else {
                websocket.close(CloseReason.Codes.INTERNAL_ERROR.code, "Client failed")
            }
        }
    }

    @OptIn(InternalAPI::class)
    override fun start(negotiatedExtensions: List<WebSocketExtension<*>>) {
        require(negotiatedExtensions.isEmpty()) { "Extensions are not supported." }
    }

    override suspend fun flush() {
    }

    @Deprecated(
        "Use cancel() instead.",
        ReplaceWith("cancel()", "kotlinx.coroutines.cancel")
    )
    override fun terminate() {
        _incoming.cancel()
        _outgoing.cancel()
        _closeReason.cancel("WebSocket terminated")
        websocket.close()
    }

    @OptIn(InternalAPI::class)
    private fun Short.isReservedStatusCode(): Boolean {
        return CloseReason.Codes.byCode(this).let { resolved ->
            @Suppress("DEPRECATION")
            resolved == null || resolved == CloseReason.Codes.CLOSED_ABNORMALLY
        }
    }
}
