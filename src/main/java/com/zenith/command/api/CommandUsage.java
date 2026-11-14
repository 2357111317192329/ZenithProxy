package com.zenith.command.api;

import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.zenith.Globals.DEFAULT_LOG;

@Getter
@Setter
public class CommandUsage {
    private final String name;
    private final CommandCategory category;
    private final String description;
    private final List<String> usageLines;
    private final List<String> aliases;

    private CommandUsage(final String name, final CommandCategory category, final String description, final List<String> usageLines, final List<String> aliases) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.usageLines = usageLines;
        this.aliases = aliases;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private CommandCategory category;
        private String description;
        private List<String> usageLines = Collections.singletonList("");
        private List<String> aliases = Collections.emptyList();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder category(CommandCategory category) {
            this.category = category;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder usageLines(List<String> usageLines) {
            this.usageLines = usageLines;
            return this;
        }

        public Builder usageLines(String... usageLines) {
            this.usageLines = List.of(usageLines);
            return this;
        }

        public Builder aliases(List<String> aliases) {
            this.aliases = aliases;
            return this;
        }

        public Builder aliases(String... aliases) {
            this.aliases = List.of(aliases);
            return this;
        }

        public CommandUsage build() {
            if (name == null) {
                throw new IllegalStateException("name cannot be null");
            }
            if (category == null) {
                throw new IllegalStateException("category cannot be null");
            }
            if (description == null) {
                throw new IllegalStateException("description cannot be null");
            }
            return new CommandUsage(name, category, description, usageLines, aliases);
        }
    }

    @Deprecated
    public static CommandUsage simple(final String name, final CommandCategory category, final String description) {
        return new CommandUsage(name, category, description, Collections.singletonList(""), Collections.emptyList());
    }

    @Deprecated
    public static CommandUsage simpleAliases(final String name, final CommandCategory category, final String description, final List<String> aliases) {
        return new CommandUsage(name, category, description, Collections.singletonList(""), aliases);
    }

    @Deprecated
    public static CommandUsage args(final String name, final CommandCategory category, final String description, final List<String> usageLines) {
        return new CommandUsage(name, category, description, usageLines, Collections.emptyList());
    }

    @Deprecated
    public static CommandUsage full(final String name, final CommandCategory category, final String description, final List<String> usageLines, final List<String> aliases) {
        return new CommandUsage(name, category, description, usageLines, aliases);
    }

    // serializes everything
    public String serialize(CommandSource commandSource) {
        var result = this.description
            + "\n**Commands**"
            + usageLines.stream()
            .map(line -> "\n" + commandSource.commandPrefix() + name + " " + line)
            .collect(Collectors.joining());
        result += "\n\n[Commands Wiki](https://link.2b2t.vc/0)";
        if (isTooLongForDiscordDescription(result)) {
            DEFAULT_LOG.debug("Full command usage too long for discord description: {}", name);
            return this.mediumSerialize(commandSource);
        }
        return result;
    }

    /**
     * Full usage serialized as a list of pages. Unlike {@link #serialize(CommandSource)} this never
     * degrades to the bare command name: long usage lists are split into multiple pages (each page
     * fits within Discord's 2000-char message limit and the 4096-char embed description limit).
     * Intended for the "Invalid command usage" output and `help <command>`.
     */
    public List<String> serializeAll(CommandSource commandSource) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add(this.description);
        lines.add("**Commands**");
        for (var usageLine : usageLines) {
            lines.add(commandSource.commandPrefix() + name + " " + usageLine);
        }
        lines.add("");
        lines.add("[Commands Wiki](https://link.2b2t.vc/0)");
        final int pageLimit = 1900; // Discord message limit is 2000 chars
        final List<String> pages = new java.util.ArrayList<>();
        final StringBuilder current = new StringBuilder();
        for (final String line : lines) {
            final String addition = current.length() == 0 ? line : "\n" + line;
            if (current.length() > 0 && current.length() + addition.length() > pageLimit) {
                pages.add(current.toString());
                current.setLength(0);
                current.append("**Commands**\n").append(line); // continuation page header
            } else {
                current.append(addition);
            }
        }
        if (current.length() > 0) pages.add(current.toString());
        return pages;
    }

    // serializes usage lines only
    public String mediumSerialize(CommandSource commandSource) {
        var result = "**Commands**"
            + usageLines.stream()
            .map(line -> "\n" + commandSource.commandPrefix() + name + " " + line)
            .collect(Collectors.joining());
        result += "\n\n[Commands Wiki](https://link.2b2t.vc/0)";
        if (isTooLongForDiscordDescription(result)) {
            DEFAULT_LOG.debug("Medium command usage too long for discord description: {}", name);
            return this.shortSerialize(commandSource);
        }
        return result;
    }

    // serializes aliases only
    public String shortSerialize(CommandSource commandSource) {
        String result = commandSource.commandPrefix() + this.name;
        if (!aliases.isEmpty()) {
            result += aliases.stream()
                    .collect(Collectors.joining(" / " + commandSource.commandPrefix(),
                            " / " + commandSource.commandPrefix(),
                            ""));
        }
        result += "\n\n[Commands Wiki](https://link.2b2t.vc/0)";
        return result;
    }

    public String shortSerializeButNoWikiFooter(CommandSource commandSource) {
        String result = commandSource.commandPrefix() + this.name;
        if (!aliases.isEmpty()) {
            result += aliases.stream()
                .collect(Collectors.joining(" / " + commandSource.commandPrefix(),
                                            " / " + commandSource.commandPrefix(),
                                            ""));
        }
        return result;
    }

    // Discord embed description limit is 4096 characters, not 1024.
    // Using 1024 caused long usage lists (e.g. clientConnection with 33 usage lines) to
    // degrade all the way down to the bare command name in `help <command>` and in the
    // "Invalid command usage" output, making the usage impossible to discover without tab-completion.
    private boolean isTooLongForDiscordDescription(String str) {
        return str.length() > 4096;
    }
}
