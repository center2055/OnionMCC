package com.onionmcc.launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.onionmcc.launcher.ipc.IPCClient;
import com.sun.tools.attach.VirtualMachineDescriptor;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.*;

/**
 * OnionMCC External Launcher — Modern dark-themed JavaFX application.
 * Provides the external GUI for managing the injected client.
 */
public class LauncherApp extends Application {

    // ── Services ─────────────────────────────────────────────────────
    private final InjectorService injector = new InjectorService();
    private final IPCClient ipcClient = new IPCClient();

    private String selectedCategory = "Combat";
    private final Map<String, JsonObject> moduleStates = new LinkedHashMap<>();
    private VBox moduleListContainer;
    private Label categoryHeader;
    private Label categoryDesc;
    private Label statusLabel;
    private Circle statusDot;
    private Label versionLabel;
    private TextArea consoleArea;
    private String expectedPid;
    private int expectedIpcPort = 47891;

    // ── Colors ───────────────────────────────────────────────────────
    private static final String BG_DARK = "#070711";
    private static final String BG_PANEL = "#0B0B16";
    private static final String BG_SURFACE = "#101023";
    private static final String BG_CARD = "#151529";
    private static final String BG_CARD_HOVER = "#1A1A30";
    private static final String BG_ELEVATED = "#19192B";
    private static final String BG_SETTINGS = "#0E0E1C";
    private static final String ACCENT = "#8B3DFF";
    private static final String ACCENT_LIGHT = "#A970FF";
    private static final String ACCENT_GLOW = "#8B3DFF33";
    private static final String ACCENT_DIM = "#5B21B6";
    private static final String TEXT_PRIMARY = "#F3F1FF";
    private static final String TEXT_SECONDARY = "#9BA4C8";
    private static final String TEXT_DIM = "#505670";
    private static final String TOGGLE_ON = "#8B3DFF";
    private static final String DANGER = "#FF4D5E";
    private static final String BORDER = "rgba(140,120,255,0.22)";
    private static final String BORDER_SOLID = "#2D2854";

    // ── Category icons ───────────────────────────────────────────────────
    private static final List<String> CATEGORIES = Arrays.asList(
            "Combat", "Movement", "Render", "Player", "Utility");

    @Override
    public void start(Stage primaryStage) {
        try {
            primaryStage.getIcons().add(new javafx.scene.image.Image(getClass().getResourceAsStream("/assets/logo.png")));
        } catch (Exception ignored) {}
        
        primaryStage.initStyle(StageStyle.TRANSPARENT);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG_DARK + "; -fx-background-radius: 10; -fx-border-color: " + BORDER + "; -fx-border-radius: 10; -fx-border-width: 1;");

        // ── Title Bar ────────────────────────────────────────────────
        root.setTop(createTitleBar(primaryStage));
        root.setLeft(createSidebar());
        root.setCenter(createCenterPanel());
        root.setBottom(createBottomPanel());

        Scene scene = new Scene(root, 1150, 750);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(createStyleSheet());

        primaryStage.setScene(scene);
        primaryStage.setTitle("OnionMCC");
        primaryStage.show();

        root.setEffect(new DropShadow(25, Color.web(ACCENT_GLOW)));

        setupIPCListener();

        ipcClient.setOnDisconnect(() -> {
            updateStatus(false, "Disconnected");
            Platform.runLater(() -> {
                refreshModuleList();
            });
        });

        loadDefaultModules();
        startAutoInjector();

        log("OnionMCC Launcher v1.0.0 ready.");
        log("Waiting for Minecraft process to auto-inject...");
    }

    private void startAutoInjector() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(3000);
                    if (!ipcClient.isConnected() && !injector.isInjected()) {
                        var procs = injector.findMinecraftProcesses();
                        if (!procs.isEmpty()) {
                            doAutoInject(procs.get(0));
                        }
                    }
                } catch (InterruptedException ignored) {}
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private synchronized void doAutoInject(VirtualMachineDescriptor proc) {
        String pid = proc.id();
        String displayName = proc.displayName();
        if (displayName.length() > 50) displayName = displayName.substring(0, 50) + "...";
        
        log("Auto-injecting into: " + displayName + " (PID " + pid + ")");
        expectedPid = pid;
        expectedIpcPort = InjectorService.computeIpcPort(pid);

        var agentJar = injector.findAgentJar();
        if (agentJar.isEmpty()) {
            log("ERROR: Agent JAR not found. Build the project first.");
            return;
        }

        Thread injectThread = new Thread(() -> {
            boolean success = injector.inject(pid, agentJar.get(), expectedIpcPort);
            Platform.runLater(() -> {
                if (success) {
                    log("\u2713 Injection successful!");
                    updateStatus(true, "Injected");

                    new Thread(() -> {
                        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                        boolean ipcOk = false;
                        int connectedPort = -1;
                        int[] candidatePorts = expectedIpcPort == 47891 ? new int[] { expectedIpcPort } : new int[] { expectedIpcPort, 47891 };
                        for (int attempt = 1; attempt <= 90 && !ipcOk; attempt++) {
                            for (int port : candidatePorts) {
                                if (ipcClient.connect(port)) {
                                    ipcOk = true;
                                    connectedPort = port;
                                    break;
                                }
                            }
                            if (!ipcOk) {
                                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                            }
                        }
                        final boolean ipcConnected = ipcOk;
                        final int ipcConnectedPort = connectedPort;
                        Platform.runLater(() -> {
                            if (ipcConnected) {
                                updateStatus(true, "Injected");
                                ipcClient.requestState();
                                log("✓ IPC connected on port " + ipcConnectedPort + ". Syncing module states...");
                                pushLocalConfigToIPC();
                            } else {
                                log("IPC connection failed on port " + expectedIpcPort + ".");
                                updateStatus(false, "IPC Failed");
                                injector.detach();
                            }
                        });
                    }).start();
                } else {
                    log("\u2717 Injection failed.");
                    showAgentLogTail();
                }
            });
        });
        injectThread.setDaemon(true);
        injectThread.start();
    }

    private void updateStatus(boolean connected, String text) {
        Platform.runLater(() -> {
            String color = connected ? "#22c55e" : DANGER;
            statusDot.setFill(Color.web(color));
            statusLabel.setText(text);
            statusLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 10; -fx-font-weight: bold;");
            statusDot.getParent().setStyle("-fx-background-color: " + color + "15; -fx-padding: 0 16; -fx-background-radius: 16; -fx-border-color: " + color + "33; -fx-border-radius: 16; -fx-border-width: 1;");
        });
    }

    private void pushLocalConfigToIPC() {
        for (Map.Entry<String, JsonObject> entry : moduleStates.entrySet()) {
            String name = entry.getKey();
            JsonObject mod = entry.getValue();
            if (mod.has("enabled") && mod.get("enabled").getAsBoolean()) {
                ipcClient.toggleModule(name); 
            }
            if (mod.has("settings")) {
                JsonObject settings = mod.getAsJsonObject("settings");
                for (String key : settings.keySet()) {
                    JsonElement val = settings.get(key);
                    if (val.isJsonPrimitive()) {
                        if (val.getAsJsonPrimitive().isBoolean()) {
                            ipcClient.updateSetting(name, key, val.getAsBoolean());
                        }
                    } else if (val.isJsonObject() && val.getAsJsonObject().has("value")) {
                        ipcClient.updateSetting(name, key, val.getAsJsonObject().get("value").getAsDouble());
                    }
                }
            }
        }
    }

    private String createStyleSheet() {
        String css = """
                .scroll-pane {
                    -fx-background-color: transparent;
                    -fx-background: transparent;
                }
                .scroll-pane .viewport { -fx-background-color: transparent; }
                .scroll-bar:vertical, .scroll-bar:horizontal { -fx-background-color: transparent; }
                .scroll-bar:vertical .track, .scroll-bar:horizontal .track {
                    -fx-background-color: transparent;
                    -fx-border-color: transparent;
                }
                .scroll-bar .increment-button, .scroll-bar .decrement-button {
                    -fx-background-color: transparent;
                    -fx-padding: 0;
                }
                .scroll-bar .increment-arrow, .scroll-bar .decrement-arrow {
                    -fx-shape: "";
                    -fx-padding: 0;
                }
                .scroll-bar:vertical .thumb, .scroll-bar:horizontal .thumb {
                    -fx-background-color: #2A2648;
                    -fx-background-radius: 3;
                    -fx-pref-width: 6;
                }
                .scroll-bar:vertical .thumb:hover, .scroll-bar:horizontal .thumb:hover {
                    -fx-background-color: #3d3868;
                }
                .scroll-bar:vertical { -fx-pref-width: 8; }
                .check-box {
                    -fx-text-fill: %s;
                }
                .check-box .box {
                    -fx-background-color: %s;
                    -fx-border-color: %s;
                    -fx-border-radius: 4;
                    -fx-background-radius: 4;
                    -fx-padding: 3;
                }
                .check-box:selected .box {
                    -fx-background-color: %s;
                    -fx-border-color: %s;
                }
                .check-box:selected .mark {
                    -fx-background-color: white;
                }
                .slider .track {
                    -fx-background-color: %s;
                    -fx-pref-height: 3;
                    -fx-background-radius: 3;
                }
                .slider .thumb {
                    -fx-background-color: %s;
                    -fx-background-radius: 50%%;
                    -fx-effect: dropshadow(gaussian, #00000066, 4, 0, 0, 1);
                }
                .combo-box {
                    -fx-background-color: %s;
                    -fx-border-color: %s;
                    -fx-border-radius: 6;
                    -fx-background-radius: 6;
                }
                .combo-box .list-cell {
                    -fx-text-fill: %s;
                    -fx-background-color: transparent;
                }
                .combo-box .arrow-button {
                    -fx-background-color: transparent;
                }
                .combo-box .arrow {
                    -fx-background-color: %s;
                }
                .combo-box-popup .list-view {
                    -fx-background-color: %s;
                    -fx-border-color: %s;
                    -fx-border-radius: 4;
                    -fx-background-radius: 4;
                }
                .combo-box-popup .list-cell {
                    -fx-background-color: transparent;
                    -fx-text-fill: %s;
                    -fx-padding: 6 12;
                    -fx-font-size: 12;
                }
                .combo-box-popup .list-cell:hover {
                    -fx-background-color: %s;
                    -fx-text-fill: %s;
                }
                """.formatted(
                    TEXT_PRIMARY,
                    BG_SURFACE, BORDER_SOLID,
                    ACCENT_DIM, ACCENT,
                    BORDER_SOLID,
                    ACCENT_LIGHT,
                    BG_SURFACE, BORDER_SOLID,
                    ACCENT_LIGHT,
                    TEXT_DIM,
                    BG_CARD, BORDER_SOLID,
                    TEXT_SECONDARY,
                    BG_CARD_HOVER, TEXT_PRIMARY
                );

        try {
            Path tempCss = Files.createTempFile("onion_style", ".css");
            Files.writeString(tempCss, css, StandardCharsets.UTF_8);
            tempCss.toFile().deleteOnExit();
            return tempCss.toUri().toString();
        } catch (Exception e) {
            String encoded = Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
            return "data:text/css;base64," + encoded;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ── Title Bar ──────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════

    private HBox createTitleBar(Stage stage) {
        HBox titleBar = new HBox(0);
        titleBar.setPadding(new Insets(0, 16, 0, 20));
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPrefHeight(48);
        titleBar.setStyle("-fx-background-color: " + BG_PANEL + "; -fx-border-color: " + BORDER_SOLID + "; -fx-border-width: 0 0 1 0;");

        HBox logoBox = new HBox(8);
        logoBox.setAlignment(Pos.CENTER_LEFT);
        
        try {
            javafx.scene.image.Image logoImg = new javafx.scene.image.Image(getClass().getResourceAsStream("/assets/logo.png"));
            javafx.scene.image.ImageView logoView = new javafx.scene.image.ImageView(logoImg);
            logoView.setFitHeight(28);
            logoView.setFitWidth(28);
            logoView.setPreserveRatio(true);
            logoView.setBlendMode(javafx.scene.effect.BlendMode.SCREEN);
            logoBox.getChildren().add(logoView);
        } catch (Exception e) {
            System.err.println("Failed to load logo: " + e.getMessage());
        }

        Text logoText = new Text("OnionMCC");
        logoText.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        logoText.setFill(Color.web(TEXT_PRIMARY));
        logoBox.getChildren().add(logoText);

        // Separator
        javafx.scene.shape.Rectangle sep1 = new javafx.scene.shape.Rectangle(1, 20);
        sep1.setFill(Color.web(BORDER_SOLID));
        HBox.setMargin(sep1, new Insets(14, 14, 14, 14));

        statusDot = new Circle(3.5);
        statusDot.setFill(Color.web(DANGER));
        statusLabel = new Label("Disconnected");
        statusLabel.setStyle("-fx-text-fill: " + DANGER + "; -fx-font-size: 10; -fx-font-weight: bold;");
        statusLabel.setPadding(new Insets(1, 0, 0, 0));

        HBox statusBox = new HBox(8, statusDot, statusLabel);
        statusBox.setAlignment(Pos.CENTER);
        statusBox.setMaxHeight(26);
        statusBox.setMinHeight(26);
        statusBox.setStyle("-fx-background-color: #ff4d5e15; -fx-padding: 0 16; -fx-background-radius: 16; -fx-border-color: #ff4d5e33; -fx-border-radius: 16; -fx-border-width: 1;");
        HBox.setMargin(statusBox, new Insets(11, 0, 11, 0));

        javafx.scene.shape.Rectangle sep2 = new javafx.scene.shape.Rectangle(1, 20);
        sep2.setFill(Color.web(BORDER_SOLID));
        HBox.setMargin(sep2, new Insets(14, 14, 14, 14));

        versionLabel = new Label("v1.0.0");
        versionLabel.setStyle("-fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 11;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minimizeBtn = createWindowButton("_");
        minimizeBtn.setOnAction(e -> stage.setIconified(true));
        Button maxBtn = createWindowButton("[ ]");
        maxBtn.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
        Button closeBtn = createWindowButton("X");
        closeBtn.setOnAction(e -> {
            ipcClient.disconnect();
            injector.detach();
            Platform.exit();
        });

        titleBar.getChildren().addAll(logoBox, sep1, statusBox, sep2, versionLabel, spacer, minimizeBtn, maxBtn, closeBtn);

        final double[] dragDelta = new double[2];
        titleBar.setOnMousePressed(e -> {
            dragDelta[0] = stage.getX() - e.getScreenX();
            dragDelta[1] = stage.getY() - e.getScreenY();
        });
        titleBar.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() + dragDelta[0]);
            stage.setY(e.getScreenY() + dragDelta[1]);
        });

        return titleBar;
    }

    private Button createWindowButton(String text) {
        Button btn = new Button(text);
        btn.setStyle("""
                    -fx-background-color: transparent;
                    -fx-text-fill: %s;
                    -fx-font-size: 13;
                    -fx-cursor: hand;
                    -fx-padding: 4 10;
                    -fx-min-width: 32;
                """.formatted(TEXT_DIM));
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle().replace(TEXT_DIM, TEXT_PRIMARY)));
        btn.setOnMouseExited(e -> btn.setStyle(btn.getStyle().replace(TEXT_PRIMARY, TEXT_DIM)));
        return btn;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ── Sidebar ────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════

    private VBox createSidebar() {
        VBox sidebar = new VBox(2);
        sidebar.setPadding(new Insets(14, 8, 14, 0));
        sidebar.setPrefWidth(175);
        sidebar.setMaxHeight(Double.MAX_VALUE);
        sidebar.setStyle("-fx-background-color: linear-gradient(to bottom, #0D0D19, #0A0A14); -fx-border-color: " + BORDER_SOLID + "; -fx-border-width: 0 1 0 0;");

        Label catTitle = new Label("MODULES");
        catTitle.setStyle("-fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 9; -fx-font-weight: bold;");
        catTitle.setPadding(new Insets(2, 0, 6, 16));

        VBox categoryButtons = new VBox(2);
        categoryButtons.setId("categoryButtons");
        for (String category : CATEGORIES) {
            categoryButtons.getChildren().add(createCategoryButton(category));
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button loadBtn = new Button("Load Config");
        styleConfigButton(loadBtn);
        loadBtn.setOnAction(e -> loadConfigLocally());

        Button saveBtn = new Button("Save Config");
        styleConfigButton(saveBtn);
        saveBtn.setOnAction(e -> saveConfigLocally());

        VBox configBox = new VBox(6);
        configBox.setPadding(new Insets(0, 0, 0, 8));
        configBox.getChildren().addAll(loadBtn, saveBtn);

        sidebar.getChildren().addAll(catTitle, categoryButtons, spacer, configBox);
        return sidebar;
    }

    private void styleConfigButton(Button btn) {
        btn.setPrefWidth(158);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setStyle("-fx-background-color: " + BG_SURFACE + "; -fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 11; -fx-padding: 7 12; -fx-background-radius: 7; -fx-border-color: " + BORDER_SOLID + "; -fx-border-radius: 7; -fx-border-width: 1; -fx-cursor: hand;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: " + BG_CARD + "; -fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 11; -fx-padding: 7 12; -fx-background-radius: 7; -fx-border-color: " + BORDER_SOLID + "; -fx-border-radius: 7; -fx-border-width: 1; -fx-cursor: hand;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: " + BG_SURFACE + "; -fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 11; -fx-padding: 7 12; -fx-background-radius: 7; -fx-border-color: " + BORDER_SOLID + "; -fx-border-radius: 7; -fx-border-width: 1; -fx-cursor: hand;"));
    }

    private Button createCategoryButton(String category) {
        Button btn = new Button("  " + category);
        try {
            javafx.scene.image.Image iconImg = new javafx.scene.image.Image(getClass().getResourceAsStream("/assets/icons/" + category.toLowerCase() + ".png"));
            javafx.scene.image.ImageView iconView = new javafx.scene.image.ImageView(iconImg);
            iconView.setFitHeight(20);
            iconView.setFitWidth(20);
            iconView.setPreserveRatio(true);
            btn.setGraphic(iconView);
        } catch (Exception e) {
            btn.setText("•  " + category);
        }
        btn.setMinHeight(50);
        btn.setPrefHeight(50);
        btn.setMaxHeight(50);
        btn.setPrefWidth(167);
        btn.setAlignment(Pos.CENTER_LEFT);

        boolean isSelected = category.equals(selectedCategory);

        if (isSelected) {
            btn.setStyle("""
                        -fx-background-color: linear-gradient(to right, #8B3DFF33, #151529);
                        -fx-text-fill: #ffffff;
                        -fx-font-size: 12.5;
                        -fx-padding: 0 14;
                        -fx-background-radius: 0 8 8 0;
                        -fx-border-color: #8B3DFF;
                        -fx-border-width: 0 0 0 3;
                        -fx-border-radius: 0 8 8 0;
                        -fx-cursor: hand;
                        -fx-font-weight: bold;
                    """);
        } else {
            btn.setStyle("""
                        -fx-background-color: transparent;
                        -fx-text-fill: %s;
                        -fx-font-size: 12.5;
                        -fx-padding: 0 14;
                        -fx-background-radius: 0 8 8 0;
                        -fx-cursor: hand;
                        -fx-font-weight: normal;
                    """.formatted(TEXT_SECONDARY));
        }

        btn.setOnMouseEntered(e -> {
            if (!category.equals(selectedCategory)) {
                btn.setStyle("""
                            -fx-background-color: %s;
                            -fx-text-fill: %s;
                            -fx-font-size: 12.5;
                            -fx-padding: 0 14;
                            -fx-background-radius: 0 8 8 0;
                            -fx-cursor: hand;
                            -fx-font-weight: normal;
                        """.formatted(BG_CARD, TEXT_PRIMARY));
            }
        });
        btn.setOnMouseExited(e -> {
            if (!category.equals(selectedCategory)) {
                btn.setStyle("""
                            -fx-background-color: transparent;
                            -fx-text-fill: %s;
                            -fx-font-size: 12.5;
                            -fx-padding: 0 14;
                            -fx-background-radius: 0 8 8 0;
                            -fx-cursor: hand;
                            -fx-font-weight: normal;
                        """.formatted(TEXT_SECONDARY));
            }
        });
        btn.setOnAction(e -> {
            selectedCategory = category;
            refreshSidebar();
            refreshModuleList();
        });

        return btn;
    }
    private void refreshSidebar() {
        VBox sidebar = (VBox) ((BorderPane) statusLabel.getScene().getRoot()).getLeft();
        for (var node : sidebar.getChildren()) {
            if ("categoryButtons".equals(node.getId()) && node instanceof VBox catBox) {
                catBox.getChildren().clear();
                for (String category : CATEGORIES) {
                    catBox.getChildren().add(createCategoryButton(category));
                }
                break;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ── Center Panel (Module List) ─────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════

    private String getCategoryDescription(String category) {
        return switch (category) {
            case "Combat" -> "Offensive automation and combat enhancements.";
            case "Movement" -> "Mobility and traversal modifications.";
            case "Render" -> "Visual enhancements and entity ESP.";
            case "Player" -> "Player state and interaction adjustments.";
            case "Utility" -> "General game automation and utilities.";
            default -> "Various client modifications.";
        };
    }

    private VBox createCenterPanel() {
        VBox center = new VBox(0);
        center.setPadding(new Insets(32, 24, 0, 36));
        center.setStyle("-fx-background-color: " + BG_DARK + ";");

        VBox headerBox = new VBox(4);
        headerBox.setPadding(new Insets(0, 0, 14, 2));

        categoryHeader = new Label(selectedCategory + " Modules");
        categoryHeader.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 20; -fx-font-weight: bold;");

        categoryDesc = new Label(getCategoryDescription(selectedCategory));
        categoryDesc.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 11;");

        headerBox.getChildren().addAll(categoryHeader, categoryDesc);

        moduleListContainer = new VBox(10);
        moduleListContainer.setPadding(new Insets(0, 2, 24, 0));
        moduleListContainer.setPrefWidth(1080);
        moduleListContainer.setMaxWidth(1100);

        VBox wrapper = new VBox(moduleListContainer);
        wrapper.setAlignment(Pos.TOP_LEFT);
        wrapper.setFillWidth(false);

        ScrollPane scrollPane = new ScrollPane(wrapper);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("""
                    -fx-background-color: transparent;
                    -fx-background: transparent;
                    -fx-border-color: transparent;
                """);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        center.getChildren().addAll(headerBox, scrollPane);
        refreshModuleList();

        return center;
    }

    private void refreshModuleList() {
        if (moduleListContainer == null)
            return;

        Platform.runLater(() -> {
            moduleListContainer.getChildren().clear();

            if (categoryHeader != null && categoryDesc != null) {
                categoryHeader.setText(selectedCategory + " Modules");
                categoryDesc.setText(getCategoryDescription(selectedCategory));
            }

            for (Map.Entry<String, JsonObject> entry : moduleStates.entrySet()) {
                JsonObject moduleData = entry.getValue();
                String category = moduleData.has("category") ? moduleData.get("category").getAsString() : "";

                if (!category.equalsIgnoreCase(selectedCategory))
                    continue;

                moduleListContainer.getChildren().add(createModuleCard(entry.getKey(), moduleData));
            }

            if (moduleListContainer.getChildren().isEmpty()) {
                Label empty = new Label("No modules in this category");
                empty.setStyle("-fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 14;");
                empty.setPadding(new Insets(40));
                moduleListContainer.getChildren().add(empty);
            }
        });
    }

    private String getKeyCodeName(int code) {
        if (code == 0) return "None";
        for (javafx.scene.input.KeyCode kc : javafx.scene.input.KeyCode.values()) {
            if (kc.getCode() == code) {
                return kc.getName();
            }
        }
        return "Unknown";
    }

    private VBox createModuleCard(String name, JsonObject data) {
        boolean enabled = data.has("enabled") && data.get("enabled").getAsBoolean();
        String description = data.has("description") ? data.get("description").getAsString() : "";

        String borderColor = enabled ? "#8B3DFF55" : BORDER_SOLID;
        VBox card = new VBox(0);
        card.setStyle(String.format(
            "-fx-background-color: linear-gradient(to bottom, %s, %s);" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: %s;" +
            "-fx-border-radius: 12;" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(gaussian, #00000055, 10, 0, 0, 3);",
            BG_CARD, BG_ELEVATED, borderColor));

        // Top highlight
        Region topHighlight = new Region();
        topHighlight.setPrefHeight(1);
        topHighlight.setMaxHeight(1);
        topHighlight.setStyle("-fx-background-color: linear-gradient(to right, transparent, #8B3DFF22, transparent);");

        // Content
        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.setPadding(new Insets(8, 16, 6, 16));

        VBox nameBox = new VBox(2);
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 13; -fx-font-weight: bold;");
        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 10.5;");
        nameBox.getChildren().addAll(nameLabel, descLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Keybind Button
        int currentBind = data.has("keyBind") ? data.get("keyBind").getAsInt() : 0;
        Button bindBtn = new Button("[" + getKeyCodeName(currentBind) + "]");
        bindBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 10; -fx-cursor: hand; -fx-border-color: " + BORDER_SOLID + "; -fx-border-radius: 4; -fx-padding: 2 6;");
        bindBtn.setOnMouseEntered(e -> bindBtn.setStyle("-fx-background-color: " + BG_CARD_HOVER + "; -fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 10; -fx-cursor: hand; -fx-border-color: " + BORDER_SOLID + "; -fx-border-radius: 4; -fx-padding: 2 6;"));
        bindBtn.setOnMouseExited(e -> bindBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 10; -fx-cursor: hand; -fx-border-color: " + BORDER_SOLID + "; -fx-border-radius: 4; -fx-padding: 2 6;"));
        
        bindBtn.setOnAction(e -> {
            bindBtn.setText("Press a key...");
            bindBtn.setStyle("-fx-background-color: " + ACCENT_GLOW + "; -fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 10; -fx-border-color: " + ACCENT + "; -fx-border-radius: 4; -fx-padding: 2 6;");
            
            java.util.function.Consumer<Integer> bindTask = (code) -> {
                data.addProperty("keyBind", code);
                statusLabel.getScene().setOnKeyPressed(null);
                statusLabel.getScene().setOnMouseClicked(null);
                if (ipcClient.isConnected()) {
                    JsonObject msg = new JsonObject();
                    msg.addProperty("type", "update_bind");
                    msg.addProperty("name", name);
                    msg.addProperty("keyBind", code);
                    ipcClient.send(msg);
                }
                refreshModuleList();
                log("Bound " + name + " to " + getKeyCodeName(code));
            };

            // Listen for the next key press on the scene
            statusLabel.getScene().setOnKeyPressed(keyEvent -> {
                int code = keyEvent.getCode().getCode();
                if (keyEvent.getCode() == javafx.scene.input.KeyCode.ESCAPE || keyEvent.getCode() == javafx.scene.input.KeyCode.BACK_SPACE) {
                    code = 0; // clear bind
                }
                bindTask.accept(code);
            });

            // Listen for mouse clicks
            statusLabel.getScene().setOnMouseClicked(mouseEvent -> {
                int code = 0;
                if (mouseEvent.getButton() == javafx.scene.input.MouseButton.PRIMARY) code = 1;
                else if (mouseEvent.getButton() == javafx.scene.input.MouseButton.SECONDARY) code = 2;
                else if (mouseEvent.getButton() == javafx.scene.input.MouseButton.MIDDLE) code = 4;
                else if (mouseEvent.getButton() == javafx.scene.input.MouseButton.BACK) code = 5;
                else if (mouseEvent.getButton() == javafx.scene.input.MouseButton.FORWARD) code = 6;
                if (code != 0) {
                    bindTask.accept(code);
                }
            });
        });

        // Toggle
        StackPane toggle = new StackPane();
        toggle.setPrefSize(44, 24);
        toggle.setMinSize(44, 24);
        toggle.setMaxSize(44, 24);
        HBox.setMargin(toggle, new Insets(0, 0, 0, 10));

        javafx.scene.shape.Rectangle trackBg = new javafx.scene.shape.Rectangle(44, 24);
        trackBg.setArcWidth(24);
        trackBg.setArcHeight(24);
        trackBg.setFill(Color.web(enabled ? TOGGLE_ON : "#1A1A2E"));
        trackBg.setStroke(Color.web(enabled ? "#A970FF44" : "#2D285433"));
        trackBg.setStrokeWidth(1);

        Circle knob = new Circle(9);
        knob.setFill(Color.WHITE);
        knob.setEffect(new DropShadow(5, Color.web("#00000055")));
        StackPane.setAlignment(knob, enabled ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        StackPane.setMargin(knob, new Insets(0, 3, 0, 3));

        toggle.getChildren().addAll(trackBg, knob);
        toggle.setCursor(javafx.scene.Cursor.HAND);

        toggle.setOnMouseClicked(e -> {
            boolean newState = !enabled;
            data.addProperty("enabled", newState);
            refreshModuleList();
            log(name + " " + (newState ? "enabled" : "disabled"));
            if (ipcClient.isConnected()) {
                ipcClient.setModuleEnabled(name, newState);
            }
        });

        topRow.getChildren().addAll(nameBox, spacer, bindBtn, toggle);

        // Separator
        Region separator = new Region();
        separator.setPrefHeight(1);
        separator.setMaxHeight(1);
        separator.setStyle("-fx-background-color: " + BORDER_SOLID + "88;");

        // Settings bar
        JsonObject settings = data.has("settings") ? data.getAsJsonObject("settings") : new JsonObject();
        VBox settingsBox = createSettingsPanel(name, settings);
        settingsBox.setVisible(false);
        settingsBox.setManaged(false);
        settingsBox.setPadding(new Insets(6, 18, 10, 18));

        HBox expander = new HBox(5);
        expander.setAlignment(Pos.CENTER_LEFT);
        expander.setPadding(new Insets(6, 18, 6, 18));
        expander.setStyle("-fx-background-color: " + BG_SETTINGS + "; -fx-background-radius: 0 0 11 11; -fx-cursor: hand;");

        Label gearIcon = new Label("\u2699");
        gearIcon.setStyle("-fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 10;");
        Label settingsLabel = new Label("Settings");
        settingsLabel.setStyle("-fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 10.5;");

        Region expanderSpacer = new Region();
        HBox.setHgrow(expanderSpacer, Priority.ALWAYS);

        Label chevron = new Label("\u2304");
        chevron.setStyle("-fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 12;");

        expander.getChildren().addAll(gearIcon, settingsLabel, expanderSpacer, chevron);

        expander.setOnMouseEntered(e -> {
            expander.setStyle("-fx-background-color: " + BG_SURFACE + "; -fx-background-radius: 0 0 11 11; -fx-cursor: hand;");
            settingsLabel.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 10.5;");
        });
        expander.setOnMouseExited(e -> {
            expander.setStyle("-fx-background-color: " + BG_SETTINGS + "; -fx-background-radius: 0 0 11 11; -fx-cursor: hand;");
            settingsLabel.setStyle("-fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 10.5;");
        });

        expander.setOnMouseClicked(e -> {
            boolean isExpanded = settingsBox.isVisible();
            settingsBox.setVisible(!isExpanded);
            settingsBox.setManaged(!isExpanded);
            chevron.setText(!isExpanded ? "\u2303" : "\u2304");
        });

        card.getChildren().addAll(topHighlight, topRow, separator, expander, settingsBox);

        // Hover
        card.setOnMouseEntered(e -> {
            String hoverBorder = enabled ? "#8B3DFF77" : "#3D3668";
            card.setStyle(String.format(
                "-fx-background-color: linear-gradient(to bottom, %s, %s);" +
                "-fx-background-radius: 14;" +
                "-fx-border-color: %s;" +
                "-fx-border-radius: 14;" +
                "-fx-border-width: 1;" +
                "-fx-effect: dropshadow(gaussian, #8B3DFF18, 20, 0, 0, 4);",
                BG_CARD_HOVER, BG_ELEVATED, hoverBorder));
        });
        card.setOnMouseExited(e -> {
            card.setStyle(String.format(
                "-fx-background-color: linear-gradient(to bottom, %s, %s);" +
                "-fx-background-radius: 14;" +
                "-fx-border-color: %s;" +
                "-fx-border-radius: 14;" +
                "-fx-border-width: 1;" +
                "-fx-effect: dropshadow(gaussian, #00000066, 16, 0, 0, 4);",
                BG_CARD, BG_ELEVATED, borderColor));
        });

        return card;
    }

    private VBox createSettingsPanel(String moduleName, JsonObject settings) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(8, 0, 0, 0));

        if (settings == null || settings.size() == 0) {
            Label placeholder = new Label("No settings loaded yet. Inject + sync to load live module options.");
            placeholder.setStyle("-fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 11;");
            box.getChildren().add(placeholder);
            return box;
        }

        for (String key : settings.keySet()) {
            JsonElement val = settings.get(key);

            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);

            Label label = new Label(key);
            label.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 12;");
            label.setMinWidth(100);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            if (val.isJsonPrimitive()) {
                if (val.getAsJsonPrimitive().isBoolean()) {
                    CheckBox cb = new CheckBox();
                    cb.setSelected(val.getAsBoolean());
                    cb.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");
                    cb.setOnAction(e -> {
                        settings.addProperty(key, cb.isSelected());
                        if (ipcClient.isConnected()) {
                            ipcClient.updateSetting(moduleName, key, cb.isSelected());
                        }
                    });
                    row.getChildren().addAll(label, spacer, cb);
                } else {
                    Label valueLabel = new Label(val.getAsString());
                    valueLabel.setStyle("-fx-text-fill: " + ACCENT_LIGHT + "; -fx-font-size: 12;");
                    row.getChildren().addAll(label, spacer, valueLabel);
                }
            } else if (val.isJsonObject() && val.getAsJsonObject().has("value") && val.getAsJsonObject().has("min")) {
                JsonObject obj = val.getAsJsonObject();
                double currentVal = obj.get("value").getAsDouble();
                double min = obj.get("min").getAsDouble();
                double max = obj.get("max").getAsDouble();
                Label valueLabel = new Label(String.format("%.1f", currentVal));
                valueLabel.setStyle("-fx-text-fill: " + ACCENT_LIGHT + "; -fx-font-size: 12;");
                Slider slider = new Slider(min, max, currentVal);
                slider.setPrefWidth(150);
                slider.setStyle("-fx-control-inner-background: " + BG_CARD + ";");
                slider.valueProperty().addListener((obs, old, newVal) -> {
                    valueLabel.setText(String.format("%.1f", newVal.doubleValue()));
                    obj.addProperty("value", newVal.doubleValue());
                    if (ipcClient.isConnected()) {
                        ipcClient.updateSetting(moduleName, key, newVal.doubleValue());
                    }
                });
                row.getChildren().addAll(label, spacer, slider, valueLabel);
            } else if (val.isJsonObject() && val.getAsJsonObject().has("value") && val.getAsJsonObject().has("modes")) {
                JsonObject obj = val.getAsJsonObject();
                String currentVal = obj.get("value").getAsString();
                com.google.gson.JsonArray modesArr = obj.getAsJsonArray("modes");
                
                ComboBox<String> modeBox = new ComboBox<>();
                for (int i = 0; i < modesArr.size(); i++) {
                    modeBox.getItems().add(modesArr.get(i).getAsString());
                }
                modeBox.setValue(currentVal);
                modeBox.setPrefWidth(150);
                modeBox.setStyle("-fx-background-color: " + BG_CARD + "; -fx-text-fill: " + ACCENT_LIGHT + "; -fx-font-size: 12; -fx-cursor: hand;");
                modeBox.setOnAction(e -> {
                    String nextVal = modeBox.getValue();
                    obj.addProperty("value", nextVal);
                    if (ipcClient.isConnected()) {
                        ipcClient.updateSetting(moduleName, key, nextVal);
                    }
                });
                row.getChildren().addAll(label, spacer, modeBox);
            }

            box.getChildren().add(row);
        }

        return box;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ── Bottom Panel (Console) ─────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════

    private VBox createBottomPanel() {
        VBox bottomWrapper = new VBox();
        bottomWrapper.setPadding(new Insets(16, 24, 24, 0));
        bottomWrapper.setStyle("-fx-background-color: transparent;");

        VBox bottom = new VBox(0);
        bottom.setPrefHeight(120);
        bottom.setMinHeight(120);
        bottom.setMaxHeight(120);
        bottom.setStyle(
                "-fx-background-color: " + BG_PANEL + "; -fx-border-color: " + BORDER_SOLID + "; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;");

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(6, 12, 3, 12));

        Label consoleTitle = new Label("CONSOLE");
        consoleTitle.setStyle("-fx-text-fill: " + ACCENT_LIGHT + "; -fx-font-size: 9; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clearBtn = new Button("Clear");
        clearBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 9; -fx-cursor: hand;");
        clearBtn.setOnMouseEntered(e -> clearBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + ACCENT_LIGHT + "; -fx-font-size: 9; -fx-cursor: hand;"));
        clearBtn.setOnMouseExited(e -> clearBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 9; -fx-cursor: hand;"));
        clearBtn.setOnAction(e -> consoleArea.clear());

        header.getChildren().addAll(consoleTitle, spacer, clearBtn);

        consoleArea = new TextArea();
        consoleArea.setEditable(false);
        consoleArea.setWrapText(true);
        consoleArea.setStyle("""
                    -fx-control-inner-background: %s;
                    -fx-text-fill: %s;
                    -fx-font-family: 'Consolas', 'Courier New', monospace;
                    -fx-font-size: 10.5;
                    -fx-border-color: transparent;
                    -fx-background-color: transparent;
                """.formatted(BG_PANEL, TEXT_SECONDARY));
        VBox.setVgrow(consoleArea, Priority.ALWAYS);
        VBox.setMargin(consoleArea, new Insets(0, 6, 4, 6));

        bottom.getChildren().addAll(header, consoleArea);
        bottomWrapper.getChildren().add(bottom);
        return bottomWrapper;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ── Actions ────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════

    private void saveConfigLocally() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Config");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        fileChooser.setInitialFileName("onionmcc_config.json");
        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                JsonObject root = new JsonObject();
                for (Map.Entry<String, JsonObject> entry : moduleStates.entrySet()) {
                    root.add(entry.getKey(), entry.getValue());
                }
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                Files.writeString(file.toPath(), gson.toJson(root), StandardCharsets.UTF_8);
                log("Config saved to " + file.getAbsolutePath());
            } catch (Exception e) {
                log("Failed to save config: " + e.getMessage());
            }
        }
    }

    private void loadConfigLocally() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Config");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                for (String key : root.keySet()) {
                    if (moduleStates.containsKey(key)) {
                        JsonObject loadedMod = root.getAsJsonObject(key);
                        JsonObject currentMod = moduleStates.get(key);
                        
                        if (loadedMod.has("enabled")) {
                            boolean enabled = loadedMod.get("enabled").getAsBoolean();
                            currentMod.addProperty("enabled", enabled);
                        }
                        if (loadedMod.has("settings")) {
                            currentMod.add("settings", loadedMod.getAsJsonObject("settings"));
                        }
                    }
                }
                refreshModuleList();
                if (ipcClient.isConnected()) {
                    pushLocalConfigToIPC();
                }
                log("Config loaded from " + file.getAbsolutePath());
            } catch (Exception e) {
                log("Failed to load config: " + e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ── IPC ────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════

    private void setupIPCListener() {
        ipcClient.addMessageListener(msg -> {
            String type = msg.has("type") ? msg.get("type").getAsString() : "";

            switch (type) {
                case "full_state" -> {
                    if (msg.has("pid") && expectedPid != null) {
                        String actualPid = msg.get("pid").getAsString();
                        if (!expectedPid.equals(actualPid)) {
                            log("ERROR: IPC connected to unexpected PID " + actualPid + " (expected " + expectedPid + ").");
                            log("Close old injected Java processes and retry injection.");
                            updateStatus(false, "Wrong IPC Target");
                            ipcClient.disconnect();
                            return;
                        }
                    }
                    String version = msg.has("version") ? msg.get("version").getAsString() : "?";
                    Platform.runLater(() -> versionLabel.setText("MC " + version + " | v1.0.0"));
                    if (msg.has("modules")) {
                        JsonObject modules = msg.getAsJsonObject("modules");
                        if (modules.size() > 0) {
                            // Merge client's fresh state but keep our locally loaded config if it overrides
                            for (String key : modules.keySet()) {
                                if (moduleStates.containsKey(key)) {
                                    JsonObject clientMod = modules.getAsJsonObject(key);
                                    JsonObject localMod = moduleStates.get(key);
                                    
                                    boolean localEnabled = localMod.has("enabled") && localMod.get("enabled").getAsBoolean();
                                    
                                    // only override if we haven't set settings locally
                                    if (!localMod.has("settings") || localMod.getAsJsonObject("settings").size() == 0) {
                                        clientMod.addProperty("enabled", localEnabled);
                                        moduleStates.put(key, clientMod);
                                    } else {
                                        // Merge settings structure (min/max bounds from client)
                                        if (clientMod.has("settings")) {
                                            JsonObject clientSettings = clientMod.getAsJsonObject("settings");
                                            JsonObject localSettings = localMod.getAsJsonObject("settings");
                                            for (String sKey : clientSettings.keySet()) {
                                                if (localSettings.has(sKey)) {
                                                    JsonElement cVal = clientSettings.get(sKey);
                                                    JsonElement lVal = localSettings.get(sKey);
                                                    if (cVal.isJsonObject() && lVal.isJsonObject()) {
                                                        cVal.getAsJsonObject().add("value", lVal.getAsJsonObject().get("value"));
                                                        localSettings.add(sKey, cVal);
                                                    }
                                                } else {
                                                    localSettings.add(sKey, clientSettings.get(sKey));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            refreshModuleList();
                            log("Synced modules with client.");
                        } else {
                            boolean ready = msg.has("ready") && msg.get("ready").getAsBoolean();
                            if (!ready) {
                                log("IPC connected; client is still initializing modules...");
                            }
                        }
                    }
                }
                case "module_state" -> {
                    String name = msg.get("name").getAsString();
                    boolean enabled = msg.get("enabled").getAsBoolean();
                    if (moduleStates.containsKey(name)) {
                        moduleStates.get(name).addProperty("enabled", enabled);
                        if (msg.has("settings")) {
                            moduleStates.get(name).add("settings", msg.getAsJsonObject("settings"));
                        }
                    }
                    refreshModuleList();
                }
            }
        });
    }

    private void loadDefaultModules() {
        addDefaultModule("KillAura", "COMBAT", "Automatically attacks nearby entities");
        addDefaultModule("TriggerBot", "COMBAT", "Attacks the entity you're looking at");
        addDefaultModule("Reach", "COMBAT", "Extends your attack reach distance");
        addDefaultModule("Velocity", "COMBAT", "Legitimately jump-resets on damage");
        addDefaultModule("AutoClicker", "COMBAT", "Automatically clicks at randomized CPS");
        addDefaultModule("WTap", "COMBAT", "Re-sprints after attacks for combos");
        addDefaultModule("AimAssist", "COMBAT", "Smoothly aims toward nearest target");
        addDefaultModule("SilentKiller", "COMBAT", "Attacks entities silently without aiming");
        addDefaultModule("SilentAim", "COMBAT", "Hits targets outside crosshair silently");

        addDefaultModule("Sprint", "MOVEMENT", "Automatically sprints while moving");

        addDefaultModule("Fullbright", "RENDER", "Maximum brightness everywhere");
        addDefaultModule("ArrayList", "RENDER", "Displays enabled modules on screen");
        addDefaultModule("ESP", "RENDER", "Draws boxes around players");
        addDefaultModule("Tracers", "RENDER", "Draws lines to players");

        addDefaultModule("Teams", "PLAYER", "Prevents attacking teammates");
        addDefaultModule("Friends", "PLAYER", "Middle-click players to friend them");
        addDefaultModule("LegitScaffold", "PLAYER", "Helps speed-bridging safely");
        addDefaultModule("ChestStealer", "UTILITY", "Automatically loots chests stealthily");
        addDefaultModule("AutoArmor", "PLAYER", "Automatically equips the best armor");
        addDefaultModule("Clutch", "PLAYER", "Automatically places blocks to save you from falling");

        addDefaultSettingNumber("Fullbright", "Brightness", 1.0, 0.0, 1.0);
        
        addDefaultSettingBoolean("Sprint", "Omni", false);
        addDefaultSettingNumber("WTap", "Delay", 2.0, 1.0, 10.0);
        addDefaultSettingNumber("Velocity", "Chance", 80.0, 0.0, 100.0);
        addDefaultSettingNumber("LegitScaffold", "Delay", 50.0, 0.0, 200.0);
        
        addDefaultSettingNumber("ChestStealer", "Start Delay", 150.0, 0.0, 500.0);
        addDefaultSettingNumber("ChestStealer", "Steal Delay", 100.0, 0.0, 500.0);
        addDefaultSettingNumber("ChestStealer", "Close Delay", 150.0, 0.0, 500.0);
        addDefaultSettingBoolean("ChestStealer", "Auto Close", true);
        addDefaultSettingMode("ChestStealer", "Mode", "Smart", "Smart", "Normal", "Random");
        addDefaultSettingNumber("TriggerBot", "Range", 3.0, 1.0, 6.0);
        addDefaultSettingNumber("TriggerBot", "MinDelay", 1.0, -5.0, 5.0);
        addDefaultSettingNumber("TriggerBot", "MaxDelay", 3.0, -5.0, 5.0);
        addDefaultSettingBoolean("TriggerBot", "RequireMouseDown", false);
        addDefaultSettingNumber("Reach", "MinReach", 3.0, 3.0, 6.0);
        addDefaultSettingNumber("Reach", "MaxReach", 3.1, 3.0, 6.0);
        addDefaultSettingNumber("Reach", "Chance", 15.0, 0.0, 100.0);
        addDefaultSettingNumber("HitBox", "Expand", 0.1, 0.05, 0.5);
        addDefaultSettingNumber("KillAura", "Range", 3.1, 1.0, 6.0);
        addDefaultSettingNumber("KillAura", "APS", 9.0, 1.0, 20.0);
        addDefaultSettingMode("KillAura", "Targeting", "Switch", "Single", "Switch", "Multi");
        addDefaultSettingBoolean("KillAura", "AutoWeapon", true);
        addDefaultSettingNumber("AutoClicker", "Min CPS", 9.0, 1.0, 20.0);
        addDefaultSettingNumber("AutoClicker", "Max CPS", 13.0, 1.0, 20.0);
        addDefaultSettingBoolean("AutoClicker", "Left Click", true);
        addDefaultSettingBoolean("AutoClicker", "Right Click", false);
        addDefaultSettingBoolean("AutoClicker", "BreakBlocks", true);
        addDefaultSettingBoolean("AutoClicker", "AllowEat", true);
        addDefaultSettingBoolean("AutoClicker", "Jitter", false);
        addDefaultSettingBoolean("AimAssist", "ClickOnly", true);
        addDefaultSettingNumber("AimAssist", "Speed", 5.0, 1.0, 50.0);
        addDefaultSettingNumber("AimAssist", "FOV", 45.0, 10.0, 360.0);
        addDefaultSettingBoolean("AimAssist", "LockOn", false);

        refreshModuleList();
    }

    private void addDefaultSettingNumber(String module, String name, double val, double min, double max) {
        if (!moduleStates.containsKey(module)) return;
        JsonObject settings = moduleStates.get(module).getAsJsonObject("settings");
        JsonObject obj = new JsonObject();
        obj.addProperty("value", val);
        obj.addProperty("min", min);
        obj.addProperty("max", max);
        settings.add(name, obj);
    }
    
    private void addDefaultSettingBoolean(String module, String name, boolean val) {
        if (!moduleStates.containsKey(module)) return;
        JsonObject settings = moduleStates.get(module).getAsJsonObject("settings");
        settings.addProperty(name, val);
    }
    
    private void addDefaultSettingMode(String module, String name, String val, String... modes) {
        if (!moduleStates.containsKey(module)) return;
        JsonObject settings = moduleStates.get(module).getAsJsonObject("settings");
        JsonObject obj = new JsonObject();
        obj.addProperty("value", val);
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (String m : modes) {
            arr.add(m);
        }
        obj.add("modes", arr);
        settings.add(name, obj);
    }

    private void addDefaultModule(String name, String category, String description) {
        JsonObject obj = new JsonObject();
        obj.addProperty("enabled", false);
        obj.addProperty("category", category);
        obj.addProperty("description", description);
        obj.addProperty("keyBind", 0);
        obj.add("settings", new JsonObject());
        moduleStates.put(name, obj);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ── Utility ────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════

    private void styleButton(Button btn, String bgColor, String textColor) {
        btn.setStyle("""
                    -fx-background-color: %s;
                    -fx-text-fill: %s;
                    -fx-font-size: 12;
                    -fx-font-weight: bold;
                    -fx-background-radius: 8;
                    -fx-padding: 8 12;
                    -fx-cursor: hand;
                """.formatted(bgColor, textColor));

        btn.setOnMouseEntered(e -> btn.setOpacity(0.85));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));
    }

    private void log(String message) {
        Platform.runLater(() -> {
            if (consoleArea != null) {
                String time = java.time.LocalTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                consoleArea.appendText("[" + time + "] " + message + "\n");
            }
        });
    }

    private void showAgentLogTail() {
        try {
            String agentLogPath = injector.getLastAgentLogPath();
            if (agentLogPath == null || agentLogPath.isBlank()) {
                return;
            }
            Path path = Paths.get(agentLogPath);
            if (!Files.exists(path)) {
                log("Agent log not found: " + agentLogPath);
                return;
            }
            List<String> lines = Files.readAllLines(path);
           


            log("Last agent log lines:");
            int start = Math.max(0, lines.size() - 8);
            for (int i = start; i < lines.size(); i++) {
                log("  " + lines.get(i));
            }
        } catch (Exception e) {
            log("Failed to read agent log tail: " + e.getMessage());
        }
    }

    // -- Main -----------------------------------------------------------

    public static void main(String[] args) {
        launch(args);
    }
}
