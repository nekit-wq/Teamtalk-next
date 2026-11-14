package org.nekit.ttproplus.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;

import org.nekit.ttproplus.gui.ThemeHelper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownFormatter {

    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^\\)]+)\\)");
    private static final Pattern BOLD_ITALIC_PATTERN = Pattern.compile("(?<![\\*_])(\\*{3}|_{3})(?![\\*_])(.+?)(?<![\\*_])\\1(?![\\*_])");
    private static final Pattern BOLD_PATTERN = Pattern.compile("(?<![\\*_])(\\*{2}|_{2})(?![\\*_])(.+?)(?<![\\*_])\\1(?![\\*_])");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<![\\*_])(\\*|_)(?![\\*_])(.+?)(?<![\\*_])\\1(?![\\*_])");
    private static final Pattern STRIKE_PATTERN = Pattern.compile("(?<!~)(~~)(?!~)(.+?)(?<!~)\\1(?!~)");
    private static final Pattern CODE_PATTERN = Pattern.compile("(`)(.+?)\\1");
    private static final Pattern HR_PATTERN = Pattern.compile("^([*\\-_])\\s*(\\1\\s*){2,}$");
    private static final Pattern LIST_PATTERN = Pattern.compile("^(\\s*)([*\\-+]|\\d+\\.)\\s+(.*)$");
    private static final Pattern QUOTE_PREFIX_PATTERN = Pattern.compile("^>\\s*");

    public static CharSequence format(Context context, String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";

        int primaryColor = ThemeHelper.getPrimaryColor(context);
        int onSurfaceVariant = resolveColorAttr(context, "colorOnSurfaceVariant", 0xFF757575);
        int codeBg = resolveColorAttr(context, "colorSurfaceContainerHigh", 0x1F888888);

        SpannableStringBuilder sb = new SpannableStringBuilder();
        String[] lines = markdown.split("\r?\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append("\n");
                }
                continue;
            }

            // Horizontal rule
            if (HR_PATTERN.matcher(trimmed).matches()) {
                ensureSpacing(sb);
                int start = sb.length();
                sb.append("────────────────────────────────────────\n");
                sb.setSpan(new ForegroundColorSpan(onSurfaceVariant), start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new RelativeSizeSpan(0.75f), start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }

            // Headings (#, ##, ###, ####, etc.)
            if (trimmed.startsWith("#")) {
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') {
                    level++;
                }
                if (level < trimmed.length() && trimmed.charAt(level) == ' ') {
                    String headingText = trimmed.substring(level).trim();
                    ensureSpacing(sb);
                    int start = sb.length();
                    appendInlineFormatted(sb, headingText, primaryColor, onSurfaceVariant, codeBg);
                    sb.append("\n");

                    float sizeMultiplier;
                    switch (level) {
                        case 1:
                            sizeMultiplier = 1.35f;
                            break;
                        case 2:
                            sizeMultiplier = 1.25f;
                            break;
                        case 3:
                            sizeMultiplier = 1.15f;
                            break;
                        case 4:
                            sizeMultiplier = 1.05f;
                            break;
                        default:
                            sizeMultiplier = 1.0f;
                            break;
                    }

                    sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length() - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new RelativeSizeSpan(sizeMultiplier), start, sb.length() - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new ForegroundColorSpan(primaryColor), start, sb.length() - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    continue;
                }
            }

            // Blockquotes
            if (trimmed.startsWith(">")) {
                String quoteText = QUOTE_PREFIX_PATTERN.matcher(trimmed).replaceFirst("");
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append("\n");
                }
                int start = sb.length();
                sb.append("▎ ");
                int quoteStart = sb.length();
                appendInlineFormatted(sb, quoteText, primaryColor, onSurfaceVariant, codeBg);
                sb.append("\n");

                sb.setSpan(new ForegroundColorSpan(primaryColor), start, quoteStart, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(onSurfaceVariant), quoteStart, sb.length() - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new StyleSpan(Typeface.ITALIC), quoteStart, sb.length() - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }

            // Lists: unordered (*, -, +) or ordered (1., 2.)
            Matcher listMatcher = LIST_PATTERN.matcher(line);
            if (listMatcher.matches()) {
                String indent = listMatcher.group(1);
                String bullet = listMatcher.group(2);
                String content = listMatcher.group(3);

                int depth = indent != null ? indent.length() / 2 : 0;
                StringBuilder prefix = new StringBuilder();
                for (int d = 0; d < depth; d++) {
                    prefix.append("   ");
                }
                if (bullet != null && bullet.matches("\\d+\\.")) {
                    prefix.append(bullet).append(" ");
                } else {
                    prefix.append(depth == 0 ? " • " : "   ◦ ");
                }

                int start = sb.length();
                sb.append(prefix);
                int bulletEnd = sb.length();
                sb.setSpan(new ForegroundColorSpan(primaryColor), start, bulletEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new StyleSpan(Typeface.BOLD), start, bulletEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                appendInlineFormatted(sb, content, primaryColor, onSurfaceVariant, codeBg);
                sb.append("\n");
                continue;
            }

            // Regular paragraph line
            appendInlineFormatted(sb, line, primaryColor, onSurfaceVariant, codeBg);
            sb.append("\n");
        }

        // Trim trailing newlines
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.delete(sb.length() - 1, sb.length());
        }

        return sb;
    }

    private static void ensureSpacing(SpannableStringBuilder sb) {
        if (sb.length() == 0) return;
        if (sb.charAt(sb.length() - 1) != '\n') {
            sb.append("\n\n");
        } else if (sb.length() > 1 && sb.charAt(sb.length() - 2) != '\n') {
            sb.append("\n");
        }
    }

    private static void appendInlineFormatted(SpannableStringBuilder sb, String text, int primaryColor, int onSurfaceVariant, int codeBg) {
        if (text == null || text.isEmpty()) return;

        int current = 0;
        int len = text.length();

        while (current < len) {
            int nextToken = -1;
            int tokenType = 0; // 1: link, 2: bold-italic, 3: bold, 4: italic, 5: strike, 6: code
            Matcher matchedMatcher = null;

            Matcher mLink = LINK_PATTERN.matcher(text);
            if (mLink.find(current)) {
                nextToken = mLink.start();
                tokenType = 1;
                matchedMatcher = mLink;
            }

            Matcher mBoldItalic = BOLD_ITALIC_PATTERN.matcher(text);
            if (mBoldItalic.find(current) && (nextToken == -1 || mBoldItalic.start() < nextToken)) {
                nextToken = mBoldItalic.start();
                tokenType = 2;
                matchedMatcher = mBoldItalic;
            }

            Matcher mBold = BOLD_PATTERN.matcher(text);
            if (mBold.find(current) && (nextToken == -1 || mBold.start() < nextToken)) {
                nextToken = mBold.start();
                tokenType = 3;
                matchedMatcher = mBold;
            }

            Matcher mItalic = ITALIC_PATTERN.matcher(text);
            if (mItalic.find(current) && (nextToken == -1 || mItalic.start() < nextToken)) {
                nextToken = mItalic.start();
                tokenType = 4;
                matchedMatcher = mItalic;
            }

            Matcher mStrike = STRIKE_PATTERN.matcher(text);
            if (mStrike.find(current) && (nextToken == -1 || mStrike.start() < nextToken)) {
                nextToken = mStrike.start();
                tokenType = 5;
                matchedMatcher = mStrike;
            }

            Matcher mCode = CODE_PATTERN.matcher(text);
            if (mCode.find(current) && (nextToken == -1 || mCode.start() < nextToken)) {
                nextToken = mCode.start();
                tokenType = 6;
                matchedMatcher = mCode;
            }

            if (nextToken == -1 || matchedMatcher == null) {
                sb.append(text.substring(current));
                break;
            }

            if (nextToken > current) {
                sb.append(text.substring(current, nextToken));
            }

            switch (tokenType) {
                case 1: { // Link: [text](url)
                    int matchStart = sb.length();
                    String linkText = matchedMatcher.group(1);
                    String url = matchedMatcher.group(2);
                    sb.append(linkText);
                    sb.setSpan(new URLSpan(url), matchStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new ForegroundColorSpan(primaryColor), matchStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                }
                case 2: { // Bold Italic: ***text***
                    int matchStart = sb.length();
                    String content = matchedMatcher.group(2);
                    appendInlineFormatted(sb, content, primaryColor, onSurfaceVariant, codeBg);
                    sb.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), matchStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                }
                case 3: { // Bold: **text**
                    int matchStart = sb.length();
                    String content = matchedMatcher.group(2);
                    appendInlineFormatted(sb, content, primaryColor, onSurfaceVariant, codeBg);
                    sb.setSpan(new StyleSpan(Typeface.BOLD), matchStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                }
                case 4: { // Italic: *text*
                    int matchStart = sb.length();
                    String content = matchedMatcher.group(2);
                    appendInlineFormatted(sb, content, primaryColor, onSurfaceVariant, codeBg);
                    sb.setSpan(new StyleSpan(Typeface.ITALIC), matchStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                }
                case 5: { // Strike: ~~text~~
                    int matchStart = sb.length();
                    String content = matchedMatcher.group(2);
                    appendInlineFormatted(sb, content, primaryColor, onSurfaceVariant, codeBg);
                    sb.setSpan(new StrikethroughSpan(), matchStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                }
                case 6: { // Code: `text`
                    int matchStart = sb.length();
                    String content = matchedMatcher.group(2);
                    sb.append(" ").append(content).append(" ");
                    sb.setSpan(new TypefaceSpan("monospace"), matchStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new BackgroundColorSpan(codeBg), matchStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new RelativeSizeSpan(0.9f), matchStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                }
            }

            current = matchedMatcher.end();
        }
    }

    private static int resolveColorAttr(Context context, String attrName, int defaultColor) {
        if (context == null || attrName == null) return defaultColor;
        int attrId = context.getResources().getIdentifier(attrName, "attr", context.getPackageName());
        if (attrId != 0) {
            return ThemeHelper.resolveThemeColor(context, attrId, defaultColor);
        }
        return defaultColor;
    }
}
