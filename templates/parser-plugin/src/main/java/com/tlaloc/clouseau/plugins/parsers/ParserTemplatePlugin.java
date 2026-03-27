package com.tlaloc.clouseau.plugins.parsers;

import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

public final class ParserTemplatePlugin extends Plugin {

    public ParserTemplatePlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        log.info("Parser template plugin started");
    }

    @Override
    public void stop() {
        log.info("Parser template plugin stopped");
    }
}
