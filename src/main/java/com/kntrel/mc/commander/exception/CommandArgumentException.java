package com.kntrel.mc.commander.exception;

import com.kntrel.mc.commander.command.Argument;

public class CommandArgumentException extends CommandException {

    private final Argument argument_;

    public CommandArgumentException(Argument argument, String message) {
        super(message);
        this.argument_ = argument;
    }

    public Argument getArgument() {
        return this.argument_;
    }

}
