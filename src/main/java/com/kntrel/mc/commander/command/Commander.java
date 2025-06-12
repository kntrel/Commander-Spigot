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
import javax.annotation.Nullable;
import javax.management.openmbean.KeyAlreadyExistsException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Commander {

    //ASSETS
    private record ProviderKey(@Nullable Class<? extends Annotation> annotation, Class<?> toProvide) {}

    //FIELDS
    private final HashMap<ProviderKey, Supplier<? extends CommandProvider<?>>> providerMap_ = new HashMap<>();
    private final JavaPlugin plugin_;
    private final CommandMap commandMap_;
    private final HashMap<String, CommanderCommand> registrationMap_ = new HashMap<>();
    private final Logger logger_;
    private Level logLevel_ = Level.FINEST;
    private static final Map<Class<?>,Class<?>> PRIMITIVE_WRAPPERS = Map.ofEntries(
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
    public Commander(JavaPlugin plugin) {
        this.plugin_ = plugin;
        this.logger_ = this.plugin_.getLogger();
        CommandMap commandMap;
        try {
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            commandMap = (CommandMap) f.get(this.plugin_.getServer());
            this.plugin_.getServer().getPluginManager().registerEvents(new TabListener(this),this.plugin_);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            this.logger_.warning(e.getMessage());
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
    public <E> void registerProvider(Class<E> target, Class<? extends CommandProvider<E>> providerClass) {
        this.registerProvider(null, target, providerClass);
    }
    public <E> void registerProvider(@Nullable Class<? extends Annotation> annotation, Class<E> target, Class<? extends CommandProvider<E>> providerClass) {
        Constructor<? extends CommandProvider<E>> constructor;
        try {
            constructor = providerClass.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        Supplier<? extends CommandProvider<E>> supplier = () -> {
            try {
                return constructor.newInstance();
            } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        };

        this.registerProvider(annotation, target, supplier);
    }
    public <E> void registerProvider(Class<E> target, Supplier<? extends CommandProvider<E>> providerSupplier) {
        this.registerProvider(null, target, providerSupplier);
    }

    @SuppressWarnings("unchecked")
    public <E> void registerProvider(@Nullable Class<? extends Annotation> annotation, Class<E> target, Supplier<? extends CommandProvider<E>> providerSupplier) {
        target = (Class<E>) Commander.PRIMITIVE_WRAPPERS.getOrDefault(target, target);
        ProviderKey key = new ProviderKey(annotation, target);

        if (this.providerMap_.containsKey(key)) {
            String annotationName = (annotation == null) ? "<null>" : annotation.getSimpleName();
            throw new KeyAlreadyExistsException(
                "There's already a provider registered under such key: [" + annotationName + " + " + target.getSimpleName() + "]."
            );
        }

        this.providerMap_.put(key, providerSupplier);

    }

    @SuppressWarnings("unchecked")
    public <E> Optional<CommandProvider<E>> getProvider(@Nullable Class<? extends Annotation> annotation, Class<E> providerType) {
        providerType = (Class<E>) Commander.PRIMITIVE_WRAPPERS.getOrDefault(providerType, providerType);
        ProviderKey pKey = new ProviderKey(annotation, providerType);
        Supplier<? extends CommandProvider<?>> provider = this.providerMap_.get(pKey);

        if (provider != null) {
            return Optional.of((CommandProvider<E>) provider.get());
        }

        Class<E> finalProviderType = providerType;
        return this.providerMap_.entrySet().stream()
                .filter(e -> {
                    ProviderKey k = e.getKey();
                    return Objects.equals(k.annotation(), annotation) && k.toProvide().equals(finalProviderType);
                })
                .findFirst()
                .map(e -> (CommandProvider<E>) e.getValue().get());
    }

    //PRIVATE METHODS
    private void registerBuiltInProviders_() {
        this.registerProvider(Number.class, NumberProvider.class);
        this.registerProvider(Boolean.class, BooleanProvider.class);
        this.registerProvider(Character.class, CharacterProvider.class);
        this.registerProvider(Enum.class, EnumProvider.class);
        this.registerProvider(Player.class, PlayerProvider.class);
        this.registerProvider(Location.class, LocationProvider.class);
        this.registerProvider(World.class, WorldProvider.class);
        this.registerProvider(String.class, StringProvider.class);
        this.registerProvider(CommandSender.class, SenderProvider.class);
    }
}
