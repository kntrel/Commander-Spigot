package com.kntrel.mc.commander.command.provider.builtIn;

import com.kntrel.mc.commander.command.Argument;
import com.kntrel.mc.commander.command.provider.CommandProvider;
import com.kntrel.mc.commander.exception.CommandArgumentException;
import com.kntrel.mc.commander.exception.CommandException;
import com.kntrel.mc.commander.exception.CommandUnrunnableException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

public class NumberProvider extends CommandProvider<Number> {

    private static final Map<Class<?>,Class<?>> primitiveWrappers_ = Map.ofEntries(
            Map.entry(int.class,Integer.class),
            Map.entry(long.class,Long.class),
            Map.entry(byte.class,Byte.class),
            Map.entry(short.class,Short.class),
            Map.entry(double.class,Double.class),
            Map.entry(float.class,Float.class)
    );

    private Constructor constructor_;
    private Number val_;

    @Override
    protected void onInitialization() throws CommandException {
        Class<?> type = this.getParameter().getType();
        type = NumberProvider.primitiveWrappers_.getOrDefault(type,type);
        try {
            this.constructor_ = type.getConstructor(String.class);
            this.constructor_.setAccessible(true);
        } catch (NoSuchMethodException e) {
            this.getCommander().getLogger().severe(
            "Command unrunnable. '" + type.getSimpleName() + "' is not supported by the default NumberProvider. Please implement your own provider.\n" + e.getStackTrace()[0].toString()
            );
            throw new CommandUnrunnableException("Unable to run this command.");
        }
    }
    @Override
    public List<String> suggest() {
        return null;
    }
    @Override
    protected boolean handleArgument(Argument argument) throws CommandException {
        if (!(argument.isInt() || argument.isDouble())) {
            throw new CommandArgumentException(argument,"Not a number.");
        }
        try {
            this.val_ = (Number) constructor_.newInstance(argument.getString());
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof NumberFormatException) {
                throw new CommandArgumentException(argument,"Must be an integer.");
            }
        } catch (InstantiationException | IllegalAccessException e) {
            this.getCommander().getLogger().severe("Unable to parse number argument.");
            e.printStackTrace();
            throw new CommandArgumentException(argument, "Invalid argument.");
        }
        return true;
    }

    @Override
    public Number provide() throws CommandException {
        return val_;
    }
}
