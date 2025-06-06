package com.kntrel.mc.commander.command;

import com.kntrel.mc.commander.command.annotations.Command;
import com.kntrel.mc.commander.command.annotations.Requires;
import com.kntrel.mc.commander.command.provider.CommandProvider;
import com.kntrel.mc.commander.command.provider.builtIn.*;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.management.openmbean.KeyAlreadyExistsException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Commander {

    //ASSETS
    private record ProviderKey(Class<? extends Annotation> annotation, Class<?> toProvide) {}

    //FIELDS
    private final HashMap<ProviderKey, Constructor<? extends CommandProvider>> providers_ = new HashMap<>();
    private final JavaPlugin plugin_;
    private final CommandMap commandMap_;
    private final HashMap<String, CommanderCommand> registrationMap_ = new HashMap<>();
    private final Logger logger_;
    private Level logLevel_ = Level.FINEST;
    private static final Map<Class<?>,Class<?>> primitiveWrappers_ = Map.ofEntries(
            Map.entry(int.class,Integer.class),
            Map.entry(long.class,Long.class),
            Map.entry(byte.class,Byte.class),
            Map.entry(short.class,Short.class),
            Map.entry(double.class,Double.class),
            Map.entry(float.class,Float.class),
            Map.entry(boolean.class,Boolean.class),
            Map.entry(char.class,Character.class)
    );

    //CONSTRUCTORS
    public Commander(JavaPlugin mainInstance) {
        this.plugin_ = mainInstance;
        this.logger_ = this.plugin_.getLogger();
        CommandMap commandMap;
        try {
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            commandMap = (CommandMap) f.get(this.plugin_.getServer());
            this.plugin_.getServer().getPluginManager().registerEvents(new TabListener(this),this.plugin_);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            commandMap = null;
        }
        this.commandMap_ = commandMap;

        this.registerBuiltInProviders_();
    }

    //GETTERS
    public JavaPlugin getPlugin() {
        return this.plugin_;
    }
    public Logger getLogger() {
        return this.logger_;
    }
    public Level getLogLevel() {
        return this.logLevel_;
    }
    CommandMap getCommandMap() {
        return this.commandMap_;
    }
    public CommanderCommand getCommand(String label) {
        return this.registrationMap_.get(label.toLowerCase());
    }

    //SETTERS
    public void setLogLevel(Level level) {
        this.logLevel_ = level;
    }

    //COMMAND REGISTRATION
    public void register(CommandHolder commandHolder) {
        List<String> classLabels = Collections.emptyList();
        if (commandHolder.getClass().isAnnotationPresent(Command.class)) {
            classLabels = List.of(StringUtils.split(commandHolder.getClass().getAnnotation(Command.class).label(),' '));
        }

        for (Method method : commandHolder.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Command.class)) { continue; }

            LinkedList<String> labels = new LinkedList<>(classLabels);
            labels.addAll(List.of(StringUtils.split(method.getAnnotation(Command.class).label(),' ')));

            Iterator<String> i = labels.iterator();
            String label = i.next();
            CommanderCommand command = this.getCommand(label);
            if (command == null) {
                command = new CommanderCommand(this,label, commandHolder);
                this.registrationMap_.put(label.toLowerCase(),command);
            }
            CommandNode node = command.head_;
            while (i.hasNext()) {
                node = node.getNode(i.next());
            }

            String perm = null;
            if (method.isAnnotationPresent(Requires.class)) {
                perm = method.getAnnotation(Requires.class).permission();
            }

            node.addMethod(method,perm);
        }
    }
    void log(CommandNode node, String msg) {
        StringBuilder builder = new StringBuilder();
        builder.append("[").append(node.getFullPath()).append("] ").append(msg);
        this.logger_.log(this.logLevel_,builder.toString());
    }

    //PROVIDER HANDLING
    public <E> void registerProvider(Class<E> target, CommandProvider<E> provider) {
        this.registerProvider(null,target,provider);
    }
    public <E> void registerProvider(Class<? extends Annotation> annotation,Class<E> target, CommandProvider<E> provider) {
        if (this.providers_.keySet().stream().anyMatch(k -> {
                if (annotation == null) {
                    return k.annotation == null && k.toProvide.equals(target);
                }
                return annotation.equals(k.annotation) && k.toProvide.equals(target);
        })) {
            throw new KeyAlreadyExistsException(
                "There's already a provider registered under such key: [" + annotation.getSimpleName() + " + " + target.getSimpleName() + "]."
            );
        }
        Class<? extends CommandProvider> providerClass = provider.getClass();
        try {
            this.providers_.put(new ProviderKey(annotation,target),providerClass.getConstructor());
        } catch (NoSuchMethodException es) {
            es.printStackTrace();
            throw new IllegalArgumentException("'" + providerClass.getSimpleName() + "' must have a no-parameter constructor to be register to a Commander.");
        }
    }
    public <E> CommandProvider<E> getProvider(Class<? extends Annotation> annotation, Class<E> providerType) {
        Map.Entry<ProviderKey,Constructor<? extends CommandProvider>> entry;
        boolean repeat = true;
        Class<E> type = (Class<E>) Commander.primitiveWrappers_.getOrDefault(providerType, providerType);
        Predicate<ProviderKey> filter = k -> k.toProvide.equals(type);

        while (true) {
            Predicate<ProviderKey> f = filter;
            entry = this.providers_.entrySet().stream()
                    .filter(e -> {
                        ProviderKey key = e.getKey();
                        if (annotation == null) {
                            return key.annotation == null && f.test(key);
                        }
                        return annotation.equals(key.annotation) && f.test(key);
                    })
                    .findFirst().orElse(null);
            if (entry == null && repeat) {
                filter = k -> k.toProvide.isAssignableFrom(type);
                repeat = false;
            } else { break; }
        }

        if (entry == null) {return null;}

        try {
            return (CommandProvider<E>) entry.getValue().newInstance();
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    //PRIVATE METHODS
    private void registerBuiltInProviders_() {
        this.registerProvider(Number.class, new NumberProvider());
        this.registerProvider(Boolean.class, new BooleanProvider());
        this.registerProvider(Character.class, new CharacterProvider());
        this.registerProvider(Enum.class, new EnumProvider());
        this.registerProvider(Player.class, new PlayerProvider());
        this.registerProvider(Location.class, new LocationProvider());
        this.registerProvider(World.class, new WorldProvider());
        this.registerProvider(String.class, new StringProvider());
        this.registerProvider(CommandSender.class, new SenderProvider());
    }
}
