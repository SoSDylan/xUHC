/*
 * Plugin UHCReloaded : Alliances
 *
 * Copyright ou © ou Copr. Amaury Carrade (2016)
 * Idées et réflexions : Alexandre Prokopowicz, Amaury Carrade, "Vayan".
 *
 * Ce logiciel est régi par la licence CeCILL soumise au droit français et
 * respectant les principes de diffusion des logiciels libres. Vous pouvez
 * utiliser, modifier et/ou redistribuer ce programme sous les conditions
 * de la licence CeCILL telle que diffusée par le CEA, le CNRS et l'INRIA
 * sur le site "http://www.cecill.info".
 *
 * En contrepartie de l'accessibilité au code source et des droits de copie,
 * de modification et de redistribution accordés par cette licence, il n'est
 * offert aux utilisateurs qu'une garantie limitée.  Pour les mêmes raisons,
 * seule une responsabilité restreinte pèse sur l'auteur du programme,  le
 * titulaire des droits patrimoniaux et les concédants successifs.
 *
 * A cet égard  l'attention de l'utilisateur est attirée sur les risques
 * associés au chargement,  à l'utilisation,  à la modification et/ou au
 * développement et à la reproduction du logiciel par l'utilisateur étant
 * donné sa spécificité de logiciel libre, qui peut le rendre complexe à
 * manipuler et qui le réserve donc à des développeurs et des professionnels
 * avertis possédant  des  connaissances  informatiques approfondies.  Les
 * utilisateurs sont donc invités à charger  et  tester  l'adéquation  du
 * logiciel à leurs besoins dans des conditions permettant d'assurer la
 * sécurité de leurs systèmes et ou de leurs données et, plus généralement,
 * à l'utiliser et l'exploiter dans les mêmes conditions de sécurité.
 *
 * Le fait que vous puissiez accéder à cet en-tête signifie que vous avez
 * pris connaissance de la licence CeCILL, et que vous en avez accepté les
 * termes.
 */
package eu.carrade.amaury.UHCReloaded.modules.core.game.submanagers;

import eu.carrade.amaury.UHCReloaded.modules.core.game.Config;
import eu.carrade.amaury.UHCReloaded.modules.core.game.GameModule;
import eu.carrade.amaury.UHCReloaded.modules.core.game.GamePhase;
import eu.carrade.amaury.UHCReloaded.modules.core.game.events.game.GamePhaseChangedEvent;
import eu.carrade.amaury.UHCReloaded.modules.core.timers.TimeDelta;
import eu.carrade.amaury.UHCReloaded.shortcuts.UR;
import eu.carrade.amaury.UHCReloaded.utils.EntitiesUtils;
import fr.zcraft.zlib.components.i18n.I;
import fr.zcraft.zlib.core.ZLibComponent;
import fr.zcraft.zlib.tools.runners.RunTask;
import fr.zcraft.zlib.tools.text.ActionBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class GameBeginning extends ZLibComponent implements Listener
{
    /**
     * A flag used to disable damages at the beginning of the game to avoid fall damages and early ones.
     */
    private boolean inGracePeriod = false;

    /**
     * A flag used to disable hostile mobs spawn on the surface a few minutes after the beginning of the game.
     */
    private boolean inMobsFreePeriod = false;


    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameStarts(GamePhaseChangedEvent ev)
    {
        if (ev.getNewPhase() != GamePhase.IN_GAME) return;


        /* *** Grace period (damages disabled) *** */

        TimeDelta grace = Config.BEGINNING.GRACE_PERIOD.get();

        if (grace.getSeconds() < 15)
        {
            grace = new TimeDelta(15);
        }

        if (Config.BEGINNING.DISPLAY_GRACE_PERIOD.get())
        {
            UR.module(GameModule.class).getAliveConnectedPlayers().forEach(player ->
                    ActionBar.sendPermanentMessage(player, I.t("{green}Grace period {gray}-{green} All damages are disabled")));
        }

        inGracePeriod = true;

        RunTask.later(() -> {
            inGracePeriod = false;
            UR.module(GameModule.class).getAliveConnectedPlayers().forEach(ActionBar::removeMessage);
            Bukkit.broadcastMessage(I.t("{red}{bold}Warning!{white} The grace period ended, you are now vulnerable."));
        }, grace.getSeconds() * 20L);


        /* *** Peace period (PVP disabled) *** */

        if (Config.BEGINNING.PEACE_PERIOD.get().getSeconds() > 0)
        {
            setPVP(false);

            RunTask.later(() -> {
                setPVP(true);
                Bukkit.broadcastMessage(I.t("{red}{bold}Warning!{white} PvP is now enabled."));
            }, Config.BEGINNING.PEACE_PERIOD.get().getSeconds() * 20L);
        }

        else setPVP(true);


        /* *** Mobs-free period (mobs disabled on surface) *** */

        if (Config.BEGINNING.SURFACE_MOBS_FREE_PERIOD.get().getSeconds() > 0)
        {
            inMobsFreePeriod = true;
            RunTask.later(() -> inMobsFreePeriod = false, Config.BEGINNING.SURFACE_MOBS_FREE_PERIOD.get().getSeconds() * 20L);
        }
    }


    /**
     * Used to disable all damages if the game is not started.
     */
    @EventHandler
    public void onEntityDamage(final EntityDamageEvent ev)
    {
        if (ev.getEntity() instanceof Player && inGracePeriod)
        {
            ev.setCancelled(true);
        }
    }

    /**
     * Used to cancel the spawn of hostile entities on the surface only, at the beginning of the game.
     */
    @EventHandler
    public void onSurfaceCreatureSpawn(final CreatureSpawnEvent ev)
    {
        if (inMobsFreePeriod && EntitiesUtils.isNaturalSpawn(ev.getSpawnReason()) && EntitiesUtils.isHostile(ev.getEntityType()))
        {
            // We check the blocs above the entity to see if we only find surface blocks.
            final Location spawnLocation = ev.getLocation();
            final World world = spawnLocation.getWorld();
            final int highestBlockY = world.getHighestBlockYAt(spawnLocation);

            final int x = spawnLocation.getBlockX();
            final int z = spawnLocation.getBlockZ();

            boolean surface = true;

            for (int y = spawnLocation.getBlockY(); y <= highestBlockY; y++)
            {
                switch (world.getBlockAt(x, y, z).getType())
                {
                    // Air
                    case AIR:

                        // Trees
                    case LOG:
                    case LOG_2:
                    case LEAVES:
                    case LEAVES_2:
                    case HUGE_MUSHROOM_1:
                    case HUGE_MUSHROOM_2:

                        // Vegetation
                    case DEAD_BUSH:
                    case CROPS:
                    case GRASS:
                    case LONG_GRASS:
                    case DOUBLE_PLANT:
                    case YELLOW_FLOWER:
                    case VINE:
                    case SUGAR_CANE_BLOCK:
                    case BROWN_MUSHROOM:
                    case RED_MUSHROOM:

                        // Nature
                    case SNOW:

                        // Igloos
                    case SNOW_BLOCK:

                        // Villages
                    case WOOD:
                    case WOOD_STAIRS:
                    case SANDSTONE_STAIRS:
                    case BOOKSHELF:

                        // Redstone
                    case REDSTONE_WIRE:
                    case REDSTONE_COMPARATOR:
                    case REDSTONE_COMPARATOR_OFF:
                    case REDSTONE_COMPARATOR_ON:
                    case REDSTONE_TORCH_OFF:
                    case REDSTONE_TORCH_ON:

                        // Other blocs frequently used on surface on custom maps
                    case TORCH:
                    case RAILS:
                    case ACTIVATOR_RAIL:
                    case DETECTOR_RAIL:
                    case POWERED_RAIL:
                        break;

                    default:
                        surface = false;
                }

                if (!surface) break;
            }

            if (surface) ev.setCancelled(true);
        }
    }

    private void setPVP(final boolean pvp)
    {
        UR.get().getWorld(World.Environment.NORMAL).setPVP(pvp);
        UR.get().getWorld(World.Environment.NETHER).setPVP(pvp);
        UR.get().getWorld(World.Environment.THE_END).setPVP(pvp);
    }
}