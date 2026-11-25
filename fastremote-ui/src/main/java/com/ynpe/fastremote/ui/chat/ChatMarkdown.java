// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui.chat;

import java.util.*;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.ext.gfm.tables.*;
import org.commonmark.ext.gfm.strikethrough.*;
import com.ynpe.fastremote.ui.chat.ChatDocument.Cell;
import com.ynpe.fastremote.ui.chat.ChatDocument.Run;

/** Worker-confined Markdown parser. It creates data only, never scene graph nodes. */
public final class ChatMarkdown {
    public record Block(String style, List<Cell> cells, Optional<ChatDocument.CodeSnippet> code) {
        public Block { cells = List.copyOf(cells); Objects.requireNonNull(code); }
        public Block(String style, List<Cell> cells) { this(style, cells, Optional.empty()); }
    }
    private final java.util.function.BiFunction<String, String, List<Run>> highlighter;
    public ChatMarkdown() { this((text, language) -> List.of(new Run(text, "code", ""))); }
    public ChatMarkdown(java.util.function.BiFunction<String, String, List<Run>> highlighter) { this.highlighter = Objects.requireNonNull(highlighter); }
    private final Parser parser = Parser.builder().maxOpenBlockParsers(64).maxInlineNesting(64).extensions(List.of(
            TablesExtension.builder().maxCells(8192).build(), StrikethroughExtension.create())).build();
    public List<Block> parse(String markdown) {
        var result = new ArrayList<Block>();
        try { blocks(parser.parse(markdown), "", result); }
        catch (IllegalArgumentException invalid) {
            // Bounded parser rejects oversized tables. Preserve all text as safe literal content.
            result.clear(); add(result, "", List.of(new Run(markdown)));
        }
        // A short-line flood must not create hundreds of thousands of retained block objects.
        if (result.size() > 2048) { result.clear(); add(result, "", List.of(new Run(markdown))); }
        if (result.isEmpty()) result.add(new Block("", List.of(Cell.text(""))));
        return List.copyOf(result);
    }
    private void blocks(Node parent, String inherited, List<Block> out) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            switch (n) {
                case Heading h -> add(out, "heading heading-" + h.getLevel(), inline(n));
                case Paragraph p -> add(out, inherited, inline(p));
                case FencedCodeBlock c -> code(out, c.getLiteral(), c.getInfo());
                case IndentedCodeBlock c -> code(out, c.getLiteral(), "");
                case BlockQuote q -> blocks(q, "quote", out);
                case BulletList l -> list(l, false, 1, out);
                case OrderedList l -> list(l, true, l.getMarkerStartNumber(), out);
                case ThematicBreak ignored -> out.add(new Block("rule", List.of(Cell.text("―"))));
                case HtmlBlock h -> add(out, inherited, List.of(new Run(h.getLiteral())));
                case TableBlock table -> table(table, out);
                case LinkReferenceDefinition ignored -> { }
                default -> blocks(n, inherited, out);
            }
        }
    }
    private void list(Node list, boolean ordered, int number, List<Block> out) {
        for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
            var children = new ArrayList<Block>(); blocks(item, "list", children);
            if (children.isEmpty()) continue;
            Block first = children.getFirst(); var runs = new ArrayList<Run>();
            if (first.code().isPresent()) { out.addAll(children); if (ordered) number++; continue; }
            runs.add(new Run(ordered ? (number++) + ". " : "• ")); runs.addAll(first.cells.getFirst().runs());
            add(out, "list", runs); out.addAll(children.subList(1, children.size()));
        }
    }
    private void table(Node table, List<Block> out) {
        for (Node section = table.getFirstChild(); section != null; section = section.getNext()) {
            for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                var cells = new ArrayList<Cell>();
                for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) cells.add(new Cell(inline(cell)));
                if (cells.stream().anyMatch(c -> c.text().length() > 2048) || cells.size() > 32) {
                    var plain = new ArrayList<Run>();
                    for (int i = 0; i < cells.size(); i++) { if (i > 0) plain.add(new Run("\t")); plain.addAll(cells.get(i).runs()); }
                    add(out, "table", plain); continue;
                }
                if (!cells.isEmpty()) out.add(new Block(section instanceof TableHead ? "table table-head" : "table", cells));
            }
        }
    }
    private void code(List<Block> out, String text, String language) {
        int first = out.size();
        String label = language.strip().split("\\s+", 2)[0];
        if (label.length() > 32) label = label.substring(0, 32);
        String code = text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
        var line = new ArrayList<Run>();
        for (Run run : highlighter.apply(code, language.strip().split("\\s+", 2)[0])) {
            String[] parts = run.text().split("\n", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) { add(out, "code", line); line = new ArrayList<>(); }
                line.add(new Run(parts[i], run.style(), ""));
            }
        }
        add(out, "code", line);
        for (int i = first; i < out.size(); i++) {
            Block block = out.get(i);
            out.set(i, new Block(block.style() + (i == first ? " code-first" : "")
                    + (i == out.size() - 1 ? " code-last" : ""), block.cells(),
                    i == first ? Optional.of(new ChatDocument.CodeSnippet(label, text)) : Optional.empty()));
        }
    }
    private List<Run> inline(Node node) {
        var runs = new ArrayList<Run>(); inline(node, "", "", runs); return runs;
    }
    private void inline(Node parent, String style, String link, List<Run> out) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) {
            switch (n) {
                case Text t -> out.add(new Run(t.getLiteral(), style, link));
                case Code c -> out.add(new Run(c.getLiteral(), style + " inline-code", link));
                case SoftLineBreak ignored -> out.add(new Run(" ", style, link));
                case HardLineBreak ignored -> out.add(new Run("\n", style, link));
                case Emphasis ignored -> inline(n, style + " italic", link, out);
                case StrongEmphasis ignored -> inline(n, style + " bold", link, out);
                case Strikethrough ignored -> inline(n, style + " strike", link, out);
                case Link l -> inline(n, style + " link", l.getDestination(), out);
                case Image ignored -> inline(n, style, "", out); // alt only; never fetch model URLs
                case HtmlInline h -> out.add(new Run(h.getLiteral(), style, link));
                default -> inline(n, style, link, out);
            }
        }
    }
    private static void add(List<Block> out, String style, List<Run> runs) {
        // Bound the amount of text laid out by a single virtual cell, even without newlines.
        var part = new ArrayList<Run>(); int length = 0; boolean continued = false;
        for (Run run : runs) {
            for (int start = 0; start < run.text().length();) {
                int end = Math.min(run.text().length(), start + 2048 - length);
                if (end < run.text().length() && Character.isHighSurrogate(run.text().charAt(end - 1))) end--;
                if (end == start) { out.add(new Block(style + (continued ? " continuation" : ""), List.of(new Cell(part)))); continued = true; part = new ArrayList<>(); length = 0; continue; }
                String text = run.text().substring(start, end); part.add(new Run(text, run.style(), run.link()));
                length += text.length(); start = end;
                if (length >= 2047) { out.add(new Block(style + (continued ? " continuation" : ""), List.of(new Cell(part)))); continued = true; part = new ArrayList<>(); length = 0; }
            }
        }
        if (!part.isEmpty() || runs.isEmpty() || runs.stream().allMatch(r -> r.text().isEmpty()))
            out.add(new Block(style + (continued ? " continuation" : ""), List.of(new Cell(part))));
    }
}
