package com.jkantrell.commander.command;

import com.jkantrell.commander.exception.*;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;

import java.util.*;

class CommanderCommand extends BukkitCommand {
    protected final CommandNode head_;
    private final Commander commander_;
    private final List<String> perms_ = new LinkedList<>();

    CommanderCommand(Commander commander, String name, Object commandHolder) {
        super(name);
        this.commander_ = commander;
        this.head_ = new CommandNode(this,this.getName(),commandHolder);
        this.commander_.getCommandMap().register(this.commander_.getPlugin().getName(),this);
    }

    Commander getCommander() {
        return this.commander_;
    }

    void Unregister() {
        this.unregister(this.commander_.getCommandMap());
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        ArgumentPipe pipe = new ArgumentPipe(this.commander_,this,sender, args);
        try {
            return this.head_.run(sender, commandLabel, pipe);
        } catch (CommandException e) {
            StringBuilder message = new StringBuilder();
            message.append(ChatColor.RED);

            if (e instanceof CommandArgumentException exception) {
                message.append("Argument Error!").append("\n");
                if (pipe.contains(exception.getArgument())) {
                    message.append(commandLabel).append(" ");
                    for (int i = 0; i <= pipe.indexOf(exception.getArgument()); i++) {
                        message.append(args[i]).append(" ");
                    }
                    message.append("<-- HERE\n");
                }
                message.append(exception.getMessage());
            }
            if (e instanceof NoMoreArgumentsException) {
                message.append("Incomplete command! Usage:\n").append(this.getUsage());
            }
            if (e instanceof CommandUnrunnableException) {
                message.append(e.getMessage());
            }
            if (e instanceof CommandNotAllowedException) {
                message.append(e.getMessage());
            }

            sender.sendMessage(message.toString());
            return false;
        }
    }
    public List<String> suggest(CommandSender sender, String[] args) {
        return this.head_.suggest(sender, new ArgumentPipe(this.commander_,this,sender,args));
    }
    public void addPermission(String permission) {
        this.perms_.add(permission);
        this.composePermission_();
    }
    public boolean removePermission(String permission) {
        boolean r = this.perms_.remove(permission);
        if (r) { this.composePermission_(); }
        return r;
    }
    public boolean removeAllPermissions(String permission) {
        boolean r = this.perms_.removeIf(s -> s.equals(permission));
        if (r) { this.composePermission_(); }
        return r;
    }

    //PRIVATE METHODS
    private void composePermission_() {
        /*
        StringBuilder builder = new StringBuilder();
        Iterator<String> i = this.perms_.stream().distinct().toList().iterator();
        String perm;
        while (true) {
            perm = i.next();
            if (perm == null) {
                this.setPermission(null);
                return;
            }
            builder.append(perm);
            if (!i.hasNext()) { break; }
            builder.append(";");
        }
        perm = builder.isEmpty() ? null : builder.toString();
        this.setPermission(perm);
        this.commander_.getLogger().info(this.getPermission());
         */
    }
}
