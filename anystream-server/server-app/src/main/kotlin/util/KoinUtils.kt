package anystream.util

import io.ktor.server.routing.Route
import org.koin.ktor.ext.get

inline fun <reified T : Any> Route.koinGet(): T = get()
