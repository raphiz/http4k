package org.http4k.format

import com.beust.klaxon.JsonBase
import com.beust.klaxon.JsonObject
import com.beust.klaxon.JsonParsingException
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.ContentType.Companion.APPLICATION_JSON
import org.http4k.core.HttpMessage
import org.http4k.core.with
import org.http4k.lens.BiDiBodyLensSpec
import org.http4k.lens.BiDiWsMessageLensSpec
import org.http4k.lens.ContentNegotiation
import org.http4k.lens.string
import org.http4k.websocket.WsMessage
import java.io.InputStream
import kotlin.reflect.KClass
import com.beust.klaxon.Klaxon as KKlaxon

open class ConfigurableKlaxon(private val klaxon: KKlaxon,
                              override val defaultContentType: ContentType = APPLICATION_JSON) : AutoMarshalling() {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> asA(input: String, target: KClass<T>) = asA(input.byteInputStream(), target)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> asA(input: InputStream, target: KClass<T>): T =
        when (val json = klaxon.parser(target).parse(input.reader()) as JsonBase) {
            is JsonObject -> klaxon.fromJsonObject(json, target.java, target) as T
            else -> throw JsonParsingException("Top level lists are not supported by Klaxon!")
        }

    override fun asFormatString(input: Any) = klaxon.toJsonString(input)

    inline fun <reified T : Any> Body.Companion.auto(description: String? = null,
                                                     contentNegotiation: ContentNegotiation = ContentNegotiation.None,
                                                     contentType: ContentType = defaultContentType): BiDiBodyLensSpec<T> = autoBody(description, contentNegotiation, contentType)

    inline fun <reified T : Any> autoBody(description: String? = null,
                                          contentNegotiation: ContentNegotiation = ContentNegotiation.None,
                                          contentType: ContentType = defaultContentType)
        : BiDiBodyLensSpec<T> =
        Body.string(contentType, description, contentNegotiation).map({ asA(it, T::class) }, { asFormatString(it) })

    inline fun <reified T : Any> WsMessage.Companion.auto(): BiDiWsMessageLensSpec<T> = WsMessage.string().map({ it.asA(T::class) }, { asFormatString(it) })

    inline fun <reified T: Any, R: HttpMessage> R.with(t: T): R = with<R>(Body.auto<T>().toLens() of t)
}

fun KKlaxon.asConfigurable() = asConfigurable(KKlaxon())

inline operator fun <reified T : Any> ConfigurableKlaxon.invoke(msg: HttpMessage): T = autoBody<T>().toLens()(msg)
inline operator fun <reified T : Any, R : HttpMessage> ConfigurableKlaxon.invoke(item: T) = autoBody<T>().toLens().of<R>(item)
