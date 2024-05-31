package org.wordpress.aztec.formatting

import android.graphics.Typeface
import android.text.Spannable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.util.Patterns
import androidx.annotation.ColorRes
import org.wordpress.android.util.AppLog
import org.wordpress.aztec.AztecAttributes
import org.wordpress.aztec.AztecPart
import org.wordpress.aztec.AztecText
import org.wordpress.aztec.AztecTextFormat
import org.wordpress.aztec.Constants
import org.wordpress.aztec.ITextFormat
import org.wordpress.aztec.R
import org.wordpress.aztec.spans.AztecBackgroundColorSpan
import org.wordpress.aztec.spans.AztecCodeSpan
import org.wordpress.aztec.spans.AztecMentionSpan
import org.wordpress.aztec.spans.AztecStrikethroughSpan
import org.wordpress.aztec.spans.AztecStyleBoldSpan
import org.wordpress.aztec.spans.AztecStyleCiteSpan
import org.wordpress.aztec.spans.AztecStyleEmphasisSpan
import org.wordpress.aztec.spans.AztecStyleItalicSpan
import org.wordpress.aztec.spans.AztecStyleSpan
import org.wordpress.aztec.spans.AztecStyleStrongSpan
import org.wordpress.aztec.spans.AztecURLSpan
import org.wordpress.aztec.spans.AztecUnderlineSpan
import org.wordpress.aztec.spans.HighlightSpan
import org.wordpress.aztec.spans.IAztecExclusiveInlineSpan
import org.wordpress.aztec.spans.IAztecInlineSpan
import org.wordpress.aztec.spans.MarkSpan
import org.wordpress.aztec.watchers.TextChangedEvent
import org.wordpress.aztec.watchers.TextDeleter

/**
 * <b>Important</b> - use [applySpan] to add new spans to the editor. This method will
 * make sure any attributes belonging to the span are processed.
 */
class InlineFormatter(editor: AztecText, val codeStyle: CodeStyle, private val highlightStyle: HighlightStyle) : AztecFormatter(editor) {

    var backgroundSpanColor: Int? = null
    var markStyleColor: String? = null

    data class CodeStyle(val codeBackground: Int, val codeBackgroundAlpha: Float, val codeColor: Int)
    data class HighlightStyle(@ColorRes val color: Int)

    fun toggle(textFormat: ITextFormat) {
        if (!containsInlineStyle(textFormat)) {
            val inlineSpan = makeInlineSpan(textFormat)
            if (inlineSpan is IAztecExclusiveInlineSpan) {
                // If text format is exclusive, remove all the inclusive text formats already applied
                removeAllInclusiveFormats()
            } else {
                // If text format is inclusive, remove all the exclusive text formats already applied
                removeAllExclusiveFormats()
            }
            applyInlineStyle(textFormat)
        } else {
            removeInlineStyle(textFormat)
        }
    }

    private fun removeAllInclusiveFormats() {
        editableText.getSpans(selectionStart, selectionEnd, IAztecInlineSpan::class.java).filter {
            it !is IAztecExclusiveInlineSpan
        }.forEach { removeInlineStyle(it) }
    }

    /**
     * Removes all formats in the list but if none found, applies the first one
     */
    fun toggleAny(textFormats: Set<ITextFormat>) {
        if (!textFormats
                .filter { containsInlineStyle(it) }
                .fold(false) { _, containedTextFormat -> removeInlineStyle(containedTextFormat); true }) {
            removeAllExclusiveFormats()
            applyInlineStyle(textFormats.first())
        }
    }

    private fun removeAllExclusiveFormats() {
        editableText.getSpans(selectionStart, selectionEnd, IAztecExclusiveInlineSpan::class.java)
                .forEach { removeInlineStyle(it) }
    }

    fun handleInlineStyling(textChangedEvent: TextChangedEvent) {
        if (textChangedEvent.isEndOfBufferMarker()) return



        // Auto link

        Patterns.WEB_URL.toRegex().findAll(editableText).forEach {
            val spans = editableText.getSpans(it.range.first, it.range.last, AztecURLSpan::class.java)
            Log.v("DKM", it.range.first.toString() + " " + it.range.last);
            if(spans == null || spans.isEmpty()) {
                // nec ahgen to link

                applyInlineStyle(AztecTextFormat.FORMAT_BOLD, it.range.first, it.range.last)
            }
        }


        // because we use SPAN_INCLUSIVE_INCLUSIVE for inline styles
        // we need to make sure unselected styles are not applied
        clearInlineStyles(textChangedEvent.inputStart, textChangedEvent.inputEnd, textChangedEvent.isNewLine())

//        if (textChangedEvent.isNewLine()) return

        // Handle mention
        val urlSpans = editableText.getSpans(0, editableText.length, AztecURLSpan::class.java);
        urlSpans.forEach {
            val s = editableText.getSpanStart(it);
            val e = editableText.getSpanEnd(it);
            if(!it.attributes.getValue("href").equals(editableText.subSequence(s,e))){
//                editableText.removeSpan(it);
                // example how to delete ...
                TextDeleter.mark(textChangedEvent.text as Spannable, s,e);
            }
        }


        if (editor.formattingIsApplied()) {
            for (item in editor.selectedStyles) {
                when (item) {
                    AztecTextFormat.FORMAT_BOLD,
                    AztecTextFormat.FORMAT_STRONG,
                    AztecTextFormat.FORMAT_ITALIC,
                    AztecTextFormat.FORMAT_EMPHASIS,
                    AztecTextFormat.FORMAT_CITE,
                    AztecTextFormat.FORMAT_STRIKETHROUGH,
                    AztecTextFormat.FORMAT_BACKGROUND,
                    AztecTextFormat.FORMAT_UNDERLINE,
                    AztecTextFormat.FORMAT_CODE -> {
                        applyInlineStyle(item, textChangedEvent.inputStart, textChangedEvent.inputEnd)
                    }
                    AztecTextFormat.FORMAT_HIGHLIGHT -> {
                        applyInlineStyle(item, textChangedEvent.inputStart, textChangedEvent.inputEnd)
                    }
                    AztecTextFormat.FORMAT_MARK -> {
                        applyInlineStyle(item, textChangedEvent.inputStart, textChangedEvent.inputEnd)
                        applyAfterMarkInlineStyle(textChangedEvent.inputStart, textChangedEvent.inputEnd)
                    }
                    else -> {
                        // do nothing
                    }
                }
            }
        }

        editor.setFormattingChangesApplied()
    }

    private fun clearInlineStyles(start: Int, end: Int, ignoreSelectedStyles: Boolean) {
        val newStart = if (start > end) end else start
        // if there is END_OF_BUFFER_MARKER at the end of or range, extend the range to include it

        // Clear Mark formatting styles
        if (!editor.selectedStyles.contains(AztecTextFormat.FORMAT_MARK) && start >= 1 && end > 1 ) {
            val previousMarkSpan = editableText.getSpans(start - 1, start, MarkSpan::class.java)
            val markSpan = editableText.getSpans(start, end, MarkSpan::class.java)
            if (markSpan.isNotEmpty() || previousMarkSpan.isNotEmpty()) {
                removeInlineCssStyle(start, end)
                return
            }
        }

        // remove lingering empty spans when removing characters
        if (start > end) {
            editableText.getSpans(newStart, end, IAztecInlineSpan::class.java)
                    .filter { editableText.getSpanStart(it) == editableText.getSpanEnd(it) }
                    .forEach { editableText.removeSpan(it) }
            return
        }

        editableText.getSpans(newStart, end, IAztecInlineSpan::class.java).forEach {
            if (!editor.selectedStyles.contains(spanToTextFormat(it)) || ignoreSelectedStyles || (newStart == 0 && end == 0) ||
                    (newStart > end && editableText.length > end && editableText[end] == '\n')) {
                removeInlineStyle(it, newStart, end)
            }
        }
    }

    private fun applyInlineStyle(textFormat: ITextFormat, start: Int = selectionStart, end: Int = selectionEnd, attrs: AztecAttributes = AztecAttributes()) {
        val spanToApply = makeInlineSpan(textFormat)
        spanToApply.attributes = attrs

        if (start >= end) {
            return
        }

        if (textFormat == AztecTextFormat.FORMAT_BACKGROUND) {
            //clear previous background before applying a new one to avoid problems when using multiple bg colors
            removeBackgroundInSelection(selectionStart, selectionEnd)
        }

        var precedingSpan: IAztecInlineSpan? = null
        var followingSpan: IAztecInlineSpan? = null

        if (start >= 1) {
            val previousSpans = editableText.getSpans(start - 1, start, IAztecInlineSpan::class.java)
            previousSpans.forEach {
                if (isSameInlineSpanType(it, spanToApply)) {
                    precedingSpan = it
                    return@forEach
                }
            }

            if (precedingSpan != null) {
                val spanStart = editableText.getSpanStart(precedingSpan)
                val spanEnd = editableText.getSpanEnd(precedingSpan)

                if (spanEnd > start) {
                    // ensure css style is applied
                    (precedingSpan as IAztecInlineSpan).applyInlineStyleAttributes(editableText, start, end)
                    return // we are adding text inside span - no need to do anything special
                } else {
                    applySpan(precedingSpan as IAztecInlineSpan, spanStart, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        if (editor.length() > end) {
            val nextSpans = editableText.getSpans(end, end + 1, IAztecInlineSpan::class.java)
            nextSpans.forEach {
                if (isSameInlineSpanType(it, spanToApply)) {
                    followingSpan = it
                    return@forEach
                }
            }

            if (followingSpan != null) {
                val spanEnd = editableText.getSpanEnd(followingSpan)
                applySpan(followingSpan as IAztecInlineSpan, start, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                editableText.setSpan(followingSpan, start, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        if (precedingSpan == null && followingSpan == null) {
            var existingSpanOfSameStyle: IAztecInlineSpan? = null

            val spans = editableText.getSpans(start, end, IAztecInlineSpan::class.java)
            spans.forEach {
                if (isSameInlineSpanType(it, spanToApply)) {
                    existingSpanOfSameStyle = it
                    return@forEach
                }
            }

            // if we already have same span within selection - reuse its attributes
            if (existingSpanOfSameStyle != null) {
                editableText.removeSpan(existingSpanOfSameStyle)
                spanToApply.attributes = attrs
            }

            applySpan(spanToApply, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        joinStyleSpans(start, end)
    }

    private fun removeBackgroundInSelection(selStart: Int, selEnd: Int) {
        val spans = editableText.getSpans(selStart, selEnd, AztecBackgroundColorSpan::class.java)
        spans.forEach { span ->
            if (span != null) {
                val currentSpanStart = editableText.getSpanStart(span)
                val currentSpanEnd = editableText.getSpanEnd(span)
                val color = span.backgroundColor
                editableText.removeSpan(span)
                if (selEnd < currentSpanEnd) {
                    editableText.setSpan(
                        AztecBackgroundColorSpan(color),
                        selEnd,
                        currentSpanEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                if (selStart > currentSpanStart) {
                    editableText.setSpan(
                        AztecBackgroundColorSpan(color),
                        currentSpanStart,
                        selStart,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }
    }

    private fun applyAfterMarkInlineStyle(start: Int = selectionStart, end: Int = selectionEnd) {
        // If there's no new mark style color to update, it skips applying the style updates.
        if (markStyleColor == null) {
            return
        }

        val spans = editableText.getSpans(start, end, MarkSpan::class.java)
        spans.forEach { span ->
            if (span != null) {
                val color = span.getTextColor()
                val currentSpanStart = editableText.getSpanStart(span)
                val currentSpanEnd = editableText.getSpanEnd(span)

                if (end < currentSpanEnd) {
                    markStyleColor = null
                    return
                }

                if (!color.equals(markStyleColor, ignoreCase = true)) {
                    editableText.removeSpan(span)
                    editableText.setSpan(
                        MarkSpan(AztecAttributes(), color),
                        currentSpanStart,
                        start,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    editableText.setSpan(
                        MarkSpan(AztecAttributes(), markStyleColor),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }
    }

    private fun applySpan(span: IAztecInlineSpan, start: Int, end: Int, type: Int) {
        if (start > end || start < 0 || end > editableText.length) {
            // If an external logger is available log the error there.
            val extLogger = editor.externalLogger
            if (extLogger != null) {
                extLogger.log("InlineFormatter.applySpan - setSpan has end before start." +
                        " Start:" + start + " End:" + end)
                extLogger.log("Logging the whole content" + editor.toPlainHtml())
            }
            // Now log in the default log
            AppLog.w(AppLog.T.EDITOR, "InlineFormatter.applySpan - setSpan has end before start." +
                    " Start:" + start + " End:" + end)
            AppLog.w(AppLog.T.EDITOR, "Logging the whole content" + editor.toPlainHtml())
            return
        }
        editableText.setSpan(span, start, end, type)
        span.applyInlineStyleAttributes(editableText, start, end)
    }

    fun spanToTextFormat(span: IAztecInlineSpan): ITextFormat? {
        return when (span::class.java) {
            AztecStyleBoldSpan::class.java -> AztecTextFormat.FORMAT_BOLD
            AztecStyleStrongSpan::class.java -> AztecTextFormat.FORMAT_STRONG
            AztecStyleItalicSpan::class.java -> AztecTextFormat.FORMAT_ITALIC
            AztecStyleEmphasisSpan::class.java -> AztecTextFormat.FORMAT_EMPHASIS
            AztecStyleCiteSpan::class.java -> AztecTextFormat.FORMAT_CITE
            AztecStrikethroughSpan::class.java -> AztecTextFormat.FORMAT_STRIKETHROUGH
            AztecUnderlineSpan::class.java -> AztecTextFormat.FORMAT_UNDERLINE
            AztecCodeSpan::class.java -> AztecTextFormat.FORMAT_CODE
            AztecBackgroundColorSpan::class.java -> return AztecTextFormat.FORMAT_BACKGROUND
            MarkSpan::class.java -> AztecTextFormat.FORMAT_MARK
            HighlightSpan::class.java -> AztecTextFormat.FORMAT_HIGHLIGHT
            else -> null
        }
    }

    fun removeInlineStyle(spanToRemove: IAztecInlineSpan, start: Int = selectionStart, end: Int = selectionEnd) {
        val textFormat = spanToTextFormat(spanToRemove) ?: return

        val spans = editableText.getSpans(start, end, IAztecInlineSpan::class.java)
        val list = ArrayList<AztecPart>()

        spans.forEach {
            if (isSameInlineSpanType(it, spanToRemove)) {
                list.add(AztecPart(editableText.getSpanStart(it), editableText.getSpanEnd(it), it.attributes))
                editableText.removeSpan(it)
            }
        }

        // remove the CSS style span
        removeInlineCssStyle()

        list.forEach {
            if (it.isValid) {
                if (it.start < start) {
                    applyInlineStyle(textFormat, it.start, start, it.attr)
                }
                if (it.end > end) {
                    applyInlineStyle(textFormat, end, it.end, it.attr)
                }
            }
        }

        joinStyleSpans(start, end)
    }

    private fun removeInlineCssStyle(start: Int = selectionStart, end: Int = selectionEnd) {
        editableText.getSpans(start, end, ForegroundColorSpan::class.java).forEach {
            editableText.removeSpan(it)
        }
        editableText.getSpans(start, end, BackgroundColorSpan::class.java).forEach {
            editableText.removeSpan(it)
        }
    }

    fun removeInlineStyle(textFormat: ITextFormat, start: Int = selectionStart, end: Int = selectionEnd) {
        removeInlineStyle(makeInlineSpan(textFormat), start, end)
    }

    fun isSameInlineSpanType(firstSpan: IAztecInlineSpan, secondSpan: IAztecInlineSpan): Boolean {
        // special check for StyleSpans
        if (firstSpan is StyleSpan && secondSpan is StyleSpan) {
            return firstSpan.style == secondSpan.style
        }
        // special check for BackgroundSpan
        if (firstSpan is AztecBackgroundColorSpan && secondSpan is AztecBackgroundColorSpan) {
            return firstSpan.backgroundColor == secondSpan.backgroundColor
        }

        return firstSpan.javaClass == secondSpan.javaClass
    }

    // TODO: Check if there is more efficient way to tidy spans
    fun joinStyleSpans(start: Int, end: Int) {
        // joins spans on the left
        if (start > 1) {
            val spansInSelection = editableText.getSpans(start, end, IAztecInlineSpan::class.java)

            val spansBeforeSelection = editableText.getSpans(start - 1, start, IAztecInlineSpan::class.java)
            spansInSelection.forEach { innerSpan ->
                val inSelectionSpanEnd = editableText.getSpanEnd(innerSpan)
                val inSelectionSpanStart = editableText.getSpanStart(innerSpan)
                if (inSelectionSpanEnd == -1 || inSelectionSpanStart == -1) return@forEach
                spansBeforeSelection.forEach { outerSpan ->
                    val outerSpanStart = editableText.getSpanStart(outerSpan)

                    if (isSameInlineSpanType(innerSpan, outerSpan) && inSelectionSpanEnd >= outerSpanStart) {
                        editableText.removeSpan(outerSpan)
                        applySpan(innerSpan, outerSpanStart, inSelectionSpanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }

        // joins spans on the right
        if (editor.length() > end) {
            val spansInSelection = editableText.getSpans(start, end, IAztecInlineSpan::class.java)
            val spansAfterSelection = editableText.getSpans(end, end + 1, IAztecInlineSpan::class.java)
            spansInSelection.forEach { innerSpan ->
                val inSelectionSpanEnd = editableText.getSpanEnd(innerSpan)
                val inSelectionSpanStart = editableText.getSpanStart(innerSpan)
                if (inSelectionSpanEnd == -1 || inSelectionSpanStart == -1) return@forEach
                spansAfterSelection.forEach { outerSpan ->
                    val outerSpanEnd = editableText.getSpanEnd(outerSpan)
                    if (isSameInlineSpanType(innerSpan, outerSpan) && outerSpanEnd >= inSelectionSpanStart) {
                        editableText.removeSpan(outerSpan)
                        applySpan(innerSpan, inSelectionSpanStart, outerSpanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }

        // joins spans withing selected text
        val spansInSelection = editableText.getSpans(start, end, IAztecInlineSpan::class.java)
        val spansToUse = editableText.getSpans(start, end, IAztecInlineSpan::class.java)

        spansInSelection.forEach { appliedSpan ->

            val spanStart = editableText.getSpanStart(appliedSpan)
            val spanEnd = editableText.getSpanEnd(appliedSpan)

            var neighbourSpan: IAztecInlineSpan? = null

            spansToUse.forEach inner@ {
                val aSpanStart = editableText.getSpanStart(it)
                val aSpanEnd = editableText.getSpanEnd(it)
                if (isSameInlineSpanType(it, appliedSpan)) {
                    if (aSpanStart == spanEnd || aSpanEnd == spanStart) {
                        neighbourSpan = it
                        return@inner
                    }
                }
            }

            if (neighbourSpan != null) {
                val neighbourSpanStart = editableText.getSpanStart(neighbourSpan)
                val neighbourSpanEnd = editableText.getSpanEnd(neighbourSpan)

                if (neighbourSpanStart == -1 || neighbourSpanEnd == -1)
                    return@forEach

                // span we want to join is on the left
                if (spanStart == neighbourSpanEnd) {
                    applySpan(appliedSpan, neighbourSpanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else if (spanEnd == neighbourSpanStart) {
                    applySpan(appliedSpan, spanStart, neighbourSpanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                editableText.removeSpan(neighbourSpan)
            }
        }
    }

    fun makeInlineSpan(textFormat: ITextFormat): IAztecInlineSpan {
        return when (textFormat) {
            AztecTextFormat.FORMAT_BOLD -> AztecStyleBoldSpan()
            AztecTextFormat.FORMAT_STRONG -> AztecStyleStrongSpan()
            AztecTextFormat.FORMAT_ITALIC -> AztecStyleItalicSpan()
            AztecTextFormat.FORMAT_EMPHASIS -> AztecStyleEmphasisSpan()
            AztecTextFormat.FORMAT_CITE -> AztecStyleCiteSpan()
            AztecTextFormat.FORMAT_STRIKETHROUGH -> AztecStrikethroughSpan()
            AztecTextFormat.FORMAT_UNDERLINE -> AztecUnderlineSpan()
            AztecTextFormat.FORMAT_CODE -> AztecCodeSpan(codeStyle)
            AztecTextFormat.FORMAT_BACKGROUND -> AztecBackgroundColorSpan(backgroundSpanColor ?: R.color.background)
            AztecTextFormat.FORMAT_HIGHLIGHT -> {
                HighlightSpan.create(context = editor.context, defaultStyle = highlightStyle)
            }
            AztecTextFormat.FORMAT_MARK -> MarkSpan(AztecAttributes(), markStyleColor)
            else -> AztecStyleSpan(Typeface.NORMAL)
        }
    }

    fun containsInlineStyle(textFormat: ITextFormat, start: Int = selectionStart, end: Int = selectionEnd): Boolean {
        val spanToCheck = makeInlineSpan(textFormat)

        if (start > end) {
            return false
        }

        if (start == end) {
            if (start - 1 < 0 || start + 1 > editableText.length) {
                return false
            } else {
                val before = editableText.getSpans(start - 1, start, IAztecInlineSpan::class.java)
                        .firstOrNull { isSameInlineSpanType(it, spanToCheck) }
                val after = editableText.getSpans(start, start + 1, IAztecInlineSpan::class.java)
                        .firstOrNull { isSameInlineSpanType(it, spanToCheck) }
                return before != null && after != null && isSameInlineSpanType(before, after)
            }
        } else {
            val builder = StringBuilder()

            // Make sure no duplicate characters be added
            for (i in start until end) {
                val spans = editableText.getSpans(i, i + 1, IAztecInlineSpan::class.java)

                for (span in spans) {
                    if (isSameInlineSpanType(span, spanToCheck)) {
                        builder.append(editableText.subSequence(i, i + 1).toString())
                        return true;
//                        break
                    }
                }
            }

            val originalText = editableText.subSequence(start, end).replace("\n".toRegex(), "")
            val textOfCombinedSpans = builder.toString().replace("\n".toRegex(), "")

            return originalText.isNotEmpty() && originalText == textOfCombinedSpans

        }
    }

    fun tryRemoveLeadingInlineStyle() {
        val selectionStart = editor.selectionStart
        val selectionEnd = editor.selectionEnd

        if (selectionStart == 1 && selectionEnd == selectionStart) {
            editableText.getSpans(0, 0, IAztecInlineSpan::class.java).forEach {
                if (editableText.getSpanEnd(it) == selectionEnd && editableText.getSpanEnd(it) == selectionStart) {
                    editableText.removeSpan(it)
                }
            }
        } else if (editor.length() == 1 && editor.text[0] == Constants.END_OF_BUFFER_MARKER) {
            editableText.getSpans(0, 1, IAztecInlineSpan::class.java).forEach {
                if (editableText.getSpanStart(it) == 1 && editableText.getSpanEnd(it) == 1) {
                    editableText.removeSpan(it)
                }
            }
        }
    }
}
