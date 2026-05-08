package pl.filebit.gymtracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

/**
 * Lekki Markdown renderer dla tekstu AI — bez zewnętrznej biblioteki.
 *
 * Obsługiwana składnia:
 * - **bold** → FontWeight.Bold
 * - *italic* → FontStyle.Italic
 * - `code` → monospace (basic)
 * - # Header (H1) → 18sp bold
 * - ## Header (H2) → 16sp bold
 * - ### Header (H3) → 14sp bold
 * - - bullet (lub *) → "•  text" indent
 * - 1. numbered → "1.  text" indent
 * - puste linie (\n\n) → odstęp między paragrafami
 *
 * Plus lepsza typografia: lineHeight 1.45× dla czytelności.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    baseFontSize: TextUnit = 15.sp
) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier = modifier) {
        blocks.forEachIndexed { idx, block ->
            BlockView(block, color = color, baseFontSize = baseFontSize)
            // Odstęp między blokami (oprócz ostatniego)
            if (idx < blocks.size - 1) {
                Text(
                    "",
                    style = TextStyle(fontSize = (baseFontSize.value * 0.4).sp)
                )
            }
        }
    }
}

@Composable
private fun BlockView(
    block: MarkdownBlock,
    color: Color,
    baseFontSize: TextUnit
) {
    val baseLineHeight = (baseFontSize.value * 1.45).sp
    when (block) {
        is MarkdownBlock.Header -> {
            val fontSize = when (block.level) {
                1 -> (baseFontSize.value + 4).sp
                2 -> (baseFontSize.value + 2).sp
                else -> (baseFontSize.value + 1).sp
            }
            Text(
                text = parseInlineFormatting(block.text),
                style = TextStyle(
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    lineHeight = (fontSize.value * 1.35).sp
                )
            )
        }
        is MarkdownBlock.Paragraph -> {
            Text(
                text = parseInlineFormatting(block.text),
                style = TextStyle(
                    fontSize = baseFontSize,
                    color = color,
                    lineHeight = baseLineHeight
                )
            )
        }
        is MarkdownBlock.BulletList -> {
            Column {
                block.items.forEach { item ->
                    Row(modifier = Modifier.padding(start = 4.dp)) {
                        Text(
                            "•  ",
                            style = TextStyle(
                                fontSize = baseFontSize,
                                color = color,
                                lineHeight = baseLineHeight
                            )
                        )
                        Text(
                            text = parseInlineFormatting(item),
                            style = TextStyle(
                                fontSize = baseFontSize,
                                color = color,
                                lineHeight = baseLineHeight
                            )
                        )
                    }
                }
            }
        }
        is MarkdownBlock.NumberedList -> {
            Column {
                block.items.forEachIndexed { i, item ->
                    Row(modifier = Modifier.padding(start = 4.dp)) {
                        Text(
                            "${i + 1}.  ",
                            style = TextStyle(
                                fontSize = baseFontSize,
                                color = color,
                                lineHeight = baseLineHeight,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = parseInlineFormatting(item),
                            style = TextStyle(
                                fontSize = baseFontSize,
                                color = color,
                                lineHeight = baseLineHeight
                            )
                        )
                    }
                }
            }
        }
    }
}

// ============= Parser =============

internal sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class BulletList(val items: List<String>) : MarkdownBlock()
    data class NumberedList(val items: List<String>) : MarkdownBlock()
}

internal fun parseBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.split("\n")
    var i = 0
    val paragraphLines = mutableListOf<String>()
    val bulletItems = mutableListOf<String>()
    val numberedItems = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphLines.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(paragraphLines.joinToString(" ")))
            paragraphLines.clear()
        }
    }
    fun flushBullets() {
        if (bulletItems.isNotEmpty()) {
            blocks.add(MarkdownBlock.BulletList(bulletItems.toList()))
            bulletItems.clear()
        }
    }
    fun flushNumbered() {
        if (numberedItems.isNotEmpty()) {
            blocks.add(MarkdownBlock.NumberedList(numberedItems.toList()))
            numberedItems.clear()
        }
    }
    fun flushAll() { flushParagraph(); flushBullets(); flushNumbered() }

    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trim()

        // Pusta linia → flush wszystkich aktywnych bloków
        if (line.isEmpty()) {
            flushAll()
            i++
            continue
        }

        // Headers
        val headerMatch = Regex("^(#{1,3})\\s+(.+)$").find(line)
        if (headerMatch != null) {
            flushAll()
            val level = headerMatch.groupValues[1].length
            blocks.add(MarkdownBlock.Header(level, headerMatch.groupValues[2]))
            i++
            continue
        }

        // Bullet list (- item lub * item)
        val bulletMatch = Regex("^[-*]\\s+(.+)$").find(line)
        if (bulletMatch != null) {
            flushParagraph()
            flushNumbered()
            bulletItems.add(bulletMatch.groupValues[1])
            i++
            continue
        }

        // Numbered list (1. item)
        val numberedMatch = Regex("^\\d+\\.\\s+(.+)$").find(line)
        if (numberedMatch != null) {
            flushParagraph()
            flushBullets()
            numberedItems.add(numberedMatch.groupValues[1])
            i++
            continue
        }

        // Zwykły tekst → akumuluj jako paragraph
        flushBullets()
        flushNumbered()
        paragraphLines.add(line)
        i++
    }
    flushAll()
    return blocks
}

/**
 * Inline formatting: **bold**, *italic*, `code`.
 * Używa AnnotatedString z SpanStyle.
 */
internal fun parseInlineFormatting(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        // **bold**
        if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
            val end = text.indexOf("**", i + 2)
            if (end != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(text.substring(i + 2, end))
                }
                i = end + 2
                continue
            }
        }
        // *italic* (musi być po **)
        if (text[i] == '*') {
            val end = text.indexOf('*', i + 1)
            if (end != -1 && (end + 1 >= text.length || text[end + 1] != '*')) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }
        // `code`
        if (text[i] == '`') {
            val end = text.indexOf('`', i + 1)
            if (end != -1) {
                withStyle(SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }
        append(text[i])
        i++
    }
}
