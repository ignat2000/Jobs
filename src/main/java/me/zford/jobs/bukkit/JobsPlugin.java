/*
 * Jobs Plugin for Bukkit
 * Copyright (C) 2011  Zak Ford <zak.j.ford@gmail.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package me.zford.jobs.bukkit;

import java.util.List;

import me.zford.jobs.bukkit.config.JobConfig;
import me.zford.jobs.bukkit.config.JobsConfiguration;
import me.zford.jobs.bukkit.config.MessageConfig;
import me.zford.jobs.bukkit.economy.BufferedEconomy;
import me.zford.jobs.bukkit.listeners.JobsListener;
import me.zford.jobs.bukkit.listeners.JobsPaymentListener;
import me.zford.jobs.bukkit.tasks.BufferedPaymentRepeatableTask;
import me.zford.jobs.container.ActionInfo;
import me.zford.jobs.container.Job;
import me.zford.jobs.container.JobProgression;
import me.zford.jobs.container.JobsPlayer;
import me.zford.jobs.tasks.DatabaseSaveTask;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class JobsPlugin extends JavaPlugin {    
    private MessageConfig messageConfig = new MessageConfig(this);
    private JobsConfiguration jobsConfiguration = new JobsConfiguration(this);
    private JobConfig jobConfig = new JobConfig(this);
    private PlayerManager pManager = new PlayerManager(this);
    private BufferedEconomy economy;

    /**
     * Method called when you disable the plugin
     */
    public void onDisable() {        
        // remove all permissions for online players
        for (Player online: getServer().getOnlinePlayers()) {
            JobsPlayer jPlayer = pManager.getJobsPlayer(online.getName());
            jPlayer.removePermissions();
        }
        // kill all scheduled tasks associated to this.
        getServer().getScheduler().cancelTasks(this);
        
        pManager.saveAll();
        
        if (getJobsConfiguration().getJobsDAO() != null) {
            getJobsConfiguration().getJobsDAO().closeConnections();
        }
        
        getLogger().info("Plugin has been disabled succesfully.");
    }

    /**
     * Method called when the plugin is enabled
     */
    public void onEnable() {
        JobsCommands commands = new JobsCommands(this);
        this.getCommand("jobs").setExecutor(commands);
        
        reloadConfigurations();
        
        if(!this.isEnabled())
            return;
        
        if (!loadVault()) {
            getServer().getLogger().severe("==================== Jobs ====================");
            getServer().getLogger().severe("Vault is required by this plugin to operate!");
            getServer().getLogger().severe("Please install Vault first!");
            getServer().getLogger().severe("You can find the latest version here:");
            getServer().getLogger().severe("http://dev.bukkit.org/server-mods/vault/");
            getServer().getLogger().severe("==============================================");
            setEnabled(false);
            return;
        }
        
        // set the system to auto save
        if (getJobsConfiguration().getSavePeriod() > 0) {
            getServer().getScheduler().scheduleSyncRepeatingTask(this, new DatabaseSaveTask(pManager), 20*60*getJobsConfiguration().getSavePeriod(), 20*60*getJobsConfiguration().getSavePeriod());
        }
        
        // schedule payouts to buffered payments
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new BufferedPaymentRepeatableTask(economy), 100, 100);
        
        // register the listeners
        getServer().getPluginManager().registerEvents(new JobsListener(this), this);
        getServer().getPluginManager().registerEvents(new JobsPaymentListener(this), this);
        
        // add all online players
        for (Player online: getServer().getOnlinePlayers()){
            pManager.addPlayer(online.getName());
        }
        
        // register permissions
        reRegisterPermissions();
        
        // all loaded properly.
        getLogger().info("Plugin has been enabled succesfully.");
    }
    
    /**
     * Loads vault and sets as default economy
     */
    private boolean loadVault() {
        Plugin test = getServer().getPluginManager().getPlugin("Vault");
        if (test == null)
            return false;
        
        economy = new BufferedEconomy(this);
        
        getLogger().info("["+getDescription().getName()+"] Successfully linked with Vault.");
        return true;
    }
    
    /**
     * Disable the plugin
     */
    public void disablePlugin(){
        setEnabled(false);
    }
    
    /**
     * Returns the jobs configuration
     */
    public JobsConfiguration getJobsConfiguration() {
        return jobsConfiguration;
    }
    
    /**
     * Returns the job config
     */
    public JobConfig getJobConfig() {
        return jobConfig;
    }
    
    /**
     * Get the message configuration data
     * @return - the message configuration
     */
    public MessageConfig getMessageConfig() {
        return messageConfig;
    }
    
    /**
     * Returns player manager
     */
    public PlayerManager getPlayerManager() {
        return pManager;
    }
    
    /**
     * Reloads all configuration files
     */
    public void reloadConfigurations() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        getMessageConfig().reload();
        jobsConfiguration.reload();
        getJobConfig().reload();
    }
    
    /**
     * Check World permissions
     */
    public boolean hasWorldPermission(Permissible permissable, World world) {
        if (!permissable.hasPermission("jobs.use")) {
            return false;
        } else {
            return permissable.hasPermission("jobs.world."+world.getName().toLowerCase());
        }
    }
    
    /**
     * Check Job joining permission
     */
    public boolean hasJobPermission(Permissible permissable, Job job) {
        if (!permissable.hasPermission("jobs.use")) {
            return false;
        } else {
            return permissable.hasPermission("jobs.join."+job.getName().toLowerCase());
        }
    }
    
    /**
     * Re-Register Permissions
     */
    public void reRegisterPermissions() {
        PluginManager pm = getServer().getPluginManager();
        for (World world : getServer().getWorlds()) {
            if (pm.getPermission("jobs.world."+world.getName().toLowerCase()) == null)
                pm.addPermission(new Permission("jobs.world."+world.getName().toLowerCase(), PermissionDefault.TRUE));
        }
        for (Job job : getJobConfig().getJobs()) {
            if (pm.getPermission("jobs.join."+job.getName().toLowerCase()) == null)
                pm.addPermission(new Permission("jobs.join."+job.getName().toLowerCase(), PermissionDefault.TRUE));
        }
    }
    
    /**
     * Performed an action
     * 
     * Give correct experience and income
     * @param jPlayer - the player
     * @param action - the action
     * @param multiplier - the payment/xp multiplier
     */
    public void action(JobsPlayer jPlayer, ActionInfo info, double multiplier) {
        List<JobProgression> progression = jPlayer.getJobProgression();
        int numjobs = progression.size();
        // no job
        if (numjobs == 0) {
            Job jobNone = getJobConfig().getNoneJob();
            if (jobNone != null) {
                Double income = jobNone.getIncome(info, 1, numjobs);
                if (income != null)
                    economy.pay(jPlayer, income*multiplier);
            }
        } else {
            for (JobProgression prog : progression) {
                int level = prog.getLevel();
                Double income = prog.getJob().getIncome(info, level, numjobs);
                if (income != null) {
                    Double exp = prog.getJob().getExperience(info, level, numjobs);
                    // give income
                    economy.pay(jPlayer, income*multiplier);
                    if (prog.addExperience(exp*multiplier))
                        pManager.performLevelUp(jPlayer, prog.getJob());
                }
            }
        }
    }
}