package com.mmnuig.shoplistcc.xlsx

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.ceil

/**
 * Minimal .xlsx reader/writer for the shopping list format:
 * bold cell = category header, non-bold cell = item, strikethrough = crossed
 * off. Categories stack vertically within each column; columns are read
 * left-to-right. Hand-rolled (zip + XML) to avoid heavyweight spreadsheet
 * libraries on Android.
 */
object Xlsx {

    private data class Cell(
        val col: Int,
        val row: Int,
        val text: String,
        val bold: Boolean,
        val strike: Boolean
    )

    // ---------- Reading ----------

    fun read(input: InputStream): List<Pair<String, List<Pair<String, Boolean>>>> {
        val parts = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) parts[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        val styles = parts["xl/styles.xml"]?.let { parseStyles(it) } ?: emptyList()
        val shared = parts["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheetPart = parts.keys
            .filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
            .minOrNull() ?: throw IllegalArgumentException("No worksheet found in file")
        val cells = parseSheet(parts.getValue(sheetPart), shared, styles)

        val result = mutableListOf<Pair<String, MutableList<Pair<String, Boolean>>>>()
        for ((_, columnCells) in cells.groupBy { it.col }.toSortedMap()) {
            for (cell in columnCells.sortedBy { it.row }) {
                val text = cell.text.trim()
                if (text.isEmpty()) continue
                if (cell.bold) {
                    result.add(text to mutableListOf())
                } else {
                    if (result.isEmpty()) result.add("Imported" to mutableListOf())
                    result.last().second.add(text to cell.strike)
                }
            }
        }
        return result
    }

    private fun newParser(bytes: ByteArray): XmlPullParser =
        XmlPullParserFactory.newInstance().newPullParser()
            .apply { setInput(bytes.inputStream(), null) }

    private fun flagAttr(parser: XmlPullParser): Boolean {
        val v = parser.getAttributeValue(null, "val")
        return v == null || (v != "0" && v != "false")
    }

    /** Returns, for each cellXfs style index, (bold, strike) of its font. */
    private fun parseStyles(bytes: ByteArray): List<Pair<Boolean, Boolean>> {
        val parser = newParser(bytes)
        val fonts = mutableListOf<Pair<Boolean, Boolean>>()
        val xfFontIds = mutableListOf<Int>()
        var inFonts = false
        var inCellXfs = false
        var inFont = false
        var bold = false
        var strike = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "fonts" -> inFonts = true
                    "font" -> if (inFonts) {
                        inFont = true; bold = false; strike = false
                    }
                    "b" -> if (inFont) bold = flagAttr(parser)
                    "strike" -> if (inFont) strike = flagAttr(parser)
                    "cellXfs" -> inCellXfs = true
                    "xf" -> if (inCellXfs) {
                        xfFontIds.add(
                            parser.getAttributeValue(null, "fontId")?.toIntOrNull() ?: 0
                        )
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "fonts" -> inFonts = false
                    "font" -> if (inFonts) {
                        fonts.add(bold to strike); inFont = false
                    }
                    "cellXfs" -> inCellXfs = false
                }
            }
            event = parser.next()
        }
        return xfFontIds.map { fonts.getOrElse(it) { false to false } }
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val parser = newParser(bytes)
        val strings = mutableListOf<String>()
        val sb = StringBuilder()
        var inT = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> sb.setLength(0)
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> if (inT) sb.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inT = false
                    "si" -> strings.add(sb.toString())
                }
            }
            event = parser.next()
        }
        return strings
    }

    private fun parseSheet(
        bytes: ByteArray,
        shared: List<String>,
        styles: List<Pair<Boolean, Boolean>>
    ): List<Cell> {
        val parser = newParser(bytes)
        val cells = mutableListOf<Cell>()
        var ref = ""
        var styleIdx = 0
        var type = ""
        val sb = StringBuilder()
        var inValue = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "c" -> {
                        ref = parser.getAttributeValue(null, "r") ?: ""
                        styleIdx = parser.getAttributeValue(null, "s")?.toIntOrNull() ?: 0
                        type = parser.getAttributeValue(null, "t") ?: ""
                        sb.setLength(0)
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) sb.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val raw = sb.toString()
                        val text = if (type == "s") {
                            raw.trim().toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                        } else raw
                        if (text.isNotBlank() && ref.isNotEmpty()) {
                            val col = ref.takeWhile { it.isLetter() }
                                .fold(0) { acc, ch -> acc * 26 + (ch.uppercaseChar() - 'A' + 1) } - 1
                            val row = ref.dropWhile { it.isLetter() }.toIntOrNull() ?: 0
                            val (bold, strike) = styles.getOrElse(styleIdx) { false to false }
                            cells.add(Cell(col, row, text, bold, strike))
                        }
                    }
                }
            }
            event = parser.next()
        }
        return cells
    }

    // ---------- Writing ----------

    fun write(output: OutputStream, categories: List<Pair<String, List<Pair<String, Boolean>>>>) {
        // Distribute categories across up to 4 columns, keeping order and
        // roughly balancing column heights, like the reference files.
        val totalRows = categories.sumOf { it.second.size + 1 }
        val cols = minOf(4, ((categories.size + 1) / 2).coerceAtLeast(1))
        val target = ceil(totalRows / cols.toDouble()).toInt()
        val columns = MutableList(cols) { mutableListOf<Pair<String, List<Pair<String, Boolean>>>>() }
        var ci = 0
        var height = 0
        for (cat in categories) {
            if (height >= target && ci < cols - 1) {
                ci++; height = 0
            }
            columns[ci].add(cat)
            height += cat.second.size + 1
        }

        // grid[row][col] = text to styleIndex (1 = bold, 2 = strike, 0 = plain)
        val grid = HashMap<Int, HashMap<Int, Pair<String, Int>>>()
        columns.forEachIndexed { col, catList ->
            var row = 1
            for ((name, items) in catList) {
                grid.getOrPut(row) { HashMap() }[col] = name to 1
                row++
                for ((itemName, crossed) in items) {
                    grid.getOrPut(row) { HashMap() }[col] = itemName to if (crossed) 2 else 0
                    row++
                }
            }
        }

        val sheetXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            append("<sheetData>")
            for (row in grid.keys.sorted()) {
                append("<row r=\"$row\">")
                for (col in grid.getValue(row).keys.sorted()) {
                    val (text, style) = grid.getValue(row).getValue(col)
                    append("<c r=\"${colLetter(col)}$row\" s=\"$style\" t=\"inlineStr\">")
                    append("<is><t xml:space=\"preserve\">${escapeXml(text)}</t></is></c>")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

        val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="3">
<font><sz val="11"/><name val="Arial"/></font>
<font><b/><sz val="11"/><name val="Arial"/></font>
<font><strike/><sz val="11"/><name val="Arial"/></font>
</fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="3">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""

        val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Shop" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

        val workbookRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

        val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

        ZipOutputStream(output).use { zip ->
            fun part(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            part("[Content_Types].xml", contentTypes)
            part("_rels/.rels", rootRels)
            part("xl/workbook.xml", workbookXml)
            part("xl/_rels/workbook.xml.rels", workbookRels)
            part("xl/styles.xml", stylesXml)
            part("xl/worksheets/sheet1.xml", sheetXml)
        }
    }

    private fun colLetter(col: Int): String {
        var c = col
        val sb = StringBuilder()
        while (c >= 0) {
            sb.insert(0, ('A' + c % 26))
            c = c / 26 - 1
        }
        return sb.toString()
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
