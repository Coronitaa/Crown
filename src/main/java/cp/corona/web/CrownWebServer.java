package cp.corona.web;

import cp.corona.crown.Crown;
import cp.corona.database.DatabaseManager;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class CrownWebServer {

    private final Crown plugin;
    private Javalin app;
    private final int port;
    private final String host;
    private final String secretToken;

    public CrownWebServer(Crown plugin, int port, String host, String secretToken) {
        this.plugin = plugin;
        this.port = port;
        this.host = host;
        this.secretToken = secretToken;
    }

    public void start() {
        try {
            app = Javalin.create(config -> {
                config.staticFiles.add("/web", Location.CLASSPATH);
                config.showJavalinBanner = false;
            });

            // Middleware for simple token authentication
            app.before("/api/*", ctx -> {
                String token = ctx.header("Authorization");
                if (secretToken != null && !secretToken.isEmpty()) {
                    if (token == null || !token.equals("Bearer " + secretToken)) {
                        ctx.status(401).result("Unauthorized");
                    }
                }
            });

            setupEndpoints();

            app.start(port);
            plugin.getLogger().info("Web Manager started on port " + port);
            Bukkit.getConsoleSender().sendMessage("§a[CROWN] Web Manager is available at: §bhttp://" + host + ":" + port);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Could not start Web Manager: " + t.getMessage(), t);
        }
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }

    private void setupEndpoints() {
        // Punishments
        app.get("/api/punishments", ctx -> {
            String type = ctx.queryParam("type");
            String target = ctx.queryParam("target");
            String moderator = ctx.queryParam("moderator");
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(100);

            ctx.json(plugin.getSoftBanDatabaseManager().getAllPunishments(limit, type, target, moderator).join());
        });

        app.get("/api/punishments/{id}", ctx -> {
            String id = ctx.pathParam("id");
            DatabaseManager.PunishmentEntry entry = plugin.getSoftBanDatabaseManager().getPunishmentById(id);
            if (entry == null) {
                ctx.status(404).result("Punishment not found");
                return;
            }
            
            Map<String, Object> details = new HashMap<>();
            details.put("punishment", entry);
            details.put("playerInfo", plugin.getSoftBanDatabaseManager().getPlayerInfo(id));
            
            ctx.json(details);
        });

        app.post("/api/punishments", this::handleCreatePunishment);

        // Moderators
        app.get("/api/moderators", ctx -> {
            // This is a bit tricky, maybe get from preferences table
            // For now, let's just return a placeholder or implement it properly
            ctx.json(new String[]{"Implement listing"});
        });

        app.get("/api/moderators/{uuid}", ctx -> {
            UUID uuid = UUID.fromString(ctx.pathParam("uuid"));
            Map<String, Object> stats = new HashMap<>();
            
            CompletableFuture<List<DatabaseManager.ModeratorSession>> sessionsFuture = plugin.getSoftBanDatabaseManager().getModeratorSessions(uuid);
            CompletableFuture<List<DatabaseManager.AuditLogEntry>> logsFuture = plugin.getSoftBanDatabaseManager().getModeratorAuditLogs(uuid);
            CompletableFuture<List<DatabaseManager.PunishmentEntry>> punishmentsFuture = plugin.getSoftBanDatabaseManager().getAllPunishments(100, null, null, uuid.toString());

            stats.put("sessions", sessionsFuture.join());
            stats.put("auditLogs", logsFuture.join());
            stats.put("punishments", punishmentsFuture.join());
            
            // Calculate playtime average
            List<DatabaseManager.ModeratorSession> sessions = (List<DatabaseManager.ModeratorSession>) stats.get("sessions");
            long totalPlaytime = 0;
            int count = 0;
            for (DatabaseManager.ModeratorSession s : sessions) {
                if (s.getLogoutAt() > 0) {
                    totalPlaytime += s.getPlaytime();
                    count++;
                }
            }
            stats.put("averagePlaytime", count > 0 ? totalPlaytime / count : 0);
            stats.put("totalPunishments", ((List<?>)stats.get("punishments")).size());

            ctx.json(stats);
        });
        
        app.get("/api/moderators/{uuid}/locker", ctx -> {
             UUID uuid = UUID.fromString(ctx.pathParam("uuid"));
             ctx.json(plugin.getSoftBanDatabaseManager().getConfiscatedItems(uuid, 1, 100).join());
        });

        // Reports
        app.get("/api/reports", ctx -> {
            String status = ctx.queryParam("status");
            String target = ctx.queryParam("target");
            String type = ctx.queryParam("type");
            int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);

            cp.corona.report.ReportStatus filterStatus = null;
            if (status != null && !status.isEmpty()) {
                try {
                    filterStatus = cp.corona.report.ReportStatus.valueOf(status.toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }

            ctx.json(plugin.getSoftBanDatabaseManager().getReports(page, limit, filterStatus, target, false, null, type).join());
        });

        app.get("/api/reports/{id}", ctx -> {
            ctx.json(plugin.getSoftBanDatabaseManager().getReportById(ctx.pathParam("id")).join());
        });

        app.post("/api/reports/{id}/status", ctx -> {
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String statusStr = body.get("status");
            String moderatorUuidStr = body.get("moderatorUuid");
            
            cp.corona.report.ReportStatus status = cp.corona.report.ReportStatus.valueOf(statusStr.toUpperCase());
            UUID moderatorUuid = moderatorUuidStr != null ? UUID.fromString(moderatorUuidStr) : null;
            
            ctx.json(Map.of("success", plugin.getSoftBanDatabaseManager().updateReportStatus(ctx.pathParam("id"), status, moderatorUuid).join()));
        });

        // Stats for dashboard
        app.get("/api/stats", ctx -> {
            Map<String, Object> stats = new HashMap<>();
            // Total punishments, active mutes, etc.
            ctx.json(stats);
        });
    }

    private void handleCreatePunishment(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String targetName = (String) body.get("target");
        String type = (String) body.get("type");
        String reason = (String) body.get("reason");
        String duration = (String) body.get("duration");
        boolean byIp = (boolean) body.getOrDefault("byIp", false);
        String adminName = (String) body.getOrDefault("adminName", "WebAdmin");

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            ctx.status(400).result("Invalid target");
            return;
        }

        // Create a custom CommandSender for the web admin
        WebAdminSender sender = new WebAdminSender(adminName);

        plugin.getPunishmentManager().issuePunishment(
                sender,
                target,
                type,
                duration,
                reason,
                byIp
        ).thenAccept(id -> {
            if (id != null) {
                ctx.status(201).json(Map.of("id", id));
            } else {
                ctx.status(500).result("Failed to create punishment or target bypassed");
            }
        });
    }

    private static class WebAdminSender implements org.bukkit.command.CommandSender {
        private final String name;

        public WebAdminSender(String name) {
            this.name = name;
        }

        @Override public void sendMessage(String message) {}
        @Override public void sendMessage(String[] messages) {}
        @Override public void sendMessage(UUID uuid, String s) {}
        @Override public void sendMessage(UUID uuid, String[] strings) {}
        @Override public String getName() { return name; }
        @Override public org.bukkit.command.CommandSender.Spigot spigot() { return null; }
        @Override public net.kyori.adventure.text.Component name() { return net.kyori.adventure.text.Component.text(name); }
        @Override public boolean isPermissionSet(String name) { return true; }
        @Override public boolean isPermissionSet(org.bukkit.permissions.Permission perm) { return true; }
        @Override public boolean hasPermission(String name) { return true; }
        @Override public boolean hasPermission(org.bukkit.permissions.Permission perm) { return true; }
        @Override public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin, String name, boolean value) { return null; }
        @Override public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin) { return null; }
        @Override public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin, String name, boolean value, int ticks) { return null; }
        @Override public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin, int ticks) { return null; }
        @Override public void removeAttachment(org.bukkit.permissions.PermissionAttachment attachment) {}
        @Override public void recalculatePermissions() {}
        @Override public java.util.Set<org.bukkit.permissions.PermissionAttachmentInfo> getEffectivePermissions() { return java.util.Collections.emptySet(); }
        @Override public boolean isOp() { return true; }
        @Override public void setOp(boolean value) {}
        @Override public org.bukkit.Server getServer() { return org.bukkit.Bukkit.getServer(); }
    }
}
