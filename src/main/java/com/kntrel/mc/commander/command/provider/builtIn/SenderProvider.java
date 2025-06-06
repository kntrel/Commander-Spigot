package com.kntrel.mc.commander.command.provider.builtIn;

import com.kntrel.mc.commander.command.Argument;
import com.kntrel.mc.commander.exception.CommandException;
import com.kntrel.mc.commander.command.provider.CommandProvider;
import org.bukkit.command.CommandSender;

import java.util.List;

public class SenderProvider extends CommandProvider<CommandSender> {

    @Override
    protected void onInitialization() {
        this.setReadyToProvide(true);
    }

    @Override
    public List<String> suggest() {
        return null;
    }

    @Override
    protected boolean handleArgument(Argument argument) {
        return true;
    }

    @Override
    public CommandSender provide() throws CommandException {
        return this.getCommandSender();
    }
}
