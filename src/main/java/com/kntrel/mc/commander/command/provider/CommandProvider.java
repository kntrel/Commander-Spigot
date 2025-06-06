package com.kntrel.mc.commander.command.provider;

import com.kntrel.mc.commander.command.Argument;
import com.kntrel.mc.commander.command.CommandEndpoint;
import com.kntrel.mc.commander.command.Commander;
import com.kntrel.mc.commander.exception.CommandException;
import com.kntrel.mc.commander.exception.CommandProviderException;
import org.bukkit.command.CommandSender;
import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.List;

public abstract class CommandProvider<E> {

    private int consecutive_ = 0;
    private Commander commander_ = null;
    private CommandSender commandSender_ = null;
    private Parameter parameter_ = null;
    private boolean isInitialized_ = false;
    private boolean readyToProvide_ = false;
    private List<CommandProvider<?>> invocationProviders_ = Collections.emptyList();

    public final void initialize(Commander commander, CommandSender sender, Parameter parameter) throws CommandException {
        this.consecutive_ = 0;
        this.commander_ = commander;
        this.commandSender_ = sender;
        this.parameter_ = parameter;
        this.isInitialized_ = true;

        this.onInitialization();
    }
    public final void initialize(CommandProvider<?> reference) throws CommandException {
        this.initialize(reference.commander_,reference.commandSender_,reference.parameter_,reference.invocationProviders_);
    }
    public final void initialize(Commander commander, CommandSender sender, Parameter parameter, List<CommandProvider<?>> invocationProviders) throws CommandException {
        this.invocationProviders_ = invocationProviders;
        this.initialize(commander,sender,parameter);
    }

    //PROTECTED GETTERS
    protected final Commander getCommander() {
        return this.commander_;
    }
    protected final CommandSender getCommandSender() {
        return this.commandSender_;
    }
    protected final Annotation[] getAnnotations() {
        return this.parameter_.getAnnotations();
    }
    protected final <A extends Annotation> boolean isAnnotationPresent(Class<A> annotationTYpe) {
        return this.parameter_.isAnnotationPresent(annotationTYpe);
    }
    protected final <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        return this.parameter_.getAnnotation(annotationType);
    }
    protected final Parameter getParameter() {
        return this.parameter_;
    }
    protected final int getSupplyConsecutive() {
        return this.consecutive_;
    }
    protected final List<CommandProvider<?>> getInvocationProviders() {
        return this.invocationProviders_;
    }

    //PUBLIC GETTERS
    public boolean readyToProvide() {
        return this.readyToProvide_;
    }
    public final boolean isInitialized() {
        return this.isInitialized_;
    }

    //PROTECTED SETTERS
    protected void setReadyToProvide(boolean isReadyToProvide) {
        this.readyToProvide_ = isReadyToProvide;
    }

    //PUBLIC SETTERS
    public void setRunningEndpoint(CommandEndpoint endpoint) {

    }

    //PUBLIC METHODS
    public final boolean supply(Argument argument) throws CommandException {
        if (!this.isInitialized_) {
            throw new CommandProviderException("The provider is not yet initialized. Use the 'initialize()' method before supplying.");
        }
        this.consecutive_++;
        this.readyToProvide_ = this.handleArgument(argument);
        return this.readyToProvide_;
    }
    protected void onInitialization() throws CommandException {}

    //ABSTRACT METHODS
    abstract public List<String> suggest();
    abstract protected boolean handleArgument(Argument argument) throws CommandException;
    abstract public E provide() throws CommandException;

}
