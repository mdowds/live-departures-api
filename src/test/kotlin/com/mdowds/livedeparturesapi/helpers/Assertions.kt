package com.mdowds.livedeparturesapi.helpers

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.assertj.core.api.AbstractAssert
import java.lang.ClassCastException

fun assertThat(actual: JsonObject?): JsonObjectAssert = JsonObjectAssert(actual)
fun assertThat(actual: JsonArray): JsonArrayAssert = JsonArrayAssert(actual)

class JsonObjectAssert(actual: JsonObject?):
        AbstractAssert<JsonObjectAssert, JsonObject?>(actual, JsonObjectAssert::class.java) {

    fun hasProperty(expected: Pair<String, Any>): JsonObjectAssert {
        isNotNull
        val (key, expectedValue) = expected
        val actualValue = actual!!.get(key)

        when(expectedValue) {
            is String -> checkStringValue(key, expectedValue, actualValue)
            is Collection<*> -> checkArrayValue(key, expectedValue, actualValue)
        }

        return this
    }

    fun hasChildren(vararg expected: Pair<String, Any>): JsonObjectAssert {
        isNotNull
        expected.forEach {
            hasProperty(it)
        }
        return this
    }

    private fun checkArrayValue(key: String, expectedValues: Collection<*>, actualValue: JsonElement) {

        if(!actualValue.isJsonArray)
            failWithPropertyMessage(key, expectedValues, actualValue)

        assertThat(actualValue.asJsonArray).hasStringMembers(expectedValues)
    }

    private fun checkStringValue(key: String, expectedValue: String, actualValue: JsonElement) {

        try {
            if(actualValue.asString != expectedValue)
                failWithPropertyMessage(key, expectedValue, actualValue)
        } catch (e: ClassCastException) {
            failWithPropertyMessage(key, expectedValue, actualValue)
        }
    }

    private fun failWithPropertyMessage(key: String, expected: Any, actual: Any) =
            failWithMessage("Expected property <%s> to be <%s> but was <%s>", key, expected, actual)
}

class JsonArrayAssert(actual: JsonArray):
        AbstractAssert<JsonArrayAssert, JsonArray>(actual, JsonArrayAssert::class.java) {

    fun hasStringMembers(expected: Collection<*>): JsonArrayAssert {
        val actualValues = mutableListOf<String>().apply {
            actual.forEach { add(it.asString) }
        }

        if(actualValues != expected)
            failWithMessage("Expected array to have members <%s>  but was <%s>", expected, actual)

        return this
    }
}
