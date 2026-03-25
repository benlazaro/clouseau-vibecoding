package com.clouseau.plugins.parsers;

import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

public final class BuiltinParsersPlugin extends Plugin {

    public BuiltinParsersPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        log.info("Built-in parsers plugin started (Log4j, JSON, syslog)");
    }

    @Override
    public void stop() {
        log.info("Built-in parsers plugin stopped");
    }
}
