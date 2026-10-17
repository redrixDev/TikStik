// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringsParityTest {

    private val english = load("values")
    private val spanish = load("values-es")

    @Test
    fun bothLanguagesHaveTheSameKeys() {
        assertThat(spanish.keys).containsExactlyElementsIn(english.keys - untranslatable("values"))
    }

    @Test
    fun placeholdersMatchInEveryText() {
        for ((key, texts) in spanish) {
            val expected = english.getValue(key).map(::placeholders).toSet()
            texts.forEach { assertWithMessage(key).that(placeholders(it)).isIn(expected) }
        }
    }

    @Test
    fun noTextIsBlank() {
        // Addresses (`*_url`) may be empty: the option that opens them is hidden until there is one.
        for ((key, texts) in english.entries + spanish.entries) {
            if (key.endsWith("_url")) continue
            texts.forEach { assertWithMessage(key).that(it).isNotEmpty() }
        }
    }

    private fun placeholders(text: String) = PLACEHOLDER.findAll(text).map { it.value }.sorted().toList()

    private fun untranslatable(folder: String) =
        elements(folder).filter { it.getAttribute("translatable") == "false" }.map(::key).toSet()

    // Key "type:name" → texts (one per string; one per quantity for plurals).
    private fun load(folder: String): Map<String, List<String>> = elements(folder).associate { element ->
        val texts = if (element.tagName == "plurals" || element.tagName == "string-array") {
            val items = element.getElementsByTagName("item")
            (0 until items.length).map { items.item(it).textContent }
        } else {
            listOf(element.textContent)
        }
        key(element) to texts
    }

    private fun key(element: Element) = "${element.tagName}:${element.getAttribute("name")}"

    private fun elements(folder: String): List<Element> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$folder/strings.xml")).documentElement
        return (0 until root.childNodes.length).map { root.childNodes.item(it) }.filterIsInstance<Element>()
    }

    private companion object {
        val PLACEHOLDER = Regex("""%(\d+\$)?[sd]""")
    }
}
