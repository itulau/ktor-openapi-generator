package com.papsign.ktor.openapigen

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.typeOf

val unitKType = getKType<Unit>()

internal inline fun <reified T> isNullable(): Boolean {
    return null is T
}

@PublishedApi
internal inline fun <reified T> getKType() = typeOf<T>()

internal fun KType.strip(nullable: Boolean = isMarkedNullable): KType {
    return jvmErasure.createType(arguments, nullable)
}

internal val KType.isValue get() = jvmErasure.isValue

// we also make a strip type to remove platform type
// for example, if we get a UUID! it will be converted to UUID
internal val KType.unwrappedType get() = (if (isValue) memberProperties.first().type else this).strip()

internal fun KType.deepStrip(nullable: Boolean = isMarkedNullable): KType {
    return jvmErasure.createType(arguments.map { it.copy(type = it.type?.deepStrip()) }, nullable)
}

internal fun KType.getIterableContentType(): KType? {
    return arguments.getOrNull(0)?.type
        ?: jvmErasure.supertypes.firstOrNull { it.isSubtypeOf(typeOf<Iterable<*>>()) }
            ?.arguments?.getOrNull(0)?.type
}

internal fun KType.getArrayContentType(): KType? {
    return arguments.getOrNull(0)?.type
        ?: jvmErasure.supertypes.firstOrNull { it.isSubtypeOf(typeOf<Array<*>>()) }
            ?.arguments?.getOrNull(0)?.type
}

internal fun KType.getMapKeyContentType(): KType? {
    return arguments.getOrNull(0)?.type
        ?: jvmErasure.supertypes.firstOrNull { it.isSubtypeOf(typeOf<Map<*, *>>()) }
            ?.arguments?.getOrNull(0)?.type
}

internal fun KType.getMapValueContentType(): KType? {
    return arguments.getOrNull(1)?.type
        ?: jvmErasure.supertypes.firstOrNull { it.isSubtypeOf(typeOf<Map<*, *>>()) }
            ?.arguments?.getOrNull(1)?.type
}

data class KTypeProperty(
    val name: String,
    val type: KType,
    val source: KProperty1<*, *>
)

val KType.memberProperties: List<KTypeProperty>
    get() {
        val typeParameters = jvmErasure.typeParameters.zip(arguments).associate { Pair(it.first.name, it.second.type) }
        return jvmErasure.memberProperties.map {
            val retType = it.returnType
            val properType = when (val classifier = retType.classifier) {
                is KTypeParameter -> typeParameters[classifier.name] ?: it.returnType
                else -> it.returnType
            }
            KTypeProperty(it.name, properType, it)
        }
    }

internal val KClass<*>.isInterface get() = java.isInterface
