package baegmon.blocksurvival.timer;

import baegmon.blocksurvival.BlockPlugin;
import baegmon.blocksurvival.game.Arena;
import baegmon.blocksurvival.game.GameState;
import baegmon.blocksurvival.tools.ArenaUtils;
import baegmon.blocksurvival.tools.Strings;

import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class Game extends BukkitRunnable {

    private Arena arena;
    private ArrayList<BlockState> blocks = new ArrayList<>();

    private int dropCounter = 0;

    public Game(Arena arena){
        this.arena = arena;
    }

    void start(int difficulty){

        long tick;
        if(difficulty != 10){
            tick = 20 - (difficulty * 2);
        } else {
            tick = 1L;
        }

        arena.setGameState(GameState.STARTED);
        arena.setRemaining(new HashSet<>(arena.getPlayers()));
        blocks = arena.getArenaBlocks();

        for(UUID id : arena.getPlayers()){
            Player player = Bukkit.getPlayer(id);
            player.setScoreboard(arena.generateGameScoreboard());

            // teleport players into a random position inside of the arena
            if(!ArenaUtils.insideArena(player.getLocation(), arena.getPos1(), arena.getPos2())){
                player.teleport(ArenaUtils.getRandomLocation(arena.getWorld(), arena.getPos1(), arena.getPos2()));
            }
        }

        this.runTaskTimer(BlockPlugin.getPlugin(BlockPlugin.class), 0L, tick);
    }

    @Override
    public void run() {

        if(blocks.size() > 0 && arena.getGameState() == GameState.STARTED){

            if(arena.getSnapshotBlocks().size() > dropCounter){

                BlockState state = blocks.get(ThreadLocalRandom.current().nextInt(blocks.size()));

                Location location = state.getLocation();
                location.setX(location.getBlockX() + 0.5);
                location.setZ(location.getBlockZ() + 0.5);

                blocks.remove(state);
                state.getBlock().setType(Material.AIR);

                dropCounter++;
            }
        }
    }

    public void eliminatePlayer(Player player){
        if(arena.getGameState() == GameState.STARTED && arena.getRemaining().contains(player.getUniqueId())){
            arena.getRemaining().remove(player.getUniqueId());

            player.setGameMode(GameMode.SPECTATOR);

            arena.sendMessage(Strings.PREFIX + ChatColor.WHITE + player.getDisplayName() + ChatColor.AQUA + " has been eliminated!");
            arena.updateScoreboard();

            if(arena.getRemaining().size() == 1){
                arena.setGameState(GameState.FINISHED);
                arenaComplete();
            }
        }
    }

    public void playerLeave(Player player){
        if(arena.getRemaining().size() == 1){
            arena.getRemaining().remove(player.getUniqueId());
            arena.updateScoreboard();
            arena.setGameState(GameState.FINISHED);
            arenaComplete();
        }
    }

    private void arenaComplete(){

        if(arena.getGameState() == GameState.FINISHED){

            arena.setGameState(GameState.RESTORING);
            arena.removeScoreboard();

            Player winner = Bukkit.getPlayer(arena.getRemaining().iterator().next());
            arena.sendTitle(winner.getDisplayName() + " has won!", "", 120);
            winner.setAllowFlight(true);
            winner.setFlying(true);

            Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(BlockPlugin.getPlugin(BlockPlugin.class), new Runnable(){
                int counter = 0;

                @Override
                public void run() {
                    if(counter < 3){
                        Firework firework = winner.getWorld().spawn(winner.getLocation(), Firework.class);
                        firework.setFireworkMeta(ArenaUtils.randomFireWorkEffect(firework));
                        counter++;
                    } else {
                        cancel();
                    }
                }

            }, 0, 20L);

            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(BlockPlugin.getPlugin(BlockPlugin.class), () -> {
                cancel();
                arena.reset();
                winner.setAllowFlight(false);
                winner.setFlying(false);
            }, 140L);

        }
    }
}
