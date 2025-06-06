package com.kntrel.mc.commander.command;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;

import java.util.Arrays;
import java.util.List;

public final class TabListener implements Listener {

    private final Commander commander_;

    TabListener(Commander commander) {
        this.commander_ = commander;
    }

    @EventHandler
    private void onTab(TabCompleteEvent e) {
        String buffer = e.getBuffer();
        String[] args = StringUtils.split(buffer," ");
        String label = args[0].startsWith("/") ? args[0].substring(1) : args[0];

        CommanderCommand command = this.commander_.getCommand(label);
        if (command == null) { return; }

        args = Arrays.copyOfRange(args,1,args.length);
        if (!(buffer.charAt(buffer.length() - 1) == ' ')) {
            args = Arrays.copyOfRange(args, 0,args.length - 1);
        }

        List<String> suggestions = this.commander_.getCommand(label).suggest(e.getSender(),args);
        e.setCompletions(suggestions);
    }

}
