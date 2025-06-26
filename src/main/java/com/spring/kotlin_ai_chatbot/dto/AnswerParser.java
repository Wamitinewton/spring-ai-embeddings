package com.spring.kotlin_ai_chatbot.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnswerParser {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w+)?\\n([\\s\\S]*?)```");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,4}\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern NUMBERED_STEPS_PATTERN = Pattern.compile("^\\d+\\.\\s+\\*\\*(.+?)\\*\\*:?$", Pattern.MULTILINE);

    public ChatResponse.ParsedContent parse(String rawAnswer) {
        if (rawAnswer == null || rawAnswer.trim().isEmpty()) {
            return createEmptyContent();
        }

        List<ChatResponse.CodeExample> codeExamples = extractCodeExamples(rawAnswer);
        
        String textForSummary = removeCodeBlocks(rawAnswer);
        
        String summary = extractSummary(textForSummary);
        
        List<ChatResponse.ContentSection> sections = parseIntoSections(rawAnswer);

        return new ChatResponse.ParsedContent(summary, sections, codeExamples);
    }

    private List<ChatResponse.CodeExample> extractCodeExamples(String text) {
        List<ChatResponse.CodeExample> examples = new ArrayList<>();
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);
        
        while (matcher.find()) {
            String language = matcher.group(1) != null ? matcher.group(1) : "text";
            String code = matcher.group(2).trim();
            
            String description = getCodeDescription(text, matcher.start());
            
            examples.add(new ChatResponse.CodeExample(language, code, description, null));
        }
        
        return examples;
    }

    private String getCodeDescription(String text, int codeStart) {
        String beforeCode = text.substring(0, codeStart);
        String[] lines = beforeCode.split("\n");
        
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty() && !line.startsWith("#") && !line.matches("^\\d+\\..*")) {
                return cleanMarkdown(line);
            }
        }
        
        return "Code example";
    }

    private String removeCodeBlocks(String text) {
        return CODE_BLOCK_PATTERN.matcher(text).replaceAll("");
    }

    private String extractSummary(String text) {
        String[] paragraphs = text.split("\n\n");
        
        for (String paragraph : paragraphs) {
            String cleaned = cleanMarkdown(paragraph.trim());
            if (cleaned.length() > 50 && !cleaned.startsWith("#")) {
                String[] sentences = cleaned.split("\\. ");
                if (sentences.length > 0) {
                    String summary = sentences[0];
                    if (sentences.length > 1 && summary.length() < 100) {
                        summary += ". " + sentences[1];
                    }
                    return summary.endsWith(".") ? summary : summary + ".";
                }
            }
        }
        
        return "Kotlin programming guidance";
    }

    private List<ChatResponse.ContentSection> parseIntoSections(String text) {
        List<ChatResponse.ContentSection> sections = new ArrayList<>();
        
        String[] parts = HEADING_PATTERN.split(text);
        Matcher headingMatcher = HEADING_PATTERN.matcher(text);
        
        List<String> headings = new ArrayList<>();
        while (headingMatcher.find()) {
            headings.add(headingMatcher.group(1));
        }

        for (int i = 0; i < Math.min(parts.length - 1, headings.size()); i++) {
            String title = cleanMarkdown(headings.get(i));
            String rawContent = parts[i + 1].trim();
            String cleanContent = cleanMarkdown(rawContent);
            
            if (!cleanContent.isEmpty()) {
                String sectionType = determineSectionType(title);
                boolean hasCode = rawContent.contains("```");
                
                sections.add(new ChatResponse.ContentSection(title, rawContent, cleanContent, sectionType, hasCode));
            }
        }

        if (sections.isEmpty()) {
            sections = parseByParagraphs(text);
        }

        return sections;
    }

    private List<ChatResponse.ContentSection> parseByParagraphs(String text) {
        List<ChatResponse.ContentSection> sections = new ArrayList<>();
        String[] paragraphs = text.split("\n\n");
        
        for (int i = 0; i < paragraphs.length; i++) {
            String rawParagraph = paragraphs[i].trim();
            String cleanParagraph = cleanMarkdown(rawParagraph);
            
            if (cleanParagraph.length() > 20) {
                String title = "Section " + (i + 1);
                
                Matcher stepMatcher = NUMBERED_STEPS_PATTERN.matcher(rawParagraph);
                if (stepMatcher.find()) {
                    title = stepMatcher.group(1);
                }
                
                boolean hasCode = rawParagraph.contains("```");
                sections.add(new ChatResponse.ContentSection(title, rawParagraph, cleanParagraph, "explanation", hasCode));
            }
        }
        
        return sections;
    }

    private String determineSectionType(String title) {
        String lowerTitle = title.toLowerCase();
        
        if (lowerTitle.contains("step") || lowerTitle.contains("implement")) {
            return "steps";
        } else if (lowerTitle.contains("conclusion") || lowerTitle.contains("summary")) {
            return "conclusion";
        } else if (lowerTitle.contains("example") || lowerTitle.contains("usage")) {
            return "example";
        } else {
            return "explanation";
        }
    }

    private String cleanMarkdown(String text) {
        return text
            .replaceAll("\\*\\*(.*?)\\*\\*", "$1") 
            .replaceAll("\\*(.*?)\\*", "$1")
            .replaceAll("`(.*?)`", "$1")
            .replaceAll("#{1,6}\\s*", "")
            .replaceAll("\\n+", " ")
            .trim();
    }

    private ChatResponse.ParsedContent createEmptyContent() {
        return new ChatResponse.ParsedContent(
            "No content available",
            new ArrayList<>(),
            new ArrayList<>()
        );
    }
}