package com.papsign.ktor.openapigen.content.type.multipart

import com.papsign.ktor.openapigen.*
import com.papsign.ktor.openapigen.annotations.mapping.openAPIName
import com.papsign.ktor.openapigen.content.type.BodyParser
import com.papsign.ktor.openapigen.content.type.ContentTypeProvider
import com.papsign.ktor.openapigen.exceptions.OpenAPIParseException
import com.papsign.ktor.openapigen.exceptions.assertContent
import com.papsign.ktor.openapigen.model.operation.MediaTypeEncodingModel
import com.papsign.ktor.openapigen.model.operation.MediaTypeModel
import com.papsign.ktor.openapigen.model.schema.SchemaModel
import com.papsign.ktor.openapigen.modules.ModuleProvider
import com.papsign.ktor.openapigen.modules.ofType
import com.papsign.ktor.openapigen.parameters.util.localDateTimeFormatter
import com.papsign.ktor.openapigen.parameters.util.offsetDateTimeFormatter
import com.papsign.ktor.openapigen.parameters.util.zonedDateTimeFormatter
import com.papsign.ktor.openapigen.schema.builder.provider.FinalSchemaBuilderProviderModule
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import java.io.InputStream
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.set
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.jvmErasure

object MultipartFormDataContentProvider : BodyParser, OpenAPIGenModuleExtension {

    override fun <T : Any> getParseableContentTypes(type: KType): List<ContentType> {
        return listOf(ContentType.MultiPart.FormData)
    }

    data class MultipartCVT<T>(
        val default: T?,
        val type: KType,
        val clazz: KClass<*>,
        // val serializer: (T) -> String,
        val parser: (String) -> T,
    )

    inline fun <reified T> cvt(noinline parser: (String) -> T, default: T? = null) =
        MultipartCVT(default, getKType<T>(), T::class, parser)

    private fun createValueClassConverter(type: KType): MultipartCVT<Any?> {
        val unwrappedType = type.unwrappedType
        val primitiveConverter = conversionsByType[unwrappedType] ?: error("Unhandled Type $unwrappedType")
        val primaryConstructor = type.jvmErasure.primaryConstructor ?: error("primary constructor not exists")

        return MultipartCVT(
            default = primitiveConverter.default?.let { primaryConstructor.call(it) },
            type = type,
            clazz = type.jvmErasure,
            parser = parser@{ value ->
                val convertedValue = primitiveConverter.parser(value) ?: return@parser null
                primaryConstructor.call(convertedValue)
            },
        )
    }

    private val streamTypes = setOf(
        getKType<InputStream>(),
        getKType<ContentInputStream>(),
        getKType<NamedFileInputStream>(),
        getKType<InputStream?>(),
        getKType<ContentInputStream?>(),
        getKType<NamedFileInputStream?>()
    )

    private val conversions = setOf(
        cvt({ it }, ""),
        cvt(String::toInt, 0),
        cvt(String::toLong, 0),
        cvt(String::toFloat, .0f),
        cvt(String::toDouble, .0),
        cvt(Instant::parse),
        cvt(String::toBoolean, false),
        cvt(UUID::fromString),
        cvt({ LocalDate.parse(it, DateTimeFormatter.ISO_DATE) }),
        cvt({ LocalTime.parse(it, DateTimeFormatter.ISO_LOCAL_TIME) }),
        cvt({ OffsetTime.parse(it, DateTimeFormatter.ISO_OFFSET_TIME) }),
        cvt({ LocalDateTime.parse(it, localDateTimeFormatter) }),
        cvt({ OffsetDateTime.parse(it, offsetDateTimeFormatter) }),
        cvt({ ZonedDateTime.parse(it, zonedDateTimeFormatter) }),
        cvt({ it.toLongOrNull()?.let(Instant::ofEpochMilli) ?: Instant.from(offsetDateTimeFormatter.parse(it)) }),
    )

    private val conversionsByType = run {
        conversions.associateBy { it.type.withNullability(false) } +
            conversions.associateBy { it.type.withNullability(true) }
    }.toMutableMap()

    private val nonNullTypes = streamTypes + conversionsByType.keys

    private val allowedTypes = nonNullTypes

    private val typeContentTypes = HashMap<KType, Map<String, MediaTypeEncodingModel>>()

    override suspend fun <T : Any> parseBody(clazz: KType, request: PipelineContext<Unit, ApplicationCall>): T {
        val objectMap = HashMap<String, Any>()
        request.context.receiveMultipart().forEachPart {
            val name = it.name
            if (name != null) {
                when (it) {
                    is PartData.FormItem -> {
                        objectMap[name] = it.value
                    }

                    is PartData.FileItem -> {
                        objectMap[name] = NamedFileInputStream(it.originalFileName, it.contentType, it.streamProvider())
                    }

                    is PartData.BinaryItem -> {
                        objectMap[name] = ContentInputStream(it.contentType, it.provider().asStream())
                    }

                    else -> {}
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        val ctor = (clazz.classifier as KClass<T>).primaryConstructor!!
        return ctor.callBy(ctor.parameters.associateWith {
            val raw = objectMap[it.openAPIName]
            if ((raw == null || (raw !is InputStream && streamTypes.contains(it.type))) && it.type.isMarkedNullable) {
                null
            } else {
                if (raw is InputStream) {
                    raw
                } else {
                    val cvt = conversionsByType[it.type] ?: error("Unhandled Type ${it.type}")
                    when (raw) {
                        null -> {
                            cvt.default ?: error("No provided value for field ${it.openAPIName}")
                        }

                        is String -> {
                            cvt.parser(raw)
                        }

                        else -> error("Unhandled Type ${it.type}")
                    }
                }
            }
        })
    }


    override fun <T> getMediaType(
        type: KType,
        apiGen: OpenAPIGen,
        provider: ModuleProvider<*>,
        example: T?,
        usage: ContentTypeProvider.Usage
    ): Map<ContentType, MediaTypeModel<T>>? {
        if (type == unitKType) return null
        val formContentType = type.jvmErasure.findAnnotation<FormDataRequest>()?.type?.contentType ?: return null
        val ctor = type.jvmErasure.primaryConstructor
        assertContent(ctor != null) { "${this::class.simpleName} requires a primary constructor" }

        ctor.parameters.filter { it.type.isValue }.forEach { parameter ->
            conversionsByType.computeIfAbsent(parameter.type) {
                createValueClassConverter(it)
            }
        }
        val parameterTypes = ctor.parameters.map { it.type.unwrappedType.withNullability(false) }

        when (usage) {
            ContentTypeProvider.Usage.PARSE -> {
                assertContent(allowedTypes.containsAll(parameterTypes)) {
                    "${this::class.simpleName} all constructor parameters must be of types: $allowedTypes"
                }
            }

            ContentTypeProvider.Usage.SERIALIZE -> error("MultiPart response not supported")
        }

        val contentTypes = synchronized(typeContentTypes) {
            typeContentTypes.getOrPut(type) {
                type.jvmErasure.memberProperties
                    .associateBy { it.name }
                    .mapValues { it.value.findAnnotation<PartEncoding>() }
                    .filterValues { it != null }
                    .mapValues { MediaTypeEncodingModel(it.value!!.contentType) }
            }.toMap()
        }

        val schemaBuilder = provider.ofType<FinalSchemaBuilderProviderModule>().last().provide(apiGen, provider)

        @Suppress("UNCHECKED_CAST")
        return mapOf(
            formContentType to MediaTypeModel(
                schemaBuilder.build(type) as SchemaModel<T>,
                example,
                null,
                contentTypes
            )
        )
    }
}
