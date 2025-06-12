package com.kntrel.mc.commander.command;

import com.kntrel.mc.commander.exception.CommandException;
import com.kntrel.mc.commander.exception.CommandUnrunnableException;
import com.kntrel.mc.commander.exception.NoMoreArgumentsException;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import java.lang.reflect.Method;
import java.util.*;

public class CommandNode {

    //FIELDS
    private final String label_;
    private final Commander commander_;
    private final HashMap<String, CommandNode> children_ = new HashMap<>();
    private final CommandNode parentNode_;
    private final CommanderCommand parentCommand_;
    private final Object commandHolder_;
    private final ArrayList<CommandEndpoint> endpoints_ = new ArrayList<>();
    private final List<String> perms_ = new LinkedList<>();

    //ASSETS
    private static class NodeException extends Exception {
        private final CommandException innerException_;
        private int level_;
        private NodeException (CommandException exception, int level) { this.innerException_ = exception; this.level_ = level; }
    }

    //CONSTRUCTORS
    private CommandNode(CommandNode parent, String label, Object holder) {
        this.parentNode_ = parent;
        this.parentCommand_ = null;
        this.commander_ = this.parentNode_.commander_;
        this.label_ = label;
        this.commandHolder_ = holder;
    }
    CommandNode(CommanderCommand parent, String label, Object holder) {
        this.parentNode_ = null;
        this.parentCommand_ = parent;
        this.commander_ = this.parentCommand_.getCommander();
        this.label_ = label;
        this.commandHolder_ = holder;
    }

    //GETTERS
    CommandNode getNode(String label) {
        if (!this.children_.containsKey(label)) {
            this.children_.put(label,new CommandNode(this,label,this.commandHolder_));
        }

        return this.children_.get(label);
    }
    Commander getCommander() {
        return this.commander_;
    }
    Object getCommandHolder() {
        return this.commandHolder_;
    }
    public String getLabel() {
        return this.label_;
    }

    //PUBLIC METHODS
    void addMethod(Method method) {
        this.addMethod(method,null);
    }
    void addMethod(Method method, String permission) {
        CommandEndpoint endpoint = new CommandEndpoint(this,method);
        endpoint.setPermission(permission);
        this.endpoints_.add(endpoint);
    }

    String getFullPath () {
        StringBuilder builder = new StringBuilder();
        CommandNode node = this;
        do {
            builder.append(StringUtils.reverse(node.label_)).append(" ");
            node = node.parentNode_;
        } while (node != null);
        builder.setLength(builder.length() - 1);
        builder.reverse();

        return builder.toString();
    }

    private List<CommandEndpoint> getEndpoints(CommandSender sender, ArgumentPipe args) throws NodeException {
        LinkedList<NodeException> exceptions = new LinkedList<>();
        List<CommandEndpoint> endpoints = new LinkedList<>(this.endpoints_);
        Iterator<CommandEndpoint> i = endpoints.iterator();
        CommandEndpoint endpoint;

        while (i.hasNext()) {
            endpoint = i.next();
            try {
                endpoint.initialize(sender);
            } catch (CommandException e) {
                i.remove();
                exceptions.add(new NodeException(e,0));
            }
        }

        Argument arg;
        while (true) {
            try { arg = args.extract(); } catch (NoMoreArgumentsException ex) { break; }
            i = endpoints.iterator();
            while (i.hasNext()) {
                endpoint = i.next();
                if (endpoint.isReadyToRun()) { i.remove(); continue; }
                try {
                    endpoint.supplyArgument(arg);
                } catch (CommandException e) {
                    i.remove();
                    exceptions.add(new NodeException(e,args.getExtractedAmount()));
                }
            }
        }
        if (endpoints.isEmpty() && !exceptions.isEmpty()) {
            exceptions.sort(Comparator.comparingInt(a -> a.level_));
            throw exceptions.getLast();
        }
        return endpoints;
    }

    List<String> suggest(CommandSender sender, ArgumentPipe args) {
        List<String> r = new LinkedList<>();

        if (args.size() < 1) {
            this.children_.keySet().stream().filter(s -> this.children_.get(s).testPermission(sender)).forEach(r::add);
        } else if (this.children_.containsKey(args.get(0).getString())) {
            ArgumentPipe subArgs = args.CopyFromRemaining();
            subArgs.remove(0);
            r.addAll(this.children_.get(args.get(0).getString()).suggest(sender,subArgs));
        }
        try {
            this.getEndpoints(sender, args).stream().map(CommandEndpoint::suggest).forEach(l -> { if (l != null) r.addAll(l); });
        } catch (NodeException ignored) {}
        return r;
    }

    boolean testPermission(CommandSender sender) {
        if (this.perms_.stream().anyMatch(p -> p == null || sender.hasPermission(p))) { return true; }
        return this.children_.values().stream().anyMatch(c -> c.testPermission(sender));
    }

    public void addPermission(String permission) {
        this.perms_.add(permission);
        if (this.parentCommand_ != null) {
            this.parentCommand_.addPermission(permission);
        }
        if (this.parentNode_ != null) {
            this.parentNode_.addPermission(permission);
        }
    }
    public boolean removePermission(String permission) {
        boolean r = this.perms_.remove(permission);
        if (!r) { return false; }
        if (this.parentCommand_ != null) {
            this.parentCommand_.removePermission(permission);
        }
        if (this.parentNode_ != null) {
            this.parentNode_.removePermission(permission);
        }
        return true;
    }
    public boolean removeAllPermissions(String permission) {
        boolean r = false;
        while (this.perms_.contains(permission)) {
            this.perms_.remove(permission);
            r = true;
        }
        return r;
    }

    boolean run(CommandSender sender, String commandLabel, ArgumentPipe args) throws CommandException {
        try {
            return this.run(sender,commandLabel,args,false);
        } catch (NodeException ignored) { return false; }
    }

    private boolean run(CommandSender sender, String commandLabel, ArgumentPipe args, boolean subCall) throws CommandException, NodeException {
        this.log("Run triggered with args: " + args.toString() + ".");

        NodeException exception = null;
        if (args.size() > 0 && this.children_.containsKey(args.get(0).getString())) {
            String childName = args.get(0).getString();
            this.log("Trying to execute child '" + childName + "'.");
            try {
                ArgumentPipe subArgs = args.CopyFromRemaining();
                subArgs.remove(0);
                return this.children_.get(childName).run(sender, commandLabel, subArgs, true);
            } catch (NodeException ex) {
                ex.level_ = ex.level_ + 1;
                exception = ex;
                this.log("Failed execution of '" + childName + "' due to " + ex.innerException_.getClass().getSimpleName() + ". Level :" + ex.level_ + ".");
            }
        }


        try {
            List<CommandEndpoint> endpoints = this.getEndpoints(sender, args);

            if (endpoints.isEmpty()) {
                throw new NodeException(new CommandUnrunnableException("Unknown command"),0);
            } else if (endpoints.size() > 1) {
                endpoints.sort(Comparator.comparingInt(CommandEndpoint::consumedArguments));
                endpoints.removeIf(e -> e.getParameterCount() > endpoints.get(0).getParameterCount());
            }

            Object r = null;
            Iterator<CommandEndpoint> iterator = endpoints.iterator();
            CommandEndpoint endpoint;
            while (iterator.hasNext()) {
                endpoint = iterator.next();
                try {
                    r = endpoint.run();
                    break;
                } catch (CommandException e) {
                    iterator.remove();
                    if (!iterator.hasNext()) {
                        throw new NodeException(e,args.getExtractedAmount());
                    }
                }
            }

            if (endpoints.size() > 1) {
                this.commander_.getLogger().warning("Ambiguity executing command '" + this.getFullPath() + "'.");
            }

            if (r == null) { return true; }
            if (r instanceof Boolean bool) { return bool; } else { return true; }

        } catch (NodeException ex) {
            this.log("Exception caught. Type: " + ex.innerException_.getClass().getSimpleName() + ", Level: " + ex.level_ + ".");
            if (exception == null || exception.level_ < ex.level_) {
                exception = ex;
            }
        }

        if (subCall) { throw exception; } else { throw exception.innerException_; }
    }

    private void log (String msg){
        this.commander_.log(this,msg);
    }
}
