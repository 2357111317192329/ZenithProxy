package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;

import static com.zenith.Globals.LAUNCH_CONFIG;

public class LicenseCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("license")
            .category(CommandCategory.INFO)
            .description("Displays the software license and information about your legal rights")
            .usageLines("")
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("license").executes(c -> {
            c.getSource()
                .getEmbed()
                .title("ZenithProxy License Info")
                .description("""
                    ZenithProxy is licensed under [the GNU Affero General Public License](https://www.gnu.org/licenses/agpl-3.0.en.html).
                    
                    This means that you are free to use, modify, and distribute ZenithProxy (including hosting as a free or paid service) as long as:
                    1. You make the source code available to users
                    2. Any modifications you make to ZenithProxy are licensed under the AGPL
                    
                    Compiled releases bundle third party code and data which each remain licensed under their original licenses.
                    
                    Source code for ZenithProxy is available at [the ZenithProxy GitHub repository](%s).
                    """.formatted(sourceUrl()))
                .primaryColor();

        });
    }

    /**
     * If you distribute modified versions of ZenithProxy that do not properly configure the launch_config.json, replace this with a link to your repository.
     */
    private String sourceUrl() {
        return "https://github.com/" + LAUNCH_CONFIG.repo_owner + "/" + LAUNCH_CONFIG.repo_name;
    }
}
