/*
 * Copyright or © or Copr. Amaury Carrade (2014 - 2016)
 *
 * http://amaury.carrade.eu
 *
 * This software is governed by the CeCILL-B license under French law and
 * abiding by the rules of distribution of free software.  You can  use,
 * modify and/ or redistribute the software under the terms of the CeCILL-B
 * license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and  rights to copy,
 * modify and redistribute granted by the license, users are provided only
 * with a limited warranty  and the software's author,  the holder of the
 * economic rights,  and the successive licensors  have only  limited
 * liability.
 *
 * In this respect, the user's attention is drawn to the risks associated
 * with loading,  using,  modifying and/or developing or reproducing the
 * software by the user in light of its specific status of free software,
 * that may mean  that it is complicated to manipulate,  and  that  also
 * therefore means  that it is reserved for developers  and  experienced
 * professionals having in-depth computer knowledge. Users are therefore
 * encouraged to load and test the software's suitability as regards their
 * requirements in conditions enabling the security of their systems and/or
 * data to be ensured and,  more generally, to use and operate it in the
 * same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-B license and that you accept its terms.
 */

package eu.carrade.amaury.UHCReloaded.teams;

import eu.carrade.amaury.UHCReloaded.UHCReloaded;
import eu.carrade.amaury.UHCReloaded.UHConfig;
import eu.carrade.amaury.UHCReloaded.utils.ColorsUtils;
import eu.carrade.amaury.UHCReloaded.utils.TextUtils;
import fr.zcraft.zlib.components.i18n.I;
import fr.zcraft.zlib.tools.items.ItemStackBuilder;
import fr.zcraft.zlib.tools.items.TextualBanners;
import fr.zcraft.zlib.tools.text.ActionBar;
import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;


public class UHTeam
{
    private static final boolean BANNER_SHAPE_WRITE_LETTER = UHConfig.TEAMS_OPTIONS.BANNER.SHAPE.WRITE_LETTER.get();
    private static final boolean BANNER_SHAPE_ADD_BORDER = UHConfig.TEAMS_OPTIONS.BANNER.SHAPE.ADD_BORDER.get();

    private static final Random random = new Random();

    private UHCReloaded plugin = UHCReloaded.get();

    private String name = null;
    private String internalName;
    private String displayName = null;
    private TeamColor color = null;
    private ItemStack defaultBanner = null;
    private ItemStack banner = null;

    private HashSet<UUID> players = new HashSet<>();


    public UHTeam(String name, TeamColor color)
    {
        Validate.notNull(name, "The name cannot be null.");

        // We use a random internal name because the name of a team, in Minecraft vanilla, is limited
        // (16 characters max).
        this.internalName = String.valueOf(random.nextInt(99999999)) + String.valueOf(random.nextInt(99999999));

        final Scoreboard sb = this.plugin.getScoreboardManager().getScoreboard();
        final Team t = sb.registerNewTeam(this.internalName);

        t.setSuffix(ChatColor.RESET.toString());
        t.setCanSeeFriendlyInvisibles(UHConfig.TEAMS_OPTIONS.CAN_SEE_FRIENDLY_INVISIBLES.get());
        t.setAllowFriendlyFire(UHConfig.TEAMS_OPTIONS.ALLOW_FRIENDLY_FIRE.get());

        setName(name, true);
        setColor(color);
        updateDefaultBanner();
    }

    /**
     * @deprecated Use {@link #UHTeam(String, TeamColor)} instead.
     */
    @Deprecated
    public UHTeam(String name, TeamColor color, UHCReloaded plugin)
    {
        this(name, color);
    }

    /**
     * Returns the name of the team.
     *
     * Can include spaces.
     *
     * @return The name.
     */
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        setName(name, false);
    }

    /**
     * Changes the name of this team.
     *
     * @param name The new name.
     * @param silent if {@code true}, the players will not be notified.
     */
    public void setName(String name, boolean silent)
    {
        if (name == null || (this.name != null && this.name.equals(name)))
            return;

        this.name = name;

        updateDisplayName();
        updateDefaultBanner();

        plugin.getTeamManager().updateGUIs();

        for (Player player : getOnlinePlayers())
        {
            if (!silent)
                player.sendMessage(I.t("{cs}Your team is now called {0}{cs}.", displayName));

            if (UHConfig.BEFORE_START.TEAM_IN_ACTION_BAR.get())
                plugin.getTeamManager().displayTeamInActionBar(player, this);
        }
    }

    /**
     * Returns the display name of the team.
     *
     * This name is:
     *  - if the team is uncolored, the name of the team;
     *  - else, the name of the team with:
     *     - before, the color of the team;
     *     - after, the "reset" formatting mark (§r).
     *
     * @return The display name.
     */
    public String getDisplayName()
    {
        return displayName;
    }

    private void updateDisplayName()
    {
        displayName = (color != null) ? color.toChatColor() + name + ChatColor.RESET : name;

        final Team t = plugin.getScoreboardManager().getScoreboard().getTeam(internalName);
        if (t != null)
            t.setDisplayName(displayName.substring(0, Math.min(displayName.length(), 32)));
    }

    /**
     * Returns the players inside this team.
     *
     * @return The players.
     */
    public Set<OfflinePlayer> getPlayers()
    {
        final Set<OfflinePlayer> playersList = new HashSet<>();

        for (UUID id : players)
        {
            final Player player = plugin.getServer().getPlayer(id);
            if (player != null)
            {
                playersList.add(player);
            }
            else
            {
                playersList.add(plugin.getServer().getOfflinePlayer(id));
            }
        }

        return playersList;
    }

    /**
     * Returns the online players inside this team.
     *
     * @return The online players.
     */
    public Set<Player> getOnlinePlayers()
    {
        HashSet<Player> playersList = new HashSet<>();

        for (UUID id : players)
        {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null && player.isOnline())
            {
                playersList.add(player);
            }
        }

        return playersList;
    }

    /**
     * Returns the UUIDs of the players inside this team.
     *
     * @return The UUIDs of the players.
     */
    @SuppressWarnings ("unchecked")
    public Set<UUID> getPlayersUUID()
    {
        return Collections.unmodifiableSet(players);
    }

    /**
     * Returns the UUIDs of the online players inside this team.
     *
     * @return The UUID of the online players.
     */
    public Set<UUID> getOnlinePlayersUUID()
    {
        HashSet<UUID> playersList = new HashSet<>();

        for (UUID id : players)
        {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null && player.isOnline())
            {
                playersList.add(id);
            }
        }

        return playersList;
    }

    /**
     * Returns the size of this team.
     *
     * @return The size.
     */
    public int getSize()
    {
        return players.size();
    }

    /**
     * Returns true if the team is empty.
     *
     * @return The emptiness.
     */
    public boolean isEmpty()
    {
        return getSize() == 0;
    }

    /**
     * Returns true if the team is full.
     *
     * @return The fullness.
     */
    public boolean isFull()
    {
        return plugin.getTeamManager().getMaxPlayersPerTeam() != 0 && getSize() >= plugin.getTeamManager().getMaxPlayersPerTeam();
    }

    /**
     * Adds a player inside this team.
     *
     * @param player The player to add.
     */
    public void addPlayer(OfflinePlayer player)
    {
        addPlayer(player, false);
    }

    /**
     * Adds a player inside this team.
     *
     * @param player The player to add.
     * @param silent If true, the player will not be notified about this.
     */
    public void addPlayer(OfflinePlayer player, boolean silent)
    {
        Validate.notNull(player, "The player cannot be null.");

        if (plugin.getTeamManager().getMaxPlayersPerTeam() != 0
                && this.players.size() >= plugin.getTeamManager().getMaxPlayersPerTeam())
        {
            throw new RuntimeException("The team " + getName() + " is full");
        }

        plugin.getTeamManager().removePlayerFromTeam(player, true);

        players.add(player.getUniqueId());
        plugin.getScoreboardManager().getScoreboard().getTeam(this.internalName).addPlayer(player);

        plugin.getTeamManager().colorizePlayer(player);

        plugin.getTeamManager().updateGUIs();

        if (player.isOnline())
        {
            if (!silent)
                ((Player) player).sendMessage(I.t("{aqua}You are now in the {0}{aqua} team.", getDisplayName()));

            if (UHConfig.BEFORE_START.TEAM_IN_ACTION_BAR.get())
                plugin.getTeamManager().displayTeamInActionBar((Player) player, this);
        }
    }

    /**
     * Removes a player from this team.
     *
     * Nothing is done if the player wasn't in this team.
     *
     * @param player The player to remove.
     */
    public void removePlayer(OfflinePlayer player)
    {
        removePlayer(player, false);
    }

    /**
     * Removes a player from this team.
     *
     * Nothing is done if the player wasn't in this team.
     *
     * @param player The player to remove.
     * @param silent If true, the player will not be notified.
     */
    public void removePlayer(OfflinePlayer player, boolean silent)
    {
        Validate.notNull(player, "The player cannot be null.");

        players.remove(player.getUniqueId());
        unregisterPlayer(player, silent);

        plugin.getTeamManager().updateGUIs();
    }

    /**
     * Unregisters a player from the scoreboard and uncolorizes the pseudo.
     *
     * Internal use, avoids a ConcurrentModificationException in this.deleteTeam()
     * (this.players is listed and emptied simultaneously, else).
     */
    private void unregisterPlayer(OfflinePlayer player, boolean silent)
    {
        if (player == null) return;

        plugin.getScoreboardManager().getScoreboard().getTeam(this.internalName).removePlayer(player);
        plugin.getTeamManager().colorizePlayer(player);

        if (player.isOnline())
        {
            if (!silent)
                ((Player) player).sendMessage(I.t("{darkaqua}You are no longer part of the {0}{darkaqua} team.", getDisplayName()));

            if (UHConfig.BEFORE_START.TEAM_IN_ACTION_BAR.get())
                ActionBar.removeMessage((Player) player, true);
        }
    }

    /**
     * Deletes this team.
     *
     * The players inside the team are left without any team.
     */
    public void deleteTeam()
    {
        // We removes the players from the team (scoreboard team too)
        players.forEach(id -> unregisterPlayer(plugin.getServer().getOfflinePlayer(id), false));

        players.clear();

        // Then the scoreboard team is deleted.
        plugin.getScoreboardManager().getScoreboard().getTeam(this.internalName).unregister();
    }

    /**
     * Returns true if the given player is in this team.
     *
     * @param player The player to check.
     * @return true if the given player is in this team.
     */
    public boolean containsPlayer(Player player)
    {
        Validate.notNull(player, "The player cannot be null.");

        return players.contains(player.getUniqueId());
    }

    /**
     * Returns true if the player with the given UUID is in this team.
     *
     * @param id The UUID of the player to check.
     * @return true if the given player is in this team.
     */
    public boolean containsPlayer(UUID id)
    {
        Validate.notNull(id, "The player cannot be null.");

        return players.contains(id);
    }

    /**
     * Teleports the entire team to the given location.
     *
     * @param location The location.
     */
    public void teleportTo(Location location)
    {
        Validate.notNull(location, "The location cannot be null.");

        players.stream()
                .map(id -> plugin.getServer().getPlayer(id))
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .forEach(player -> player.teleport(location, TeleportCause.PLUGIN));
    }

    /**
     * @return the color of the team.
     */
    public TeamColor getColor()
    {
        return color;
    }

    /**
     * @return the color of the team, or white if the color is set to null. Never returns {@code null}.
     */
    public TeamColor getColorOrWhite()
    {
        return color != null ? color : TeamColor.WHITE;
    }

    /**
     * Updates the team color.
     *
     * @param color The new color.
     */
    public void setColor(TeamColor color)
    {
        // We don't use generateColor directly because we want to keep the "null" color.
        if (color == TeamColor.RANDOM) this.color = plugin.getTeamManager().generateColor(color);
        else this.color = color;

        updateDisplayName();

        // The team color needs to be updated
        if (this.color != null)
        {
            Team t = plugin.getScoreboardManager().getScoreboard().getTeam(internalName);
            if (t != null)
                t.setPrefix(this.color.toChatColor().toString());
        }

        // The players names too
        for (Player player : getOnlinePlayers())
        {
            plugin.getTeamManager().colorizePlayer(player);

            // Also we update the action bar if needed
            if (UHConfig.BEFORE_START.TEAM_IN_ACTION_BAR.get())
                plugin.getTeamManager().displayTeamInActionBar(player, this);
        }

        // The default banner too
        updateDefaultBanner();

        plugin.getTeamManager().updateGUIs();
    }


    /**
     * Generates and return the default banner for this team, following the
     * banners options in the configuration file.
     *
     * @return the generated banner.
     */
    public ItemStack getDefaultBanner()
    {
        final ItemStack banner;
        final DyeColor dye = ColorsUtils.chat2Dye(getColorOrWhite().toChatColor());

        if (BANNER_SHAPE_WRITE_LETTER)
        {
            banner = TextualBanners.getCharBanner(Character.toUpperCase(TextUtils.getInitialLetter(name)), dye, BANNER_SHAPE_ADD_BORDER);
        }
        else
        {
            banner = new ItemStack(Material.BANNER);
            BannerMeta meta = (BannerMeta) banner.getItemMeta();
            meta.setBaseColor(dye);
            banner.setItemMeta(meta);
        }

        return banner;
    }

    /**
     * Regenerates the default banner.
     */
    private void updateDefaultBanner()
    {
        // Avoid updating in the constructor before all the object is populated.
        if (name != null)
            defaultBanner = getDefaultBanner();
        else
            defaultBanner = new ItemStack(Material.BANNER);
    }

    /**
     * Updates this team's banner.
     *
     * @param banner The new banner. {@code null} to use the default banner.
     */
    public void setBanner(ItemStack banner)
    {
        if (banner == null)
        {
            this.banner = null;
            return;
        }

        if (banner.getType() != Material.BANNER)
            throw new IllegalArgumentException("A banner is required");

        this.banner = new ItemStackBuilder(banner.clone())
                .title(displayName)
                .amount(1)
                .hideAttributes()
                .item();

        if (banner.hasItemMeta())
        {
            BannerMeta meta = (BannerMeta) this.banner.getItemMeta();
            meta.setBaseColor(((BannerMeta) banner.getItemMeta()).getBaseColor());
            this.banner.setItemMeta(meta);
        }

        plugin.getTeamManager().updateGUIs();
    }
    
    /**
     * Updates this team's banner.
     *
     * @param banner The new banner. {@code null} to use the default banner.
     */
    public void setBanner(BannerMeta banner)
    {
        if (banner == null)
        {
            this.banner = null;
            return;
        }

        this.banner = new ItemStackBuilder(Material.BANNER)
                .title(displayName)
                .amount(1)
                .hideAttributes()
                .item();

        this.banner.setItemMeta(banner.clone());

        plugin.getTeamManager().updateGUIs();
    }

    /**
     * Returns this team's banner.
     *
     * @return the banner.
     */
    public ItemStack getBanner()
    {
        return banner == null ? defaultBanner : banner;
    }


    @Override
    public int hashCode()
    {
        return ((name == null) ? 0 : name.hashCode());
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof UHTeam))
            return false;

        final UHTeam other = (UHTeam) obj;
        return name == null ? other.name == null : name.equals(other.name);
    }
}
