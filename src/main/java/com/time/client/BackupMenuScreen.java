package com.time.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BackupMenuScreen extends Screen {
    private static final DateTimeFormatter BACKUP_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private final Screen previousScreen;
    private Component statusMessage = Component.literal("Select a backup to restore, or create a new one.");
    private BackupSelectionList backupSelectionList;
    private EditBox backupNameBox;

    public BackupMenuScreen(Screen previousScreen) {
        super(Component.literal("World Backup"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        super.init();

        int listTop = 54;
        int listBottom = this.height - 48;
        this.backupSelectionList = new BackupSelectionList(this.minecraft, this.width - 40, listTop, listBottom, 24);
        this.backupSelectionList.setLeftPos(20);
        this.addRenderableWidget(this.backupSelectionList);

        this.backupNameBox = new EditBox(this.font, (this.width / 2) - 150, 24, 196, 20, Component.literal("Backup name"));
        this.backupNameBox.setMaxLength(64);
        this.backupNameBox.setHint(Component.literal("Backup name"));
        this.addRenderableWidget(this.backupNameBox);

        refreshBackupList();

        this.addRenderableWidget(Button.builder(Component.literal("Create Backup"), button -> backupCurrentWorld())
                .bounds((this.width / 2) + 52, 24, 100, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds((this.width / 2) + 52, this.height - 24, 100, 20)
                .build());
    }

    private void refreshBackupList() {
        this.backupSelectionList.replaceEntries(getBackupDirectoriesForCurrentWorld());
    }

    private List<Path> getBackupDirectoriesForCurrentWorld() {
        Path backupsDirectory = getCurrentWorldBackupsDirectory();
        if (backupsDirectory == null || !Files.isDirectory(backupsDirectory)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.list(backupsDirectory)) {
            return paths
                    .filter(Files::isDirectory)
                    .filter(BackupMenuScreen::isValidWorldBackup)
                    .sorted(Comparator.comparing(BackupMenuScreen::lastModifiedOrMin).reversed())
                    .collect(Collectors.toList());
        } catch (IOException exception) {
            setStatus(Component.literal("Could not read backups: " + exception.getMessage()).withStyle(ChatFormatting.RED));
            return List.of();
        }
    }

    private void backupCurrentWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            setStatus(Component.literal("Open a singleplayer world before creating a backup.").withStyle(ChatFormatting.RED));
            return;
        }

        try {
            server.saveEverything(true, false, true);

            Path worldFolder = getCurrentWorldFolder(server);
            if (worldFolder == null) {
                return;
            }

            Path backupsDirectory = getWorldBackupsDirectory(worldFolder);
            Files.createDirectories(backupsDirectory);

            String backupFolderName = buildBackupFolderName();
            Path backupPath = backupsDirectory.resolve(backupFolderName);
            copyDirectory(worldFolder, backupPath, true);

            refreshBackupList();
            setStatus(Component.literal("Backup created: " + backupFolderName).withStyle(ChatFormatting.GREEN));
        } catch (IOException exception) {
            setStatus(Component.literal("Backup failed: " + exception.getMessage()).withStyle(ChatFormatting.RED));
        }
    }

    private void restoreBackup(Path backupDirectory) {
        Minecraft minecraft = Minecraft.getInstance();
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            setStatus(Component.literal("Open a singleplayer world before restoring a backup.").withStyle(ChatFormatting.RED));
            return;
        }

        try {
            Path worldFolder = getCurrentWorldFolder(server);
            if (worldFolder == null) {
                return;
            }

            String worldId = worldFolder.getFileName().toString();
            Component loadingMessage = Component.literal("Restoring backup and reloading world...");
            minecraft.setScreen(new GenericDirtMessageScreen(loadingMessage));

            Thread restoreThread = new Thread(() -> restoreAndReloadWorld(minecraft, server, worldFolder, worldId, backupDirectory), "time_machine-backup-restore");
            restoreThread.setDaemon(true);
            restoreThread.start();
        } catch (Exception exception) {
            setStatus(Component.literal("Restore failed: " + exception.getMessage()).withStyle(ChatFormatting.RED));
        }
    }

    private void restoreAndReloadWorld(Minecraft minecraft, MinecraftServer server, Path worldFolder, String worldId, Path backupDirectory) {
        try {
            minecraft.execute(() -> {
                if (minecraft.level != null) {
                    minecraft.level.disconnect();
                }
                minecraft.clearLevel(new GenericDirtMessageScreen(Component.literal("Unloading world...")));
            });
            waitForWorldUnload(minecraft, 10000L);

            clearDirectoryContents(worldFolder);
            copyDirectory(backupDirectory, worldFolder, true);

            minecraft.execute(() -> minecraft.createWorldOpenFlows().loadLevel(new GenericDirtMessageScreen(Component.literal("Reloading world...")), worldId));
        } catch (Exception exception) {
            minecraft.execute(() -> minecraft.setScreen(new GenericDirtMessageScreen(Component.literal("Restore failed: " + exception.getMessage()))));
        }
    }

    private Path getCurrentWorldFolder(MinecraftServer server) {
        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        Path worldFolder = resolveWorldFolder(worldPath);
        if (worldFolder == null || !Files.exists(worldFolder)) {
            setStatus(Component.literal("Could not resolve the current world folder.").withStyle(ChatFormatting.RED));
            return null;
        }
        return worldFolder.normalize();
    }

    private Path getCurrentWorldBackupsDirectory() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return null;
        }

        Path worldFolder = getCurrentWorldFolder(server);
        if (worldFolder == null) {
            return null;
        }

        return getWorldBackupsDirectory(worldFolder);
    }

    private Path getWorldBackupsDirectory(Path worldFolder) {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("backups")
                .resolve(sanitizePathSegment(worldFolder.getFileName().toString()));
    }

    private String buildBackupFolderName() {
        String typedName = this.backupNameBox != null ? this.backupNameBox.getValue().trim() : "";
        String safeName = sanitizePathSegment(typedName);
        String timestamp = BACKUP_TIME_FORMAT.format(LocalDateTime.now());
        return safeName.isEmpty() ? timestamp : safeName + "_" + timestamp;
    }

    private void setStatus(Component message) {
        this.statusMessage = message;
    }

    private static Path resolveWorldFolder(Path worldPath) {
        Path normalizedWorldPath = worldPath.normalize();
        if (Files.exists(normalizedWorldPath.resolve("level.dat"))) {
            return normalizedWorldPath;
        }

        if (Files.exists(worldPath.resolve("level.dat"))) {
            return worldPath;
        }

        Path parent = worldPath.getParent();
        if (parent != null && Files.exists(parent.resolve("level.dat"))) {
            return parent.normalize();
        }

        return normalizedWorldPath;
    }

    private static boolean isValidWorldBackup(Path path) {
        return Files.exists(path.resolve("level.dat"));
    }

    private static String sanitizePathSegment(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
        String sanitized = normalized.replaceAll("[\\\\/:*?\"<>|]", "_");
        sanitized = sanitized.replaceAll("\\s+", " ");
        if (".".equals(sanitized) || "..".equals(sanitized)) {
            return "";
        }
        return sanitized;
    }

    private static void clearDirectoryContents(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> toDelete = paths
                    .filter(path -> !path.equals(directory))
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());

            for (Path path : toDelete) {
                if ("session.lock".equals(path.getFileName().toString())) {
                    continue;
                }

                Files.deleteIfExists(path);
            }
        }
    }

    private static void waitForWorldUnload(Minecraft minecraft, long timeoutMs) throws InterruptedException, IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (minecraft.hasSingleplayerServer() || minecraft.level != null) {
            if (System.currentTimeMillis() >= deadline) {
                throw new IOException("Timed out while unloading the current world.");
            }
            Thread.sleep(50L);
        }
    }

    private static void copyDirectory(Path source, Path target, boolean skipSessionLock) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.naturalOrder())::iterator) {
                Path relativePath = source.relativize(path);
                Path destination = target.resolve(relativePath);

                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else if (skipSessionLock && "session.lock".equals(path.getFileName().toString())) {
                    continue;
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static LocalDateTime lastModifiedOrMin(Path path) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), java.time.ZoneId.systemDefault());
        } catch (IOException exception) {
            return LocalDateTime.MIN;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.literal("Backups"), this.width / 2, 58, 0xFFFFFF);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        List<FormattedCharSequence> lines = this.font.split(this.statusMessage, this.width - 40);
        int textY = this.height - 36;
        for (FormattedCharSequence line : lines) {
            guiGraphics.drawCenteredString(this.font, line, this.width / 2, textY, 0xD0D0D0);
            textY += this.font.lineHeight + 2;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.previousScreen);
    }

    private final class BackupSelectionList extends ObjectSelectionList<BackupEntry> {
        private BackupSelectionList(Minecraft minecraft, int width, int top, int bottom, int itemHeight) {
            super(minecraft, width, bottom - top, top, bottom, itemHeight);
        }

        private void replaceEntries(List<Path> backupDirectories) {
            super.clearEntries();
            for (Path backupDirectory : backupDirectories) {
                super.addEntry(new BackupEntry(backupDirectory));
            }
        }

        @Override
        public int getRowWidth() {
            return this.width - 12;
        }
    }

    private final class BackupEntry extends ObjectSelectionList.Entry<BackupEntry> {
        private final Path backupDirectory;

        private BackupEntry(Path backupDirectory) {
            this.backupDirectory = backupDirectory;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int color = hovered ? 0xFFFFFF : 0xD0D0D0;
            guiGraphics.drawString(BackupMenuScreen.this.font, this.backupDirectory.getFileName().toString(), left + 4, top + 8, color);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                BackupMenuScreen.this.backupSelectionList.setSelected(this);
                BackupMenuScreen.this.restoreBackup(this.backupDirectory);
                return true;
            }

            return false;
        }

        @Override
        public Component getNarration() {
            return Component.literal(this.backupDirectory.getFileName().toString());
        }
    }
}
