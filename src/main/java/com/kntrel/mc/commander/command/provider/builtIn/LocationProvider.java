package com.kntrel.mc.commander.command.provider.builtIn;

import com.kntrel.mc.commander.command.Argument;
import com.kntrel.mc.commander.exception.CommandArgumentException;
import com.kntrel.mc.commander.exception.CommandException;
import com.kntrel.mc.commander.command.provider.CommandProvider;
import com.kntrel.mc.commander.command.provider.identify.ExcludeWorld;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LocationProvider extends CommandProvider<Location> {

    private final double[] pos_ = new double[3];
    private World world_ = null;
    private boolean excludeWorld_ = false;
    private Block block_ = null;
    private Entity entity_ = null;
    private WorldProvider worldProvider_ = null;

    @Override
    protected void onInitialization() throws CommandException {
        if (this.getCommandSender() instanceof Entity entity) {
            this.excludeWorld_ = true;
            this.entity_ = entity;
            this.world_ = entity.getWorld();
            if (entity instanceof LivingEntity livingEntity) {
                Set<Material> transparentSet = Set.of(Material.AIR, Material.WATER, Material.LAVA);
                for (Block block : livingEntity.getLineOfSight(transparentSet, 15)) {
                    if (!transparentSet.contains(block.getType())) {
                        this.block_ = block;
                    }
                }
            }
        } else if (this.getCommandSender() instanceof BlockCommandSender blockCommandSender) {
            this.excludeWorld_ = true;
            this.block_ = blockCommandSender.getBlock();
        }
        this.excludeWorld_ = this.isAnnotationPresent(ExcludeWorld.class);
        if (!excludeWorld_) {
            this.worldProvider_ = new WorldProvider();
            this.worldProvider_.initialize(this);
        }
    }

    @Override
    public List<String> suggest() {
        List<String> r = new ArrayList<>();
        Block block = null;
        if (this.getCommandSender() instanceof Player player) {
            StringBuilder builder = new StringBuilder();
            builder.append("~ ~ ~");
            builder.setLength((builder.length() - (this.getSupplyConsecutive() * 2)));
            r.add(builder.toString());

            block = (this.block_ == null) ? player.getLocation().getBlock() : this.block_;
        } else if (this.getCommandSender() instanceof BlockCommandSender blockCommandSender) {
            block = blockCommandSender.getBlock();
        }
        if (block != null) {
            String[] pos = {
                Integer.toString(block.getX()),
                Integer.toString(block.getY()),
                Integer.toString(block.getZ())
            };
            StringBuilder builder = new StringBuilder();
            for (int i = this.getSupplyConsecutive(); i < pos.length; i++) {
                builder.setLength(0);
                for (int j = this.getSupplyConsecutive(); j <= i; j++) {
                    builder.append(pos[j]).append(" ");
                }
                builder.setLength(builder.length() - 1);
                r.add(builder.toString());
            }
        }
        if (this.worldProvider_ != null && this.getSupplyConsecutive() > 2) {
            r.addAll(this.worldProvider_.suggest());
        }

        return r;
    }

    @Override
    protected boolean handleArgument(Argument argument) throws CommandException {
        if (!(argument.isDouble() || argument.isInt())) {
            throw new CommandArgumentException(argument,"Value must be numeric!");
        }

        int consecutive = this.getSupplyConsecutive();

        double relative = 0;
        if (argument.isRelative()) {
            Location loc;
            if (this.entity_ != null) {
                loc = this.entity_.getLocation();
            } else if (this.block_ != null) {
                loc = this.block_.getLocation().add(.5,.5,.5);
            } else {
                throw new CommandArgumentException(argument,"Relative coordinates are not supported here.");
            }
            relative = switch (consecutive) {
                case 1 -> loc.getX();
                case 2 -> loc.getY();
                case 3 -> loc.getZ();
                default -> 0;
            };
        }

        this.pos_[consecutive - 1] = argument.getDouble() + relative;

        if (this.excludeWorld_ && consecutive > 2) { return true; }
        if (consecutive > 3) {
            this.worldProvider_.supply(argument);
            this.world_ = this.worldProvider_.provide();
            return true;
        }
        return false;
    }

    @Override
    public Location provide() throws CommandException {
        return new Location(this.world_,this.pos_[0],this.pos_[1],this.pos_[2]);
    }
}