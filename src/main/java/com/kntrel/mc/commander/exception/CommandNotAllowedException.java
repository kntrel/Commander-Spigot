package com.kntrel.mc.commander.exception;

public class CommandNotAllowedException extends CommandException {

    private final String permission_;

    public CommandNotAllowedException(String permission, String message) {
        super(message);
        this.permission_ = permission;
    }

    public String getPermission() {
        return this.permission_;
    }
}
