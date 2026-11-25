// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static com.ynpe.fastremote.ui.LibraryTreeSupport.*;
import static com.ynpe.fastremote.ui.HostCatalogModel.*;
import static com.ynpe.fastremote.ui.SftpBrowserModel.*;
import static com.ynpe.fastremote.ui.WorkspaceViewModel.*;
import static com.ynpe.fastremote.ui.WorkspacePaletteActions.Action.*;

import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.ConnectionProgress;
import com.ynpe.fastremote.domain.connection.ConnectionException;
import com.ynpe.fastremote.domain.connection.ConnectionFailureType;
import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.PasswordCredentials;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.connection.PrivateKeyCredentials;
import com.ynpe.fastremote.domain.connection.Username;
import com.ynpe.fastremote.domain.catalog.GroupId;
import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.catalog.RecentConnection;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.catalog.HostGroup;
import com.ynpe.fastremote.domain.catalog.HostIcon;
import com.ynpe.fastremote.domain.command.CommandPaletteUsage;
import com.ynpe.fastremote.domain.security.HostKeyCheck;
import com.ynpe.fastremote.domain.security.HostKeyDecision;
import com.ynpe.fastremote.domain.security.HostKeyStatus;
import com.ynpe.fastremote.domain.vault.SecretId;
import com.ynpe.fastremote.domain.vault.VaultStatus;
import com.ynpe.fastremote.domain.settings.GlobalSettings;
import com.ynpe.fastremote.domain.settings.GlobalToolbarButton;
import com.ynpe.fastremote.domain.settings.CommandPaletteSettings;
import com.ynpe.fastremote.domain.settings.DockDisplayMode;
import com.ynpe.fastremote.domain.settings.DockSide;
import com.ynpe.fastremote.domain.settings.HostTabTitlePart;
import com.ynpe.fastremote.domain.settings.SftpBookmarks;
import com.ynpe.fastremote.domain.settings.SftpSettings;
import com.ynpe.fastremote.domain.settings.SftpDoubleClickAction;
import com.ynpe.fastremote.domain.settings.WindowBounds;
import com.ynpe.fastremote.domain.settings.WindowSettings;
import com.ynpe.fastremote.domain.settings.WorkspaceBlankDoubleClickAction;
import com.ynpe.fastremote.domain.settings.TerminalShortcutSettings;
import com.ynpe.fastremote.domain.credential.GlobalCredential;
import com.ynpe.fastremote.domain.credential.CredentialGroup;
import com.ynpe.fastremote.domain.credential.CredentialGroupId;
import com.ynpe.fastremote.domain.transfer.RemoteFileEntry;
import com.ynpe.fastremote.domain.transfer.TransferActivity;
import com.ynpe.fastremote.domain.transfer.TransferConflict;
import com.ynpe.fastremote.domain.transfer.TransferConflictChoice;
import com.ynpe.fastremote.domain.terminal.TerminalTermination;
import com.ynpe.fastremote.domain.automation.AutomationApprovalRequest;
import com.ynpe.fastremote.domain.automation.AutomationSource;
import com.ynpe.fastremote.domain.automation.InteractiveAutomationGateway;
import com.ynpe.fastremote.domain.ai.AiSettings;
import com.ynpe.fastremote.domain.ai.AiChatMessage;
import com.ynpe.fastremote.domain.ai.AiChatImage;
import com.ynpe.fastremote.domain.ai.AiChatSession;
import com.ynpe.fastremote.domain.ai.McpServerSettings;
import com.ynpe.fastremote.domain.ai.ModelProviderConfig;
import com.ynpe.fastremote.domain.ai.ModelProviderType;
import com.ynpe.fastremote.domain.ai.AiInferenceOptions;
import com.ynpe.fastremote.domain.ai.AiModelCapabilities;
import com.ynpe.fastremote.domain.ai.AiInputModality;
import com.ynpe.fastremote.domain.ai.AiWorkspaceContext;
import com.ynpe.fastremote.domain.ai.AiReasoningEffort;
import com.ynpe.fastremote.domain.snippet.Snippet;
import com.ynpe.fastremote.domain.snippet.SnippetGroup;
import com.ynpe.fastremote.domain.snippet.SnippetGroupId;
import com.ynpe.fastremote.domain.snippet.SnippetId;
import com.ynpe.fastremote.domain.snippet.SnippetInput;
import com.ynpe.fastremote.domain.snippet.SnippetTemplateAnalysis;
import com.ynpe.fastremote.domain.snippet.SnippetTemplateContext;
import com.ynpe.fastremote.domain.snippet.SnippetTemplateEngine;
import com.ynpe.fastremote.domain.snippet.SnippetVariable;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroup;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroupId;
import com.ynpe.fastremote.domain.snippet.BuiltinSnippetConflictPolicy;
import com.ynpe.fastremote.ui.LucideIconView.Icon;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.collections.ListChangeListener;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.DialogPane;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TableRow;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.input.Dragboard;
import javafx.scene.input.DragEvent;
import javafx.scene.image.Image;
import javafx.scene.SnapshotParameters;
import javafx.scene.paint.Color;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

public final class MainWindowController implements AutoCloseable {
    private static final String AUTOMATION_OWNED_TAB_KEY =
            MainWindowController.class.getName() + ".automationOwned";
    static final double QUICK_CONNECT_DIALOG_WIDTH = 600.0;
    static final double QUICK_CONNECT_DIALOG_HEIGHT = 675.0;
    static final double QUICK_CONNECT_COMPACT_BUTTON_WIDTH = 95.0;
    static final double SESSION_MANAGER_INITIAL_WIDTH = 480.0;
    static final double SESSION_MANAGER_INITIAL_HEIGHT = 700.0;
    static final double SESSION_MANAGER_MINIMUM_WIDTH = 360.0;
    static final double SESSION_MANAGER_MINIMUM_HEIGHT = 360.0;
    private static final int MIN_MASTER_PASSWORD_LENGTH = 8;
    private static final ButtonType ACCEPT_ONCE = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e1466f5390da3066"));
    private static final ButtonType TRUST_AND_SAVE = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.52f94743915f8b79"));
    private static final char[] PASSWORD_LOWERCASE = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char[] PASSWORD_UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final char[] PASSWORD_DIGITS = "0123456789".toCharArray();
    private static final char[] PASSWORD_ALPHANUMERIC =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final SecureRandom PASSWORD_RANDOM = new SecureRandom();
    private static final double QUICK_FORM_LABEL_LEFT = 0.0;
    private static final double QUICK_FORM_LABEL_WIDTH = FormLabel.ENGLISH_BASELINE_WIDTH;
    private static final double QUICK_FORM_FIELD_GAP = 15.0;
    private static final double HIDDEN_TOOL_RAIL_DROP_WIDTH = 30.0;
    private static final double QUICK_FORM_FIELD_RIGHT = 0.0;
    private static final double QUICK_FORM_CONTROL_HEIGHT = 34.0;
    private static final double QUICK_FORM_ROW_HEIGHT = 44.0;
    private static final double QUICK_FORM_LABEL_HEIGHT = FormLabel.COMPACT_TWO_LINE_HEIGHT;
    private static final double QUICK_FORM_ROW_GAP = 0.0;
    private static final double QUICK_FORM_FIELD_MIN_WIDTH = 280.0;
    private static final double STANDARD_DOCK_MIN_WIDTH = 245.0;
    static final double VAULT_DIALOG_WIDTH = 540.0;
    static final int DIAGNOSTIC_LOG_MAXIMUM_CHARACTERS = 1024 * 1024;
    static final int FILE_DROP_CONFIRM_THRESHOLD = 5;
    static final int FILE_DROP_MAXIMUM = 50;
    private static final ThreadPoolExecutor FILE_DROP_INSPECTOR = new ThreadPoolExecutor(
            1, 1, 20L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2),
            Thread.ofPlatform().daemon(true).name("fastremote-file-drop-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());
    private static final Object WORKSPACE_STATUS_TOOLS_KEY = new Object();
    static final int TRAFFIC_LOG_INTERVAL_SECONDS = 3;
    private static final String AI_REASONING_RECORD_PREFIX = "[[fastremote:reasoning:v1]]\n";
    private static final double TERMINAL_ACTIVITY_MIN_HEIGHT = 100.0;
    private static final double SFTP_ACTIVITY_MIN_HEIGHT = 135.0;
    private final ConnectionWorkflow workflow;
    private final HostCatalogWorkflow catalogWorkflow;
    private final SettingsWorkflow settingsWorkflow;
    private final SnippetWorkflow snippetWorkflow;
    private final LocalTerminalWorkflow localTerminalWorkflow;
    private final AiAssistantWorkflow aiAssistantWorkflow;
    private CommandPaletteUsageWorkflow commandPaletteUsageWorkflow = CommandPaletteUsageWorkflow.unavailable();
    private final Map<String, CommandPaletteUsage> commandPaletteUsage = new HashMap<>();
    private final InteractiveAutomationGateway automationGateway;
    private final com.ynpe.fastremote.i18n.Localizer localizer;
    private final com.ynpe.fastremote.domain.license.LicenseAccess licenseAccess;
    private final java.util.function.Supplier<CompletionStage<com.ynpe.fastremote.domain.license.LicenseSnapshot>> licenseStatus;
    private final java.util.function.Supplier<CompletionStage<com.ynpe.fastremote.domain.license.LicenseSnapshot>> deactivateLicense;
    private final Runnable openLicenseActivation;
    private final Runnable showSoftLicenseReminder;
    private final com.ynpe.fastremote.domain.settings.DiagnosticLogOutput diagnosticLogOutput;
    private java.util.function.Supplier<Boolean> automaticUpdateChecks = () -> true;
    private java.util.function.Function<Boolean, CompletionStage<Void>> saveAutomaticUpdateChecks =
            ignored -> CompletableFuture.completedFuture(null);
    private Runnable manualUpdateCheck = () -> { };
    private final SnippetLibraryEditor snippetLibraryEditor;
    private VariableLibraryTree variableLibraryTree;
    private final VariableLibraryEditor variableLibraryEditor;
    private CredentialLibraryTree credentialLibraryTree;
    private final CredentialLibraryEditor credentialLibraryEditor;
    private final SnippetTemplateEngine snippetTemplateEngine = new SnippetTemplateEngine();
    private final Map<TreeItem<String>, SavedHost> hostItems = new IdentityHashMap<>();
    private final Map<TreeItem<String>, HostGroup> groupItems = new IdentityHashMap<>();
    private final Map<Tab, OpenSession> openSessions = new HashMap<>();
    private final ActivationHistory<Tab> workspaceActivationHistory = new ActivationHistory<>();
    private final Map<Tab, Tab> pendingWorkspaceCloseFallbacks = new IdentityHashMap<>();
    private OpenSession latestAutomationSftpTarget;
    private TransferConflictDialogQueue transferConflictDialogs;
    private final InitialWindowGate initialWindowGate = new InitialWindowGate();
    private boolean initialWorkspacePresentationPending = true;
    private final WorkspaceObservations observations = new WorkspaceObservations();
    private final Map<Tab, LocalTerminalHandle> localTerminals = new HashMap<>();
    private final Map<Tab, LocalWorkspaceSession> localWorkspaceSessions = new HashMap<>();
    private final Map<Tab, LocalFileWorkspaceController> localFileWorkspaces = new HashMap<>();
    private final Map<UUID, Tab> pendingWorkspaceInsertionAnchors = new HashMap<>();
    private final Map<UUID, String> pendingRemoteFileSelections = new LinkedHashMap<>();
    private final List<Runnable> catalogRefreshListeners = new ArrayList<>();
    private final Map<TreeItem<String>, String> expansionGroupItems = new WeakHashMap<>();
    private SnippetLibraryDialog snippetLibraryDialog;
    private final Map<TreeItem<String>, SnippetVariable> variableItems = new IdentityHashMap<>();
    private final Map<TreeItem<String>, SnippetVariableGroup> variableGroupItems = new IdentityHashMap<>();
    private final Map<TreeItem<String>, GlobalCredential> credentialItems = new IdentityHashMap<>();
    private final Map<TreeItem<String>, CredentialGroup> credentialGroupItems = new IdentityHashMap<>();
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(250));
    private final PauseTransition expansionSaveDelay = new PauseTransition(Duration.millis(300));
    private final PauseTransition windowBoundsSaveDelay = new PauseTransition(Duration.millis(450));
    private final PauseTransition horizontalDockRestoreDelay = new PauseTransition(Duration.millis(40));
    private final PauseTransition ownedWindowFocusRestoreDelay = new PauseTransition(Duration.millis(120));
    private final ListChangeListener<Window> ownedWindowListListener = this::handleOwnedWindowListChange;
    private final ThrottledTextLog diagnosticTextLog = new ThrottledTextLog(
            DIAGNOSTIC_LOG_MAXIMUM_CHARACTERS, "diagnostic-log-flusher");
    private final Object diagnosticLogLock = new Object();
    private final AtomicBoolean diagnosticPathUpdatePending = new AtomicBoolean();
    private String previousDiagnosticMessage;
    private String previousDiagnosticTone;
    private final Map<Object, TrafficSample> trafficSamples = new IdentityHashMap<>();
    private long totalReceivedBytes;
    private long totalSentBytes;
    private long previousTrafficSampleNanos = System.nanoTime();
    private final Timeline trafficLogPulse = new Timeline(new KeyFrame(
            Duration.seconds(TRAFFIC_LOG_INTERVAL_SECONDS), ignored -> sampleTraffic()));
    private final Timeline mcpStatusPulse = new Timeline(
            new KeyFrame(Duration.seconds(1), ignored -> refreshMcpStatusBar()));
    private final AtomicBoolean controllerClosed = new AtomicBoolean();
    private Runnable closeMainWindow = () -> { };
    private Runnable closeApplication = () -> { };
    private javafx.event.EventHandler<WindowEvent> mainWindowCloseHandler;
    private final Set<AutomationSource> automationSessionAllowAll =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private GlobalSettings persistedWorkspaceSettings = GlobalSettings.defaults();
    private boolean workspaceSettingsLoaded;
    private boolean synchronizingGroupExpansion;
    private boolean expansionStateDirty;
    private Stage mainWindow;
    private KeyboardShortcutManager keyboardShortcutManager;
    private final ShortcutDispatchGate shortcutDispatchGate = new ShortcutDispatchGate();
    private javafx.scene.control.Dialog<Void> hostLibraryDialog;
    private TextField hostLibrarySearchField;
    private Tab hostLibraryConnectAnchor;
    private boolean restoringWindowBounds;
    private boolean windowBoundsRestored;

    @FXML private StackPane rootStack;
    @FXML private BorderPane rootPane;
    @FXML private MenuBar mainMenuBar;
    @FXML private ToolBar mainToolbar;
    @FXML private CheckMenuItem globalToolbarMenuItem;
    @FXML private CheckMenuItem globalStatusBarMenuItem;
    @FXML private BorderPane workbenchBoard;
    @FXML private StackPane workbenchOverlayHost;
    @FXML private SplitPane horizontalWorkbenchSplit;
    @FXML private VBox centerWorkbench;
    @FXML private StackPane workspaceHost;
    @FXML private VBox hostSidebar;
    @FXML private VBox rightDock;
    @FXML private VBox leftToolRail;
    @FXML private VBox rightToolRail;
    @FXML private StackPane leftDockContentHost;
    @FXML private StackPane rightDockContentHost;
    @FXML private Label leftDockTitle;
    @FXML private Label leftDockSubtitle;
    @FXML private Label rightDockTitle;
    @FXML private Label rightDockSubtitle;
    private ComboBox<ModelProviderConfig> aiProviderField;
    private ComboBox<String> aiModelField;
    private ComboBox<AiReasoningEffort> aiReasoningField;
    private CheckBox aiReasoningSummaryField;
    private StackPane aiConversationHost;
    private TextArea aiPromptField;
    private Button aiSendButton;
    private LucideIconView aiSendIcon;
    private Tooltip aiSendTooltip;
    private Button aiNewChatButton;
    private Button aiPlacementButton;
    private LucideIconView aiPlacementIcon;
    private Tooltip aiPlacementTooltip;
    private Label aiChatTitleLabel;
    @FXML private Button leftHostLibraryButton;
    @FXML private Button rightHostLibraryButton;
    @FXML private Button leftDockImportButton;
    @FXML private Button leftDockExportButton;
    @FXML private Button rightDockImportButton;
    @FXML private Button rightDockExportButton;
    @FXML private Button leftDockDisplayModeButton;
    @FXML private Button rightDockDisplayModeButton;
    @FXML private LucideIconView leftDockDisplayModeIcon;
    @FXML private LucideIconView rightDockDisplayModeIcon;
    @FXML private Button toolbarLeftDockButton;
    @FXML private Button toolbarRightDockButton;
    @FXML private Node hostLibraryPage;
    @FXML private HostLibraryController hostLibraryPageController;
    @FXML private Node quickConnectPage;
    @FXML private Node snippetsPage;
    @FXML private SnippetLibraryController snippetsPageController;
    @FXML private MenuItem snippetLibraryMenuItem;
    @FXML private Node variableLibraryPage;
    @FXML private VariableLibraryController variableLibraryPageController;
    @FXML private Node credentialVaultPage;
    @FXML private CredentialLibraryController credentialVaultPageController;
    @FXML private Node aiAssistantPage;
    @FXML private AiAssistantController aiAssistantPageController;
    @FXML private Node diagnosticLogPage;
    @FXML private DiagnosticLogController diagnosticLogPageController;
    @FXML private Button hostLibraryRailButton;
    @FXML private Button quickConnectRailButton;
    @FXML private Button snippetsRailButton;
    @FXML private Button variableLibraryRailButton;
    @FXML private Button credentialVaultRailButton;
    @FXML private Button aiAssistantRailButton;
    @FXML private Button diagnosticLogRailButton;
    private TreeView<String> hostTree;

    private TextField snippetSearchField;

    private TreeView<String> variableTree;
    private TextField variableSearchField;
    private TextField hostSearchField;
    private ToggleButton hostAllFilter;
    private ToggleButton hostFavoriteFilter;
    private ToggleButton hostRecentFilter;
    private TreeView<String> credentialTree;
    private TextField credentialSearchField;
    private VBox credentialEmpty;
    @FXML private TabPane workspaceTabs;
    @FXML private Node workspaceIntro;
    @FXML private ListView<RecentConnection> recentHostList;
    @FXML private VBox recentHostEmpty;

    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField usernameField;
    @FXML private MenuButton jumpHostField;
    @FXML private ComboBox<AuthenticationType> authenticationField;
    @FXML private ComboBox<QuickCredentialSource> credentialSourceField;
    @FXML private MenuButton credentialProfileField;
    @FXML private RevealablePasswordEditor hostPasswordField;
    @FXML private RevealablePasswordEditor privateKeyPassphraseField;
    @FXML private TextField privateKeyField;
    @FXML private CheckBox legacyAlgorithmsField;
    @FXML private AnchorPane quickConnectFields;
    @FXML private Button parseQuickConnectClipboardButton;
    @FXML private Button chooseKeyButton;
    @FXML private Button testQuickConnectionButton;
    @FXML private Button saveQuickHostButton;
    @FXML private Button connectButton;
    @FXML private Button disconnectButton;
    @FXML private Label quickConnectionTestStatus;
    @FXML private MenuItem reconnectMenuItem;
    @FXML private MenuItem disconnectMenuItem;
    @FXML private MenuItem localTerminalMenuItem;
    @FXML private MenuItem quickConnectMenuItem;
    @FXML private MenuItem commandPaletteMenuItem;
    @FXML private MenuItem aiAssistantMenuItem;
    @FXML private MenuItem diagnosticLogMenuItem;
    @FXML private MenuItem createHostMenuItem;
    @FXML private MenuItem hostLibraryMenuItem;
    @FXML private MenuItem settingsMenuItem;
    @FXML private MenuItem installCommandLineToolsMenuItem;
    @FXML private MenuItem newEditorDocumentMenuItem;
    @FXML private MenuItem openTextFileMenuItem;
    @FXML private MenuItem saveTextFileMenuItem;
    @FXML private MenuItem saveTextFileAsMenuItem;
    @FXML private MenuItem saveAllTextFilesMenuItem;
    @FXML private MenuItem closeTextFileMenuItem;
    @FXML private MenuItem cutTextMenuItem;
    @FXML private MenuItem copyTextMenuItem;
    @FXML private MenuItem pasteTextMenuItem;
    @FXML private MenuItem findTextMenuItem;
    @FXML private MenuItem replaceTextMenuItem;
    @FXML private Menu openRecentMenu;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private HBox statusBar;
    @FXML private Label mcpStatusIndicator;
    @FXML private Label mcpStatusLabel;
    @FXML private Button primaryStatusLabel;
    @FXML private Label statusIndicator;
    @FXML private Label trafficStatisticsLabel;
    @FXML private HBox workspaceQuickTools;
    @FXML private Button toolbarGlobalFavoritesButton;
    @FXML private Button toolbarCommandLibraryButton;
    @FXML private Button toolbarEditorButton;
    @FXML private Button toolbarAiAssistantButton;
    @FXML private Button toolbarAiAssistantDialogButton;
    @FXML private Button toolbarDiagnosticLogButton;
    @FXML private Label toolbarActivationStatusLabel;
    @FXML private Label vaultStatusLabel;
    @FXML private LucideIconView vaultActionIcon;
    @FXML private MenuItem activateLicenseMenuItem;
    @FXML private MenuItem deactivateLicenseMenuItem;
    @FXML private MenuItem licenseMenuSeparator;
    @FXML private MenuItem updateMenuItem;
    @FXML private MenuItem updateMenuSeparator;
    private boolean commercialLicensing = true;
    private boolean applicationUpdates = true;
    private TextArea diagnosticLogTextArea;
    private Button diagnosticLogPlacementButton;
    private LucideIconView diagnosticLogPlacementIcon;
    private Tooltip diagnosticLogPlacementTooltip;
    private Label diagnosticLogPathLabel;
    private Tooltip diagnosticLogPathTooltip;

    private HostCatalogSnapshot catalog = new HostCatalogSnapshot(List.of(), List.of(), List.of(), VaultStatus.NOT_CREATED);
    private SnippetSnapshot snippetSnapshot = new SnippetSnapshot(List.of(), List.of(), List.of());
    private long snippetRefreshGeneration;
    private SearchableJumpHostSelector quickJumpSelector;
    private SearchableCredentialSelector quickCredentialSelector;
    private QuickConnectionTestRunner sideQuickConnectionTestRunner;
    private boolean hostSidebarVisible = true;
    private boolean rightDockVisible = true;
    private boolean bottomDockVisible = true;
    private double leftDockWidth = GlobalSettings.DEFAULT_LEFT_DOCK_WIDTH;
    private double rightDockWidth = GlobalSettings.DEFAULT_RIGHT_DOCK_WIDTH;
    private boolean initialVaultPromptShown;
    private ToolWindow selectedLeftTool = ToolWindow.HOST_LIBRARY;
    private ToolWindow selectedRightTool;
    private Map<ToolWindow, Node> toolWindowPages;
    private Map<ToolWindow, Button> toolWindowButtons;
    private ToolWindow draggedToolWindow;
    private boolean leftToolRailDropPreview;
    private boolean rightToolRailDropPreview;
    private List<TreeItem<String>> draggedCatalogItems = List.of();



    private List<TreeItem<String>> pressedCatalogSelection = List.of();



    private SavedHost promptedCredentialHost;
    private CredentialDraft pendingConnectionCredential;
    private String pendingSftpBookmarkKey;
    private boolean resizingFloatingDock;
    private Path requestedInitialLocalTerminalDirectory;
    private boolean initialLocalTerminalRequested;
    private final List<Path> requestedInitialLocalFileDirectories = new ArrayList<>();
    private final List<Path> requestedInitialEditorFiles = new ArrayList<>();
    private CompletableFuture<AutomationLocalTerminal> initialLocalTerminalOpening;
    private Path initialLocalTerminalLaunchDirectory;
    private Path appliedInitialLocalTerminalDirectory;
    private boolean ownedWindowFocusRestorePending;
    private double floatingResizeStartX;
    private double floatingResizeStartWidth;
    private AiSettings aiSettings = AiSettings.defaults();
    private List<com.ynpe.fastremote.ai.AiConversationMessage> aiConversation = new ArrayList<>();
    private Map<String, com.ynpe.fastremote.ai.AiImageInput> aiImagesById = new HashMap<>();
    private List<AiTranscriptEntry> aiTranscript = new ArrayList<>();
    private final List<AiChatSession> aiChatSessions = new ArrayList<>();
    private final Map<String, List<String>> aiModelsByProvider = new HashMap<>();
    private final Set<String> aiProvidersWithoutModels = new HashSet<>();
    private final Map<String, Map<String, AiModelCapabilities>> aiCapabilitiesByProvider = new HashMap<>();
    private final Map<UUID, AiInferenceOptions> aiInferenceByChat = new HashMap<>();
    private final Map<UUID, AiConversationRuntime> aiRuntimes = new java.util.LinkedHashMap<>();
    private long aiChatLoadGeneration;
    private UUID activeAiChatId;
    private AiWorkspaceContext aiWorkspaceContext = AiWorkspaceContext.none();
    private Optional<com.ynpe.fastremote.domain.ai.AiHostAssociation> aiAssociatedHost = Optional.empty();
    private boolean aiAssociationSaving;
    private Tab lastNonAiWorkspaceTab;
    private AiAssistantPlacementController aiPlacement;
    private DiagnosticLogPlacementController diagnosticLogPlacement;
    private WorkspacePaneCoordinator workspacePanes;
    private TextEditorWorkspace textEditors;
    private boolean aiLicenseCheckRunning;
    private boolean aiChatLoading;
    private boolean updatingAiInferenceControls;
    private boolean updatingAiModelSelection;
    private String pendingAiProviderRestore = "";
    private String pendingAiModelRestore = "";
    private InputMethodEnterState aiPromptInputMethodState;
    private AiPromptFocusRestorer aiPromptFocusRestorer;
    private boolean aiAssistantWarmupStarted;
    private boolean configurationDialogLoading;
    private boolean licensePromptVisible;
    private AiChatView aiChatView;

    public MainWindowController(
            ConnectionWorkflow workflow,
            HostCatalogWorkflow catalogWorkflow,
            SettingsWorkflow settingsWorkflow,
            SnippetWorkflow snippetWorkflow) {
        this(workflow, catalogWorkflow, settingsWorkflow, snippetWorkflow, new LocalTerminalWorkflow() {
            @Override public CompletionStage<LocalTerminalHandle> open(com.ynpe.fastremote.domain.settings.TerminalPreferences value) {
                return CompletableFuture.failedFuture(new IllegalStateException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6595de01acc71fa3")));
            }
            @Override public void close() { }
        }, new UnavailableAiAssistantWorkflow(), null);
    }

    public MainWindowController(
            ConnectionWorkflow workflow,
            HostCatalogWorkflow catalogWorkflow,
            SettingsWorkflow settingsWorkflow,
            SnippetWorkflow snippetWorkflow,
            LocalTerminalWorkflow localTerminalWorkflow) {
        this(workflow, catalogWorkflow, settingsWorkflow, snippetWorkflow, localTerminalWorkflow,
                new UnavailableAiAssistantWorkflow(), null);
    }

    public MainWindowController(
            ConnectionWorkflow workflow,
            HostCatalogWorkflow catalogWorkflow,
            SettingsWorkflow settingsWorkflow,
            SnippetWorkflow snippetWorkflow,
            LocalTerminalWorkflow localTerminalWorkflow,
            AiAssistantWorkflow aiAssistantWorkflow,
            InteractiveAutomationGateway automationGateway) {
        this(workflow, catalogWorkflow, settingsWorkflow, snippetWorkflow, localTerminalWorkflow,
                aiAssistantWorkflow, automationGateway, com.ynpe.fastremote.i18n.Localizer.systemDefault());
    }

    public MainWindowController(
            ConnectionWorkflow workflow,
            HostCatalogWorkflow catalogWorkflow,
            SettingsWorkflow settingsWorkflow,
            SnippetWorkflow snippetWorkflow,
            LocalTerminalWorkflow localTerminalWorkflow,
            AiAssistantWorkflow aiAssistantWorkflow,
            InteractiveAutomationGateway automationGateway,
            com.ynpe.fastremote.i18n.Localizer localizer) {
        this(workflow, catalogWorkflow, settingsWorkflow, snippetWorkflow, localTerminalWorkflow,
                aiAssistantWorkflow, automationGateway, localizer,
                ignored -> CompletableFuture.completedFuture(true), unavailableLicenseOperation(),
                unavailableLicenseOperation(), () -> { }, () -> { });
    }

    public MainWindowController(
            ConnectionWorkflow workflow,
            HostCatalogWorkflow catalogWorkflow,
            SettingsWorkflow settingsWorkflow,
            SnippetWorkflow snippetWorkflow,
            LocalTerminalWorkflow localTerminalWorkflow,
            AiAssistantWorkflow aiAssistantWorkflow,
            InteractiveAutomationGateway automationGateway,
            com.ynpe.fastremote.i18n.Localizer localizer,
            com.ynpe.fastremote.domain.license.LicenseAccess licenseAccess) {
        this(workflow, catalogWorkflow, settingsWorkflow, snippetWorkflow, localTerminalWorkflow,
                aiAssistantWorkflow, automationGateway, localizer, licenseAccess, unavailableLicenseOperation(),
                unavailableLicenseOperation(), () -> { }, () -> { });
    }

    public MainWindowController(
            ConnectionWorkflow workflow,
            HostCatalogWorkflow catalogWorkflow,
            SettingsWorkflow settingsWorkflow,
            SnippetWorkflow snippetWorkflow,
            LocalTerminalWorkflow localTerminalWorkflow,
            AiAssistantWorkflow aiAssistantWorkflow,
            InteractiveAutomationGateway automationGateway,
            com.ynpe.fastremote.i18n.Localizer localizer,
            com.ynpe.fastremote.domain.license.LicenseAccess licenseAccess,
            Runnable openLicenseActivation) {
        this(workflow, catalogWorkflow, settingsWorkflow, snippetWorkflow, localTerminalWorkflow,
                aiAssistantWorkflow, automationGateway, localizer, licenseAccess, unavailableLicenseOperation(),
                unavailableLicenseOperation(), openLicenseActivation, () -> { });
    }

    public MainWindowController(
            ConnectionWorkflow workflow,
            HostCatalogWorkflow catalogWorkflow,
            SettingsWorkflow settingsWorkflow,
            SnippetWorkflow snippetWorkflow,
            LocalTerminalWorkflow localTerminalWorkflow,
            AiAssistantWorkflow aiAssistantWorkflow,
            InteractiveAutomationGateway automationGateway,
            com.ynpe.fastremote.i18n.Localizer localizer,
            com.ynpe.fastremote.domain.license.LicenseAccess licenseAccess,
            java.util.function.Supplier<CompletionStage<com.ynpe.fastremote.domain.license.LicenseSnapshot>> licenseStatus,
            java.util.function.Supplier<CompletionStage<com.ynpe.fastremote.domain.license.LicenseSnapshot>> deactivateLicense,
            Runnable openLicenseActivation,
            Runnable showSoftLicenseReminder) {
        this(workflow, catalogWorkflow, settingsWorkflow, snippetWorkflow, localTerminalWorkflow,
                aiAssistantWorkflow, automationGateway, localizer, licenseAccess, licenseStatus,
                deactivateLicense, openLicenseActivation, showSoftLicenseReminder,
                com.ynpe.fastremote.domain.settings.DiagnosticLogOutput.discarding());
    }

    public MainWindowController(
            ConnectionWorkflow workflow,
            HostCatalogWorkflow catalogWorkflow,
            SettingsWorkflow settingsWorkflow,
            SnippetWorkflow snippetWorkflow,
            LocalTerminalWorkflow localTerminalWorkflow,
            AiAssistantWorkflow aiAssistantWorkflow,
            InteractiveAutomationGateway automationGateway,
            com.ynpe.fastremote.i18n.Localizer localizer,
            com.ynpe.fastremote.domain.license.LicenseAccess licenseAccess,
            java.util.function.Supplier<CompletionStage<com.ynpe.fastremote.domain.license.LicenseSnapshot>> licenseStatus,
            java.util.function.Supplier<CompletionStage<com.ynpe.fastremote.domain.license.LicenseSnapshot>> deactivateLicense,
            Runnable openLicenseActivation,
            Runnable showSoftLicenseReminder,
            com.ynpe.fastremote.domain.settings.DiagnosticLogOutput diagnosticLogOutput) {
        this.workflow = Objects.requireNonNull(workflow, "workflow");
        this.catalogWorkflow = Objects.requireNonNull(catalogWorkflow, "catalogWorkflow");
        this.settingsWorkflow = Objects.requireNonNull(settingsWorkflow, "settingsWorkflow");
        this.snippetWorkflow = Objects.requireNonNull(snippetWorkflow, "snippetWorkflow");
        this.localTerminalWorkflow = Objects.requireNonNull(localTerminalWorkflow, "localTerminalWorkflow");
        this.aiAssistantWorkflow = Objects.requireNonNull(aiAssistantWorkflow, "aiAssistantWorkflow");
        this.automationGateway = automationGateway;
        this.localizer = Objects.requireNonNull(localizer, "localizer");
        this.licenseAccess = Objects.requireNonNull(licenseAccess, "licenseAccess");
        this.licenseStatus = Objects.requireNonNull(licenseStatus, "licenseStatus");
        this.deactivateLicense = Objects.requireNonNull(deactivateLicense, "deactivateLicense");
        this.openLicenseActivation = Objects.requireNonNull(openLicenseActivation, "openLicenseActivation");
        this.showSoftLicenseReminder = Objects.requireNonNull(showSoftLicenseReminder,
                "showSoftLicenseReminder");
        this.diagnosticLogOutput = Objects.requireNonNull(diagnosticLogOutput, "diagnosticLogOutput");
        snippetLibraryEditor = new SnippetLibraryEditor(snippetWorkflow, () -> snippetSnapshot, new LibraryEditorActions() {
            @Override public Window owner() { return snippetDialogOwner(); }
            @Override public void refresh() { refreshSnippets(); }
            @Override public void status(String message, boolean error) { setStatus(message, error ? StatusTone.ERROR : StatusTone.IDLE); }
        }, snippetTemplateEngine);
        variableLibraryEditor = new VariableLibraryEditor(snippetWorkflow, () -> snippetSnapshot, new LibraryEditorActions() {
            @Override public Window owner() { return rootPane.getScene().getWindow(); }
            @Override public void refresh() { refreshSnippets(); }
            @Override public void status(String message, boolean error) { setStatus(message, error ? StatusTone.ERROR : StatusTone.IDLE); }
        });
        credentialLibraryEditor = new CredentialLibraryEditor(catalogWorkflow, () -> catalog, new CredentialLibraryEditor.Actions() {
            @Override public Window owner() { return rootPane.getScene().getWindow(); }
            @Override public void refresh() { refreshCatalog(); }
            @Override public void status(String message, boolean error) { setStatus(message, error ? StatusTone.ERROR : StatusTone.IDLE); }
            @Override public void manageVault() { MainWindowController.this.manageVault(); }
            @Override public CompletionStage<ResolvedCredential> readSavedCredential(Window owner, SecretId id) { return MainWindowController.this.readSavedCredential(owner, id); }
        });

    }

    private static java.util.function.Supplier<CompletionStage<com.ynpe.fastremote.domain.license.LicenseSnapshot>> unavailableLicenseOperation() {
        return () -> CompletableFuture.completedFuture(new com.ynpe.fastremote.domain.license.LicenseSnapshot(
                com.ynpe.fastremote.domain.license.LicenseStatus.TRIAL_AVAILABLE, "", Optional.empty(),
                Optional.empty(), Optional.empty(), Set.of(), ""));
    }

    @FXML
    private void initialize() {
        // Keep dynamic Java-created controls on the same immutable startup locale as the FXML bundle.
        com.ynpe.fastremote.i18n.Texts.configure(localizer);
        MainMenuClickSupport.install(mainMenuBar);
        GlobalToolbarPresentation.install(mainToolbar,
                () -> persistedWorkspaceSettings.windowSettings().toolbarDisplayMode(),
                this::setToolbarDisplayMode, () -> showSettings(ConfigurationDialog.TOOLBAR_SECTION));
        bindLibraryViews();
        bindAiAssistantView();
        expansionSaveDelay.setOnFinished(ignored -> persistLibraryGroupExpansion());
        windowBoundsSaveDelay.setOnFinished(ignored -> persistWindowBounds());
        horizontalDockRestoreDelay.setOnFinished(ignored -> restoreHorizontalDockWidths());
        aiPlacement = new AiAssistantPlacementController(aiAssistantPage, workspaceTabs, this::onAiPlacementChanged);
        diagnosticLogPlacement = new DiagnosticLogPlacementController(
                diagnosticLogPage, workspaceTabs, this::onDiagnosticLogPlacementChanged);
        initializeWorkbenchLayout();
        trafficLogPulse.setCycleCount(Timeline.INDEFINITE);
        trafficLogPulse.play();
        mcpStatusPulse.setCycleCount(Timeline.INDEFINITE);
        mcpStatusPulse.play();
        refreshMcpStatusBar();
        setStatus(localizer.text("status.ready"), StatusTone.IDLE);
        if (commercialLicensing) {
            refreshLicenseControls();
        } else {
            activateLicenseMenuItem.setVisible(false);
            deactivateLicenseMenuItem.setVisible(false);
            licenseMenuSeparator.setVisible(false);
            toolbarActivationStatusLabel.setManaged(false);
            toolbarActivationStatusLabel.setVisible(false);
        }
        updateMenuItem.setVisible(applicationUpdates);
        updateMenuSeparator.setVisible(applicationUpdates);
        installCommandLineToolsMenuItem.setVisible(applicationUpdates);
        applyWorkspaceSettings(GlobalSettings.defaults());
        authenticationField.getItems().setAll(AuthenticationType.PASSWORD, AuthenticationType.PRIVATE_KEY);
        authenticationField.setCellFactory(ignored -> quickAuthenticationCell());
        authenticationField.setButtonCell(quickAuthenticationCell());
        authenticationField.setValue(AuthenticationType.PASSWORD);
        credentialSourceField.getItems().setAll(QuickCredentialSource.values());
        credentialSourceField.setValue(QuickCredentialSource.MANUAL);
        initializeQuickConnectFieldLabels();
        quickJumpSelector = new SearchableJumpHostSelector(
                jumpHostField, List.of(), List.of(), Optional.empty(), Optional.empty());
        quickCredentialSelector = new SearchableCredentialSelector(
                credentialProfileField, List.of(), List.of(), Optional.empty());
        sideQuickConnectionTestRunner = new QuickConnectionTestRunner(
                () -> rootPane.getScene().getWindow(),
                () -> quickConnectionTestRequest(
                        hostField, portField, usernameField, authenticationField.getValue(),
                        credentialSourceField.getValue(), quickCredentialSelector.selectedCredential().orElse(null),
                        hostPasswordField.getText(), privateKeyField.getText(),
                        privateKeyPassphraseField.getText(), legacyAlgorithmsField.isSelected(),
                        quickJumpHostId()),
                quickConnectFields, testQuickConnectionButton,
                List.of(parseQuickConnectClipboardButton, saveQuickHostButton, disconnectButton, connectButton),
                quickConnectionTestStatus);
        installQuickConnectionTestReset(
                sideQuickConnectionTestRunner, hostField, portField, usernameField,
                authenticationField, credentialSourceField,
                hostPasswordField, privateKeyField, privateKeyPassphraseField, legacyAlgorithmsField);
        quickJumpSelector.setOnSelectionChanged(sideQuickConnectionTestRunner::clearCompletedStatus);
        credentialSourceField.valueProperty().addListener((ignored, previous, selected) ->
                updateQuickCredentialSource());
        quickCredentialSelector.setOnSelectionChanged(() -> {
            applyCredentialChoice(quickCredentialSelector.selectedCredential().orElse(null));
            sideQuickConnectionTestRunner.clearCompletedStatus();
        });
        initializeCredentialTree();
        authenticationField.valueProperty().addListener((ignored, previous, selected) -> updateAuthenticationFields());
        initializeHostTree();
        refreshSnippets();
        initializeVariableLibrary();
        initializeHomePage();
        initializeWorkspaceTabs();
        workspacePanes = new WorkspacePaneCoordinator(workspaceTabs, this::closeWorkspacePane,
                this::confirmWorkspaceClose,
                this::installWorkspaceTabContextMenu,
                this::remoteSessionHostName,
                () -> {
                    updateWorkspaceVisibility();
                    updateActions();
                    refreshWorkspaceQuickTools();
                    refreshAiHostAssociation();
                });
        textEditors = new TextEditorWorkspace(workspacePanes, workflow,
                () -> rootPane.getScene().getWindow(),
                () -> persistedWorkspaceSettings.sftpSettings(),
                message -> setStatus(message, StatusTone.IDLE),
                () -> { updateWorkspaceVisibility(); updateActions(); },
                this::installWorkspaceTabContextMenu,
                this::revealLocalEditorFile);
        textEditors.bindRecentMenu(openRecentMenu);
        installExternalFileDropTarget();
        configureTextEditorMenuShortcuts();
        initializeAiAssistantControls();
        updateWorkspaceVisibility();
        workspaceTabs.getSelectionModel().selectedItemProperty().addListener(
                (ignored, previous, selected) -> {
                    if (previous != null && previous != selected
                            && workspaceTabs.getTabs().contains(previous)) {
                        pendingWorkspaceCloseFallbacks.remove(previous);
                    }
                    if (selected != null) workspaceActivationHistory.activated(selected);
                    if (selected != null && !aiPlacement.isWorkspaceTab(selected)
                            && !diagnosticLogPlacement.isWorkspaceTab(selected)) {
                        lastNonAiWorkspaceTab = workspacePanes.activePane(selected);
                    }
                    else if (previous != null && !aiPlacement.isWorkspaceTab(previous)
                            && !diagnosticLogPlacement.isWorkspaceTab(previous)) {
                        lastNonAiWorkspaceTab = workspacePanes.activePane(previous);
                    }
                    updateSelectedConnectionStatus();
                    refreshAiHostAssociation();
                    if (aiPlacement.isWorkspaceTab(selected)) requestAiPromptFocusIfInitialized();
                });
        workspaceTabs.getTabs().addListener((ListChangeListener<Tab>) change -> {
            while (change.next()) {
                if (!change.wasRemoved()) continue;
                for (Tab removed : change.getRemoved()) {
                    workspaceActivationHistory.removed(removed);
                    Tab fallback = pendingWorkspaceCloseFallbacks.remove(removed);
                    if (fallback != null && workspaceTabs.getTabs().contains(fallback)) {
                        Platform.runLater(() -> {
                            if (workspaceTabs.getTabs().contains(fallback)) {
                                workspaceTabs.getSelectionModel().select(fallback);
                            }
                        });
                    }
                }
            }
        });
        updateAuthenticationFields();
        updateActions();
        settingsWorkflow.load().whenComplete((settings, error) -> Platform.runLater(() -> {
            if (error != null) {
                diagnosticLogOutput.configure(GlobalSettings.defaults().diagnosticLogSettings());
                applyWorkspaceSettings(GlobalSettings.defaults());
                workspaceSettingsLoaded = true;
                openRequestedInitialEditorFiles();
                restoreMainWindowBounds();
                if (initialLocalTerminalRequested) {
                    beginInitialLocalTerminal(requestedInitialLocalTerminalDirectory);
                } else {
                    finishInitialWorkspacePresentation();
                    initialWindowGate.markReady();
                }
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ffc319bd26db29f5"), StatusTone.ERROR);
            } else {
                diagnosticLogOutput.configure(settings.diagnosticLogSettings());
                applyWorkspaceSettings(settings);
                workspaceSettingsLoaded = true;
                openRequestedInitialEditorFiles();
                restoreMainWindowBounds();
                if (initialLocalTerminalRequested || settings.openLocalTerminalOnStartup()) {
                    beginInitialLocalTerminal(requestedInitialLocalTerminalDirectory);
                } else {
                    finishInitialWorkspacePresentation();
                    initialWindowGate.markReady();
                }
            }
        }));
        catalogWorkflow.tryUnlockRememberedVault().whenComplete((unlocked, error) -> Platform.runLater(() -> {
            if (error != null) {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.bd0f3e57b26d2533"), StatusTone.ERROR);
                requestVaultPassword(false);
            } else if (Boolean.TRUE.equals(unlocked)) {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5bb8755c9fbefcb1"), StatusTone.IDLE);
            }
            refreshCatalog();
        }));

    }

    private void bindLibraryViews() {
        HostLibraryController hosts = Objects.requireNonNull(
                hostLibraryPageController, "Missing included host library controller");
        hosts.bind(new HostLibraryController.Actions() {
            @Override public void createHost() { MainWindowController.this.createHost(); }
            @Override public void createGroup() { MainWindowController.this.createGroup(); }
        });
        hostSearchField = hosts.searchField();
        hostAllFilter = hosts.allFilter();
        hostFavoriteFilter = hosts.favoriteFilter();
        hostRecentFilter = hosts.recentFilter();
        hostTree = hosts.tree();

        SnippetLibraryController snippets = Objects.requireNonNull(
                snippetsPageController, "Missing included snippet library controller");
        bindSnippetLibrary(snippets);
        snippetSearchField = snippets.searchField();

        VariableLibraryController variables = Objects.requireNonNull(
                variableLibraryPageController, "Missing included variable library controller");
        variables.bind(new VariableLibraryController.Actions() {
            @Override public void createVariable() { MainWindowController.this.createVariable(); }
            @Override public void createGroup() { createVariableGroup(); }
        });
        variableSearchField = variables.searchField();
        variableTree = variables.tree();

        CredentialLibraryController credentials = Objects.requireNonNull(
                credentialVaultPageController, "Missing included credential library controller");
        credentials.bind(new CredentialLibraryController.Actions() {
            @Override public void createCredential() { createReusableCredential(); }
            @Override public void createGroup() { createCredentialGroup(); }
        });
        credentialSearchField = credentials.searchField();
        credentialTree = credentials.tree();
        credentialEmpty = credentials.emptyState();

        DiagnosticLogController diagnostics = Objects.requireNonNull(
                diagnosticLogPageController, "Missing included diagnostic log controller");
        diagnostics.bind(new DiagnosticLogController.Actions() {
            @Override public void togglePlacement() { toggleDiagnosticLogPlacement(); }
            @Override public void clear() { clearDiagnosticLog(); }
            @Override public void revealCurrentFile() { revealCurrentDiagnosticLogFile(); }
            @Override public void openCleanupSettings() {
                showSettings(com.ynpe.fastremote.i18n.Texts.text("diagnostic.log.settingsTitle"));
            }
        });
        diagnosticLogTextArea = diagnostics.textArea();
        diagnosticTextLog.attach(diagnosticLogTextArea);
        diagnosticLogPlacementButton = diagnostics.placementButton();
        diagnosticLogPlacementIcon = diagnostics.placementIcon();
        diagnosticLogPlacementTooltip = diagnostics.placementTooltip();
        diagnosticLogPathLabel = diagnostics.pathLabel();
        diagnosticLogPathTooltip = diagnostics.pathTooltip();
        updateDiagnosticLogPath();
    }

    private void bindAiAssistantView() {
        AiAssistantController view = Objects.requireNonNull(
                aiAssistantPageController, "Missing included AI assistant controller");
        view.bind(new AiAssistantController.Actions() {
            @Override public void sessionsCollapsedChanged(boolean collapsed) {
                persistedWorkspaceSettings = persistedWorkspaceSettings.withAiSessionsCollapsed(collapsed);
                settingsWorkflow.save(persistedWorkspaceSettings).whenComplete((ignored, error) -> {
                    if (error != null) Platform.runLater(() -> setStatus(messageFor(error,
                            localizer.text("config.failed")), StatusTone.ERROR));
                });
            }
            @Override public void togglePlacement() { toggleAiPlacement(); }
            @Override public void createChat() { createAiChat(); }
            @Override public void selectChat(UUID id) {
                if (!aiChatLoading && !id.equals(activeAiChatId)) activateAiChat(id);
            }
            @Override public void configure() { configureAiAssistant(); }
            @Override public void sendOrInterrupt() { sendOrInterruptAiMessage(); }
            @Override public void stop() { interruptAiRequest(); }
            @Override public void steer() { sendAiMessage(true); }
            @Override public void resumeQueue() {
                var runtime = currentAiRuntime();
                if (runtime == null || runtime.running || runtime.queueSaving) return;
                runtime.pending.paused = false;
                configureAiRuntime(runtime); sendNextPendingAiPrompt(runtime); updateAiActionState();
            }
            @Override public void editQueued(UUID id, String text) {
                var runtime = currentAiRuntime();
                if (runtime == null || runtime.queueSaving) return;
                if (text.isBlank() && runtime.pending.find(id).orElseThrow().images().isEmpty()) return;
                runtime.pending.edit(id, text); saveAiQueue(runtime, () -> {});
            }
            @Override public void deleteQueued(UUID id) {
                var runtime = currentAiRuntime();
                if (runtime == null || runtime.queueSaving) return;
                runtime.pending.remove(id); saveAiQueue(runtime, () -> {});
            }
            @Override public void steerQueued(UUID id) {
                var runtime = currentAiRuntime();
                if (runtime != null && !runtime.queueSaving) steerAiPrompt(runtime, id);
            }
            @Override public void imageStateChanged() { updateAiActionState(); }
            @Override public void imageError(String message) { setStatus(message, StatusTone.ERROR); }
            @Override public void associateHost(Optional<com.ynpe.fastremote.domain.ai.AiHostAssociation> host) {
                saveAiAssociatedHost(host);
            }
        });
        aiProviderField = view.providerField();
        aiModelField = view.modelField();
        aiReasoningField = view.reasoningField();
        aiReasoningSummaryField = view.reasoningSummaryField();
        aiConversationHost = view.conversationHost();
        aiPromptField = view.promptField();
        aiPromptFocusRestorer = new AiPromptFocusRestorer(aiPromptField, aiAssistantPage);
        aiSendButton = view.sendButton();
        aiSendIcon = view.sendIcon();
        aiSendTooltip = view.sendTooltip();
        aiNewChatButton = view.newChatButton();
        view.configureSessionManagement(new AiSessionListController.Actions() {
            @Override public CompletionStage<?> rename(UUID id, String title) {
                return aiAssistantWorkflow.renameChatSession(id, title).thenAccept(session ->
                        Platform.runLater(() -> rememberAiChat(session)));
            }
            @Override public CompletionStage<?> pin(List<UUID> ids, boolean pinned) {
                return aiAssistantWorkflow.setChatsPinned(ids, pinned).thenAccept(sessions ->
                        Platform.runLater(() -> { sessions.forEach(MainWindowController.this::rememberAiChat); refreshAiChatSessions(); }));
            }
            @Override public CompletionStage<?> delete(List<UUID> ids) {
                if (ids.stream().anyMatch(this::running)) return CompletableFuture.failedFuture(
                        new IllegalStateException("A selected conversation is generating"));
                aiChatLoading = true;
                updateAiActionState();
                return aiAssistantWorkflow.deleteChatSessions(ids).whenComplete((ignored, error) -> Platform.runLater(() -> {
                    aiChatLoading = false;
                    if (error != null) { updateAiActionState(); return; }
                    aiChatSessions.removeIf(session -> ids.contains(session.id()));
                    for (UUID id : ids) {
                        AiConversationRuntime removed = aiRuntimes.remove(id);
                        if (removed != null) removed.view.close();
                    }
                    view.updateSessions(aiChatSessions, activeAiChatId);
                    if (ids.contains(activeAiChatId)) {
                        if (aiChatSessions.isEmpty()) createAiChat();
                        else activateAiChat(aiChatSessions.getFirst().id());
                    }
                    updateAiActionState();
                }));
            }
            @Override public boolean running(UUID id) {
                AiConversationRuntime runtime = aiRuntimes.get(id);
                return runtime != null && (runtime.running || runtime.queueSaving);
            }
            @Override public Window owner() { return aiInteractionOwner(); }
            @Override public void failed(Throwable error) {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("ai.sessions.operationFailed") + ": "
                        + messageFor(unwrap(error), ""), StatusTone.ERROR);
            }
        });
        view.sessionList().setOnKeyReleased(event -> {
            if (!view.batchSessions() && event.getCode() == KeyCode.ENTER && view.sessionList().getSelectionModel().getSelectedItem() != null) {
                activateAiChat(view.sessionList().getSelectionModel().getSelectedItem().id());
                event.consume();
            }
        });
        aiPlacementButton = view.placementButton();
        aiPlacementIcon = view.placementIcon();
        aiPlacementTooltip = view.placementTooltip();
        aiChatTitleLabel = view.chatTitleLabel();
    }

    private void initializeQuickConnectFieldLabels() {
        quickConnectFields.getChildren().stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .forEach(label -> {
                    String original = label.getText();
                    label.setAccessibleText(original);
                    label.setText(FormLabel.withSoftWrapOpportunities(original));
                });
    }

    public void attachMainWindow(Stage stage) {
        if (mainWindow != null) throw new IllegalStateException("Main window is already attached");
        mainWindow = Objects.requireNonNull(stage, "stage");
        ThemeManager.install(stage.getScene());
        keyboardShortcutManager = new KeyboardShortcutManager(stage.getScene(), this::runShortcut);
        applyApplicationPreferences(persistedWorkspaceSettings);
        javafx.beans.value.ChangeListener<Number> changed = (ignored, previous, current) -> scheduleWindowBoundsSave();
        stage.xProperty().addListener(changed);
        stage.yProperty().addListener(changed);
        stage.widthProperty().addListener((ignored, previous, current) -> {
            scheduleWindowBoundsSave();
            requestHorizontalDockWidthRestore();
        });
        stage.heightProperty().addListener(changed);
        Window.getWindows().addListener(ownedWindowListListener);
        ownedWindowFocusRestoreDelay.setOnFinished(ignored -> {
            ownedWindowFocusRestorePending = false;
            hideFloatingDocksUnlessOwnedWindowIsShowing();
            hideFloatingTerminalActivitiesUnlessOwnedWindowIsShowing(stage.getScene().getFocusOwner());
        });
        stage.focusedProperty().addListener((ignored, previous, focused) -> {
            if (!focused) Platform.runLater(() -> {
                hideFloatingDocksUnlessOwnedWindowIsShowing();
                hideFloatingTerminalActivitiesUnlessOwnedWindowIsShowing(null);
            });
        });
        stage.getScene().focusOwnerProperty().addListener((ignored, previous, focused) ->
                Platform.runLater(() -> {
                    Node currentFocus = stage.getScene().getFocusOwner();
                    hideFloatingDocksAfterFocusChange(currentFocus);
                    hideFloatingTerminalActivitiesUnlessOwnedWindowIsShowing(currentFocus);
                }));
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, ignored -> Platform.runLater(() -> {
            restoreMainWindowBounds();
            rootPane.applyCss();
            rootPane.layout();
            requestHorizontalDockWidthRestore();
        }));
        mainWindowCloseHandler = event -> {
            event.consume();
            closeMainWindow.run();
        };
        stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, mainWindowCloseHandler);
        if (workspaceSettingsLoaded) restoreMainWindowBounds();
    }

    public void configureApplicationLifecycle(Runnable closeWindow, Runnable exitProcess) {
        closeMainWindow = Objects.requireNonNull(closeWindow, "closeWindow");
        closeApplication = Objects.requireNonNull(exitProcess, "exitProcess");
    }

    public void configureCommandPaletteUsage(CommandPaletteUsageWorkflow workflow) {
        commandPaletteUsageWorkflow = Objects.requireNonNull(workflow, "workflow");
        workflow.load().whenComplete((loaded, error) -> {
            if (error != null) {
                System.getLogger(MainWindowController.class.getName()).log(
                        System.Logger.Level.WARNING, "Unable to load command palette usage", unwrap(error));
                return;
            }
            Platform.runLater(() -> {
                if (controllerClosed.get()) return;
                for (CommandPaletteUsage usage : loaded) commandPaletteUsage.merge(
                        usage.entryKey(), usage, MainWindowController::newerCommandPaletteUsage);
            });
        });
    }

    private static CommandPaletteUsage newerCommandPaletteUsage(
            CommandPaletteUsage left, CommandPaletteUsage right) {
        if (left.useCount() != right.useCount()) return left.useCount() > right.useCount() ? left : right;
        return left.lastUsedAt().isAfter(right.lastUsedAt()) ? left : right;
    }

    public boolean exitOnMainWindowClose() {
        return persistedWorkspaceSettings.windowSettings().exitOnMainWindowClose();
    }

    public boolean confirmWindowClose() {
        return confirmMainWindowClose(false) && confirmEditorClose();
    }

    public boolean confirmApplicationExit() {
        return confirmMainWindowClose(true) && confirmEditorClose();
    }

    /** Closes every workspace tab after the enclosing lifecycle obtained confirmation. */
    public void closeAllWorkspaceTabs() {
        closeMainTabs(List.copyOf(workspaceTabs.getTabs()), false);
    }

    private boolean confirmEditorClose() {
        return textEditors == null || textEditors.confirmCloseAll();
    }

    public CompletionStage<Void> initialWindowReady() {
        return initialWindowGate.ready();
    }

    public void requestInitialLocalTerminal(Path initialDirectory) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Launch requests must be consumed on the JavaFX application thread");
        }
        initialLocalTerminalRequested = true;
        requestedInitialLocalTerminalDirectory = initialDirectory == null
                ? null : initialDirectory.toAbsolutePath().normalize();
        if (!workspaceSettingsLoaded) return;
        if (initialLocalTerminalOpening == null) {
            beginInitialLocalTerminal(requestedInitialLocalTerminalDirectory);
        } else {
            applyRequestedInitialLocalTerminalDirectory(initialLocalTerminalOpening);
        }
    }

    public void requestAdditionalLocalTerminal(Path initialDirectory) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Launch requests must be consumed on the JavaFX application thread");
        }
        if (!workspaceSettingsLoaded) {
            throw new IllegalStateException("Additional terminal request arrived before settings were loaded");
        }
        openLocalTerminal(false, initialDirectory, null);
    }

    public void requestInitialLocalFileDirectories(List<Path> directories) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Launch requests must be consumed on the JavaFX application thread");
        }
        requestedInitialLocalFileDirectories.addAll(normalizedEditorFiles(directories));
        if (workspaceSettingsLoaded) openRequestedInitialEditorFiles();
    }

    public void requestAdditionalLocalFileDirectories(List<Path> directories) {
        if (!Platform.isFxApplicationThread() || !workspaceSettingsLoaded) {
            throw new IllegalStateException("Local file requests require the ready JavaFX workspace");
        }
        for (Path directory : normalizedEditorFiles(directories)) {
            createLocalFileWorkspace(new LocalWorkspaceSession(directory), directory, null);
        }
    }

    public void requestInitialEditorFiles(List<Path> files) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Launch requests must be consumed on the JavaFX application thread");
        }
        requestedInitialEditorFiles.addAll(normalizedEditorFiles(files));
        if (workspaceSettingsLoaded) openRequestedInitialEditorFiles();
    }

    public void requestAdditionalEditorFiles(List<Path> files) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Launch requests must be consumed on the JavaFX application thread");
        }
        if (!workspaceSettingsLoaded) {
            throw new IllegalStateException("Additional editor request arrived before settings were loaded");
        }
        openEditorFiles(normalizedEditorFiles(files));
    }

    private void openRequestedInitialEditorFiles() {
        List<Path> directories = List.copyOf(requestedInitialLocalFileDirectories);
        requestedInitialLocalFileDirectories.clear();
        requestAdditionalLocalFileDirectories(directories);
        if (requestedInitialEditorFiles.isEmpty()) return;
        List<Path> files = List.copyOf(requestedInitialEditorFiles);
        requestedInitialEditorFiles.clear();
        openEditorFiles(files);
    }

    private void openEditorFiles(List<Path> files) {
        for (Path file : files) {
            textEditors.openLocalInNewTab(file, message -> setStatus(message, StatusTone.ERROR));
        }
    }

    private static List<Path> normalizedEditorFiles(List<Path> files) {
        return Objects.requireNonNull(files, "files").stream()
                .filter(Objects::nonNull)
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
    }

    /** Opens the standalone quick-connect dialog for an enclosing application-level action. */
    public void requestQuickConnectDialog() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Quick Connect requests must run on the JavaFX application thread");
        }
        if (!workspaceSettingsLoaded) {
            throw new IllegalStateException("Quick Connect request arrived before settings were loaded");
        }
        showQuickConnectDialog();
    }

    private void scheduleWindowBoundsSave() {
        if (!workspaceSettingsLoaded || restoringWindowBounds || mainWindow == null
                || !persistedWorkspaceSettings.windowSettings().restoreWindowBounds()) return;
        windowBoundsSaveDelay.playFromStart();
    }

    private void restoreMainWindowBounds() {
        if (windowBoundsRestored || !workspaceSettingsLoaded || mainWindow == null) return;
        // Only GTK needs the native window's output scale before restoring logical dimensions.
        // On macOS/Windows restore before initialWindowReady allows the first show, otherwise
        // the default-sized window flashes before the queued WINDOW_SHOWN callback resizes it.
        if (LocalizedTextInputContextMenus.isLinux() && !mainWindow.isShowing()) return;
        windowBoundsRestored = true;
        WindowSettings settings = persistedWorkspaceSettings.windowSettings();
        if (!settings.restoreWindowBounds() || settings.lastBounds().isEmpty()) return;
        WindowBounds restored = visibleWindowBounds(
                settings.lastBounds().get(), Screen.getScreens().stream().map(Screen::getVisualBounds).toList(),
                mainWindow.getMinWidth(), mainWindow.getMinHeight());
        restoringWindowBounds = true;
        try {
            mainWindow.setWidth(restored.width());
            mainWindow.setHeight(restored.height());
            mainWindow.setX(restored.x());
            mainWindow.setY(restored.y());
        } finally {
            restoringWindowBounds = false;
        }
        requestHorizontalDockWidthRestore();
    }

    private void persistWindowBounds() {
        if (!workspaceSettingsLoaded || mainWindow == null
                || !persistedWorkspaceSettings.windowSettings().restoreWindowBounds()) return;
        WindowBounds bounds;
        try {
            bounds = new WindowBounds(mainWindow.getX(), mainWindow.getY(),
                    mainWindow.getWidth(), mainWindow.getHeight());
        } catch (IllegalArgumentException invalid) {
            return;
        }
        WindowSettings current = persistedWorkspaceSettings.windowSettings();
        if (current.lastBounds().filter(bounds::equals).isPresent()) return;
        GlobalSettings settings = persistedWorkspaceSettings.withWindowSettings(current.withLastBounds(bounds));
        persistedWorkspaceSettings = settings;
        settingsWorkflow.save(settings).whenComplete((ignored, error) -> {
            if (error != null && !controllerClosed.get()) Platform.runLater(() ->
                    setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.0d7e4b2eb8f99467")), StatusTone.ERROR));
        });
    }

    @FXML
    private void openLocalTerminal() {
        openLocalTerminal(false);
    }

    private void openLocalTerminal(boolean initialTerminal) {
        openLocalTerminal(initialTerminal, null, null);
    }

    private void openLocalTerminal(boolean initialTerminal,
            CompletableFuture<AutomationLocalTerminal> automationResult) {
        openLocalTerminal(initialTerminal, null, automationResult);
    }

    private void openLocalTerminal(boolean initialTerminal, Path initialDirectory,
            CompletableFuture<AutomationLocalTerminal> automationResult) {
        openLocalTerminal(initialTerminal, initialDirectory, automationResult, false, true);
    }

    private void beginInitialLocalTerminal(Path initialDirectory) {
        Path normalizedDirectory = initialDirectory == null
                ? null : initialDirectory.toAbsolutePath().normalize();
        CompletableFuture<AutomationLocalTerminal> opening = new CompletableFuture<>();
        initialLocalTerminalOpening = opening;
        initialLocalTerminalLaunchDirectory = normalizedDirectory;
        appliedInitialLocalTerminalDirectory = null;
        openLocalTerminal(true, normalizedDirectory, opening);
        opening.whenComplete((terminal, error) -> {
            Runnable completeInitialWindow = () -> {
                if (error == null && !controllerClosed.get()) {
                    applyRequestedInitialLocalTerminalDirectory(opening);
                }
                initialWindowGate.markReady();
            };
            if (Platform.isFxApplicationThread()) completeInitialWindow.run();
            else Platform.runLater(completeInitialWindow);
        });
    }

    private void applyRequestedInitialLocalTerminalDirectory(
            CompletableFuture<AutomationLocalTerminal> opening) {
        if (!opening.isDone() || opening.isCompletedExceptionally()
                || opening != initialLocalTerminalOpening || controllerClosed.get()) return;
        AutomationLocalTerminal terminal = opening.getNow(null);
        Path requestedDirectory = requestedInitialLocalTerminalDirectory;
        if (terminal == null || requestedDirectory == null) return;
        if (requestedDirectory.equals(initialLocalTerminalLaunchDirectory)) {
            appliedInitialLocalTerminalDirectory = requestedDirectory;
            return;
        }
        if (requestedDirectory.equals(appliedInitialLocalTerminalDirectory)) return;
        appliedInitialLocalTerminalDirectory = requestedDirectory;
        sendLocalTerminalDirectory(terminal.handle(), requestedDirectory.toString());
    }

    private void openLocalTerminal(boolean initialTerminal, Path initialDirectory,
            CompletableFuture<AutomationLocalTerminal> automationResult,
            boolean automationOwned, boolean activateTab) {
        openLocalTerminal(initialTerminal, initialDirectory, automationResult,
                automationOwned, activateTab, null);
    }

    private void openLocalTerminal(boolean initialTerminal, Path initialDirectory,
            CompletableFuture<AutomationLocalTerminal> automationResult,
            boolean automationOwned, boolean activateTab, Tab insertionAnchor) {
        openAuthorizedLocalTerminal(initialTerminal, initialDirectory, automationResult,
                automationOwned, activateTab, insertionAnchor);
        if (!initialTerminal) remindIfLicenseMissing();
    }

    private void openLocalTerminalInWorkspace(Tab insertionAnchor) {
        openLocalTerminal(false, null, null, false, true, insertionAnchor);
    }

    private void remindIfLicenseMissing() {
        licenseAccess.permits(com.ynpe.fastremote.domain.license.LicenseCapability.LOCAL_TERMINAL)
                .whenComplete((permitted, error) -> Platform.runLater(() -> {
                    if (!controllerClosed.get() && (error != null || !Boolean.TRUE.equals(permitted))) {
                        showSoftLicenseReminder.run();
                    }
                }));
    }

    private void openAuthorizedLocalTerminal(boolean initialTerminal, Path initialDirectory,
            CompletableFuture<AutomationLocalTerminal> automationResult,
            boolean automationOwned, boolean activateTab, Tab insertionAnchor) {
        openAuthorizedLocalTerminal(initialTerminal, initialDirectory, automationResult,
                automationOwned, activateTab, insertionAnchor, new LocalWorkspaceSession(initialDirectory));
    }

    private void openAuthorizedLocalTerminal(boolean initialTerminal, Path initialDirectory,
            CompletableFuture<AutomationLocalTerminal> automationResult,
            boolean automationOwned, boolean activateTab, Tab insertionAnchor,
            LocalWorkspaceSession localWorkspace) {
        setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3bddda0a27bfd48e"), StatusTone.WORKING);
        CompletionStage<LocalTerminalHandle> opening = automationOwned
                ? localTerminalWorkflow.openAutomation(persistedWorkspaceSettings.terminalPreferences(),
                        initialDirectory == null ? Path.of(System.getProperty("user.home")) : initialDirectory)
                : initialDirectory == null
                ? localTerminalWorkflow.open(persistedWorkspaceSettings.terminalPreferences())
                : localTerminalWorkflow.open(persistedWorkspaceSettings.terminalPreferences(), initialDirectory);
        opening
                .whenComplete((handle, error) -> Platform.runLater(() -> {
                    if (controllerClosed.get()) {
                        if (handle != null) handle.close();
                        if (automationResult != null) automationResult.completeExceptionally(
                                new IllegalStateException("FastRemote is closing"));
                        return;
                    }
                    if (error != null) {
                        if (initialTerminal) finishInitialWorkspacePresentation();
                        setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d383559e09480614")), StatusTone.ERROR);
                        if (automationResult != null) automationResult.completeExceptionally(error);
                        return;
                    }
                    handle.applyKeyboardShortcuts(terminalShortcutSettings());
                    StackPane host = new StackPane(handle.node());
                    host.getStyleClass().add("terminal-host");
                    installTerminalFocusHandler(host, handle::requestFocus);
                    installLocalPathDrop(host, handle);
                    Tab tab = LocalTerminalPresentation.createTab(
                            automationWorkspaceTabTitle(localTerminalTitle(), automationOwned),
                            host, localTerminalWorkflow.isSandboxed(), message -> setStatus(message, StatusTone.ERROR));
                    tab.setGraphic(WorkspaceTabGraphics.localTerminal());
                    tab.setOnCloseRequest(event -> { event.consume(); closeLocalTerminal(tab); });
                    installWorkspaceTabContextMenu(tab);
                    localTerminals.put(tab, handle);
                    localWorkspace.terminalTab(tab);
                    handle.workingDirectory().ifPresent(localWorkspace::directory);
                    localWorkspaceSessions.put(tab, localWorkspace);
                    handle.setContextMenuItems(() -> localTerminalContextMenuItems(tab, handle));
                    tab.getProperties().put(AUTOMATION_OWNED_TAB_KEY, automationOwned);
                    boolean activate = shouldActivateAutomationWorkspace(automationOwned, activateTab);
                    boolean addedBeside = insertionAnchor != null
                            && workspacePanes.addPaneBeside(tab, insertionAnchor,
                            WorkspaceLayoutModel.DropEdge.RIGHT, activate);
                    if (!addedBeside) workspacePanes.addPane(tab, null, activate);
                    if (initialTerminal) initialWorkspacePresentationPending = false;
                    updateWorkspaceVisibility();
                    if (shouldActivateAutomationWorkspace(automationOwned, activateTab)) {
                        workspacePanes.selectPane(tab);
                    }
                    handle.termination().whenComplete((termination, terminationError) -> Platform.runLater(() -> {
                        if (localTerminals.containsKey(tab)) closeLocalTerminal(tab);
                    }));
                    if (shouldActivateAutomationWorkspace(automationOwned, activateTab)) handle.requestFocus();
                    refreshWorkspaceQuickTools();
                    setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7f3c1c0401898014"), StatusTone.CONNECTED);
                    if (automationResult != null) {
                        automationResult.complete(new AutomationLocalTerminal(tab, handle));
                    }
                }));
    }

    private record AutomationLocalTerminal(Tab tab, LocalTerminalHandle handle) { }

    private void closeLocalTerminal(Tab tab) {
        LocalTerminalHandle handle = localTerminals.remove(tab);
        if (handle == null) return;
        LocalWorkspaceSession localWorkspace = localWorkspaceSessions.remove(tab);
        if (localWorkspace != null) {
            localWorkspace.terminalTab(null);
            handle.workingDirectory().ifPresent(localWorkspace::directory);
        }
        tab.setOnCloseRequest(null);
        workspacePanes.removePane(tab);
        tab.getProperties().remove(AUTOMATION_OWNED_TAB_KEY);
        handle.close();
        updateWorkspaceVisibility();
        setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5fd3506d7df76e70"), StatusTone.IDLE);
        updateActions();
    }

    private void openPairedLocalFiles(Tab sourceTab, LocalTerminalHandle source) {
        LocalWorkspaceSession localWorkspace = localWorkspaceSessions.get(sourceTab);
        if (localWorkspace == null) return;
        Tab existing = localWorkspace.filesTab().orElse(null);
        if (existing != null && localFileWorkspaces.containsKey(existing)) {
            selectWorkspaceTab(existing);
            return;
        }
        source.resolveWorkingDirectory().whenComplete((resolved, error) -> Platform.runLater(() -> {
            if (localTerminals.get(sourceTab) != source) return;
            Tab anchor = persistedWorkspaceSettings.windowSettings()
                    .openLocalFilesFromTerminalInCurrentWorkspace() ? sourceTab : null;
            createLocalFileWorkspace(localWorkspace,
                    resolved.or(() -> localWorkspace.lastDirectory())
                            .orElseGet(() -> Path.of(System.getProperty("user.home", "."))), anchor);
        }));
    }

    @FXML
    private void openLocalFiles() {
        openLocalFilesInWorkspace(null);
    }

    private void openLocalFilesInWorkspace(Tab anchor) {
        Path home = Path.of(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
        createLocalFileWorkspace(new LocalWorkspaceSession(home), home, anchor);
    }

    private Tab createLocalFileWorkspace(LocalWorkspaceSession localWorkspace, Path initialDirectory, Tab anchor) {
        Tab existing = localWorkspace.filesTab().orElse(null);
        if (existing != null && localFileWorkspaces.containsKey(existing)) {
            selectWorkspaceTab(existing);
            return existing;
        }
        AtomicReference<Tab> localFileTab = new AtomicReference<>();
        LocalFileWorkspaceView.Loaded loaded = LocalFileWorkspaceView.load(workflow,
                new LocalFileWorkspaceController.Actions() {
                    @Override public void openPairedTerminal(Path directory) {
                        openPairedLocalTerminal(localWorkspace, directory);
                    }
                    @Override public void edit(LocalFileEntry entry) {
                        textEditors.openLocal(entry,
                                message -> setStatus(message, StatusTone.ERROR),
                                localEditorInsertionAnchor(
                                        persistedWorkspaceSettings.windowSettings(),
                                        localFileTab.get()));
                    }
                    @Override public void status(String message, boolean error) {
                        setStatus(message, error ? StatusTone.ERROR : StatusTone.IDLE);
                    }
                    @Override public void addFavorite(Path directory) {
                        addGlobalLocalFavorite(directory);
                    }
                    @Override public void directoryChanged(Path directory) {
                        localWorkspace.directory(directory);
                    }
                    @Override public boolean showHiddenFiles() {
                        return persistedWorkspaceSettings.windowSettings().showLocalFileHiddenFiles();
                    }
                    @Override public boolean showDirectoryTree() {
                        return persistedWorkspaceSettings.windowSettings().showLocalFileDirectoryTree();
                    }
                    @Override public void setShowHiddenFiles(boolean show) {
                        saveLocalFileDisplaySettings(show,
                                persistedWorkspaceSettings.windowSettings().showLocalFileDirectoryTree());
                    }
                    @Override public void setShowDirectoryTree(boolean show) {
                        saveLocalFileDisplaySettings(
                                persistedWorkspaceSettings.windowSettings().showLocalFileHiddenFiles(), show);
                    }
                    @Override public void manageFavorites() {
                        manageGlobalBookmarks();
                    }
                    @Override public List<String> favoritePaths() {
                        return persistedWorkspaceSettings.sftpSettings()
                                .globalBookmarks().localPaths();
                    }
                }, initialDirectory);
        Tab tab = new Tab(localizer.text("localFiles.title"));
        localFileTab.set(tab);
        tab.setGraphic(WorkspaceTabGraphics.localFiles());
        tab.setContent(loaded.root());
        tab.setOnCloseRequest(event -> { event.consume(); closeLocalFileWorkspace(tab); });
        installWorkspaceTabContextMenu(tab);
        localWorkspace.filesTab(tab);
        localWorkspace.directory(initialDirectory);
        localWorkspaceSessions.put(tab, localWorkspace);
        localFileWorkspaces.put(tab, loaded.controller());
        if (!workspacePanes.addPaneBeside(tab, anchor, WorkspaceLayoutModel.DropEdge.RIGHT, true)) {
            workspacePanes.addPane(tab, null, true);
        }
        updateWorkspaceVisibility();
        selectWorkspaceTab(tab);
        refreshWorkspaceQuickTools();
        return tab;
    }

    private void revealLocalEditorFile(Path file) {
        if (file == null) return;
        Path target = file.toAbsolutePath().normalize();
        Tab localFilesTab = null;
        List<Tab> displayed = workspacePanes.logicalTabsInDisplayOrder();
        for (int index = displayed.size() - 1; index >= 0; index--) {
            Tab candidate = displayed.get(index);
            if (localFileWorkspaces.containsKey(candidate)) {
                localFilesTab = candidate;
                break;
            }
        }
        if (localFilesTab == null) {
            Path directory = target.getParent() == null
                    ? Path.of(System.getProperty("user.home", ".")) : target.getParent();
            localFilesTab = createLocalFileWorkspace(
                    new LocalWorkspaceSession(directory), directory, null);
        }
        LocalFileWorkspaceController controller = localFileWorkspaces.get(localFilesTab);
        if (controller == null) return;
        controller.revealFile(target);
        selectWorkspaceTab(localFilesTab);
    }

    private void openPairedLocalTerminal(LocalWorkspaceSession localWorkspace, Path directory) {
        Tab existing = localWorkspace.terminalTab().orElse(null);
        if (existing != null && localTerminals.containsKey(existing)) {
            selectWorkspaceTab(existing);
            return;
        }
        Tab anchor = persistedWorkspaceSettings.windowSettings()
                .openLocalTerminalFromFilesInCurrentWorkspace()
                ? localWorkspace.filesTab().orElse(null) : null;
        openAuthorizedLocalTerminal(false, directory, null, false, true, anchor, localWorkspace);
        remindIfLicenseMissing();
    }

    private void saveLocalFileDisplaySettings(boolean showHiddenFiles, boolean showDirectoryTree) {
        GlobalSettings previous = persistedWorkspaceSettings;
        GlobalSettings updated = previous.withWindowSettings(previous.windowSettings()
                .withLocalFileDisplay(showHiddenFiles, showDirectoryTree));
        persistedWorkspaceSettings = updated;
        localFileWorkspaces.values().forEach(controller ->
                controller.applyDisplaySettings(showHiddenFiles, showDirectoryTree));
        refreshSftpWorkspaceLayouts();
        settingsWorkflow.save(updated).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (error == null) return;
            if (persistedWorkspaceSettings == updated) {
                persistedWorkspaceSettings = previous;
                localFileWorkspaces.values().forEach(controller -> controller.applyDisplaySettings(
                        previous.windowSettings().showLocalFileHiddenFiles(),
                        previous.windowSettings().showLocalFileDirectoryTree()));
                refreshSftpWorkspaceLayouts();
            }
            setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text(
                    "legacy.java.3abd9672dab81cf7")), StatusTone.ERROR);
        }));
    }

    private void closeLocalFileWorkspace(Tab tab) {
        LocalFileWorkspaceController controller = localFileWorkspaces.remove(tab);
        if (controller == null) return;
        controller.close();
        LocalWorkspaceSession localWorkspace = localWorkspaceSessions.remove(tab);
        if (localWorkspace != null) {
            localWorkspace.directory(controller.currentDirectory());
            localWorkspace.filesTab(null);
        }
        tab.setOnCloseRequest(null);
        workspacePanes.removePane(tab);
        updateWorkspaceVisibility();
        updateActions();
    }

    private void addGlobalLocalFavorite(Path directory) {
        SftpSettings settings = persistedWorkspaceSettings.sftpSettings();
        SftpBookmarks bookmarks = settings.globalBookmarks();
        List<String> paths = new ArrayList<>(bookmarks.localPaths());
        String value = directory.toAbsolutePath().normalize().toString();
        if (!paths.contains(value)) paths.add(value);
        saveGlobalBookmarks(new SftpBookmarks(paths, bookmarks.remotePaths()),
                localizer.text("localFiles.favoriteAdded"));
    }

    private void updateWorkspaceVisibility() {
        boolean hasTabs = !workspaceTabs.getTabs().isEmpty();
        workspaceTabs.setVisible(hasTabs);
        workspaceTabs.setManaged(hasTabs);
        boolean showWelcome = welcomeVisible(hasTabs, initialWorkspacePresentationPending);
        workspaceIntro.setVisible(showWelcome);
        workspaceIntro.setManaged(showWelcome);
    }

    static boolean welcomeVisible(boolean hasTabs, boolean initialPresentationPending) {
        return !hasTabs && !initialPresentationPending;
    }

    private void finishInitialWorkspacePresentation() {
        initialWorkspacePresentationPending = false;
        updateWorkspaceVisibility();
    }

    private void initializeWorkspaceTabs() {
        DisconnectedWorkspaceReconnect.installTabHeader(workspaceTabs,
                () -> canReconnectDisconnectedWorkspace(openSessions.get(selectedWorkspaceTab())),
                this::reconnectSelectedHost);
        WorkspaceTabHeaderGestures.install(workspaceTabs, () -> {
            Tab selected = workspaceTabs.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            if (persistedWorkspaceSettings.windowSettings().closeTabOnDoubleClick()) {
                closeMainTab(selected);
            } else {
                workspacePanes.expandWorkspaceToTabs(selected);
            }
        }, () -> {
            switch (persistedWorkspaceSettings.windowSettings().workspaceBlankDoubleClickAction()) {
                case EDITOR -> newEditorDocument();
                case LOCAL_FILES -> openLocalFiles();
                case LOCAL_TERMINAL -> openLocalTerminal();
            }
        });
        workspaceTabs.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            if (!(event.getTarget() instanceof Node target) || !workspaceTabHeaderBlank(target)) return;
            event.consume();
            workspaceBlankHeaderContextMenu().show(
                    workspaceTabs, event.getScreenX(), event.getScreenY());
        });
    }

    private boolean workspaceTabHeaderBlank(Node target) {
        return WorkspaceTabHeaderGestures.blank(workspaceTabs, target);
    }

    private ContextMenu workspaceBlankHeaderContextMenu() {
        MenuItem localTerminal = new MenuItem(
                com.ynpe.fastremote.i18n.Texts.text("workspace.titleMenu.localTerminal"));
        localTerminal.setOnAction(event -> openLocalTerminal());
        MenuItem localFiles = new MenuItem(localizer.text("localFiles.title"));
        localFiles.setOnAction(event -> openLocalFiles());
        MenuItem editor = new MenuItem(
                com.ynpe.fastremote.i18n.Texts.text("workspace.titleMenu.editor"));
        editor.setOnAction(event -> newEditorDocument());
        MenuItem quickConnect = new MenuItem(
                com.ynpe.fastremote.i18n.Texts.text("action.quickConnect"));
        quickConnect.setOnAction(event -> showQuickConnectDialog());
        MenuItem hostLibrary = new MenuItem(
                com.ynpe.fastremote.i18n.Texts.text("action.hostLibrary"));
        hostLibrary.setOnAction(event -> showHostLibraryDialog());
        MenuItem aiAssistant = new MenuItem(
                com.ynpe.fastremote.i18n.Texts.text("workspace.titleMenu.aiAssistant"));
        aiAssistant.setOnAction(event -> openAiWorkspace());
        MenuItem diagnosticLog = new MenuItem(
                com.ynpe.fastremote.i18n.Texts.text("workspace.titleMenu.diagnosticLog"));
        diagnosticLog.setOnAction(event -> openDiagnosticLogWorkspace());
        CheckMenuItem toolbar = new CheckMenuItem(com.ynpe.fastremote.i18n.Texts.text("action.toolbar"));
        toolbar.setSelected(persistedWorkspaceSettings.windowSettings().showGlobalToolbar());
        toolbar.setOnAction(event -> {
            globalToolbarMenuItem.setSelected(toolbar.isSelected());
            toggleGlobalToolbar();
        });
        CheckMenuItem statusBar = new CheckMenuItem(com.ynpe.fastremote.i18n.Texts.text("action.statusBar"));
        statusBar.setSelected(persistedWorkspaceSettings.windowSettings().showGlobalStatusBar());
        statusBar.setOnAction(event -> {
            globalStatusBarMenuItem.setSelected(statusBar.isSelected());
            toggleGlobalStatusBar();
        });
        MenuItem closeAll = new MenuItem(
                com.ynpe.fastremote.i18n.Texts.text("workspace.titleMenu.closeAll"));
        closeAll.setDisable(workspaceTabs.getTabs().isEmpty());
        closeAll.setOnAction(event -> closeMainTabs(List.copyOf(workspaceTabs.getTabs())));
        return ApplicationContextMenus.create(orderedWorkspaceBlankHeaderMenuItems(
                localTerminal, localFiles, editor, quickConnect, hostLibrary, new SeparatorMenuItem(),
                aiAssistant, diagnosticLog, new SeparatorMenuItem(), toolbar, statusBar,
                new SeparatorMenuItem(), closeAll).toArray(MenuItem[]::new));
    }

    static List<MenuItem> orderedWorkspaceBlankHeaderMenuItems(
            MenuItem localTerminal, MenuItem localFiles, MenuItem editor,
            MenuItem quickConnect, MenuItem hostLibrary,
            MenuItem firstSeparator, MenuItem aiAssistant, MenuItem diagnosticLog,
            MenuItem secondSeparator, MenuItem toolbar, MenuItem statusBar,
            MenuItem thirdSeparator, MenuItem closeAll) {
        return List.of(localTerminal, localFiles, editor, quickConnect, hostLibrary, firstSeparator,
                aiAssistant, diagnosticLog, secondSeparator, toolbar, statusBar, thirdSeparator, closeAll);
    }

    private void installWorkspaceTabContextMenu(Tab tab) {
        MenuItem close = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3fd47edce45b3603"));
        close.setOnAction(event -> closeMainTab(tab));
        MenuItem closeAll = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.38cc84bcc9a902a5"));
        closeAll.setOnAction(event -> closeMainTabs(List.copyOf(workspaceTabs.getTabs())));
        MenuItem closeRight = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.9594d7707c4bfc74"));
        closeRight.setOnAction(event -> closeMainTabs(itemsAfter(workspaceTabs.getTabs(), tab)));
        MenuItem closeLeft = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.bc066eb3c8b6d497"));
        closeLeft.setOnAction(event -> closeMainTabs(itemsBefore(workspaceTabs.getTabs(), tab)));

        MenuItem moveLeft = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ab52c89c199db369"));
        moveLeft.setOnAction(event -> moveMainTab(tab, workspaceTabs.getTabs().indexOf(tab) - 1));
        MenuItem moveRight = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5cb1517e101effc2"));
        moveRight.setOnAction(event -> moveMainTab(tab, workspaceTabs.getTabs().indexOf(tab) + 1));
        MenuItem moveFirst = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e933dda587448315"));
        moveFirst.setOnAction(event -> moveMainTab(tab, 0));
        MenuItem moveLast = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ea9a3c79a0d74bb5"));
        moveLast.setOnAction(event -> moveMainTab(tab, workspaceTabs.getTabs().size() - 1));

        MenuItem localTerminal = new MenuItem(
                com.ynpe.fastremote.i18n.Texts.text("workspace.titleMenu.localTerminal"));
        localTerminal.setId("workspace-title-open-local-terminal");
        localTerminal.setOnAction(event -> openLocalTerminalInWorkspace(workspacePanes.activePane(tab)));
        MenuItem localFiles = new MenuItem(localizer.text("localFiles.title"));
        localFiles.setId("workspace-title-open-local-files");
        localFiles.setOnAction(event -> openLocalFilesInWorkspace(workspacePanes.activePane(tab)));
        MenuItem editor = new MenuItem(
                com.ynpe.fastremote.i18n.Texts.text("workspace.titleMenu.editor"));
        editor.setId("workspace-title-open-editor");
        editor.setOnAction(event -> textEditors.newDocument(workspacePanes.activePane(tab)));
        MenuItem quickConnect = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("action.quickConnect"));
        quickConnect.setId("workspace-title-open-quick-connect");
        quickConnect.setOnAction(event -> showQuickConnectDialog(workspacePanes.activePane(tab)));
        MenuItem hostLibrary = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("action.hostLibrary"));
        hostLibrary.setId("workspace-title-open-host-library");
        hostLibrary.setOnAction(event -> showHostLibraryDialog(workspacePanes.activePane(tab)));

        MenuItem mergePrevious = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("workspace.pane.mergePrevious"));
        mergePrevious.setOnAction(event -> mergeAdjacentWorkspace(tab, -1));
        MenuItem mergeNext = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("workspace.pane.mergeNext"));
        mergeNext.setOnAction(event -> mergeAdjacentWorkspace(tab, 1));

        SeparatorMenuItem copyNameSeparator = new SeparatorMenuItem();
        MenuItem copyName = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("workspace.pane.copyName"));
        copyName.setOnAction(event -> remoteSessionHostName(workspacePanes.activePane(tab))
                .ifPresent(this::copyToClipboard));
        MenuItem copyIp = new MenuItem(localizer.text("host.copyIp"));
        copyIp.setOnAction(event -> {
            OpenSession session = openSessions.get(workspacePanes.activePane(tab));
            if (session != null) copyToClipboard(session.profile.host().value());
        });

        List<MenuItem> items = new ArrayList<>(orderedWorkspaceTabMenuItems(
                close, closeAll, closeRight, closeLeft, new SeparatorMenuItem(),
                moveLeft, moveRight, moveFirst, moveLast, new SeparatorMenuItem(),
                localTerminal, localFiles, editor, quickConnect, hostLibrary, new SeparatorMenuItem(),
                mergePrevious, mergeNext));
        items.add(copyNameSeparator);
        items.add(copyName);
        items.add(copyIp);
        ContextMenu menu = ApplicationContextMenus.create(items.toArray(MenuItem[]::new));
        ApplicationContextMenus.prepareBeforeShow(menu, () -> {
            boolean hostTab = workspacePanes != null
                    && remoteSessionHostName(workspacePanes.activePane(tab)).isPresent();
            copyNameSeparator.setVisible(hostTab);
            copyName.setVisible(hostTab);
            copyIp.setVisible(hostTab);
            int index = workspaceTabs.getTabs().indexOf(tab);
            int last = workspaceTabs.getTabs().size() - 1;
            boolean missing = index < 0;
            close.setDisable(missing);
            closeAll.setDisable(workspaceTabs.getTabs().isEmpty());
            closeLeft.setDisable(missing || index == 0);
            closeRight.setDisable(missing || index == last);
            moveLeft.setDisable(missing || index == 0);
            moveRight.setDisable(missing || index == last);
            moveFirst.setDisable(missing || index == 0);
            moveLast.setDisable(missing || index == last);
            mergePrevious.setDisable(!canMergeAdjacentWorkspace(tab, -1));
            mergeNext.setDisable(!canMergeAdjacentWorkspace(tab, 1));
        });
        tab.setContextMenu(menu);
    }

    private Optional<String> remoteSessionHostName(Tab tab) {
        OpenSession session = openSessions.get(tab);
        return session == null || session.name == null || session.name.isBlank()
                ? Optional.empty() : Optional.of(session.name);
    }

    private void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    static List<MenuItem> orderedWorkspaceTabMenuItems(
            MenuItem close, MenuItem closeAll, MenuItem closeRight, MenuItem closeLeft,
            MenuItem closeSeparator, MenuItem moveLeft, MenuItem moveRight,
            MenuItem moveFirst, MenuItem moveLast, MenuItem openSeparator,
            MenuItem localTerminal, MenuItem localFiles, MenuItem editor, MenuItem quickConnect,
            MenuItem hostLibrary, MenuItem mergeSeparator,
            MenuItem mergePrevious, MenuItem mergeNext) {
        return List.of(close, closeAll, closeRight, closeLeft, closeSeparator,
                moveLeft, moveRight, moveFirst, moveLast, openSeparator,
                localTerminal, localFiles, editor, quickConnect, hostLibrary, mergeSeparator,
                mergePrevious, mergeNext);
    }

    private boolean canMergeAdjacentWorkspace(Tab tab, int offset) {
        if (workspacePanes == null || !workspacePanes.isWorkspaceTab(tab)) return false;
        int index = workspaceTabs.getTabs().indexOf(tab);
        int targetIndex = index + offset;
        if (index < 0 || targetIndex < 0 || targetIndex >= workspaceTabs.getTabs().size()) return false;
        Tab target = workspaceTabs.getTabs().get(targetIndex);
        return workspacePanes.isWorkspaceTab(target);
    }

    private void mergeAdjacentWorkspace(Tab tab, int offset) {
        if (!canMergeAdjacentWorkspace(tab, offset)) return;
        int index = workspaceTabs.getTabs().indexOf(tab);
        Tab target = workspaceTabs.getTabs().get(index + offset);
        WorkspaceLayoutModel.DropEdge edge = offset < 0
                ? WorkspaceLayoutModel.DropEdge.RIGHT : WorkspaceLayoutModel.DropEdge.LEFT;
        workspacePanes.mergeWorkspaceInto(tab, target, edge);
    }

    private void closeMainTabs(List<Tab> tabs) {
        closeMainTabs(tabs, true);
    }

    private void closeMainTabs(List<Tab> tabs, boolean confirmWorkspace) {
        for (Tab tab : tabs) closeMainTab(tab, confirmWorkspace);
    }

    private void closeMainTab(Tab tab) {
        closeMainTab(tab, true);
    }

    private void closeMainTab(Tab tab, boolean confirmWorkspace) {
        if (!workspaceTabs.getTabs().contains(tab)) return;
        if (workspacePanes != null && workspacePanes.isWorkspaceTab(tab)) {
            if (confirmWorkspace) workspacePanes.requestCloseWorkspace(tab);
            else workspacePanes.closeWorkspace(tab);
            return;
        }
        prepareWorkspaceCloseFallback(tab);
        if (aiPlacement.isWorkspaceTab(tab)) {
            aiPlacement.closeWorkspace();
        } else if (diagnosticLogPlacement.isWorkspaceTab(tab)) {
            diagnosticLogPlacement.closeWorkspace();
        } else if (localTerminals.containsKey(tab)) {
            closeLocalTerminal(tab);
        } else if (localFileWorkspaces.containsKey(tab)) {
            closeLocalFileWorkspace(tab);
        } else if (textEditors != null && textEditors.owns(tab)) {
            textEditors.requestClose(tab);
        } else if (openSessions.containsKey(tab)) {
            closeWorkspaceTab(tab, com.ynpe.fastremote.i18n.Texts.text("legacy.java.a9504e63d452b238"));
        } else {
            workspaceTabs.getTabs().remove(tab);
            updateWorkspaceVisibility();
            updateActions();
        }
    }

    private boolean confirmWorkspaceClose(Tab topLevel) {
        int paneCount = workspacePanes.paneCount(topLevel);
        boolean unfinishedTransfers = workspacePanes.panes(topLevel).stream()
                .anyMatch(this::hasUnfinishedSftpTransfers);
        if (!shouldConfirmWorkspaceClose(paneCount, unfinishedTransfers)) return true;

        String messageKey = paneCount >= 2
                ? (unfinishedTransfers
                        ? "workspace.closeConfirm.message.multipleWithTransfers"
                        : "workspace.closeConfirm.message.multiple")
                : "workspace.closeConfirm.message.transfers";
        ButtonType confirm = new ButtonType(
                com.ynpe.fastremote.i18n.Texts.text("workspace.closeConfirm.action"),
                ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(mainWindow);
        alert.setTitle(com.ynpe.fastremote.i18n.Texts.text("workspace.closeConfirm.title"));
        alert.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("workspace.closeConfirm.header"));
        alert.setContentText(com.ynpe.fastremote.i18n.Texts.text(messageKey, paneCount));
        alert.getButtonTypes().setAll(ButtonType.CANCEL, confirm);
        TextEditorWorkspace.prepareDialog(alert);
        return alert.showAndWait().filter(confirm::equals).isPresent();
    }

    private boolean hasUnfinishedSftpTransfers(Tab pane) {
        OpenSession session = openSessions.get(pane);
        return session != null
                && session.tabs.get(WorkspaceType.SFTP) == pane
                && session.transferQueue != null
                && session.transferQueue.hasUnfinishedTransfers();
    }

    static boolean shouldConfirmWorkspaceClose(int paneCount, boolean unfinishedTransfers) {
        return paneCount >= 2 || unfinishedTransfers;
    }

    private void closeWorkspacePane(Tab tab) {
        Tab topLevel = workspacePanes.topLevelFor(tab);
        if (workspacePanes.paneCount(topLevel) == 1) prepareWorkspaceCloseFallback(topLevel);
        if (localTerminals.containsKey(tab)) {
            closeLocalTerminal(tab);
        } else if (localFileWorkspaces.containsKey(tab)) {
            closeLocalFileWorkspace(tab);
        } else if (textEditors != null && textEditors.owns(tab)) {
            textEditors.requestClose(tab);
        } else if (openSessions.containsKey(tab)) {
            closeWorkspaceTab(tab, com.ynpe.fastremote.i18n.Texts.text("legacy.java.a9504e63d452b238"));
        }
    }

    private void prepareWorkspaceCloseFallback(Tab tab) {
        pendingWorkspaceCloseFallbacks.remove(tab);
        if (workspaceTabs.getSelectionModel().getSelectedItem() == tab) {
            workspaceActivationHistory.previous(tab, workspaceTabs.getTabs())
                    .ifPresent(fallback -> pendingWorkspaceCloseFallbacks.put(tab, fallback));
        }
    }

    private Tab selectedWorkspaceTab() {
        return workspacePanes == null
                ? workspaceTabs.getSelectionModel().getSelectedItem() : workspacePanes.selectedPane();
    }

    private void selectWorkspaceTab(Tab tab) {
        if (workspacePanes != null && workspacePanes.containsPane(tab)) workspacePanes.selectPane(tab);
        else workspaceTabs.getSelectionModel().select(tab);
    }

    private void moveMainTab(Tab tab, int targetIndex) {
        if (moveItem(workspaceTabs.getTabs(), tab, targetIndex)) {
            workspaceTabs.getSelectionModel().select(tab);
        }
    }

    private void initializeWorkbenchLayout() {
        initializeToolWindows();
        Rectangle overlayClip = new Rectangle();
        overlayClip.widthProperty().bind(workbenchOverlayHost.widthProperty());
        overlayClip.heightProperty().bind(workbenchOverlayHost.heightProperty());
        workbenchOverlayHost.setClip(overlayClip);
        SplitPane.setResizableWithParent(hostSidebar, false);
        SplitPane.setResizableWithParent(rightDock, false);
        horizontalWorkbenchSplit.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && splitPaneDividerTarget(event.getTarget())) {
                unlockHorizontalDockWidths();
            }
        });
        horizontalWorkbenchSplit.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (captureHorizontalDockWidths()) {
                saveDockDimensions();
            }
        });
        installFloatingDockResize(hostSidebar, DockSide.LEFT);
        installFloatingDockResize(rightDock, DockSide.RIGHT);
        workbenchOverlayHost.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            Node target = event.getTarget() instanceof Node node ? node : null;
            if (isFloatingDockVisible(DockSide.LEFT)
                    && !isDescendant(target, hostSidebar)
                    && !isDescendant(target, leftToolRail)) {
                setHostSidebarVisible(false);
            }
            if (isFloatingDockVisible(DockSide.RIGHT)
                    && !isDescendant(target, rightDock)
                    && !isDescendant(target, rightToolRail)) {
                setRightDockVisible(false);
            }
        });
    }

    private void installFloatingDockResize(VBox dock, DockSide side) {
        dock.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            dock.setCursor(floatingDockCursor(isFloating(side), side, dock.sceneToLocal(event.getSceneX(), event.getSceneY()).getX(), dock.getWidth()));
        });
        dock.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (!isFloating(side) || event.getButton() != MouseButton.PRIMARY) return;
            double pointerX = dock.sceneToLocal(event.getSceneX(), event.getSceneY()).getX();
            boolean edge = side == DockSide.LEFT
                    ? pointerX >= dock.getWidth() - 7.0 : pointerX <= 7.0;
            if (!edge) return;
            resizingFloatingDock = true;
            floatingResizeStartX = event.getSceneX();
            floatingResizeStartWidth = dock.getWidth();
            event.consume();
        });
        dock.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!resizingFloatingDock || !isFloating(side)) return;
            double delta = event.getSceneX() - floatingResizeStartX;
            double proposed = floatingResizeStartWidth + (side == DockSide.LEFT ? delta : -delta);
            double minimum = Math.max(STANDARD_DOCK_MIN_WIDTH, dock.minWidth(-1));
            double maximum = Math.max(minimum, workbenchOverlayHost.getWidth() * 0.72);
            double width = clamp(proposed, minimum, maximum);
            if (side == DockSide.LEFT) leftDockWidth = width;
            else rightDockWidth = width;
            lockDockWidthForReveal(dock, width);
            event.consume();
        });
        dock.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (!resizingFloatingDock) return;
            resizingFloatingDock = false;
            saveDockDimensions();
            event.consume();
        });
    }

    static javafx.scene.Cursor floatingDockCursor(
            boolean floating, DockSide side, double pointerX, double dockWidth) {
        if (!floating) return javafx.scene.Cursor.DEFAULT;
        boolean edge = side == DockSide.LEFT
                ? pointerX >= dockWidth - 7.0 : pointerX <= 7.0;
        return edge ? javafx.scene.Cursor.H_RESIZE : javafx.scene.Cursor.DEFAULT;
    }

    private static boolean isDescendant(Node candidate, Node ancestor) {
        for (Node current = candidate; current != null; current = current.getParent()) {
            if (current == ancestor) return true;
        }
        return false;
    }

    private void hideFloatingDocksUnlessOwnedWindowIsShowing() {
        if (mainWindow == null || floatingDockHideBlocked(
                mainWindow.isFocused(), ownedWindowIsShowing(mainWindow), ownedWindowFocusRestorePending)) return;
        if (isFloatingDockVisible(DockSide.LEFT)) setHostSidebarVisible(false);
        if (isFloatingDockVisible(DockSide.RIGHT)) setRightDockVisible(false);
    }

    private void hideFloatingDocksAfterFocusChange(Node focused) {
        if (mainWindow != null && floatingSurfaceFocusChangeBlocked(
                ownedWindowIsShowing(mainWindow), ownedWindowFocusRestorePending)) return;
        if (isFloatingDockVisible(DockSide.LEFT)
                && !isDescendant(focused, hostSidebar)
                && !isDescendant(focused, leftToolRail)
                && focused != toolbarLeftDockButton) {
            setHostSidebarVisible(false);
        }
        if (isFloatingDockVisible(DockSide.RIGHT)
                && !isDescendant(focused, rightDock)
                && !isDescendant(focused, rightToolRail)
                && focused != toolbarRightDockButton) {
            setRightDockVisible(false);
        }
    }

    private static boolean ownedWindowIsShowing(Window owner) {
        return Window.getWindows().stream().anyMatch(window -> window.isShowing() && isOwnedBy(window, owner));
    }

    private void handleOwnedWindowListChange(ListChangeListener.Change<? extends Window> change) {
        if (mainWindow == null) return;
        boolean ownedWindowClosed = false;
        while (change.next()) {
            if (change.wasRemoved() && change.getRemoved().stream().anyMatch(window -> isOwnedBy(window, mainWindow))) {
                ownedWindowClosed = true;
            }
        }
        if (!ownedWindowClosed || !anyFloatingSurfaceVisible()) return;
        ownedWindowFocusRestorePending = true;
        ownedWindowFocusRestoreDelay.playFromStart();
    }

    private boolean anyFloatingSurfaceVisible() {
        if (isFloatingDockVisible(DockSide.LEFT) || isFloatingDockVisible(DockSide.RIGHT)) return true;
        return rootStack.lookupAll(".workspace-terminal-floating").stream().anyMatch(Node::isManaged);
    }

    static boolean floatingDockHideBlocked(
            boolean mainWindowFocused, boolean ownedWindowShowing, boolean focusRestorePending) {
        return mainWindowFocused || ownedWindowShowing || focusRestorePending;
    }

    static boolean floatingSurfaceFocusChangeBlocked(boolean ownedWindowShowing, boolean focusRestorePending) {
        return ownedWindowShowing || focusRestorePending;
    }

    private static boolean isOwnedBy(Window window, Window owner) {
        for (Window current = ownerOf(window); current != null; current = ownerOf(current)) {
            if (current == owner) return true;
        }
        return false;
    }

    private static Window ownerOf(Window window) {
        if (window instanceof Stage stage) return stage.getOwner();
        if (window instanceof javafx.stage.PopupWindow popup) return popup.getOwnerWindow();
        return null;
    }

    private void hideFloatingTerminalActivitiesUnlessOwnedWindowIsShowing(Node focused) {
        if (mainWindow != null && floatingSurfaceFocusChangeBlocked(
                ownedWindowIsShowing(mainWindow), ownedWindowFocusRestorePending)) return;
        for (Node activity : rootStack.lookupAll(".workspace-terminal-floating")) {
            Object toggle = activity.getProperties().get("activityToggle");
            if (activity.isManaged() && !isDescendant(focused, activity) && focused != toggle) {
                setManagedVisible(activity, false);
                if (toggle instanceof Button button) setToolWindowButtonSelected(button, false);
            }
        }
    }

    private void initializeToolWindows() {
        toolWindowPages = Map.of(
                ToolWindow.QUICK_CONNECT, quickConnectPage,
                ToolWindow.HOST_LIBRARY, hostLibraryPage,
                ToolWindow.CREDENTIAL_VAULT, credentialVaultPage,
                ToolWindow.SNIPPETS, snippetsPage,
                ToolWindow.VARIABLE_LIBRARY, variableLibraryPage,
                ToolWindow.AI_ASSISTANT, aiAssistantPage,
                ToolWindow.DIAGNOSTIC_LOG, diagnosticLogPage);
        toolWindowButtons = Map.of(
                ToolWindow.QUICK_CONNECT, quickConnectRailButton,
                ToolWindow.HOST_LIBRARY, hostLibraryRailButton,
                ToolWindow.CREDENTIAL_VAULT, credentialVaultRailButton,
                ToolWindow.SNIPPETS, snippetsRailButton,
                ToolWindow.VARIABLE_LIBRARY, variableLibraryRailButton,
                ToolWindow.AI_ASSISTANT, aiAssistantRailButton,
                ToolWindow.DIAGNOSTIC_LOG, diagnosticLogRailButton);
        leftDockContentHost.getChildren().clear();
        toolWindowButtons.forEach(this::installToolWindowDrag);
        installToolWindowDropTargets();
        arrangeToolRails(GlobalSettings.defaults());
        installToolWindow(DockSide.LEFT, ToolWindow.HOST_LIBRARY);
    }

    private void initializeHostTree() {
        hostTree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        ToggleGroup filters = exclusiveHostFilters(
                hostAllFilter, hostFavoriteFilter, hostRecentFilter);
        SearchTreeNavigation.install(hostSearchField, hostTree, () -> {
            if (searchDelay.getStatus() != javafx.animation.Animation.Status.STOPPED) {
                searchDelay.stop();
                rebuildHostTree(filteredCatalog(catalog, hostSearchField.getText(), selectedHostFilter(filters)));
            }
        });
        filters.selectedToggleProperty().addListener((ignored, previous, selected) -> {
            if (selected == null) {
                if (previous != null) previous.setSelected(true);
            } else {
                rebuildHostTree(filteredCatalog(
                        catalog, hostSearchField.getText(), selectedHostFilter(filters)));
            }
        });
        hostTree.setCellFactory(ignored -> createHostTreeCell());
        hostTree.setContextMenu(catalogContextMenu(null));
        hostTree.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                connectSelectedHost();
            }
        });
        hostSearchField.textProperty().addListener((ignored, previous, value) -> {
            searchDelay.setOnFinished(event -> rebuildHostTree(filteredCatalog(
                    catalog, value, selectedHostFilter(filters))));
            searchDelay.playFromStart();
        });
    }



    private void bindSnippetLibrary(SnippetLibraryController controller) {
        controller.bind(new SnippetLibraryController.Actions() {
            @Override public void importBuiltins() { importBuiltinCommands(); }
            @Override public void createSnippet(Optional<SnippetGroupId> group) { showSnippetEditor(null, group); }
            @Override public void createGroup() { createSnippetGroup(); }
            @Override public void importText() { importSnippets(); }
            @Override public void send(Snippet snippet, boolean execute) { sendSnippet(snippet, execute); }
            @Override public void edit(Snippet snippet) { editSnippet(snippet); }
            @Override public void copy(Snippet snippet) { copySnippet(snippet); }
            @Override public void editGroup(SnippetGroup group) { editSnippetGroup(group); }
            @Override public void deleteSnippets(List<Snippet> snippets) { MainWindowController.this.deleteSnippets(snippets); }
            @Override public void deleteSelection(List<Snippet> snippets, List<SnippetGroup> groups) {
                deleteSnippetSelection(snippets, groups);
            }
            @Override public void refresh() { refreshSnippets(); }
            @Override public CompletionStage<Snippet> saveSnippet(Snippet snippet) {
                return snippetWorkflow.saveSnippet(snippet);
            }
            @Override public CompletionStage<SnippetGroup> saveGroup(SnippetGroup group) {
                return snippetWorkflow.saveGroup(group);
            }
            @Override public void registerExpansion(TreeItem<String> item, SnippetGroupId group) {
                registerGroupExpansion(item, snippetGroupExpansionKey(group));
            }
            @Override public void prepareSearch(TreeItem<String> root, Map<TreeItem<String>, SnippetGroup> groups, String query) {
                prepareLibrarySearch(root, groups, query);
            }
            @Override public void showShortcut(MenuItem item, ShortcutAction action) {
                MainWindowController.this.showShortcut(item, action);
            }
        });
    }

    @FXML
    private void showSnippetLibraryDialog() {
        if (snippetLibraryDialog != null) {
            snippetLibraryDialog.focus();
            return;
        }
        SnippetLibraryDialog dialog = new SnippetLibraryDialog(rootPane.getScene().getWindow(),
                persistedWorkspaceSettings.windowSettings().keyboardShortcuts(),
                this::importSnippets, this::exportSnippets);
        bindSnippetLibrary(dialog.controller());
        dialog.controller().update(snippetSnapshot);
        snippetLibraryDialog = dialog;
        dialog.onHidden(() -> {
            if (snippetLibraryDialog == dialog) snippetLibraryDialog = null;
        });
        dialog.show();
    }

    private Window snippetDialogOwner() {
        return snippetLibraryDialog == null ? rootPane.getScene().getWindow() : snippetLibraryDialog.window();
    }

    private void initializeVariableLibrary() {
        variableLibraryTree = new VariableLibraryTree(variableTree,variableSearchField,variableItems,variableGroupItems,
                () -> snippetSnapshot,snippetWorkflow,new VariableLibraryTree.Actions() {
            public void prepareLibrarySearch(TreeItem<String> root, Map<TreeItem<String>, ?> groups, String query) { MainWindowController.this.prepareLibrarySearch(root,groups,query); }
            public void registerGroupExpansion(TreeItem<String> item, SnippetVariableGroupId id) { MainWindowController.this.registerGroupExpansion(item, variableGroupExpansionKey(id)); }
            public ContextMenu variableContextMenu(TreeItem<String> item) { return MainWindowController.this.variableContextMenu(item); }
            public void editVariable(SnippetVariable value) { MainWindowController.this.editVariable(value); }
            public void refreshSnippets() { MainWindowController.this.refreshSnippets(); }
            public void status(String message, boolean error) { setStatus(message,error?StatusTone.ERROR:StatusTone.IDLE); }
        });
        variableLibraryTree.initialize();
    }

    private void initializeCredentialTree() {
        credentialLibraryTree = new CredentialLibraryTree(credentialTree,credentialSearchField,credentialItems,credentialGroupItems,
                () -> catalog,catalogWorkflow,new CredentialLibraryTree.Actions() {
            public void prepareLibrarySearch(TreeItem<String> root, Map<TreeItem<String>, ?> groups, String query) { MainWindowController.this.prepareLibrarySearch(root,groups,query); }
            public void registerGroupExpansion(TreeItem<String> item, CredentialGroupId id) { MainWindowController.this.registerGroupExpansion(item, credentialGroupExpansionKey(id)); }
            public ContextMenu credentialLibraryContextMenu() { return MainWindowController.this.credentialLibraryContextMenu(); }
            public ContextMenu credentialGroupContextMenu(CredentialGroup group) { return MainWindowController.this.credentialGroupContextMenu(group); }
            public ContextMenu credentialContextMenu(GlobalCredential value) { return MainWindowController.this.credentialContextMenu(value); }
            public void editReusableCredential(GlobalCredential value) { MainWindowController.this.editReusableCredential(value); }
            public void refreshCatalog() { MainWindowController.this.refreshCatalog(); }
        }, credentialEmpty);
        credentialLibraryTree.initialize();
    }



    private void rebuildCredentialTree() { if (credentialLibraryTree != null) credentialLibraryTree.rebuild(); }

    @FXML private void createCredentialGroup() { showCredentialGroupEditor(null); }

    private void showCredentialGroupEditor(CredentialGroup existing) {
        credentialLibraryEditor.showCredentialGroupEditor(existing);
    }

    private List<GlobalCredential> selectedCredentials() {
        return credentialTree.getSelectionModel().getSelectedItems().stream().map(credentialItems::get)
                .filter(Objects::nonNull).toList();
    }

    private ContextMenu credentialGroupContextMenu(CredentialGroup group) {
        if (group == null) return credentialLibraryContextMenu();
        MenuItem create = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.9ef774b3220dc7d9"));
        create.setOnAction(event -> createReusableCredentialAt(Optional.of(group.id())));
        MenuItem edit = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d00b2a96c3b35930")); edit.setOnAction(event -> showCredentialGroupEditor(group));
        showShortcut(edit, ShortcutAction.EDIT_SELECTED_LIBRARY_ITEM);
        MenuItem delete = new MenuItem(credentialTree.getSelectionModel().getSelectedItems().size() > 1
                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.435bf8a234fde6f2") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.7605c1d82eed69a5"));
        delete.setOnAction(event -> deleteCredentialSelection());
        showShortcut(delete, ShortcutAction.DELETE_SELECTED_LIBRARY_ITEM);
        return ApplicationContextMenus.create(create, new SeparatorMenuItem(), edit, delete);
    }

    private void deleteCredentialSelection() {
        List<TreeItem<String>> selected = List.copyOf(credentialTree.getSelectionModel().getSelectedItems());
        List<GlobalCredential> selectedCredentials = selected.stream().map(credentialItems::get)
                .filter(Objects::nonNull).toList();
        List<CredentialGroup> selectedGroups = selected.stream().map(credentialGroupItems::get)
                .filter(Objects::nonNull).toList();
        if (selectedCredentials.isEmpty() && selectedGroups.isEmpty()) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.4eb2f60c03a3de03") + (selectedCredentials.size() + selectedGroups.size())
                        + com.ynpe.fastremote.i18n.Texts.text("legacy.java.56bb89892c2273fa"),
                ButtonType.CANCEL, ButtonType.OK);
        alert.initOwner(rootPane.getScene().getWindow()); alert.setHeaderText(null);
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        List<Supplier<CompletionStage<Void>>> operations = new ArrayList<>();
        selectedCredentials.forEach(value -> operations.add(() -> catalogWorkflow.deleteReusableCredential(value)));
        selectedGroups.forEach(value -> operations.add(() -> catalogWorkflow.deleteCredentialGroup(value.id())));
        runDeleteOperations(operations).whenComplete((errors, error) -> Platform.runLater(() -> {
            refreshCatalog();
            if (error != null || (errors != null && !errors.isEmpty()))
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.32faef09b8cc104d"), StatusTone.ERROR);
        }));
    }



    private void deleteSnippetSelection(List<Snippet> snippets, List<SnippetGroup> groups) {
        snippetLibraryEditor.deleteSnippetSelection(snippets, groups);
    }

    private void refreshSnippets() {
        long generation = ++snippetRefreshGeneration;
        snippetWorkflow.load().whenComplete((snapshot, error) -> Platform.runLater(() -> {
            if (generation != snippetRefreshGeneration) return;
            if (error != null) {
                setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.6542292802971f24")), StatusTone.ERROR);
                return;
            }
            snippetSnapshot = snapshot;
            snippetsPageController.update(snapshot);
            if (snippetLibraryDialog != null) snippetLibraryDialog.controller().update(snapshot);
            rebuildVariableTree();
        }));
    }



    @FXML
    private void createSnippetGroup() {
        showSnippetGroupEditor(null);
    }

    private void editSnippetGroup(SnippetGroup group) {
        showSnippetGroupEditor(group);
    }

    private void showSnippetGroupEditor(SnippetGroup existing) {
        snippetLibraryEditor.showSnippetGroupEditor(existing);
    }

    private void deleteSnippetGroup(SnippetGroup group) {
        snippetLibraryEditor.deleteSnippetGroup(group);
    }



    @FXML
    private void importSnippets() {
        importCatalog();
    }

    @FXML
    private void installCommandLineTools() {
        CommandLineToolsDialog.install(rootPane.getScene().getWindow(),
                installCommandLineToolsMenuItem, localTerminalWorkflow);
    }

    @FXML
    private void importBuiltinCommands() {
        Map<String, com.ynpe.fastremote.domain.settings.InterfaceLanguage> choices = new LinkedHashMap<>();
        snippetWorkflow.builtinCommandLanguages().stream()
                .forEach(language -> choices.put(
                        localizer.text("language." + language.persistedTag()), language));
        if (choices.isEmpty()) {
            setStatus(localizer.text("builtin.import.unavailable"), StatusTone.ERROR);
            return;
        }
        com.ynpe.fastremote.domain.settings.InterfaceLanguage preferred = localizer.resolvedLanguage();
        String initial = choices.entrySet().stream().filter(entry -> entry.getValue() == preferred)
                .map(Map.Entry::getKey).findFirst().orElse(choices.keySet().iterator().next());
        Dialog<BuiltinImportSelection> dialog = new Dialog<>();
        dialog.initOwner(snippetDialogOwner());
        dialog.setTitle(localizer.text("builtin.import.title"));
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);

        Label explanation = DialogSpacing.wrappingContentLabel(
                localizer.text("builtin.import.header"), 460.0);
        Label languageLabel = new Label(localizer.text("builtin.import.language"));
        languageLabel.setMinWidth(Region.USE_PREF_SIZE);
        ComboBox<String> languageChoice = new ComboBox<>(FXCollections.observableArrayList(choices.keySet()));
        languageChoice.setValue(initial);
        languageChoice.setMinWidth(280.0);
        languageChoice.setMaxWidth(Double.MAX_VALUE);
        CheckBox overwriteExisting = builtinOverwriteCheckBox(localizer.text("builtin.import.overwrite"));
        GridPane languageRow = new GridPane();
        languageRow.setHgap(12.0);
        languageRow.setVgap(12.0);
        languageRow.setPrefWidth(460.0);
        languageRow.addRow(0, languageLabel, languageChoice);
        languageRow.add(overwriteExisting, 1, 1);
        GridPane.setHgrow(languageChoice, Priority.ALWAYS);

        VBox content = new VBox(16.0, explanation, languageRow);
        content.setPrefWidth(460.0);
        dialog.getDialogPane().setContent(DialogSpacing.anchoredContent(content));
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new BuiltinImportSelection(languageChoice.getValue(), overwriteExisting.isSelected())
                : null);
        DialogSpacing.contentToButtonBar(dialog);
        dialog.showAndWait().ifPresent(selection -> {
            com.ynpe.fastremote.domain.settings.InterfaceLanguage language = choices.get(selection.language());
            BuiltinSnippetConflictPolicy conflictPolicy = selection.overwriteExisting()
                    ? BuiltinSnippetConflictPolicy.OVERWRITE
                    : BuiltinSnippetConflictPolicy.SKIP;
            setStatus(localizer.text("builtin.import.running"), StatusTone.WORKING);
            snippetWorkflow.importBuiltinCommands(language, conflictPolicy)
                    .whenComplete((result, error) -> Platform.runLater(() -> {
                if (error != null) {
                    setStatus(messageFor(error, localizer.text("builtin.import.failed")), StatusTone.ERROR);
                    return;
                }
                setStatus(localizer.text("builtin.import.success",
                        result.importedCommands(), result.overwrittenCommands(), result.skippedCommands()),
                        StatusTone.IDLE);
                refreshSnippets();
            }));
        });
    }

    private record BuiltinImportSelection(String language, boolean overwriteExisting) { }

    static CheckBox builtinOverwriteCheckBox(String text) {
        CheckBox option = new CheckBox(Objects.requireNonNull(text, "text"));
        option.setSelected(true);
        return option;
    }

    @FXML
    private void exportSnippets() {
        exportCatalog();
    }

    @FXML
    private void importVariables() {
        importCatalog();
    }

    @FXML
    private void exportVariables() {
        exportCatalog();
    }

    private void inspectAndImport(Path file, boolean variablesOnly) {
        snippetLibraryEditor.inspectAndImport(file, variablesOnly);
    }

    private Optional<ImportConflictPolicy> importConflictPolicy(LibraryImportPreview preview) {
        return snippetLibraryEditor.importConflictPolicy(preview);
    }

    private void editSnippet(Snippet snippet) {
        showSnippetEditor(snippet, snippet.groupId());
    }

    private void copySnippet(Snippet source) {
        snippetLibraryEditor.copySnippet(source);
    }

    private void showSnippetEditor(Snippet existing, Optional<SnippetGroupId> initialGroup) {
        snippetLibraryEditor.showSnippetEditor(existing, initialGroup);
    }

    private int nextSnippetOrder(Optional<SnippetGroupId> groupId) {
        return snippetLibraryEditor.nextSnippetOrder(groupId);
    }



    private ContextMenu variableContextMenu(TreeItem<String> item) {
        SnippetVariable variable = variableItems.get(item);
        SnippetVariableGroup group = variableGroupItems.get(item);
        if (variable != null) {
            MenuItem edit = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.051836569928a9f9"));
            edit.setOnAction(event -> editVariable(variable));
            showShortcut(edit, ShortcutAction.EDIT_SELECTED_LIBRARY_ITEM);
            MenuItem delete = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2f9daa828907b93f"));
            delete.setOnAction(event -> deleteVariableSelection());
            showShortcut(delete, ShortcutAction.DELETE_SELECTED_LIBRARY_ITEM);
            return ApplicationContextMenus.create(edit, delete);
        }
        MenuItem create = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.300a3219c4cdcda8"));
        create.setOnAction(event -> showVariableEditor(null, Optional.ofNullable(group).map(SnippetVariableGroup::id)));
        MenuItem importText = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("config.import.title"));
        importText.setOnAction(event -> importVariables());
        if (group == null) return ApplicationContextMenus.create(create, importText);
        MenuItem edit = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d00b2a96c3b35930"));
        edit.setOnAction(event -> showVariableGroupEditor(group));
        showShortcut(edit, ShortcutAction.EDIT_SELECTED_LIBRARY_ITEM);
        MenuItem delete = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7605c1d82eed69a5"));
        delete.setOnAction(event -> deleteVariableSelection());
        showShortcut(delete, ShortcutAction.DELETE_SELECTED_LIBRARY_ITEM);
        return ApplicationContextMenus.create(create, importText, new SeparatorMenuItem(), edit, delete);
    }

    private void rebuildVariableTree() { if (variableLibraryTree != null) variableLibraryTree.rebuild(); }

    @FXML
    private void createVariable() {
        SnippetVariableGroup selected = variableGroupItems.get(variableTree.getSelectionModel().getSelectedItem());
        showVariableEditor(null, Optional.ofNullable(selected).map(SnippetVariableGroup::id));
    }

    private void editVariable(SnippetVariable variable) {
        showVariableEditor(variable, variable.groupId());
    }

    private void showVariableEditor(
            SnippetVariable existing, Optional<SnippetVariableGroupId> initialGroup) {
        variableLibraryEditor.showVariableEditor(existing, initialGroup);
    }

    private int nextVariableOrder(Optional<SnippetVariableGroupId> groupId) {
        return variableLibraryEditor.nextVariableOrder(groupId);
    }

    @FXML
    private void createVariableGroup() {
        showVariableGroupEditor(null);
    }

    private void showVariableGroupEditor(SnippetVariableGroup existing) {
        variableLibraryEditor.showVariableGroupEditor(existing);
    }

    private void deleteVariableSelection() {
        List<TreeItem<String>> selected = List.copyOf(variableTree.getSelectionModel().getSelectedItems());
        List<SnippetVariable> variables = selected.stream().map(variableItems::get).filter(Objects::nonNull).toList();
        List<SnippetVariableGroup> groups = selected.stream().map(variableGroupItems::get)
                .filter(Objects::nonNull).toList();
        if (variables.isEmpty() && groups.isEmpty()) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.4eb2f60c03a3de03") + (variables.size() + groups.size()) + com.ynpe.fastremote.i18n.Texts.text("legacy.java.be6621f2160dbd63"),
                ButtonType.CANCEL, ButtonType.OK);
        alert.initOwner(rootPane.getScene().getWindow());
        alert.setHeaderText(null);
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        List<Supplier<CompletionStage<Void>>> operations = new ArrayList<>();
        variables.forEach(value -> operations.add(() -> snippetWorkflow.deleteVariable(value.id())));
        groups.forEach(value -> operations.add(() -> snippetWorkflow.deleteVariableGroup(value.id())));
        runDeleteOperations(operations).whenComplete((errors, error) -> Platform.runLater(() -> {
            refreshSnippets();
            if (error != null || (errors != null && !errors.isEmpty())) {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4ea98d0b78b3aa45"), StatusTone.ERROR);
            }
        }));
    }

    private void deleteSnippet(Snippet snippet) {
        deleteSnippets(List.of(snippet));
    }

    private void deleteSnippets(List<Snippet> snippets) {
        snippetLibraryEditor.deleteSnippets(snippets);
    }

    private void sendSnippet(Snippet snippet, boolean execute) {
        Tab selectedTab = selectedWorkspaceTab();
        LocalTerminalHandle local = localTerminals.get(selectedTab);
        OpenSession session = openSessions.get(selectedTab);
        if (session == null && local == null) {
            setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5a2d1dcd98e59f65"), StatusTone.ERROR);
            return;
        }
        if (session != null && session.tabs.get(WorkspaceType.SFTP) == selectedTab) {
            setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c8c211339cc101fd"), StatusTone.ERROR);
            return;
        }
        if (session != null && (session.state != ConnectionState.CONNECTED || session.terminal == null)) {
            setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4cb7b6dadb4c74e8"), StatusTone.ERROR);
            return;
        }
        final SnippetTemplateAnalysis analysis;
        try {
            analysis = snippetTemplateEngine.analyze(snippet.content());
        } catch (IllegalArgumentException failure) {
            setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ed6ace65b08cf66c") + com.ynpe.fastremote.i18n.Texts.translateExternalText(failure.getMessage()), StatusTone.ERROR);
            return;
        }
        Optional<Map<String, String>> inputs = requestSnippetInputs(snippet, analysis.inputs());
        if (inputs.isEmpty()) return;
        Map<String, SnippetVariable> variables = new HashMap<>();
        snippetSnapshot.variables().forEach(variable -> variables.put(variable.variableName(), variable));
        Map<String, String> sessionFields = session == null ? Map.of() : Map.of(
                "name", session.name,
                "host", session.profile.host().value(),
                "port", Integer.toString(session.profile.port().value()),
                "username", session.profile.username().value());
        final String rendered;
        try {
            rendered = snippetTemplateEngine.render(snippet.content(),
                    new SnippetTemplateContext(inputs.get(), variables, sessionFields));
        } catch (IllegalArgumentException failure) {
            setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.040e541dda660bfb") + com.ynpe.fastremote.i18n.Texts.translateExternalText(failure.getMessage()), StatusTone.ERROR);
            appendDiagnosticLog(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f1704ab0818e9145") + snippet.name() + "”："
                    + com.ynpe.fastremote.i18n.Texts.translateExternalText(failure.getMessage()),
                    StatusTone.ERROR);
            return;
        }
        ServerOverview.SystemFamily systemFamily = snippetSystemFamily(
                local != null, session == null ? null : session.serverOverview, localSystemFamily());
        LocalTerminalHandle.ShellFamily shellFamily = local != null ? local.shellFamily()
                : commandShellFamily(systemFamily, session == null || session.terminal == null
                        ? LocalTerminalHandle.ShellFamily.UNKNOWN : session.terminal.shellFamily());
        String payload = snippetPayload(rendered, execute, shellFamily);
        appendDiagnosticLog(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b62a2a4899808737") + snippet.name() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.5c55ae89ce823246")
                + (local != null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.51b72143be70e638") : connectionLogName(session.name, session.profile))
                + com.ynpe.fastremote.i18n.Texts.text("legacy.java.bb2f3f34ffcff1df") + (execute ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.32c8d373afe08328") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.33517926747180e6")) + com.ynpe.fastremote.i18n.Texts.text("legacy.java.ab4f3b0660289984")
                + payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                + System.lineSeparator() + visibleDiagnosticContent(
                        redactSensitiveSnippetInputs(payload, inputs.get())), StatusTone.WORKING);
        CompletionStage<Void> sending = local != null ? local.sendInput(payload) : session.terminal.sendInput(payload);
        sending.whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (error != null) setStatus(
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.b11af7693b0ba74a") + messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.82e4839467e14c66")), StatusTone.ERROR);
            else {
                setStatus(localizer.text(execute ? "snippet.executed" : "snippet.pasted", snippet.name()),
                        StatusTone.CONNECTED);
                if (local != null) local.requestFocus(); else session.terminal.requestFocus();
            }
        }));
    }

    static String snippetPayload(
            String rendered, boolean execute, LocalTerminalHandle.ShellFamily shellFamily) {
        Objects.requireNonNull(rendered, "rendered");
        Objects.requireNonNull(shellFamily, "shellFamily");
        if (!execute) return rendered;
        if (shellFamily == LocalTerminalHandle.ShellFamily.COMMAND_PROMPT) {
            String normalized = rendered.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\r\n");
            return normalized.endsWith("\r\n") ? normalized : normalized + "\r\n";
        }
        String normalized = rendered.replace("\r\n", "\n").replace('\r', '\n');
        if (shellFamily == LocalTerminalHandle.ShellFamily.POWERSHELL) {
            normalized = normalized.replace('\n', '\r');
            return normalized.endsWith("\r") ? normalized : normalized + "\r";
        }
        return normalized.endsWith("\n") ? normalized : normalized + "\n";
    }

    static LocalTerminalHandle.ShellFamily commandShellFamily(
            ServerOverview.SystemFamily systemFamily, LocalTerminalHandle.ShellFamily detected) {
        Objects.requireNonNull(systemFamily, "systemFamily");
        Objects.requireNonNull(detected, "detected");
        if (detected != LocalTerminalHandle.ShellFamily.UNKNOWN) return detected;
        return systemFamily == ServerOverview.SystemFamily.WINDOWS
                ? LocalTerminalHandle.ShellFamily.COMMAND_PROMPT
                : LocalTerminalHandle.ShellFamily.POSIX;
    }

    static ServerOverview.SystemFamily snippetSystemFamily(
            boolean localTerminal, ServerOverview serverOverview,
            ServerOverview.SystemFamily localFamily) {
        Objects.requireNonNull(localFamily, "localFamily");
        if (localTerminal) return localFamily;
        return serverOverview == null ? ServerOverview.SystemFamily.UNKNOWN : serverOverview.systemFamily();
    }

    private static String visibleDiagnosticContent(String value) {
        StringBuilder visible = new StringBuilder(Math.min(value.length(), 256 * 1024));
        for (int index = 0; index < value.length() && visible.length() < 256 * 1024; index++) {
            char character = value.charAt(index);
            if (character == '\u001b') visible.append("\\e");
            else if (character == '\u0000') visible.append("\\0");
            else if (character == '\r') visible.append("\\r");
            else visible.append(character);
        }
        if (visible.length() < value.length()) visible.append(System.lineSeparator()).append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b46806bbdd8e7c95"));
        return visible.toString();
    }

    private static String redactSensitiveSnippetInputs(String content, Map<String, String> inputs) {
        String redacted = content;
        for (Map.Entry<String, String> input : inputs.entrySet()) {
            String name = input.getKey().toLowerCase(Locale.ROOT);
            if (!(name.contains("password") || name.contains("passphrase") || name.contains("secret")
                    || name.contains("token") || name.contains("private_key") || name.contains("\u79c1\u94a5")
                    || name.contains("\u5bc6\u7801") || name.contains("\u53e3\u4ee4"))) continue;
            String value = input.getValue();
            if (value != null && !value.isEmpty()) redacted = redacted.replace(value, com.ynpe.fastremote.i18n.Texts.text("legacy.java.e78af63a407bea2a"));
        }
        return redacted;
    }

    private Optional<Map<String, String>> requestSnippetInputs(Snippet snippet, List<SnippetInput> inputs) {
        return SnippetDialogs.requestInputs(
                snippetDialogOwner(), snippet, inputs, snippetSnapshot.variables());
    }

    private TreeCell<String> createHostTreeCell() {
        TreeCell<String> cell = new StableGraphicTreeCell() {
            @Override protected Node createGraphic(TreeItem<String> treeItem) {
                return catalogIcon(treeItem);
            }
        };
        cell.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            TreeItem<String> item = cell.getTreeItem();
            pressedCatalogSelection = event.getButton() == MouseButton.PRIMARY && item != null
                    && hostTree.getSelectionModel().getSelectedItems().contains(item)
                    ? List.copyOf(hostTree.getSelectionModel().getSelectedItems()) : List.of();
        });
        cell.setOnDragDetected(event -> {
            TreeItem<String> item = cell.getTreeItem();
            if (item == null || event.getButton() != MouseButton.PRIMARY
                    || (!hostItems.containsKey(item) && !groupItems.containsKey(item))) return;
            if (!hostSearchField.getText().isBlank()) {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.95c671b69d6a2c08"), StatusTone.IDLE);
                return;
            }
            draggedCatalogItems = dragSelection(item,
                    pressedCatalogSelection.isEmpty()
                            ? List.copyOf(hostTree.getSelectionModel().getSelectedItems())
                            : pressedCatalogSelection,
                    hostItems, groupItems);
            if (draggedCatalogItems.size() == 1
                    && !hostTree.getSelectionModel().getSelectedItems().contains(item))
                hostTree.getSelectionModel().clearAndSelect(cell.getIndex());
            var dragboard = cell.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString("fastremote/catalog:" + draggedCatalogItems.size());
            dragboard.setContent(content);
            event.consume();
        });
        cell.setOnDragOver(event -> {
            DropPosition position = dropPosition(event.getY(), cell.getHeight(), groupItems.containsKey(cell.getTreeItem()));
            if (validCatalogDropTarget(cell.getTreeItem(), position)) {
                event.acceptTransferModes(TransferMode.MOVE);
                showDropTarget(cell, position);
                event.consume();
            } else {
                clearDropTarget(cell);
            }
        });
        cell.setOnDragExited(event -> clearDropTarget(cell));
        cell.setOnDragDropped(event -> {
            TreeItem<String> target = cell.getTreeItem();
            DropPosition position = dropPosition(event.getY(), cell.getHeight(), groupItems.containsKey(target));
            List<SavedHost> draggedHosts = draggedCatalogItems.stream()
                    .map(hostItems::get).filter(Objects::nonNull).toList();
            List<HostGroup> draggedGroups = draggedCatalogItems.stream()
                    .map(groupItems::get).filter(Objects::nonNull).toList();
            clearDropTarget(cell);
            if (!validCatalogDropTarget(target, position)) return;
            event.setDropCompleted(true);
            draggedCatalogItems = List.of();
            pressedCatalogSelection = List.of();
            CompletionStage<?> move = CompletableFuture.completedFuture(null);
            String label;
            if (!draggedHosts.isEmpty()) {
                Optional<GroupId> destination = dropGroup(target, position);
                int targetIndex = hostDropIndex(draggedHosts, target, destination, position);
                Set<com.ynpe.fastremote.domain.catalog.HostId> movedIds = draggedHosts.stream()
                        .map(SavedHost::id).collect(java.util.stream.Collectors.toSet());
                List<SavedHost> ordered = catalog.hosts().stream()
                        .filter(host -> host.groupId().equals(destination) && !movedIds.contains(host.id()))
                        .sorted(Comparator.comparingInt(SavedHost::sortOrder)
                                .thenComparing(SavedHost::name, String.CASE_INSENSITIVE_ORDER))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                ordered.addAll(targetIndex, draggedHosts);
                for (int index = 0; index < ordered.size(); index++) {
                    SavedHost host = ordered.get(index);
                    int destinationIndex = index;
                    move = move.thenCompose(ignored -> catalogWorkflow.moveHost(
                            host.id(), destination, destinationIndex));
                }
                label = draggedHosts.size() == 1 ? draggedHosts.getFirst().name()
                        : draggedHosts.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.ef19aca25dd22faa");
            } else if (!draggedGroups.isEmpty()) {
                GroupDrop drop = groupDrop(draggedGroups, target, position);
                Set<GroupId> movedIds = draggedGroups.stream().map(HostGroup::id)
                        .collect(java.util.stream.Collectors.toSet());
                List<HostGroup> ordered = catalog.groups().stream()
                        .filter(group -> group.parentId().equals(drop.parentId()) && !movedIds.contains(group.id()))
                        .sorted(Comparator.comparingInt(HostGroup::sortOrder)
                                .thenComparing(HostGroup::name, String.CASE_INSENSITIVE_ORDER))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                ordered.addAll(drop.targetIndex(), draggedGroups);
                for (int index = 0; index < ordered.size(); index++) {
                    HostGroup group = ordered.get(index);
                    int destinationIndex = index;
                    move = move.thenCompose(ignored -> catalogWorkflow.moveGroup(
                            group.id(), drop.parentId(), destinationIndex));
                }
                label = draggedGroups.size() == 1 ? draggedGroups.getFirst().name()
                        : draggedGroups.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.3ca8a114e74cdddb");
            } else {
                return;
            }
            move.whenComplete((ignored, error) ->
                    Platform.runLater(() -> {
                        if (error != null) {
                            setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d3f1e9f6f6e921d1")), StatusTone.ERROR);
                        } else {
                            setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c2e2eb5028597df7") + label, StatusTone.IDLE);
                            refreshCatalog();
                        }
                    }));
            event.consume();
        });
        cell.setOnDragDone(event -> {
            draggedCatalogItems = List.of();
            clearDropTarget(cell);
        });
        cell.setOnContextMenuRequested(event -> {
            TreeItem<String> item = cell.getTreeItem();
            if (item != null) selectContextTreeItem(hostTree, item);
            ContextMenu menu = catalogContextMenu(item);
            menu.show(cell, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        return cell;
    }

    private ContextMenu catalogContextMenu(TreeItem<String> item) {
        MenuItem newHost = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.22a3f0c4b4bd8606"));
        newHost.setOnAction(event -> createHostAt(targetGroupForHost(item)));
        showShortcut(newHost, ShortcutAction.CREATE_HOST);
        MenuItem newGroup = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3d49d9fcb6d1bbc5"));
        newGroup.setOnAction(event -> createGroupAt(suggestedPrimaryGroup(item)));
        ContextMenu menu = ApplicationContextMenus.create(newHost, newGroup);
        menu.getStyleClass().add("catalog-context-menu");
        if (item != null && (hostItems.containsKey(item) || groupItems.containsKey(item))) {
            MenuItem edit = new MenuItem(hostItems.containsKey(item) ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.50158b7fd5855d55") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.d00b2a96c3b35930"));
            edit.setOnAction(event -> editCatalogItem(item));
            showShortcut(edit, ShortcutAction.EDIT_SELECTED_LIBRARY_ITEM);
            MenuItem copy = new MenuItem(hostItems.containsKey(item) ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.dd56ba585c080a47") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.124ae8893e84a75e"));
            copy.setOnAction(event -> copyCatalogItem(item));
            showShortcut(copy, ShortcutAction.COPY_SELECTED_LIBRARY_ITEM);
            int selectedCount = selectedCatalogItemCount(hostTree, hostItems, groupItems);
            MenuItem delete = new MenuItem(selectedCount > 1
                    ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.435bf8a234fde6f2") : hostItems.containsKey(item) ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.ab6b223c2ef992da") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.7605c1d82eed69a5"));
            delete.setOnAction(event -> deleteCatalogSelection(
                    hostTree, hostItems, groupItems, rootPane.getScene().getWindow()));
            showShortcut(delete, ShortcutAction.DELETE_SELECTED_LIBRARY_ITEM);
            menu.getItems().add(new SeparatorMenuItem());
            if (hostItems.containsKey(item)) {
                SavedHost host = hostItems.get(item);
                MenuItem openTerminal = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b89a0e4584f27ab5"));
                openTerminal.setOnAction(event -> connectHost(host, WorkspaceType.TERMINAL));
                showShortcut(openTerminal, ShortcutAction.OPEN_SELECTED_LIBRARY_ITEM);
                MenuItem openSftp = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3b350c8e49f7b7c3"));
                openSftp.setOnAction(event -> connectHost(host, WorkspaceType.SFTP));
                MenuItem favorite = new MenuItem(host.favorite() ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.dca60869e7d26839") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.991ab30a8e4da7c7"));
                favorite.setOnAction(event -> setHostFavorite(host, !host.favorite()));
                menu.getItems().addAll(openTerminal, openSftp, new SeparatorMenuItem(), favorite);
            }
            menu.getItems().addAll(edit, copy, delete);
        }
        withExpansionActions(menu, hostTree);
        if (item != null && (hostItems.containsKey(item) || groupItems.containsKey(item))) {
            addHostLibraryCopyActions(menu, hostTree, hostItems, groupItems);
        }
        return menu;
    }

    private static void selectContextTreeItem(TreeView<String> tree, TreeItem<String> item) {
        int row = tree.getRow(item);
        if (row >= 0 && !tree.getSelectionModel().isSelected(row)) {
            tree.getSelectionModel().clearAndSelect(row);
        }
    }

    private static int selectedCatalogItemCount(
            TreeView<String> tree,
            Map<TreeItem<String>, SavedHost> hosts,
            Map<TreeItem<String>, HostGroup> groups) {
        return (int) tree.getSelectionModel().getSelectedItems().stream()
                .filter(item -> hosts.containsKey(item) || groups.containsKey(item))
                .count();
    }

    private static List<SavedHost> selectedHosts(
            TreeView<String> tree, Map<TreeItem<String>, SavedHost> hosts) {
        return tree.getSelectionModel().getSelectedItems().stream()
                .map(hosts::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static List<HostGroup> selectedGroups(
            TreeView<String> tree, Map<TreeItem<String>, HostGroup> groups) {
        return tree.getSelectionModel().getSelectedItems().stream()
                .map(groups::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    static <T> T editTarget(T contextItem, List<T> selectedItems) {
        if (contextItem != null) return contextItem;
        return selectedItems.isEmpty() ? null : selectedItems.getFirst();
    }

    private void setHostFavorite(SavedHost host, boolean favorite) {
        catalogWorkflow.saveHost(host.withFavorite(favorite), Optional.empty())
                .whenComplete((saved, error) -> Platform.runLater(() -> {
                    if (error != null) setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.24a7f4dc9c7d6060")), StatusTone.ERROR);
                    else {
                        setStatus(favorite ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.ef032e0715cbfe3a") + saved.name() : com.ynpe.fastremote.i18n.Texts.text("legacy.java.f6603a2aa3fabc87") + saved.name(),
                                StatusTone.IDLE);
                        refreshCatalog();
                    }
                }));
    }

    private Optional<GroupId> targetGroupForHost(TreeItem<String> item) {
        HostGroup group = groupItems.get(item);
        if (group != null) return Optional.of(group.id());
        SavedHost host = hostItems.get(item);
        return host == null ? Optional.empty() : host.groupId();
    }

    private Optional<GroupId> suggestedPrimaryGroup(TreeItem<String> item) {
        HostGroup group = groupItems.get(item);
        if (group == null) {
            SavedHost host = hostItems.get(item);
            group = host == null ? null : host.groupId().flatMap(this::findGroup).orElse(null);
        }
        if (group == null) return Optional.empty();
        return group.parentId().isEmpty() ? Optional.of(group.id()) : group.parentId();
    }

    private Optional<HostGroup> findGroup(GroupId id) {
        return catalog.groups().stream().filter(group -> group.id().equals(id)).findFirst();
    }

    private boolean validCatalogDropTarget(TreeItem<String> target, DropPosition position) {
        return LibraryTreeSupport.validCatalogDropTarget(
                target, position, draggedCatalogItems, hostItems, groupItems);
    }

    private Optional<GroupId> dropGroup(TreeItem<String> target, DropPosition position) {
        return dropGroup(target, position, hostItems, groupItems);
    }

    private static Optional<GroupId> dropGroup(
            TreeItem<String> target, DropPosition position,
            Map<TreeItem<String>, SavedHost> hosts,
            Map<TreeItem<String>, HostGroup> groups) {
        HostGroup group = groups.get(target);
        if (group != null && position == DropPosition.INTO) return Optional.of(group.id());
        SavedHost host = hosts.get(target);
        return host == null ? Optional.empty() : host.groupId();
    }

    private int hostDropIndex(
            SavedHost dragged, TreeItem<String> target, Optional<GroupId> destination, DropPosition position) {
        return hostDropIndex(dragged, target, destination, position, hostItems);
    }

    private int hostDropIndex(
            List<SavedHost> dragged, TreeItem<String> target,
            Optional<GroupId> destination, DropPosition position) {
        Set<com.ynpe.fastremote.domain.catalog.HostId> draggedIds = dragged.stream().map(SavedHost::id)
                .collect(java.util.stream.Collectors.toSet());
        List<SavedHost> siblings = catalog.hosts().stream()
                .filter(host -> host.groupId().equals(destination) && !draggedIds.contains(host.id()))
                .sorted(Comparator.comparingInt(SavedHost::sortOrder)
                        .thenComparing(SavedHost::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        SavedHost targetHost = hostItems.get(target);
        if (targetHost == null) return siblings.size();
        int index = Math.max(0, siblings.indexOf(targetHost));
        return position == DropPosition.AFTER ? index + 1 : index;
    }

    private int hostDropIndex(
            SavedHost dragged, TreeItem<String> target, Optional<GroupId> destination, DropPosition position,
            Map<TreeItem<String>, SavedHost> hosts) {
        List<SavedHost> siblings = catalog.hosts().stream()
                .filter(host -> host.groupId().equals(destination) && !host.id().equals(dragged.id()))
                .sorted(java.util.Comparator.comparingInt(SavedHost::sortOrder)
                        .thenComparing(SavedHost::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        SavedHost targetHost = hosts.get(target);
        if (targetHost == null) return siblings.size();
        int index = Math.max(0, siblings.indexOf(targetHost));
        return position == DropPosition.AFTER ? index + 1 : index;
    }

    private GroupDrop groupDrop(HostGroup dragged, TreeItem<String> target, DropPosition position) {
        return groupDrop(dragged, target, position, groupItems);
    }

    private GroupDrop groupDrop(
            List<HostGroup> dragged, TreeItem<String> target, DropPosition position) {
        Set<GroupId> draggedIds = dragged.stream().map(HostGroup::id)
                .collect(java.util.stream.Collectors.toSet());
        HostGroup targetGroup = groupItems.get(target);
        Optional<GroupId> parent = position == DropPosition.INTO
                ? Optional.of(targetGroup.id()) : targetGroup.parentId();
        List<HostGroup> siblings = catalog.groups().stream()
                .filter(group -> group.parentId().equals(parent) && !draggedIds.contains(group.id()))
                .sorted(Comparator.comparingInt(HostGroup::sortOrder)
                        .thenComparing(HostGroup::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (position == DropPosition.INTO) return new GroupDrop(parent, siblings.size());
        int index = Math.max(0, siblings.indexOf(targetGroup));
        return new GroupDrop(parent, position == DropPosition.AFTER ? index + 1 : index);
    }

    private GroupDrop groupDrop(
            HostGroup dragged, TreeItem<String> target, DropPosition position,
            Map<TreeItem<String>, HostGroup> groups) {
        if (target == null) {
            Optional<GroupId> parent = Optional.empty();
            return new GroupDrop(parent, groupSiblingCount(dragged, parent));
        }
        HostGroup targetGroup = groups.get(target);
        if (position == DropPosition.INTO) {
            Optional<GroupId> parent = Optional.of(targetGroup.id());
            return new GroupDrop(parent, groupSiblingCount(dragged, parent));
        }
        int index = groupIndexBefore(dragged, targetGroup, targetGroup.parentId());
        return new GroupDrop(targetGroup.parentId(), position == DropPosition.AFTER ? index + 1 : index);
    }

    private Node catalogIcon(TreeItem<String> item) {
        return catalogIcon(groupItems.get(item), hostItems.get(item));
    }

    private static Node catalogIcon(HostGroup group, SavedHost host) {
        if (group == null && host == null) return null;
        LucideIconView icon;
        if (group != null) {
            icon = new LucideIconView(Icon.FOLDER);
            icon.getStyleClass().setAll("catalog-item-icon", "catalog-group-icon", groupColorClass(group.color()));
        } else {
            icon = new LucideIconView(HostIconGraphics.icon(host.icon()));
            icon.getStyleClass().setAll("catalog-item-icon", "catalog-host-icon");
        }
        return icon;
    }

    private static String groupColorClass(GroupColor color) {
        return "catalog-group-" + color.name().toLowerCase(java.util.Locale.ROOT);
    }

    private int groupSiblingCount(HostGroup dragged, Optional<GroupId> parent) {
        return (int) catalog.groups().stream()
                .filter(group -> group.parentId().equals(parent) && !group.id().equals(dragged.id())).count();
    }

    private int groupIndexBefore(HostGroup dragged, HostGroup target, Optional<GroupId> parent) {
        List<HostGroup> siblings = catalog.groups().stream()
                .filter(group -> group.parentId().equals(parent) && !group.id().equals(dragged.id()))
                .sorted(java.util.Comparator.comparingInt(HostGroup::sortOrder)
                        .thenComparing(HostGroup::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return Math.max(0, siblings.indexOf(target));
    }

    private void refreshCatalog() {
        catalogWorkflow.loadCatalog("").whenComplete((snapshot, error) ->
                Platform.runLater(() -> {
                    if (error != null) {
                        setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.8eaaed14d1f4ad1e")), StatusTone.ERROR);
                        return;
                    }
                    catalog = snapshot;
                    rebuildHostTree(filteredCatalog(
                            snapshot, hostSearchField.getText(), selectedHostFilter(
                                    hostAllFilter.getToggleGroup())));
                    rebuildCredentialChoices(snapshot.credentials());
                    rebuildQuickJumpChoices(snapshot.hosts(), snapshot.groups());
                    rebuildHomeRecentHosts(snapshot);
                    updateVaultStatus(snapshot.vaultStatus());
                    List.copyOf(catalogRefreshListeners).forEach(Runnable::run);
                    if (snapshot.vaultStatus() == VaultStatus.NOT_CREATED && !initialVaultPromptShown) {
                        initialVaultPromptShown = true;
                        Platform.runLater(() -> requestVaultPassword(true));
                    }
                }));
    }

    private void initializeHomePage() {
        recentHostList.setCellFactory(ignored -> new ListCell<>() {
            private final LucideIconView hostIcon = new LucideIconView(Icon.SERVER);
            private final Label title = new Label();
            private final Label detail = new Label();
            private final Button terminal = homeRecentAction(Icon.SQUARE_TERMINAL, com.ynpe.fastremote.i18n.Texts.text("legacy.java.b89a0e4584f27ab5"));
            private final Button sftp = homeRecentAction(Icon.FOLDER, com.ynpe.fastremote.i18n.Texts.text("legacy.java.3b350c8e49f7b7c3"));
            private final VBox text = new VBox(1.0, title, detail);
            private final HBox row = new HBox(7.0, hostIcon, text, terminal, sftp);
            {
                hostIcon.getStyleClass().add("home-recent-icon");
                title.getStyleClass().add("home-recent-title");
                detail.getStyleClass().add("home-recent-copy");
                text.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(text, Priority.ALWAYS);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("home-recent-row");
                terminal.setOnAction(event -> openRecentConnection(getItem(), WorkspaceType.TERMINAL));
                sftp.setOnAction(event -> openRecentConnection(getItem(), WorkspaceType.SFTP));
            }

            @Override
            protected void updateItem(RecentConnection connection, boolean empty) {
                super.updateItem(connection, empty);
                if (empty || connection == null) {
                    setText(null);
                    setGraphic(null);
                    setAccessibleText(null);
                    return;
                }
                hostIcon.setIcon(HostIconGraphics.icon(connection.icon()));
                title.setText(connection.name());
                detail.setText(homeRecentConnectionDetail(connection, catalog));
                setText(null);
                setGraphic(row);
                setAccessibleText(connection.name() + "，" + detail.getText());
            }
        });
        recentHostList.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openRecentConnection(recentHostList.getSelectionModel().getSelectedItem(), WorkspaceType.TERMINAL);
            }
        });
        rebuildHomeRecentHosts(catalog);
    }

    private static Button homeRecentAction(Icon icon, String accessibleText) {
        Button button = new Button();
        button.setGraphic(new LucideIconView(icon));
        button.setAccessibleText(accessibleText);
        button.setTooltip(new Tooltip(accessibleText));
        button.getStyleClass().add("home-recent-action");
        return button;
    }

    private void rebuildHomeRecentHosts(HostCatalogSnapshot snapshot) {
        List<RecentConnection> recent = homeRecentConnections(snapshot);
        recentHostList.getItems().setAll(recent);
        setManagedVisible(recentHostList, !recent.isEmpty());
        setManagedVisible(recentHostEmpty, recent.isEmpty());
    }

    private void openRecentConnection(RecentConnection connection, WorkspaceType type) {
        if (connection == null) return;
        if (connection.savedHost()) {
            SavedHost saved = connection.hostId().flatMap(id -> catalog.hosts().stream()
                    .filter(host -> host.id().equals(id)).findFirst()).orElse(null);
            if (saved == null) {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.64dfbeeb55274cd5"), StatusTone.ERROR);
                refreshCatalog();
                return;
            }
            connectHost(saved, type);
            return;
        }
        showQuickConnectDialog(connection, type);
    }

    private static ToggleGroup exclusiveHostFilters(ToggleButton all, ToggleButton favorites, ToggleButton recent) {
        ToggleGroup group = new ToggleGroup();
        all.setUserData(HostFilter.ALL);
        favorites.setUserData(HostFilter.FAVORITES);
        recent.setUserData(HostFilter.RECENT);
        all.setToggleGroup(group);
        favorites.setToggleGroup(group);
        recent.setToggleGroup(group);
        if (group.getSelectedToggle() == null) all.setSelected(true);
        return group;
    }

    private static HostFilter selectedHostFilter(ToggleGroup group) {
        if (group == null || group.getSelectedToggle() == null) return HostFilter.ALL;
        return (HostFilter) group.getSelectedToggle().getUserData();
    }

    private void rebuildHostTree(HostCatalogSnapshot snapshot) {
        HostLibraryTree.rebuild(hostTree, hostItems, groupItems, snapshot,
                (item, id) -> registerGroupExpansion(item, hostGroupExpansionKey(id)),
                root -> prepareLibrarySearch(root, groupItems, hostSearchField.getText()));
    }

    @FXML
    private void focusQuickConnect() {
        promptedCredentialHost = null;
        updateAuthenticationFields();
        showToolWindow(ToolWindow.QUICK_CONNECT, false);
        Platform.runLater(hostField::requestFocus);
    }

    @FXML
    private void showQuickConnectDialog() {
        showQuickConnectDialog(null, WorkspaceType.TERMINAL);
    }

    private void showQuickConnectDialog(Tab insertionAnchor) {
        showQuickConnectDialog(null, WorkspaceType.TERMINAL,
                java.util.UUID.randomUUID(), null, insertionAnchor);
    }

    private void showQuickConnectDialog(RecentConnection recent, WorkspaceType initialType) {
        showQuickConnectDialog(recent, initialType, java.util.UUID.randomUUID(), null, null);
    }

    private void showQuickConnectDialog(
            RecentConnection recent, WorkspaceType initialType, java.util.UUID pairingId,
            String initialRemotePath) {
        showQuickConnectDialog(recent, initialType, pairingId, initialRemotePath, null);
    }

    private void showQuickConnectDialog(
            RecentConnection recent, WorkspaceType initialType, java.util.UUID pairingId,
            String initialRemotePath, Tab insertionAnchor) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setDialogPane(new InsertionOrderedDialogPane());
        dialog.initOwner(rootPane.getScene().getWindow());
        dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.bec3d896410b5294"));
        dialog.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.129d40826f7f53f2"));
        dialog.setResizable(quickConnectDialogResizable());
        DialogSpacing.prepare(dialog);

        TextField host = new TextField();
        host.setPromptText("server.example.com");
        TextField port = new TextField("22");
        port.setPrefColumnCount(5);
        TextField username = new TextField();
        username.setPromptText("root");
        MenuButton jumpSession = new MenuButton();
        SearchableJumpHostSelector jumpSelector = new SearchableJumpHostSelector(
                jumpSession, catalog.hosts(), catalog.groups(), quickJumpHostId(), Optional.empty());
        ComboBox<QuickCredentialSource> source = new ComboBox<>();
        source.getItems().setAll(QuickCredentialSource.values());
        source.setValue(QuickCredentialSource.MANUAL);
        source.setMaxWidth(Double.MAX_VALUE);
        MenuButton library = new MenuButton();
        SearchableCredentialSelector credentialSelector = new SearchableCredentialSelector(
                library, catalog.credentials(), catalog.credentialGroups(),
                quickCredentialSelector.selectedCredential().map(GlobalCredential::id));
        ComboBox<AuthenticationType> authentication = new ComboBox<>();
        authentication.getItems().setAll(AuthenticationType.PASSWORD, AuthenticationType.PRIVATE_KEY);
        authentication.setValue(AuthenticationType.PASSWORD);
        authentication.setCellFactory(ignored -> quickAuthenticationCell());
        authentication.setButtonCell(quickAuthenticationCell());
        if (recent != null) {
            host.setText(recent.host().value());
            port.setText(Integer.toString(recent.port().value()));
            username.setText(recent.username().value());
            authentication.setValue(recent.authenticationType());
        }
        TextField privateKey = new TextField();
        Button chooseKey = FileSelectionButton.create(
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.c11330b85234f9c0"));
        AnchorPane keyRow = privateKeyRow(privateKey, chooseKey);
        RevealablePasswordEditor hostPassword = new RevealablePasswordEditor(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7be1419614f3508d"));
        hostPassword.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f2fc5e01bdeadb80"));
        RevealablePasswordEditor privateKeyPassphrase = new RevealablePasswordEditor(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d2ec1829b67bbe98"));
        privateKeyPassphrase.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d1c3dc89bb25e351"));
        CheckBox legacyAlgorithms = new CheckBox(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8702dbfeecca0ce2"));
        legacyAlgorithms.setTooltip(new Tooltip(com.ynpe.fastremote.i18n.Texts.text("legacy.java.cf205dc1042fc648")));
        Label feedback = ConnectionFeedback.reservedLabel();

        AnchorPane form = new AnchorPane();
        form.getStyleClass().add("quick-connect-fields-shell");
        List<String> fieldLabels = quickConnectDialogFieldLabels();
        List<Node> fields = List.of(host, port, username, source, authentication, hostPassword,
                keyRow, privateKeyPassphrase, library, legacyAlgorithms, jumpSession);
        for (int index = 0; index < fieldLabels.size(); index++) {
            addQuickFormRow(form, index, fieldLabels.get(index), fields.get(index));
        }
        form.setMinHeight(quickFormHeight(fieldLabels.size()));
        form.setPrefHeight(quickFormHeight(fieldLabels.size()));
        form.setMaxHeight(quickFormHeight(fieldLabels.size()));
        form.setMinWidth(quickFormFieldLeft() + QUICK_FORM_FIELD_MIN_WIDTH);

        Runnable updateFields = () -> {
            boolean fromLibrary = manualQuickFieldsDisabled(source.getValue());
            library.setDisable(!fromLibrary);
            GlobalCredential selected = fromLibrary
                    ? credentialSelector.selectedCredential().orElse(null) : null;
            if (fromLibrary && selected == null && !credentialSelector.isEmpty()) {
                credentialSelector.selectFirst();
                selected = credentialSelector.selectedCredential().orElse(null);
            }
            if (selected != null) {
                username.setText(selected.username());
                authentication.setValue(selected.authenticationType());
            }
            boolean privateKeyAuthentication = authentication.getValue() == AuthenticationType.PRIVATE_KEY;
            username.setDisable(fromLibrary);
            authentication.setDisable(fromLibrary);
            privateKey.setDisable(fromLibrary || !privateKeyAuthentication);
            chooseKey.setDisable(fromLibrary || !privateKeyAuthentication);
            hostPassword.setDisable(fromLibrary || privateKeyAuthentication);
            privateKeyPassphrase.setDisable(fromLibrary || !privateKeyAuthentication);
        };
        source.valueProperty().addListener((ignored, previous, selected) -> updateFields.run());
        credentialSelector.setOnSelectionChanged(updateFields);
        authentication.valueProperty().addListener((ignored, previous, selected) -> updateFields.run());
        updateFields.run();
        chooseKey.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1c331767f030aa90"));
            File selected = PlatformFileChooser.showOpenDialog(
                    chooser, dialog.getDialogPane().getScene().getWindow());
            if (selected != null) privateKey.setText(selected.getAbsolutePath());
        });

        Button parseClipboard = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1c0d0153188749d8"));
        parseClipboard.setMaxWidth(Double.MAX_VALUE);
        parseClipboard.setOnAction(event -> {
            parseClipboardInto(host, port, username, authentication, source, credentialSelector,
                    hostPassword, privateKey, privateKeyPassphrase, feedback);
        });
        VBox content = new VBox(10.0, parseClipboard, form, feedback);
        content.setPadding(new javafx.geometry.Insets(14.0, 18.0, 6.0, 18.0));
        dialog.getDialogPane().setContent(content);
        configureQuickConnectDialogSizing(dialog);

        List<ButtonType> dialogButtons = quickConnectDialogButtonTypes();
        ButtonType test = dialogButtons.get(0);
        ButtonType save = dialogButtons.get(1);
        ButtonType connect = dialogButtons.get(3);
        dialog.getDialogPane().getButtonTypes().setAll(dialogButtons);
        configureQuickConnectDialogButtonWidths(dialog.getDialogPane(), dialogButtons);
        dialog.setResultConverter(DialogResultPolicy.explicitOnly());
        Runnable validateValues = () -> {
            FormValidation.value(host, () -> new Host(requiredQuickValue(host.getText(), com.ynpe.fastremote.i18n.Texts.text("legacy.java.0d2f997c185f913d"))));
            FormValidation.value(username, () -> new Username(requiredQuickValue(username.getText(), com.ynpe.fastremote.i18n.Texts.text("legacy.java.c723b1fab58f5dbc"))));
            int parsedPort;
            try {
                parsedPort = Integer.parseInt(port.getText().strip());
            } catch (NumberFormatException failure) {
                DialogSpacing.focusAtEnd(port);
                throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.584c7ad845c11aa4"), failure);
            }
            if (parsedPort < 1 || parsedPort > 65535) {
                DialogSpacing.focusAtEnd(port);
                throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.584c7ad845c11aa4"));
            }
            if (source.getValue() == QuickCredentialSource.LIBRARY
                    && credentialSelector.selectedCredential().isEmpty()) {
                library.requestFocus();
                throw new IllegalArgumentException(credentialSelector.isEmpty()
                        ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.7b9903e626187f28") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.45496152d757090a"));
            }
            if (source.getValue() == QuickCredentialSource.MANUAL
                    && authentication.getValue() == AuthenticationType.PASSWORD
                    && hostPassword.getText().isEmpty()) {
                hostPassword.focusInput();
                throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f79c2da2bb2c6440"));
            }
            if (source.getValue() == QuickCredentialSource.MANUAL
                    && authentication.getValue() == AuthenticationType.PRIVATE_KEY
                    && privateKey.getText().isBlank()) {
                DialogSpacing.focusAtEnd(privateKey);
                throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.776eaa6963f26389"));
            }
            FormValidation.value(jumpSession, () -> { HostEditorDialog.validateJumpSelection(Optional.empty(), jumpSelector.selectedHostId(), catalog.hosts()); return null; });
            if (!privateKey.isDisabled() && !privateKey.getText().isBlank()) FormValidation.value(privateKey, () -> Path.of(privateKey.getText()));
        };
        Runnable applyValues = () -> {
            validateValues.run();
            int parsedPort = parseQuickPort(port.getText());
            hostField.setText(host.getText().strip());
            portField.setText(Integer.toString(parsedPort));
            usernameField.setText(username.getText().strip());
            quickJumpSelector.rebuild(
                    catalog.hosts(), catalog.groups(), jumpSelector.selectedHostId(), Optional.empty());
            authenticationField.setValue(authentication.getValue());
            privateKeyField.setText(privateKey.getText().strip());
            hostPasswordField.setText(hostPassword.getText());
            privateKeyPassphraseField.setText(privateKeyPassphrase.getText());
            legacyAlgorithmsField.setSelected(legacyAlgorithms.isSelected());
            credentialSourceField.setValue(source.getValue());
            if (source.getValue() == QuickCredentialSource.LIBRARY) {
                credentialSelector.selectedCredential().map(GlobalCredential::id)
                        .ifPresent(quickCredentialSelector::selectCredential);
            }
            updateAuthenticationFields();
        };
        java.util.function.Consumer<Runnable> perform = action -> {
            try {
                applyValues.run();
                ConnectionFeedback.clear(feedback);
                action.run();
            } catch (IllegalArgumentException failure) {
                ConnectionFeedback.show(feedback, com.ynpe.fastremote.i18n.Texts.translateExternalText(failure.getMessage()), ConnectionFeedback.Tone.ERROR);
            }
        };
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
        Button testButton = (Button) dialog.getDialogPane().lookupButton(test);
        Button connectButton = (Button) dialog.getDialogPane().lookupButton(connect);
        connectButton.setDefaultButton(true);
        testButton.setDefaultButton(false);
        saveButton.setDefaultButton(false);
        QuickConnectionTestRunner dialogConnectionTestRunner = new QuickConnectionTestRunner(
                () -> dialog.getDialogPane().getScene().getWindow(),
                () -> { validateValues.run(); return quickConnectionTestRequest(
                        host, port, username, authentication.getValue(), source.getValue(),
                        credentialSelector.selectedCredential().orElse(null),
                        hostPassword.getText(), privateKey.getText(), privateKeyPassphrase.getText(),
                        legacyAlgorithms.isSelected(), jumpSelector.selectedHostId()); },
                form, testButton, List.of(parseClipboard, saveButton, connectButton), feedback);
        installQuickConnectionTestReset(
                dialogConnectionTestRunner, host, port, username, authentication, source,
                hostPassword, privateKey, privateKeyPassphrase, legacyAlgorithms);
        jumpSelector.setOnSelectionChanged(dialogConnectionTestRunner::clearCompletedStatus);
        credentialSelector.setOnSelectionChanged(() -> {
            updateFields.run();
            dialogConnectionTestRunner.clearCompletedStatus();
        });
        testButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            dialogConnectionTestRunner.toggle();
        });
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            perform.accept(() -> saveCurrentHost(dialog.getDialogPane().getScene().getWindow()));
        });
        connectButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            perform.accept(() -> {
                dialog.close();
                registerWorkspaceInsertionAnchor(pairingId, insertionAnchor);
                connect(initialType, message -> setStatus(message, StatusTone.ERROR),
                        pairingId, initialRemotePath);
            });
        });
        dialog.setOnHidden(event -> {
            dialogConnectionTestRunner.close();
            hostPassword.clear();
            privateKeyPassphrase.clear();
        });
        dialog.setOnShown(event -> Platform.runLater(() -> DialogSpacing.focusAtEnd(host)));
        dialog.showAndWait();
    }

    private static ListCell<AuthenticationType> quickAuthenticationCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(AuthenticationType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : item == AuthenticationType.PASSWORD ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.a621ab606db2a11f") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.7ea96a871de9fe44"));
            }
        };
    }

    @FXML
    private void focusSnippetManager() {
        showToolWindow(ToolWindow.SNIPPETS, false);
    }

    @FXML
    private void focusVariableManager() {
        showToolWindow(ToolWindow.VARIABLE_LIBRARY, false);
    }

    @FXML
    private void focusCredentialManager() {
        showToolWindow(ToolWindow.CREDENTIAL_VAULT, false);
    }

    @FXML
    private void openSftpFromHome() {
        RecentConnection selected = recentHostList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            openRecentConnection(selected, WorkspaceType.SFTP);
            return;
        }
        setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.996be4d81c6d323d"), StatusTone.IDLE);
        showHostLibraryDialog();
    }

    @FXML
    private void createReusableCredential() {
        createReusableCredentialAt(Optional.empty());
    }

    private void createReusableCredentialAt(Optional<CredentialGroupId> groupId) {
        credentialLibraryEditor.createReusableCredentialAt(groupId);
    }

    private ContextMenu credentialLibraryContextMenu() {
        MenuItem create = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.9ef774b3220dc7d9"));
        create.setOnAction(event -> createReusableCredential());
        MenuItem createGroup = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3d49d9fcb6d1bbc5"));
        createGroup.setOnAction(event -> createCredentialGroup());
        ContextMenu menu = ApplicationContextMenus.create(create, createGroup);
        menu.getStyleClass().add("catalog-context-menu");
        return menu;
    }

    private ContextMenu credentialContextMenu(GlobalCredential credential) {
        MenuItem create = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.9ef774b3220dc7d9"));
        create.setOnAction(event -> createReusableCredential());
        MenuItem edit = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4cf2ff842a0cfa66"));
        edit.setOnAction(event -> editReusableCredential(editTarget(
                credential, selectedCredentials())));
        showShortcut(edit, ShortcutAction.EDIT_SELECTED_LIBRARY_ITEM);
        MenuItem delete = new MenuItem(selectedCredentials().size() > 1
                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.435bf8a234fde6f2") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.5da9db927b31092a"));
        delete.setOnAction(event -> deleteReusableCredentials(
                selectedCredentials(),
                rootPane.getScene().getWindow()));
        showShortcut(delete, ShortcutAction.DELETE_SELECTED_LIBRARY_ITEM);
        ContextMenu menu = ApplicationContextMenus.create(create, edit, new SeparatorMenuItem(), delete);
        edit.setOnAction(event -> {
            GlobalCredential selected = editTarget(credential, selectedCredentials());
            ApplicationContextMenus.runAfterHide(menu, () -> editReusableCredential(selected));
        });
        menu.getStyleClass().add("catalog-context-menu");
        return menu;
    }

    private void editReusableCredential(GlobalCredential credential) {
        credentialLibraryEditor.editReusableCredential(credential);
    }

    private void deleteReusableCredentials(List<GlobalCredential> credentials, Window owner) {
        credentialLibraryEditor.deleteReusableCredentials(credentials, owner);
    }

    @FXML
    private void toggleHostLibrary() {
        showToolWindow(ToolWindow.HOST_LIBRARY, true);
    }

    @FXML
    private void toggleQuickConnect() {
        showToolWindow(ToolWindow.QUICK_CONNECT, true);
    }

    @FXML
    private void focusHostLibrary() {
        showToolWindow(ToolWindow.HOST_LIBRARY, false);
    }

    @FXML
    private void toggleSnippetManager() {
        showToolWindow(ToolWindow.SNIPPETS, true);
    }

    @FXML
    private void toggleVariableManager() {
        showToolWindow(ToolWindow.VARIABLE_LIBRARY, true);
    }

    @FXML
    private void toggleCredentialManager() {
        showToolWindow(ToolWindow.CREDENTIAL_VAULT, true);
    }

    @FXML
    private void toggleAiAssistant() {
        if (aiPlacement.isDialogOpen()) {
            aiPlacement.closeDialogToDock();
            showToolWindow(ToolWindow.AI_ASSISTANT, false);
            return;
        }
        if (aiPlacement.isWorkspaceOpen()) {
            aiPlacement.workspaceTab().ifPresent(workspaceTabs.getSelectionModel()::select);
            requestAiPromptFocusIfInitialized();
            return;
        }
        showToolWindow(ToolWindow.AI_ASSISTANT, true);
    }

    @FXML
    private void activateAiAssistantPlacement() {
        if (aiPlacement.isWorkspaceOpen()) {
            aiPlacement.workspaceTab().ifPresent(workspaceTabs.getSelectionModel()::select);
            requestAiPromptFocusIfInitialized();
            return;
        }
        warmUpAiAssistant();
        showToolWindow(ToolWindow.AI_ASSISTANT, false);
    }

    @FXML
    private void openAiSidebar() {
        openAuthorizedAiSidebar();
    }

    private void openAuthorizedAiSidebar() {
        warmUpAiAssistant();
        aiPlacement.closeDialogToDock();
        if (aiPlacement.isWorkspaceOpen()) aiPlacement.closeWorkspace();
        showToolWindow(ToolWindow.AI_ASSISTANT, false);
    }

    @FXML
    private void openAiDialog() {
        warmUpAiAssistant();
        aiPlacement.openDialog(rootPane.getScene().getWindow());
        refreshAiHostAssociation();
        requestAiPromptFocusIfInitialized();
    }

    private Window aiInteractionOwner() {
        return aiPlacement != null && aiPlacement.isDialogOpen() && aiAssistantPage.getScene() != null
                ? aiAssistantPage.getScene().getWindow() : rootPane.getScene().getWindow();
    }

    @FXML
    private void openAiWorkspace() {
        openAuthorizedAiWorkspace();
    }

    @FXML
    private void toggleAiWorkspace() {
        if (aiPlacement.closeWorkspaceIfOpen()) return;
        openAiWorkspace();
    }

    private void openAuthorizedAiWorkspace() {
        warmUpAiAssistant();
        aiPlacement.closeDialogToDock();
        Tab selected = selectedWorkspaceTab();
        if (selected != null && !aiPlacement.isWorkspaceTab(selected)
                && !diagnosticLogPlacement.isWorkspaceTab(selected)) lastNonAiWorkspaceTab = selected;
        DockSide side = dockSide(ToolWindow.AI_ASSISTANT, persistedWorkspaceSettings);
        if ((side == DockSide.LEFT && selectedLeftTool == ToolWindow.AI_ASSISTANT) ||
                (side == DockSide.RIGHT && selectedRightTool == ToolWindow.AI_ASSISTANT)) {
            if (side == DockSide.LEFT) setHostSidebarVisible(false);
            else setRightDockVisible(false);
        }
        aiPlacement.openWorkspace();
        installWorkspaceTabContextMenu(aiPlacement.workspaceTab().orElseThrow());
        updateWorkspaceVisibility();
        refreshAiHostAssociation();
        requestAiPromptFocusIfInitialized();
    }

    @FXML
    private void toggleAiPlacement() {
        if (!aiPlacement.isWorkspaceOpen()) {
            openAiWorkspace();
            return;
        }
        aiPlacement.closeWorkspace();
        showToolWindow(ToolWindow.AI_ASSISTANT, false);
    }

    private void initializeAiAssistantControls() {
        ProgressIndicator warmupIndicator = new ProgressIndicator();
        warmupIndicator.setMaxSize(28.0, 28.0);
        aiConversationHost.getChildren().setAll(warmupIndicator);
        aiProviderField.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(ModelProviderConfig item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });
        aiProviderField.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(ModelProviderConfig item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });
        aiProviderField.valueProperty().addListener((ignored, previous, selected) -> {
            if (!updatingAiModelSelection) {
                updatingAiModelSelection = true;
                aiModelField.setValue(null);
                aiModelField.getEditor().clear();
                updatingAiModelSelection = false;
                pendingAiProviderRestore = "";
                pendingAiModelRestore = "";
            }
            if (updatingAiModelSelection) return;
            if (selected == null) {
                aiModelField.getItems().clear();
                persistAiModelSelection(Optional.empty(), Optional.empty());
            } else {
                discoverAiModels(selected);
            }
            updateAiInferenceControls();
            updateAiActionState();
        });
        aiModelField.valueProperty().addListener((ignored, previous, selected) -> {
            if (!updatingAiModelSelection) {
                ModelProviderConfig provider = aiProviderField.getValue();
                if (provider != null && selected != null && !selected.isBlank()) {
                    persistAiModelSelection(Optional.of(provider.id()), Optional.of(selected.strip()));
                }
            }
            updateAiInferenceControls();
            updateAiActionState();
        });
        aiModelField.getEditor().textProperty().addListener((ignored, previous, selected) -> {
            updateAiInferenceControls();
            updateAiActionState();
        });
        aiReasoningField.setCellFactory(ignored -> reasoningEffortCell());
        aiReasoningField.setButtonCell(reasoningEffortCell());
        aiReasoningField.valueProperty().addListener((ignored, previous, selected) -> rememberAiInferenceOptions());
        aiReasoningSummaryField.selectedProperty().addListener(
                (ignored, previous, selected) -> rememberAiInferenceOptions());
        aiPromptInputMethodState = InputMethodEnterState.track(aiPromptField);
        // Enter is handled after the TextArea and input method finish processing it. The tracker
        // associates an observed input-method commit with the physical key that follows it.
        aiPromptField.setOnKeyReleased(event -> handleAiPromptKeyReleased(
                event, aiPromptInputMethodState.inputMethodOwnsKeyRelease(event.getCode()),
                () -> aiPromptField.replaceSelection("\n"),
                this::sendAiMessage));
        aiPromptField.textProperty().addListener((ignored, previous, selected) -> updateAiActionState());
        aiChatLoading = true;
        updateAiActionState();
        if (automationGateway != null) {
            automationGateway.setApprovalHandler(this::approveAutomationOperation);
            automationGateway.setHostKeyPrompt(this::confirmHostKey);
            automationGateway.setHostActivationHandler(this::activateAutomationHost);
            automationGateway.setAuditHandler(event -> Platform.runLater(() -> appendAutomationAudit(event)));
            automationGateway.setLibraryChangedHandler(() -> Platform.runLater(() -> {
                refreshCatalog();
                refreshSnippets();
            }));
            automationGateway.setUiOperationHandler(new com.ynpe.fastremote.domain.automation.AutomationUiOperationHandler() {
                @Override public CompletionStage<Boolean> openLocalTerminal() {
                    return openAutomationLocalTerminal(false);
                }
                @Override public CompletionStage<Boolean> openLocalTerminal(boolean reuseExistingTab) {
                    return openAutomationLocalTerminal(reuseExistingTab);
                }
                @Override public CompletionStage<Integer> closeWorkspaceTabs(
                        com.ynpe.fastremote.domain.automation.AutomationWorkspaceScope scope) {
                    return closeAutomationWorkspaceTabs(scope);
                }
                @Override public CompletionStage<List<com.ynpe.fastremote.domain.automation.AutomationWorkspaceItem>>
                        listWorkspace() {
                    return listAutomationWorkspace();
                }
                @Override public CompletionStage<List<com.ynpe.fastremote.domain.automation.AutomationActiveHost>> listActiveHosts() {
                    return AiHostRuntimeContext.onFx(() -> AiHostRuntimeContext.active(openSessions.values(),
                            MainWindowController::automationWorkspaceId));
                }
                @Override public CompletionStage<com.ynpe.fastremote.domain.automation.AutomationConversationContext> conversationContext() {
                    return AiHostRuntimeContext.onFx(() -> new com.ynpe.fastremote.domain.automation.AutomationConversationContext(
                            Optional.ofNullable(activeAiChatId), aiAssociatedHost));
                }
                @Override public CompletionStage<Optional<SavedHost>> resolveActiveHost(com.ynpe.fastremote.domain.catalog.HostId id) {
                    return AiHostRuntimeContext.onFx(() -> AiHostRuntimeContext.resolve(openSessions.values(), id));
                }
                @Override public CompletionStage<String> readWorkspaceText(String workspaceId) {
                    return readAutomationWorkspaceText(workspaceId);
                }
                @Override public CompletionStage<Boolean> writeWorkspaceText(String workspaceId, String content) {
                    return writeAutomationWorkspaceText(workspaceId, content);
                }
                @Override public CompletionStage<com.ynpe.fastremote.domain.terminal.TerminalOutput> readTerminal(
                        String workspaceId, String generation, long after, int maximum, boolean screen) {
                    return observationOnFx(() -> {
                        var terminal = findTerminalObservation(workspaceId);
                        if (!generation.isEmpty() && !generation.equals(terminal.generation()))
                            throw new IllegalArgumentException("Terminal connection generation changed; request a new snapshot");
                        if (generation.isEmpty() && after != 0) throw new IllegalArgumentException("Incremental reads require generation");
                        return terminal.read(after, maximum, screen);
                    });
                }
                @Override public CompletionStage<List<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt>>
                        listWorkspaceOperations(String workspaceId) {
                    return observationOnFx(() -> {
                        optionalTerminalObservation(workspaceId).ifPresent(terminal -> {
                            for (String id : terminal.operationIds()) observations.terminal(workspaceId, terminal, id);
                        });
                        return observations.list(workspaceId);
                    });
                }
                @Override public CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationSnapshot>
                        readWorkspaceOperation(String operationId, long after, int maximum, int waitMillis) {
                    return observationOnFx(() -> observations.read(operationId, after, maximum));
                }
                @Override public CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt>
                        startTerminalCommand(com.ynpe.fastremote.domain.catalog.HostId id, String command, boolean reuse) {
                    return awaitAutomationWorkspace(id, WorkspaceType.TERMINAL, reuse).thenCompose(session ->
                        observationOnFx(() -> {
                            var terminal = session.terminal.observation();
                            TerminalHandle handle = session.terminal;
                            String workspace = automationWorkspaceId(session.tabs.get(WorkspaceType.TERMINAL));
                            return ObservedTerminalCommands.execute(terminal, command, session.automationOwned && !reuse,
                                    handle::shellFamily, handle::sentBytes,
                                    () -> automationCommandSystemFamily(id, session))
                                    .thenCompose(run -> observationOnFx(() -> observations.terminal(
                                            workspace, terminal, run)));
                        }).thenCompose(java.util.function.Function.identity()));
                }
                @Override public CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt>
                        startLocalTerminalCommand(String command, boolean reuse) {
                    return selectedOrOpenAutomationLocalTerminal(reuse).thenCompose(target ->
                        observationOnFx(() -> {
                            var terminal = target.handle().observation();
                            return ObservedTerminalCommands.execute(terminal, command, false,
                                    target.handle()::shellFamily, target.handle()::sentBytes,
                                    () -> ServerOverview.SystemFamily.UNKNOWN)
                                    .thenCompose(run -> observationOnFx(() -> observations.terminal(
                                            automationWorkspaceId(target.tab()), terminal, run)));
                        }).thenCompose(java.util.function.Function.identity()));
                }
                @Override public CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt>
                        startSftpUpload(com.ynpe.fastremote.domain.catalog.HostId id, List<Path> sources, String directory,
                                com.ynpe.fastremote.domain.transfer.TransferConflictChoice choice, long maximum, boolean reuse) {
                    return startObservedSftpUpload(id, sources, directory, choice, maximum, reuse);
                }
                @Override public CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt>
                        startSftpDownload(com.ynpe.fastremote.domain.catalog.HostId id, String source, Path directory,
                                long maximum, boolean reuse) {
                    return startObservedSftpDownload(id, source, directory, maximum, reuse);
                }
                @Override public CompletionStage<String> readWorkspaceLog(String workspaceId) {
                    return readAutomationWorkspaceLog(workspaceId);
                }
                @Override public CompletionStage<Boolean> showToolWindow(
                        com.ynpe.fastremote.domain.automation.AutomationToolWindow window) {
                    return showAutomationToolWindow(window);
                }
                @Override public CompletionStage<Boolean> showImportDialog(
                        com.ynpe.fastremote.domain.automation.AutomationLibrary library) {
                    return showAutomationLibraryDialog(library, true);
                }
                @Override public CompletionStage<Boolean> showExportDialog(
                        com.ynpe.fastremote.domain.automation.AutomationLibrary library) {
                    return showAutomationLibraryDialog(library, false);
                }
                @Override public CompletionStage<Boolean> runTerminalCommand(
                        com.ynpe.fastremote.domain.catalog.HostId id, String command) {
                    return runAutomationTerminalCommand(id, command, false);
                }
                @Override public CompletionStage<Boolean> runTerminalCommand(
                        com.ynpe.fastremote.domain.catalog.HostId id, String command, boolean reuseExistingTab) {
                    return runAutomationTerminalCommand(id, command, reuseExistingTab);
                }
                @Override public CompletionStage<Boolean> openTerminal(
                        com.ynpe.fastremote.domain.catalog.HostId id, boolean reuseExistingTab) {
                    return openAutomationTerminal(id, reuseExistingTab);
                }
                @Override public CompletionStage<Boolean> openSftp(
                        com.ynpe.fastremote.domain.catalog.HostId id) {
                    return openAutomationSftp(id, false);
                }
                @Override public CompletionStage<Boolean> openSftp(
                        com.ynpe.fastremote.domain.catalog.HostId id, boolean reuseExistingTab) {
                    return openAutomationSftp(id, reuseExistingTab);
                }
                @Override public CompletionStage<Boolean> changeSftpRemoteDirectory(
                        com.ynpe.fastremote.domain.catalog.HostId id, String directory) {
                    return changeAutomationSftpRemoteDirectory(id, directory);
                }
                @Override public CompletionStage<Boolean> changeSftpLocalDirectory(
                        com.ynpe.fastremote.domain.catalog.HostId id, Path directory) {
                    return changeAutomationSftpLocalDirectory(id, directory);
                }
                @Override public CompletionStage<Boolean> runLocalTerminalCommand(String command) {
                    return runAutomationLocalTerminalCommand(command, false);
                }
                @Override public CompletionStage<Boolean> runLocalTerminalCommand(
                        String command, boolean reuseExistingTab) {
                    return runAutomationLocalTerminalCommand(command, reuseExistingTab);
                }
                @Override public CompletionStage<Boolean> uploadInSftp(
                        com.ynpe.fastremote.domain.catalog.HostId id, List<Path> sources, String directory) {
                    return uploadAutomationInSftp(id, sources, directory);
                }
                @Override public CompletionStage<Boolean> uploadInSftp(
                        com.ynpe.fastremote.domain.catalog.HostId id, List<Path> sources, String directory,
                        com.ynpe.fastremote.domain.transfer.TransferConflictChoice conflictChoice) {
                    return uploadAutomationInSftp(id, sources, directory, conflictChoice);
                }
                @Override public CompletionStage<Boolean> uploadInSftp(
                        com.ynpe.fastremote.domain.catalog.HostId id, List<Path> sources, String directory,
                        com.ynpe.fastremote.domain.transfer.TransferConflictChoice conflictChoice,
                        long maximumBytes) {
                    return uploadAutomationInSftp(id, sources, directory, conflictChoice, maximumBytes, false);
                }
                @Override public CompletionStage<Boolean> uploadInSftp(
                        com.ynpe.fastremote.domain.catalog.HostId id, List<Path> sources, String directory,
                        com.ynpe.fastremote.domain.transfer.TransferConflictChoice conflictChoice,
                        long maximumBytes, boolean reuseExistingTab) {
                    return uploadAutomationInSftp(id, sources, directory, conflictChoice, maximumBytes,
                            reuseExistingTab);
                }
                @Override public CompletionStage<Boolean> downloadInSftp(
                        com.ynpe.fastremote.domain.catalog.HostId id, String source, Path directory) {
                    return downloadAutomationInSftp(id, source, directory);
                }
                @Override public CompletionStage<Boolean> downloadInSftp(
                        com.ynpe.fastremote.domain.catalog.HostId id, String source, Path directory,
                        long maximumBytes) {
                    return downloadAutomationInSftp(id, source, directory, maximumBytes, false);
                }
                @Override public CompletionStage<Boolean> downloadInSftp(
                        com.ynpe.fastremote.domain.catalog.HostId id, String source, Path directory,
                        long maximumBytes, boolean reuseExistingTab) {
                    return downloadAutomationInSftp(id, source, directory, maximumBytes, reuseExistingTab);
                }
                @Override public CompletionStage<Boolean> createRemoteDirectory(
                        com.ynpe.fastremote.domain.catalog.HostId id, String path) {
                    return mutateAutomationRemote(id, com.ynpe.fastremote.i18n.Texts.text("legacy.java.c04a58cd98f154cc") + path,
                            files -> files.createDirectory(path));
                }
                @Override public CompletionStage<Boolean> createRemoteFile(
                        com.ynpe.fastremote.domain.catalog.HostId id, String path) {
                    return mutateAutomationRemote(id, com.ynpe.fastremote.i18n.Texts.text("legacy.java.0c4f22b5c92a6ad8") + path,
                            files -> files.createFile(path));
                }
                @Override public CompletionStage<Boolean> renameRemote(
                        com.ynpe.fastremote.domain.catalog.HostId id, String source, String target) {
                    return mutateAutomationRemote(id, com.ynpe.fastremote.i18n.Texts.text("legacy.java.4413833101e1914a") + source + " → " + target,
                            files -> files.rename(source, target));
                }
                @Override public CompletionStage<Boolean> deleteRemote(
                        com.ynpe.fastremote.domain.catalog.HostId id, String path, boolean recursive) {
                    return mutateAutomationRemote(id, com.ynpe.fastremote.i18n.Texts.text("legacy.java.373efab4b0cb5405") + path,
                            files -> files.readAttributes(path).thenCompose(attributes -> {
                                if (attributes.directory() && !recursive) {
                                    return CompletableFuture.failedFuture(new IllegalArgumentException(
                                            "Remote directory deletion requires recursive=true"));
                                }
                                return files.delete(path);
                            }));
                }
                @Override public CompletionStage<Boolean> setRemotePermissions(
                        com.ynpe.fastremote.domain.catalog.HostId id, String path, int permissions) {
                    return mutateAutomationRemote(id, com.ynpe.fastremote.i18n.Texts.text("legacy.java.da412e8f1b09c8df") + path + " → "
                                    + String.format(java.util.Locale.ROOT, "%03o", permissions),
                            files -> files.setPermissions(path, permissions));
                }
                @Override public CompletionStage<Boolean> writeRemoteText(
                        com.ynpe.fastremote.domain.catalog.HostId id, String path, byte[] content, boolean overwrite) {
                    byte[] copy = content.clone();
                    return mutateAutomationRemote(id, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d9432ff9c36edb1a") + path,
                            files -> overwrite
                                    ? files.readAttributes(path).thenCompose(attributes -> {
                                        if (attributes.directory()) {
                                            return CompletableFuture.failedFuture(new IllegalArgumentException(
                                                    "Remote target is not a regular file"));
                                        }
                                        return files.writeFileAtomic(path, copy, true);
                                    })
                                    : files.writeFileAtomic(path, copy, false))
                            .whenComplete((ignored, error) -> java.util.Arrays.fill(copy, (byte) 0));
                }
            });
        }
    }

    /** Creates the native conversation view after the main window has become visible. */
    public void warmUpAiAssistant() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::warmUpAiAssistant);
            return;
        }
        if (controllerClosed.get() || aiAssistantWarmupStarted) return;
        aiAssistantWarmupStarted = true;
        try {
            aiChatView = new AiChatView(message -> setStatus(message, StatusTone.IDLE), imageId -> {
                com.ynpe.fastremote.ai.AiImageInput image = aiImagesById.get(imageId);
                if (image != null) aiAssistantPageController.showImage(image);
            });
            aiConversationHost.getChildren().setAll(aiChatView);
            loadInitialAiChat();
            loadAiAssistantSettings();
        } catch (RuntimeException | LinkageError failure) {
            aiChatLoading = false;
            updateAiActionState();
            setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4c8e3924eedca496")
                    + messageFor(failure, com.ynpe.fastremote.i18n.Texts.text("legacy.java.78b5cf18e062a54c")),
                    StatusTone.ERROR);
            System.getLogger(MainWindowController.class.getName()).log(
                    System.Logger.Level.ERROR, "Unable to initialize AI native chat view", failure);
        }
    }

    private void loadAiAssistantSettings() {
        aiAssistantWorkflow.loadSettings().whenComplete((settings, error) -> Platform.runLater(() -> {
            if (controllerClosed.get()) return;
            if (error != null) {
                appendAiConversation(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5b50d7c4b5950dc5"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.4c8e3924eedca496") + messageFor(unwrap(error), com.ynpe.fastremote.i18n.Texts.text("legacy.java.78b5cf18e062a54c")),
                        AiMessageKind.SYSTEM);
            } else {
                applyAiSettings(settings);
            }
            refreshAiHostAssociation();
            updateAiActionState();
        }));
    }

    static boolean handleAiPromptKeyReleased(
            KeyEvent event, boolean inputMethodComposing, Runnable insertLineBreak, Runnable send) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(insertLineBreak, "insertLineBreak");
        Objects.requireNonNull(send, "send");
        if (inputMethodComposing || event.getCode() != KeyCode.ENTER) return false;
        event.consume();
        if (event.isShiftDown()) insertLineBreak.run();
        else send.run();
        return true;
    }

    private void requestAiPromptFocusIfInitialized() {
        if (aiPromptFocusRestorer != null) {
            Platform.runLater(aiPromptFocusRestorer::request);
        }
    }

    private void loadInitialAiChat() {
        aiChatLoading = true;
        updateAiActionState();
        aiAssistantWorkflow.listChatSessions().whenComplete((sessions, error) -> Platform.runLater(() -> {
            if (error != null) {
                aiChatLoading = false;
                renderEmptyAiChat();
                appendAiConversation(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5b50d7c4b5950dc5"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.0062dc433b726bb5") + messageFor(unwrap(error), com.ynpe.fastremote.i18n.Texts.text("legacy.java.743145bd4f971188")),
                        AiMessageKind.SYSTEM);
                updateAiActionState();
                return;
            }
            aiChatSessions.clear();
            aiChatSessions.addAll(sessions);
            if (sessions.isEmpty()) {
                aiChatLoading = false;
                createAiChat();
            }
            else activateAiChat(sessions.getFirst().id());
        }));
    }

    @FXML
    private void createAiChat() {
        if (aiChatLoading) return;
        AiChatSession active = aiChatSessions.stream()
                .filter(session -> session.id().equals(activeAiChatId)).findFirst().orElse(null);
        if (active != null && active.messageCount() == 0 && !aiRequestRunning()) {
            showAiChat(active, List.of());
            return;
        }
        aiChatLoading = true;
        updateAiActionState();
        aiAssistantWorkflow.createChatSession().whenComplete((session, error) -> Platform.runLater(() -> {
            if (error != null) {
                aiChatLoading = false;
                appendAiConversation(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5b50d7c4b5950dc5"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.2e125126ea2b60c7") + messageFor(unwrap(error), com.ynpe.fastremote.i18n.Texts.text("legacy.java.743145bd4f971188")),
                        AiMessageKind.SYSTEM);
                updateAiActionState();
                return;
            }
            aiChatSessions.removeIf(existing -> existing.id().equals(session.id()));
            aiChatSessions.addFirst(session);
            showAiChat(session, List.of());
        }));
    }

    private void activateAiChat(UUID sessionId) {
        saveAiDraft();
        long generation = ++aiChatLoadGeneration;
        aiChatLoading = true;
        updateAiActionState();
        aiAssistantWorkflow.loadChatSession(sessionId).whenComplete((transcript, error) -> Platform.runLater(() -> {
            if (generation != aiChatLoadGeneration || controllerClosed.get()) return;
            if (error != null) {
                aiChatLoading = false;
                appendAiConversation(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5b50d7c4b5950dc5"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.64f51aeb353eb085") + messageFor(unwrap(error), com.ynpe.fastremote.i18n.Texts.text("legacy.java.978f188b993de88b")),
                        AiMessageKind.SYSTEM);
                refreshAiChatSessions();
                updateAiActionState();
                return;
            }
            showAiChat(transcript.session(), transcript.messages(), transcript.pending());
        }));
    }

    private void showAiChat(AiChatSession session, List<AiChatMessage> messages) {
        showAiChat(session, messages, List.of());
    }
    private void showAiChat(AiChatSession session, List<AiChatMessage> messages,
            List<com.ynpe.fastremote.domain.ai.AiPendingPrompt> pending) {
        saveAiDraft();
        activeAiChatId = session.id();
        if (aiRuntimes.isEmpty() && aiChatView != null) aiChatView.close();
        AiConversationRuntime runtime = aiRuntimes.computeIfAbsent(session.id(), id -> new AiConversationRuntime(id,
                message -> setStatus(message, StatusTone.IDLE), aiAssistantPageController::showImage));
        if (aiRuntimes.size() > 16) {
            var iterator = aiRuntimes.values().iterator();
            while (iterator.hasNext() && aiRuntimes.size() > 16) {
                var candidate = iterator.next();
                if (!candidate.running && !candidate.queueSaving && candidate.pending.isEmpty() && !candidate.id.equals(activeAiChatId) && candidate.draft.isBlank() && candidate.draftImages.isEmpty()) {
                    candidate.view.close(); iterator.remove();
                }
            }
        }
        aiConversation = runtime.conversation;
        aiImagesById = runtime.images;
        aiTranscript = runtime.transcript;
        aiChatView = runtime.view;
        aiConversationHost.getChildren().setAll(aiChatView);
        aiPromptField.setText(runtime.draft);
        aiAssistantPageController.restoreImages(runtime.draftImages);
        aiAssociatedHost = session.associatedHost();
        refreshAiHostAssociation();
        aiAssistantPageController.sessionList().refresh();
        restoreAiModelSelection(session);
        aiChatTitleLabel.setText(aiSessionDisplayTitle(session));
        if (runtime.initialized) {
            aiChatLoading = false;
            updateAiInferenceControls();
            updateAiActionState();
            return;
        }
        runtime.initialized = true;
        runtime.pending.restore(pending);
        aiConversation.clear();
        aiImagesById.clear();
        aiTranscript.clear();
        aiChatView.clearConversation();
        appendAiConversation(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5b50d7c4b5950dc5"), aiWelcomeMessage(), AiMessageKind.SYSTEM);
        String displayGroupId = "";
        for (AiChatMessage message : messages) {
            if (message.role() == AiChatMessage.Role.USER) displayGroupId = "stream:" + UUID.randomUUID();
            boolean reasoningRecord = message.role() == AiChatMessage.Role.TOOL
                    && message.content().startsWith(AI_REASONING_RECORD_PREFIX);
            AiMessageKind kind = switch (message.role()) {
                case USER -> AiMessageKind.USER;
                case ASSISTANT -> AiMessageKind.ASSISTANT;
                case SYSTEM -> AiMessageKind.SYSTEM;
                case TOOL -> reasoningRecord ? AiMessageKind.REASONING : AiMessageKind.TOOL;
            };
            if (message.role() == AiChatMessage.Role.USER || message.role() == AiChatMessage.Role.ASSISTANT) {
                List<com.ynpe.fastremote.ai.AiImageInput> historyImages = message.images().stream()
                        .map(image -> new com.ynpe.fastremote.ai.AiImageInput(image.mediaType(), image.data(),
                                image.width(), image.height(), image.thumbnailData())).toList();
                aiConversation.add(new com.ynpe.fastremote.ai.AiConversationMessage(
                        message.role() == AiChatMessage.Role.USER
                                ? com.ynpe.fastremote.ai.AiConversationMessage.Role.USER
                                : com.ynpe.fastremote.ai.AiConversationMessage.Role.ASSISTANT,
                        message.content(), historyImages));
            }
            String displayContent = reasoningRecord
                    ? message.content().substring(AI_REASONING_RECORD_PREFIX.length()) : message.content();
            List<com.ynpe.fastremote.ai.AiImageInput> displayImages = message.images().stream()
                    .map(image -> new com.ynpe.fastremote.ai.AiImageInput(image.mediaType(), image.data(),
                            image.width(), image.height(), image.thumbnailData())).toList();
            appendAiConversation(aiSpeaker(kind), displayContent, kind, true,
                    message.role() == AiChatMessage.Role.USER ? "" : displayGroupId, displayImages);
        }
        aiChatLoading = false;
        updateAiInferenceControls();
        updateAiActionState();
        requestAiPromptFocusIfInitialized();
    }

    private void renderEmptyAiChat() {
        activeAiChatId = null;
        aiAssociatedHost = Optional.empty();
        refreshAiHostAssociation();
        aiChatTitleLabel.setText(com.ynpe.fastremote.i18n.Texts.text("ai.chat.newSession"));
        aiConversation.clear();
        aiImagesById.clear();
        aiTranscript.clear();
        aiChatView.clearConversation();
        appendAiConversation(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5b50d7c4b5950dc5"), aiWelcomeMessage(), AiMessageKind.SYSTEM);
        updateAiInferenceControls();
    }

    private static String aiWelcomeMessage() {
        return com.ynpe.fastremote.i18n.Texts.text("legacy.java.cea9de7fbf637b27");
    }

    private static String aiSpeaker(AiMessageKind kind) {
        return switch (kind) {
            case USER -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.a0c7716669b5ded0");
            case ASSISTANT -> "AI";
            case SYSTEM -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.5b50d7c4b5950dc5");
            case TOOL -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.ed31fbb483ee1b0a");
            case REASONING -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.132411ba41da7354");
        };
    }

    static String aiSessionDisplayTitle(AiChatSession session) {
        Objects.requireNonNull(session, "session");
        if (session.messageCount() != 0 || !isGeneratedAiSessionTitle(session.title())) return session.title();
        return com.ynpe.fastremote.i18n.Texts.text("ai.chat.newSession");
    }

    private static boolean isGeneratedAiSessionTitle(String title) {
        if (title == null) return false;
        for (com.ynpe.fastremote.domain.settings.InterfaceLanguage language
                : com.ynpe.fastremote.domain.settings.InterfaceLanguage.publishedLanguages()) {
            com.ynpe.fastremote.i18n.Localizer candidate = com.ynpe.fastremote.i18n.Localizer.forLanguage(language);
            if (title.equals(candidate.text("ai.chat.newSession"))
                    || title.equals(candidate.text("legacy.fxml.c57c30bc05f744ab"))) return true;
        }
        return false;
    }

    static Runnable holdAiHistoryEditorWidth(Region titleHost, Region editor) {
        double titleSlotWidth = Math.max(0.0, titleHost.getWidth());
        double previousMinWidth = titleHost.getMinWidth();
        double previousPrefWidth = titleHost.getPrefWidth();
        double previousMaxWidth = titleHost.getMaxWidth();
        if (titleSlotWidth > 0.0) {
            titleHost.setMinWidth(titleSlotWidth);
            titleHost.setPrefWidth(titleSlotWidth);
            titleHost.setMaxWidth(titleSlotWidth);
            editor.setMinWidth(titleSlotWidth);
            editor.setPrefWidth(titleSlotWidth);
            editor.setMaxWidth(titleSlotWidth);
        } else {
            editor.setMaxWidth(Double.MAX_VALUE);
        }
        return () -> {
            titleHost.setMinWidth(previousMinWidth);
            titleHost.setPrefWidth(previousPrefWidth);
            titleHost.setMaxWidth(previousMaxWidth);
        };
    }

    private void refreshAiChatSessions() {
        aiAssistantWorkflow.listChatSessions().whenComplete((sessions, error) -> Platform.runLater(() -> {
            if (error == null) {
                aiChatSessions.clear();
                aiChatSessions.addAll(sessions);
                aiAssistantPageController.updateSessions(aiChatSessions, activeAiChatId);
            }
        }));
    }

    static String aiChatTitle(String prompt) {
        String normalized = Objects.requireNonNullElse(prompt, "").strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) return com.ynpe.fastremote.i18n.Texts.text("legacy.java.c57c30bc05f744ab");
        int end = normalized.offsetByCodePoints(0, Math.min(32, normalized.codePointCount(0, normalized.length())));
        return normalized.substring(0, end) + (end < normalized.length() ? "…" : "");
    }

    static Optional<String> aiRenamedTitle(String currentTitle, String candidate) {
        String normalized = Objects.requireNonNullElse(candidate, "").strip();
        return normalized.isEmpty() || normalized.equals(Objects.requireNonNull(currentTitle, "currentTitle"))
                ? Optional.empty() : Optional.of(normalized);
    }

    static String aiHistoryAge(Instant updatedAt, Instant now) {
        long minutes = Math.max(0, java.time.Duration.between(updatedAt, now).toMinutes());
        var localizer = com.ynpe.fastremote.i18n.Texts.localizer();
        if (minutes < 1) return localizer.text("ai.history.justNow");
        if (minutes < 60) return localizer.text("ai.history.minutes", localizer.formatNumber(minutes));
        long hours = minutes / 60;
        if (hours < 24) return localizer.text("ai.history.hours", localizer.formatNumber(hours));
        long days = hours / 24;
        if (days < 30) return localizer.text("ai.history.days", localizer.formatNumber(days));
        long months = days / 30;
        if (months < 12) return localizer.text("ai.history.months", localizer.formatNumber(months));
        return localizer.text("ai.history.years", localizer.formatNumber(days / 365));
    }

    private void applyAiSettings(AiSettings settings) {
        aiSettings = Objects.requireNonNull(settings, "settings");
        aiModelsByProvider.clear();
        aiProvidersWithoutModels.clear();
        aiCapabilitiesByProvider.clear();
        updatingAiModelSelection = true;
        aiProviderField.getItems().setAll(settings.providers());
        updatingAiModelSelection = false;
        AiChatSession active = aiChatSessions.stream()
                .filter(session -> session.id().equals(activeAiChatId)).findFirst().orElse(null);
        restoreAiModelSelection(active);
        refreshAiHostAssociation();
        updateAiActionState();
    }

    private void discoverAiModels(ModelProviderConfig provider) {
        List<String> cached = aiModelsByProvider.get(provider.id());
        if (cached != null) {
            applyDiscoveredModels(provider, cached);
            return;
        }
        aiModelField.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d61b27abfe74268f"));
        aiAssistantWorkflow.discoverModelCapabilities(provider.id()).whenComplete((capabilities, error) -> Platform.runLater(() -> {
            if (aiProviderField.getValue() == null || !aiProviderField.getValue().id().equals(provider.id())) return;
            if (error != null) {
                String restored = provider.id().equals(pendingAiProviderRestore) ? pendingAiModelRestore : "";
                List<String> fallback = !restored.isBlank() ? List.of(restored)
                        : provider.model().isBlank() ? List.of() : List.of(provider.model());
                applyDiscoveredModels(provider, fallback);
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.13ec9a891806d8c2") + messageFor(unwrap(error), com.ynpe.fastremote.i18n.Texts.text("legacy.java.2eeabbe3ea2649e2")), StatusTone.ERROR);
            } else {
                List<AiModelCapabilities> safeCapabilities = capabilities.stream()
                        .filter(value -> value != null && !value.model().isBlank())
                        .collect(java.util.stream.Collectors.toMap(AiModelCapabilities::model, value -> value,
                                (first, ignored) -> first, java.util.LinkedHashMap::new))
                        .values().stream().toList();
                List<String> safe = safeCapabilities.stream().map(AiModelCapabilities::model).toList();
                aiModelsByProvider.put(provider.id(), safe);
                aiCapabilitiesByProvider.put(provider.id(), safeCapabilities.stream().collect(
                        java.util.stream.Collectors.toUnmodifiableMap(AiModelCapabilities::model, value -> value)));
                applyDiscoveredModels(provider, safe);
            }
        }));
    }

    private void applyDiscoveredModels(ModelProviderConfig provider, List<String> models) {
        String previous = selectedAiModel();
        boolean restoring = provider.id().equals(pendingAiProviderRestore);
        String selected = AiModelSelection.availableModel(restoring ? pendingAiModelRestore : previous,
                provider.model(), models);
        updatingAiModelSelection = true;
        aiModelField.getItems().setAll(models);
        aiModelField.setValue(selected.isBlank() ? null : selected);
        if (selected.isBlank()) aiModelField.getEditor().clear();
        updatingAiModelSelection = false;
        pendingAiProviderRestore = "";
        pendingAiModelRestore = "";
        if (!selected.isBlank()) {
            aiProvidersWithoutModels.remove(provider.id());
            persistAiModelSelection(Optional.of(provider.id()), Optional.of(selected));
        } else {
            aiProvidersWithoutModels.add(provider.id());
            var fallback = aiSettings.providers().stream()
                    .filter(candidate -> !aiProvidersWithoutModels.contains(candidate.id())).findFirst();
            if (fallback.isPresent()) {
                aiProviderField.setValue(fallback.orElseThrow());
                return;
            }
        }
        aiModelField.setPromptText(models.isEmpty() ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.9fdf0d442537e3a4") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.1afed6a81a2512d2"));
        updateAiInferenceControls();
        updateAiActionState();
    }

    private void restoreAiModelSelection(AiChatSession session) {
        var selection = AiModelSelection.restore(aiSettings, session, aiChatSessions);
        updatingAiModelSelection = true;
        aiModelField.setValue(null);
        aiModelField.getEditor().clear();
        aiModelField.getItems().clear();
        aiProviderField.setValue(selection.map(AiModelSelection.Selection::provider).orElse(null));
        updatingAiModelSelection = false;
        pendingAiProviderRestore = selection.map(value -> value.provider().id()).orElse("");
        pendingAiModelRestore = selection.map(AiModelSelection.Selection::model).orElse("");
        selection.ifPresent(value -> discoverAiModels(value.provider()));
    }

    private void persistAiModelSelection(Optional<String> providerId, Optional<String> model) {
        UUID sessionId = activeAiChatId;
        if (updatingAiModelSelection || sessionId == null) return;
        if (aiChatSessions.stream().anyMatch(session -> session.id().equals(sessionId)
                && session.providerId().equals(providerId) && session.model().equals(model))) return;
        aiAssistantWorkflow.updateChatModelSelection(sessionId, providerId, model)
                .whenComplete((session, error) -> Platform.runLater(() -> {
                    if (error == null) rememberAiChat(session);
                    else setStatus(messageFor(unwrap(error), "Unable to save AI model selection"), StatusTone.ERROR);
                }));
    }

    private String selectedAiModel() {
        String editor = aiModelField.isEditable() ? aiModelField.getEditor().getText() : null;
        String value = editor == null || editor.isBlank() ? aiModelField.getValue() : editor;
        return value == null ? "" : value.strip();
    }

    private static ListCell<AiReasoningEffort> reasoningEffortCell() {
        return new ListCell<>() {
            @Override protected void updateItem(AiReasoningEffort item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : switch (item) {
                    case NONE -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.484d55613910eb8c");
                    case MINIMAL -> com.ynpe.fastremote.i18n.Texts.text("ai.reasoning.minimal");
                    case LOW -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.aa9e366f68d3d097");
                    case MEDIUM -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.a567bdaa11367f26");
                    case HIGH -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.b1c27820fec23edb");
                    case XHIGH -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.392d0dceb45998d3");
                    case MAX -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.9730c15f3c1963a1");
                    case ULTRA -> com.ynpe.fastremote.i18n.Texts.text("ai.reasoning.ultra");
                });
            }
        };
    }

    static List<AiReasoningEffort> reasoningEfforts(ModelProviderConfig provider, String model) {
        if (provider == null) return List.of();
        if (provider.type() == ModelProviderType.OLLAMA) {
            return List.of(AiReasoningEffort.NONE, AiReasoningEffort.LOW, AiReasoningEffort.MEDIUM,
                    AiReasoningEffort.HIGH, AiReasoningEffort.MAX);
        }
        if (provider.type() != ModelProviderType.OPENAI_RESPONSES
                && provider.type() != ModelProviderType.CHATGPT_CODEX) return List.of();
        String id = Objects.requireNonNullElse(model, "").strip().toLowerCase(Locale.ROOT);
        if (id.startsWith("gpt-5.6")) return List.of(AiReasoningEffort.NONE, AiReasoningEffort.LOW,
                AiReasoningEffort.MEDIUM, AiReasoningEffort.HIGH, AiReasoningEffort.XHIGH, AiReasoningEffort.MAX);
        if (id.startsWith("gpt-5.5") || id.startsWith("gpt-5.4") || id.startsWith("gpt-5.2")) {
            return List.of(AiReasoningEffort.NONE, AiReasoningEffort.LOW, AiReasoningEffort.MEDIUM,
                    AiReasoningEffort.HIGH, AiReasoningEffort.XHIGH);
        }
        if (id.startsWith("gpt-5.1")) return List.of(AiReasoningEffort.NONE, AiReasoningEffort.LOW,
                AiReasoningEffort.MEDIUM, AiReasoningEffort.HIGH);
        if (id.equals("gpt-5") || id.startsWith("gpt-5-") || id.matches("o[134](?:$|-.*)")) {
            return List.of(AiReasoningEffort.LOW, AiReasoningEffort.MEDIUM, AiReasoningEffort.HIGH);
        }
        return List.of();
    }

    static boolean supportsReasoningDisplay(ModelProviderConfig provider, String model) {
        return provider != null && (provider.type() == ModelProviderType.OLLAMA
                || !reasoningEfforts(provider, model).isEmpty());
    }

    private void updateAiInferenceControls() {
        if (aiReasoningField == null || aiReasoningSummaryField == null) return;
        ModelProviderConfig provider = aiProviderField.getValue();
        String model = selectedAiModel();
        AiModelCapabilities capabilities = provider == null ? null
                : aiCapabilitiesByProvider.getOrDefault(provider.id(), Map.of()).get(model);
        aiAssistantPageController.setImagesAllowed(supportsImageInput(provider, capabilities));
        List<AiReasoningEffort> efforts = capabilities != null
                && !capabilities.supportedReasoningEfforts().isEmpty()
                        ? capabilities.supportedReasoningEfforts() : reasoningEfforts(provider, model);
        boolean supportsEffort = !efforts.isEmpty();
        boolean chatGpt = provider != null && provider.type() == ModelProviderType.CHATGPT_CODEX;
        boolean supportsDisplay = chatGpt || supportsReasoningDisplay(provider, model);
        updatingAiInferenceControls = true;
        try {
            aiReasoningField.setManaged(supportsEffort);
            aiReasoningField.setVisible(supportsEffort);
            aiReasoningSummaryField.setManaged(supportsDisplay);
            aiReasoningSummaryField.setVisible(supportsDisplay);
            if (!supportsDisplay) {
                aiReasoningField.getItems().clear();
                aiReasoningField.setValue(null);
                aiReasoningSummaryField.setSelected(false);
                return;
            }
            AiReasoningEffort suggested = capabilities == null ? null
                    : capabilities.defaultReasoningEffort().orElse(null);
            AiInferenceOptions defaults = supportsEffort
                    ? new AiInferenceOptions(Optional.ofNullable(suggested != null ? suggested
                            : efforts.contains(AiReasoningEffort.MEDIUM)
                                    ? AiReasoningEffort.MEDIUM : efforts.getFirst()), chatGpt)
                    : new AiInferenceOptions(Optional.empty(), chatGpt);
            AiInferenceOptions remembered = activeAiChatId == null
                    ? defaults : aiInferenceByChat.getOrDefault(activeAiChatId, defaults);
            if (!supportsEffort) {
                aiReasoningField.getItems().clear();
                aiReasoningField.setValue(null);
                aiReasoningSummaryField.setSelected(remembered.showReasoningSummary());
                return;
            }
            aiReasoningField.getItems().setAll(efforts);
            AiReasoningEffort selected = remembered.reasoningEffort().filter(efforts::contains)
                    .orElse(efforts.contains(AiReasoningEffort.MEDIUM)
                            ? AiReasoningEffort.MEDIUM : efforts.getFirst());
            aiReasoningField.setValue(selected);
            aiReasoningSummaryField.setSelected(remembered.showReasoningSummary());
        } finally {
            updatingAiInferenceControls = false;
        }
    }

    static boolean supportsImageInput(ModelProviderConfig provider, AiModelCapabilities capabilities) {
        if (provider == null) return false;
        if (provider.type() == ModelProviderType.OPENAI_RESPONSES) return true;
        return provider.type() == ModelProviderType.CHATGPT_CODEX && capabilities != null
                && capabilities.inputModalities().contains(AiInputModality.IMAGE);
    }

    private void rememberAiInferenceOptions() {
        if (updatingAiInferenceControls || activeAiChatId == null || !aiReasoningSummaryField.isVisible()) return;
        aiInferenceByChat.put(activeAiChatId, new AiInferenceOptions(
                aiReasoningField.isVisible() ? Optional.ofNullable(aiReasoningField.getValue()) : Optional.empty(),
                aiReasoningSummaryField.isSelected()));
    }

    private AiInferenceOptions currentAiInferenceOptions() {
        if (!aiReasoningSummaryField.isVisible()) return AiInferenceOptions.DEFAULT;
        if (aiReasoningField.isVisible() && aiReasoningField.getValue() == null) return AiInferenceOptions.DEFAULT;
        AiInferenceOptions options = new AiInferenceOptions(
                aiReasoningField.isVisible() ? Optional.of(aiReasoningField.getValue()) : Optional.empty(),
                aiReasoningSummaryField.isSelected());
        if (activeAiChatId != null) aiInferenceByChat.put(activeAiChatId, options);
        return options;
    }

    private void refreshAiHostAssociation() {
        if (aiAssistantPageController == null) return;
        var quick = openSessions.values().stream().distinct()
                .filter(session -> !session.closed.get() && session.catalogHostId == null)
                .map(AiHostAssociationSelector::quick).distinct().toList();
        aiAssistantPageController.updateAssociatedHosts(catalog.hosts(), catalog.groups(), quick, aiAssociatedHost);
        aiWorkspaceContext = aiAssociatedHost.map(host -> AiWorkspaceContext.remote(
                host.displayName() + " (" + host.endpoint() + ")",
                Optional.of(host.targetId()))).orElseGet(AiWorkspaceContext::none);
    }

    private void saveAiAssociatedHost(Optional<com.ynpe.fastremote.domain.ai.AiHostAssociation> host) {
        if (activeAiChatId == null || aiRequestRunning() || aiAssociationSaving) return;
        UUID chatId = activeAiChatId;
        aiAssociationSaving = true;
        updateAiActionState();
        aiAssistantWorkflow.updateAssociatedHost(chatId, host).whenComplete((session, error) -> Platform.runLater(() -> {
            aiAssociationSaving = false;
            if (error == null) {
                rememberAiChat(session);
                if (chatId.equals(activeAiChatId)) aiAssociatedHost = session.associatedHost();
            } else setStatus(messageFor(unwrap(error), "Unable to save associated host"), StatusTone.ERROR);
            refreshAiHostAssociation();
            updateAiActionState();
        }));
    }

    static Optional<com.ynpe.fastremote.domain.catalog.HostId> resolveAiTarget(
            OpenSession selected, List<SavedHost> savedSessions) {
        if (selected == null) return Optional.empty();
        if (selected.catalogHostId != null) return Optional.of(selected.catalogHostId);
        if (selected.bookmarkKey != null && !selected.bookmarkKey.isBlank()) {
            try {
                com.ynpe.fastremote.domain.catalog.HostId bookmarkId =
                        com.ynpe.fastremote.domain.catalog.HostId.parse(selected.bookmarkKey);
                if (savedSessions.stream().anyMatch(saved -> saved.id().equals(bookmarkId))) {
                    selected.catalogHostId = bookmarkId;
                    return Optional.of(bookmarkId);
                }
            } catch (IllegalArgumentException ignored) {
                // Quick connections may use a non-host bookmark key; continue with profile matching.
            }
        }
        List<SavedHost> matches = Objects.requireNonNull(savedSessions, "savedSessions").stream()
                .filter(saved -> saved.profile().equals(selected.profile))
                .filter(saved -> selected.name.isBlank() || saved.name().equals(selected.name))
                .limit(2).toList();
        if (matches.size() != 1) return Optional.empty();
        selected.catalogHostId = matches.getFirst().id();
        selected.bookmarkKey = matches.getFirst().id().toString();
        return Optional.of(selected.catalogHostId);
    }

    @FXML
    private void sendOrInterruptAiMessage() {
        sendAiMessage();
    }

    private AiConversationRuntime currentAiRuntime() { return aiRuntimes.get(activeAiChatId); }
    private boolean aiRequestRunning() {
        AiConversationRuntime runtime = currentAiRuntime();
        return runtime != null && runtime.running;
    }
    private void saveAiDraft() {
        AiConversationRuntime runtime = currentAiRuntime();
        if (runtime != null) {
            runtime.draft = aiPromptField.getText(); runtime.draftImages = aiAssistantPageController.images();
        }
    }

    private void interruptAiRequest() {
        AiConversationRuntime runtime = currentAiRuntime();
        if (runtime == null || !runtime.running || runtime.interruptInProgress) return;
        runtime.interruptRequested = true;
        runtime.interruptInProgress = true;
        runtime.pending.paused = true;
        updateAiActionState();
        setStatus(com.ynpe.fastremote.i18n.Texts.text("ai.chat.stopping"), StatusTone.WORKING);
        aiAssistantWorkflow.cancelRequest(runtime.id).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (error == null || !runtime.running) return;
            runtime.interruptInProgress = false;
            updateAiActionState();
            setStatus(messageFor(unwrap(error), com.ynpe.fastremote.i18n.Texts.text("ai.chat.stopFailed")), StatusTone.ERROR);
        }));
    }

    @FXML
    private void sendAiMessage() { sendAiMessage(false); }

    private void sendAiMessage(boolean steer) {
        if (aiChatLoading || aiLicenseCheckRunning) return;
        UUID licensedChatId = activeAiChatId;
        aiLicenseCheckRunning = true;
        updateAiActionState();
        licenseAccess.permits(com.ynpe.fastremote.domain.license.LicenseCapability.AI)
                .whenComplete((permitted, error) -> Platform.runLater(() -> {
                    aiLicenseCheckRunning = false;
                    if (controllerClosed.get()) return;
                    if (!Objects.equals(licensedChatId, activeAiChatId)) { updateAiActionState(); return; }
                    if (error != null || !Boolean.TRUE.equals(permitted)) {
                        updateAiActionState();
                        requestAiPromptFocusIfInitialized();
                        setStatus(localizer.text("license.actionRequired", "ai"), StatusTone.ERROR);
                        showLicenseRequiredPrompt(com.ynpe.fastremote.domain.license.LicenseCapability.AI);
                        return;
                    }
                    sendAuthorizedAiMessage(steer);
                }));
    }

    private void sendAuthorizedAiMessage(boolean steer) {
        if (aiChatLoading) return;
        refreshAiHostAssociation();
        ModelProviderConfig provider = aiProviderField.getValue();
        String model = selectedAiModel();
        String prompt = aiPromptField.getText().strip();
        if (provider == null) {
            setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1dce39df9fde9c02"), StatusTone.ERROR);
            configureAiAssistant();
            return;
        }
        if (model.isEmpty()) {
            setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7684a4db98abfef0"), StatusTone.ERROR);
            return;
        }
        if (prompt.isEmpty() && !aiAssistantPageController.hasImages()) return;
        AiModelCapabilities capabilities = aiCapabilitiesByProvider
                .getOrDefault(provider.id(), Map.of()).get(model);
        if (aiAssistantPageController.hasImages() && !supportsImageInput(provider, capabilities)) {
            setStatus(com.ynpe.fastremote.i18n.Texts.text("ai.image.unsupportedModel"), StatusTone.ERROR);
            return;
        }
        if (activeAiChatId == null) {
            setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.57afc62e6336c169"), StatusTone.ERROR);
            return;
        }
        if (currentAiRuntime().queueSaving) return;
        if (aiRequestRunning()) {
            var runtime = currentAiRuntime();
            if (runtime.pending.size() >= 8) {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7abc59d9f1ff3d37"), StatusTone.ERROR); return;
            }
            var entry = runtime.pending.add(prompt, aiAssistantPageController.takeImages());
            aiPromptField.clear();
            saveAiQueue(runtime, () -> {
                if (steer) steerAiPrompt(runtime, entry.id());
                else if (!runtime.running) sendNextPendingAiPrompt(runtime);
            });
            requestAiPromptFocusIfInitialized(); return;
        }
        List<com.ynpe.fastremote.ai.AiImageInput> images = aiAssistantPageController.takeImages();
        aiPromptField.clear();
        startAiRequest(provider, model, prompt, images, false);
    }

    private void configureAiRuntime(AiConversationRuntime runtime) {
        runtime.provider = aiProviderField.getValue(); runtime.model = selectedAiModel();
        runtime.inference = currentAiInferenceOptions();
        runtime.context = aiWorkspaceContext.withConversation(runtime.id, aiAssociatedHost);
    }

    private void saveAiQueue(AiConversationRuntime runtime, Runnable saved) {
        runtime.queueWrites++; runtime.queueSaving = true; updateAiActionState();
        runtime.pending.save(aiAssistantWorkflow, runtime.id).whenComplete((ignored, error) -> Platform.runLater(() -> {
            runtime.queueSaving = --runtime.queueWrites > 0;
            if (controllerClosed.get()) { runtime.pending.paused = true; return; }
            if (error != null) {
                runtime.pending.paused = true;
                setStatus(com.ynpe.fastremote.i18n.Texts.text("ai.queue.saveFailed"), StatusTone.ERROR);
            } else saved.run();
            updateAiActionState();
        }));
    }

    private void steerAiPrompt(AiConversationRuntime runtime, UUID id) {
        if (!runtime.running || runtime.interruptRequested) { runtime.pending.paused = true; updateAiActionState(); return; }
        var entry = runtime.pending.find(id).orElseThrow();
        if (entry.state() != AiPromptQueue.State.QUEUED) return;
        UUID runId = runtime.runId;
        runtime.pending.state(id, AiPromptQueue.State.WAITING);
        saveAiQueue(runtime, () -> {
            if (!runtime.running || runtime.interruptRequested || !Objects.equals(runId, runtime.runId)) {
                runtime.pending.state(id, AiPromptQueue.State.QUEUED); runtime.pending.paused = true;
                saveAiQueue(runtime, () -> {}); return;
            }
            aiAssistantWorkflow.steer(runtime.id, new com.ynpe.fastremote.ai.AiSteeringInput(id, entry.text(), entry.inputs()))
                    .whenComplete((result, error) -> Platform.runLater(() -> {
                        if (!Objects.equals(runId, runtime.runId)) return;
                        if (runtime.pending.find(id).isEmpty() || runtime.pending.find(id).orElseThrow().state() == AiPromptQueue.State.ACCEPTED) return;
                        if (error != null) {
                            runtime.pending.state(id, AiPromptQueue.State.UNCERTAIN); runtime.pending.paused = true;
                        } else if (result == AiAssistantWorkflow.SteeringResult.NOT_RUNNING) {
                            runtime.pending.state(id, AiPromptQueue.State.QUEUED); runtime.pending.paused = true;
                        }
                        saveAiQueue(runtime, () -> {});
                    }));
        });
    }

    private void startAiRequest(ModelProviderConfig provider, String model, String prompt,
            List<com.ynpe.fastremote.ai.AiImageInput> images, boolean alreadyDisplayed) {
        var runtime = currentAiRuntime(); configureAiRuntime(runtime);
        runtime.provider = provider; runtime.model = model;
        startAiRequest(runtime, prompt, images, alreadyDisplayed);
    }

    private void startAiRequest(AiConversationRuntime runtime, String prompt,
            List<com.ynpe.fastremote.ai.AiImageInput> images, boolean alreadyDisplayed) {
        UUID requestChatId = runtime.id;
        ModelProviderConfig provider = runtime.provider;
        String model = runtime.model;
        AiInferenceOptions inference = runtime.inference;
        List<com.ynpe.fastremote.ai.AiConversationMessage> history = List.copyOf(runtime.conversation);
        runtime.conversation.add(new com.ynpe.fastremote.ai.AiConversationMessage(
                com.ynpe.fastremote.ai.AiConversationMessage.Role.USER, prompt, images));
        appendAiConversation(runtime, aiSpeaker(AiMessageKind.USER), prompt, AiMessageKind.USER, true, "", images);
        runtime.interruptRequested = false; runtime.interruptInProgress = false; runtime.running = true;
        runtime.runId = UUID.randomUUID();
        UUID runId = runtime.runId;
        updateAiActionState();
        AiRunPresentation streaming = new AiRunPresentation((kind, text, complete, group, attachments) -> {
            var type = AiMessageKind.valueOf(kind);
            var handle = appendAiConversation(runtime, aiSpeaker(type), text, type, complete, group, attachments);
            return handle::setText;
        }, input -> {
            if (!Objects.equals(runtime.runId, runId)) return;
            runtime.pending.find(input.id()).ifPresent(entry -> runtime.pending.state(input.id(), AiPromptQueue.State.ACCEPTED));
            updateAiActionState();
        });
        UUID starting = runtime.startingPromptId;
        runtime.startingPromptId = null;
        aiAssistantWorkflow.appendChatMessage(requestChatId, AiChatMessage.Role.USER, prompt,
                        storedAiImages(images), Optional.empty())
                .thenCompose(saved -> {
                    Platform.runLater(() -> {
                        rememberAiChat(saved);
                        if (starting != null) runtime.pending.find(starting).ifPresent(entry -> runtime.pending.state(starting, AiPromptQueue.State.ACCEPTED));
                    });
                    if (runtime.interruptRequested) return CompletableFuture.failedFuture(new CancellationException());
                    var request = aiAssistantWorkflow.sendStreaming(requestChatId, provider.id(), model, history, prompt, images,
                            runtime.context, inference, streaming);
                    // Stop may have raced the registration between the preceding check and sendStreaming.
                    if (runtime.interruptRequested || controllerClosed.get()) aiAssistantWorkflow.cancelRequest(requestChatId);
                    return request;
                }).whenComplete((reply, error) -> Platform.runLater(() -> {
                    if (!Objects.equals(runId, runtime.runId)) return;
                    boolean interrupted = runtime.interruptRequested || error != null && unwrap(error) instanceof CancellationException;
                    var entries = streaming.finish(reply);
                    for (var entry : entries) if (entry.kind().equals("USER") || entry.kind().equals("ASSISTANT"))
                        runtime.conversation.add(new com.ynpe.fastremote.ai.AiConversationMessage(
                                entry.kind().equals("USER") ? com.ynpe.fastremote.ai.AiConversationMessage.Role.USER
                                        : com.ynpe.fastremote.ai.AiConversationMessage.Role.ASSISTANT, entry.text(), entry.images()));
                    // Persist even on cancellation/failure, in precisely the displayed order.
                    CompletionStage<AiChatSession> stored = CompletableFuture.completedFuture(null);
                    for (var entry : entries) {
                        boolean thought = entry.kind().equals("REASONING");
                        var role = thought ? AiChatMessage.Role.TOOL : AiChatMessage.Role.valueOf(entry.kind());
                        String text = (thought ? AI_REASONING_RECORD_PREFIX : "") + entry.text();
                        stored = stored.thenCompose(ignored -> aiAssistantWorkflow.appendChatMessage(requestChatId, role,
                                text, storedAiImages(entry.images()), Optional.empty()));
                    }
                    if (error != null && !interrupted) {
                        String fallback = com.ynpe.fastremote.i18n.Texts.text("legacy.java.ab15836064961694");
                        String message = StatusMessagePresentation.aiFailure(messageFor(unwrap(error), fallback), fallback);
                        appendAiConversation(runtime, aiSpeaker(AiMessageKind.SYSTEM), message, AiMessageKind.SYSTEM, true, streaming.groupId());
                        stored = stored.thenCompose(ignored -> aiAssistantWorkflow.appendChatMessage(requestChatId,
                                AiChatMessage.Role.SYSTEM, message, Optional.empty()));
                    }
                    if (error == null && reply != null) stored = stored.thenCompose(session -> session == null
                            ? CompletableFuture.completedFuture(null)
                            : generatedAiChatTitle(session, provider.id(), model, prompt, reply.text()).thenCompose(title ->
                                title.isPresent() ? aiAssistantWorkflow.renameChatSession(requestChatId, title.orElseThrow())
                                        : CompletableFuture.completedFuture(session)));
                    stored.whenComplete((session, saveError) -> Platform.runLater(() -> {
                        runtime.running = false; runtime.interruptInProgress = false; runtime.interruptRequested = false;
                        if (session != null) rememberAiChat(session);
                        if (saveError == null) runtime.pending.finish(error != null || interrupted);
                        else runtime.pending.persistenceFailed();
                        setStatus(com.ynpe.fastremote.i18n.Texts.text(saveError != null ? "ai.queue.saveFailed"
                                : interrupted ? "ai.chat.stopped" : error != null ? "ai.queue.paused" : "legacy.java.d4b0cc16e44b95ed"),
                                error != null || saveError != null ? StatusTone.ERROR : StatusTone.IDLE);
                        saveAiQueue(runtime, () -> {
                            if (error == null && saveError == null && !interrupted) sendNextPendingAiPrompt(runtime);
                        });
                        refreshAiChatSessions(); updateAiActionState();
                        if (runtime.id.equals(activeAiChatId)) requestAiPromptFocusIfInitialized();
                    }));
                }));
    }

    private static List<AiChatImage> storedAiImages(List<com.ynpe.fastremote.ai.AiImageInput> images) {
        return images.stream().map(image -> new AiChatImage(UUID.randomUUID(), image.mediaType(), image.data(),
                image.width(), image.height(), image.thumbnailData())).toList();
    }

    private void sendNextPendingAiPrompt(AiConversationRuntime runtime) {
        if (controllerClosed.get() || runtime.running || runtime.queueSaving || runtime.provider == null || runtime.model == null || runtime.model.isBlank()) return;
        runtime.pending.next().ifPresent(entry -> {
            runtime.pending.state(entry.id(), AiPromptQueue.State.WAITING);
            saveAiQueue(runtime, () -> {
                runtime.startingPromptId = entry.id();
                startAiRequest(runtime, entry.text(), entry.inputs(), false);
            });
        });
    }

    static boolean supportsPendingAiPrompts(ModelProviderConfig provider) { return provider != null; }

    private CompletionStage<Optional<String>> generatedAiChatTitle(
            AiChatSession session, String providerId, String model, String prompt, String answer) {
        if (!isGeneratedAiSessionTitle(session.title())) return CompletableFuture.completedFuture(Optional.empty());
        return aiAssistantWorkflow.generateChatTitle(session.id(), providerId, model, prompt, answer)
                .handle((title, error) -> Optional.of(usableAiChatTitle(title, prompt)));
    }

    static String usableAiChatTitle(String title, String prompt) {
        String normalized = Objects.requireNonNullElse(title, "").strip();
        return normalized.isEmpty() || normalized.length() > 120 || isGeneratedAiSessionTitle(normalized)
                ? aiChatTitle(prompt) : normalized;
    }

    private void rememberAiChat(AiChatSession session) {
        aiChatSessions.removeIf(existing -> existing.id().equals(session.id()));
        aiChatSessions.addFirst(session);
        aiChatSessions.sort(java.util.Comparator.comparing(AiChatSession::pinned).reversed()
                .thenComparing(AiChatSession::updatedAt, java.util.Comparator.reverseOrder()));
        if (session.id().equals(activeAiChatId)) aiChatTitleLabel.setText(session.title());
        aiAssistantPageController.updateSessions(aiChatSessions, activeAiChatId);
    }

    private AiMessageHandle appendAiConversation(String speaker, String text, AiMessageKind kind) {
        return appendAiConversation(speaker, text, kind, true, "");
    }

    private AiMessageHandle appendAiConversation(
            String speaker, String text, AiMessageKind kind, boolean complete) {
        return appendAiConversation(speaker, text, kind, complete, "");
    }

    private AiMessageHandle appendAiConversation(
            String speaker, String text, AiMessageKind kind, boolean complete, String groupId) {
        return appendAiConversation(speaker, text, kind, complete, groupId, List.of());
    }

    private AiMessageHandle appendAiConversation(
            String speaker, String text, AiMessageKind kind, boolean complete, String groupId,
            List<com.ynpe.fastremote.ai.AiImageInput> images) {
        return appendAiConversation(currentAiRuntime(), speaker, text, kind, complete, groupId, images);
    }

    private AiMessageHandle appendAiConversation(AiConversationRuntime runtime, String speaker, String text,
            AiMessageKind kind, boolean complete, String groupId) {
        return appendAiConversation(runtime, speaker, text, kind, complete, groupId, List.of());
    }

    private AiMessageHandle appendAiConversation(AiConversationRuntime runtime, String speaker, String text,
            AiMessageKind kind, boolean complete, String groupId, List<com.ynpe.fastremote.ai.AiImageInput> images) {
        String safeText = Objects.requireNonNullElse(text, "");
        String messageId = UUID.randomUUID().toString();
        List<AiChatView.ImageAttachment> chatImages = List.copyOf(images).stream().map(image -> {
            String imageId = UUID.randomUUID().toString();
            (runtime == null ? aiImagesById : runtime.images).put(imageId, image);
            return new AiChatView.ImageAttachment(imageId, image.thumbnailData(),
                    com.ynpe.fastremote.i18n.Texts.text("ai.image.original"));
        }).toList();
        (runtime == null ? aiChatView : runtime.view).appendMessage(new AiChatView.Message(
                messageId, kind.name(), speaker, safeText, complete, groupId, chatImages));
        AiTranscriptEntry transcriptEntry = new AiTranscriptEntry(speaker, safeText);
        List<AiTranscriptEntry> transcript = runtime == null ? aiTranscript : runtime.transcript;
        transcript.add(transcriptEntry);
        while (transcript.size() > 100) transcript.removeFirst();
        return new AiMessageHandle(runtime, messageId, transcriptEntry);
    }

    private final class AiMessageHandle {
        private final AiConversationRuntime runtime;
        private final String messageId;
        private AiTranscriptEntry transcriptEntry;

        private AiMessageHandle(AiConversationRuntime runtime, String messageId, AiTranscriptEntry transcriptEntry) {
            this.runtime = runtime;
            this.messageId = messageId;
            this.transcriptEntry = transcriptEntry;
        }

        private void setText(String text, boolean complete) {
            String safeText = Objects.requireNonNullElse(text, "");
            (runtime == null ? aiChatView : runtime.view).updateMessage(messageId, safeText, complete);
            List<AiTranscriptEntry> transcript = runtime == null ? aiTranscript : runtime.transcript;
            for (int index = 0; index < transcript.size(); index++) {
                if (transcript.get(index) != transcriptEntry) continue;
                transcriptEntry = new AiTranscriptEntry(transcriptEntry.speaker(), safeText);
                transcript.set(index, transcriptEntry);
                break;
            }
        }
    }

    private void copyAiText(String text, String status) {
        ClipboardContent content = new ClipboardContent();
        content.putString(Objects.requireNonNullElse(text, ""));
        Clipboard.getSystemClipboard().setContent(content);
        setStatus(status, StatusTone.IDLE);
    }

    static String aiTranscriptText(List<AiTranscriptEntry> entries) {
        return entries.stream()
                .map(entry -> entry.speaker() + "：\n" + entry.text())
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }

    private void updateAiActionState() {
        refreshAiHostAssociation();
        ModelProviderConfig provider = aiProviderField.getValue();
        boolean promptUnavailable = aiChatLoading || aiAssociationSaving || activeAiChatId == null;
        boolean unavailable = aiLicenseCheckRunning || promptUnavailable || provider == null
                || selectedAiModel().isEmpty() || (aiPromptField.getText().isBlank() && !aiAssistantPageController.hasImages())
                || (currentAiRuntime() != null && (currentAiRuntime().queueSaving || currentAiRuntime().interruptInProgress));
        aiSendButton.setDisable(unavailable);
        aiSendIcon.setIcon(Icon.ARROW_UP);
        String sendActionText = com.ynpe.fastremote.i18n.Texts.text(
                aiRequestRunning() ? "ai.queue.enqueue" : "legacy.fxml.f42b7fa9cb3ab0f4");
        aiSendButton.setAccessibleText(sendActionText); aiSendTooltip.setText(sendActionText);
        aiAssistantPageController.updateExecutionControls(aiRequestRunning(),
                aiRequestRunning() && currentAiRuntime().interruptInProgress, unavailable,
                currentAiRuntime() == null ? null : currentAiRuntime().pending);
        aiPromptField.setDisable(promptUnavailable);
        aiAssistantPageController.setAssociatedHostDisabled(aiRequestRunning() || aiChatLoading || aiAssociationSaving);
        aiProviderField.setDisable(aiRequestRunning());
        aiModelField.setDisable(aiRequestRunning());
        aiNewChatButton.setDisable(aiChatLoading || aiAssociationSaving);
        aiAssistantPageController.setSessionsDisabled(aiChatLoading || aiAssociationSaving);
        aiAssistantPageController.updateSessions(aiChatSessions, activeAiChatId);
    }

    static boolean shouldFocusAiPromptBeforeDisablingSend(
            boolean sendButtonFocused, boolean sendUnavailable, boolean promptUnavailable) {
        return sendButtonFocused && sendUnavailable && !promptUnavailable;
    }

    record AiTranscriptEntry(String speaker, String text) {
        AiTranscriptEntry {
            speaker = Objects.requireNonNullElse(speaker, "");
            text = Objects.requireNonNullElse(text, "");
        }
    }

    private enum AiMessageKind { USER, ASSISTANT, SYSTEM, TOOL, REASONING }

    @FXML
    private void configureAiAssistant() {
        showSettings(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e2eb47024664bec8"));
    }

    private CompletionStage<Boolean> approveAutomationOperation(AutomationApprovalRequest request) {
        if (automationSessionAllowAll.contains(request.source())) {
            return CompletableFuture.completedFuture(true);
        }
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Runnable show = () -> {
            Dialog<ButtonType> dialog = new Dialog<>();
            if (rootPane.getScene() != null) dialog.initOwner(rootPane.getScene().getWindow());
            dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.06dc1c9ca7b34ee3"));
            dialog.setHeaderText(null);
            dialog.setResizable(false);
            DialogSpacing.prepare(dialog);
            dialog.getDialogPane().getStyleClass().add("automation-approval-dialog");
            String source = request.source() == AutomationSource.BUILT_IN_AI
                    ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.c71cb4f8eef0140e") : "MCP";
            Label summary = new Label(request.summary());
            summary.setWrapText(true);
            summary.setMaxWidth(Double.MAX_VALUE);
            summary.getStyleClass().add("automation-approval-summary");
            Label heading = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.06dc1c9ca7b34ee3"));
            heading.getStyleClass().add("automation-approval-heading");
            Label subheading = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d6a301718f8f06fe"));
            subheading.getStyleClass().add("automation-approval-subheading");
            VBox headingText = new VBox(2.0, heading, subheading);
            StackPane emblem = new StackPane(new LucideIconView(Icon.SHIELD_CHECK));
            emblem.getStyleClass().add("automation-approval-emblem");
            HBox hero = new HBox(10.0, emblem, headingText);
            hero.setAlignment(Pos.CENTER_LEFT);

            VBox sourceCard = automationApprovalFact(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a488e93d69cc1296"), source);
            VBox targetCard = automationApprovalFact(com.ynpe.fastremote.i18n.Texts.text("legacy.java.57060c88a36bf3d0"), request.hostName());
            HBox.setHgrow(sourceCard, Priority.ALWAYS);
            HBox.setHgrow(targetCard, Priority.ALWAYS);
            sourceCard.setMaxWidth(Double.MAX_VALUE);
            targetCard.setMaxWidth(Double.MAX_VALUE);
            HBox context = new HBox(8.0, sourceCard, targetCard);
            TextArea detail = new TextArea(request.detail());
            detail.setEditable(false);
            detail.setWrapText(true);
            int detailRows = Math.min(6, Math.max(2, request.detail().split("\\R", -1).length));
            detail.setPrefRowCount(detailRows);
            detail.setMinHeight(50.0);
            detail.setMaxHeight(116.0);
            detail.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e910fce8679ce3bb"));
            detail.getStyleClass().add("automation-approval-detail");
            Label detailTitle = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8b8bcb0829a1b166"));
            detailTitle.getStyleClass().add("automation-approval-section-title");
            VBox detailCard = new VBox(6.0, detailTitle, detail);
            detailCard.getStyleClass().add("automation-approval-detail-card");
            Label sessionNote = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1b380851d03c85e4"));
            sessionNote.setWrapText(true);
            sessionNote.getStyleClass().add("automation-approval-note");
            VBox content = new VBox(10.0, hero, summary, context, detailCard, sessionNote);
            content.getStyleClass().add("automation-approval-content");
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().setPrefWidth(560.0);
            ButtonType allowSession = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d2d945b722ea9aab"), ButtonBar.ButtonData.APPLY);
            ButtonType allowOnce = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4a6d8d490c54d438"), ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, allowSession, allowOnce);
            Button sessionButton = (Button) dialog.getDialogPane().lookupButton(allowSession);
            Button onceButton = (Button) dialog.getDialogPane().lookupButton(allowOnce);
            sessionButton.getStyleClass().add("automation-approval-session-button");
            onceButton.getStyleClass().add("automation-approval-once-button");
            sessionButton.setMinWidth(142.0);
            onceButton.setMinWidth(92.0);
            Optional<ButtonType> selected = dialog.showAndWait();
            if (selected.filter(allowSession::equals).isPresent()) {
                automationSessionAllowAll.add(request.source());
                result.complete(true);
            } else {
                result.complete(selected.filter(allowOnce::equals).isPresent());
            }
        };
        if (Platform.isFxApplicationThread()) show.run(); else Platform.runLater(show);
        return result;
    }

    private CompletionStage<Boolean> activateAutomationHost(
            com.ynpe.fastremote.domain.catalog.HostId hostId) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                OpenSession existing = openSessions.values().stream().distinct()
                        .filter(value -> AiHostRuntimeContext.matches(value, hostId)
                                || hostId.toString().equals(value.bookmarkKey))
                        .findFirst().orElse(null);
                if (existing != null) {
                    Tab tab = Optional.ofNullable(existing.tabs.get(WorkspaceType.TERMINAL))
                            .orElseGet(() -> existing.tabs.values().stream().findFirst().orElse(null));
                    if (tab != null) selectWorkspaceTab(tab);
                    refreshAiHostAssociation();
                    setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.14ddcaae0c24aa71"), StatusTone.IDLE);
                    result.complete(tab != null);
                    return;
                }
                SavedHost saved = catalog.hosts().stream()
                        .filter(value -> value.id().equals(hostId)).findFirst().orElse(null);
                if (saved == null) {
                    result.complete(false);
                    return;
                }
                connectHost(saved, WorkspaceType.TERMINAL);
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6b8fb19640f80240") + saved.name(), StatusTone.WORKING);
                result.complete(true);
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private CompletionStage<Boolean> openAutomationLocalTerminal(boolean reuseExistingTab) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                Tab reusable = reuseExistingTab
                        ? selectedWorkspaceTab()
                        : latestAutomationLocalTerminalTab();
                if (localTerminals.containsKey(reusable)) {
                    if (reuseExistingTab) selectWorkspaceTab(reusable);
                    result.complete(true);
                    return;
                }
                CompletableFuture<AutomationLocalTerminal> opened = new CompletableFuture<>();
                opened.whenComplete((ignored, failure) -> {
                    if (failure == null) result.complete(true);
                    else result.completeExceptionally(failure);
                });
                openLocalTerminal(false, null, opened, true, reuseExistingTab);
            }
            catch (RuntimeException failure) { result.completeExceptionally(failure); }
        });
        return result;
    }

    private CompletionStage<Boolean> openAutomationTerminal(
            com.ynpe.fastremote.domain.catalog.HostId hostId, boolean reuseExistingTab) {
        return awaitAutomationWorkspace(hostId, WorkspaceType.TERMINAL, reuseExistingTab)
                .thenApply(ignored -> true);
    }

    private CompletionStage<Integer> closeAutomationWorkspaceTabs(
            com.ynpe.fastremote.domain.automation.AutomationWorkspaceScope scope) {
        CompletableFuture<Integer> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                List<Tab> matches = workspacePanes.logicalTabsInDisplayOrder().stream().filter(tab -> switch (scope) {
                    case ALL -> true;
                    case LOCAL_TERMINALS -> localTerminals.containsKey(tab);
                    case REMOTE_TERMINALS -> workspaceType(tab) == WorkspaceType.TERMINAL;
                    case SFTP -> workspaceType(tab) == WorkspaceType.SFTP;
                    case ALL_TERMINALS -> localTerminals.containsKey(tab)
                            || workspaceType(tab) == WorkspaceType.TERMINAL;
                }).toList();
                matches.forEach(tab -> {
                    if (workspacePanes.containsPane(tab)) closeWorkspacePane(tab);
                    else closeMainTab(tab);
                });
                result.complete(matches.size());
            } catch (RuntimeException failure) { result.completeExceptionally(failure); }
        });
        return result;
    }

    private CompletionStage<List<com.ynpe.fastremote.domain.automation.AutomationWorkspaceItem>>
            listAutomationWorkspace() {
        CompletableFuture<List<com.ynpe.fastremote.domain.automation.AutomationWorkspaceItem>> result =
                new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                Tab active = selectedWorkspaceTab();
                List<com.ynpe.fastremote.domain.automation.AutomationWorkspaceItem> items = new ArrayList<>();
                if (textEditors != null) items.addAll(textEditors.automationItems(active));
                for (Tab tab : workspacePanes.logicalTabsInDisplayOrder()) {
                    if (textEditors != null && textEditors.owns(tab)) continue;
                    LocalTerminalHandle local = localTerminals.get(tab);
                    if (local != null) {
                        items.add(new com.ynpe.fastremote.domain.automation.AutomationWorkspaceItem(
                                automationWorkspaceId(tab),
                                com.ynpe.fastremote.domain.automation.AutomationWorkspaceKind.LOCAL_TERMINAL,
                                nonBlank(tab.getText(), "Local terminal"), local.workingDirectory().map(Path::toString).orElse(""),
                                tab == active, false, false, false, false));
                        continue;
                    }
                    OpenSession session = openSessions.get(tab);
                    WorkspaceType type = workspaceType(tab);
                    if (session == null || type == null) continue;
                    if (type == WorkspaceType.TERMINAL) {
                        items.add(new com.ynpe.fastremote.domain.automation.AutomationWorkspaceItem(
                                automationWorkspaceId(tab),
                                com.ynpe.fastremote.domain.automation.AutomationWorkspaceKind.REMOTE_TERMINAL,
                                nonBlank(session.name, "Remote terminal"), session.latestInferredWorkingDirectory, tab == active,
                                false, false, false, false));
                    } else {
                        items.add(new com.ynpe.fastremote.domain.automation.AutomationWorkspaceItem(
                                automationWorkspaceId(tab),
                                com.ynpe.fastremote.domain.automation.AutomationWorkspaceKind.SFTP,
                                nonBlank(session.name, "SFTP"), "local=" + session.currentLocalPath + "; remote="
                                        + session.currentRemotePath, tab == active,
                                false, false, false, true));
                    }
                }
                if (diagnosticLogPlacement != null) {
                    boolean activeDiagnostic = diagnosticLogPlacement.isWorkspaceTab(
                            workspaceTabs.getSelectionModel().getSelectedItem());
                    items.add(new com.ynpe.fastremote.domain.automation.AutomationWorkspaceItem(
                            "diagnostic-log", com.ynpe.fastremote.domain.automation.AutomationWorkspaceKind.DIAGNOSTIC_LOG,
                            "Diagnostic log", diagnosticLogPlacement.isWorkspaceOpen() ? "workspace" : "sidebar",
                            activeDiagnostic, false, false, false, true));
                }
                result.complete(List.copyOf(items));
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private CompletionStage<String> readAutomationWorkspaceText(String workspaceId) {
        CompletableFuture<String> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                if (textEditors == null) throw new IllegalArgumentException("Open workspace editor was not found");
                result.complete(textEditors.automationText(workspaceId));
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private CompletionStage<Boolean> writeAutomationWorkspaceText(String workspaceId, String content) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(textEditors != null && textEditors.replaceAutomationText(workspaceId, content));
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private CompletionStage<String> readAutomationWorkspaceLog(String workspaceId) {
        CompletableFuture<String> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                if ("diagnostic-log".equals(workspaceId)) {
                    result.complete(diagnosticTextLog.snapshot());
                    return;
                }
                for (Map.Entry<Tab, OpenSession> entry : openSessions.entrySet()) {
                    if (automationWorkspaceId(entry.getKey()).equals(workspaceId)
                            && workspaceType(entry.getKey()) == WorkspaceType.SFTP) {
                        result.complete(entry.getValue().fileLog.snapshot());
                        return;
                    }
                }
                throw new IllegalArgumentException("Workspace log was not found");
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private static String automationWorkspaceId(Tab tab) {
        return (String) tab.getProperties().computeIfAbsent("fastremote.workspace.observation.id",
                ignored -> "workspace-" + java.util.UUID.randomUUID());
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private WorkspaceType workspaceType(Tab tab) {
        OpenSession session = openSessions.get(tab);
        if (session == null) return null;
        return session.tabs.entrySet().stream().filter(entry -> entry.getValue() == tab)
                .map(java.util.Map.Entry::getKey).findFirst().orElse(null);
    }

    private CompletionStage<Boolean> showAutomationToolWindow(
            com.ynpe.fastremote.domain.automation.AutomationToolWindow requested) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                ToolWindow tool = switch (requested) {
                    case QUICK_CONNECT -> ToolWindow.QUICK_CONNECT;
                    case HOST_LIBRARY -> ToolWindow.HOST_LIBRARY;
                    case COMMAND_LIBRARY -> ToolWindow.SNIPPETS;
                    case VARIABLE_LIBRARY -> ToolWindow.VARIABLE_LIBRARY;
                    case CREDENTIAL_VAULT -> ToolWindow.CREDENTIAL_VAULT;
                    case AI_ASSISTANT -> ToolWindow.AI_ASSISTANT;
                    case DIAGNOSTIC_LOG -> ToolWindow.DIAGNOSTIC_LOG;
                };
                showToolWindow(tool, false); result.complete(true);
            } catch (RuntimeException failure) { result.completeExceptionally(failure); }
        });
        return result;
    }

    private CompletionStage<Boolean> showAutomationLibraryDialog(
            com.ynpe.fastremote.domain.automation.AutomationLibrary library, boolean importing) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(true);
                switch (library) {
                    case HOSTS -> { if (importing) importCatalog(); else exportCatalog(); }
                    case COMMANDS -> { if (importing) importSnippets(); else exportSnippets(); }
                    case VARIABLES -> { if (importing) importVariables(); else exportVariables(); }
                }
            } catch (RuntimeException failure) {
                if (!result.isDone()) result.completeExceptionally(failure);
                else appendDiagnosticLog(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2f3938f190d8637e")
                        + messageFor(failure, com.ynpe.fastremote.i18n.Texts.text("legacy.java.98034d30501159f1")), StatusTone.ERROR);
            }
        });
        return result;
    }

    private CompletionStage<Boolean> runAutomationTerminalCommand(
            com.ynpe.fastremote.domain.catalog.HostId hostId, String command,
            boolean reuseExistingTab) {
        return awaitAutomationWorkspace(hostId, WorkspaceType.TERMINAL, reuseExistingTab).thenCompose(session -> {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            Platform.runLater(() -> {
                Tab tab = session.tabs.get(WorkspaceType.TERMINAL);
                if (reuseExistingTab && tab != null) selectWorkspaceTab(tab);
                ServerOverview.SystemFamily systemFamily = automationCommandSystemFamily(hostId, session);
                String payload = snippetPayload(command, true,
                        commandShellFamily(systemFamily, session.terminal == null
                                ? LocalTerminalHandle.ShellFamily.UNKNOWN : session.terminal.shellFamily()));
                appendDiagnosticLog(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c72de2cda6b1159f")
                        + connectionLogName(session.name, session.profile) + com.ynpe.fastremote.i18n.Texts.text("legacy.java.ab4f3b0660289984")
                        + payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                        StatusTone.WORKING);
                session.terminal.sendInput(payload).whenComplete((ignored, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        result.completeExceptionally(error);
                    } else {
                        if (reuseExistingTab) session.terminal.requestFocus();
                        setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.667e39f86139c7b6"), StatusTone.CONNECTED);
                        result.complete(true);
                    }
                }));
            });
            return result;
        });
    }

    private CompletionStage<Boolean> runAutomationLocalTerminalCommand(
            String command, boolean reuseExistingTab) {
        return selectedOrOpenAutomationLocalTerminal(reuseExistingTab).thenCompose(target -> {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            Platform.runLater(() -> {
                if (reuseExistingTab) selectWorkspaceTab(target.tab());
                String payload = snippetPayload(command, true, target.handle().shellFamily());
                appendDiagnosticLog(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ce365d09e7f8b378")
                        + payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                        StatusTone.WORKING);
                target.handle().sendInput(payload).whenComplete((ignored, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        result.completeExceptionally(error);
                    } else {
                        if (reuseExistingTab) target.handle().requestFocus();
                        setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7928ce2e3b124483"), StatusTone.CONNECTED);
                        result.complete(true);
                    }
                }));
            });
            return result;
        });
    }

    private CompletionStage<AutomationLocalTerminal> selectedOrOpenAutomationLocalTerminal(
            boolean reuseExistingTab) {
        CompletableFuture<AutomationLocalTerminal> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            Tab selected = reuseExistingTab
                    ? selectedWorkspaceTab()
                    : latestAutomationLocalTerminalTab();
            LocalTerminalHandle handle = localTerminals.get(selected);
            if (handle != null) {
                result.complete(new AutomationLocalTerminal(selected, handle));
            } else {
                openLocalTerminal(false, null, result, true, reuseExistingTab);
            }
        });
        return result;
    }

    private Tab latestAutomationLocalTerminalTab() {
        List<Tab> tabs = workspacePanes.logicalTabsInDisplayOrder();
        for (int index = tabs.size() - 1; index >= 0; index--) {
            Tab tab = tabs.get(index);
            if (localTerminals.containsKey(tab) && isAutomationOwnedTab(tab)) return tab;
        }
        return null;
    }

    static ServerOverview.SystemFamily localSystemFamily() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? ServerOverview.SystemFamily.WINDOWS : ServerOverview.SystemFamily.POSIX;
    }

    private ServerOverview.SystemFamily automationCommandSystemFamily(
            com.ynpe.fastremote.domain.catalog.HostId hostId, OpenSession target) {
        return knownSystemFamily(hostId, target, openSessions.values());
    }

    static ServerOverview.SystemFamily knownSystemFamily(
            com.ynpe.fastremote.domain.catalog.HostId hostId, OpenSession target,
            java.util.Collection<OpenSession> sessions) {
        if (target.serverOverview != null
                && target.serverOverview.systemFamily() != ServerOverview.SystemFamily.UNKNOWN) {
            return target.serverOverview.systemFamily();
        }
        return sessions.stream().distinct()
                .filter(session -> session != target)
                .filter(session -> AiHostRuntimeContext.matches(session, hostId)
                        || hostId.toString().equals(session.bookmarkKey))
                .map(session -> session.serverOverview)
                .filter(Objects::nonNull)
                .map(ServerOverview::systemFamily)
                .filter(family -> family != ServerOverview.SystemFamily.UNKNOWN)
                .findFirst().orElse(ServerOverview.SystemFamily.UNKNOWN);
    }

    private CompletionStage<Boolean> openAutomationSftp(
            com.ynpe.fastremote.domain.catalog.HostId hostId, boolean reuseExistingTab) {
        return awaitAutomationWorkspace(hostId, WorkspaceType.SFTP, reuseExistingTab).thenApply(session -> {
            Platform.runLater(() -> {
                latestAutomationSftpTarget = session;
                selectAutomationWorkspace(session, WorkspaceType.SFTP, reuseExistingTab);
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.926188276f64d302"), StatusTone.CONNECTED);
            });
            return true;
        });
    }

    private CompletionStage<Boolean> changeAutomationSftpRemoteDirectory(
            com.ynpe.fastremote.domain.catalog.HostId hostId, String directory) {
        return existingAutomationSftp(hostId).thenCompose(session -> {
            CompletableFuture<Boolean> changed = new CompletableFuture<>();
            Platform.runLater(() -> {
                selectAutomationWorkspace(session, WorkspaceType.SFTP, false);
                loadRemoteDirectory(session, directory, session.remoteTable, session.remotePathField, changed);
            });
            return changed.thenApply(success -> {
                Platform.runLater(() -> setStatus(
                        com.ynpe.fastremote.i18n.Texts.text("legacy.java.3a06d529e7c98f86"), StatusTone.CONNECTED));
                return success;
            });
        });
    }

    private CompletionStage<Boolean> changeAutomationSftpLocalDirectory(
            com.ynpe.fastremote.domain.catalog.HostId hostId, Path directory) {
        return existingAutomationSftp(hostId).thenCompose(session -> {
            CompletableFuture<Boolean> changed = new CompletableFuture<>();
            Platform.runLater(() -> {
                selectAutomationWorkspace(session, WorkspaceType.SFTP, false);
                loadLocalDirectory(session, directory, session.localTable, session.localPathField, changed);
            });
            return changed.thenApply(success -> {
                Platform.runLater(() -> setStatus(
                        com.ynpe.fastremote.i18n.Texts.text("legacy.java.c60e5f4a21213c57"), StatusTone.CONNECTED));
                return success;
            });
        });
    }

    private CompletionStage<OpenSession> existingAutomationSftp(
            com.ynpe.fastremote.domain.catalog.HostId hostId) {
        CompletableFuture<OpenSession> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            OpenSession session = latestAutomationSftpTarget;
            if (!automationWorkspaceReady(session, WorkspaceType.SFTP)
                    || !(hostId.equals(session.catalogHostId)
                            || hostId.toString().equals(session.bookmarkKey))) {
                session = findLatestPassiveAutomationWorkspace(hostId, WorkspaceType.SFTP);
            }
            if (!automationWorkspaceReady(session, WorkspaceType.SFTP)) {
                result.completeExceptionally(new IllegalStateException(
                        "Open the saved host SFTP tab before changing its directory"));
                return;
            }
            result.complete(session);
        });
        return result;
    }

    private static <T> CompletionStage<T> observationOnFx(java.util.concurrent.Callable<T> read) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> { try { result.complete(read.call()); }
            catch (Exception failure) { result.completeExceptionally(failure); } });
        return result;
    }

    private com.ynpe.fastremote.domain.terminal.TerminalObservation findTerminalObservation(String id) {
        return optionalTerminalObservation(id).orElseThrow(() -> new IllegalArgumentException("Terminal workspace not found"));
    }
    private Optional<com.ynpe.fastremote.domain.terminal.TerminalObservation> optionalTerminalObservation(String id) {
        for (var entry : localTerminals.entrySet())
            if (automationWorkspaceId(entry.getKey()).equals(id)) return Optional.of(entry.getValue().observation());
        for (var entry : openSessions.entrySet())
            if (automationWorkspaceId(entry.getKey()).equals(id) && workspaceType(entry.getKey()) == WorkspaceType.TERMINAL
                    && entry.getValue().terminal != null) return Optional.of(entry.getValue().terminal.observation());
        return Optional.empty();
    }

    private CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt> startObservedSftpUpload(
            com.ynpe.fastremote.domain.catalog.HostId id, List<Path> sources, String directory,
            com.ynpe.fastremote.domain.transfer.TransferConflictChoice choice, long maximum, boolean reuse) {
        List<LocalFileEntry> entries;
        try { entries = sources.stream().map(MainWindowController::automationLocalEntry).toList(); }
        catch (RuntimeException error) { return CompletableFuture.failedFuture(error); }
        return awaitAutomationWorkspace(id, WorkspaceType.SFTP, reuse).thenCompose(session -> observationOnFx(() -> {
            selectAutomationWorkspace(session, WorkspaceType.SFTP, reuse);
            var jobs = choice == null ? session.transferQueue.upload(entries, directory, this::resolveAutomationTransferConflict, maximum)
                    : session.transferQueue.upload(entries, directory, choice, maximum);
            revealLatestAutomationTransfer(session);
            return observations.transfers(automationWorkspaceId(session.tabs.get(WorkspaceType.SFTP)),
                    session.observationGeneration, jobs);
        }));
    }

    private CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt> startObservedSftpDownload(
            com.ynpe.fastremote.domain.catalog.HostId id, String source, Path directory, long maximum, boolean reuse) {
        return awaitAutomationWorkspace(id, WorkspaceType.SFTP, reuse).thenCompose(session ->
            session.sftp.remoteFiles().readAttributes(source).thenCompose(attributes -> observationOnFx(() -> {
                if (!attributes.directory() && attributes.size() > maximum) throw new IllegalArgumentException("Transfer size limit exceeded");
                selectAutomationWorkspace(session, WorkspaceType.SFTP, reuse);
                var entry = new RemoteFileEntry(attributes.name(), attributes.path(), attributes.directory(),
                        attributes.symbolicLink(), attributes.size(), attributes.modifiedAt());
                var jobs = session.transferQueue.download(List.of(entry), directory, this::resolveAutomationTransferConflict, maximum);
                revealLatestAutomationTransfer(session);
                return observations.transfers(automationWorkspaceId(session.tabs.get(WorkspaceType.SFTP)),
                        session.observationGeneration, jobs);
            })));
    }

    private CompletionStage<Boolean> uploadAutomationInSftp(
            com.ynpe.fastremote.domain.catalog.HostId hostId, List<Path> sources, String remoteDirectory) {
        return uploadAutomationInSftp(hostId, sources, remoteDirectory, null, Long.MAX_VALUE, false);
    }

    private CompletionStage<Boolean> uploadAutomationInSftp(
            com.ynpe.fastremote.domain.catalog.HostId hostId, List<Path> sources, String remoteDirectory,
            com.ynpe.fastremote.domain.transfer.TransferConflictChoice conflictChoice) {
        return uploadAutomationInSftp(hostId, sources, remoteDirectory, conflictChoice, Long.MAX_VALUE, false);
    }

    private CompletionStage<Boolean> uploadAutomationInSftp(
            com.ynpe.fastremote.domain.catalog.HostId hostId, List<Path> sources, String remoteDirectory,
            com.ynpe.fastremote.domain.transfer.TransferConflictChoice conflictChoice, long maximumBytes,
            boolean reuseExistingTab) {
        List<LocalFileEntry> entries;
        try {
            entries = sources.stream().map(MainWindowController::automationLocalEntry).toList();
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
        return awaitAutomationWorkspace(hostId, WorkspaceType.SFTP, reuseExistingTab).thenApply(session -> {
            CompletableFuture<Boolean> queued = new CompletableFuture<>();
            Platform.runLater(() -> {
                try {
                    selectAutomationWorkspace(session, WorkspaceType.SFTP, reuseExistingTab);
                    if (conflictChoice == null) {
                        session.transferQueue.upload(entries, remoteDirectory, this::resolveAutomationTransferConflict, maximumBytes);
                    } else {
                        session.transferQueue.upload(entries, remoteDirectory, conflictChoice, maximumBytes);
                    }
                    revealLatestAutomationTransfer(session);
                    appendFileLog(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.3758b686ca06fbe5") + entries.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.4c54e5cb422f358e")
                            + remoteDirectory + com.ynpe.fastremote.i18n.Texts.text("legacy.java.a4378ae1b7a4678e")
                            + (conflictChoice == null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.fd5fd3c4567938c7") : conflictChoice.name()));
                    setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.45a0b4de537dd677"), StatusTone.CONNECTED);
                    queued.complete(true);
                } catch (RuntimeException failure) {
                    queued.completeExceptionally(failure);
                }
            });
            return queued;
        }).thenCompose(Function.identity());
    }

    private CompletionStage<Boolean> downloadAutomationInSftp(
            com.ynpe.fastremote.domain.catalog.HostId hostId, String remoteSource, Path localDirectory) {
        return downloadAutomationInSftp(hostId, remoteSource, localDirectory, Long.MAX_VALUE, false);
    }

    private CompletionStage<Boolean> downloadAutomationInSftp(
            com.ynpe.fastremote.domain.catalog.HostId hostId, String remoteSource, Path localDirectory,
            long maximumBytes, boolean reuseExistingTab) {
        return awaitAutomationWorkspace(hostId, WorkspaceType.SFTP, reuseExistingTab).thenCompose(session ->
                session.sftp.remoteFiles().readAttributes(remoteSource).thenCompose(attributes -> {
                    CompletableFuture<Boolean> queued = new CompletableFuture<>();
                    Platform.runLater(() -> {
                        try {
                            selectAutomationWorkspace(session, WorkspaceType.SFTP, reuseExistingTab);
                            if (!attributes.directory() && attributes.size() > maximumBytes) {
                                throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text(
                                        "ai.error.transferTooLarge", maximumBytes / (1024L * 1024L)));
                            }
                            RemoteFileEntry entry = new RemoteFileEntry(attributes.name(), attributes.path(),
                                    attributes.directory(), attributes.symbolicLink(), attributes.size(),
                                    attributes.modifiedAt());
                            session.transferQueue.download(List.of(entry), localDirectory, this::resolveAutomationTransferConflict, maximumBytes);
                            revealLatestAutomationTransfer(session);
                            appendFileLog(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.547ad2aef7dd0ebf") + remoteSource + " → " + localDirectory);
                            setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.349afaa7846c7ddb"), StatusTone.CONNECTED);
                            queued.complete(true);
                        } catch (RuntimeException failure) {
                            queued.completeExceptionally(failure);
                        }
                    });
                    return queued;
                }));
    }

    private CompletionStage<Boolean> mutateAutomationRemote(
            com.ynpe.fastremote.domain.catalog.HostId hostId, String description,
            AutomationRemoteAction action) {
        return awaitAutomationWorkspace(hostId, WorkspaceType.SFTP, false).thenCompose(session -> {
            return observationOnFx(() -> action.run(remoteFiles(session))).thenCompose(Function.identity()).thenApply(ignored -> {
                Platform.runLater(() -> {
                    refreshRemoteDirectoryViews(session, session.currentRemotePath);
                    appendFileLog(session, "AI " + description);
                    setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4d9f939359fbb1da"), StatusTone.CONNECTED);
                });
                return true;
            });
        });
    }

    @FunctionalInterface
    private interface AutomationRemoteAction {
        CompletionStage<Void> run(RemoteFilesHandle files);
    }

    private CompletionStage<OpenSession> awaitAutomationWorkspace(
            com.ynpe.fastremote.domain.catalog.HostId hostId, WorkspaceType type,
            boolean reuseExistingTab) {
        CompletableFuture<OpenSession> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            OpenSession existing = findAutomationWorkspace(hostId, type, !reuseExistingTab);
            if (automationWorkspaceReady(existing, type)) {
                selectAutomationWorkspace(existing, type, reuseExistingTab);
                result.complete(existing);
                return;
            }
            if (existing != null && existing.state == ConnectionState.CONNECTING) {
                if (reuseExistingTab) {
                    result.thenAccept(ready -> Platform.runLater(() ->
                            selectAutomationWorkspace(ready, type, true)));
                }
                pollAutomationWorkspace(hostId, type, existing.pairingId,
                        System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30), result);
                return;
            }
            SavedHost saved = catalog.hosts().stream()
                    .filter(candidate -> candidate.id().equals(hostId)).findFirst().orElse(null);
            if (saved == null) {
                OpenSession quick = openSessions.values().stream().distinct()
                        .filter(value -> value.catalogHostId == null && AiHostRuntimeContext.matches(value, hostId))
                        .filter(value -> value.temporaryCredential != null && value.state == ConnectionState.CONNECTED)
                        .findFirst().orElse(null);
                if (quick == null) {
                    result.completeExceptionally(new IllegalArgumentException("Associated quick connection is not currently open"));
                    return;
                }
                var requestId = UUID.randomUUID();
                TemporaryConnectionCredential credential = quick.temporaryCredential.copy();
                OpenSession pending = null;
                try {
                    pending = createPendingSession(quick.name, quick.profile, type, requestId, null, true, reuseExistingTab);
                    pending.temporaryCredential = credential;
                    startConnection(pending, credential.copySecret(), credential.copyPrivateKey());
                    pollAutomationWorkspace(hostId, type, requestId,
                            System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30), result);
                } catch (RuntimeException failure) {
                    credential.close();
                    if (pending != null) pending.temporaryCredential = null;
                    result.completeExceptionally(failure);
                }
                return;
            }
            java.util.UUID requestId = java.util.UUID.randomUUID();
            connectHost(saved, type, requestId, null, true, reuseExistingTab);
            pollAutomationWorkspace(hostId, type, requestId,
                    System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30), result);
        });
        return result;
    }

    private void pollAutomationWorkspace(com.ynpe.fastremote.domain.catalog.HostId hostId, WorkspaceType type,
            java.util.UUID requestId, long deadline, CompletableFuture<OpenSession> result) {
        if (result.isDone()) return;
        OpenSession session = openSessions.values().stream().distinct()
                .filter(candidate -> requestId.equals(candidate.pairingId))
                .filter(candidate -> AiHostRuntimeContext.matches(candidate, hostId))
                .filter(candidate -> candidate.tabs.containsKey(type))
                .findFirst().orElse(null);
        if (automationWorkspaceReady(session, type)) {
            result.complete(session);
            return;
        }
        if (session != null && session.state == ConnectionState.DISCONNECTED) {
            result.completeExceptionally(new IllegalStateException(session.failureMessage));
            return;
        }
        if (System.nanoTime() >= deadline) {
            result.completeExceptionally(new IllegalStateException("Timed out waiting for the visible workspace"));
            return;
        }
        PauseTransition retry = new PauseTransition(Duration.millis(100));
        retry.setOnFinished(ignored -> pollAutomationWorkspace(hostId, type, requestId, deadline, result));
        retry.play();
    }

    private OpenSession findAutomationWorkspace(
            com.ynpe.fastremote.domain.catalog.HostId hostId, WorkspaceType type,
            boolean automationOnly) {
        Tab selected = selectedWorkspaceTab();
        OpenSession selectedSession = openSessions.get(selected);
        if (selectedSession != null
                && (!automationOnly || selectedSession.automationOwned || selectedSession.catalogHostId == null)
                && !selectedSession.closed.get()
                && selectedSession.state != ConnectionState.DISCONNECTED
                && (AiHostRuntimeContext.matches(selectedSession, hostId)
                        || hostId.toString().equals(selectedSession.bookmarkKey))
                && selectedSession.tabs.get(type) == selected) {
            return selectedSession;
        }
        return openSessions.values().stream().distinct()
                .filter(session -> !automationOnly || session.automationOwned || session.catalogHostId == null)
                .filter(session -> !session.closed.get())
                .filter(session -> session.state != ConnectionState.DISCONNECTED)
                .filter(session -> AiHostRuntimeContext.matches(session, hostId)
                        || hostId.toString().equals(session.bookmarkKey))
                .filter(session -> session.tabs.containsKey(type))
                .findFirst().orElse(null);
    }

    private OpenSession findLatestPassiveAutomationWorkspace(
            com.ynpe.fastremote.domain.catalog.HostId hostId, WorkspaceType type) {
        List<Tab> tabs = workspacePanes.logicalTabsInDisplayOrder();
        for (int index = tabs.size() - 1; index >= 0; index--) {
            OpenSession session = openSessions.get(tabs.get(index));
            if (session != null && session.automationOwned
                    && (hostId.equals(session.catalogHostId)
                            || hostId.toString().equals(session.bookmarkKey))
                    && session.tabs.get(type) == tabs.get(index)) {
                return session;
            }
        }
        return null;
    }

    private static boolean automationWorkspaceReady(OpenSession session, WorkspaceType type) {
        if (session == null || session.state != ConnectionState.CONNECTED) return false;
        return type == WorkspaceType.TERMINAL ? session.terminal != null
                : session.sftp != null && session.transferQueue != null;
    }

    private void selectAutomationWorkspace(
            OpenSession session, WorkspaceType type, boolean activateTab) {
        if (!activateTab) return;
        Tab tab = session.tabs.get(type);
        if (tab != null) selectWorkspaceTab(tab);
        if (type == WorkspaceType.TERMINAL && session.terminal != null) session.terminal.requestFocus();
    }

    private void revealLatestAutomationTransfer(OpenSession session) {
        if (session.transferTable == null || session.transferQueue == null || session.transferQueue.jobs().isEmpty()) return;
        SftpTransferJob latest = session.transferQueue.jobs().getLast();
        session.transferTable.getSelectionModel().select(latest);
        session.transferTable.scrollTo(latest);
    }

    private static LocalFileEntry automationLocalEntry(Path path) {
        try {
            var attributes = java.nio.file.Files.readAttributes(path,
                    java.nio.file.attribute.BasicFileAttributes.class,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS);
            Path name = path.getFileName();
            if (name == null) throw new IllegalArgumentException("Local source has no file name");
            boolean hidden;
            try { hidden = java.nio.file.Files.isHidden(path); }
            catch (java.io.IOException ignored) { hidden = name.toString().startsWith("."); }
            return new LocalFileEntry(name.toString(), path, attributes.isDirectory(), attributes.isSymbolicLink(),
                    attributes.isDirectory() ? 0L : attributes.size(),
                    Optional.ofNullable(attributes.lastModifiedTime()).map(value -> value.toInstant()), hidden);
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException("Local source cannot be read: " + path, failure);
        }
    }

    private static VBox automationApprovalFact(String key, String value) {
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("automation-approval-key");
        Label valueLabel = new Label(value);
        valueLabel.setWrapText(true);
        valueLabel.getStyleClass().add("automation-approval-value");
        VBox fact = new VBox(3.0, keyLabel, valueLabel);
        fact.getStyleClass().add("automation-approval-fact");
        return fact;
    }

    @FXML
    private void toggleDiagnosticLog() {
        if (diagnosticLogPlacement.isWorkspaceOpen()) {
            diagnosticLogPlacement.workspaceTab().ifPresent(workspaceTabs.getSelectionModel()::select);
            return;
        }
        showToolWindow(ToolWindow.DIAGNOSTIC_LOG, true);
    }

    @FXML
    private void activateDiagnosticLogPlacement() {
        if (diagnosticLogPlacement.isWorkspaceOpen()) {
            diagnosticLogPlacement.workspaceTab().ifPresent(workspaceTabs.getSelectionModel()::select);
            return;
        }
        showToolWindow(ToolWindow.DIAGNOSTIC_LOG, false);
    }

    @FXML
    private void openDiagnosticLogWorkspace() {
        Tab selected = selectedWorkspaceTab();
        if (selected != null && !aiPlacement.isWorkspaceTab(selected)
                && !diagnosticLogPlacement.isWorkspaceTab(selected)) lastNonAiWorkspaceTab = selected;
        DockSide side = dockSide(ToolWindow.DIAGNOSTIC_LOG, persistedWorkspaceSettings);
        if ((side == DockSide.LEFT && selectedLeftTool == ToolWindow.DIAGNOSTIC_LOG)
                || (side == DockSide.RIGHT && selectedRightTool == ToolWindow.DIAGNOSTIC_LOG)) {
            if (side == DockSide.LEFT) setHostSidebarVisible(false);
            else setRightDockVisible(false);
        }
        diagnosticLogPlacement.openWorkspace();
        installWorkspaceTabContextMenu(diagnosticLogPlacement.workspaceTab().orElseThrow());
        updateWorkspaceVisibility();
        updateDiagnosticLogPlacementButton();
    }

    @FXML
    private void openDiagnosticLogSidebar() {
        if (diagnosticLogPlacement.isWorkspaceOpen()) diagnosticLogPlacement.closeWorkspace();
        showToolWindow(ToolWindow.DIAGNOSTIC_LOG, false);
        updateDiagnosticLogPlacementButton();
    }

    @FXML
    private void toggleDiagnosticLogWorkspace() {
        if (diagnosticLogPlacement.closeWorkspaceIfOpen()) return;
        openDiagnosticLogWorkspace();
    }

    private void toggleDiagnosticLogPlacement() {
        if (!diagnosticLogPlacement.isWorkspaceOpen()) {
            openDiagnosticLogWorkspace();
            return;
        }
        diagnosticLogPlacement.closeWorkspace();
        showToolWindow(ToolWindow.DIAGNOSTIC_LOG, false);
    }

    private void revealCurrentDiagnosticLogFile() {
        diagnosticLogOutput.ensureCurrentFile().whenComplete((file, error) -> Platform.runLater(() -> {
            if (error != null) {
                setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("diagnostic.log.revealFailed")),
                        StatusTone.ERROR);
                return;
            }
            workflow.revealLocal(file, true).whenComplete((ignored, revealError) -> Platform.runLater(() -> {
                if (revealError != null) setStatus(messageFor(revealError,
                        com.ynpe.fastremote.i18n.Texts.text("diagnostic.log.revealFailed")), StatusTone.ERROR);
            }));
        }));
    }

    @FXML
    private void clearDiagnosticLog() {
        synchronized (diagnosticLogLock) {
            previousDiagnosticMessage = null;
            previousDiagnosticTone = null;
            diagnosticTextLog.clear();
        }
        primaryStatusLabel.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4f0f1db39594917b"));
    }

    @FXML
    private void choosePrivateKey() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1c331767f030aa90"));
        String current = privateKeyField.getText().strip();
        if (!current.isEmpty()) {
            File parent = Path.of(current).toFile().getParentFile();
            if (parent != null && parent.isDirectory()) {
                chooser.setInitialDirectory(parent);
            }
        }
        File selected = PlatformFileChooser.showOpenDialog(chooser, rootPane.getScene().getWindow());
        if (selected != null) {
            privateKeyField.setText(selected.getAbsolutePath());
        }
    }

    @FXML
    private void parseQuickConnectClipboard() {
        try {
            ConnectionClipboardImporter.ParsedConnection parsed =
                    ConnectionClipboardImporter.parseSystemClipboard();
            credentialSourceField.setValue(QuickCredentialSource.MANUAL);
            quickCredentialSelector.clearSelection();
            ConnectionClipboardImporter.apply(parsed, Optional.empty(), hostField, portField, usernameField,
                    authenticationField, hostPasswordField::setText,
                    privateKeyField, privateKeyPassphraseField::setText);
            ConnectionClipboardImporter.revealParsedSecret(parsed, hostPasswordField, privateKeyPassphraseField);
            updateQuickCredentialSource();
            ConnectionFeedback.show(quickConnectionTestStatus,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.99790d597c9a514d") + parsed.protocol().toUpperCase(Locale.ROOT) + com.ynpe.fastremote.i18n.Texts.text("legacy.java.7991139668186898"),
                    ConnectionFeedback.Tone.SUCCESS);
        } catch (IllegalArgumentException failure) {
            ConnectionFeedback.show(
                    quickConnectionTestStatus, com.ynpe.fastremote.i18n.Texts.translateExternalText(failure.getMessage()), ConnectionFeedback.Tone.ERROR);
        }
    }

    @FXML
    private void testQuickConnection() {
        sideQuickConnectionTestRunner.toggle();
    }

    private void parseClipboardInto(
            TextField host,
            TextField port,
            TextField username,
            ComboBox<AuthenticationType> authentication,
            ComboBox<QuickCredentialSource> source,
            SearchableCredentialSelector library,
            RevealablePasswordEditor hostPassword,
            TextField privateKey,
            RevealablePasswordEditor privateKeyPassphrase,
            Label feedback) {
        try {
            ConnectionClipboardImporter.ParsedConnection parsed =
                    ConnectionClipboardImporter.parseSystemClipboard();
            source.setValue(QuickCredentialSource.MANUAL);
            library.clearSelection();
            ConnectionClipboardImporter.apply(parsed, Optional.empty(), host, port, username, authentication,
                    hostPassword::setText, privateKey, privateKeyPassphrase::setText);
            ConnectionClipboardImporter.revealParsedSecret(parsed, hostPassword, privateKeyPassphrase);
            ConnectionFeedback.clear(feedback);
        } catch (IllegalArgumentException failure) {
            ConnectionFeedback.show(feedback, com.ynpe.fastremote.i18n.Texts.translateExternalText(failure.getMessage()), ConnectionFeedback.Tone.ERROR);
        }
    }

    @FXML
    private void saveCurrentHost() {
        saveCurrentHost(rootPane.getScene().getWindow(), message -> ConnectionFeedback.show(
                quickConnectionTestStatus, message, ConnectionFeedback.Tone.ERROR));
    }

    private void saveCurrentHost(Window owner) {
        saveCurrentHost(owner, message -> setStatus(message, StatusTone.ERROR));
    }

    private void saveCurrentHost(Window owner, Consumer<String> validationFailure) {
        try {
            ConnectionProfile profile = profileFromFields();
            TextInputDialog dialog = new TextInputDialog(profile.host().value());
            dialog.initOwner(owner);
            dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.882e71b2549b330f"));
            dialog.setHeaderText(sessionSaveDialogHeader());
            dialog.setContentText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b3abf4ccd9143872"));
            dialog.showAndWait().map(String::strip).filter(name -> !name.isEmpty()).ifPresent(name -> {
                Optional<GroupId> groupId = quickHostSaveGroup();
                SavedHost existing = quickSaveMatch(catalog.hosts(), profile).orElse(null);
                SavedHost host = existing == null
                        ? SavedHost.create(name, groupId, profile).withJumpHost(quickJumpHostId())
                        : new SavedHost(existing.id(), existing.groupId(), name, profile,
                                quickJumpHostId(), existing.credentialSecretId(), existing.favorite(),
                                existing.sortOrder(), existing.createdAt(), java.time.Instant.now(), existing.icon());
                GlobalCredential selectedCredential = selectedQuickCredential();
                char[] credential = selectedCredential == null
                        ? copyQuickSecret(profile.authenticationType(), hostPasswordField.getText(),
                                privateKeyPassphraseField.getText())
                        : new char[0];
                boolean canStore = catalog.vaultStatus() == VaultStatus.UNLOCKED
                        && selectedCredential == null
                        && (profile.authenticationType() == AuthenticationType.PRIVATE_KEY || credential.length > 0);
                Optional<CredentialDraft> stored = canStore
                        ? Optional.of(new CredentialDraft(
                                profile.authenticationType(), credential, profile.privateKeyPath()))
                        : Optional.empty();
                SavedHost sessionToSave = selectedCredential != null
                        ? host.withCredential(Optional.of(selectedCredential.id()))
                        : canStore ? host : host.withCredential(Optional.empty());
                Arrays.fill(credential, '\0');
                catalogWorkflow.saveHost(sessionToSave, stored).whenComplete((saved, error) ->
                        Platform.runLater(() -> {
                            if (error != null) {
                                setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.e2a6573ea0798d7d")), StatusTone.ERROR);
                            } else {
                                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b22232e465cc9188") + saved.name()
                                                + (saved.credentialSecretId().isPresent() ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.0a9a51ee45f0dcb3") : ""),
                                        StatusTone.IDLE);
                                refreshCatalog();
                            }
                        }));
            });
        } catch (IllegalArgumentException failure) {
            validationFailure.accept(com.ynpe.fastremote.i18n.Texts.translateExternalText(failure.getMessage()));
        }
    }

    static String sessionSaveDialogHeader() {
        return com.ynpe.fastremote.i18n.Texts.text("legacy.java.322bceb74715d2a7");
    }

    static Optional<GroupId> quickHostSaveGroup() {
        return Optional.empty();
    }

    static Optional<SavedHost> quickSaveMatch(List<SavedHost> hosts, ConnectionProfile profile) {
        Objects.requireNonNull(hosts, "hosts");
        Objects.requireNonNull(profile, "profile");
        return hosts.stream().filter(host ->
                host.profile().host().value().equalsIgnoreCase(profile.host().value())
                        && host.profile().port().equals(profile.port())
                        && host.profile().username().equals(profile.username()))
                .findFirst();
    }

    static String quickSecret(
            AuthenticationType authenticationType, String hostPassword, String privateKeyPassphrase) {
        Objects.requireNonNull(authenticationType, "authenticationType");
        return authenticationType == AuthenticationType.PASSWORD
                ? Objects.requireNonNull(hostPassword, "hostPassword")
                : Objects.requireNonNull(privateKeyPassphrase, "privateKeyPassphrase");
    }

    static char[] copyQuickSecret(
            AuthenticationType authenticationType, String hostPassword, String privateKeyPassphrase) {
        return quickSecret(authenticationType, hostPassword, privateKeyPassphrase).toCharArray();
    }

    static boolean manualQuickFieldsDisabled(QuickCredentialSource source) {
        return source == QuickCredentialSource.LIBRARY;
    }

    static boolean quickConnectDialogResizable() {
        return LocalizedTextInputContextMenus.isLinux();
    }

    static void configureQuickConnectDialogSizing(javafx.scene.control.Dialog<?> dialog) {
        Objects.requireNonNull(dialog, "dialog");
        dialog.setResizable(quickConnectDialogResizable());
        if (quickConnectDialogResizable()) {
            DialogSpacing.configureResizableWindowGeometry(dialog,
                    QUICK_CONNECT_DIALOG_WIDTH, QUICK_CONNECT_DIALOG_HEIGHT, 480, 400);
        } else {
            DialogSpacing.configureFixedWindowSize(
                    dialog, QUICK_CONNECT_DIALOG_WIDTH, QUICK_CONNECT_DIALOG_HEIGHT);
        }
    }

    @FXML
    private void exitApplication() {
        // JavaFX closes a Linux MenuBar popup only after its MenuItem action returns.  Exit
        // confirmation is modal, so let the action return before opening that dialog.
        if (LocalizedTextInputContextMenus.isLinux()) Platform.runLater(closeApplication);
        else closeApplication.run();
    }

    private boolean confirmMainWindowClose(boolean exiting) {
        int tabCount = countExitRelevantTabs(workspaceTabs.getTabs(), tab ->
                aiPlacement.isWorkspaceTab(tab) || diagnosticLogPlacement.isWorkspaceTab(tab));
        if (!shouldConfirmExit(
                persistedWorkspaceSettings.windowSettings().confirmExitWithMultipleTabs(), tabCount)) {
            return true;
        }
        ButtonType confirm = new ButtonType(
                com.ynpe.fastremote.i18n.Texts.text(exiting ? "action.exit" : "window.close.action"),
                ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(mainWindow);
        String prefix = exiting ? "exit.confirm" : "window.close";
        alert.setTitle(com.ynpe.fastremote.i18n.Texts.text(prefix + ".title"));
        alert.setHeaderText(com.ynpe.fastremote.i18n.Texts.text(prefix + ".header"));
        alert.setContentText(com.ynpe.fastremote.i18n.Texts.text(prefix + ".message", tabCount));
        alert.getButtonTypes().setAll(ButtonType.CANCEL, confirm);
        TextEditorWorkspace.prepareDialog(alert);
        return alert.showAndWait().filter(confirm::equals).isPresent();
    }

    static boolean shouldConfirmExit(boolean enabled, int relevantTabCount) {
        return enabled && relevantTabCount >= 2;
    }

    static int countExitRelevantTabs(List<Tab> tabs, Predicate<Tab> excluded) {
        Objects.requireNonNull(tabs, "tabs");
        Objects.requireNonNull(excluded, "excluded");
        return Math.toIntExact(tabs.stream().filter(tab -> !excluded.test(tab)).count());
    }

    @FXML private void newEditorDocument() { textEditors.newDocument(); }
    @FXML private void openTextFile() { textEditors.openLocalChooser(); }
    @FXML private void rebuildRecentFiles() { textEditors.rebuildRecentMenu(openRecentMenu); }
    @FXML private void saveTextFile() { textEditors.save(); }
    @FXML private void saveTextFileAs() { textEditors.saveAs(); }
    @FXML private void saveAllTextFiles() { textEditors.saveAll(); }
    @FXML private void closeTextFile() { textEditors.closeActive(); }
    @FXML private void cutText() { textEditors.cut(); }
    @FXML private void copyText() { textEditors.copy(); }
    @FXML private void pasteText() { textEditors.paste(); }
    @FXML private void findText() { textEditors.find(); }
    @FXML private void replaceText() { textEditors.replace(); }

    static List<ButtonType> quickConnectDialogButtonTypes() {
        return List.of(
                new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0a98a48eaaac4175"), ButtonBar.ButtonData.OTHER),
                new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1d1e888fdc965297"), ButtonBar.ButtonData.APPLY),
                new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2cd0f3be8738a86c"), ButtonBar.ButtonData.CANCEL_CLOSE),
                new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a5574109f0208e89"), ButtonBar.ButtonData.OK_DONE));
    }

    static void configureQuickConnectDialogButtonWidths(DialogPane pane, List<ButtonType> buttonTypes) {
        Objects.requireNonNull(pane, "pane");
        Objects.requireNonNull(buttonTypes, "buttonTypes");
        if (buttonTypes.size() != 4) {
            throw new IllegalArgumentException("quick connect dialog requires four buttons");
        }
        for (int index = 2; index < buttonTypes.size(); index++) {
            Node node = pane.lookupButton(buttonTypes.get(index));
            if (node != null) {
                ButtonBar.setButtonUniformSize(node, false);
                DialogSpacing.configureFixedButtonWidth(node, QUICK_CONNECT_COMPACT_BUTTON_WIDTH);
            }
        }
    }

    static List<String> quickConnectDialogFieldLabels() {
        return List.of(
                com.ynpe.fastremote.i18n.Texts.text("form.targetHost"),
                com.ynpe.fastremote.i18n.Texts.text("form.connectionPort"),
                com.ynpe.fastremote.i18n.Texts.text("form.loginUser"),
                com.ynpe.fastremote.i18n.Texts.text("form.credentialSource"),
                com.ynpe.fastremote.i18n.Texts.text("form.authenticationMethod"),
                com.ynpe.fastremote.i18n.Texts.text("form.hostPassword"),
                com.ynpe.fastremote.i18n.Texts.text("form.privateKey"),
                com.ynpe.fastremote.i18n.Texts.text("form.privateKeyPassphrase"),
                com.ynpe.fastremote.i18n.Texts.text("form.vaultCredential"),
                com.ynpe.fastremote.i18n.Texts.text("form.compatibilityMode"),
                com.ynpe.fastremote.i18n.Texts.text("form.connectionPath"));
    }

    static double quickFormFieldLeft() {
        return QUICK_FORM_LABEL_LEFT + QUICK_FORM_LABEL_WIDTH + QUICK_FORM_FIELD_GAP;
    }

    static double quickFormHeight(int rowCount) {
        return rowCount <= 0 ? 0.0
                : rowCount * QUICK_FORM_ROW_HEIGHT + (rowCount - 1) * QUICK_FORM_ROW_GAP;
    }

    private static void addQuickFormRow(AnchorPane form, int row, String labelText, Node field) {
        double top = row * (QUICK_FORM_ROW_HEIGHT + QUICK_FORM_ROW_GAP);
        FormLabel label = new FormLabel(
                labelText, QUICK_FORM_LABEL_WIDTH, QUICK_FORM_LABEL_HEIGHT);
        AnchorPane.setLeftAnchor(label, QUICK_FORM_LABEL_LEFT);
        AnchorPane.setTopAnchor(label,
                top + (QUICK_FORM_ROW_HEIGHT - QUICK_FORM_LABEL_HEIGHT) / 2.0);
        AnchorPane.setLeftAnchor(field, quickFormFieldLeft());
        AnchorPane.setRightAnchor(field, QUICK_FORM_FIELD_RIGHT);
        AnchorPane.setTopAnchor(field, top + (QUICK_FORM_ROW_HEIGHT - QUICK_FORM_CONTROL_HEIGHT) / 2.0);
        if (field instanceof Region region) {
            region.setMinWidth(QUICK_FORM_FIELD_MIN_WIDTH);
            region.setMinHeight(QUICK_FORM_CONTROL_HEIGHT);
            region.setPrefHeight(QUICK_FORM_CONTROL_HEIGHT);
            region.setMaxHeight(QUICK_FORM_CONTROL_HEIGHT);
        }
        form.getChildren().addAll(label, field);
    }

    private static AnchorPane privateKeyRow(TextField privateKey, Button chooseKey) {
        AnchorPane row = new AnchorPane(privateKey, chooseKey);
        AnchorPane.setLeftAnchor(privateKey, 0.0);
        AnchorPane.setRightAnchor(privateKey, FileSelectionButton.WIDTH + 6.0);
        AnchorPane.setTopAnchor(privateKey, 0.0);
        AnchorPane.setBottomAnchor(privateKey, 0.0);
        AnchorPane.setRightAnchor(chooseKey, 0.0);
        AnchorPane.setTopAnchor(chooseKey, 0.0);
        AnchorPane.setBottomAnchor(chooseKey, 0.0);
        return row;
    }

    @FXML
    private void createHost() {
        createHostAt(toolbarNewHostGroup());
    }

    static Optional<GroupId> toolbarNewHostGroup() {
        return Optional.empty();
    }

    private void createHostAt(Optional<GroupId> groupId) {
        createHostAt(groupId, rootPane.getScene().getWindow());
    }

    private void createHostAt(Optional<GroupId> groupId, Window owner) {
        HostEditorDialog.show(
                owner,
                catalog.groups(),
                catalog.hosts(),
                catalog.credentials(),
                catalog.credentialGroups(),
                catalog.vaultStatus(),
                groupId,
                this::requestVaultUnlockForHostSave,
                this::testHostConnection)
                .ifPresent(request -> saveSessionRequest(request, com.ynpe.fastremote.i18n.Texts.text("legacy.java.da092e29008f91bf")));
    }

    @FXML
    private void createGroup() {
        Optional<GroupId> suggestedParent = selectedGroupId().flatMap(id -> catalog.groups().stream()
                .filter(group -> group.id().equals(id) && group.parentId().isEmpty())
                .map(HostGroup::id).findFirst());
        createGroupAt(suggestedParent);
    }

    private void createGroupAt(Optional<GroupId> suggestedParent) {
        createGroupAt(suggestedParent, rootPane.getScene().getWindow());
    }

    private void createGroupAt(Optional<GroupId> suggestedParent, Window owner) {
        GroupEditorDialog.show(owner, catalog.groups(), suggestedParent)
                .ifPresent(request -> catalogWorkflow.createGroup(
                        request.name(), request.parentId(), request.color()).whenComplete((group, error) ->
                        Platform.runLater(() -> {
                            if (error != null) {
                                setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.36d1923a0ff08dc4")), StatusTone.ERROR);
                            } else {
                                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ab86a0ee6963c2a8") + group.name(), StatusTone.IDLE);
                                refreshCatalog();
                            }
                        })));
    }

    private void editCatalogItem(TreeItem<String> item) {
        SavedHost host = hostItems.get(item);
        HostGroup group = groupItems.get(item);
        editCatalogItem(host, group, rootPane.getScene().getWindow());
    }

    private void editCatalogItem(SavedHost host, HostGroup group, Window owner) {
        if (host != null) {
            HostEditorDialog.edit(
                    owner, catalog.groups(), catalog.hosts(), catalog.credentials(), catalog.credentialGroups(),
                    catalog.vaultStatus(), host, this::requestVaultUnlockForHostSave,
                    this::testHostConnection, this::readSavedCredential)
                    .ifPresent(request -> saveSessionRequest(request, com.ynpe.fastremote.i18n.Texts.text("legacy.java.51e22df6fc2d2248")));
            return;
        }
        if (group == null) return;
        GroupEditorDialog.edit(owner, catalog.groups(), group).ifPresent(request -> {
            int sortOrder = group.parentId().equals(request.parentId())
                    ? group.sortOrder() : nextGroupSortOrder(request.parentId(), Optional.of(group.id()));
            HostGroup updated = new HostGroup(
                    group.id(), request.parentId(), request.name(), sortOrder, request.color());
            catalogWorkflow.updateGroup(updated).whenComplete((saved, error) -> Platform.runLater(() -> {
                if (error != null) setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.aa0c2c303633ec62")), StatusTone.ERROR);
                else {
                    setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.358392203d39f94b") + saved.name(), StatusTone.IDLE);
                    refreshCatalog();
                }
            }));
        });
    }

    private void copyCatalogItem(TreeItem<String> item) {
        SavedHost host = hostItems.get(item);
        HostGroup group = groupItems.get(item);
        copyCatalogItem(host, group, rootPane.getScene().getWindow());
    }

    private void copyCatalogItem(SavedHost host, HostGroup group, Window owner) {
        if (host != null) {
            HostEditorDialog.copy(
                    owner, catalog.groups(), catalog.hosts(), catalog.credentials(), catalog.credentialGroups(),
                    catalog.vaultStatus(), host, this::requestVaultUnlockForHostSave,
                    this::testHostConnection)
                    .ifPresent(request -> saveCopiedSessionRequest(
                            host, request, com.ynpe.fastremote.i18n.Texts.text("legacy.java.8c1a744ceb5f9bba")));
            return;
        }
        if (group == null) return;
        GroupEditorDialog.copy(owner, catalog.groups(), group).ifPresent(request ->
                catalogWorkflow.createGroup(request.name(), request.parentId(), request.color())
                        .whenComplete((saved, error) -> Platform.runLater(() -> {
                            if (error != null) setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.7171eb7360aa8aa4")), StatusTone.ERROR);
                            else {
                                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ea03c077bae9c9f3") + saved.name(), StatusTone.IDLE);
                                refreshCatalog();
                            }
                        })));
    }

    private void saveSessionRequest(HostEditorDialog.HostRequest request, String successPrefix) {
        catalogWorkflow.saveHost(request.host(), request.credential()).whenComplete((saved, error) ->
                Platform.runLater(() -> {
                    if (error != null) setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.49f2ea4ac77efb41")), StatusTone.ERROR);
                    else {
                        setStatus(successPrefix + saved.name(), StatusTone.IDLE);
                        refreshCatalog();
                    }
                }));
    }

    private void saveCopiedSessionRequest(
            SavedHost source, HostEditorDialog.HostRequest request, String successPrefix) {
        catalogWorkflow.copyHost(source, request.host(), request.credential()).whenComplete((saved, error) ->
                Platform.runLater(() -> {
                    if (error != null) setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.49f2ea4ac77efb41")), StatusTone.ERROR);
                    else {
                        setStatus(successPrefix + saved.name(), StatusTone.IDLE);
                        refreshCatalog();
                    }
                }));
    }

    private static void installQuickConnectionTestReset(
            QuickConnectionTestRunner runner,
            TextField host,
            TextField port,
            TextField username,
            ComboBox<?> authentication,
            ComboBox<?> source,
            RevealablePasswordEditor hostPassword,
            TextField privateKey,
            RevealablePasswordEditor privateKeyPassphrase,
            CheckBox legacyAlgorithms) {
        host.textProperty().addListener((ignored, previous, value) -> runner.clearCompletedStatus());
        port.textProperty().addListener((ignored, previous, value) -> runner.clearCompletedStatus());
        username.textProperty().addListener((ignored, previous, value) -> runner.clearCompletedStatus());
        authentication.valueProperty().addListener((ignored, previous, value) -> runner.clearCompletedStatus());
        source.valueProperty().addListener((ignored, previous, value) -> runner.clearCompletedStatus());
        hostPassword.textProperty().addListener((ignored, previous, value) -> runner.clearCompletedStatus());
        privateKey.textProperty().addListener((ignored, previous, value) -> runner.clearCompletedStatus());
        privateKeyPassphrase.textProperty().addListener((ignored, previous, value) -> runner.clearCompletedStatus());
        legacyAlgorithms.selectedProperty().addListener((ignored, previous, value) -> runner.clearCompletedStatus());
    }

    private final class QuickConnectionTestRunner implements AutoCloseable {
        private final Supplier<Window> owner;
        private final Supplier<HostEditorDialog.ConnectionTestRequest> requestFactory;
        private final Node form;
        private final Button testButton;
        private final List<Button> disabledActions;
        private final Label status;
        private final Map<Button, Boolean> disabledBeforeTest = new IdentityHashMap<>();
        private ConnectionTestAttempt activeAttempt;
        private boolean vaultAvailable;
        private boolean closed;

        private QuickConnectionTestRunner(
                Supplier<Window> owner,
                Supplier<HostEditorDialog.ConnectionTestRequest> requestFactory,
                Node form,
                Button testButton,
                List<Button> disabledActions,
                Label status) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.requestFactory = Objects.requireNonNull(requestFactory, "requestFactory");
            this.form = Objects.requireNonNull(form, "form");
            this.testButton = Objects.requireNonNull(testButton, "testButton");
            this.disabledActions = List.copyOf(disabledActions);
            this.status = Objects.requireNonNull(status, "status");
            this.vaultAvailable = catalog.vaultStatus() == VaultStatus.UNLOCKED;
        }

        private void toggle() {
            if (closed) return;
            if (activeAttempt != null) {
                showStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.58db0fe39e87bf53"), QuickTestTone.WORKING);
                activeAttempt.cancel();
                return;
            }
            HostEditorDialog.ConnectionTestRequest request;
            try {
                request = requestFactory.get();
            } catch (IllegalArgumentException | NullPointerException invalid) {
                showStatus(invalid.getMessage() == null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.85f2ba637f7f5d7e")
                                : com.ynpe.fastremote.i18n.Texts.translateExternalText(invalid.getMessage()),
                        QuickTestTone.ERROR);
                return;
            }
            vaultAvailable = vaultAvailable || catalog.vaultStatus() == VaultStatus.UNLOCKED;
            if (HostEditorDialog.connectionTestRequiresVault(
                    request.credentialId(), request.jumpHostId(), catalog.hosts()) && !vaultAvailable) {
                request.close();
                showStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.325fa105f60b51c4"), QuickTestTone.ERROR);
                testButton.setDisable(true);
                CompletionStage<Boolean> unlockAttempt;
                try {
                    unlockAttempt = requestVaultUnlockForHostSave(owner.get());
                } catch (RuntimeException failure) {
                    testButton.setDisable(false);
                    showStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f65eaa468471364a"), QuickTestTone.ERROR);
                    return;
                }
                unlockAttempt.whenComplete((unlocked, failure) -> Platform.runLater(() -> {
                    if (closed) return;
                    testButton.setDisable(false);
                    if (failure != null || !Boolean.TRUE.equals(unlocked)) {
                        showStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.627b097a22304cff"), QuickTestTone.ERROR);
                    } else {
                        vaultAvailable = true;
                        toggle();
                    }
                }));
                return;
            }
            begin(request);
        }

        private void begin(HostEditorDialog.ConnectionTestRequest request) {
            ConnectionTestAttempt attempt;
            try {
                attempt = testHostConnection(owner.get(), request,
                        progress -> Platform.runLater(() -> showStatus(
                                HostEditorDialog.testProgressMessage(progress), QuickTestTone.WORKING)));
            } catch (RuntimeException failure) {
                request.close();
                showStatus(connectionFailureMessage(failure), QuickTestTone.ERROR);
                return;
            }
            activeAttempt = attempt;
            form.setDisable(true);
            disabledBeforeTest.clear();
            disabledActions.forEach(button -> {
                disabledBeforeTest.put(button, button.isDisable());
                button.setDisable(true);
            });
            testButton.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.49be72e6045c007d"));
            showStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a5ece418ec332f50"), QuickTestTone.WORKING);
            attempt.result().whenComplete((ignored, failure) -> Platform.runLater(() -> {
                if (closed || activeAttempt != attempt) return;
                activeAttempt = null;
                form.setDisable(false);
                disabledActions.forEach(button -> button.setDisable(
                        disabledBeforeTest.getOrDefault(button, false)));
                disabledBeforeTest.clear();
                testButton.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0a98a48eaaac4175"));
                if (failure == null) {
                    showStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.da0bfef256bc82e4"), QuickTestTone.SUCCESS);
                } else {
                    Throwable cause = unwrap(failure);
                    showStatus(cause instanceof CancellationException
                            ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.b7c75172b393766f") : connectionFailureMessage(cause), QuickTestTone.ERROR);
                }
            }));
        }

        private void clearCompletedStatus() {
            if (activeAttempt != null || !status.isVisible()) return;
            ConnectionFeedback.clear(status);
        }

        private void showStatus(String message, QuickTestTone tone) {
            if (closed) return;
            ConnectionFeedback.show(status, message, switch (tone) {
                case WORKING -> ConnectionFeedback.Tone.WORKING;
                case SUCCESS -> ConnectionFeedback.Tone.SUCCESS;
                case ERROR -> ConnectionFeedback.Tone.ERROR;
            });
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            ConnectionTestAttempt running = activeAttempt;
            activeAttempt = null;
            if (running != null) running.cancel();
            form.setDisable(false);
            disabledActions.forEach(button -> button.setDisable(
                    disabledBeforeTest.getOrDefault(button, button.isDisable())));
            disabledBeforeTest.clear();
        }
    }

    private enum QuickTestTone { WORKING, SUCCESS, ERROR }

    private ConnectionTestAttempt testHostConnection(
            Window owner,
            HostEditorDialog.ConnectionTestRequest request,
            Consumer<ConnectionProgress> progress) {
        PendingConnectionTest pending = new PendingConnectionTest(owner, request, progress);
        pending.start();
        return new ConnectionTestAttempt(pending.result, pending::cancel);
    }

    private final class PendingConnectionTest {
        private final Window owner;
        private final HostEditorDialog.ConnectionTestRequest request;
        private final Consumer<ConnectionProgress> progress;
        private final CompletableFuture<Void> result = new CompletableFuture<>();
        private final List<com.ynpe.fastremote.domain.connection.ConnectionHop> resolved = new ArrayList<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private ConnectionTestAttempt activeAttempt;
        private List<SavedHost> route;
        private SavedHost target;

        private PendingConnectionTest(
                Window owner,
                HostEditorDialog.ConnectionTestRequest request,
                Consumer<ConnectionProgress> progress) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.request = Objects.requireNonNull(request, "request");
            this.progress = Objects.requireNonNull(progress, "progress");
        }

        private void start() {
            try {
                target = SavedHost.create(com.ynpe.fastremote.i18n.Texts.text("legacy.java.56f9e71aaa5a68dc"), Optional.empty(), request.profile(), 0)
                        .withJumpHost(request.jumpHostId())
                        .withCredential(request.credentialId());
                route = savedConnectionRoute(target);
                resolve(0);
            } catch (RuntimeException failure) {
                fail(failure);
            }
        }

        private void resolve(int index) {
            if (cancelled.get()) return;
            if (index >= route.size()) {
                beginTest();
                return;
            }
            SavedHost hop = route.get(index);
            if (hop.id().equals(target.id()) && request.credential().isPresent()) {
                try {
                    resolved.add(new com.ynpe.fastremote.domain.connection.ConnectionHop(
                            hop.name(), hop.profile(), draftCredentials(hop.profile(), request.credential().orElseThrow())));
                    request.close();
                    resolve(index + 1);
                } catch (RuntimeException failure) {
                    fail(failure);
                }
                return;
            }
            if (hop.credentialSecretId().isPresent()) {
                catalogWorkflow.loadCredential(hop).whenComplete((credential, error) -> Platform.runLater(() -> {
                    if (cancelled.get()) {
                        if (credential != null) credential.close();
                        return;
                    }
                    if (error != null) {
                        fail(unwrap(error));
                        return;
                    }
                    try {
                        resolved.add(new com.ynpe.fastremote.domain.connection.ConnectionHop(
                                hop.name(), hop.profile(), domainCredentials(hop.profile(), credential)));
                        resolve(index + 1);
                    } catch (RuntimeException failure) {
                        if (credential != null) credential.close();
                        fail(failure);
                    }
                }));
                return;
            }
            Optional<com.ynpe.fastremote.domain.connection.Credentials> prompted = promptRouteCredential(hop, owner);
            if (prompted.isEmpty()) {
                fail(new CancellationException("Connection test credential entry was cancelled"));
                return;
            }
            resolved.add(new com.ynpe.fastremote.domain.connection.ConnectionHop(
                    hop.name(), hop.profile(), prompted.orElseThrow()));
            resolve(index + 1);
        }

        private void beginTest() {
            if (cancelled.get()) return;
            List<com.ynpe.fastremote.domain.connection.ConnectionHop> transferred = List.copyOf(resolved);
            resolved.clear();
            request.close();
            try {
                activeAttempt = workflow.testConnection(
                        transferred, check -> confirmHostKey(check, owner), progress);
                activeAttempt.result().whenComplete((ignored, error) -> Platform.runLater(() -> {
                    activeAttempt = null;
                    if (cancelled.get()) return;
                    if (error == null) result.complete(null);
                    else result.completeExceptionally(unwrap(error));
                }));
            } catch (RuntimeException failure) {
                closeConnectionHops(transferred);
                fail(failure);
            }
        }

        private void fail(Throwable failure) {
            request.close();
            closeConnectionHops(resolved);
            resolved.clear();
            result.completeExceptionally(failure);
        }

        private void cancel() {
            if (!cancelled.compareAndSet(false, true)) return;
            request.close();
            closeConnectionHops(resolved);
            resolved.clear();
            if (activeAttempt != null) activeAttempt.cancel();
            result.completeExceptionally(new CancellationException("Connection test was cancelled"));
        }
    }

    private static com.ynpe.fastremote.domain.connection.Credentials draftCredentials(
            ConnectionProfile profile, CredentialDraft draft) {
        if (profile.authenticationType() != draft.authenticationType()) {
            throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.92544709551f0d3c"));
        }
        char[] secret = draft.copySecret();
        try {
            return profile.authenticationType() == AuthenticationType.PASSWORD
                    ? new PasswordCredentials(secret) : new PrivateKeyCredentials(secret);
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    private int nextGroupSortOrder(Optional<GroupId> parentId, Optional<GroupId> excluded) {
        return catalog.groups().stream()
                .filter(group -> group.parentId().equals(parentId)
                        && excluded.map(id -> !id.equals(group.id())).orElse(true))
                .mapToInt(HostGroup::sortOrder).max().orElse(-10) + 10;
    }

    @FXML
    private void deleteSelectedSavedHost() {
        List<SavedHost> hosts = selectedHosts(hostTree, hostItems);
        if (hosts.isEmpty()) {
            setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.34c72760eb852a24"), StatusTone.IDLE);
            return;
        }
        deleteCatalogItems(hosts, List.of(), rootPane.getScene().getWindow());
    }

    private void deleteCatalogSelection(
            TreeView<String> tree,
            Map<TreeItem<String>, SavedHost> hosts,
            Map<TreeItem<String>, HostGroup> groups,
            Window owner) {
        deleteCatalogItems(selectedHosts(tree, hosts), selectedGroups(tree, groups), owner);
    }

    private void deleteCatalogItems(List<SavedHost> hosts, List<HostGroup> groups, Window owner) {
        if (hosts.isEmpty() && groups.isEmpty()) return;
        Optional<HostGroup> blocked = firstGroupContainingUnselectedContent(hosts, groups);
        if (blocked.isPresent()) {
            setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1bdba2fb52d8937a") + blocked.get().name() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.7c6aadb2fd28ae66"), StatusTone.ERROR);
            return;
        }
        List<HostGroup> orderedGroups = groupsForBulkDeletion(groups);
        int total = hosts.size() + orderedGroups.size();
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(owner);
        confirmation.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c8cdb45698e53a79"));
        if (total == 1 && !hosts.isEmpty()) {
            confirmation.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2ac7fd2fd8db298f") + hosts.getFirst().name() + "”？");
        } else if (total == 1) {
            confirmation.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c2e534f39d91ccb8") + orderedGroups.getFirst().name() + "”？");
        } else {
            confirmation.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f2c699b7c0d1072f") + total + com.ynpe.fastremote.i18n.Texts.text("legacy.java.d250ad4437329505"));
        }
        confirmation.setContentText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3fa2cced3fdc3504") + hosts.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.d29da5936a54a17d") + orderedGroups.size()
                + com.ynpe.fastremote.i18n.Texts.text("legacy.java.fe171e0700fb6515"));
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        List<Supplier<CompletionStage<Void>>> operations = new ArrayList<>(total);
        hosts.forEach(host -> operations.add(() -> catalogWorkflow.deleteHost(host).thenAccept(directHosts -> {
            if (!directHosts.isEmpty()) {
                Platform.runLater(() -> appendDiagnosticLog(statusMessage(StatusModule.GLOBAL, "",
                        localizer.text("hostLibrary.jumpDeletedDirectHosts", host.name(), String.join(", ", directHosts))),
                        StatusTone.IDLE));
            }
        })));
        orderedGroups.forEach(group -> operations.add(() -> catalogWorkflow.deleteGroup(group.id())));
        runDeleteOperations(operations).whenComplete((errors, error) -> Platform.runLater(() -> {
            List<Throwable> failures = error == null ? errors : List.of(unwrap(error));
            int deleted = total - failures.size();
            if (failures.isEmpty()) {
                setStatus(total == 1 ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.7645e784e9090459") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.4f616c9f960e9818") + deleted + com.ynpe.fastremote.i18n.Texts.text("legacy.java.cdc23fef3ca08abf"),
                        StatusTone.IDLE);
            } else {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4f616c9f960e9818") + deleted + com.ynpe.fastremote.i18n.Texts.text("legacy.java.94619ad60ed44789") + failures.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.5f6cb87491f93dc1")
                        + messageFor(failures.getFirst(), com.ynpe.fastremote.i18n.Texts.text("legacy.java.9a2170ba54a5b942")), StatusTone.ERROR);
            }
            refreshCatalog();
        }));
    }

    private Optional<HostGroup> firstGroupContainingUnselectedContent(
            List<SavedHost> selectedHosts, List<HostGroup> selectedGroups) {
        Set<com.ynpe.fastremote.domain.catalog.HostId> selectedHostIds = selectedHosts.stream()
                .map(SavedHost::id).collect(java.util.stream.Collectors.toSet());
        Set<GroupId> selectedGroupIds = selectedGroups.stream()
                .map(HostGroup::id).collect(java.util.stream.Collectors.toSet());
        return selectedGroups.stream().filter(group ->
                catalog.hosts().stream().anyMatch(host ->
                        host.groupId().equals(Optional.of(group.id()))
                                && !selectedHostIds.contains(host.id()))
                || catalog.groups().stream().anyMatch(child ->
                        child.parentId().equals(Optional.of(group.id()))
                                && !selectedGroupIds.contains(child.id())))
                .findFirst();
    }

    static List<HostGroup> groupsForBulkDeletion(List<HostGroup> groups) {
        return groups.stream()
                .distinct()
                .sorted(Comparator.comparing((HostGroup group) -> group.parentId().isPresent()).reversed())
                .toList();
    }

    static CompletionStage<List<Throwable>> runDeleteOperations(
            List<Supplier<CompletionStage<Void>>> operations) {
        return LibraryMutationSequence.runDeleteOperations(operations);
    }

    @FXML
    private void exportCatalog() {
        Dialog<HostExportRequest> options = new Dialog<>();
        options.initOwner(rootPane.getScene().getWindow());
        options.setTitle(com.ynpe.fastremote.i18n.Texts.text("config.export.title"));
        options.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.dc03501dbd678954"));
        DialogSpacing.prepare(options);
        CheckBox program = new CheckBox(localizer.text("config.section.program"));
        CheckBox hosts = new CheckBox(localizer.text("config.section.hosts"));
        CheckBox commands = new CheckBox(localizer.text("config.section.commands"));
        CheckBox variables = new CheckBox(localizer.text("config.section.variables"));
        program.setSelected(true);
        hosts.setSelected(true);
        CheckBox reusable = new CheckBox(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1ea5ac836eb68bb1"));
        CheckBox hostBound = new CheckBox(com.ynpe.fastremote.i18n.Texts.text("legacy.java.28cfbf50389af6f1"));
        hostBound.selectedProperty().addListener((observable, before, selected) -> {
            if (selected) hosts.setSelected(true);
        });
        hosts.disableProperty().bind(hostBound.selectedProperty());
        RevealablePasswordEditor password = new RevealablePasswordEditor(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6278dd89bea6ded5"));
        password.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.36723127cd5cb82b"));
        password.disableProperty().bind(reusable.selectedProperty().or(hostBound.selectedProperty()).not());
        Label note = new Label(localizer.text("config.export.note"));
        note.setWrapText(true);
        note.setMinHeight(Region.USE_PREF_SIZE);
        note.setMaxWidth(Double.MAX_VALUE);
        note.getStyleClass().add("inline-security-note");
        Label validation = new Label();
        validation.setWrapText(true);
        validation.setMinHeight(Region.USE_PREF_SIZE);
        validation.setMaxWidth(Double.MAX_VALUE);
        validation.setVisible(false);
        validation.setManaged(false);
        validation.getStyleClass().add("vault-password-validation-error");
        Label passwordLabel = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f36f5f11edefb912"));
        boolean[] remembered = {false};
        javafx.beans.property.BooleanProperty checkingSavedUnlock = new javafx.beans.property.SimpleBooleanProperty(true);
        VBox content = new VBox(10.0, program, hosts, reusable, hostBound, commands, variables, passwordLabel, password, validation, note);
        content.setPrefWidth(440.0);
        options.getDialogPane().setContent(content);
        options.getDialogPane().setPrefWidth(500.0);
        options.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        options.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        Runnable resizeToContent = () -> Platform.runLater(() -> {
            if (options.getDialogPane().getScene() != null) {
                options.getDialogPane().getScene().getWindow().sizeToScene();
            }
        });
        password.textProperty().addListener((ignored, previous, current) -> {
            if (!validation.isVisible()) return;
            validation.setText("");
            validation.setVisible(false);
            validation.setManaged(false);
            resizeToContent.run();
        });
        Node confirm = options.getDialogPane().lookupButton(ButtonType.OK);
        confirm.disableProperty().bind(program.selectedProperty().or(hosts.selectedProperty())
                .or(reusable.selectedProperty()).or(hostBound.selectedProperty())
                .or(commands.selectedProperty()).or(variables.selectedProperty()).not().or(content.disabledProperty()).or(checkingSavedUnlock));
        catalogWorkflow.rememberedVaultUnlockConfigured().whenComplete((saved, error) -> Platform.runLater(() -> {
            if (!options.isShowing()) return;
            if (error != null) {
                validation.setText(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.4da0335f4856f83e")));
                validation.setVisible(true);
                validation.setManaged(true);
                resizeToContent.run();
                return;
            }
            remembered[0] = Boolean.TRUE.equals(saved);
            password.setVisible(!remembered[0]);
            password.setManaged(!remembered[0]);
            passwordLabel.setVisible(!remembered[0]);
            passwordLabel.setManaged(!remembered[0]);
            if (remembered[0]) note.setText(localizer.text("config.export.remembered.note"));
            checkingSavedUnlock.set(false);
            resizeToContent.run();
        }));
        confirm.addEventFilter(ActionEvent.ACTION, event -> {
            boolean includesCredentials = reusable.isSelected() || hostBound.isSelected();
            if (includesCredentials && !remembered[0] && password.getText().isEmpty()) {
                event.consume();
                validation.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ab8401de84318309"));
                validation.setVisible(true);
                validation.setManaged(true);
                FormValidation.focus(password);
                resizeToContent.run();
                return;
            }
            if (!includesCredentials) return;

            event.consume();
            HostExportRequest pending = new HostExportRequest(
                    new ConfigurationSelection(program.isSelected(), hosts.isSelected(), reusable.isSelected(), hostBound.isSelected(), commands.isSelected(), variables.isSelected()), password.getText().toCharArray());
            char[] entered = pending.password().clone();
            validation.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2786d5363fb74955"));
            validation.setVisible(true);
            validation.setManaged(true);
            content.setDisable(true);

            resizeToContent.run();
            catalogWorkflow.verifyVaultMasterPassword(entered).whenComplete((ignored, error) ->
                    Platform.runLater(() -> {
                        Arrays.fill(entered, '\0');
                        if (!options.isShowing()) {
                            pending.close();
                            return;
                        }
                        if (error != null) {
                            pending.close();
                            content.setDisable(false);

                            validation.setText(exportVaultVerificationMessage(error));
                            password.focusInput();
                            resizeToContent.run();
                            return;
                        }
                        options.setResult(pending);
                        options.close();
                    }));
        });
        options.setResultConverter(button -> button == ButtonType.OK
                ? new HostExportRequest(new ConfigurationSelection(program.isSelected(), hosts.isSelected(), reusable.isSelected(), hostBound.isSelected(), commands.isSelected(), variables.isSelected()), password.getText().toCharArray()) : null);
        Optional<HostExportRequest> requested = options.showAndWait();
        password.clear();
        if (requested.isEmpty()) return;

        FileChooser chooser = ConfigurationFileChooser.forExport(
                localizer.text("config.export.title"), localizer.text("config.file.filter"),
                System.getProperty("os.name", "").startsWith("Mac"));
        File target = PlatformFileChooser.showSaveDialog(chooser, rootPane.getScene().getWindow());
        if (target == null) {
            requested.get().close();
            return;
        }
        HostExportRequest request = requested.get();
        setStatus(com.ynpe.fastremote.i18n.Texts.text("config.export.title"), StatusTone.WORKING);
        catalogWorkflow.exportConfiguration(target.toPath(), request.selection(), request.password())
                .whenComplete((ignored, error) -> Platform.runLater(() ->
                setStatus(error == null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.76aa0eda7a822c15") + target.getAbsolutePath()
                                : messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.fc1408139550f8af")),
                        error == null ? StatusTone.IDLE : StatusTone.ERROR)));
        request.close();
    }

    @FXML
    private void importCatalog() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(com.ynpe.fastremote.i18n.Texts.text("config.import.title"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                localizer.text("config.file.filter"), "*.frc"));
        File source = PlatformFileChooser.showOpenDialog(chooser, rootPane.getScene().getWindow());
        if (source == null) {
            return;
        }
        setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b0930bc8002ee6ff"), StatusTone.WORKING);
        catalogWorkflow.inspectCatalogArchive(source.toPath()).whenComplete((info, inspectError) -> Platform.runLater(() -> {
            if (inspectError != null) {
                setStatus(messageFor(inspectError, com.ynpe.fastremote.i18n.Texts.text("legacy.java.9e5d22c448363aad")), StatusTone.ERROR);
                return;
            }
            Alert confirmConfiguration = new Alert(Alert.AlertType.CONFIRMATION);
            confirmConfiguration.initOwner(rootPane.getScene().getWindow());
            confirmConfiguration.setTitle(localizer.text("config.import.title"));
            confirmConfiguration.setHeaderText(localizer.text("config.import.confirm"));
            confirmConfiguration.setContentText(localizer.text("config.import.note"));
            if (confirmConfiguration.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            if (info.hasIdConflicts() && !confirmCatalogIdOverwrite(info)) {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.08d7a98d788a6892"), StatusTone.IDLE);
                return;
            }
            if (!info.hasSecrets()) {
                runCatalogImport(source.toPath(), new char[0]);
                return;
            }
            Optional<char[]> sourcePassword = requestArchiveMasterPassword();
            if (sourcePassword.isEmpty()) {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.08d7a98d788a6892"), StatusTone.IDLE);
                return;
            }
            char[] entered = sourcePassword.get();
            setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.9d0de6fea44aab49"), StatusTone.WORKING);
            catalogWorkflow.verifyCatalogArchivePassword(source.toPath(), entered)
                    .whenComplete((ignored, passwordError) -> Platform.runLater(() -> {
                        if (passwordError != null) {
                            Arrays.fill(entered, '\0');
                            setStatus(messageFor(passwordError, com.ynpe.fastremote.i18n.Texts.text("legacy.java.8e139a3e9fd2a944")), StatusTone.ERROR);
                            return;
                        }
                        ensureVaultForCatalogImport(rootPane.getScene().getWindow())
                                .whenComplete((ready, vaultError) -> Platform.runLater(() -> {
                                    if (vaultError != null || !ready) {
                                        Arrays.fill(entered, '\0');
                                        setStatus(vaultError == null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.08d7a98d788a6892")
                                                        : messageFor(vaultError, com.ynpe.fastremote.i18n.Texts.text("legacy.java.059bddc7c451ca81")),
                                                vaultError == null ? StatusTone.IDLE : StatusTone.ERROR);
                                        return;
                                    }
                                    runCatalogImport(source.toPath(), entered);
                                }));
                    }));
        }));
    }

    @FXML
    private void importSecureCrtCatalog() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f388a53c5a98366b"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.616e6e0b57d436e0"), "*.xml"));
        File source = PlatformFileChooser.showOpenDialog(chooser, rootPane.getScene().getWindow());
        if (source != null) importSecureCrt(source.toPath());
    }

    @FXML
    private void importMobaXtermCatalog() {
        showThirdPartyImport(com.ynpe.fastremote.domain.host.HostImportSource.MOBAXTERM);
    }

    @FXML
    private void importXShellCatalog() {
        showThirdPartyImport(com.ynpe.fastremote.domain.host.HostImportSource.XSHELL);
    }

    @FXML
    private void importPuttyCatalog() {
        showThirdPartyImport(com.ynpe.fastremote.domain.host.HostImportSource.PUTTY);
    }

    @FXML
    private void importTermiusCatalog() {
        showThirdPartyImport(com.ynpe.fastremote.domain.host.HostImportSource.TERMIUS);
    }

    @FXML
    private void importBitviseCatalog() {
        showThirdPartyImport(com.ynpe.fastremote.domain.host.HostImportSource.BITVISE);
    }

    private void showThirdPartyImport(com.ynpe.fastremote.domain.host.HostImportSource source) {
        ThirdPartyImportDialog.show(rootPane.getScene().getWindow(), source, catalogWorkflow,
                () -> ensureVaultForCatalogImport(rootPane.getScene().getWindow()), this::refreshCatalog);
    }

    @FXML
    private void importOpenSshCatalog() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.64da0b36b60f970a"));
        Path sshDirectory = Path.of(System.getProperty("user.home", "."), ".ssh");
        if (java.nio.file.Files.isDirectory(sshDirectory)) chooser.setInitialDirectory(sshDirectory.toFile());
        chooser.setInitialFileName("config");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7f6ebe587f536387"), "*"));
        File source = PlatformFileChooser.showOpenDialog(chooser, rootPane.getScene().getWindow());
        if (source != null) inspectOpenSshConfig(source.toPath());
    }

    private void inspectOpenSshConfig(Path source) {
        setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f738752ab3008933"), StatusTone.WORKING);
        catalogWorkflow.inspectOpenSshConfig(source).whenComplete((info, inspectError) -> Platform.runLater(() -> {
            if (inspectError != null) {
                setStatus(messageFor(inspectError, com.ynpe.fastremote.i18n.Texts.text("legacy.java.f4c487f143e587ec")), StatusTone.ERROR);
                return;
            }
            if (info.hosts() == 0) {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1ea1b8140526f40a"), StatusTone.ERROR);
                return;
            }
            Alert summary = new Alert(Alert.AlertType.CONFIRMATION);
            summary.initOwner(rootPane.getScene().getWindow());
            summary.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.752f99612df6bf62"));
            summary.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e1f357bb8e1ba04a"));
            StringBuilder details = new StringBuilder()
                    .append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0fcee7bd88287dd8")).append(info.configFiles()).append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.448a4abbda11becb"))
                    .append(info.hosts()).append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a1ff883ceef59192")).append(info.readableIdentities())
                    .append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8b2b8e2787d35480")).append(info.encryptedIdentities()).append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3c4f80c2d5f2385f"))
                    .append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c502b0a014116529"));
            if (info.missingIdentities() > 0) {
                details.append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.81d6aa0987ca4bae")).append(info.missingIdentities())
                        .append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4cc5f79d285fc566"));
            }
            if (info.proxyHosts() > 0) {
                details.append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.560e668a6061e091"))
                        .append(info.proxyHosts())
                        .append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.731d2011e657d10d"));
            }
            if (info.invalidHosts() > 0) {
                details.append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8e2e28b3dc36cb72"))
                        .append(info.invalidHosts()).append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3c4f80c2d5f2385f"));
            }
            if (info.wildcardPatterns() > 0 || info.ignoredOptions() > 0) {
                details.append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5e740e9cb142c35d"));
            }
            details.append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7f94b8b28119754e"));
            summary.setContentText(details.toString());
            ButtonType proceed = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e292c6bbf1480044"), ButtonBar.ButtonData.OK_DONE);
            summary.getButtonTypes().setAll(ButtonType.CANCEL, proceed);
            if (summary.showAndWait().orElse(ButtonType.CANCEL) != proceed) {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.08d7a98d788a6892"), StatusTone.IDLE);
                return;
            }

            Map<Path, char[]> passphrases = requestOpenSshKeyPassphrases(info);
            if (passphrases == null) {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.08d7a98d788a6892"), StatusTone.IDLE);
                return;
            }
            boolean importsSecrets = info.identities().stream().anyMatch(identity -> !identity.encrypted())
                    || !passphrases.isEmpty();
            CompletionStage<Boolean> ready = importsSecrets
                    ? ensureVaultForCatalogImport(rootPane.getScene().getWindow())
                    : CompletableFuture.completedFuture(true);
            ready.whenComplete((vaultReady, vaultError) -> Platform.runLater(() -> {
                if (vaultError != null || !vaultReady) {
                    clearSecrets(passphrases);
                    setStatus(vaultError == null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.08d7a98d788a6892")
                                    : messageFor(vaultError, com.ynpe.fastremote.i18n.Texts.text("legacy.java.059bddc7c451ca81")),
                            vaultError == null ? StatusTone.IDLE : StatusTone.ERROR);
                    return;
                }
                runOpenSshImport(source, passphrases);
            }));
        }));
    }

    /** Returns null only when the user cancels the entire import. */
    private Map<Path, char[]> requestOpenSshKeyPassphrases(OpenSshImportInfo info) {
        Map<Path, char[]> result = new HashMap<>();
        for (OpenSshImportInfo.Identity identity : info.identities()) {
            if (!identity.encrypted()) continue;
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.initOwner(rootPane.getScene().getWindow());
            dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.dc39b30343ac2d9d"));
            dialog.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.75cb96da78961c3f"));
            DialogSpacing.prepare(dialog);
            RevealablePasswordEditor passphrase = new RevealablePasswordEditor(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0550c993e3f9f19a"));
            passphrase.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.41f074af4c2b87ef"));
            Label path = new Label(identity.path().toString());
            path.setWrapText(true);
            path.getStyleClass().add("component-help");
            Label note = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8489c6df03a29f14"));
            note.setWrapText(true);
            note.getStyleClass().add("inline-security-note");
            dialog.getDialogPane().setContent(new VBox(8.0, new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3ffe935f9d0ff77e")), path,
                    new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f265a7b34089884e")), passphrase, note));
            ButtonType skip = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.00d19eec59840203"), ButtonBar.ButtonData.OTHER);
            ButtonType use = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.63ec50121ae3fb38"), ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, skip, use);
            ButtonType choice = dialog.showAndWait().orElse(ButtonType.CANCEL);
            if (choice == ButtonType.CANCEL) {
                passphrase.clear();
                clearSecrets(result);
                return null;
            }
            if (choice == use) result.put(identity.path(), passphrase.getText().toCharArray());
            passphrase.clear();
        }
        return result;
    }

    private void runOpenSshImport(Path source, Map<Path, char[]> passphrases) {
        setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1fc6dcf70a82650e"), StatusTone.WORKING);
        CompletionStage<HostCatalogImportResult> importStage = catalogWorkflow.importOpenSshConfig(source, passphrases);
        clearSecrets(passphrases);
        importStage.whenComplete((result, error) -> Platform.runLater(() -> {
            if (error != null) {
                setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.ce9bf40761ab1f35")), StatusTone.ERROR);
            } else {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2a9010137d7ba142") + result.hosts() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.068921c4e871de7b") + result.groups()
                        + com.ynpe.fastremote.i18n.Texts.text("legacy.java.8ce746df80a7dc29") + result.credentials() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.41db51d7cd30c142"), StatusTone.IDLE);
                refreshCatalog();
            }
        }));
    }

    private static void clearSecrets(Map<Path, char[]> secrets) {
        secrets.values().forEach(value -> Arrays.fill(value, '\0'));
        secrets.clear();
    }

    private void importSecureCrt(Path source) {
        setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8e2d303790186f2d"), StatusTone.WORKING);
        catalogWorkflow.inspectSecureCrtArchive(source).whenComplete((info, inspectError) -> Platform.runLater(() -> {
            if (inspectError != null) {
                setStatus(messageFor(inspectError, com.ynpe.fastremote.i18n.Texts.text("legacy.java.854f82bb7c687453")), StatusTone.ERROR);
                return;
            }
            Alert summary = new Alert(Alert.AlertType.CONFIRMATION);
            summary.initOwner(rootPane.getScene().getWindow());
            summary.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5eac55f65e4483d9"));
            summary.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d610255fb443ac2a"));
            StringBuilder details = new StringBuilder()
                    .append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.27815705eb548150")).append(info.hosts()).append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.da5bbb8a47923164"))
                    .append(info.groups()).append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.508eb06f9f8a8a47"))
                    .append(info.sharedCredentials()).append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3c4f80c2d5f2385f"))
                    .append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8d9fac715527eda4"))
                    .append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0f4cb115c66777aa"));
            if (info.skippedHosts() > 0) {
                details.append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4ee0560e6fdc4c04"))
                        .append(info.skippedHosts()).append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3c4f80c2d5f2385f"));
            }
            if (info.certificateReferences() > 0) {
                details.append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.dc9ade873665a763")).append(info.readableCertificates()).append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.fbf0c0c9f43ec6aa"))
                        .append(info.missingCertificates()).append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b11a6b6a4ebaac55"))
                        .append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a678c0141a43a8f8"));
            }
            details.append(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1423b304aa3cf18e"));
            summary.setContentText(details.toString());
            ButtonType proceed = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e292c6bbf1480044"), ButtonBar.ButtonData.OK_DONE);
            summary.getButtonTypes().setAll(ButtonType.CANCEL, proceed);
            if (summary.showAndWait().orElse(ButtonType.CANCEL) != proceed) {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.08d7a98d788a6892"), StatusTone.IDLE);
                return;
            }

            char[] sourcePassphrase;
            if (info.encryptedPasswords() > 0) {
                Optional<char[]> entered = requestSecureCrtPassphrase();
                if (entered.isEmpty()) {
                    setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.08d7a98d788a6892"), StatusTone.IDLE);
                    return;
                }
                sourcePassphrase = entered.get();
            } else {
                sourcePassphrase = new char[0];
            }
            setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.06afc0111194ac45"), StatusTone.WORKING);
            catalogWorkflow.verifySecureCrtArchivePassword(source, sourcePassphrase)
                    .whenComplete((ignored, passwordError) -> Platform.runLater(() -> {
                        if (passwordError != null) {
                            Arrays.fill(sourcePassphrase, '\0');
                            setStatus(messageFor(passwordError, com.ynpe.fastremote.i18n.Texts.text("legacy.java.e476ad1e44b0c1db")),
                                    StatusTone.ERROR);
                            return;
                        }
                        CompletionStage<Boolean> ready = info.hasImportableSecrets()
                                ? ensureVaultForCatalogImport(rootPane.getScene().getWindow())
                                : CompletableFuture.completedFuture(true);
                        ready.whenComplete((vaultReady, vaultError) -> Platform.runLater(() -> {
                            if (vaultError != null || !vaultReady) {
                                Arrays.fill(sourcePassphrase, '\0');
                                setStatus(vaultError == null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.08d7a98d788a6892")
                                                : messageFor(vaultError, com.ynpe.fastremote.i18n.Texts.text("legacy.java.059bddc7c451ca81")),
                                        vaultError == null ? StatusTone.IDLE : StatusTone.ERROR);
                                return;
                            }
                            runSecureCrtImport(source, sourcePassphrase);
                        }));
                    }));
        }));
    }

    private Optional<char[]> requestSecureCrtPassphrase() {
        Dialog<char[]> dialog = new Dialog<>();
        dialog.initOwner(rootPane.getScene().getWindow());
        dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6108a81acec22767"));
        dialog.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e26a39713a2bf0cd"));
        DialogSpacing.prepare(dialog);
        RevealablePasswordEditor password = new RevealablePasswordEditor(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8c678eff20c8634f"));
        password.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.18045982c8388cac"));
        Label note = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.341b5eb7aef2fa31"));
        note.setWrapText(true);
        note.getStyleClass().add("inline-security-note");
        VBox content = new VBox(8.0, new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.27b1877927b50c0e")), password, note);
        content.setPrefWidth(430.0);
        dialog.getDialogPane().setContent(content);
        ButtonType proceed = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.64ded447c5bdd6ea"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, proceed);
        dialog.setResultConverter(button -> button == proceed ? password.getText().toCharArray() : null);
        Optional<char[]> result = dialog.showAndWait();
        password.clear();
        return result;
    }

    private void runSecureCrtImport(Path source, char[] sourcePassphrase) {
        setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2c9b4b06e2a234d3"), StatusTone.WORKING);
        catalogWorkflow.importSecureCrtArchive(source, sourcePassphrase)
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.e81ad3cd781db78b")), StatusTone.ERROR);
                    } else {
                        setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2a9010137d7ba142") + result.hosts() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.068921c4e871de7b") + result.groups()
                                + com.ynpe.fastremote.i18n.Texts.text("legacy.java.8ce746df80a7dc29") + result.credentials() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.41db51d7cd30c142"), StatusTone.IDLE);
                        refreshCatalog();
                    }
                }));
        Arrays.fill(sourcePassphrase, '\0');
    }

    private boolean confirmCatalogIdOverwrite(HostCatalogArchiveInfo info) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(rootPane.getScene().getWindow());
        confirmation.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ea262387b62f6a13"));
        confirmation.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.10ebb0b8b693c917"));
        confirmation.setContentText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f743fca36426b222")
                + com.ynpe.fastremote.i18n.Texts.text("legacy.java.4a37e2cfee5240a2") + info.groupIdConflicts() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.239da6016154dee7") + info.hostIdConflicts()
                + com.ynpe.fastremote.i18n.Texts.text("legacy.java.3592c1bba789a17e") + info.credentialIdConflicts() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.30e9d2ad955a5c94")
                + com.ynpe.fastremote.i18n.Texts.text("legacy.java.7e701c2c429ebf6b"));
        ButtonType overwrite = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a0e322d6cc7c5fb2"), ButtonBar.ButtonData.OK_DONE);
        confirmation.getButtonTypes().setAll(ButtonType.CANCEL, overwrite);
        return confirmation.showAndWait().orElse(ButtonType.CANCEL) == overwrite;
    }

    private void runCatalogImport(Path source, char[] sourcePassword) {
        setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.bd1ac1c7e962271f"), StatusTone.WORKING);
        catalogWorkflow.importCatalog(source, sourcePassword).whenComplete((result, error) -> Platform.runLater(() -> {
            if (error != null) {
                setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.6fc445d20ef3cc7f")), StatusTone.ERROR);
            } else {
                setStatus(localizer.text("config.import.done"), StatusTone.IDLE);
                refreshCatalog();
                refreshSnippets();
                reloadProgramConfiguration();
            }
        }));
        Arrays.fill(sourcePassword, '\0');
    }

    private Optional<char[]> requestArchiveMasterPassword() {
        Dialog<char[]> dialog = new Dialog<>();
        dialog.initOwner(rootPane.getScene().getWindow());
        dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d7e03a0d3c530279"));
        dialog.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.91c51d7ea04ad525"));
        DialogSpacing.prepare(dialog);
        RevealablePasswordEditor password = new RevealablePasswordEditor(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c5ecc321f53eff25"));
        password.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c3df0d909c9e45ff"));
        Label note = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.179c08ac785d8369"));
        note.setWrapText(true);
        note.getStyleClass().add("inline-security-note");
        dialog.getDialogPane().setContent(new VBox(8.0, new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f36f5f11edefb912")), password, note));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        Node confirm = dialog.getDialogPane().lookupButton(ButtonType.OK);
        confirm.addEventFilter(ActionEvent.ACTION, event -> {
            if (password.getText().isEmpty()) {
                event.consume();
                FormValidation.focus(password);
            }
        });
        dialog.setResultConverter(button -> button == ButtonType.OK ? password.getText().toCharArray() : null);
        Optional<char[]> result = dialog.showAndWait();
        password.clear();
        return result;
    }

    private CompletionStage<Boolean> ensureVaultForCatalogImport(Window owner) {
        return switch (catalog.vaultStatus()) {
            case UNLOCKED -> CompletableFuture.completedFuture(true);
            case NOT_CREATED -> requestVaultCreation(owner,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.3e269d447ecae36f"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.d304a1e6df759dd2"));
            case LOCKED -> requestLockedVault(owner,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.c2ac344691485bbb"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.d53faa1e0431c338"));
        };
    }

    @FXML
    private void manageVault() {
        manageVault(rootPane.getScene().getWindow());
    }

    private void manageVault(Window owner) {
        catalogWorkflow.loadCatalog("").whenComplete((snapshot, error) -> Platform.runLater(() -> {
            if (error != null) {
                setStatus(messageFor(unwrap(error), com.ynpe.fastremote.i18n.Texts.text("legacy.java.635803a7fd2d8154")), StatusTone.ERROR);
                return;
            }
            catalog = snapshot;
            updateVaultStatus(snapshot.vaultStatus());
            showVaultManager(owner, snapshot.vaultStatus());
        }));
    }

    private void showVaultManager(Window owner, VaultStatus currentStatus) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(masterPasswordManagerTitle());
        DialogSpacing.prepare(dialog);
        dialog.setHeaderText(null);
        String state = switch (currentStatus) {
            case NOT_CREATED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.53b650d6606ef8b9");
            case LOCKED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.56cee9095850d2fa");
            case UNLOCKED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.e4cf2de9b47ffd6e");
        };
        Label status = new Label(state);
        status.getStyleClass().add(currentStatus == VaultStatus.UNLOCKED
                ? "vault-state-unlocked" : "vault-state-locked");
        Label encryption = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d374e57da32f4cbe"));
        encryption.setWrapText(true);
        encryption.setMaxWidth(Double.MAX_VALUE);
        encryption.getStyleClass().add("component-help");
        Label note = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.97862e889aed0c75"));
        note.setWrapText(true);
        note.setMaxWidth(Double.MAX_VALUE);
        note.getStyleClass().add("inline-security-note");
        Label rememberedState = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.dfc1ca17e24bdf6d"));
        rememberedState.setWrapText(true);
        rememberedState.setMaxWidth(Double.MAX_VALUE);
        rememberedState.getStyleClass().add("component-help");
        Button rememberAction = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1cfb7d3492fd1eca"));
        rememberAction.setDisable(true);
        Button changePassword = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.41c964f19c974372"));
        changePassword.setDisable(currentStatus == VaultStatus.NOT_CREATED);
        boolean[] remembered = {false};
        catalogWorkflow.rememberedVaultUnlockConfigured().whenComplete((configured, error) ->
                Platform.runLater(() -> {
                    if (error != null) {
                        rememberedState.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4da0335f4856f83e"));
                    } else {
                        remembered[0] = Boolean.TRUE.equals(configured);
                        rememberedState.setText(remembered[0]
                                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.b6484bca44b8b638")
                                : com.ynpe.fastremote.i18n.Texts.text("legacy.java.0c7015cdd29cca44"));
                        rememberAction.setText(rememberedUnlockActionLabel(remembered[0]));
                        rememberAction.setDisable(currentStatus == VaultStatus.NOT_CREATED);
                    }
                }));
        javafx.scene.layout.FlowPane management = new javafx.scene.layout.FlowPane(8.0, 8.0,
                changePassword, rememberAction);
        Label heading = wrappingVaultLabel(com.ynpe.fastremote.i18n.Texts.text("legacy.java.56ab9b541d6514af"),
                "vault-dialog-heading");
        Label contents = wrappingVaultLabel(com.ynpe.fastremote.i18n.Texts.text("legacy.java.9b802e5ecb40a9d4"),
                "component-help");
        VBox content = new VBox(12.0,
                heading, new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.82b7cf40b5e63d33")), status, encryption, new Separator(),
                new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2f38e74b0968d92e")), rememberedState, management, new Separator(),
                new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c3a49c0a189f4aea")), contents, note);
        content.getStyleClass().add("vault-dialog-content");
        constrainVaultFormWidth(content, 500.0);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(VAULT_DIALOG_WIDTH);
        dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        ButtonType action = new ButtonType(switch (currentStatus) {
            case NOT_CREATED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.8153411bcdb9595c");
            case LOCKED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.70fba17fffb72f1a");
            case UNLOCKED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.44fe1de7b6ef51c0");
        }, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE, action);
        ButtonType changePasswordAction = new ButtonType(changePassword.getText());
        ButtonType rememberUnlockAction = new ButtonType(rememberAction.getText());
        changePassword.setOnAction(event -> dialog.setResult(changePasswordAction));
        rememberAction.setOnAction(event -> dialog.setResult(rememberUnlockAction));
        // Let the manager's nested event loop finish before opening another modal dialog.
        // Opening it inside the closing button handler stalls macOS pulses and queued focus work.
        dialog.showAndWait().ifPresent(selected -> {
            if (selected == action) {
                performVaultAction(owner);
            } else if (selected == changePasswordAction) {
                showChangeMasterPasswordDialog(owner);
            } else if (selected == rememberUnlockAction) {
                if (remembered[0]) clearRememberedUnlock(owner);
                else changeRememberedUnlock(owner, true);
            }
        });
    }

    static String rememberedUnlockActionLabel(boolean remembered) {
        return remembered ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.084670b30fa83d6b") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.1cfb7d3492fd1eca");
    }

    static String masterPasswordManagerTitle() {
        return com.ynpe.fastremote.i18n.Texts.text("legacy.java.f36f5f11edefb912");
    }

    private static void constrainVaultFormWidth(VBox content, double width) {
        content.setMinWidth(0.0);
        content.setPrefWidth(width);
        content.setMaxWidth(width);
        for (Node child : content.getChildren()) {
            if (child instanceof javafx.scene.control.Labeled text) {
                text.setWrapText(true);
                text.setMinWidth(0.0);
                text.setMaxWidth(Double.MAX_VALUE);
                text.setMinHeight(Region.USE_PREF_SIZE);
            }
        }
    }

    private static Label wrappingVaultLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private void changeRememberedUnlock(Window owner, boolean remember) {
        if (remember) {
            Alert warning = new Alert(Alert.AlertType.WARNING);
            warning.initOwner(owner);
            warning.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ba8f85edd9c9c256"));
            warning.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d9204041eb6e3465"));
            warning.setContentText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5f219e62f9fc148e")
                    + com.ynpe.fastremote.i18n.Texts.text("legacy.java.d0f1d336aa204d05"));
            if (warning.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            requestCurrentPassword(owner, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d16f3745f5ef4979"), password ->
                    updateRememberedUnlock(true, password));
            return;
        }
        clearRememberedUnlock(owner);
    }

    private void clearRememberedUnlock(Window owner) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.084670b30fa83d6b"));
        dialog.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7dcc9dbdaa5f030f"));
        DialogSpacing.prepare(dialog);

        RevealablePasswordEditor password = new RevealablePasswordEditor(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5ee7b0789fb7ea3f"));
        password.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.76c09aa82d2b539d"));
        Label explanation = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.440c0c6fd9768ea6")
                + com.ynpe.fastremote.i18n.Texts.text("legacy.java.ad4d94d2667ede5c"));
        explanation.setWrapText(true);
        explanation.setPrefWidth(520.0);
        explanation.setMaxWidth(Double.MAX_VALUE);
        explanation.getStyleClass().add("inline-security-note");
        Label validation = new Label();
        validation.setWrapText(true);
        validation.setVisible(false);
        validation.setManaged(false);
        validation.getStyleClass().add("vault-password-validation-error");
        VBox content = new VBox(10.0,
                new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5ee7b0789fb7ea3f")), password, validation, explanation);
        content.getStyleClass().add("vault-password-dialog-content");
        constrainVaultFormWidth(content, 520.0);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(600.0);
        dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        Runnable resizeToContent = () -> Platform.runLater(() -> {
            if (dialog.getDialogPane().getScene() != null) {
                dialog.getDialogPane().getScene().getWindow().sizeToScene();
            }
        });
        password.textProperty().addListener((ignored, previous, value) -> {
            if (!validation.isVisible()) return;
            validation.setText("");
            validation.setVisible(false);
            validation.setManaged(false);
            resizeToContent.run();
        });

        ButtonType clear = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6e5b891a196a0e28"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, clear);
        dialog.setResultConverter(DialogResultPolicy.explicitOnly());
        Button clearButton = (Button) dialog.getDialogPane().lookupButton(clear);
        clearButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (password.getText().isEmpty()) {
                validation.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ef5269daabbfd99a"));
                validation.setVisible(true);
                validation.setManaged(true);
                password.focusInput();
                resizeToContent.run();
                return;
            }

            char[] entered = password.getText().toCharArray();
            validation.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.256cdfccb2d4884c"));
            validation.setVisible(true);
            validation.setManaged(true);
            clearButton.setDisable(true);
            resizeToContent.run();
            CompletionStage<Void> operation = catalogWorkflow.setRememberedVaultUnlock(false, entered);
            Arrays.fill(entered, '\0');
            operation.whenComplete((ignored, error) -> Platform.runLater(() -> {
                clearButton.setDisable(false);
                if (error != null) {
                    validation.setText(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.db98d27ac63146ba")));
                    password.focusInput();
                    resizeToContent.run();
                    return;
                }
                password.clear();
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.516686a709b39494"), StatusTone.IDLE);
                dialog.close();
            }));
        });
        dialog.setOnHidden(event -> password.clear());
        dialog.show();
        Platform.runLater(password::focusInput);
    }

    private void requestCurrentPassword(Window owner, String title, java.util.function.Consumer<char[]> action) {
        Dialog<char[]> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(title);
        dialog.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ff16c7847a26c1fe"));
        DialogSpacing.prepare(dialog);
        PasswordField password = new PasswordField();
        password.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5ee7b0789fb7ea3f"));
        dialog.getDialogPane().setContent(new VBox(8.0, new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f36f5f11edefb912")), password));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.setResultConverter(button -> button == ButtonType.OK ? password.getText().toCharArray() : null);
        dialog.showAndWait().ifPresent(action);
    }

    private void updateRememberedUnlock(boolean remember, char[] password) {
        setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5fc7b9377e1ff3d1"), StatusTone.WORKING);
        CompletionStage<Void> operation = catalogWorkflow.setRememberedVaultUnlock(remember, password);
        Arrays.fill(password, '\0');
        operation.whenComplete((ignored, error) ->
                Platform.runLater(() -> {
                    if (error != null) {
                        setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.772da2dc1c26a00e")), StatusTone.ERROR);
                    } else {
                        setStatus(remember
                                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.60cadfdeaaae9bff")
                                : com.ynpe.fastremote.i18n.Texts.text("legacy.java.516686a709b39494"), StatusTone.IDLE);
                    }
                }));
    }

    private void showChangeMasterPasswordDialog(Window owner) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.41c964f19c974372"));
        dialog.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.63226af8b56336ce"));
        DialogSpacing.prepare(dialog);
        PasswordField current = new PasswordField();
        PasswordField replacement = new PasswordField();
        PasswordField confirmation = new PasswordField();
        current.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5ee7b0789fb7ea3f"));
        replacement.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d20553030ce5e732"));
        confirmation.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.598941f23ed0219b"));
        CheckBox remember = new CheckBox(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0e2703a730f9a204"));
        Label explanation = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0709ff5d982b6821"));
        explanation.setWrapText(true);
        explanation.getStyleClass().add("component-help");
        Label currentLabel = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5ee7b0789fb7ea3f"));
        Label validation = new Label();
        validation.setWrapText(true);
        validation.setMinHeight(Region.USE_PREF_SIZE);
        validation.getStyleClass().add("vault-password-validation-error");
        validation.setVisible(false);
        validation.setManaged(false);
        VBox content = new VBox(8.0, currentLabel, current,
                new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1cdcd2c700d05acf")), replacement,
                new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3024a2967dba66bc")), confirmation,
                remember, explanation, validation);
        constrainVaultFormWidth(content, 440.0);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(500.0);
        dialog.getDialogPane().setMinWidth(500.0);
        dialog.getDialogPane().setMaxWidth(500.0);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        Node confirm = dialog.getDialogPane().lookupButton(ButtonType.OK);
        confirm.setDisable(true);
        boolean[] remembered = {false};
        java.util.function.Consumer<String> showError = message -> {
            validation.setText(message);
            validation.setVisible(true);
            validation.setManaged(true);
            Platform.runLater(() -> {
                if (dialog.isShowing()) dialog.getDialogPane().getScene().getWindow().sizeToScene();
            });
        };
        catalogWorkflow.rememberedVaultUnlockConfigured().whenComplete((saved, error) -> Platform.runLater(() -> {
            if (!dialog.isShowing()) return;
            if (error != null) {
                showError.accept(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.4da0335f4856f83e")));
                return;
            }
            remembered[0] = Boolean.TRUE.equals(saved);
            current.setVisible(!remembered[0]);
            current.setManaged(!remembered[0]);
            currentLabel.setVisible(!remembered[0]);
            currentLabel.setManaged(!remembered[0]);
            if (remembered[0]) dialog.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("vault.change.remembered"));
            confirm.setDisable(false);
            FormValidation.focus(remembered[0] ? replacement : current);
            dialog.getDialogPane().getScene().getWindow().sizeToScene();
        }));
        confirm.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (!remembered[0] && current.getText().isEmpty()) {
                showError.accept(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7ef4c5ef71fc45be"));
                FormValidation.focus(current);
                return;
            }
            Optional<String> invalid = validateVaultPasswordInput(true, replacement.getText(), confirmation.getText());
            if (invalid.isPresent()) {
                showError.accept(invalid.get());
                FormValidation.focus(replacement.getText().length() < MIN_MASTER_PASSWORD_LENGTH ? replacement : confirmation);
                return;
            }
            char[] oldPassword = remembered[0] ? new char[0] : current.getText().toCharArray();
            char[] newPassword = replacement.getText().toCharArray();
            content.setDisable(true);
            confirm.setDisable(true);
            dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
            CompletionStage<Void> operation = catalogWorkflow.changeVaultMasterPassword(oldPassword, newPassword, remember.isSelected());
            Arrays.fill(oldPassword, '\0');
            Arrays.fill(newPassword, '\0');
            operation.whenComplete((ignored, error) -> Platform.runLater(() -> {
                content.setDisable(false);
                confirm.setDisable(false);
                dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(false);
                if (error != null) {
                    showError.accept(exportVaultVerificationMessage(error));
                    FormValidation.focus(remembered[0] ? replacement : current);
                    return;
                }
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6ff9fed089f4f2e2"), StatusTone.IDLE);
                dialog.close();
                refreshCatalog();
            }));
        });
        dialog.setOnHidden(event -> {
            current.clear();
            replacement.clear();
            confirmation.clear();
        });
        dialog.show();
    }

    private void performVaultAction(Window owner) {
        switch (catalog.vaultStatus()) {
            case NOT_CREATED -> requestVaultPassword(true, owner);
            case LOCKED -> requestVaultPassword(false, owner);
            case UNLOCKED -> {
                catalogWorkflow.lockVault();
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f738a2030da268b6"), StatusTone.IDLE);
                refreshCatalog();
            }
        }
    }

    private CompletionStage<ResolvedCredential> readSavedCredential(Window owner, SecretId id) {
        java.util.function.Supplier<CompletionStage<ResolvedCredential>> read = () -> {
            Optional<GlobalCredential> reusable = catalog.credentials().stream()
                    .filter(credential -> credential.id().equals(id)).findFirst();
            if (reusable.isPresent()) return catalogWorkflow.loadGlobalCredential(reusable.orElseThrow());
            Optional<SavedHost> host = catalog.hosts().stream()
                    .filter(value -> value.credentialSecretId().filter(id::equals).isPresent()).findFirst();
            if (host.isPresent()) return catalogWorkflow.loadCredential(host.orElseThrow());
            return CompletableFuture.failedFuture(new IllegalStateException("Stored credential no longer exists"));
        };
        return SavedCredentialAccess.read(read, () -> owner.isShowing()
                ? requestLockedVault(owner, com.ynpe.fastremote.i18n.Texts.text("legacy.java.0115c8e48216f11f"),
                        com.ynpe.fastremote.i18n.Texts.text("legacy.java.166b3d09c68b5342"))
                : CompletableFuture.completedFuture(false));
    }

    private CompletionStage<Boolean> requestVaultUnlockForHostSave(Window owner) {
        return switch (sessionSaveVaultAction(catalog.vaultStatus())) {
            case NONE -> CompletableFuture.completedFuture(true);
            case CREATE -> requestVaultCreationForHostSave(owner);
            case UNLOCK -> requestLockedVaultForHostSave(owner);
        };
    }

    static HostSaveVaultAction sessionSaveVaultAction(VaultStatus status) {
        return switch (Objects.requireNonNull(status, "status")) {
            case NOT_CREATED -> HostSaveVaultAction.CREATE;
            case LOCKED -> HostSaveVaultAction.UNLOCK;
            case UNLOCKED -> HostSaveVaultAction.NONE;
        };
    }

    private CompletionStage<Boolean> requestVaultCreationForHostSave(Window owner) {
        return requestVaultCreation(owner,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.1fea0c1d8698ae8a"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.b5380d365a12747e"));
    }

    private CompletionStage<Boolean> requestVaultCreation(Window owner, String header, String actionText) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0a72ed1550a7591d"));
        dialog.setHeaderText(header);
        DialogSpacing.prepare(dialog);

        RevealablePasswordEditor password = new RevealablePasswordEditor(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0b85d0b4ff1d201e"));
        password.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d20553030ce5e732"));
        RevealablePasswordEditor confirmation = new RevealablePasswordEditor(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b40068806626ba53"));
        confirmation.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.decc928ee46e152c"));
        Label validation = new Label();
        validation.setWrapText(true);
        validation.setVisible(false);
        validation.setManaged(false);
        validation.getStyleClass().add("vault-password-validation-error");
        Label warning = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6f45fcc9a3e2000c"));
        warning.setWrapText(true);
        warning.getStyleClass().add("inline-security-note");
        VBox content = new VBox(8.0,
                new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f36f5f11edefb912")), password,
                new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.12aaa082bc80f929")), confirmation,
                validation, warning);
        content.getStyleClass().add("vault-password-dialog-content");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        VaultDialogFeedback.configure(dialog, validation, VAULT_DIALOG_WIDTH);

        ButtonType create = new ButtonType(actionText, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, create);
        dialog.setResultConverter(DialogResultPolicy.explicitOnly());
        Button createButton = (Button) dialog.getDialogPane().lookupButton(create);
        createButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            Optional<String> validationError = validateVaultPasswordInput(
                    true, password.getText(), confirmation.getText());
            if (validationError.isPresent()) {
                validation.setText(validationError.get());
                validation.setVisible(true);
                validation.setManaged(true);
                FormValidation.focus(password.getText().length() < MIN_MASTER_PASSWORD_LENGTH ? password : confirmation);
                return;
            }

            char[] entered = password.getText().toCharArray();
            password.clear();
            confirmation.clear();
            validation.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.9d55d046c14e66d6"));
            validation.setVisible(true);
            validation.setManaged(true);
            createButton.setDisable(true);
            catalogWorkflow.createVault(entered, false).whenComplete((ignored, failure) -> Platform.runLater(() -> {
                createButton.setDisable(false);
                if (failure != null) {
                    validation.setText(messageFor(failure, com.ynpe.fastremote.i18n.Texts.text("legacy.java.e3be342a00ce6bee")));
                    FormValidation.focus(password);
                    return;
                }
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3c83bcc704588401"), StatusTone.IDLE);
                refreshCatalog();
                result.complete(true);
                dialog.close();
            }));
            Arrays.fill(entered, '\0');
        });
        dialog.setOnHidden(event -> {
            password.clear();
            confirmation.clear();
            result.complete(false);
        });
        dialog.show();
        Platform.runLater(password::requestFocus);
        return result;
    }

    private CompletionStage<Boolean> requestLockedVaultForHostSave(Window owner) {
        return requestLockedVault(owner,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.b19efa3b87c5095a"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.166b3d09c68b5342"));
    }

    private CompletionStage<Boolean> requestLockedVault(Window owner, String header, String actionText) {

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.86e913c3ed764b1b"));
        dialog.setHeaderText(header);
        DialogSpacing.prepare(dialog);

        RevealablePasswordEditor password = new RevealablePasswordEditor(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0b85d0b4ff1d201e"));
        password.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.97d360eeac3e62a1"));
        Label validation = new Label();
        validation.setWrapText(true);
        validation.setVisible(false);
        validation.setManaged(false);
        validation.getStyleClass().add("vault-password-validation-error");
        VBox content = new VBox(8.0, new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f36f5f11edefb912")), password, validation);
        content.getStyleClass().add("vault-password-dialog-content");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        VaultDialogFeedback.configure(dialog, validation, VAULT_DIALOG_WIDTH);

        ButtonType unlock = new ButtonType(actionText, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, unlock);
        dialog.setResultConverter(DialogResultPolicy.explicitOnly());
        Button unlockButton = (Button) dialog.getDialogPane().lookupButton(unlock);
        unlockButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (password.getText().isEmpty()) {
                validation.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7ef4c5ef71fc45be"));
                validation.setVisible(true);
                validation.setManaged(true);
                FormValidation.focus(password);
                return;
            }

            char[] entered = password.getText().toCharArray();
            password.clear();
            validation.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f797426ff1ccb86e"));
            validation.setVisible(true);
            validation.setManaged(true);
            unlockButton.setDisable(true);
            catalogWorkflow.unlockVault(entered).whenComplete((ignored, failure) -> Platform.runLater(() -> {
                unlockButton.setDisable(false);
                if (failure != null) {
                    validation.setText(vaultUnlockFailureMessage(failure));
                    FormValidation.focus(password);
                    return;
                }
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ab5882582347d3d9"), StatusTone.IDLE);
                refreshCatalog();
                result.complete(true);
                dialog.close();
            }));
            Arrays.fill(entered, '\0');
        });
        dialog.setOnHidden(event -> {
            password.clear();
            result.complete(false);
        });
        dialog.show();
        Platform.runLater(password::requestFocus);
        return result;
    }

    enum HostSaveVaultAction {
        CREATE,
        UNLOCK,
        NONE
    }

    private void requestVaultPassword(boolean create) {
        requestVaultPassword(create, rootPane.getScene().getWindow());
    }

    private void requestVaultPassword(boolean create, Window owner) {
        Dialog<VaultPasswordRequest> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(create ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.0a72ed1550a7591d") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.86e913c3ed764b1b"));
        String headingText = create
                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.9b09b8b03db77a36")
                : com.ynpe.fastremote.i18n.Texts.text("legacy.java.0115c8e48216f11f");
        dialog.setHeaderText(null);
        DialogSpacing.prepare(dialog);
        PasswordField password = new PasswordField();
        password.setPromptText(create ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.d20553030ce5e732") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.f36f5f11edefb912"));
        password.getStyleClass().add("vault-master-password-input");
        PasswordField confirmation = new PasswordField();
        confirmation.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.decc928ee46e152c"));
        confirmation.getStyleClass().add("vault-master-password-input");
        TextField visiblePassword = new TextField();
        visiblePassword.setPromptText(password.getPromptText());
        visiblePassword.getStyleClass().addAll("vault-master-password-input", "vault-visible-password-input");
        visiblePassword.setManaged(false);
        visiblePassword.setVisible(false);
        visiblePassword.textProperty().bindBidirectional(password.textProperty());
        TextField visibleConfirmation = new TextField();
        visibleConfirmation.setPromptText(confirmation.getPromptText());
        visibleConfirmation.getStyleClass().addAll(
                "vault-master-password-input", "vault-visible-password-input");
        visibleConfirmation.setManaged(false);
        visibleConfirmation.setVisible(false);
        visibleConfirmation.textProperty().bindBidirectional(confirmation.textProperty());

        Button revealPassword = vaultIconButton(
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.cda877c642c146c2"),
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.cda877c642c146c2"),
                "M1,6 C2.8,2.7 5.2,1 8,1 C10.8,1 13.2,2.7 15,6 C13.2,9.3 10.8,11 8,11 C5.2,11 2.8,9.3 1,6 Z "
                        + "M8,3.5 A2.5,2.5 0 1,0 8,8.5 A2.5,2.5 0 1,0 8,3.5 Z");
        Button revealConfirmation = vaultIconButton(
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.cda877c642c146c2"),
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.cda877c642c146c2"),
                "M1,6 C2.8,2.7 5.2,1 8,1 C10.8,1 13.2,2.7 15,6 C13.2,9.3 10.8,11 8,11 C5.2,11 2.8,9.3 1,6 Z "
                        + "M8,3.5 A2.5,2.5 0 1,0 8,8.5 A2.5,2.5 0 1,0 8,3.5 Z");
        boolean[] passwordRevealed = {false};
        java.util.function.Consumer<Boolean> setPasswordRevealed = reveal -> {
            passwordRevealed[0] = reveal;
            visiblePassword.setVisible(reveal);
            visiblePassword.setManaged(reveal);
            password.setVisible(!reveal);
            password.setManaged(!reveal);
            visibleConfirmation.setVisible(create && reveal);
            visibleConfirmation.setManaged(create && reveal);
            confirmation.setVisible(!create || !reveal);
            confirmation.setManaged(!create || !reveal);
            updateVaultVisibilityButton(revealPassword, reveal);
            updateVaultVisibilityButton(revealConfirmation, reveal);
        };
        revealPassword.setOnAction(event -> {
            setPasswordRevealed.accept(!passwordRevealed[0]);
            TextField activeInput = passwordRevealed[0] ? visiblePassword : password;
            FormValidation.focus(activeInput);
            activeInput.positionCaret(password.getText().length());
        });
        revealConfirmation.setOnAction(event -> {
            setPasswordRevealed.accept(!passwordRevealed[0]);
            TextField activeInput = passwordRevealed[0] ? visibleConfirmation : confirmation;
            FormValidation.focus(activeInput);
            activeInput.positionCaret(confirmation.getText().length());
        });

        HBox passwordActions = new HBox(2.0);
        passwordActions.setAlignment(Pos.CENTER_RIGHT);
        passwordActions.setPickOnBounds(false);
        passwordActions.getStyleClass().add("vault-password-actions");
        if (create) {
            Button generatePassword = vaultIconButton(
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.8427730fea8512a6"),
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.8427730fea8512a6"),
                    "M8,0.5 L9.35,4.65 L13.5,6 L9.35,7.35 L8,11.5 L6.65,7.35 L2.5,6 "
                            + "L6.65,4.65 Z M2,9 L2.65,10.35 L4,11 L2.65,11.65 L2,13 L1.35,11.65 "
                            + "L0,11 L1.35,10.35 Z");
            generatePassword.setOnAction(event -> {
                String generated = generateRandomMasterPassword(PASSWORD_RANDOM);
                password.setText(generated);
                confirmation.setText(generated);
                setPasswordRevealed.accept(true);
                FormValidation.focus(visiblePassword);
                visiblePassword.positionCaret(generated.length());
            });
            passwordActions.getChildren().add(generatePassword);
        }
        passwordActions.getChildren().add(revealPassword);

        StackPane passwordInput = new StackPane(password, visiblePassword, passwordActions);
        passwordInput.getStyleClass().add("vault-password-editor");
        StackPane.setAlignment(passwordActions, Pos.CENTER_RIGHT);

        Label heading = wrappingVaultLabel(headingText, "vault-dialog-heading");
        VBox content = new VBox(8.0, heading,
                new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f36f5f11edefb912")), passwordInput);
        content.getStyleClass().add("vault-password-dialog-content");
        javafx.scene.control.CheckBox rememberUnlock = new javafx.scene.control.CheckBox(
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.4197d4a7f083737a"));
        rememberUnlock.setWrapText(true);
        rememberUnlock.setMaxWidth(Double.MAX_VALUE);
        Label clipboardNotice = new Label(
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.62f576019f2fc125")
        );
        clipboardNotice.setWrapText(true);
        clipboardNotice.setVisible(false);
        clipboardNotice.setManaged(false);
        clipboardNotice.getStyleClass().add("vault-password-copy-notice");
        if (create) {
            Button copyConfirmation = vaultIconButton(
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.5ab634060589f64c"),
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.5ab634060589f64c"),
                    "M4,1 L12,1 C13.1,1 14,1.9 14,3 L14,11 L12,11 L12,3 L4,3 Z "
                            + "M1,4 L10,4 C11.1,4 12,4.9 12,6 L12,14 L3,14 C1.9,14 1,13.1 1,12 Z");
            copyConfirmation.setOnAction(event -> {
                ClipboardContent clipboardContent = new ClipboardContent();
                clipboardContent.putString(confirmation.getText());
                Clipboard.getSystemClipboard().setContent(clipboardContent);
                clipboardNotice.setVisible(true);
                clipboardNotice.setManaged(true);
                TextField activeInput = passwordRevealed[0] ? visibleConfirmation : confirmation;
                FormValidation.focus(activeInput);
                activeInput.positionCaret(confirmation.getText().length());
                Platform.runLater(() -> {
                    if (dialog.getDialogPane().getScene() != null) {
                        dialog.getDialogPane().getScene().getWindow().sizeToScene();
                    }
                });
            });
            HBox confirmationActions = new HBox(2.0, copyConfirmation, revealConfirmation);
            confirmationActions.setAlignment(Pos.CENTER_RIGHT);
            confirmationActions.setPickOnBounds(false);
            confirmationActions.getStyleClass().add("vault-password-actions");
            StackPane confirmationInput = new StackPane(
                    confirmation, visibleConfirmation, confirmationActions);
            confirmationInput.getStyleClass().add("vault-password-editor");
            StackPane.setAlignment(confirmationActions, Pos.CENTER_RIGHT);
            Label recoveryWarning = wrappingVaultLabel(
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.c7403272abbacd60"), "component-help");
            content.getChildren().addAll(new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.12aaa082bc80f929")), confirmationInput,
                    rememberUnlock, recoveryWarning,
                    clipboardNotice);
        } else {
            content.getChildren().add(rememberUnlock);
        }
        Label rememberWarning = new Label(
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.998b904f90c8bbd3")
        );
        rememberWarning.setWrapText(true);
        rememberWarning.getStyleClass().add("inline-security-note");
        rememberWarning.visibleProperty().bind(rememberUnlock.selectedProperty());
        rememberWarning.managedProperty().bind(rememberWarning.visibleProperty());
        content.getChildren().add(rememberWarning);

        Label validationMessage = new Label();
        validationMessage.setWrapText(true);
        validationMessage.setVisible(false);
        validationMessage.setManaged(false);
        validationMessage.getStyleClass().add("vault-password-validation-error");
        content.getChildren().add(create ? 4 : 2, validationMessage);
        Runnable clearValidation = () -> {
            validationMessage.setText("");
            validationMessage.setVisible(false);
            validationMessage.setManaged(false);
        };
        Runnable clearClipboardNotice = () -> {
            clipboardNotice.setVisible(false);
            clipboardNotice.setManaged(false);
        };
        password.textProperty().addListener((ignored, previous, value) -> {
            clearValidation.run();
            clearClipboardNotice.run();
        });
        confirmation.textProperty().addListener((ignored, previous, value) -> {
            clearValidation.run();
            clearClipboardNotice.run();
        });

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(VAULT_DIALOG_WIDTH);
        dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        rememberUnlock.selectedProperty().addListener((ignored, previous, selected) -> Platform.runLater(() -> {
            if (dialog.getDialogPane().getScene() != null) {
                dialog.getDialogPane().getScene().getWindow().sizeToScene();
            }
        }));
        Node confirmButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        confirmButton.addEventFilter(ActionEvent.ACTION, event -> {
            Optional<String> validationError = validateVaultPasswordInput(
                    create, password.getText(), confirmation.getText());
            if (validationError.isEmpty()) {
                return;
            }
            event.consume();
            validationMessage.setText(validationError.get());
            validationMessage.setVisible(true);
            validationMessage.setManaged(true);
            if (create && !password.getText().isBlank()
                    && password.getText().length() >= 10
                    && !password.getText().equals(confirmation.getText())) {
                FormValidation.focus(passwordRevealed[0] ? visibleConfirmation : confirmation);
            } else {
                FormValidation.focus(passwordRevealed[0] ? visiblePassword : password);
            }
            Platform.runLater(() -> {
                if (dialog.getDialogPane().getScene() != null) {
                    dialog.getDialogPane().getScene().getWindow().sizeToScene();
                }
            });
        });
        dialog.setResultConverter(button -> {
            return button == ButtonType.OK
                    ? new VaultPasswordRequest(password.getText().toCharArray(), rememberUnlock.isSelected())
                    : null;
        });
        dialog.showAndWait().ifPresent(request -> {
            setStatus(create ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.3bd9de7cd48f837b") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.defb14185966a64e"), StatusTone.WORKING);
            char[] passwordValue = request.password();
            java.util.concurrent.CompletionStage<Void> operation;
            if (create) {
                operation = catalogWorkflow.createVault(passwordValue, request.rememberUnlock());
            } else {
                char[] rememberCopy = passwordValue.clone();
                operation = catalogWorkflow.unlockVault(passwordValue).thenCompose(ignored ->
                        catalogWorkflow.setRememberedVaultUnlock(request.rememberUnlock(), rememberCopy));
                operation.whenComplete((ignored, error) -> Arrays.fill(rememberCopy, '\0'));
            }
            operation.whenComplete((ignored, error) -> Platform.runLater(() -> {
                if (error != null) {
                    setStatus(messageFor(error, create ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.c29aa9439d91f253") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.13a0e28518f4e5b4")), StatusTone.ERROR);
                } else {
                    setStatus(create ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.b4207edf137ad610") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.ab5882582347d3d9"), StatusTone.IDLE);
                    refreshCatalog();
                }
            }));
        });
    }

    static Optional<String> validateVaultPasswordInput(boolean create, String password, String confirmation) {
        String safePassword = Objects.requireNonNull(password, "password");
        String safeConfirmation = Objects.requireNonNull(confirmation, "confirmation");
        if (safePassword.isEmpty()) {
            return Optional.of(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7ef4c5ef71fc45be"));
        }
        if (create && safePassword.length() < MIN_MASTER_PASSWORD_LENGTH) {
            return Optional.of(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e79a5e16bbb7cc97"));
        }
        if (create && !safePassword.equals(safeConfirmation)) {
            return Optional.of(com.ynpe.fastremote.i18n.Texts.text("legacy.java.61153d5c7dd3da51"));
        }
        return Optional.empty();
    }

    private static Button vaultIconButton(String accessibleText, String tooltipText, String path) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.getStyleClass().add("vault-password-action-icon");
        Button button = new Button();
        button.setAccessibleText(accessibleText);
        button.setTooltip(new Tooltip(tooltipText));
        button.setGraphic(icon);
        button.getStyleClass().add("vault-password-action-button");
        return button;
    }

    private static void updateVaultVisibilityButton(Button button, boolean revealed) {
        String action = revealed ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.eb26c751d7379736") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.cda877c642c146c2");
        button.setAccessibleText(action);
        button.setTooltip(new Tooltip(action));
    }

    static String generateRandomMasterPassword(SecureRandom random) {
        Objects.requireNonNull(random, "random");
        char[] generated = new char[12];
        generated[0] = PASSWORD_LOWERCASE[random.nextInt(PASSWORD_LOWERCASE.length)];
        generated[1] = PASSWORD_UPPERCASE[random.nextInt(PASSWORD_UPPERCASE.length)];
        generated[2] = PASSWORD_DIGITS[random.nextInt(PASSWORD_DIGITS.length)];
        for (int index = 3; index < generated.length; index++) {
            generated[index] = PASSWORD_ALPHANUMERIC[random.nextInt(PASSWORD_ALPHANUMERIC.length)];
        }
        for (int index = generated.length - 1; index > 0; index--) {
            int swapIndex = random.nextInt(index + 1);
            char value = generated[index];
            generated[index] = generated[swapIndex];
            generated[swapIndex] = value;
        }
        return new String(generated);
    }

    private void connectSelectedHost() {
        connectTarget(hostTree.getSelectionModel().getSelectedItem(), hostItems)
                .ifPresent(this::connectHost);
    }

    static Optional<SavedHost> connectTarget(
            TreeItem<String> selected, Map<TreeItem<String>, SavedHost> hosts) {
        return Optional.ofNullable(selected).map(hosts::get);
    }

    private void connectHost(SavedHost host) {
        connectHost(host, WorkspaceType.TERMINAL);
    }

    private void connectHost(SavedHost host, WorkspaceType initialType) {
        connectHost(host, initialType, java.util.UUID.randomUUID());
    }

    private void connectHost(
            SavedHost host, WorkspaceType initialType, java.util.UUID pairingId) {
        connectHost(host, initialType, pairingId, null);
    }

    private void connectHost(
            SavedHost host, WorkspaceType initialType, java.util.UUID pairingId,
            String initialRemotePath) {
        connectHost(host, initialType, pairingId, initialRemotePath, false, true);
    }

    private void connectHost(
            SavedHost host, WorkspaceType initialType, java.util.UUID pairingId,
            String initialRemotePath, boolean automationOwned, boolean activateTab) {
        if (host == null) return;
        requireLicense(initialType == WorkspaceType.SFTP
                        ? com.ynpe.fastremote.domain.license.LicenseCapability.SFTP
                        : com.ynpe.fastremote.domain.license.LicenseCapability.SSH,
                () -> connectAuthorizedHost(host, initialType, pairingId, initialRemotePath,
                        automationOwned, activateTab), null);
    }

    private void connectAuthorizedHost(
            SavedHost host, WorkspaceType initialType, java.util.UUID pairingId,
            String initialRemotePath, boolean automationOwned, boolean activateTab) {
        if (host == null) return;
        if (host.jumpHostId().isPresent()) {
            connectRoutedHost(host, initialType, pairingId, initialRemotePath,
                    automationOwned, activateTab);
            return;
        }
        if (requiresConnectionPrompt(host)) {
            promptForHostCredential(host, initialType, pairingId, initialRemotePath,
                    automationOwned, activateTab);
            return;
        }
        promptedCredentialHost = null;
        updateAuthenticationFields();
        if (catalog.vaultStatus() != VaultStatus.UNLOCKED) {
            setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b6301740b2de3ab5"), StatusTone.ERROR);
            manageVault();
            return;
        }
        OpenSession pending = createPendingSession(
                host, initialType, pairingId, automationOwned, activateTab);
        applyInitialRemotePath(pending, initialRemotePath);
        pending.bookmarkKey = host.id().toString();
        logConnection(pending, com.ynpe.fastremote.i18n.Texts.text("legacy.java.29a3b65d81074f5f"), StatusTone.WORKING);
        catalogWorkflow.loadCredential(host).whenComplete((credential, error) -> Platform.runLater(() -> {
            if (error != null) {
                failBeforeConnection(pending, sshSftpFailureMessage(
                        error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.0d730ec882776c29")));
            } else {
                try {
                    startConnection(pending, credential);
                } catch (RuntimeException failure) {
                    credential.close();
                    failBeforeConnection(pending, sshSftpFailureMessage(
                            failure, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d3ff6836a7c50037")));
                }
            }
        }));
    }

    private void connectRoutedHost(
            SavedHost target, WorkspaceType initialType, java.util.UUID pairingId,
            String initialRemotePath, boolean automationOwned, boolean activateTab) {
        List<SavedHost> route;
        try {
            route = savedConnectionRoute(target);
        } catch (IllegalArgumentException invalid) {
            setStatus(com.ynpe.fastremote.i18n.Texts.translateExternalText(invalid.getMessage()), StatusTone.ERROR);
            return;
        }
        boolean needsVault = route.stream().anyMatch(hop -> hop.credentialSecretId().isPresent());
        if (needsVault && catalog.vaultStatus() != VaultStatus.UNLOCKED) {
            setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8ea64aeab640765b"), StatusTone.ERROR);
            manageVault();
            return;
        }
        OpenSession pending = createPendingSession(
                target, initialType, pairingId, automationOwned, activateTab);
        applyInitialRemotePath(pending, initialRemotePath);
        pending.bookmarkKey = target.id().toString();
        logConnection(pending, com.ynpe.fastremote.i18n.Texts.text("legacy.java.7d08c704a0377453") + (route.size() - 1) + com.ynpe.fastremote.i18n.Texts.text("legacy.java.d9d48395f59ff4d9"), StatusTone.WORKING);
        resolveConnectionRoute(route, 0, new ArrayList<>(), pending);
    }

    private List<SavedHost> savedConnectionRoute(SavedHost target) {
        Map<com.ynpe.fastremote.domain.catalog.HostId, SavedHost> byId = catalog.hosts().stream()
                .collect(java.util.stream.Collectors.toMap(SavedHost::id, java.util.function.Function.identity()));
        List<SavedHost> reverse = new ArrayList<>();
        java.util.Set<com.ynpe.fastremote.domain.catalog.HostId> visited = new java.util.HashSet<>();
        SavedHost current = target;
        while (true) {
            if (!visited.add(current.id())) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.aed4b72375fe23c6"));
            reverse.add(current);
            if (reverse.size() > 6) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1d017366e7b41936"));
            if (current.jumpHostId().isEmpty()) break;
            current = Optional.ofNullable(byId.get(current.jumpHostId().orElseThrow()))
                    .orElseThrow(() -> new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f8a9699d1897dbd7")));
        }
        java.util.Collections.reverse(reverse);
        return List.copyOf(reverse);
    }

    private void resolveConnectionRoute(
            List<SavedHost> route, int index,
            List<com.ynpe.fastremote.domain.connection.ConnectionHop> resolved,
            OpenSession pending) {
        if (pending.closed.get()) {
            closeConnectionHops(resolved);
            return;
        }
        if (index >= route.size()) {
            startRoutedConnection(pending, resolved);
            return;
        }
        SavedHost hop = route.get(index);
        if (hop.credentialSecretId().isPresent()) {
            logConnection(pending, com.ynpe.fastremote.i18n.Texts.text("legacy.java.2d6cf92052dda3f8") + hop.name(), StatusTone.WORKING);
            catalogWorkflow.loadCredential(hop).whenComplete((credential, error) -> Platform.runLater(() -> {
                if (error != null) {
                    closeConnectionHops(resolved);
                    failBeforeConnection(pending, sshSftpFailureMessage(error,
                            com.ynpe.fastremote.i18n.Texts.text("legacy.java.6ecad61dbf3dcfdd") + hop.name()));
                    return;
                }
                try {
                    resolved.add(new com.ynpe.fastremote.domain.connection.ConnectionHop(
                            hop.name(), hop.profile(), domainCredentials(hop.profile(), credential)));
                    resolveConnectionRoute(route, index + 1, resolved, pending);
                } catch (RuntimeException failure) {
                    credential.close();
                    closeConnectionHops(resolved);
                    failBeforeConnection(pending, sshSftpFailureMessage(
                            failure, com.ynpe.fastremote.i18n.Texts.text("legacy.java.6b505a6529ffb7d2")));
                }
            }));
            return;
        }
        Optional<com.ynpe.fastremote.domain.connection.Credentials> prompted = promptRouteCredential(hop);
        if (prompted.isEmpty()) {
            closeConnectionHops(resolved);
            failBeforeConnection(pending, com.ynpe.fastremote.i18n.Texts.text("legacy.java.8954389e809163f8"));
            return;
        }
        resolved.add(new com.ynpe.fastremote.domain.connection.ConnectionHop(
                hop.name(), hop.profile(), prompted.orElseThrow()));
        resolveConnectionRoute(route, index + 1, resolved, pending);
    }

    private Optional<com.ynpe.fastremote.domain.connection.Credentials> promptRouteCredential(SavedHost hop) {
        return promptRouteCredential(hop, rootPane.getScene().getWindow());
    }

    private Optional<com.ynpe.fastremote.domain.connection.Credentials> promptRouteCredential(
            SavedHost hop, Window owner) {
        boolean password = hop.profile().authenticationType() == AuthenticationType.PASSWORD;
        Dialog<char[]> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3e73f55c94e9d7ea"));
        dialog.setHeaderText(hop.name() + " · " + hop.profile().username().value() + '@'
                + hop.profile().host().value());
        DialogSpacing.prepare(dialog);
        RevealablePasswordEditor secret = new RevealablePasswordEditor(
                password ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.7be1419614f3508d") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.d2ec1829b67bbe98"));
        secret.setPromptText(password ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.5fe8ca081bd504e3") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.e1ad47762150065a"));
        Label validation = new Label();
        validation.getStyleClass().add("vault-password-validation-error");
        validation.setManaged(false);
        validation.setVisible(false);
        dialog.getDialogPane().setContent(new VBox(8.0,
                new Label(password ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.94a36a5efcb1d9f0") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.31f5d63536390b45")), secret, validation));
        ButtonType connect = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f2d9802583259c6e"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, connect);
        dialog.setResultConverter(button -> button == connect ? secret.getText().toCharArray() : null);
        Button action = (Button) dialog.getDialogPane().lookupButton(connect);
        action.addEventFilter(ActionEvent.ACTION, event -> {
            if (password && secret.getText().isEmpty()) {
                event.consume();
                validation.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f79c2da2bb2c6440"));
                validation.setManaged(true);
                validation.setVisible(true);
                secret.focusInput();
            }
        });
        Optional<char[]> entered = dialog.showAndWait();
        secret.clear();
        if (entered.isEmpty()) return Optional.empty();
        char[] value = entered.orElseThrow();
        try {
            return Optional.of(password
                    ? new com.ynpe.fastremote.domain.connection.PasswordCredentials(value)
                    : new com.ynpe.fastremote.domain.connection.PrivateKeyCredentials(value));
        } finally {
            Arrays.fill(value, '\0');
        }
    }

    private static com.ynpe.fastremote.domain.connection.Credentials domainCredentials(
            ConnectionProfile profile, ResolvedCredential resolved) {
        try {
            if (profile.authenticationType() != resolved.authenticationType()) {
                throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.92544709551f0d3c"));
            }
            char[] secret = resolved.copySecret();
            byte[] key = resolved.copyPrivateKey();
            try {
                return profile.authenticationType() == AuthenticationType.PASSWORD
                        ? new com.ynpe.fastremote.domain.connection.PasswordCredentials(secret)
                        : new com.ynpe.fastremote.domain.connection.PrivateKeyCredentials(secret, key);
            } finally {
                Arrays.fill(secret, '\0');
                if (key != null) Arrays.fill(key, (byte) 0);
            }
        } finally {
            resolved.close();
        }
    }

    private void startRoutedConnection(
            OpenSession session, List<com.ynpe.fastremote.domain.connection.ConnectionHop> route) {
        try {
            if (session.initialType == WorkspaceType.SFTP) {
                SftpConnectionAttempt attempt = workflow.connectSftp(
                        route, this::confirmHostKey,
                        progress -> Platform.runLater(() -> connectionProgress(session, progress)));
                session.cancelAttempt = attempt::cancel;
                attempt.result().whenComplete((sftp, error) -> Platform.runLater(() ->
                        completeSftpConnection(session, sftp, error)));
                return;
            }
            ConnectionAttempt attempt = workflow.connect(
                    route, this::confirmHostKey,
                    progress -> Platform.runLater(() -> connectionProgress(session, progress)));
            session.attempt = attempt;
            session.cancelAttempt = attempt::cancel;
            attempt.result().whenComplete((terminal, error) -> Platform.runLater(() ->
                    completeConnection(session, terminal, error)));
        } catch (RuntimeException failure) {
            closeConnectionHops(route);
            failBeforeConnection(session, connectionFailureMessage(failure));
        }
    }

    private static void closeConnectionHops(List<com.ynpe.fastremote.domain.connection.ConnectionHop> route) {
        route.forEach(hop -> hop.credentials().close());
    }

    private void promptForHostCredential(
            SavedHost host, WorkspaceType initialType, java.util.UUID pairingId,
            String initialRemotePath, boolean automationOwned, boolean activateTab) {
        promptedCredentialHost = null;
        if (pendingConnectionCredential != null) {
            pendingConnectionCredential.close();
            pendingConnectionCredential = null;
        }
        updateAuthenticationFields();
        ConnectionProfile profile = host.profile();
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(rootPane.getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b8fcd08d84425c66"));
        dialog.setHeaderText(host.name() + " · " + profile.username().value() + '@' + profile.host().value());
        DialogSpacing.prepare(dialog);

        boolean passwordAuthentication = profile.authenticationType() == AuthenticationType.PASSWORD;
        RevealablePasswordEditor secret = new RevealablePasswordEditor(
                passwordAuthentication ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.7be1419614f3508d") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.d2ec1829b67bbe98"));
        secret.setPromptText(passwordAuthentication ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.5fe8ca081bd504e3") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.e1ad47762150065a"));
        javafx.scene.control.CheckBox saveSecret = new javafx.scene.control.CheckBox(
                passwordAuthentication ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.324c2cdce8abf64a") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.2131599e51de7dae"));
        Label validation = new Label();
        validation.setWrapText(true);
        validation.setVisible(false);
        validation.setManaged(false);
        validation.getStyleClass().add("vault-password-validation-error");
        VBox content = new VBox(8.0,
                new Label(passwordAuthentication ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.94a36a5efcb1d9f0") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.31f5d63536390b45")),
                secret,
                saveSecret,
                validation);
        content.getStyleClass().add("vault-password-dialog-content");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        ButtonType connect = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a5574109f0208e89"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, connect);
        dialog.setResultConverter(DialogResultPolicy.explicitOnly());
        Button connectButton = (Button) dialog.getDialogPane().lookupButton(connect);
        boolean[] vaultAvailable = {catalog.vaultStatus() == VaultStatus.UNLOCKED};
        connectButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (passwordAuthentication && secret.getText().isEmpty()) {
                validation.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f79c2da2bb2c6440"));
                validation.setVisible(true);
                validation.setManaged(true);
                secret.focusInput();
                return;
            }
            if (saveSecret.isSelected() && !vaultAvailable[0]) {
                validation.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f48396b206bbef50"));
                validation.setVisible(true);
                validation.setManaged(true);
                connectButton.setDisable(true);
                requestVaultUnlockForHostSave(dialog.getDialogPane().getScene().getWindow())
                        .whenComplete((unlocked, failure) -> Platform.runLater(() -> {
                            connectButton.setDisable(false);
                            if (failure != null) {
                                validation.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.39c18bfa643c9aa7"));
                            } else if (!Boolean.TRUE.equals(unlocked)) {
                                validation.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.67da459981f3c52b"));
                            } else {
                                vaultAvailable[0] = true;
                                validation.setText("");
                                validation.setVisible(false);
                                validation.setManaged(false);
                                connectButton.fire();
                            }
                        }));
                return;
            }

            char[] entered = secret.getText().toCharArray();
            try {
                if (pendingConnectionCredential != null) {
                    pendingConnectionCredential.close();
                    pendingConnectionCredential = null;
                }
                promptedCredentialHost = saveSecret.isSelected() ? host : null;
                if (saveSecret.isSelected()) {
                    pendingConnectionCredential = new CredentialDraft(
                            profile.authenticationType(), entered, profile.privateKeyPath());
                }
                secret.clear();
                dialog.close();
                pendingSftpBookmarkKey = host.id().toString();
                OpenSession pending = createPendingSession(
                        host, initialType, pairingId, automationOwned, activateTab);
                applyInitialRemotePath(pending, initialRemotePath);
                startTemporaryConnection(pending, entered, null);
            } catch (RuntimeException failure) {
                Arrays.fill(entered, '\0');
                if (pendingConnectionCredential != null) {
                    pendingConnectionCredential.close();
                    pendingConnectionCredential = null;
                }
                promptedCredentialHost = null;
                validation.setText(messageFor(failure, com.ynpe.fastremote.i18n.Texts.text("legacy.java.ad9a0ddc85f4cd8f")));
                validation.setVisible(true);
                validation.setManaged(true);
            }
        });
        dialog.setOnHidden(event -> secret.clear());
        setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c0aa82f80407ea92") + host.name() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.95e2149b985f07fc"), StatusTone.IDLE);
        dialog.show();
        Platform.runLater(secret::focusInput);
    }

    static boolean requiresConnectionPrompt(SavedHost host) {
        return Objects.requireNonNull(host, "host").credentialSecretId().isEmpty();
    }

    private Optional<GroupId> selectedGroupId() {
        TreeItem<String> selected = hostTree.getSelectionModel().getSelectedItem();
        HostGroup group = groupItems.get(selected);
        if (group != null) {
            return Optional.of(group.id());
        }
        SavedHost host = hostItems.get(selected);
        return host == null ? Optional.empty() : host.groupId();
    }

    private void updateVaultStatus(VaultStatus status) {
        vaultActionIcon.setIcon(vaultToolbarIcon(status));
        switch (status) {
            case NOT_CREATED -> {
                vaultStatusLabel.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a6bf6a830f977430"));
            }
            case LOCKED -> {
                vaultStatusLabel.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c9518406474e22e0"));
            }
            case UNLOCKED -> {
                vaultStatusLabel.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.aea60ca55ead2400"));
            }
        }
    }

    @FXML
    private void connect() {
        connect(WorkspaceType.TERMINAL, message -> ConnectionFeedback.show(
                quickConnectionTestStatus, message, ConnectionFeedback.Tone.ERROR));
    }

    private void connect(WorkspaceType initialType) {
        connect(initialType, message -> setStatus(message, StatusTone.ERROR));
    }

    private void connect(WorkspaceType initialType, Consumer<String> validationFailure) {
        connect(initialType, validationFailure, java.util.UUID.randomUUID(), null);
    }

    private void connect(
            WorkspaceType initialType, Consumer<String> validationFailure,
            java.util.UUID pairingId, String initialRemotePath) {
        requireLicense(initialType == WorkspaceType.SFTP
                        ? com.ynpe.fastremote.domain.license.LicenseCapability.SFTP
                        : com.ynpe.fastremote.domain.license.LicenseCapability.SSH,
                () -> connectAuthorized(initialType, validationFailure, pairingId, initialRemotePath), null);
    }

    private void connectAuthorized(
            WorkspaceType initialType, Consumer<String> validationFailure,
            java.util.UUID pairingId, String initialRemotePath) {
        try {
            ConnectionProfile profile = profileFromFields();
            Optional<com.ynpe.fastremote.domain.catalog.HostId> jumpId = quickJumpHostId();
            GlobalCredential selectedCredential = selectedQuickCredential();
            if (selectedCredential != null) {
                if (catalog.vaultStatus() != VaultStatus.UNLOCKED) {
                    setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.26e0d62e09c62659"), StatusTone.ERROR);
                    requestVaultPassword(false);
                    return;
                }
                OpenSession session = createPendingSession("", profile, initialType, pairingId);
                applyInitialRemotePath(session, initialRemotePath);
                logConnection(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.29a3b65d81074f5f"), StatusTone.WORKING);
                catalogWorkflow.loadGlobalCredential(selectedCredential)
                        .whenComplete((secret, error) -> Platform.runLater(() -> {
                            if (error != null) {
                                failBeforeConnection(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.fbdae73221a140d4"));
                            } else if (jumpId.isPresent()) {
                                startQuickRoutedConnection(session, jumpId.orElseThrow(),
                                        domainCredentials(profile, secret));
                            } else {
                                startTemporaryConnection(session, secret);
                            }
                        }));
                return;
            }
            char[] secret = copyQuickSecret(authenticationField.getValue(), hostPasswordField.getText(),
                    privateKeyPassphraseField.getText());
            OpenSession session = createPendingSession("", profile, initialType, pairingId);
            applyInitialRemotePath(session, initialRemotePath);
            if (jumpId.isPresent()) {
                com.ynpe.fastremote.domain.connection.Credentials targetCredentials;
                try {
                    targetCredentials = profile.authenticationType() == AuthenticationType.PASSWORD
                            ? new PasswordCredentials(secret) : new PrivateKeyCredentials(secret);
                } finally {
                    Arrays.fill(secret, '\0');
                }
                startQuickRoutedConnection(session, jumpId.orElseThrow(), targetCredentials);
            } else {
                startTemporaryConnection(session, secret, null);
            }
        } catch (IllegalArgumentException failure) {
            validationFailure.accept(com.ynpe.fastremote.i18n.Texts.translateExternalText(failure.getMessage()));
        }
    }

    private void startQuickRoutedConnection(
            OpenSession pending,
            com.ynpe.fastremote.domain.catalog.HostId jumpId,
            com.ynpe.fastremote.domain.connection.Credentials targetCredentials) {
        SavedHost jump = catalog.hosts().stream().filter(item -> item.id().equals(jumpId)).findFirst()
                .orElse(null);
        if (jump == null) {
            targetCredentials.close();
            failBeforeConnection(pending, com.ynpe.fastremote.i18n.Texts.text("legacy.java.dc700998a9739b73"));
            return;
        }
        List<SavedHost> route;
        try {
            route = savedConnectionRoute(jump);
        } catch (IllegalArgumentException invalid) {
            targetCredentials.close();
            failBeforeConnection(pending, com.ynpe.fastremote.i18n.Texts.translateExternalText(invalid.getMessage()));
            return;
        }
        if (route.stream().anyMatch(item -> item.credentialSecretId().isPresent())
                && catalog.vaultStatus() != VaultStatus.UNLOCKED) {
            targetCredentials.close();
            failBeforeConnection(pending, com.ynpe.fastremote.i18n.Texts.text("legacy.java.8ea64aeab640765b"));
            manageVault();
            return;
        }
        com.ynpe.fastremote.domain.connection.ConnectionHop target =
                new com.ynpe.fastremote.domain.connection.ConnectionHop(
                        pending.name.isBlank() ? pending.profile.host().value() : pending.name,
                        pending.profile, targetCredentials);
        resolveQuickJumpRoute(route, 0, new ArrayList<>(), pending, target);
    }

    private void resolveQuickJumpRoute(
            List<SavedHost> route, int index,
            List<com.ynpe.fastremote.domain.connection.ConnectionHop> resolved,
            OpenSession pending,
            com.ynpe.fastremote.domain.connection.ConnectionHop target) {
        if (pending.closed.get()) {
            closeConnectionHops(resolved);
            target.credentials().close();
            return;
        }
        if (index >= route.size()) {
            resolved.add(target);
            startRoutedConnection(pending, resolved);
            return;
        }
        SavedHost hop = route.get(index);
        if (hop.credentialSecretId().isPresent()) {
            catalogWorkflow.loadCredential(hop).whenComplete((credential, error) -> Platform.runLater(() -> {
                if (error != null) {
                    closeConnectionHops(resolved);
                    target.credentials().close();
                    failBeforeConnection(pending, sshSftpFailureMessage(error,
                            com.ynpe.fastremote.i18n.Texts.text("legacy.java.b15de8e8f5896e2b") + hop.name()));
                    return;
                }
                try {
                    resolved.add(new com.ynpe.fastremote.domain.connection.ConnectionHop(
                            hop.name(), hop.profile(), domainCredentials(hop.profile(), credential)));
                    resolveQuickJumpRoute(route, index + 1, resolved, pending, target);
                } catch (RuntimeException failure) {
                    closeConnectionHops(resolved);
                    target.credentials().close();
                    failBeforeConnection(pending, sshSftpFailureMessage(
                            failure, com.ynpe.fastremote.i18n.Texts.text("legacy.java.6b505a6529ffb7d2")));
                }
            }));
            return;
        }
        Optional<com.ynpe.fastremote.domain.connection.Credentials> prompted = promptRouteCredential(hop);
        if (prompted.isEmpty()) {
            closeConnectionHops(resolved);
            target.credentials().close();
            failBeforeConnection(pending, com.ynpe.fastremote.i18n.Texts.text("legacy.java.8954389e809163f8"));
            return;
        }
        resolved.add(new com.ynpe.fastremote.domain.connection.ConnectionHop(
                hop.name(), hop.profile(), prompted.orElseThrow()));
        resolveQuickJumpRoute(route, index + 1, resolved, pending, target);
    }

    private ConnectionProfile profileFromFields() {
        AuthenticationType authentication = authenticationField.getValue();
        GlobalCredential selectedCredential = selectedQuickCredential();
        if (manualQuickFieldsDisabled(credentialSourceField.getValue()) && selectedCredential == null) {
            throw new IllegalArgumentException(quickCredentialSelector.isEmpty()
                    ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.7b9903e626187f28") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.45496152d757090a"));
        }
        boolean embeddedKey = selectedCredential != null && selectedCredential.embeddedPrivateKey();
        Optional<Path> keyPath = authentication == AuthenticationType.PRIVATE_KEY && !embeddedKey
                ? Optional.of(Path.of(requireText(privateKeyField, com.ynpe.fastremote.i18n.Texts.text("legacy.java.776eaa6963f26389"))))
                : Optional.empty();
        return new ConnectionProfile(
                new Host(requireText(hostField, com.ynpe.fastremote.i18n.Texts.text("legacy.java.0d2f997c185f913d"))),
                new Port(parsePort()),
                new Username(requireText(usernameField, com.ynpe.fastremote.i18n.Texts.text("legacy.java.c723b1fab58f5dbc"))),
                authentication,
                keyPath,
                legacyAlgorithmsField.isSelected());
    }

    private HostEditorDialog.ConnectionTestRequest quickConnectionTestRequest(
            TextField host,
            TextField port,
            TextField username,
            AuthenticationType authentication,
            QuickCredentialSource source,
            GlobalCredential selectedCredential,
            String hostPassword,
            String privateKey,
            String privateKeyPassphrase,
            boolean legacyAlgorithms,
            Optional<com.ynpe.fastremote.domain.catalog.HostId> jumpHostId) {
        Objects.requireNonNull(authentication, "authentication");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(jumpHostId, "jumpHostId");
        if (source == QuickCredentialSource.LIBRARY && selectedCredential == null) {
            throw new IllegalArgumentException(catalog.credentials().isEmpty()
                    ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.7b9903e626187f28") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.45496152d757090a"));
        }
        GlobalCredential credential = source == QuickCredentialSource.LIBRARY ? selectedCredential : null;
        boolean embeddedKey = credential != null && credential.embeddedPrivateKey();
        Optional<Path> keyPath = authentication == AuthenticationType.PRIVATE_KEY && !embeddedKey
                ? Optional.of(Path.of(requiredQuickValue(privateKey, com.ynpe.fastremote.i18n.Texts.text("legacy.java.776eaa6963f26389"))))
                : Optional.empty();
        ConnectionProfile profile = new ConnectionProfile(
                new Host(requiredQuickValue(host.getText(), com.ynpe.fastremote.i18n.Texts.text("legacy.java.0d2f997c185f913d"))),
                new Port(parseQuickPort(port.getText())),
                new Username(requiredQuickValue(username.getText(), com.ynpe.fastremote.i18n.Texts.text("legacy.java.c723b1fab58f5dbc"))),
                authentication, keyPath, legacyAlgorithms);
        HostEditorDialog.validateJumpSelection(Optional.empty(), jumpHostId, catalog.hosts());
        if (credential != null) {
            return new HostEditorDialog.ConnectionTestRequest(
                    profile, jumpHostId, Optional.of(credential.id()), Optional.empty());
        }
        if (authentication == AuthenticationType.PASSWORD && hostPassword.isEmpty()) {
            throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3fb7c6e8eada2289"));
        }
        char[] entered = quickSecret(authentication, hostPassword, privateKeyPassphrase).toCharArray();
        CredentialDraft draft;
        try {
            draft = new CredentialDraft(authentication, entered, keyPath);
        } finally {
            Arrays.fill(entered, '\0');
        }
        try {
            return new HostEditorDialog.ConnectionTestRequest(
                    profile, jumpHostId, Optional.empty(), Optional.of(draft));
        } catch (RuntimeException failure) {
            draft.close();
            throw failure;
        }
    }

    static int parseQuickPort(String value) {
        try {
            int parsed = Integer.parseInt(requiredQuickValue(value, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d1192e27c3d45da3")));
            if (parsed < 1 || parsed > 65535) throw new NumberFormatException("out of range");
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.584c7ad845c11aa4"), invalid);
        }
    }

    private static String requiredQuickValue(String value, String message) {
        String normalized = Objects.requireNonNull(value, "value").strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException(message);
        return normalized;
    }

    private void startConnection(OpenSession session, ResolvedCredential resolved) {
        ConnectionProfile profile = session.profile;
        if (profile.authenticationType() != resolved.authenticationType()) {
            resolved.close();
            throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.12cc89f2f050eec3"));
        }
        char[] secret = resolved.copySecret();
        byte[] privateKey = resolved.copyPrivateKey();
        resolved.close();
        startConnection(session, secret, privateKey);
    }

    private void startConnection(
            ConnectionProfile profile, char[] secret, String hostName, WorkspaceType initialType) {
        startConnection(createPendingSession(hostName, profile, initialType), secret, null);
    }

    private OpenSession createPendingSession(
            String hostName, ConnectionProfile profile, WorkspaceType initialType) {
        return createPendingSession(hostName, profile, initialType, java.util.UUID.randomUUID());
    }

    private OpenSession createPendingSession(
            String hostName, ConnectionProfile profile, WorkspaceType initialType,
            java.util.UUID pairingId) {
        return createPendingSession(hostName, profile, initialType, pairingId, null);
    }

    private OpenSession createPendingSession(
            String hostName, ConnectionProfile profile, WorkspaceType initialType,
            java.util.UUID pairingId, com.ynpe.fastremote.domain.catalog.HostId catalogHostId) {
        return createPendingSession(hostName, profile, initialType, pairingId, catalogHostId,
                false, true);
    }

    private OpenSession createPendingSession(
            String hostName, ConnectionProfile profile, WorkspaceType initialType,
            java.util.UUID pairingId, com.ynpe.fastremote.domain.catalog.HostId catalogHostId,
            boolean automationOwned, boolean activateTab) {
        OpenSession session = new OpenSession(hostName, profile, initialType, pairingId);
        session.catalogHostId = catalogHostId;
        session.automationOwned = automationOwned;
        session.automationActivateTab = activateTab;
        session.insertionAnchor = pendingWorkspaceInsertionAnchors.remove(pairingId);
        session.pendingRemoteSelectionPath = pendingRemoteFileSelections.remove(pairingId);
        if (catalogHostId != null) {
            session.bookmarkKey = catalogHostId.toString();
            pendingSftpBookmarkKey = null;
        } else if (pendingSftpBookmarkKey != null) {
            session.bookmarkKey = pendingSftpBookmarkKey;
            pendingSftpBookmarkKey = null;
        } else if (hostName != null && !hostName.isBlank()) {
            catalog.hosts().stream()
                    .filter(saved -> saved.name().equals(hostName) && saved.profile().equals(profile))
                    .findFirst().ifPresent(saved -> session.bookmarkKey = saved.id().toString());
        }
        if (pendingConnectionCredential != null && promptedCredentialHost != null) {
            session.pendingCredential = pendingConnectionCredential;
            session.credentialOwner = promptedCredentialHost;
            pendingConnectionCredential = null;
            promptedCredentialHost = null;
        }
        logConnection(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.66af5915cc757675"), StatusTone.WORKING);
        logConnection(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.90c5a3f32652beda"), StatusTone.WORKING);
        openWorkspaceTab(session, initialType);
        updateActions();
        return session;
    }

    private OpenSession createPendingSession(SavedHost saved, WorkspaceType initialType) {
        return createPendingSession(saved, initialType, java.util.UUID.randomUUID());
    }

    private OpenSession createPendingSession(
            SavedHost saved, WorkspaceType initialType, java.util.UUID pairingId) {
        return createPendingSession(saved.name(), saved.profile(), initialType, pairingId, saved.id());
    }

    private OpenSession createPendingSession(
            SavedHost saved, WorkspaceType initialType, java.util.UUID pairingId,
            boolean automationOwned, boolean activateTab) {
        return createPendingSession(saved.name(), saved.profile(), initialType, pairingId, saved.id(),
                automationOwned, activateTab);
    }

    private void startTemporaryConnection(OpenSession session, ResolvedCredential resolved) {
        TemporaryConnectionCredential credential = TemporaryConnectionCredential.from(resolved);
        session.temporaryCredential = credential;
        try {
            startConnection(session, resolved);
        } catch (RuntimeException failure) {
            credential.close();
            session.temporaryCredential = null;
            throw failure;
        }
    }

    private void startTemporaryConnection(OpenSession session, char[] secret, byte[] privateKey) {
        TemporaryConnectionCredential credential = new TemporaryConnectionCredential(secret, privateKey);
        session.temporaryCredential = credential;
        try {
            startConnection(session, secret, privateKey);
        } catch (RuntimeException failure) {
            credential.close();
            session.temporaryCredential = null;
            throw failure;
        }
    }

    private void startConnection(OpenSession session, char[] secret, byte[] privateKey) {
        ConnectionProfile profile = session.profile;
        com.ynpe.fastremote.domain.connection.Credentials credentials;
        try {
            if (profile.authenticationType() == AuthenticationType.PASSWORD && secret.length == 0) {
                throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.728a7b601c56bdf8"));
            }
            credentials = profile.authenticationType() == AuthenticationType.PASSWORD
                    ? new PasswordCredentials(secret)
                    : new PrivateKeyCredentials(secret, privateKey);
        } finally {
            Arrays.fill(secret, '\0');
            if (privateKey != null) Arrays.fill(privateKey, (byte) 0);
        }
        updateActions();
        try {
            if (session.initialType == WorkspaceType.SFTP) {
                SftpConnectionAttempt sftpAttempt = workflow.connectSftp(profile, credentials, this::confirmHostKey,
                        progress -> Platform.runLater(() -> connectionProgress(session, progress)));
                session.cancelAttempt = sftpAttempt::cancel;
                sftpAttempt.result().whenComplete((sftp, error) -> Platform.runLater(() -> completeSftpConnection(session, sftp, error)));
                return;
            }
            ConnectionAttempt attempt = workflow.connect(
                    profile,
                    credentials,
                    this::confirmHostKey,
                    progress -> Platform.runLater(() -> connectionProgress(session, progress)));
            session.attempt = attempt;
            session.cancelAttempt = attempt::cancel;
            attempt.result().whenComplete((terminal, error) ->
                    Platform.runLater(() -> completeConnection(session, terminal, error)));
        } catch (RuntimeException failure) {
            credentials.close();
            throw failure;
        }
    }

    private void failBeforeConnection(OpenSession session, String message) {
        if (session.closed.get()) return;
        session.state = ConnectionState.DISCONNECTED;
        session.failureMessage = message;
        logConnection(session, message, StatusTone.ERROR);
        if (session.connectedOnce) showRetainedWorkspace(session, false);
        else refreshSessionTabs(session);
        updateActions();
    }

    @FXML
    private void cancelConnection() {
        OpenSession session = openSessions.get(selectedWorkspaceTab());
        cancelConnection(session);
    }

    private void cancelConnection(OpenSession session) {
        if (session == null || session.closed.get() || session.state != ConnectionState.CONNECTING
                || session.cancelAttempt == null) return;
        session.cancelAttempt.run();
        setSessionStatus(statusModule(session), session,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.9ed4a180625f15b9"), StatusTone.WORKING);
    }

    @FXML
    private void disconnect() {
        OpenSession selected = openSessions.get(selectedWorkspaceTab());
        if (selected != null && selected.state == ConnectionState.CONNECTING) {
            cancelConnection();
            return;
        }
        closeSession(selectedWorkspaceTab(), com.ynpe.fastremote.i18n.Texts.text("legacy.java.7727668c83e96ea6"));
    }

    @FXML
    private void reconnectSelectedHost() {
        OpenSession selected = openSessions.get(selectedWorkspaceTab());
        if (selected != null) requestReconnect(selected);
    }

    static boolean canReconnectDisconnectedWorkspace(OpenSession session) {
        return session != null && !session.closed.get() && session.connectedOnce
                && session.state == ConnectionState.DISCONNECTED;
    }

    @FXML
    private void closeSelectedSession() {
        if (!openSessions.isEmpty()) {
            disconnect();
        } else {
            setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b0d87bf40a7adad2"), StatusTone.IDLE);
        }
    }

    @FXML
    private void toggleLeftDock() {
        if (!hostSidebarVisible && selectedLeftTool == null) {
            selectedLeftTool = firstToolOn(DockSide.LEFT, persistedWorkspaceSettings).orElse(null);
            if (selectedLeftTool == null) {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f53ab2c9970291ec"), StatusTone.IDLE);
                return;
            }
            installToolWindow(DockSide.LEFT, selectedLeftTool);
        }
        boolean reveal = !hostSidebarVisible;
        setHostSidebarVisible(reveal);
        if (reveal && selectedLeftTool != null) requestToolWindowInitialFocus(selectedLeftTool);
    }

    @FXML
    private void toggleRightDock() {
        if (!rightDockVisible && selectedRightTool == null) {
            selectedRightTool = firstToolOn(DockSide.RIGHT, persistedWorkspaceSettings).orElse(null);
            if (selectedRightTool == null) {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d4f21fdc27fb9e1e"), StatusTone.IDLE);
                return;
            }
            installToolWindow(DockSide.RIGHT, selectedRightTool);
        }
        boolean reveal = !rightDockVisible;
        setRightDockVisible(reveal);
        if (reveal && selectedRightTool != null) requestToolWindowInitialFocus(selectedRightTool);
    }

    private void setHostSidebarVisible(boolean visible) {
        if (visible == hostSidebarVisible) {
            return;
        }
        if (visible && selectedLeftTool == ToolWindow.AI_ASSISTANT) aiAssistantPageController.collapseSessions();
        hostSidebar.setCursor(javafx.scene.Cursor.DEFAULT);
        boolean embedded = horizontalWorkbenchSplit.getItems().contains(hostSidebar);
        boolean floating = workbenchOverlayHost.getChildren().contains(hostSidebar);
        if (!visible && (embedded || floating)) {
            leftDockWidth = dockWidthAfterHiding(
                    workspaceSettingsLoaded, leftDockWidth, hostSidebar.getWidth());
        }
        hostSidebarVisible = visible;
        if (!visible) {
            if (embedded) {
                updateHorizontalDockLayout(() -> horizontalWorkbenchSplit.getItems().remove(hostSidebar));
            } else {
                horizontalWorkbenchSplit.getItems().remove(hostSidebar);
            }
            workbenchOverlayHost.getChildren().remove(hostSidebar);
            hostSidebar.getStyleClass().remove("floating-dock");
        } else if (isFloating(DockSide.LEFT)) {
            horizontalWorkbenchSplit.getItems().remove(hostSidebar);
            workbenchOverlayHost.getChildren().remove(hostSidebar);
            lockDockWidthForReveal(hostSidebar, leftDockWidth);
            StackPane.setAlignment(hostSidebar, Pos.CENTER_LEFT);
            hostSidebar.getStyleClass().add("floating-dock");
            workbenchOverlayHost.getChildren().add(hostSidebar);
        } else {
            horizontalWorkbenchSplit.getItems().remove(hostSidebar);
            workbenchOverlayHost.getChildren().remove(hostSidebar);
            hostSidebar.getStyleClass().remove("floating-dock");
            lockDockWidthForReveal(hostSidebar, leftDockWidth);
            updateHorizontalDockLayout(() -> horizontalWorkbenchSplit.getItems().add(0, hostSidebar));
        }
        refreshToolWindowButtonSelection();
    }

    private void setRightDockVisible(boolean visible) {
        if (visible == rightDockVisible) {
            return;
        }
        if (visible && selectedRightTool == ToolWindow.AI_ASSISTANT) aiAssistantPageController.collapseSessions();
        rightDock.setCursor(javafx.scene.Cursor.DEFAULT);
        boolean embedded = horizontalWorkbenchSplit.getItems().contains(rightDock);
        boolean floating = workbenchOverlayHost.getChildren().contains(rightDock);
        if (!visible && (embedded || floating)) {
            rightDockWidth = dockWidthAfterHiding(
                    workspaceSettingsLoaded, rightDockWidth, rightDock.getWidth());
        }
        rightDockVisible = visible;
        if (!visible) {
            if (embedded) {
                updateHorizontalDockLayout(() -> horizontalWorkbenchSplit.getItems().remove(rightDock));
            } else {
                horizontalWorkbenchSplit.getItems().remove(rightDock);
            }
            workbenchOverlayHost.getChildren().remove(rightDock);
            rightDock.getStyleClass().remove("floating-dock");
        } else if (isFloating(DockSide.RIGHT)) {
            horizontalWorkbenchSplit.getItems().remove(rightDock);
            workbenchOverlayHost.getChildren().remove(rightDock);
            lockDockWidthForReveal(rightDock, rightDockWidth);
            StackPane.setAlignment(rightDock, Pos.CENTER_RIGHT);
            rightDock.getStyleClass().add("floating-dock");
            workbenchOverlayHost.getChildren().add(rightDock);
        } else {
            horizontalWorkbenchSplit.getItems().remove(rightDock);
            workbenchOverlayHost.getChildren().remove(rightDock);
            rightDock.getStyleClass().remove("floating-dock");
            lockDockWidthForReveal(rightDock, rightDockWidth);
            updateHorizontalDockLayout(() -> horizontalWorkbenchSplit.getItems().add(rightDock));
        }
        refreshToolWindowButtonSelection();
    }

    @FXML
    private void toggleLeftDockDisplayMode() {
        toggleDockDisplayMode(DockSide.LEFT);
    }

    @FXML
    private void toggleRightDockDisplayMode() {
        toggleDockDisplayMode(DockSide.RIGHT);
    }

    @FXML
    private void toggleFloatingLeftPanel() {
        setDockDisplayModeAndToggle(DockSide.LEFT, DockDisplayMode.FLOATING);
    }

    @FXML
    private void toggleEmbeddedLeftPanel() {
        setDockDisplayModeAndToggle(DockSide.LEFT, DockDisplayMode.EMBEDDED);
    }

    @FXML
    private void toggleFloatingRightPanel() {
        setDockDisplayModeAndToggle(DockSide.RIGHT, DockDisplayMode.FLOATING);
    }

    @FXML
    private void toggleEmbeddedRightPanel() {
        setDockDisplayModeAndToggle(DockSide.RIGHT, DockDisplayMode.EMBEDDED);
    }

    @FXML
    private void toggleGlobalToolbar() {
        GlobalSettings previous = persistedWorkspaceSettings;
        GlobalSettings updated = previous.withWindowSettings(previous.windowSettings()
                .withShowGlobalToolbar(globalToolbarMenuItem.isSelected()));
        persistedWorkspaceSettings = updated;
        applyApplicationPreferences(updated);
        settingsWorkflow.save(updated).whenComplete((ignored, error) -> {
            if (error == null) return;
            Platform.runLater(() -> {
                if (persistedWorkspaceSettings == updated) {
                    persistedWorkspaceSettings = previous;
                    applyApplicationPreferences(previous);
                }
                setStatus(messageFor(error,
                        com.ynpe.fastremote.i18n.Texts.text("legacy.java.12299e4f6fb6a16e")),
                        StatusTone.ERROR);
            });
        });
    }

    @FXML
    private void toggleGlobalStatusBar() {
        GlobalSettings previous = persistedWorkspaceSettings;
        GlobalSettings updated = previous.withWindowSettings(previous.windowSettings()
                .withShowGlobalStatusBar(globalStatusBarMenuItem.isSelected()));
        persistedWorkspaceSettings = updated;
        applyApplicationPreferences(updated);
        settingsWorkflow.save(updated).whenComplete((ignored, error) -> {
            if (error == null) return;
            Platform.runLater(() -> {
                if (persistedWorkspaceSettings == updated) {
                    persistedWorkspaceSettings = previous;
                    applyApplicationPreferences(previous);
                }
                setStatus(messageFor(error,
                        com.ynpe.fastremote.i18n.Texts.text("legacy.java.12299e4f6fb6a16e")),
                        StatusTone.ERROR);
            });
        });
    }

    private void toggleGlobalToolbarFromCommandPalette() {
        globalToolbarMenuItem.setSelected(!persistedWorkspaceSettings.windowSettings().showGlobalToolbar());
        toggleGlobalToolbar();
    }

    private void toggleGlobalStatusBarFromCommandPalette() {
        globalStatusBarMenuItem.setSelected(!persistedWorkspaceSettings.windowSettings().showGlobalStatusBar());
        toggleGlobalStatusBar();
    }

    private void setToolbarDisplayMode(com.ynpe.fastremote.domain.settings.ToolbarDisplayMode mode) {
        GlobalSettings previous = persistedWorkspaceSettings;
        GlobalSettings updated = previous.withWindowSettings(previous.windowSettings().withToolbarDisplayMode(mode));
        persistedWorkspaceSettings = updated;
        GlobalToolbarPresentation.apply(mainToolbar, mode);
        settingsWorkflow.save(updated).whenComplete((ignored, error) -> {
            if (error == null) return;
            Platform.runLater(() -> {
                if (persistedWorkspaceSettings == updated) {
                    persistedWorkspaceSettings = previous;
                    applyApplicationPreferences(previous);
                }
                setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.12299e4f6fb6a16e")),
                        StatusTone.ERROR);
            });
        });
    }

    private void setDockDisplayModeAndToggle(DockSide side, DockDisplayMode mode) {
        setDockDisplayMode(side, mode);
        if (side == DockSide.LEFT) toggleLeftDock();
        else toggleRightDock();
    }

    private void toggleDockDisplayMode(DockSide side) {
        DockDisplayMode next = nextDockDisplayMode(side == DockSide.LEFT
                ? persistedWorkspaceSettings.leftDockDisplayMode()
                : persistedWorkspaceSettings.rightDockDisplayMode());
        setDockDisplayMode(side, next);
    }

    private void setDockDisplayMode(DockSide side, DockDisplayMode mode) {
        GlobalSettings previous = persistedWorkspaceSettings;
        DockDisplayMode current = side == DockSide.LEFT
                ? previous.leftDockDisplayMode() : previous.rightDockDisplayMode();
        if (current == mode) return;
        GlobalSettings updated = previous.withDockDisplayModes(
                side == DockSide.LEFT ? mode : previous.leftDockDisplayMode(),
                side == DockSide.RIGHT ? mode : previous.rightDockDisplayMode());
        persistedWorkspaceSettings = updated;
        rebuildVisibleDockPresentations();
        updateDockDisplayModeButtons();
        settingsWorkflow.save(updated).whenComplete((ignored, error) -> {
            if (error == null) return;
            Platform.runLater(() -> {
                if (persistedWorkspaceSettings == updated) {
                    persistedWorkspaceSettings = previous;
                    rebuildVisibleDockPresentations();
                    updateDockDisplayModeButtons();
                }
                setStatus(messageFor(error,
                        com.ynpe.fastremote.i18n.Texts.text("legacy.java.12299e4f6fb6a16e")), StatusTone.ERROR);
            });
        });
    }

    static DockDisplayMode nextDockDisplayMode(DockDisplayMode current) {
        return current == DockDisplayMode.EMBEDDED
                ? DockDisplayMode.FLOATING : DockDisplayMode.EMBEDDED;
    }

    private void updateDockDisplayModeButtons() {
        updateDockDisplayModeButton(leftDockDisplayModeButton, leftDockDisplayModeIcon,
                DockSide.LEFT, persistedWorkspaceSettings.leftDockDisplayMode());
        updateDockDisplayModeButton(rightDockDisplayModeButton, rightDockDisplayModeIcon,
                DockSide.RIGHT, persistedWorkspaceSettings.rightDockDisplayMode());
    }

    private static void updateDockDisplayModeButton(
            Button button, LucideIconView icon, DockSide side, DockDisplayMode current) {
        if (button == null || icon == null) return;
        boolean switchToFloating = current == DockDisplayMode.EMBEDDED;
        String label = com.ynpe.fastremote.i18n.Texts.text(switchToFloating
                ? "action.switchDockToFloating" : "action.switchDockToEmbedded");
        button.setAccessibleText(label);
        button.setTooltip(new Tooltip(label));
        icon.setIcon(dockDisplayModeActionIcon(side, current));
    }

    static Icon dockDisplayModeActionIcon(DockSide side, DockDisplayMode current) {
        if (current == DockDisplayMode.EMBEDDED) return Icon.EXTERNAL_LINK;
        return side == DockSide.LEFT ? Icon.PANEL_LEFT : Icon.PANEL_RIGHT;
    }

    private boolean isFloating(DockSide side) {
        return (side == DockSide.LEFT
                ? persistedWorkspaceSettings.leftDockDisplayMode()
                : persistedWorkspaceSettings.rightDockDisplayMode()) == DockDisplayMode.FLOATING;
    }

    private boolean isFloatingDockVisible(DockSide side) {
        Node dock = side == DockSide.LEFT ? hostSidebar : rightDock;
        return isFloating(side) && workbenchOverlayHost.getChildren().contains(dock);
    }

    private void requestHorizontalDockWidthRestore() {
        horizontalDockRestoreDelay.playFromStart();
    }

    static double dockWidthAfterHiding(boolean settingsLoaded, double savedWidth, double renderedWidth) {
        return settingsLoaded && Double.isFinite(renderedWidth) && renderedWidth > 0.0
                ? renderedWidth : savedWidth;
    }

    static void lockDockWidthForReveal(Region dock, double width) {
        width = Math.max(width, dock.minWidth(-1));
        dock.setPrefWidth(width);
        dock.setMaxWidth(width);
    }

    private void updateHorizontalDockLayout(Runnable itemMutation) {
        horizontalDockRestoreDelay.stop();
        double previousOpacity = horizontalWorkbenchSplit.getOpacity();
        horizontalWorkbenchSplit.setOpacity(0.0);
        try {
            itemMutation.run();

            // Adding or removing one item makes SplitPane recalculate every divider.
            // Complete that transient layout and restore both saved pixel widths before
            // any part of the workbench is painted, including the opposite visible dock.
            rootPane.applyCss();
            rootPane.layout();
            restoreHorizontalDockWidths();
            horizontalWorkbenchSplit.layout();
            restoreHorizontalDockWidths();
        } finally {
            horizontalWorkbenchSplit.setOpacity(previousOpacity);
        }
    }

    private void unlockHorizontalDockWidths() {
        hostSidebar.setMaxWidth(Double.MAX_VALUE);
        rightDock.setMaxWidth(Double.MAX_VALUE);
    }

    private static boolean splitPaneDividerTarget(Object target) {
        if (!(target instanceof Node node)) return false;
        for (Node current = node; current != null; current = current.getParent()) {
            if (current.getStyleClass().contains("split-pane-divider")) return true;
        }
        return false;
    }

    private void restoreHorizontalDockWidths() {
        double splitWidth = horizontalWorkbenchSplit.getWidth();
        if (splitWidth <= 0.0) {
            return;
        }
        if (horizontalWorkbenchSplit.getItems().contains(hostSidebar)) {
            hostSidebar.setPrefWidth(leftDockWidth);
            horizontalWorkbenchSplit.setDividerPosition(0, clamp(leftDockWidth / splitWidth, 0.0, 1.0));
        }
        if (horizontalWorkbenchSplit.getItems().contains(rightDock)) {
            rightDock.setPrefWidth(rightDockWidth);
            int dividerIndex = horizontalWorkbenchSplit.getDividers().size() - 1;
            horizontalWorkbenchSplit.setDividerPosition(
                    dividerIndex,
                    clamp(1.0 - rightDockWidth / splitWidth, 0.0, 1.0));
        }
    }

    private boolean captureHorizontalDockWidths() {
        boolean changed = false;
        if (horizontalWorkbenchSplit.getItems().contains(hostSidebar) && hostSidebar.getWidth() > 0.0) {
            double actualWidth = hostSidebar.getWidth();
            changed |= Math.abs(actualWidth - leftDockWidth) >= 0.5;
            leftDockWidth = actualWidth;
        }
        if (horizontalWorkbenchSplit.getItems().contains(rightDock) && rightDock.getWidth() > 0.0) {
            double actualWidth = rightDock.getWidth();
            changed |= Math.abs(actualWidth - rightDockWidth) >= 0.5;
            rightDockWidth = actualWidth;
        }
        return changed;
    }

    private void saveDockDimensions() {
        if (!workspaceSettingsLoaded) return;
        GlobalSettings settings = new GlobalSettings(
                persistedWorkspaceSettings.leftDockVisible(),
                persistedWorkspaceSettings.rightDockVisible(),
                persistedWorkspaceSettings.bottomDockVisible(),
                leftDockWidth,
                rightDockWidth,
                persistedWorkspaceSettings.hostLibraryDock(),
                persistedWorkspaceSettings.quickConnectDock(),
                persistedWorkspaceSettings.snippetsDock(),
                persistedWorkspaceSettings.variableLibraryDock(),
                persistedWorkspaceSettings.credentialVaultDock(),
                persistedWorkspaceSettings.aiAssistantDock(),
                persistedWorkspaceSettings.diagnosticLogDock(),
                persistedWorkspaceSettings.collapsedHostGroupIds(),
                persistedWorkspaceSettings.hostTabTitleParts(),
                persistedWorkspaceSettings.showSftpDirectoryTree(),
                persistedWorkspaceSettings.terminalPreferences(),
                persistedWorkspaceSettings.sftpSettings(),
                persistedWorkspaceSettings.connectionSettings(),
                persistedWorkspaceSettings.openLocalTerminalOnStartup(),
                persistedWorkspaceSettings.windowSettings(),
                persistedWorkspaceSettings.leftDockDisplayMode(),
                persistedWorkspaceSettings.rightDockDisplayMode(),
                persistedWorkspaceSettings.terminalActivityHeight(),
                persistedWorkspaceSettings.sftpActivityHeight(),
                persistedWorkspaceSettings.leftToolOrder(),
                persistedWorkspaceSettings.rightToolOrder(),
                persistedWorkspaceSettings.commandPaletteSettings(),
                persistedWorkspaceSettings.diagnosticLogSettings(), persistedWorkspaceSettings.aiSessionsCollapsed());
        persistedWorkspaceSettings = settings;
        settingsWorkflow.save(settings).whenComplete((ignored, error) -> {
            if (error != null) {
                Platform.runLater(() -> setStatus(
                        messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.2c633f082e2a05e7")), StatusTone.ERROR));
            }
        });
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void setToolWindowButtonSelected(Button button, boolean selected) {
        if (selected) {
            if (!button.getStyleClass().contains("tool-window-button-selected")) {
                button.getStyleClass().add("tool-window-button-selected");
            }
        } else {
            button.getStyleClass().remove("tool-window-button-selected");
        }
    }

    private void showToolWindow(ToolWindow tool, boolean toggle) {
        if (tool == ToolWindow.AI_ASSISTANT && aiPlacement.isDialogOpen()) aiPlacement.closeDialogToDock();
        if (tool == ToolWindow.AI_ASSISTANT && aiPlacement.isWorkspaceOpen()) {
            aiPlacement.workspaceTab().ifPresent(workspaceTabs.getSelectionModel()::select);
            requestAiPromptFocusIfInitialized();
            return;
        }
        DockSide side = dockSide(tool, persistedWorkspaceSettings);
        ToolWindow selected = side == DockSide.LEFT ? selectedLeftTool : selectedRightTool;
        boolean visible = side == DockSide.LEFT ? hostSidebarVisible : rightDockVisible;
        if (toggle && visible && tool == selected) {
            if (side == DockSide.LEFT) setHostSidebarVisible(false);
            else setRightDockVisible(false);
            return;
        }
        if (tool == ToolWindow.AI_ASSISTANT && tool != selected) aiAssistantPageController.collapseSessions();
        installToolWindow(side, tool);
        if (side == DockSide.LEFT) {
            selectedLeftTool = tool;
            setHostSidebarVisible(true);
        } else {
            selectedRightTool = tool;
            setRightDockVisible(true);
        }
        refreshToolWindowButtonSelection();
        requestToolWindowInitialFocus(tool);
    }

    private void requestToolWindowInitialFocus(ToolWindow tool) {
        Node target = switch (tool.initialFocus) {
            case HOST -> hostField;
            case SESSION_SEARCH -> hostSearchField;
            case CREDENTIAL_SEARCH -> credentialSearchField;
            case SNIPPET_SEARCH -> snippetSearchField;
            case VARIABLE_SEARCH -> variableSearchField;
            case AI_PROMPT -> aiPromptField;
            case NONE -> null;
        };
        if (target == aiPromptField) requestAiPromptFocusIfInitialized();
        else if (target != null) Platform.runLater(target::requestFocus);
    }

    private void installToolWindow(DockSide side, ToolWindow tool) {
        Node page = toolWindowPages.get(tool);
        leftDockContentHost.getChildren().remove(page);
        rightDockContentHost.getChildren().remove(page);
        StackPane host = side == DockSide.LEFT ? leftDockContentHost : rightDockContentHost;
        VBox dock = side == DockSide.LEFT ? hostSidebar : rightDock;
        dock.setMinWidth(dockMinimumWidth(tool));
        if (tool == ToolWindow.AI_ASSISTANT) {
            warmUpAiAssistant();
            if (aiPlacement.isWorkspaceOpen()) {
                host.getChildren().setAll(aiPlacement.workspacePlaceholder(() ->
                        aiPlacement.workspaceTab().ifPresent(workspaceTabs.getSelectionModel()::select)));
            } else {
                aiPlacement.attachDock(host);
            }
        } else if (tool == ToolWindow.DIAGNOSTIC_LOG) {
            if (diagnosticLogPlacement.isWorkspaceOpen()) {
                host.getChildren().setAll(diagnosticLogPlacement.workspacePlaceholder(() ->
                        diagnosticLogPlacement.workspaceTab().ifPresent(
                                workspaceTabs.getSelectionModel()::select)));
            } else {
                diagnosticLogPlacement.attachDock(host);
            }
        } else {
            host.getChildren().setAll(page);
        }
        if (side == DockSide.LEFT) {
            selectedLeftTool = tool;
            leftDockTitle.setText(tool.title());
            leftDockSubtitle.setText(tool.subtitle());
            setManagedVisible(leftHostLibraryButton, tool == ToolWindow.HOST_LIBRARY);
            setManagedVisible(leftDockImportButton, tool.importable());
            setManagedVisible(leftDockExportButton, tool.importable());
        } else {
            selectedRightTool = tool;
            rightDockTitle.setText(tool.title());
            rightDockSubtitle.setText(tool.subtitle());
            setManagedVisible(rightHostLibraryButton, tool == ToolWindow.HOST_LIBRARY);
            setManagedVisible(rightDockImportButton, tool.importable());
            setManagedVisible(rightDockExportButton, tool.importable());
        }
        Platform.runLater(this::restoreHorizontalDockWidths);
    }

    static double dockMinimumWidth(ToolWindow tool) {
        // AI's composer has a content-derived minimum. Let both docks inherit it instead of
        // allowing the SplitPane to shrink the header beneath the chat view's minimum width.
        if (tool == ToolWindow.AI_ASSISTANT) return Region.USE_COMPUTED_SIZE;
        return STANDARD_DOCK_MIN_WIDTH;
    }

    @FXML
    private void importLeftTool() { importTool(selectedLeftTool); }

    @FXML
    private void importRightTool() { importTool(selectedRightTool); }

    @FXML
    private void exportLeftTool() { exportTool(selectedLeftTool); }

    @FXML
    private void exportRightTool() { exportTool(selectedRightTool); }

    private void importTool(ToolWindow tool) {
        if (tool == ToolWindow.SNIPPETS) importSnippets();
        else if (tool == ToolWindow.VARIABLE_LIBRARY) importVariables();
    }

    private void exportTool(ToolWindow tool) {
        if (tool == ToolWindow.SNIPPETS) exportSnippets();
        else if (tool == ToolWindow.VARIABLE_LIBRARY) exportVariables();
    }

    private static void setManagedVisible(Node node, boolean visible) {
        node.setManaged(visible);
        node.setVisible(visible);
    }

    private void arrangeToolRails(GlobalSettings settings) {
        for (Button button : toolWindowButtons.values()) {
            leftToolRail.getChildren().remove(button);
            rightToolRail.getChildren().remove(button);
        }
        for (DockSide side : DockSide.values()) {
            VBox rail = side == DockSide.LEFT ? leftToolRail : rightToolRail;
            for (ToolWindow tool : orderedTools(side, settings)) {
                rail.getChildren().add(Math.max(0, rail.getChildren().size() - 1), toolWindowButtons.get(tool));
            }
        }
        updateAiRailButtonVisibility();
        updateAiPlacementButton();
        updateDiagnosticLogRailButtonVisibility();
        updateDiagnosticLogPlacementButton();
        refreshToolRailVisibility();
    }

    private void onAiPlacementChanged() {
        if (aiPlacement.isDialogOpen()) {
            if (selectedLeftTool == ToolWindow.AI_ASSISTANT) setHostSidebarVisible(false);
            if (selectedRightTool == ToolWindow.AI_ASSISTANT) setRightDockVisible(false);
        }
        if (aiPlacement.isDialogOpen() || aiPlacement.isWorkspaceOpen()) {
            aiAssistantPageController.setSessionsCollapsed(persistedWorkspaceSettings.aiSessionsCollapsed());
        }
        aiAssistantPageController.setDialogPresentation(aiPlacement.isDialogOpen());
        updateWorkspaceVisibility();
        updateAiRailButtonVisibility();
        updateAiPlacementButton();
        refreshToolWindowButtonSelection();
        requestAiPromptFocusIfInitialized();
    }

    private void onDiagnosticLogPlacementChanged() {
        updateWorkspaceVisibility();
        updateDiagnosticLogRailButtonVisibility();
        updateDiagnosticLogPlacementButton();
        refreshToolWindowButtonSelection();
    }

    private void updateDiagnosticLogPlacementButton() {
        if (diagnosticLogPlacementButton == null || diagnosticLogPlacementIcon == null
                || diagnosticLogPlacementTooltip == null || diagnosticLogPlacement == null) return;
        boolean workspace = diagnosticLogPlacement.isWorkspaceOpen();
        String label = diagnosticLogPlacementLabel(workspace);
        diagnosticLogPlacementButton.setAccessibleText(label);
        diagnosticLogPlacementTooltip.setText(label);
        diagnosticLogPlacementIcon.setIcon(diagnosticLogPlacementIcon(workspace,
                dockSide(ToolWindow.DIAGNOSTIC_LOG, persistedWorkspaceSettings)));
    }

    static String diagnosticLogPlacementLabel(boolean workspaceOpen) {
        return com.ynpe.fastremote.i18n.Texts.text(workspaceOpen
                ? "diagnostic.log.dockSidebar" : "diagnostic.log.openWorkspace");
    }

    static Icon diagnosticLogPlacementIcon(boolean workspaceOpen, DockSide dockSide) {
        if (!workspaceOpen) return Icon.PANELS_TOP_LEFT;
        return dockSide == DockSide.LEFT ? Icon.PANEL_LEFT : Icon.PANEL_RIGHT;
    }

    private void updateDiagnosticLogRailButtonVisibility() {
        if (diagnosticLogRailButton != null && diagnosticLogPlacement != null) {
            setManagedVisible(diagnosticLogRailButton,
                    diagnosticLogRailButtonVisible(diagnosticLogPlacement.isWorkspaceOpen()));
            refreshToolRailVisibility();
        }
    }

    static boolean diagnosticLogRailButtonVisible(boolean workspaceOpen) {
        return !workspaceOpen;
    }

    private void updateAiPlacementButton() {
        if (aiPlacementButton == null || aiPlacementIcon == null || aiPlacementTooltip == null) return;
        boolean workspace = aiPlacement.isWorkspaceOpen();
        String label = aiPlacementLabel(workspace);
        aiPlacementButton.setAccessibleText(label);
        aiPlacementTooltip.setText(label);
        aiPlacementIcon.setIcon(aiPlacementIcon(workspace,
                dockSide(ToolWindow.AI_ASSISTANT, persistedWorkspaceSettings)));
    }

    static String aiPlacementLabel(boolean workspaceOpen) {
        return workspaceOpen ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.1685d1543b6baca2") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.a5fdbd756fad1abc");
    }

    static Icon aiPlacementIcon(boolean workspaceOpen, DockSide dockSide) {
        if (!workspaceOpen) return Icon.PANELS_TOP_LEFT;
        return dockSide == DockSide.LEFT ? Icon.PANEL_LEFT : Icon.PANEL_RIGHT;
    }

    private void updateAiRailButtonVisibility() {
        if (aiAssistantRailButton != null && aiPlacement != null) {
            setManagedVisible(aiAssistantRailButton, aiRailButtonVisible(aiPlacement.isWorkspaceOpen()));
            refreshToolRailVisibility();
        }
    }

    static boolean aiRailButtonVisible(boolean workspaceOpen) {
        return !workspaceOpen;
    }

    private void installToolWindowDrag(ToolWindow tool, Button button) {
        button.setOnDragDetected(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            draggedToolWindow = tool;
            Dragboard board = button.startDragAndDrop(TransferMode.MOVE);
            Image dragView = toolWindowDragView(button);
            board.setDragView(dragView, dragView.getWidth() / 2.0, dragView.getHeight() / 2.0);
            ClipboardContent content = new ClipboardContent();
            content.putString("fastremote/tool-window/" + tool.name());
            board.setContent(content);
            event.consume();
        });
        button.setOnDragDone(event -> {
            draggedToolWindow = null;
            clearToolWindowDropIndicators();
        });
    }

    static Image toolWindowDragView(Button button) {
        Node graphic = Objects.requireNonNull(button.getGraphic(), "tool window button graphic");
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        return graphic.snapshot(parameters, null);
    }

    private void installToolWindowDropTargets() {
        rootPane.addEventFilter(DragEvent.DRAG_OVER, event -> {
            Optional<ToolWindowDropTarget> target = currentToolWindowDropTarget(
                    event.getSceneX(), event.getSceneY());
            if (target.isEmpty()) {
                clearToolWindowDropIndicators();
                return;
            }
            event.acceptTransferModes(TransferMode.MOVE);
            showToolWindowDropTarget(target.get());
            event.consume();
        });
        rootPane.addEventFilter(DragEvent.DRAG_DROPPED, event -> {
            Optional<ToolWindowDropTarget> target = currentToolWindowDropTarget(
                    event.getSceneX(), event.getSceneY());
            clearToolWindowDropIndicators();
            if (draggedToolWindow == null || target.isEmpty()) return;
            ToolWindowDropTarget destination = target.get();
            relocateToolWindow(draggedToolWindow, destination.side(),
                    destination.tool(), destination.before());
            event.setDropCompleted(true);
            event.consume();
        });
        rootPane.addEventFilter(DragEvent.DRAG_EXITED, event -> {
            if (draggedToolWindow != null) clearToolWindowDropIndicators();
        });
    }

    private void installExternalFileDropTarget() {
        // This is deliberately a bubbling handler, not a capture filter. SFTP/local-file
        // transfer targets and terminals must see the drop first and consume it; only an
        // otherwise-unhandled operating-system file drop belongs to the editor fallback.
        addDropFallbackHandler(rootPane, DragEvent.DRAG_OVER, event -> {
            if (draggedToolWindow != null || !event.getDragboard().hasFiles()
                    || isTerminalDropTarget(event)) return;
            event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
        addDropFallbackHandler(rootPane, DragEvent.DRAG_DROPPED, event -> {
            if (draggedToolWindow != null || !event.getDragboard().hasFiles()
                    || isTerminalDropTarget(event)) return;
            List<Path> paths = event.getDragboard().getFiles().stream()
                    .map(File::toPath)
                    .map(path -> path.toAbsolutePath().normalize())
                    .distinct()
                    .toList();
            event.setDropCompleted(true);
            event.consume();
            long maximumBytes = persistedWorkspaceSettings.sftpSettings().maxEditableFileBytes();
            try {
                FILE_DROP_INSPECTOR.execute(() -> {
                    DroppedFileScan scan = inspectDroppedFiles(paths, maximumBytes);
                    Platform.runLater(() -> handleDroppedFiles(scan));
                });
            } catch (RejectedExecutionException busy) {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("editor.drop.busy"), StatusTone.WORKING);
            }
        });
    }

    static <T extends javafx.event.Event> void addDropFallbackHandler(
            Node target, javafx.event.EventType<T> type,
            javafx.event.EventHandler<? super T> handler) {
        target.addEventHandler(type, handler);
    }

    private static boolean isTerminalDropTarget(DragEvent event) {
        Node picked = event.getPickResult() == null ? null : event.getPickResult().getIntersectedNode();
        return isTerminalDropTarget(picked != null ? picked
                : event.getTarget() instanceof Node node ? node : null);
    }

    static boolean isTerminalDropTarget(Node target) {
        for (Node current = target; current != null; current = current.getParent()) {
            if (current.getStyleClass().contains("terminal-host")) return true;
        }
        return false;
    }

    static DroppedFileScan inspectDroppedFiles(List<Path> paths, long maximumBytes) {
        List<LocalFileEntry> eligible = new ArrayList<>();
        int ignored = 0;
        for (Path path : new LinkedHashSet<>(paths)) {
            try {
                BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
                Path name = path.getFileName();
                if (!attributes.isRegularFile() || name == null || attributes.size() > maximumBytes
                        || !Files.isReadable(path)) {
                    ignored++;
                    continue;
                }
                boolean hidden;
                try {
                    hidden = Files.isHidden(path);
                } catch (IOException ignoredError) {
                    hidden = name.toString().startsWith(".");
                }
                eligible.add(new LocalFileEntry(name.toString(), path, false, Files.isSymbolicLink(path),
                        attributes.size(), Optional.of(attributes.lastModifiedTime().toInstant()), hidden));
            } catch (IOException | SecurityException ignoredError) {
                ignored++;
            }
        }
        return new DroppedFileScan(List.copyOf(eligible), ignored);
    }

    private void handleDroppedFiles(DroppedFileScan scan) {
        DroppedFileBatchPolicy policy = droppedFileBatchPolicy(scan.eligible().size());
        if (policy == DroppedFileBatchPolicy.REJECT) {
            Alert warning = new Alert(Alert.AlertType.WARNING,
                    com.ynpe.fastremote.i18n.Texts.text("editor.drop.tooManyMessage", scan.eligible().size()),
                    ButtonType.OK);
            warning.initOwner(rootPane.getScene().getWindow());
            warning.setTitle(com.ynpe.fastremote.i18n.Texts.text("editor.drop.title"));
            warning.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("editor.drop.tooManyHeader"));
            TextEditorWorkspace.prepareDialog(warning);
            warning.show();
            return;
        }
        if (policy == DroppedFileBatchPolicy.CONFIRM) {
            ButtonType open = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("editor.drop.openFiles"),
                    ButtonBar.ButtonData.OK_DONE);
            Alert confirmation = new Alert(Alert.AlertType.WARNING,
                    com.ynpe.fastremote.i18n.Texts.text("editor.drop.riskMessage"), open, ButtonType.CANCEL);
            confirmation.initOwner(rootPane.getScene().getWindow());
            confirmation.setTitle(com.ynpe.fastremote.i18n.Texts.text("editor.drop.title"));
            confirmation.setHeaderText(com.ynpe.fastremote.i18n.Texts.text(
                    "editor.drop.riskHeader", scan.eligible().size()));
            TextEditorWorkspace.prepareDialog(confirmation);
            if (confirmation.showAndWait().filter(open::equals).isEmpty()) return;
        }
        if (scan.eligible().isEmpty()) {
            setStatus(com.ynpe.fastremote.i18n.Texts.text("editor.drop.noneEligible"), StatusTone.IDLE);
            return;
        }
        if (scan.ignored() > 0) {
            setStatus(com.ynpe.fastremote.i18n.Texts.text("editor.drop.skipped", scan.ignored()), StatusTone.IDLE);
        }
        textEditors.openLocalBatch(scan.eligible(), message -> setStatus(message, StatusTone.ERROR));
    }

    static DroppedFileBatchPolicy droppedFileBatchPolicy(int eligibleCount) {
        if (eligibleCount > FILE_DROP_MAXIMUM) return DroppedFileBatchPolicy.REJECT;
        if (eligibleCount > FILE_DROP_CONFIRM_THRESHOLD) return DroppedFileBatchPolicy.CONFIRM;
        return DroppedFileBatchPolicy.OPEN;
    }

    enum DroppedFileBatchPolicy { OPEN, CONFIRM, REJECT }

    record DroppedFileScan(List<LocalFileEntry> eligible, int ignored) {
        DroppedFileScan {
            eligible = List.copyOf(eligible);
            if (ignored < 0) throw new IllegalArgumentException("ignored must not be negative");
        }
    }

    private Optional<ToolWindowDropTarget> currentToolWindowDropTarget(double sceneX, double sceneY) {
        if (draggedToolWindow == null) return Optional.empty();
        Map<ToolWindow, Bounds> buttonBounds = new java.util.EnumMap<>(ToolWindow.class);
        toolWindowButtons.forEach((tool, button) -> {
            if (button.isVisible() && button.isManaged()) {
                buttonBounds.put(tool, button.localToScene(button.getBoundsInLocal()));
            }
        });
        Map<DockSide, Bounds> railBounds = new java.util.EnumMap<>(DockSide.class);
        Bounds workbenchBounds = workbenchBoard.localToScene(workbenchBoard.getBoundsInLocal());
        railBounds.put(DockSide.LEFT, leftToolRail.isManaged()
                ? leftToolRail.localToScene(leftToolRail.getBoundsInLocal())
                : hiddenToolRailDropBounds(DockSide.LEFT, workbenchBounds));
        railBounds.put(DockSide.RIGHT, rightToolRail.isManaged()
                ? rightToolRail.localToScene(rightToolRail.getBoundsInLocal())
                : hiddenToolRailDropBounds(DockSide.RIGHT, workbenchBounds));
        return toolWindowDropTarget(sceneX, sceneY, draggedToolWindow,
                buttonBounds, railBounds, persistedWorkspaceSettings);
    }

    static Bounds hiddenToolRailDropBounds(DockSide side, Bounds workbenchBounds) {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(workbenchBounds, "workbenchBounds");
        double x = side == DockSide.LEFT
                ? workbenchBounds.getMinX()
                : workbenchBounds.getMaxX() - HIDDEN_TOOL_RAIL_DROP_WIDTH;
        return new javafx.geometry.BoundingBox(x, workbenchBounds.getMinY(),
                Math.min(HIDDEN_TOOL_RAIL_DROP_WIDTH, workbenchBounds.getWidth()),
                workbenchBounds.getHeight());
    }

    static Optional<ToolWindowDropTarget> toolWindowDropTarget(
            double sceneX, double sceneY, ToolWindow dragged,
            Map<ToolWindow, Bounds> buttonBounds, Map<DockSide, Bounds> railBounds,
            GlobalSettings settings) {
        for (ToolWindow tool : ToolWindow.values()) {
            Bounds bounds = buttonBounds.get(tool);
            if (tool != dragged && bounds != null && bounds.contains(sceneX, sceneY)) {
                return Optional.of(new ToolWindowDropTarget(
                        dockSide(tool, settings), tool, sceneY < bounds.getMinY() + bounds.getHeight() / 2.0));
            }
        }
        for (DockSide side : DockSide.values()) {
            Bounds bounds = railBounds.get(side);
            if (bounds != null && bounds.contains(sceneX, sceneY)) {
                return Optional.of(new ToolWindowDropTarget(side, null, false));
            }
        }
        return Optional.empty();
    }

    private void showToolWindowDropTarget(ToolWindowDropTarget target) {
        clearToolWindowDropIndicators();
        if (target.tool() == null) {
            setToolRailDropPreview(target.side(), true);
            (target.side() == DockSide.LEFT ? leftToolRail : rightToolRail)
                    .getStyleClass().add("tool-window-rail-drop-target");
            return;
        }
        toolWindowButtons.get(target.tool()).getStyleClass().add(
                target.before() ? "tool-window-drop-before" : "tool-window-drop-after");
    }

    record ToolWindowDropTarget(DockSide side, ToolWindow tool, boolean before) { }

    private void relocateToolWindow(
            ToolWindow dragged, DockSide targetSide, ToolWindow target, boolean before) {
        GlobalSettings previous = persistedWorkspaceSettings;
        DockSide sourceSide = dockSide(dragged, previous);
        boolean wasSelected = sourceSide == DockSide.LEFT
                ? hostSidebarVisible && selectedLeftTool == dragged
                : rightDockVisible && selectedRightTool == dragged;

        List<ToolWindow> left = new ArrayList<>(orderedTools(DockSide.LEFT, previous));
        List<ToolWindow> right = new ArrayList<>(orderedTools(DockSide.RIGHT, previous));
        left.remove(dragged);
        right.remove(dragged);
        List<ToolWindow> destination = targetSide == DockSide.LEFT ? left : right;
        int index = target == null ? destination.size() : destination.indexOf(target);
        if (target != null && !before) index++;
        destination.add(Math.max(0, index), dragged);

        GlobalSettings updated = withToolDock(previous, dragged, targetSide)
                .withToolWindowLayout(
                        dragged == ToolWindow.VARIABLE_LIBRARY
                                ? targetSide : previous.variableLibraryDock(),
                        left.stream().map(Enum::name).toList(),
                        right.stream().map(Enum::name).toList());
        persistedWorkspaceSettings = updated;
        applyToolDockAssignments(updated);
        if (wasSelected && sourceSide != targetSide) showToolWindow(dragged, false);
        settingsWorkflow.save(updated).whenComplete((ignored, error) -> {
            if (error == null) return;
            Platform.runLater(() -> {
                if (persistedWorkspaceSettings == updated) {
                    persistedWorkspaceSettings = previous;
                    applyToolDockAssignments(previous);
                }
                setStatus(messageFor(error,
                        com.ynpe.fastremote.i18n.Texts.text("legacy.java.1fdb47de84fd900e")), StatusTone.ERROR);
            });
        });
    }

    static GlobalSettings withToolDock(GlobalSettings settings, ToolWindow tool, DockSide side) {
        return settings.withToolDockAssignments(
                tool == ToolWindow.HOST_LIBRARY ? side : settings.hostLibraryDock(),
                tool == ToolWindow.QUICK_CONNECT ? side : settings.quickConnectDock(),
                tool == ToolWindow.SNIPPETS ? side : settings.snippetsDock(),
                tool == ToolWindow.VARIABLE_LIBRARY ? side : settings.variableLibraryDock(),
                tool == ToolWindow.CREDENTIAL_VAULT ? side : settings.credentialVaultDock(),
                tool == ToolWindow.AI_ASSISTANT ? side : settings.aiAssistantDock(),
                tool == ToolWindow.DIAGNOSTIC_LOG ? side : settings.diagnosticLogDock());
    }

    static boolean toolWindowDropBefore(double pointerY, double buttonHeight) {
        return pointerY < buttonHeight / 2.0;
    }

    private void clearToolWindowDropIndicators() {
        toolWindowButtons.values().forEach(candidate -> candidate.getStyleClass().removeAll(
                "tool-window-drop-before", "tool-window-drop-after"));
        leftToolRail.getStyleClass().remove("tool-window-rail-drop-target");
        rightToolRail.getStyleClass().remove("tool-window-rail-drop-target");
        leftToolRailDropPreview = false;
        rightToolRailDropPreview = false;
        refreshToolRailVisibility();
    }

    private void setToolRailDropPreview(DockSide side, boolean visible) {
        if (side == DockSide.LEFT) leftToolRailDropPreview = visible;
        else rightToolRailDropPreview = visible;
        refreshToolRailVisibility();
    }

    private void refreshToolRailVisibility() {
        if (toolWindowButtons == null || leftToolRail == null || rightToolRail == null) return;
        setManagedVisible(leftToolRail,
                toolRailShouldBeVisible(toolRailHasVisibleButton(DockSide.LEFT),
                        leftToolRailDropPreview));
        setManagedVisible(rightToolRail,
                toolRailShouldBeVisible(toolRailHasVisibleButton(DockSide.RIGHT),
                        rightToolRailDropPreview));
    }

    static boolean toolRailShouldBeVisible(boolean hasVisibleButton, boolean dropPreview) {
        return hasVisibleButton || dropPreview;
    }

    private boolean toolRailHasVisibleButton(DockSide side) {
        VBox rail = side == DockSide.LEFT ? leftToolRail : rightToolRail;
        return toolWindowButtons.values().stream()
                .anyMatch(button -> button.getParent() == rail
                        && button.isManaged() && button.isVisible());
    }

    private void saveToolOrder(DockSide side, List<ToolWindow> ordered) {
        List<String> ids = ordered.stream().map(Enum::name).toList();
        List<String> left = side == DockSide.LEFT ? ids : persistedWorkspaceSettings.leftToolOrder();
        List<String> right = side == DockSide.RIGHT ? ids : persistedWorkspaceSettings.rightToolOrder();
        GlobalSettings updated = persistedWorkspaceSettings.withToolWindowLayout(
                persistedWorkspaceSettings.variableLibraryDock(), left, right);
        persistedWorkspaceSettings = updated;
        arrangeToolRails(updated);
        settingsWorkflow.save(updated).whenComplete((ignored, error) -> {
            if (error != null) Platform.runLater(() -> setStatus(
                    messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.1fdb47de84fd900e")), StatusTone.ERROR));
        });
    }

    private static List<ToolWindow> orderedTools(DockSide side, GlobalSettings settings) {
        List<ToolWindow> result = new ArrayList<>();
        List<String> saved = side == DockSide.LEFT ? settings.leftToolOrder() : settings.rightToolOrder();
        for (String id : saved) {
            try {
                ToolWindow tool = ToolWindow.valueOf(id);
                if (dockSide(tool, settings) == side && !result.contains(tool)) result.add(tool);
            } catch (IllegalArgumentException ignored) { }
        }
        Arrays.stream(ToolWindow.values())
                .filter(tool -> dockSide(tool, settings) == side && !result.contains(tool))
                .forEach(result::add);
        return List.copyOf(result);
    }

    private void refreshToolWindowButtonSelection() {
        for (ToolWindow tool : ToolWindow.values()) {
            DockSide side = dockSide(tool, persistedWorkspaceSettings);
            boolean selected = side == DockSide.LEFT
                    ? hostSidebarVisible && tool == selectedLeftTool
                    : rightDockVisible && tool == selectedRightTool;
            setToolWindowButtonSelected(toolWindowButtons.get(tool), selected);
        }
    }

    private static Optional<ToolWindow> firstToolOn(DockSide side, GlobalSettings settings) {
        return orderedTools(side, settings).stream().findFirst();
    }

    private static DockSide dockSide(ToolWindow tool, GlobalSettings settings) {
        return switch (tool) {
            case HOST_LIBRARY -> settings.hostLibraryDock();
            case QUICK_CONNECT -> settings.quickConnectDock();
            case SNIPPETS -> settings.snippetsDock();
            case VARIABLE_LIBRARY -> settings.variableLibraryDock();
            case CREDENTIAL_VAULT -> settings.credentialVaultDock();
            case AI_ASSISTANT -> settings.aiAssistantDock();
            case DIAGNOSTIC_LOG -> settings.diagnosticLogDock();
        };
    }

    private static DockSide dockSide(ToolWindow tool, ConfigurationDialog.WorkspaceLayout layout) {
        return switch (tool) {
            case HOST_LIBRARY -> layout.hostLibraryDock();
            case QUICK_CONNECT -> layout.quickConnectDock();
            case SNIPPETS -> layout.snippetsDock();
            case VARIABLE_LIBRARY -> layout.variableLibraryDock();
            case CREDENTIAL_VAULT -> layout.credentialVaultDock();
            case AI_ASSISTANT -> layout.aiAssistantDock();
            case DIAGNOSTIC_LOG -> layout.diagnosticLogDock();
        };
    }

    private static List<String> reconciledToolOrder(
            DockSide side, ConfigurationDialog.WorkspaceLayout layout, List<String> savedOrder) {
        List<String> result = new ArrayList<>();
        for (String id : savedOrder) {
            try {
                ToolWindow tool = ToolWindow.valueOf(id);
                if (dockSide(tool, layout) == side && !result.contains(id)) result.add(id);
            } catch (IllegalArgumentException ignored) { }
        }
        Arrays.stream(ToolWindow.values()).filter(tool -> dockSide(tool, layout) == side)
                .map(Enum::name).filter(id -> !result.contains(id)).forEach(result::add);
        return List.copyOf(result);
    }

    @FXML
    private void showSettings() {
        showSettings(null);
    }

    private void showSettings(String initialPage) {
        if (configurationDialogLoading) return;
        configurationDialogLoading = true;
        aiAssistantWorkflow.loadSettings().whenComplete((loadedAiSettings, aiSettingsError) ->
                Platform.runLater(() -> {
                    configurationDialogLoading = false;
                    if (controllerClosed.get()) return;
                    AiSettings settingsForDialog = configurationAiSettings(
                            loadedAiSettings, aiSettingsError, aiSettings);
                    if (aiSettingsError != null) {
                        setStatus(messageFor(unwrap(aiSettingsError),
                                com.ynpe.fastremote.i18n.Texts.text("legacy.java.78b5cf18e062a54c")),
                                StatusTone.ERROR);
                    } else {
                        // Configuration data is local and must not depend on AI native chat view/model discovery warm-up.
                        aiSettings = settingsForDialog;
                    }
                    showSettingsDialog(initialPage, settingsForDialog);
                }));
    }

    static AiSettings configurationAiSettings(
            AiSettings loaded, Throwable error, AiSettings cached) {
        Objects.requireNonNull(cached, "cached");
        return error == null && loaded != null ? loaded : cached;
    }

    private void reloadProgramConfiguration() {
        settingsWorkflow.load().whenComplete((settings, error) -> Platform.runLater(() -> {
            if (error != null) { setStatus(messageFor(error, localizer.text("config.failed")), StatusTone.ERROR); return; }
            diagnosticLogOutput.configure(settings.diagnosticLogSettings());
            applyWorkspaceSettings(settings);
            rebuildVisibleDockPresentations();
            refreshSftpWorkspaceLayouts();
            localFileWorkspaces.values().forEach(controller -> controller.applyDisplaySettings(
                    settings.windowSettings().showLocalFileHiddenFiles(),
                    settings.windowSettings().showLocalFileDirectoryTree()));
            catalogWorkflow.automaticUpdatePreference().thenCompose(saveAutomaticUpdateChecks)
                    .whenComplete((ignored, updateError) -> {
                        if (updateError != null) Platform.runLater(() -> setStatus(
                                messageFor(updateError, localizer.text("config.failed")), StatusTone.ERROR));
                    });
            aiAssistantWorkflow.loadSettings().thenCompose(aiAssistantWorkflow::saveSettings).whenComplete((ai, aiError) -> Platform.runLater(() -> {
                if (aiError == null) applyAiSettings(ai);
                else setStatus(messageFor(aiError, localizer.text("config.failed")), StatusTone.ERROR);
            }));
        }));
    }

    private void resetProgramConfiguration() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(aiInteractionOwner());
        confirm.setTitle(localizer.text("config.reset.title"));
        confirm.setHeaderText(localizer.text("config.reset.confirm"));
        confirm.setContentText(localizer.text("config.reset.note"));
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        settingsWorkflow.load().thenCompose(ignored -> catalogWorkflow.resetProgramConfiguration()).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (error != null) { setStatus(messageFor(error, localizer.text("config.failed")), StatusTone.ERROR); return; }
            reloadProgramConfiguration();
            setStatus(localizer.text("config.reset.done"), StatusTone.IDLE);
        }));
    }

    private void showSettingsDialog(String initialPage, AiSettings settingsForDialog) {
        var current = new ConfigurationDialog.WorkspaceLayout(
                persistedWorkspaceSettings.leftDockVisible(),
                persistedWorkspaceSettings.rightDockVisible(),
                persistedWorkspaceSettings.bottomDockVisible(),
                persistedWorkspaceSettings.hostLibraryDock(),
                persistedWorkspaceSettings.quickConnectDock(),
                persistedWorkspaceSettings.snippetsDock(),
                persistedWorkspaceSettings.variableLibraryDock(),
                persistedWorkspaceSettings.credentialVaultDock(),
                persistedWorkspaceSettings.aiAssistantDock(),
                persistedWorkspaceSettings.diagnosticLogDock(),
                persistedWorkspaceSettings.hostTabTitleParts(),
                persistedWorkspaceSettings.showSftpDirectoryTree(),
                persistedWorkspaceSettings.terminalPreferences(),
                persistedWorkspaceSettings.sftpSettings(),
                persistedWorkspaceSettings.connectionSettings(),
                persistedWorkspaceSettings.openLocalTerminalOnStartup(),
                persistedWorkspaceSettings.windowSettings(),
                persistedWorkspaceSettings.leftDockDisplayMode(),
                persistedWorkspaceSettings.rightDockDisplayMode(),
                persistedWorkspaceSettings.commandPaletteSettings(),
                persistedWorkspaceSettings.diagnosticLogSettings());
        ConfigurationDialog.show(aiInteractionOwner(), current, localizer,
                new ConfigurationDialog.EditActions(settingsWorkflow, this::saveStartupDockLayout,
                        bookmarks -> saveGlobalBookmarks(bookmarks,
                                com.ynpe.fastremote.i18n.Texts.text("legacy.java.94b9a6fc6012d316")),
                        settingsWorkflow::importTerminalColorSchemes, this::manageVault,
                        diagnosticLogOutput, (path, select) -> workflow.revealLocal(path, select)),
                new ConfigurationDialog.AiConfiguration(aiAssistantWorkflow, settingsForDialog,
                        this::applyAiSettings, message -> setStatus(message, StatusTone.ERROR)),
                commercialLicensing ? new ConfigurationDialog.LicenseActions(licenseStatus, deactivateLicense,
                        this::updateLicenseControls, openLicenseActivation) : null,
                new ConfigurationDialog.UpdateActions(automaticUpdateChecks.get(), saveAutomaticUpdateChecks,
                        applicationUpdates),
                initialPage, new ConfigurationDialog.ArchiveActions(this::resetProgramConfiguration,
                        this::importCatalog, this::exportCatalog,
                        List.of(
                        new ConfigurationDialog.ImportAction(localizer.text("import.openSshConfig"), this::importOpenSshCatalog),
                        new ConfigurationDialog.ImportAction(localizer.text("import.program", "SecureCRT XML"), this::importSecureCrtCatalog),
                        new ConfigurationDialog.ImportAction(localizer.text("import.program", "MobaXterm"), this::importMobaXtermCatalog),
                        new ConfigurationDialog.ImportAction(localizer.text("import.program", "XShell"), this::importXShellCatalog),
                        new ConfigurationDialog.ImportAction(localizer.text("import.program", "PuTTY"), this::importPuttyCatalog),
                        new ConfigurationDialog.ImportAction(localizer.text("import.program", "Termius"), this::importTermiusCatalog),
                        new ConfigurationDialog.ImportAction(localizer.text("import.program", "Bitvise SSH Client"), this::importBitviseCatalog))));
    }

    public void configureDistribution(boolean commercialLicensing, boolean applicationUpdates) {
        this.commercialLicensing = commercialLicensing;
        this.applicationUpdates = applicationUpdates;
    }

    public void configureSoftwareUpdates(
            java.util.function.Supplier<Boolean> automaticChecks,
            java.util.function.Function<Boolean, CompletionStage<Void>> saveAutomaticChecks,
            Runnable manualCheck) {
        this.automaticUpdateChecks = Objects.requireNonNull(automaticChecks, "automaticChecks");
        this.saveAutomaticUpdateChecks = Objects.requireNonNull(saveAutomaticChecks, "saveAutomaticChecks");
        this.manualUpdateCheck = Objects.requireNonNull(manualCheck, "manualCheck");
    }

    @FXML
    private void checkForUpdates() {
        manualUpdateCheck.run();
    }

    private java.util.concurrent.CompletionStage<Void> saveStartupDockLayout(
            ConfigurationDialog.WorkspaceLayout selected) {
        GlobalSettings previousSettings = persistedWorkspaceSettings;
        WindowSettings selectedWindowSettings = selected.windowSettings();
        if (selectedWindowSettings.restoreWindowBounds() && mainWindow != null) {
            selectedWindowSettings = selectedWindowSettings.withLastBounds(new WindowBounds(
                    mainWindow.getX(), mainWindow.getY(), mainWindow.getWidth(), mainWindow.getHeight()));
        }
        GlobalSettings settings = new GlobalSettings(
                selected.leftDock(), selected.rightDock(), selected.bottomDock(),
                leftDockWidth, rightDockWidth,
                selected.hostLibraryDock(), selected.quickConnectDock(),
                selected.snippetsDock(), selected.variableLibraryDock(), selected.credentialVaultDock(),
                selected.aiAssistantDock(),
                selected.diagnosticLogDock(),
                persistedWorkspaceSettings.collapsedHostGroupIds(),
                selected.hostTabTitleParts(), selected.showSftpDirectoryTree(),
                selected.terminalPreferences(), selected.sftpSettings(), selected.connectionSettings(),
                selected.openLocalTerminalOnStartup(), selectedWindowSettings,
                selected.leftDockDisplayMode(), selected.rightDockDisplayMode(),
                persistedWorkspaceSettings.terminalActivityHeight(),
                persistedWorkspaceSettings.sftpActivityHeight(),
                reconciledToolOrder(DockSide.LEFT, selected, persistedWorkspaceSettings.leftToolOrder()),
                reconciledToolOrder(DockSide.RIGHT, selected, persistedWorkspaceSettings.rightToolOrder()),
                selected.commandPaletteSettings(), selected.diagnosticLogSettings(),
                persistedWorkspaceSettings.aiSessionsCollapsed());
        // Publish the new value before the asynchronous write. Automatic dock-size saves can otherwise
        // capture the previous startup flag and overwrite this request after it has completed.
        persistedWorkspaceSettings = settings;
        setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6254a801e3973d99"), StatusTone.WORKING);
        return settingsWorkflow.save(settings).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (error == null) {
                diagnosticLogOutput.configure(settings.diagnosticLogSettings());
                updateDiagnosticLogPath();
                applyApplicationPreferences(settings);
                boolean treeChanged = previousSettings.showSftpDirectoryTree()
                        != settings.showSftpDirectoryTree();
                boolean hiddenFilesChanged = previousSettings.sftpSettings().showHiddenFiles()
                        != settings.sftpSettings().showHiddenFiles();
                boolean localFileTreeChanged = previousSettings.windowSettings()
                        .showLocalFileDirectoryTree()
                        != settings.windowSettings().showLocalFileDirectoryTree();
                boolean localFileHiddenChanged = previousSettings.windowSettings()
                        .showLocalFileHiddenFiles()
                        != settings.windowSettings().showLocalFileHiddenFiles();
                if (previousSettings.leftDockDisplayMode() != settings.leftDockDisplayMode()
                        || previousSettings.rightDockDisplayMode() != settings.rightDockDisplayMode()) {
                    rebuildVisibleDockPresentations();
                }
                applyToolDockAssignments(settings);
                refreshWorkspaceTabTitles();
                if (treeChanged || localFileTreeChanged) refreshSftpWorkspaceLayouts();
                else if (hiddenFilesChanged) {
                    new HashSet<>(openSessions.values()).stream()
                            .filter(session -> session.state == ConnectionState.CONNECTED)
                            .filter(session -> session.sftp != null || session.terminal != null)
                            .forEach(this::refreshCurrentSftpPanel);
                }
                if (localFileTreeChanged || localFileHiddenChanged) {
                    localFileWorkspaces.values().forEach(controller ->
                            controller.applyDisplaySettings(
                                    settings.windowSettings().showLocalFileHiddenFiles(),
                                    settings.windowSettings().showLocalFileDirectoryTree()));
                    if (!localFileTreeChanged) {
                        new HashSet<>(openSessions.values()).stream()
                                .filter(session -> session.state == ConnectionState.CONNECTED)
                                .filter(session -> session.sftp != null || session.terminal != null)
                                .forEach(session -> refreshLocalDirectoryViews(
                                        session, session.currentLocalPath));
                    }
                }
                applyTerminalPreferences(settings);
                if (textEditors != null) textEditors.applySettings();
                setStatus(settingsSavedStatus(
                                localizer,
                                previousSettings.windowSettings().interfaceLanguage(),
                                settings.windowSettings().interfaceLanguage()),
                        StatusTone.IDLE);
            } else {
                if (persistedWorkspaceSettings == settings) {
                    persistedWorkspaceSettings = previousSettings;
                    applyApplicationPreferences(previousSettings);
                    rebuildVisibleDockPresentations();
                }
                setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.12299e4f6fb6a16e")), StatusTone.ERROR);
            }
        }));
    }

    static String settingsSavedStatus(
            com.ynpe.fastremote.i18n.Localizer localizer,
            com.ynpe.fastremote.domain.settings.InterfaceLanguage previousLanguage,
            com.ynpe.fastremote.domain.settings.InterfaceLanguage savedLanguage) {
        return localizer.text(previousLanguage != savedLanguage
                ? "settings.language.savedRestart"
                : "legacy.java.19594093c677fedd");
    }

    private void applyWorkspaceLayout(ConfigurationDialog.WorkspaceLayout layout) {
        if (layout.leftDock() != hostSidebarVisible) {
            toggleLeftDock();
        }
        if (layout.rightDock() != rightDockVisible) {
            toggleRightDock();
        }
        bottomDockVisible = layout.bottomDock();
    }

    private void rebuildVisibleDockPresentations() {
        if (hostSidebarVisible) {
            hostSidebarVisible = false;
            setHostSidebarVisible(true);
        }
        if (rightDockVisible) {
            rightDockVisible = false;
            setRightDockVisible(true);
        }
    }

    private void applyWorkspaceSettings(GlobalSettings settings) {
        persistedWorkspaceSettings = settings;
        aiAssistantPageController.setSessionsCollapsed(settings.aiSessionsCollapsed());
        updateDiagnosticLogPath();
        applyApplicationPreferences(settings);
        expansionStateDirty = false;
        synchronizeGroupExpansion();
        applyToolDockAssignments(settings);
        leftDockWidth = settings.leftDockWidth();
        rightDockWidth = settings.rightDockWidth();
        if (horizontalWorkbenchSplit.getItems().contains(hostSidebar)) {
            lockDockWidthForReveal(hostSidebar, leftDockWidth);
        }
        if (horizontalWorkbenchSplit.getItems().contains(rightDock)) {
            lockDockWidthForReveal(rightDock, rightDockWidth);
        }
        applyWorkspaceLayout(new ConfigurationDialog.WorkspaceLayout(
                settings.leftDockVisible() && selectedLeftTool != null
                        && settings.leftDockDisplayMode() == DockDisplayMode.EMBEDDED,
                settings.rightDockVisible() && selectedRightTool != null
                        && settings.rightDockDisplayMode() == DockDisplayMode.EMBEDDED,
                settings.bottomDockVisible(),
                settings.hostLibraryDock(), settings.quickConnectDock(),
                settings.snippetsDock(), settings.variableLibraryDock(), settings.credentialVaultDock(),
                settings.aiAssistantDock(),
                settings.diagnosticLogDock(),
                settings.hostTabTitleParts(), settings.showSftpDirectoryTree(),
                settings.terminalPreferences(), settings.sftpSettings(), settings.connectionSettings(),
                settings.openLocalTerminalOnStartup(), settings.windowSettings(),
                settings.leftDockDisplayMode(), settings.rightDockDisplayMode(),
                settings.commandPaletteSettings(), settings.diagnosticLogSettings()));
        applyTerminalPreferences(settings);
        if (textEditors != null) textEditors.applySettings();
        refreshWorkspaceTabTitles();
        requestHorizontalDockWidthRestore();
        refreshToolWindowButtonSelection();
        updateDockDisplayModeButtons();
    }

    private void updateDiagnosticLogPath() {
        if (diagnosticLogPathLabel == null || diagnosticLogPathTooltip == null) return;
        String path = diagnosticLogOutput.currentFile().toString();
        String prefix = persistedWorkspaceSettings.diagnosticLogSettings().enabled()
                ? com.ynpe.fastremote.i18n.Texts.text("diagnostic.log.currentFile")
                : com.ynpe.fastremote.i18n.Texts.text("diagnostic.log.disabledFile");
        diagnosticLogPathLabel.setText(prefix + (prefix.endsWith(":") ? " " : "") + path);
        diagnosticLogPathTooltip.setText(path);
    }

    private void applyApplicationPreferences(GlobalSettings settings) {
        GlobalToolbarPresentation.apply(mainToolbar, settings.windowSettings().toolbarDisplayMode());
        ThemeManager.setTheme(settings.windowSettings().applicationTheme());
        setGlobalToolbarVisible(mainToolbar, globalToolbarMenuItem,
                settings.windowSettings().showGlobalToolbar());
        setGlobalStatusBarVisible(statusBar, globalStatusBarMenuItem,
                settings.windowSettings().showGlobalStatusBar());
        applyToolbarButtonVisibility(settings.windowSettings().toolbarButtons());
        if (keyboardShortcutManager == null) return;
        keyboardShortcutManager.apply(settings.windowSettings().keyboardShortcuts());
        TerminalShortcutSettings terminalShortcuts = terminalShortcutSettings();
        openSessions.values().stream().map(session -> session.terminal).filter(Objects::nonNull)
                .forEach(terminal -> terminal.applyKeyboardShortcuts(terminalShortcuts));
        localTerminals.values().forEach(terminal -> terminal.applyKeyboardShortcuts(terminalShortcuts));
        configureMenuShortcut(commandPaletteMenuItem, ShortcutAction.OPEN_COMMAND_PALETTE);
        configureMenuShortcut(aiAssistantMenuItem, ShortcutAction.TOGGLE_AI_ASSISTANT);
        configureMenuShortcut(diagnosticLogMenuItem, ShortcutAction.TOGGLE_DIAGNOSTIC_LOG);
        configureMenuShortcut(localTerminalMenuItem, ShortcutAction.OPEN_LOCAL_TERMINAL);
        configureMenuShortcut(quickConnectMenuItem, ShortcutAction.OPEN_QUICK_CONNECT);
        configureMenuShortcut(snippetLibraryMenuItem, ShortcutAction.OPEN_SNIPPET_LIBRARY);
        configureMenuShortcut(createHostMenuItem, ShortcutAction.CREATE_HOST);
        configureMenuShortcut(hostLibraryMenuItem, ShortcutAction.OPEN_HOST_LIBRARY);
        configureMenuShortcut(reconnectMenuItem, ShortcutAction.RECONNECT_ACTIVE_SESSION);
        configureMenuShortcut(settingsMenuItem, ShortcutAction.OPEN_SETTINGS);
    }

    private void applyToolbarButtonVisibility(Set<GlobalToolbarButton> buttons) {
        setToolbarButtonVisible(toolbarCommandLibraryButton,
                buttons.contains(GlobalToolbarButton.COMMAND_LIBRARY));
        setToolbarButtonVisible(toolbarGlobalFavoritesButton,
                buttons.contains(GlobalToolbarButton.GLOBAL_FAVORITES));
        setToolbarButtonVisible(toolbarEditorButton, buttons.contains(GlobalToolbarButton.EDITOR));
        setToolbarButtonVisible(toolbarAiAssistantButton,
                buttons.contains(GlobalToolbarButton.AI_ASSISTANT));
        setToolbarButtonVisible(toolbarAiAssistantDialogButton,
                buttons.contains(GlobalToolbarButton.AI_ASSISTANT_DIALOG));
        setToolbarButtonVisible(toolbarDiagnosticLogButton,
                buttons.contains(GlobalToolbarButton.DIAGNOSTIC_LOG));
    }

    static void setToolbarButtonVisible(Button button, boolean visible) {
        button.setVisible(visible);
        button.setManaged(visible);
    }

    static void setGlobalToolbarVisible(
            ToolBar toolbar, CheckMenuItem menuItem, boolean visible) {
        setManagedVisible(toolbar, visible);
        menuItem.setSelected(visible);
    }

    static void setGlobalStatusBarVisible(
            HBox statusBar, CheckMenuItem menuItem, boolean visible) {
        setManagedVisible(statusBar, visible);
        menuItem.setSelected(visible);
    }

    private void configureMenuShortcut(MenuItem item, ShortcutAction action) {
        item.setOnAction(event -> runShortcut(action));
        item.setAccelerator(keyboardShortcutManager.combination(action));
    }

    private void configureTextEditorMenuShortcuts() {
        setMenuShortcut(newEditorDocumentMenuItem, "Shortcut+Alt+N");
        setMenuShortcut(openTextFileMenuItem, "Shortcut+O");
        setMenuShortcut(saveTextFileMenuItem, "Shortcut+S");
        setMenuShortcut(saveTextFileAsMenuItem, "Shortcut+Shift+S");
        setMenuShortcut(saveAllTextFilesMenuItem, "Shortcut+Alt+S");
        setMenuShortcut(closeTextFileMenuItem, "Shortcut+W");
        letFocusedTextControlOwnNativeShortcuts(cutTextMenuItem, copyTextMenuItem, pasteTextMenuItem);
        // Find and replace belong to the application menu. EmbeddedTextEditor deliberately does not
        // install a second key filter for these combinations.
        setMenuShortcut(findTextMenuItem, "Shortcut+F");
        setMenuShortcut(replaceTextMenuItem, "Shortcut+R");
    }

    static void letFocusedTextControlOwnNativeShortcuts(MenuItem... items) {
        // RichTextFX and standard JavaFX text controls already handle Shortcut+X/C/V. Registering the
        // same combinations on the MenuBar runs both the menu action and the focused control action.
        Arrays.stream(items).forEach(item -> item.setAccelerator(null));
    }

    static void setMenuShortcut(MenuItem item, String shortcut) {
        item.setAccelerator(KeyCombination.valueOf(shortcut));
    }

    private TerminalShortcutSettings terminalShortcutSettings() {
        return new TerminalShortcutSettings(
                shortcutName(ShortcutAction.TERMINAL_COPY),
                shortcutName(ShortcutAction.TERMINAL_PASTE),
                shortcutName(ShortcutAction.TERMINAL_FIND));
    }

    private String shortcutName(ShortcutAction action) {
        javafx.scene.input.KeyCombination combination = shortcutCombination(action);
        return combination == null ? action.defaultShortcut() : combination.getName();
    }

    private javafx.scene.input.KeyCombination shortcutCombination(ShortcutAction action) {
        return keyboardShortcutManager == null
                ? action.defaultCombination() : keyboardShortcutManager.combination(action);
    }

    private void showShortcut(MenuItem item, ShortcutAction action) {
        item.setAccelerator(shortcutCombination(action));
    }

    private void runShortcut(ShortcutAction action) {
        if (!shortcutDispatchGate.begin(action)) return;
        try {
            switch (action) {
                case OPEN_COMMAND_PALETTE -> showCommandPalette();
                case OPEN_LOCAL_TERMINAL -> openLocalTerminal();
                case OPEN_QUICK_CONNECT -> showQuickConnectDialog();
                case CREATE_HOST -> createHost();
                case OPEN_HOST_LIBRARY -> showHostLibraryDialog();
                case OPEN_SNIPPET_LIBRARY -> showSnippetLibraryDialog();
                case CLOSE_ACTIVE_TAB -> {
                    Tab selected = selectedWorkspaceTab();
                    if (selected != null && workspacePanes.containsPane(selected)) closeWorkspacePane(selected);
                    else if (selected != null) closeMainTab(selected);
                }
                case RECONNECT_ACTIVE_SESSION -> reconnectSelectedHost();
                case NEXT_TAB -> selectRelativeTab(1);
                case PREVIOUS_TAB -> selectRelativeTab(-1);
                case OPEN_SETTINGS -> showSettings();
                case TOGGLE_HOST_LIBRARY -> toggleHostLibrary();
                case TOGGLE_QUICK_CONNECT -> toggleQuickConnect();
                case TOGGLE_SNIPPETS -> toggleSnippetManager();
                case TOGGLE_CREDENTIALS -> toggleCredentialManager();
                // These legacy action IDs are retained for persisted shortcut compatibility. The
                // global shortcuts now mirror the Tools menu entries and open/focus workspaces;
                // rail buttons continue to own dock-panel toggling.
                case TOGGLE_AI_ASSISTANT -> openAiDialog();
                case TOGGLE_DIAGNOSTIC_LOG -> openDiagnosticLogWorkspace();
                case FOCUS_CONTEXT_SEARCH -> focusContextSearch();
                case OPEN_SELECTED_LIBRARY_ITEM -> runLibraryShortcut(LibraryShortcut.OPEN);
                case EDIT_SELECTED_LIBRARY_ITEM -> runLibraryShortcut(LibraryShortcut.EDIT);
                case COPY_SELECTED_LIBRARY_ITEM -> runLibraryShortcut(LibraryShortcut.COPY);
                case DELETE_SELECTED_LIBRARY_ITEM -> runLibraryShortcut(LibraryShortcut.DELETE);
                case SFTP_REFRESH -> runSftpShortcut(SftpShortcut.REFRESH);
                case SFTP_RENAME -> runSftpShortcut(SftpShortcut.RENAME);
                case SFTP_DELETE -> runSftpShortcut(SftpShortcut.DELETE);
                case SFTP_NEW_FOLDER -> runSftpShortcut(SftpShortcut.NEW_FOLDER);
                case SFTP_UPLOAD -> runSftpShortcut(SftpShortcut.UPLOAD);
                case SFTP_DOWNLOAD -> runSftpShortcut(SftpShortcut.DOWNLOAD);
                case SFTP_EDIT -> runSftpShortcut(SftpShortcut.EDIT);
                case SFTP_TOGGLE_HIDDEN -> runSftpShortcut(SftpShortcut.TOGGLE_HIDDEN);
            }
        } finally {
            Platform.runLater(() -> shortcutDispatchGate.release(action));
        }
    }

    @FXML
    private void showCommandPalette() {
        PaletteWorkspaceContext context = paletteWorkspaceContext();
        CommandPalette.show(rootPane.getScene().getWindow(), commandPaletteEntries(context),
                        Map.copyOf(commandPaletteUsage))
                .ifPresent(entry -> {
                    recordCommandPaletteUse(entry);
                    Platform.runLater(entry.action());
                });
    }

    private void recordCommandPaletteUse(CommandPalette.Entry entry) {
        Instant usedAt = Instant.now();
        CommandPaletteUsage previous = commandPaletteUsage.get(entry.usageKey());
        long count = previous == null ? 1 : Math.min(1_000_000_000L, previous.useCount() + 1);
        commandPaletteUsage.put(entry.usageKey(), new CommandPaletteUsage(entry.usageKey(), count, usedAt));
        commandPaletteUsageWorkflow.recordUse(entry.usageKey(), usedAt).whenComplete((stored, error) -> {
            if (error != null) {
                System.getLogger(MainWindowController.class.getName()).log(
                        System.Logger.Level.WARNING, "Unable to record command palette usage", unwrap(error));
                return;
            }
            Platform.runLater(() -> {
                if (!controllerClosed.get()) commandPaletteUsage.merge(
                        stored.entryKey(), stored, MainWindowController::newerCommandPaletteUsage);
            });
        });
    }

    private List<CommandPalette.Entry> commandPaletteEntries(PaletteWorkspaceContext context) {
        List<CommandPalette.Entry> entries = new ArrayList<>(workspacePaletteEntries(context));
        String editorDetail = com.ynpe.fastremote.i18n.Texts.text("editor.settings.description");
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("editor.title"), editorDetail,
                Icon.FILE_TEXT, textEditors::newDocument, "pinyin:bianjiqi", "pinyin:bjq", "editor"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("action.openFile"), editorDetail,
                Icon.FOLDER, textEditors::openLocalChooser, "pinyin:dakai bendi wenjian", "pinyin:dkbdwj", "open local file", "editor"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("action.save"), editorDetail,
                Icon.FILE, textEditors::save, "pinyin:baocun", "save", "editor"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("action.saveAs"), editorDetail,
                Icon.FILES, textEditors::saveAs, "pinyin:lingcunwei", "save as", "editor"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("action.saveAll"), editorDetail,
                Icon.FILES, textEditors::saveAll, "pinyin:baocun suoyou", "save all", "editor"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("action.closeFile"), editorDetail,
                Icon.FILE, textEditors::closeActive, "pinyin:guanbi", "close file", "editor"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("action.cut"), editorDetail,
                Icon.FILE, textEditors::cut, "pinyin:jianqie", "cut", "editor"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("action.copy"), editorDetail,
                Icon.COPY, textEditors::copy, "pinyin:fuzhi", "copy", "editor"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("action.paste"), editorDetail,
                Icon.FILE_TEXT, textEditors::paste, "pinyin:zhantie", "paste", "editor"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("action.find"), editorDetail,
                Icon.LOCATE_FIXED, textEditors::find, "pinyin:chazhao", "find", "search", "editor"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("action.replace"), editorDetail,
                Icon.PENCIL, textEditors::replace, "pinyin:tihuan", "replace", "editor"));
        String interfaceVisibilityDetail = com.ynpe.fastremote.i18n.Texts.text("workspace.interfaceVisibilityDescription");
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("action.toolbar"), interfaceVisibilityDetail,
                Icon.PANELS_TOP_LEFT, this::toggleGlobalToolbarFromCommandPalette,
                com.ynpe.fastremote.i18n.Texts.text("workspace.showGlobalToolbar"),
                "pinyin:gongjulan xianshi yincang qiehuan", "pinyin:gjl xs yc qh",
                "toolbar", "show hide toggle", "interface", "view"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("action.statusBar"), interfaceVisibilityDetail,
                Icon.MENU, this::toggleGlobalStatusBarFromCommandPalette,
                com.ynpe.fastremote.i18n.Texts.text("workspace.showGlobalStatusBar"),
                "pinyin:zhuangtailan xianshi yincang qiehuan", "pinyin:ztl xs yc qh",
                "status bar", "show hide toggle", "interface", "view"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("legacy.java.bec3d896410b5294"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.5968f8e55b8d678b"), Icon.CABLE, this::showQuickConnectDialog,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.bec3d896410b5294"), "pinyin:kuaisu lianjie", "pinyin:kslj", "quick connect", "quick connection", "conn", "ssh"));
        entries.add(paletteAction(ToolWindow.QUICK_CONNECT.title(), ToolWindow.QUICK_CONNECT.subtitle(), Icon.CABLE,
                this::focusQuickConnect, com.ynpe.fastremote.i18n.Texts.text("action.quickConnect"),
                "pinyin:kuaisu lianjie cebianlan", "pinyin:kslj cbl",
                "quick connect sidebar", "quick connection dock", "sidebar", "dock", "ssh"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("legacy.java.359656f33c8dac51"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.58dd8d99f7ac47cc"), Icon.SERVER,
                this::showHostLibraryDialog, com.ynpe.fastremote.i18n.Texts.text("legacy.java.359656f33c8dac51"), "pinyin:zhuji guanli", "pinyin:zjgl",
                "host manager", "hosts", "host", "host library", "machines"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("legacy.java.634b6989db3a5c41"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.c839e0b300304aaf"), Icon.SERVER,
                () -> showToolWindow(ToolWindow.HOST_LIBRARY, false), com.ynpe.fastremote.i18n.Texts.text("legacy.java.278dda9e9d5dce65"), "pinyin:zhujiku", "pinyin:zjk",
                "host", "hosts", "host library", "machines"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("legacy.java.22a3f0c4b4bd8606"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.febd97853db898c4"), Icon.MONITOR_UP, this::createHost,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.22a3f0c4b4bd8606"), "pinyin:xinjian zhuji", "pinyin:xjzj", "new host", "add", "create", "creat"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("legacy.java.83a36ee5ce1e2979"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.d76a68bc9c8bb62a"), Icon.FOLDER_PLUS, this::createGroup,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.3d49d9fcb6d1bbc5"), "pinyin:xinjian fenzu", "pinyin:xjfz", "new group", "host group", "add", "create", "creat"));
        if (entries.stream().noneMatch(entry -> entry.usageKey().equals("workspace:LOCAL_TERMINAL:NEW_TERMINAL"))) entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a9f4ff897bec7384"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.6dcc1f122d8be8dc"), Icon.SQUARE_TERMINAL,
                this::openLocalTerminal, com.ynpe.fastremote.i18n.Texts.text("legacy.java.51b72143be70e638"), "pinyin:bendi zhongduan", "pinyin:bdzd", "terminal", "shell",
                "console", "new", "add", "create", "creat"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("legacy.java.21a04cdabd8e1ffd"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.91a2a94f9a69c0f1"), Icon.BRACES,
                () -> showToolWindow(ToolWindow.SNIPPETS, false), com.ynpe.fastremote.i18n.Texts.text("legacy.java.4466bdad43dae482"), "pinyin:minglingku", "pinyin:mlk",
                "command library", "commands", "snippet", "snippets", "template"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("legacy.java.cbd43842cef0016c"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.6c9430d77f53c5e5"), Icon.FILE_CODE_2, snippetsPageController::createSnippet,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.cbd43842cef0016c"), "pinyin:xinjian mingling", "pinyin:xjml", "new command", "new snippet", "add", "create", "creat"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("legacy.java.251a5300a581e964"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.f02baf9390c5a7ef"), Icon.FOLDER_PLUS,
                this::createSnippetGroup, com.ynpe.fastremote.i18n.Texts.text("legacy.java.251a5300a581e964"), "pinyin:xinjian mingling fenzu", "pinyin:xmlfz", "command group",
                "snippet group", "add", "create", "creat"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("legacy.java.eab434631b8609da"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.9770715fd9f9e90e"), Icon.VARIABLE,
                () -> showToolWindow(ToolWindow.VARIABLE_LIBRARY, false), com.ynpe.fastremote.i18n.Texts.text("legacy.java.0aefd44f7a90ca71"), "pinyin:bianliangku", "pinyin:blk",
                "variable library", "variables", "vars", "template"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("legacy.java.300a3219c4cdcda8"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.1ea09fcc6c0c11e1"), Icon.PLUS, this::createVariable,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.300a3219c4cdcda8"), "pinyin:xinjian bianliang", "pinyin:xjbl", "new variable", "add", "create", "creat"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("legacy.java.90f0c833f5893bdf"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.c44f8d8cbc62198e"), Icon.LOCK_KEYHOLE,
                () -> showToolWindow(ToolWindow.CREDENTIAL_VAULT, false), com.ynpe.fastremote.i18n.Texts.text("legacy.java.bb2b5d546de68220"), "pinyin:pingzhengku", "pinyin:pzk",
                "credential library", "credentials", "vault", "password", "private key", "certificate"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ee00b9e85cd2e6ae"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.b026338d5d098b40"), Icon.AI,
                this::openAiDialog, com.ynpe.fastremote.i18n.Texts.text("legacy.java.24c41af32e93f424"), "pinyin:zhineng zhushou", "pinyin:znzs",
                "ai assistant", "assistant", "workspace"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7acd82777bebd8f4"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.d47f5ff20084730b"), Icon.AI,
                this::openAiSidebar, com.ynpe.fastremote.i18n.Texts.text("legacy.java.c2329388f19d4d31"), "pinyin:zhineng zhushou cebian", "pinyin:znzscb",
                "ai assistant sidebar", "assistant dock"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("action.openLogWorkspace"),
                com.ynpe.fastremote.i18n.Texts.text("diagnostic.log.toggleWorkspace"), Icon.SCROLL_TEXT,
                this::openDiagnosticLogWorkspace, "pinyin:shenji rizhi gongzuoqu", "pinyin:sjrz gzq",
                "audit log workspace", "diagnostic log workspace", "logs", "errors"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("action.openLogSidebar"),
                com.ynpe.fastremote.i18n.Texts.text("diagnostic.log.dockSidebar"), Icon.SCROLL_TEXT,
                this::openDiagnosticLogSidebar, "pinyin:shenji rizhi cebianlan", "pinyin:sjrz cbl",
                "audit log sidebar", "diagnostic log dock", "logs", "errors"));
        entries.add(paletteAction(com.ynpe.fastremote.i18n.Texts.text("legacy.java.dd9c562cd32d54a5"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.a681f6517bb874b4"), Icon.SETTINGS,
                this::showSettings, com.ynpe.fastremote.i18n.Texts.text("legacy.java.df3d58c7d84b85f2"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.148d195e21b05db5"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.4b097219883abec7"), "pinyin:shezhi", "pinyin:sz", "pinyin:peizhi", "pinyin:pz",
                "settings", "setting", "configuration", "config", "preferences", "preference"));

        CommandPaletteSettings palette = persistedWorkspaceSettings.commandPaletteSettings();
        if (palette.searchCommands()) {
            Map<SnippetGroupId, String> snippetGroups = snippetSnapshot.groups().stream()
                    .collect(java.util.stream.Collectors.toMap(SnippetGroup::id, SnippetGroup::name));
            snippetSnapshot.snippets().stream()
                    .sorted(java.util.Comparator.comparing(Snippet::name, String.CASE_INSENSITIVE_ORDER))
                    .forEach(snippet -> addSnippetPaletteEntries(entries, snippet, snippetGroups, palette));
        }
        if (palette.searchVariables()) addVariablePaletteEntries(entries);
        if (palette.searchFavoritePaths()) addFavoritePathPaletteEntries(entries, palette, context);
        if (palette.searchHosts()) catalog.hosts().stream()
                .sorted(java.util.Comparator.comparing(SavedHost::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(session -> addHostPaletteEntries(entries, session, palette));
        return List.copyOf(entries);
    }

    private List<CommandPalette.Entry> workspacePaletteEntries(PaletteWorkspaceContext context) {
        Tab tab = context.tab();
        if (tab == null) return List.of();
        boolean pinyin = persistedWorkspaceSettings.commandPaletteSettings().pinyinSearch();
        String source = switch (context.workspace()) {
            case SFTP_LOCAL -> tab.getText() + " · " + localizer.text("legacy.java.bf4ad761840fcd0a");
            case SFTP_REMOTE -> tab.getText() + " · " + localizer.text("legacy.java.7ba00927dde9122f");
            default -> tab.getText();
        };
        String detail = localizer.text("commandPalette.currentWorkspace", source);
        Node focus = rootPane.getScene().getFocusOwner();
        LocalFileWorkspaceController files = localFileWorkspaces.get(tab);
        if (files != null) return files.paletteEntries(localizer, pinyin, focus, detail,
                () -> localFileWorkspaces.get(tab) == files, this::workspacePaletteUnavailable);
        var actions = new java.util.EnumMap<WorkspacePaletteActions.Action, Runnable>(WorkspacePaletteActions.Action.class);
        SftpSettings settings = persistedWorkspaceSettings.sftpSettings();
        List<String> paths;
        java.util.function.BooleanSupplier available;
        LocalTerminalHandle terminal = localTerminals.get(tab);
        OpenSession session = openSessions.get(tab);
        if (terminal != null) {
            if (terminal.termination().toCompletableFuture().isDone()) return List.of();
            available = () -> localTerminals.get(tab) == terminal && !terminal.termination().toCompletableFuture().isDone();
            paths = settings.globalBookmarks().localPaths();
            actions.put(FILES, () -> openPairedLocalFiles(tab, terminal));
            terminal.reportedWorkingDirectory().ifPresent(path -> actions.put(COPY_PATH,
                    () -> copyText(List.of(path.toString()))));
            actions.put(DUPLICATE, () -> duplicateLocalTerminal(tab, terminal, null));
            actions.put(NEW_TERMINAL, this::openLocalTerminal);
            actions.put(REVEAL, () -> revealLocalTerminalDirectory(tab, terminal));
        } else if (session != null && session.state == ConnectionState.CONNECTED) {
            available = () -> openSessions.get(tab) == session && session.state == ConnectionState.CONNECTED;
            boolean local = context.workspace() == PaletteWorkspace.SFTP_LOCAL;
            paths = local ? mergedFavoritePaths(settings.globalBookmarks().localPaths(),
                    settings.bookmarksFor(session.bookmarkKey).localPaths())
                    : mergedFavoritePaths(settings.globalBookmarks().remotePaths(),
                    settings.bookmarksFor(session.bookmarkKey).remotePaths());
            if (context.workspace() == PaletteWorkspace.SSH_TERMINAL) {
                actions.put(SFTP, () -> openPairedWorkspace(session, WorkspaceType.SFTP));
                String directory = session.latestInferredWorkingDirectory;
                if (directory != null && !directory.isBlank()) actions.put(COPY_PATH, () -> copyText(List.of(directory)));
                actions.put(DUPLICATE, () -> duplicateRemoteTerminal(session));
                actions.put(NEW_TERMINAL, () -> openIndependentWorkspace(session, WorkspaceType.TERMINAL));
            } else {
                SftpPaletteSelection selection = SftpPaletteSelection.capture(session, local, focus);
                String directory = selection.directory();
                if (directory == null || directory.isBlank()) return List.of();
                String uploadTarget = session.currentRemotePath;
                String downloadTarget = session.currentLocalPath;
                actions.put(TERMINAL, local ? this::goToLocalTerminal
                        : () -> openPairedWorkspace(session, WorkspaceType.TERMINAL));
                actions.put(COPY_PATH, () -> copyText(selection.paths()));
                if (selection.hasSelection()) {
                    actions.put(COPY_NAME, () -> copyText(selection.names()));
                    if (local && uploadTarget != null) actions.put(UPLOAD,
                            () -> uploadEntries(session, selection.localEntries(), uploadTarget));
                    if (!local && downloadTarget != null) actions.put(DOWNLOAD,
                            () -> downloadEntries(session, selection.remoteEntries(), Path.of(downloadTarget)));
                }
                actions.put(FAVORITE, () -> addBookmarkPath(session, local, directory));
                actions.put(REFRESH, local ? () -> refreshLocalDirectoryViews(session, directory)
                        : () -> refreshRemoteDirectoryViews(session, directory));
                actions.put(NEW_FOLDER, local ? () -> createLocalObject(session, Path.of(directory), true)
                        : () -> createRemoteObject(session, directory, true));
            }
        } else return List.of();
        return WorkspacePaletteActions.entries(localizer, pinyin, context.workspace().name(), detail,
                paths, path -> useFavoritePath(context, path, true), actions, available, this::workspacePaletteUnavailable);
    }

    private void workspacePaletteUnavailable() {
        setStatus(localizer.text("commandPalette.workspaceUnavailable"), StatusTone.ERROR);
    }

    private void addFavoritePathPaletteEntries(
            List<CommandPalette.Entry> entries, CommandPaletteSettings settings,
            PaletteWorkspaceContext context) {
        for (String path : allFavoritePaths(persistedWorkspaceSettings.sftpSettings())) {
            String detail = localizer.text("commandPalette.favoritePathDetail", path);
            List<String> terms = List.of(path, localizer.text("commandPalette.favoritePaths"),
                    "path directory folder favorite bookmark");
            if (settings.showFavoritePathGoTo() && supportsFavoritePathGoTo(context.workspace())) {
                entries.add(CommandPalette.Entry.content(
                        localizer.text("commandPalette.favoritePathGoToEntry", path), detail,
                        appendSearchTerms(terms, localizer.text("commandPalette.favoritePathGoTo"),
                                "go goto go-to path directory folder cd jump navigate",
                                "pinyin:zhuandao tiaozhuan lujing zd tz lj"),
                        Icon.LOCATE_FIXED, () -> useFavoritePath(context, path, true)));
            }
            if (settings.showFavoritePathPaste() && context.workspace() != PaletteWorkspace.LOCAL_FILES) {
                entries.add(CommandPalette.Entry.content(
                        localizer.text("commandPalette.favoritePathPasteEntry", path), detail,
                        appendSearchTerms(terms, localizer.text("commandPalette.favoritePathPaste"),
                                "paste path insert", "pinyin:zhantie lujing zt lj"),
                        Icon.FILE_TEXT, () -> useFavoritePath(context, path, false)));
            }
        }
    }

    static List<String> allFavoritePaths(SftpSettings settings) {
        Objects.requireNonNull(settings, "settings");
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        paths.addAll(settings.globalBookmarks().localPaths());
        paths.addAll(settings.globalBookmarks().remotePaths());
        settings.hostBookmarks().values().forEach(bookmarks -> {
            paths.addAll(bookmarks.localPaths());
            paths.addAll(bookmarks.remotePaths());
        });
        return List.copyOf(paths);
    }

    static boolean supportsFavoritePathGoTo(PaletteWorkspace workspace) {
        return workspace != PaletteWorkspace.EDITOR;
    }

    private PaletteWorkspaceContext paletteWorkspaceContext() {
        Tab tab = selectedWorkspaceTab();
        if (tab == null) return new PaletteWorkspaceContext(null, PaletteWorkspace.OTHER);
        if (localTerminals.containsKey(tab)) {
            return new PaletteWorkspaceContext(tab, PaletteWorkspace.LOCAL_TERMINAL);
        }
        if (localFileWorkspaces.containsKey(tab)) {
            return new PaletteWorkspaceContext(tab, PaletteWorkspace.LOCAL_FILES);
        }
        OpenSession session = openSessions.get(tab);
        if (session != null && session.tabs.get(WorkspaceType.TERMINAL) == tab) {
            return new PaletteWorkspaceContext(tab, PaletteWorkspace.SSH_TERMINAL);
        }
        if (session != null && session.tabs.get(WorkspaceType.SFTP) == tab) {
            Node focus = rootPane.getScene().getFocusOwner();
            boolean local = localSftpFocus(session, focus);
            return new PaletteWorkspaceContext(tab,
                    local ? PaletteWorkspace.SFTP_LOCAL : PaletteWorkspace.SFTP_REMOTE);
        }
        if (textEditors != null && textEditors.owns(tab)) {
            return new PaletteWorkspaceContext(tab, PaletteWorkspace.EDITOR);
        }
        return new PaletteWorkspaceContext(tab, PaletteWorkspace.OTHER);
    }

    static boolean localSftpFocus(OpenSession session, Node focus) {
        Objects.requireNonNull(session, "session");
        return isDescendant(focus, session.localBrowser)
                || isDescendant(focus, session.localTable)
                || isDescendant(focus, session.localTree)
                || isDescendant(focus, session.localPathField);
    }

    private void useFavoritePath(PaletteWorkspaceContext context, String path, boolean goTo) {
        if (context.tab() == null) {
            favoritePathUnavailable();
            return;
        }
        switch (context.workspace()) {
            case LOCAL_FILES -> {
                LocalFileWorkspaceController files = localFileWorkspaces.get(context.tab());
                if (files == null || !goTo) favoritePathUnavailable();
                else {
                    try { files.navigateTo(Path.of(path)); }
                    catch (java.nio.file.InvalidPathException invalid) { favoritePathUnavailable(); }
                }
            }
            case LOCAL_TERMINAL -> {
                LocalTerminalHandle terminal = localTerminals.get(context.tab());
                if (terminal == null) favoritePathUnavailable();
                else if (goTo) sendLocalTerminalDirectory(terminal, path);
                else pasteTerminalPath(terminal::sendInput, terminal::requestFocus, path);
            }
            case SSH_TERMINAL -> {
                OpenSession session = openSessions.get(context.tab());
                if (session == null || session.terminal == null
                        || session.state != ConnectionState.CONNECTED) {
                    favoritePathUnavailable();
                } else if (goTo) {
                    sendRemoteTerminalDirectory(session, path);
                } else {
                    pasteTerminalPath(session.terminal::sendInput, session.terminal::requestFocus, path);
                }
            }
            case SFTP_LOCAL, SFTP_REMOTE -> useSftpFavoritePath(context, path, goTo);
            case EDITOR -> {
                if (goTo || !textEditors.insertText(context.tab(), path)) favoritePathUnavailable();
            }
            case OTHER -> favoritePathUnavailable();
        }
    }

    private void useSftpFavoritePath(PaletteWorkspaceContext context, String path, boolean goTo) {
        OpenSession session = openSessions.get(context.tab());
        if (session == null || session.state != ConnectionState.CONNECTED
                || session.localPathField == null || session.remotePathField == null) {
            favoritePathUnavailable();
            return;
        }
        boolean local = context.workspace() == PaletteWorkspace.SFTP_LOCAL;
        if (goTo) {
            if (local) requestLocalDirectory(session, path, session.localTable, session.localPathField);
            else changeSftpRemoteDirectory(session, path);
            return;
        }
        TextField target = local ? session.localPathField : session.remotePathField;
        target.replaceSelection(path);
        target.requestFocus();
    }

    private void pasteTerminalPath(
            Function<String, CompletionStage<Void>> sender, Runnable focus, String path) {
        sender.apply(path).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (error != null) {
                setStatus(messageFor(error, localizer.text("commandPalette.favoritePathUnavailable")),
                        StatusTone.ERROR);
            } else {
                setStatus(localizer.text("commandPalette.favoritePathPasted", path), StatusTone.CONNECTED);
                focus.run();
            }
        }));
    }

    private void favoritePathUnavailable() {
        setStatus(localizer.text("commandPalette.favoritePathUnavailable"), StatusTone.ERROR);
    }

    enum PaletteWorkspace {
        LOCAL_TERMINAL, LOCAL_FILES, SSH_TERMINAL, SFTP_LOCAL, SFTP_REMOTE, EDITOR, OTHER
    }

    private record PaletteWorkspaceContext(Tab tab, PaletteWorkspace workspace) { }

    private void addHostPaletteEntries(
            List<CommandPalette.Entry> entries, SavedHost host, CommandPaletteSettings settings) {
        String detail = HostCatalogModel.homeHostDetail(host, catalog.groups());
        List<String> searchTerms = List.of(host.name(), detail, com.ynpe.fastremote.i18n.Texts.text("legacy.java.88874a358c81f247"), "host machine");
        if (settings.showHostConnect()) {
            entries.add(CommandPalette.Entry.content(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5abe978c0c7fb907") + host.name(), detail,
                    appendSearchTerms(searchTerms, com.ynpe.fastremote.i18n.Texts.text("legacy.java.5fe1f6e088a8fcb8"),
                            "connect terminal open ssh", "pinyin:lianjie zhongduan lj zd"),
                    HostIconGraphics.icon(host.icon()),
                    () -> connectHost(host, WorkspaceType.TERMINAL)));
        }
        if (settings.showHostSftp()) {
            entries.add(CommandPalette.Entry.content(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2c5cf22072a613cc") + host.name(), detail,
                    appendSearchTerms(searchTerms, com.ynpe.fastremote.i18n.Texts.text("legacy.java.cd9958da0971af18"),
                            "open sftp connect files", "pinyin:dakai dk"),
                    Icon.FOLDER, () -> connectHost(host, WorkspaceType.SFTP)));
        }
        if (settings.showHostEdit()) {
            entries.add(CommandPalette.Entry.content(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6af1893a88004001") + host.name(), detail,
                    appendSearchTerms(searchTerms, com.ynpe.fastremote.i18n.Texts.text("legacy.java.f1117a708100eaa7"),
                            "edit modify", "pinyin:bianji bj"), Icon.FILE_COG,
                    () -> editCatalogItem(host, null, rootPane.getScene().getWindow())));
        }
    }

    private void addSnippetPaletteEntries(
            List<CommandPalette.Entry> entries, Snippet snippet, Map<SnippetGroupId, String> groups,
            CommandPaletteSettings settings) {
        String group = snippet.groupId().map(groups::get).filter(Objects::nonNull).orElse(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e86ea3f280d920a2"));
        String detail = com.ynpe.fastremote.i18n.Texts.text("legacy.java.c0ca10f6b342d56c") + group + (snippet.description().isBlank() ? "" : " · " + snippet.description());
        List<String> entityTerms = List.of(snippet.name(), snippet.description(), snippet.content(), group,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.c714184f4f8783df"));
        if (settings.showCommandSend()) entries.add(CommandPalette.Entry.content(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a5b4c16fc472160f") + snippet.name(), detail,
                appendSearchTerms(entityTerms, com.ynpe.fastremote.i18n.Texts.text("legacy.java.abdf9af7e52131d6"),
                        "send execute run command", "pinyin:fasong zhixing fs zx"),
                Icon.FILE_TERMINAL, () -> sendSnippet(snippet, true)));
        if (settings.showCommandPaste()) entries.add(CommandPalette.Entry.content(com.ynpe.fastremote.i18n.Texts.text("legacy.java.dcdde2c00c44e9c0") + snippet.name(), detail,
                appendSearchTerms(entityTerms, com.ynpe.fastremote.i18n.Texts.text("legacy.java.69c769b850171875"),
                        "paste insert command", "pinyin:zhantie zt"),
                Icon.FILE_TEXT, () -> sendSnippet(snippet, false)));
        if (settings.showCommandEdit()) entries.add(CommandPalette.Entry.content(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1ee2609b2cb4940d") + snippet.name(), detail,
                appendSearchTerms(entityTerms, com.ynpe.fastremote.i18n.Texts.text("legacy.java.f1117a708100eaa7"),
                        "edit modify command", "pinyin:bianji bj"),
                Icon.FILE_COG, () -> editSnippet(snippet)));
    }

    private void addVariablePaletteEntries(List<CommandPalette.Entry> entries) {
        Map<SnippetVariableGroupId, String> groups = snippetSnapshot.variableGroups().stream()
                .collect(java.util.stream.Collectors.toMap(SnippetVariableGroup::id, SnippetVariableGroup::name));
        snippetSnapshot.variables().stream()
                .sorted(java.util.Comparator.comparing(SnippetVariable::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(variable -> {
                    String group = variable.groupId().map(groups::get).filter(Objects::nonNull).orElse(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e86ea3f280d920a2"));
                    String detail = variable.variableName() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.bc0520b3720d9fc4") + group
                            + (variable.description().isBlank() ? "" : " · " + variable.description());
                    List<String> terms = List.of(variable.name(), variable.variableName(), variable.value(),
                            variable.description(), group, com.ynpe.fastremote.i18n.Texts.text("legacy.java.9860501e66cc9f79"));
                    entries.add(CommandPalette.Entry.content(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ffd89ff9544f64fe") + variable.name(), detail,
                            appendSearchTerms(terms, com.ynpe.fastremote.i18n.Texts.text("legacy.java.f1117a708100eaa7"),
                                    "edit modify variable", "pinyin:bianji bj"),
                            Icon.FILE_COG, () -> editVariable(variable)));
                });
    }

    private List<String> appendSearchTerms(List<String> shared, String... operationTerms) {
        List<String> terms = new ArrayList<>(shared.size() + operationTerms.length);
        terms.addAll(shared);
        terms.addAll(CommandPalette.enabledSearchTerms(
                persistedWorkspaceSettings.commandPaletteSettings().pinyinSearch(), operationTerms));
        return List.copyOf(terms);
    }

    private CommandPalette.Entry paletteAction(
            String title, String detail, Icon icon, Runnable action, String... searchTerms) {
        boolean pinyinEnabled = persistedWorkspaceSettings.commandPaletteSettings().pinyinSearch();
        List<String> enabledTerms = CommandPalette.enabledSearchTerms(pinyinEnabled, searchTerms);
        return new CommandPalette.Entry(title, detail, enabledTerms, icon, action);
    }

    private void selectRelativeTab(int offset) {
        int size = workspaceTabs.getTabs().size();
        if (size < 2) return;
        int current = workspaceTabs.getSelectionModel().getSelectedIndex();
        workspaceTabs.getSelectionModel().select(Math.floorMod(current + offset, size));
    }

    private void focusContextSearch() {
        Node focus = rootPane.getScene().getFocusOwner();
        if (isDescendant(focus, hostLibraryPage)) hostSearchField.requestFocus();
        else if (isDescendant(focus, snippetsPage)) snippetSearchField.requestFocus();
        else if (isDescendant(focus, variableLibraryPage)) variableSearchField.requestFocus();
        else if (isDescendant(focus, credentialVaultPage)) credentialSearchField.requestFocus();
        else if (isDescendant(focus, quickConnectPage)) hostField.requestFocus();
    }

    private enum LibraryShortcut { OPEN, EDIT, COPY, DELETE }

    private void runLibraryShortcut(LibraryShortcut shortcut) {
        Node focus = rootPane.getScene().getFocusOwner();
        if (isDescendant(focus, hostLibraryPage)) {
            TreeItem<String> item = hostTree.getSelectionModel().getSelectedItem();
            if (item == null) return;
            switch (shortcut) {
                case OPEN -> connectTarget(item, hostItems).ifPresent(this::connectHost);
                case EDIT -> editCatalogItem(item);
                case COPY -> copyCatalogItem(item);
                case DELETE -> deleteCatalogSelection(
                        hostTree, hostItems, groupItems, rootPane.getScene().getWindow());
            }
            return;
        }
        if (isDescendant(focus, snippetsPage)) {
            snippetsPageController.shortcut(switch (shortcut) {
                case OPEN -> ShortcutAction.OPEN_SELECTED_LIBRARY_ITEM;
                case EDIT -> ShortcutAction.EDIT_SELECTED_LIBRARY_ITEM;
                case COPY -> ShortcutAction.COPY_SELECTED_LIBRARY_ITEM;
                case DELETE -> ShortcutAction.DELETE_SELECTED_LIBRARY_ITEM;
            });
            return;
        }
        if (isDescendant(focus, variableLibraryPage)) {
            TreeItem<String> item = variableTree.getSelectionModel().getSelectedItem();
            if (item == null) return;
            SnippetVariable variable = variableItems.get(item);
            SnippetVariableGroup group = variableGroupItems.get(item);
            switch (shortcut) {
                case OPEN, EDIT -> {
                    if (variable != null) editVariable(variable);
                    else if (group != null) showVariableGroupEditor(group);
                }
                case COPY -> { }
                case DELETE -> deleteVariableSelection();
            }
            return;
        }
        if (isDescendant(focus, credentialVaultPage)) {
            TreeItem<String> item = credentialTree.getSelectionModel().getSelectedItem();
            if (item == null) return;
            GlobalCredential credential = credentialItems.get(item);
            CredentialGroup group = credentialGroupItems.get(item);
            switch (shortcut) {
                case OPEN, EDIT -> {
                    if (credential != null) editReusableCredential(credential);
                    else if (group != null) showCredentialGroupEditor(group);
                }
                case COPY -> { }
                case DELETE -> deleteCredentialSelection();
            }
        }
    }

    private enum SftpShortcut { REFRESH, RENAME, DELETE, NEW_FOLDER, UPLOAD, DOWNLOAD, EDIT, TOGGLE_HIDDEN }

    private void runSftpShortcut(SftpShortcut shortcut) {
        OpenSession host = openSessions.get(selectedWorkspaceTab());
        if (host == null || host.state != ConnectionState.CONNECTED || host.sftp == null
                || host.localTable == null || host.remoteTable == null) return;
        Node focus = rootPane.getScene().getFocusOwner();
        boolean local = isDescendant(focus, host.localTable) || isDescendant(focus, host.localTree)
                || isDescendant(focus, host.localPathField);
        List<LocalFileEntry> localSelected = transferableLocalEntries(
                List.copyOf(host.localTable.getSelectionModel().getSelectedItems()));
        List<RemoteFileEntry> remoteSelected = transferableRemoteEntries(
                List.copyOf(host.remoteTable.getSelectionModel().getSelectedItems()));
        switch (shortcut) {
            case REFRESH -> {
                if (local) refreshLocalDirectoryViews(host, host.currentLocalPath);
                else refreshRemoteDirectoryViews(host, host.currentRemotePath);
            }
            case RENAME -> {
                if (local && localSelected.size() == 1) renameLocalObject(host, localSelected.getFirst());
                else if (!local && remoteSelected.size() == 1) renameRemoteObject(host, remoteSelected.getFirst());
            }
            case DELETE -> {
                if (local && !localSelected.isEmpty()) deleteLocalObjects(host, localSelected);
                else if (!local && !remoteSelected.isEmpty()) deleteRemoteObjects(host, remoteSelected);
            }
            case NEW_FOLDER -> {
                if (local) createLocalObject(host, true);
                else createRemoteObject(host, true);
            }
            case UPLOAD -> {
                if (!localSelected.isEmpty()) uploadSelected(host, host.currentRemotePath);
            }
            case DOWNLOAD -> {
                if (!remoteSelected.isEmpty()) downloadSelected(host, host.currentLocalPath);
            }
            case EDIT -> {
                if (local && localSelected.size() == 1) editLocal(host, localSelected.getFirst());
                else if (!local && remoteSelected.size() == 1) editRemote(host, remoteSelected.getFirst());
            }
            case TOGGLE_HIDDEN -> toggleHiddenFiles(host);
        }
    }

    private void applyTerminalPreferences(GlobalSettings settings) {
        workflow.updateTerminalPreferences(settings.terminalPreferences());
        workflow.updateConnectionSettings(settings.connectionSettings());
        workflow.updateSftpSettings(settings.sftpSettings());
        openSessions.values().stream().distinct()
                .map(host -> host.terminal)
                .filter(Objects::nonNull)
                .forEach(terminal -> terminal.applyPreferences(settings.terminalPreferences()));
        localTerminals.values().forEach(terminal -> terminal.applyPreferences(settings.terminalPreferences()));
    }

    private void applyToolDockAssignments(GlobalSettings settings) {
        ToolWindow activeLeft = hostSidebarVisible ? selectedLeftTool : null;
        ToolWindow activeRight = rightDockVisible ? selectedRightTool : null;
        arrangeToolRails(settings);
        selectedLeftTool = preferredToolOn(DockSide.LEFT, settings, activeLeft, activeRight).orElse(null);
        selectedRightTool = preferredToolOn(DockSide.RIGHT, settings, activeRight, activeLeft).orElse(null);
        if (selectedLeftTool != null) installToolWindow(DockSide.LEFT, selectedLeftTool);
        else {
            leftDockContentHost.getChildren().clear();
            setHostSidebarVisible(false);
        }
        if (selectedRightTool != null) installToolWindow(DockSide.RIGHT, selectedRightTool);
        else {
            rightDockContentHost.getChildren().clear();
            setRightDockVisible(false);
        }
        refreshToolWindowButtonSelection();
    }

    private static Optional<ToolWindow> preferredToolOn(
            DockSide side, GlobalSettings settings, ToolWindow first, ToolWindow second) {
        if (first != null && dockSide(first, settings) == side) return Optional.of(first);
        if (second != null && dockSide(second, settings) == side) return Optional.of(second);
        return firstToolOn(side, settings);
    }

    @FXML
    private void showHostLibraryDialog() {
        showHostLibraryDialog(null);
    }

    private void showHostLibraryDialog(Tab insertionAnchor) {
        if (insertionAnchor != null) hostLibraryConnectAnchor = insertionAnchor;
        else if (hostLibraryDialog == null) hostLibraryConnectAnchor = null;
        if (hostLibraryDialog != null) {
            DialogPane pane = hostLibraryDialog.getDialogPane();
            if (pane.getScene() != null && pane.getScene().getWindow().isShowing()) {
                pane.getScene().getWindow().requestFocus();
                if (hostLibrarySearchField != null) {
                    Platform.runLater(hostLibrarySearchField::requestFocus);
                }
            }
            return;
        }
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(rootPane.getScene().getWindow());
        dialog.setResizable(allowsHostLibraryResize());
        dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.359656f33c8dac51"));
        DialogSpacing.prepare(dialog);
        DialogSpacing.configureResizableWindowGeometry(dialog,
                SESSION_MANAGER_INITIAL_WIDTH, SESSION_MANAGER_INITIAL_HEIGHT,
                sessionManagerMinimumWidth(), SESSION_MANAGER_MINIMUM_HEIGHT);
        dialog.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.dd3b527e61ec24e0"));
        ButtonType connect = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a5574109f0208e89"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE, connect);
        dialog.setResultConverter(DialogResultPolicy.explicitOnly());

        TextField search = new TextField();
        InputMethodEnterState.track(search);
        search.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8e38396e9a01604e"));
        search.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.feb95554de093549"));
        hostLibrarySearchField = search;
        ToggleButton allFilter = new ToggleButton(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5c55a67935af8f45"));
        ToggleButton favoriteFilter = new ToggleButton(com.ynpe.fastremote.i18n.Texts.text("legacy.java.60a53514eb9228a2"));
        ToggleButton recentFilter = new ToggleButton(com.ynpe.fastremote.i18n.Texts.text("legacy.java.997a5e6e513f2a90"));
        allFilter.setSelected(true);
        ToggleGroup filters = exclusiveHostFilters(allFilter, favoriteFilter, recentFilter);
        TreeView<String> tree = new TreeView<>();
        tree.setShowRoot(false);
        tree.getStyleClass().add("catalog-tree");
        tree.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.aa06292f69e56993"));
        tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tree.setPrefHeight(480.0);
        Map<TreeItem<String>, SavedHost> dialogHosts = new IdentityHashMap<>();
        Map<TreeItem<String>, HostGroup> dialogGroups = new IdentityHashMap<>();
        HostDragState dragState = new HostDragState();
        Runnable rebuild = () -> populateHostDialogTree(
                tree, dialogHosts, dialogGroups, search.getText(), selectedHostFilter(filters));
        SearchTreeNavigation.install(search, tree, () -> {});
        search.textProperty().addListener((ignored, previous, value) -> rebuild.run());
        filters.selectedToggleProperty().addListener((ignored, previous, selected) -> {
            if (selected == null) {
                if (previous != null) previous.setSelected(true);
            }
            else rebuild.run();
        });
        tree.setCellFactory(ignored -> createHostLibraryTreeCell(
                tree, search, dialogHosts, dialogGroups, dragState, dialog));
        rebuild.run();
        tree.setContextMenu(hostLibraryContextMenu(
                tree, null, dialogHosts, dialogGroups, dialog.getDialogPane().getScene().getWindow()));

        Button newHost = catalogCreateButton(false, com.ynpe.fastremote.i18n.Texts.text("legacy.java.22a3f0c4b4bd8606"));
        Button newGroup = catalogCreateButton(true, com.ynpe.fastremote.i18n.Texts.text("legacy.java.3d49d9fcb6d1bbc5"));
        HBox toolbar = new HBox(6.0, search, newHost, newGroup);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("component-toolbar");
        HBox.setHgrow(search, Priority.ALWAYS);
        Runnable createHost = () -> createHostAt(
                toolbarNewHostGroup(), dialog.getDialogPane().getScene().getWindow());
        Runnable createGroup = () -> {
            TreeItem<String> selected = tree.getSelectionModel().getSelectedItem();
            Optional<GroupId> parent = suggestedPrimaryGroup(
                    dialogHosts.get(selected), dialogGroups.get(selected));
            createGroupAt(parent, dialog.getDialogPane().getScene().getWindow());
        };
        newHost.setOnAction(event -> createHost.run());
        newGroup.setOnAction(event -> createGroup.run());

        Label footer = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a87b49bb0a27cf31"));
        footer.getStyleClass().add("host-manager-hint");
        DialogSpacing.configureWrappingLabel(footer);
        HBox filterRow = new HBox(6.0, allFilter, favoriteFilter, recentFilter);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        filterRow.getStyleClass().add("filter-row");
        VBox content = new VBox(8.0, toolbar, filterRow, footer, tree);
        VBox.setMargin(toolbar, new Insets(0.0, 10.0, 0.0, 10.0));
        VBox.setMargin(filterRow, new Insets(0.0, 10.0, 0.0, 10.0));
        VBox.setMargin(footer, new Insets(0.0, 10.0, 0.0, 10.0));
        VBox.setVgrow(tree, Priority.ALWAYS);
        content.getStyleClass().addAll("host-manager-dialog", "catalog-controls-shell");
        content.setMinHeight(0.0);
        dialog.getDialogPane().setContent(content);
        configureSessionManagerResizableLayout(dialog.getDialogPane(), content);
        Button connectButton = (Button) dialog.getDialogPane().lookupButton(connect);
        connectButton.setDisable(true);
        tree.getSelectionModel().selectedItemProperty().addListener((ignored, previous, selected) ->
                connectButton.setDisable(!dialogHosts.containsKey(selected)));
        Runnable connectSelected = () -> {
            SavedHost selected = dialogHosts.get(tree.getSelectionModel().getSelectedItem());
            if (selected == null) return;
            dialog.close();
            java.util.UUID pairingId = java.util.UUID.randomUUID();
            registerWorkspaceInsertionAnchor(pairingId, hostLibraryConnectAnchor);
            connectHost(selected, WorkspaceType.TERMINAL, pairingId);
        };
        connectButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            connectSelected.run();
        });
        tree.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2
                    && dialogHosts.containsKey(tree.getSelectionModel().getSelectedItem())) {
                connectSelected.run();
            }
        });
        tree.setId("hostLibraryPage");
        DialogSpacing.closeOnEscape(dialog, tree);
        content.getStyleClass().add("component-page");
        KeyboardShortcutManager dialogShortcuts = new KeyboardShortcutManager(
                dialog.getDialogPane().getScene(), action -> {
            TreeItem<String> selected = tree.getSelectionModel().getSelectedItem();
            Window owner = dialog.getDialogPane().getScene().getWindow();
            switch (action) {
                case CREATE_HOST -> createHostAt(dialogGroups.containsKey(selected)
                        ? Optional.of(dialogGroups.get(selected).id())
                        : Optional.ofNullable(dialogHosts.get(selected)).flatMap(SavedHost::groupId), owner);
                case FOCUS_CONTEXT_SEARCH, OPEN_HOST_LIBRARY -> search.requestFocus();
                case OPEN_SELECTED_LIBRARY_ITEM -> {
                    if (dialogHosts.containsKey(selected)) connectSelected.run();
                    else if (selected != null) selected.setExpanded(!selected.isExpanded());
                }
                case EDIT_SELECTED_LIBRARY_ITEM -> editCatalogItem(
                        dialogHosts.get(selected), dialogGroups.get(selected), owner);
                case COPY_SELECTED_LIBRARY_ITEM -> copyCatalogItem(
                        dialogHosts.get(selected), dialogGroups.get(selected), owner);
                case DELETE_SELECTED_LIBRARY_ITEM -> deleteCatalogSelection(tree, dialogHosts, dialogGroups, owner);
                default -> { }
            }
        });
        dialogShortcuts.apply(persistedWorkspaceSettings.windowSettings().keyboardShortcuts());
        catalogRefreshListeners.add(rebuild);
        dialog.setOnHidden(event -> catalogRefreshListeners.remove(rebuild));
        dialog.setOnShown(event -> {
            Platform.runLater(() -> DialogSpacing.focusAtEnd(search));
        });
        hostLibraryDialog = dialog;
        try {
            dialog.showAndWait();
        } finally {
            Platform.runLater(() -> {
                if (hostLibraryDialog == dialog) {
                    hostLibraryDialog = null;
                    hostLibrarySearchField = null;
                    hostLibraryConnectAnchor = null;
                }
            });
        }
    }

    private void registerWorkspaceInsertionAnchor(java.util.UUID pairingId, Tab insertionAnchor) {
        if (pairingId == null || insertionAnchor == null || workspacePanes == null
                || !workspacePanes.containsPane(insertionAnchor)) return;
        pendingWorkspaceInsertionAnchors.put(pairingId, insertionAnchor);
    }

    static boolean allowsHostLibraryResize() {
        return true;
    }

    static double sessionManagerMinimumWidth() {
        return SESSION_MANAGER_MINIMUM_WIDTH;
    }

    static void configureSessionManagerResizableLayout(DialogPane pane, Region content) {
        pane.setMinWidth(0.0);
        pane.setMinHeight(0.0);
        pane.setMaxWidth(Double.MAX_VALUE);
        content.setMinWidth(0.0);
        content.setMinHeight(0.0);
        content.setMaxWidth(Double.MAX_VALUE);
    }

    private void populateHostDialogTree(
            TreeView<String> tree,
            Map<TreeItem<String>, SavedHost> hosts,
            Map<TreeItem<String>, HostGroup> groupItems,
            String query,
            HostFilter filter) {
        HostLibraryTree.rebuild(tree, hosts, groupItems, filteredCatalog(catalog, query, filter),
                (item, id) -> registerGroupExpansion(item, hostGroupExpansionKey(id)),
                root -> prepareLibrarySearch(root, groupItems, query));
    }

    private TreeCell<String> createHostLibraryTreeCell(
            TreeView<String> tree,
            TextField search,
            Map<TreeItem<String>, SavedHost> hosts,
            Map<TreeItem<String>, HostGroup> groups,
            HostDragState dragState,
            javafx.scene.control.Dialog<Void> dialog) {
        TreeCell<String> cell = new StableGraphicTreeCell() {
            @Override protected Node createGraphic(TreeItem<String> treeItem) {
                return catalogIcon(groups.get(treeItem), hosts.get(treeItem));
            }
        };
        cell.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            TreeItem<String> item = cell.getTreeItem();
            dragState.pressedSelection = event.getButton() == MouseButton.PRIMARY && item != null
                    && tree.getSelectionModel().getSelectedItems().contains(item)
                    ? List.copyOf(tree.getSelectionModel().getSelectedItems()) : List.of();
        });
        cell.setOnDragDetected(event -> {
            TreeItem<String> item = cell.getTreeItem();
            if (item == null || event.getButton() != MouseButton.PRIMARY
                    || (!hosts.containsKey(item) && !groups.containsKey(item))) return;
            if (!search.getText().isBlank()) {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.95c671b69d6a2c08"), StatusTone.IDLE);
                return;
            }
            dragState.items = dragSelection(item,
                    dragState.pressedSelection.isEmpty()
                            ? List.copyOf(tree.getSelectionModel().getSelectedItems()) : dragState.pressedSelection,
                    hosts, groups);
            var dragboard = cell.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString("fastremote/catalog:" + dragState.items.size());
            dragboard.setContent(content);
            event.consume();
        });
        cell.setOnDragOver(event -> {
            DropPosition position = dropPosition(event.getY(), cell.getHeight(), groups.containsKey(cell.getTreeItem()));
            if (LibraryTreeSupport.validCatalogDropTarget(
                    cell.getTreeItem(), position, dragState.items, hosts, groups)) {
                event.acceptTransferModes(TransferMode.MOVE);
                showDropTarget(cell, position);
                event.consume();
            } else {
                clearDropTarget(cell);
            }
        });
        cell.setOnDragExited(event -> clearDropTarget(cell));
        cell.setOnDragDropped(event -> {
            TreeItem<String> target = cell.getTreeItem();
            DropPosition position = dropPosition(event.getY(), cell.getHeight(), groups.containsKey(target));
            List<SavedHost> draggedHosts = dragState.items.stream()
                    .map(hosts::get).filter(Objects::nonNull).toList();
            List<HostGroup> draggedGroups = dragState.items.stream()
                    .map(groups::get).filter(Objects::nonNull).toList();
            clearDropTarget(cell);
            if (!LibraryTreeSupport.validCatalogDropTarget(
                    target, position, dragState.items, hosts, groups)) return;
            event.setDropCompleted(true);
            dragState.items = List.of();
            CompletionStage<?> move = CompletableFuture.completedFuture(null);
            String label;
            if (!draggedHosts.isEmpty()) {
                Optional<GroupId> destination = dropGroup(target, position, hosts, groups);
                Set<com.ynpe.fastremote.domain.catalog.HostId> movedIds = draggedHosts.stream()
                        .map(SavedHost::id).collect(java.util.stream.Collectors.toSet());
                List<SavedHost> ordered = catalog.hosts().stream()
                        .filter(value -> value.groupId().equals(destination) && !movedIds.contains(value.id()))
                        .sorted(Comparator.comparingInt(SavedHost::sortOrder)
                                .thenComparing(SavedHost::name, String.CASE_INSENSITIVE_ORDER))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                SavedHost targetHost = hosts.get(target);
                int targetIndex = targetHost == null ? ordered.size() : Math.max(0, ordered.indexOf(targetHost));
                if (targetHost != null && position == DropPosition.AFTER) targetIndex++;
                ordered.addAll(targetIndex, draggedHosts);
                for (int index = 0; index < ordered.size(); index++) {
                    SavedHost value = ordered.get(index);
                    int destinationIndex = index;
                    move = move.thenCompose(ignored -> catalogWorkflow.moveHost(
                            value.id(), destination, destinationIndex));
                }
                label = draggedHosts.size() == 1 ? draggedHosts.getFirst().name()
                        : draggedHosts.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.ef19aca25dd22faa");
            } else if (!draggedGroups.isEmpty()) {
                HostGroup targetGroup = groups.get(target);
                Optional<GroupId> parent = position == DropPosition.INTO
                        ? Optional.of(targetGroup.id()) : targetGroup.parentId();
                Set<GroupId> movedIds = draggedGroups.stream().map(HostGroup::id)
                        .collect(java.util.stream.Collectors.toSet());
                List<HostGroup> ordered = catalog.groups().stream()
                        .filter(value -> value.parentId().equals(parent) && !movedIds.contains(value.id()))
                        .sorted(Comparator.comparingInt(HostGroup::sortOrder)
                                .thenComparing(HostGroup::name, String.CASE_INSENSITIVE_ORDER))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                int targetIndex = position == DropPosition.INTO ? ordered.size()
                        : Math.max(0, ordered.indexOf(targetGroup)) + (position == DropPosition.AFTER ? 1 : 0);
                ordered.addAll(targetIndex, draggedGroups);
                for (int index = 0; index < ordered.size(); index++) {
                    HostGroup value = ordered.get(index);
                    int destinationIndex = index;
                    move = move.thenCompose(ignored -> catalogWorkflow.moveGroup(
                            value.id(), parent, destinationIndex));
                }
                label = draggedGroups.size() == 1 ? draggedGroups.getFirst().name()
                        : draggedGroups.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.3ca8a114e74cdddb");
            } else {
                return;
            }
            move.whenComplete((ignored, error) -> Platform.runLater(() -> {
                if (error != null) {
                    setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d3f1e9f6f6e921d1")), StatusTone.ERROR);
                } else {
                    setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c2e2eb5028597df7") + label, StatusTone.IDLE);
                    refreshCatalog();
                }
            }));
            event.consume();
        });
        cell.setOnDragDone(event -> {
            dragState.items = List.of();
            dragState.pressedSelection = List.of();
            clearDropTarget(cell);
        });
        cell.setOnContextMenuRequested(event -> {
            TreeItem<String> item = cell.getTreeItem();
            if (item == null) return;
            selectContextTreeItem(tree, item);
            ContextMenu menu = hostLibraryContextMenu(
                    tree, item, hosts, groups, dialog.getDialogPane().getScene().getWindow());
            menu.show(cell, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        return cell;
    }

    private ContextMenu hostLibraryContextMenu(
            TreeView<String> tree,
            TreeItem<String> item,
            Map<TreeItem<String>, SavedHost> hosts,
            Map<TreeItem<String>, HostGroup> groups,
            Window owner) {
        SavedHost host = hosts.get(item);
        HostGroup group = groups.get(item);
        Optional<GroupId> targetGroup = group != null
                ? Optional.of(group.id())
                : host == null ? Optional.empty() : host.groupId();
        MenuItem newHost = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.22a3f0c4b4bd8606"));
        showShortcut(newHost, ShortcutAction.CREATE_HOST);
        MenuItem newGroup = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3d49d9fcb6d1bbc5"));
        ContextMenu menu = ApplicationContextMenus.create(newHost, newGroup);
        newHost.setOnAction(event -> ApplicationContextMenus.runAfterHide(menu,
                () -> createHostAt(targetGroup, owner)));
        newGroup.setOnAction(event -> ApplicationContextMenus.runAfterHide(menu,
                () -> createGroupAt(suggestedPrimaryGroup(host, group), owner)));
        menu.getStyleClass().add("catalog-context-menu");
        if (host == null && group == null) return withExpansionActions(menu, tree);

        menu.getItems().add(new SeparatorMenuItem());
        if (host != null) {
            MenuItem openTerminal = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b89a0e4584f27ab5"));
            openTerminal.setOnAction(event -> connectHost(host, WorkspaceType.TERMINAL));
            showShortcut(openTerminal, ShortcutAction.OPEN_SELECTED_LIBRARY_ITEM);
            MenuItem openSftp = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3b350c8e49f7b7c3"));
            openSftp.setOnAction(event -> connectHost(host, WorkspaceType.SFTP));
            MenuItem favorite = new MenuItem(host.favorite() ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.dca60869e7d26839") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.991ab30a8e4da7c7"));
            favorite.setOnAction(event -> setHostFavorite(host, !host.favorite()));
            menu.getItems().addAll(openTerminal, openSftp, new SeparatorMenuItem(), favorite);
        }
        MenuItem edit = new MenuItem(host != null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.50158b7fd5855d55") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.d00b2a96c3b35930"));
        edit.setOnAction(event -> {
            TreeItem<String> target = editTarget(item, List.copyOf(tree.getSelectionModel().getSelectedItems()));
            SavedHost selectedHost = hosts.get(target);
            HostGroup selectedGroup = groups.get(target);
            ApplicationContextMenus.runAfterHide(menu, () -> editCatalogItem(selectedHost, selectedGroup, owner));
        });
        MenuItem copy = new MenuItem(host != null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.dd56ba585c080a47") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.124ae8893e84a75e"));
        copy.setOnAction(event -> ApplicationContextMenus.runAfterHide(menu,
                () -> copyCatalogItem(host, group, owner)));
        int selectedCount = selectedCatalogItemCount(tree, hosts, groups);
        MenuItem delete = new MenuItem(selectedCount > 1
                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.435bf8a234fde6f2") : host != null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.ab6b223c2ef992da") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.7605c1d82eed69a5"));
        delete.setOnAction(event -> ApplicationContextMenus.runAfterHide(menu,
                () -> deleteCatalogSelection(tree, hosts, groups, owner)));
        showShortcut(edit, ShortcutAction.EDIT_SELECTED_LIBRARY_ITEM);
        showShortcut(copy, ShortcutAction.COPY_SELECTED_LIBRARY_ITEM);
        showShortcut(delete, ShortcutAction.DELETE_SELECTED_LIBRARY_ITEM);
        menu.getItems().addAll(edit, copy, delete);
        withExpansionActions(menu, tree);
        addHostLibraryCopyActions(menu, tree, hosts, groups);
        return menu;
    }

    private void addHostLibraryCopyActions(ContextMenu menu, TreeView<String> tree,
            Map<TreeItem<String>, SavedHost> hosts, Map<TreeItem<String>, HostGroup> groups) {
        MenuItem copyName = new MenuItem(localizer.text("hostLibrary.copyName"));
        copyName.setOnAction(event -> HostLibraryTree.selectedNames(tree, hosts, groups)
                .ifPresent(this::copyToClipboard));
        menu.getItems().addAll(new SeparatorMenuItem(), copyName);
        if (!selectedHosts(tree, hosts).isEmpty()) {
            MenuItem copyIp = new MenuItem(localizer.text("host.copyIp"));
            copyIp.setOnAction(event -> copyText(selectedHosts(tree, hosts).stream()
                    .map(host -> host.profile().host().value()).toList()));
            menu.getItems().add(copyIp);
        }
    }

    static Optional<String> catalogItemName(SavedHost host, HostGroup group) {
        String name = host != null ? host.name() : group != null ? group.name() : null;
        return name == null || name.isBlank() ? Optional.empty() : Optional.of(name);
    }

    private Optional<GroupId> suggestedPrimaryGroup(SavedHost host, HostGroup group) {
        HostGroup selectedGroup = group;
        if (selectedGroup == null && host != null) {
            selectedGroup = host.groupId().flatMap(this::findGroup).orElse(null);
        }
        if (selectedGroup == null) return Optional.empty();
        return selectedGroup.parentId().isEmpty()
                ? Optional.of(selectedGroup.id()) : selectedGroup.parentId();
    }

    private static Button catalogCreateButton(boolean group, String accessibleText) {
        StackPane graphic = new StackPane();
        graphic.setPrefSize(24.0, 24.0);
        LucideIconView icon = new LucideIconView(group ? Icon.FOLDER_PLUS : Icon.PLUS);
        icon.getStyleClass().add("catalog-create-icon");
        graphic.getChildren().add(icon);
        Button button = new Button();
        button.setGraphic(graphic);
        button.setAccessibleText(accessibleText);
        button.setTooltip(new Tooltip(accessibleText));
        button.getStyleClass().addAll("icon-button", "catalog-create-button");
        return button;
    }

    @FXML
    private void showAbout() {
        AboutDialog.show(rootPane.getScene().getWindow());
    }

    private HostKeyDecision confirmHostKey(HostKeyCheck check) throws InterruptedException {
        return confirmHostKey(check, rootPane.getScene().getWindow());
    }

    private HostKeyDecision confirmHostKey(HostKeyCheck check, Window owner) throws InterruptedException {
        CompletableFuture<HostKeyDecision> answer = new CompletableFuture<>();
        Platform.runLater(() -> showHostKeyDialog(check, owner, answer));
        try {
            return answer.get();
        } catch (InterruptedException cancelled) {
            answer.cancel(false);
            Thread.currentThread().interrupt();
            throw cancelled;
        } catch (ExecutionException dialogFailure) {
            throw new IllegalStateException("Host key dialog failed", dialogFailure.getCause());
        }
    }

    private void showHostKeyDialog(
            HostKeyCheck check, Window owner, CompletableFuture<HostKeyDecision> answer) {
        boolean changed = check.status() == HostKeyStatus.CHANGED;
        appendDiagnosticLog(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f4451bfbc57580d1") + check.presented().host().value() + ':'
                + check.presented().port().value() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.d6e43b5f7012df76") + check.status()
                + com.ynpe.fastremote.i18n.Texts.text("legacy.java.7af19700cda36cd4") + check.presented().algorithm() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.88dc8c52829f0f27")
                + check.presented().sha256Fingerprint(), changed ? StatusTone.ERROR : StatusTone.WORKING);
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(owner);
        alert.setTitle(changed ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.315968c07708c1fb") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.3082c881ae8c78fa"));
        alert.setHeaderText(changed ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.9d5a98fdb3c6fa62") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.2160124fc3e1837c"));
        String previous = check.previouslyTrusted()
                .map(identity -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.684373a2472cde94") + identity.sha256Fingerprint())
                .orElse("");
        alert.setContentText(
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.adcf7c6f428bb647") + check.presented().host().value() + ':' + check.presented().port().value()
                        + com.ynpe.fastremote.i18n.Texts.text("legacy.java.8f73eed2f35c458d") + check.presented().algorithm()
                        + com.ynpe.fastremote.i18n.Texts.text("legacy.java.cbf6f4a51032ed99") + check.presented().sha256Fingerprint()
                        + previous
                        + com.ynpe.fastremote.i18n.Texts.text("legacy.java.49f8f6df932934db"));
        alert.getButtonTypes().setAll(ButtonType.CANCEL, ACCEPT_ONCE, TRUST_AND_SAVE);
        answer.whenComplete((ignored, failure) -> {
            if (answer.isCancelled()) Platform.runLater(alert::close);
        });
        if (answer.isCancelled()) return;
        alert.showAndWait().ifPresentOrElse(selected -> {
            if (selected == TRUST_AND_SAVE) {
                appendDiagnosticLog(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2ddc513ca543a21b"), StatusTone.CONNECTED);
                answer.complete(HostKeyDecision.TRUST_AND_SAVE);
            } else if (selected == ACCEPT_ONCE) {
                appendDiagnosticLog(com.ynpe.fastremote.i18n.Texts.text("legacy.java.27b8992b92b1a8a9"), StatusTone.CONNECTED);
                answer.complete(HostKeyDecision.ACCEPT_ONCE);
            } else {
                appendDiagnosticLog(com.ynpe.fastremote.i18n.Texts.text("legacy.java.438ac75a15372858"), StatusTone.ERROR);
                answer.complete(HostKeyDecision.REJECT);
            }
        }, () -> {
            appendDiagnosticLog(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5faffe3c2ece2b37"), StatusTone.ERROR);
            answer.complete(HostKeyDecision.REJECT);
        });
    }

    private void completeConnection(OpenSession session, TerminalHandle terminal, Throwable error) {
        if (session.closed.get()) {
            if (terminal != null) terminal.close();
            return;
        }
        session.attempt = null;
        session.cancelAttempt = null;
        if (error != null) {
            if (session.pendingCredential != null) session.pendingCredential.close();
            session.pendingCredential = null;
            session.credentialOwner = null;
            Throwable cause = unwrap(error);
            session.state = ConnectionState.DISCONNECTED;
            session.failureMessage = connectionFailureMessage(cause);
            logConnection(session, sshSftpFailureMessage(
                    cause, connectionFailureLog(session.profile, cause)), StatusTone.ERROR);
            appendDiagnosticLog("[SSH][" + connectionLogName(session.name, session.profile)
                    + com.ynpe.fastremote.i18n.Texts.text("legacy.java.df14e50099c5fc2c") + diagnosticFailure(cause), StatusTone.ERROR);
            if (session.connectedOnce) showRetainedWorkspace(session, false);
            else refreshSessionTabs(session);
        } else {
            releaseRetainedConnections(session);
            session.terminal = terminal;
            session.connectedOnce = true;
            terminal.setInputEnabled(true);
            terminal.applyKeyboardShortcuts(terminalShortcutSettings());
            terminal.observeWorkingDirectory(path -> session.latestInferredWorkingDirectory =
                    expandRemoteHomePath(path, session.remoteHomeCaptured ? session.remoteHome : null));
            resolveRemoteHome(session, terminal);
            terminal.setContextMenuItems(() -> remoteTerminalContextMenuItems(session));
            session.state = ConnectionState.CONNECTED;
            String initialTerminalDirectory = session.pendingTerminalDirectory;
            session.pendingTerminalDirectory = null;
            logConnection(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.7baacab84b536bdc"), StatusTone.CONNECTED);
            recordSuccessfulConnection(session);
            refreshSessionTabs(session);
            if (session.initialType == WorkspaceType.TERMINAL
                    && shouldActivateAutomationWorkspace(session.automationOwned, session.automationActivateTab)) {
                Platform.runLater(terminal::requestFocus);
            }
            terminal.termination().whenComplete((termination, terminationError) -> Platform.runLater(() ->
                    terminalTerminated(session, terminal, termination, terminationError)));
            if (session.initialType == WorkspaceType.TERMINAL) {
                var probeSettings = persistedWorkspaceSettings.terminalPreferences().remoteProbeSettings();
                if (probeSettings.probeServerInformation()) loadServerOverview(session, false);
                if (initialTerminalDirectory != null) {
                    sendInitialRemoteTerminalDirectory(session, initialTerminalDirectory, 0);
                }
            }
            if (session.pendingCredential != null && session.credentialOwner != null) {
                CredentialDraft savedDraft = session.pendingCredential;
                SavedHost owner = session.credentialOwner;
                session.pendingCredential = null;
                session.credentialOwner = null;
                updateAuthenticationFields();
                catalogWorkflow.saveHost(owner, Optional.of(savedDraft)).whenComplete((saved, saveError) ->
                        Platform.runLater(() -> {
                            if (saveError != null) {
                                setSessionStatus(StatusModule.TERMINAL, session,
                                        com.ynpe.fastremote.i18n.Texts.text("legacy.java.b5ef23c0617949e3") + messageFor(saveError, com.ynpe.fastremote.i18n.Texts.text("legacy.java.13a46616e16fe5e6")),
                                        StatusTone.ERROR);
                            } else {
                                setSessionStatus(StatusModule.TERMINAL, session,
                                        com.ynpe.fastremote.i18n.Texts.text("legacy.java.8755a2134769666c"), StatusTone.CONNECTED);
                                refreshCatalog();
                            }
                        }));
            }
        }
        updateActions();
    }

    private void completeSftpConnection(OpenSession session, SftpHandle sftp, Throwable error) {
        if (session.closed.get()) { if (sftp != null) sftp.close(); return; }
        session.cancelAttempt = null;
        if (error != null) { completeConnection(session, null, error); return; }
        session.sftp = sftp;
        session.state = ConnectionState.CONNECTED;
        session.connectedOnce = true;
        releaseRetainedConnections(session);
        logConnection(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.c33ecc3025d420de"), StatusTone.CONNECTED);
        recordSuccessfulConnection(session);
        refreshSessionTabs(session);
        sftp.termination().whenComplete((ignored, terminationError) -> Platform.runLater(() ->
                sftpTerminated(session, sftp, terminationError)));
        updateActions();
    }

    private void recordSuccessfulConnection(OpenSession session) {
        catalogWorkflow.recordSuccessfulConnection(
                Optional.ofNullable(session.catalogHostId), session.profile)
                .whenComplete((ignored, error) -> Platform.runLater(() -> {
                    if (controllerClosed.get()) return;
                    if (error != null) {
                        setSessionStatus(statusModule(session), session,
                                com.ynpe.fastremote.i18n.Texts.text("legacy.java.b63b3b2e230024d8") + messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.aac6ef498a335b59")),
                                StatusTone.ERROR);
                    } else {
                        refreshCatalog();
                    }
                }));
    }

    private void connectionProgress(OpenSession session, ConnectionProgress progress) {
        if (session.closed.get() || session.state != ConnectionState.CONNECTING) return;
        String message = session.initialType == WorkspaceType.SFTP
                && progress == ConnectionProgress.SESSION_ESTABLISHED
                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.c22e7c115257e49c")
                : connectionProgressMessage(progress);
        logConnection(session, message, StatusTone.WORKING);
    }

    private void terminalTerminated(
            OpenSession session, TerminalHandle source,
            TerminalTermination termination, Throwable terminationError) {
        if (session.closed.get() || session.terminal != source || session.state != ConnectionState.CONNECTED) return;
        if (terminationError == null && termination != null
                && termination.kind() == TerminalTermination.Kind.NORMAL) {
            String suffix = termination.exitCode().isPresent()
                    ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.e0d74361ab3c131a") + termination.exitCode().getAsInt() : "（EOF）";
            setSessionStatus(StatusModule.TERMINAL, session,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.f70740815179a0d5") + suffix, StatusTone.IDLE);
            Tab terminalTab = session.tabs.get(WorkspaceType.TERMINAL);
            if (terminalTab == null && !session.tabs.isEmpty()) terminalTab = session.tabs.values().iterator().next();
            closeWorkspaceTab(terminalTab, com.ynpe.fastremote.i18n.Texts.text("legacy.java.f70740815179a0d5"));
            return;
        }
        if (terminationError == null && termination != null
                && termination.kind() == TerminalTermination.Kind.IDLE_TIMEOUT) {
            session.state = ConnectionState.DISCONNECTED;
            session.failureMessage = com.ynpe.fastremote.i18n.Texts.text("legacy.java.72e010d7793242ee");
            setSessionStatus(StatusModule.TERMINAL, session,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.6e483ad2a53ee3eb"), StatusTone.IDLE);
            session.terminal.setInputEnabled(false);
            showRetainedWorkspace(session, false);
            updateActions();
            return;
        }
        session.state = ConnectionState.DISCONNECTED;
        session.failureMessage = com.ynpe.fastremote.i18n.Texts.text("legacy.java.e1b4dbc5b9dd356e");
        logConnection(session, sshSftpFailureMessage(terminationError,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.2ea42cbfc63475ae")), StatusTone.ERROR);
        session.terminal.setInputEnabled(false);
        showRetainedWorkspace(session, false);
        updateActions();
    }

    private void sftpTerminated(OpenSession session, SftpHandle source, Throwable terminationError) {
        if (session.closed.get() || session.sftp != source || session.state != ConnectionState.CONNECTED) return;
        session.state = ConnectionState.DISCONNECTED;
        session.failureMessage = com.ynpe.fastremote.i18n.Texts.text("legacy.java.e1b4dbc5b9dd356e");
        if (session.transferQueue != null) session.transferQueue.connectionLost();
        String fallback = terminationError == null
                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.2142e0468703e9a7")
                : com.ynpe.fastremote.i18n.Texts.text("legacy.java.592501cf01a11806");
        logConnection(session, sshSftpFailureMessage(terminationError, fallback), StatusTone.ERROR);
        showRetainedWorkspace(session, false);
        updateActions();
    }

    static String connectionProgressMessage(ConnectionProgress progress) {
        return switch (progress) {
            case NETWORK_CONNECTING -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.5a5918a784b78b65");
            case HOST_KEY_VALIDATING -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.dd89b7aaa9102d76");
            case PASSWORD_AUTHENTICATING -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.2e99d393ec15de37");
            case PRIVATE_KEY_AUTHENTICATING -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.1abdc270c836614f");
            case SESSION_ESTABLISHED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.5691ada15f5680b2");
        };
    }

    static String connectionFailureMessage(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof CancellationException) return com.ynpe.fastremote.i18n.Texts.text("legacy.java.703ed122eea2bf76");
        if (cause instanceof ConnectionException connectionFailure) {
            return switch (connectionFailure.failureType()) {
                case CANCELLED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.703ed122eea2bf76");
                case AUTHENTICATION_FAILED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.2fb9661d2c0d1325");
                case KEY_LOADING_FAILED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.de689a0a1095a3f4");
                case HOST_KEY_REJECTED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.e5bf9fbd8c7e5a79");
                case CONNECTION_TIMED_OUT, CONNECTION_FAILED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.915bbad1cb06888f");
                case SESSION_CLOSED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.721767e184af8342");
            };
        }
        return com.ynpe.fastremote.i18n.Texts.text("legacy.java.c35a3247c413ea4a");
    }

    private static String connectionFailureLog(ConnectionProfile profile, Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof CancellationException) return com.ynpe.fastremote.i18n.Texts.text("legacy.java.0f682468f9cc0ed4");
        if (cause instanceof ConnectionException connectionFailure) {
            return switch (connectionFailure.failureType()) {
                case CANCELLED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.0f682468f9cc0ed4");
                case AUTHENTICATION_FAILED -> profile.authenticationType() == AuthenticationType.PASSWORD
                        ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.b276174af94fc2f1") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.0fc8f63e00c3b783");
                case KEY_LOADING_FAILED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.8bf936f9ce5b8f5d");
                case HOST_KEY_REJECTED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.3b6036eb718397e3");
                case CONNECTION_TIMED_OUT, CONNECTION_FAILED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.156c4a73d3c598f6");
                case SESSION_CLOSED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.721767e184af8342");
            };
        }
        return com.ynpe.fastremote.i18n.Texts.text("legacy.java.89c4766afc85125f");
    }

    private static String diagnosticFailure(Throwable error) {
        StringBuilder detail = new StringBuilder();
        Throwable current = unwrap(error);
        for (int depth = 0; current != null && depth < 5; depth++, current = current.getCause()) {
            if (depth > 0) detail.append(" <- ");
            detail.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                String safe = message.replaceAll("[\\r\\n\\t\\u0000-\\u001f]", " ").strip();
                String lowered = safe.toLowerCase(Locale.ROOT);
                if (lowered.contains("password") || lowered.contains("passphrase")
                        || lowered.contains("private key") || safe.contains("\u5bc6\u7801")
                        || safe.contains("\u53e3\u4ee4") || safe.contains("\u79c1\u94a5")) {
                    safe = com.ynpe.fastremote.i18n.Texts.text("legacy.java.f86a7deba8cbcdbf");
                }
                if (safe.length() > 500) safe = safe.substring(0, 500) + "…";
                detail.append(": ").append(safe);
            }
        }
        return detail.toString();
    }

    private Tab openWorkspaceTab(OpenSession session, WorkspaceType type) {
        Tab existing = session.tabs.get(type);
        if (existing != null) {
            if (shouldActivateAutomationWorkspace(session.automationOwned, session.automationActivateTab)) {
                selectWorkspaceTab(existing);
            }
            return existing;
        }
        Node primary;
        primary = type == WorkspaceType.TERMINAL ? terminalPrimary(session) : sftpPrimary(session);
        Tab tab = new Tab();
        tab.setClosable(true);
        tab.setContent(createWorkspaceShell(session, type, primary));
        tab.setTooltip(new Tooltip(connectionDetail(session.name, session.profile)));
        tab.setOnCloseRequest(event -> {
            event.consume();
            closeWorkspaceTab(tab, com.ynpe.fastremote.i18n.Texts.text("legacy.java.a9504e63d452b238"));
        });
        installWorkspaceTabContextMenu(tab);
        tab.getProperties().put(AUTOMATION_OWNED_TAB_KEY, session.automationOwned);
        session.tabs.put(type, tab);
        openSessions.put(tab, session);
        updateWorkspaceTabGraphic(tab, session, type);
        boolean activate = shouldActivateAutomationWorkspace(
                session.automationOwned, session.automationActivateTab);
        boolean addedBeside = session.insertionAnchor != null
                && workspacePanes.addPaneBeside(tab, session.insertionAnchor,
                WorkspaceLayoutModel.DropEdge.RIGHT, activate);
        if (!addedBeside) workspacePanes.addPane(tab, session.insertionAnchor, activate);
        session.insertionAnchor = null;
        updateWorkspaceVisibility();
        if (shouldActivateAutomationWorkspace(session.automationOwned, session.automationActivateTab)) {
            selectWorkspaceTab(tab);
        }
        return tab;
    }

    private Node terminalPrimary(OpenSession session) {
        TerminalHandle terminal = session.terminal;
        if (session.state == ConnectionState.CONNECTED && terminal != null) {
            StackPane terminalHost = new StackPane(terminal.node());
            terminalHost.getStyleClass().add("terminal-host");
            installTerminalFocusHandler(terminalHost, terminal::requestFocus);
            installRemotePathDrop(terminalHost, session);
            return terminalHost;
        }
        VBox state = new VBox(10.0);
        state.setAlignment(Pos.CENTER);
        state.getStyleClass().add("workspace-connection-state");
        Label title = new Label(session.state == ConnectionState.CONNECTING ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.8be0ea36a9bba286") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.fcdcd99ffae981e0"));
        title.getStyleClass().add("workspace-connection-state-title");
        Label detail = new Label(session.state == ConnectionState.CONNECTING
                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.d1732be268ea4eb1")
                : session.failureMessage);
        detail.setWrapText(true);
        detail.getStyleClass().add("workspace-connection-state-detail");
        state.getChildren().addAll(title, detail);
        if (session.state == ConnectionState.DISCONNECTED) {
            Button retry = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.68891ab35cab9663"));
            retry.getStyleClass().add("primary-button");
            retry.setOnAction(ignored -> requestReconnect(session));
            Button close = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3fd47edce45b3603"));
            close.setOnAction(ignored -> closeSession(session.tabs.get(WorkspaceType.TERMINAL), com.ynpe.fastremote.i18n.Texts.text("legacy.java.a9504e63d452b238")));
            state.getChildren().add(new HBox(8.0, retry, close));
            ((HBox) state.getChildren().getLast()).setAlignment(Pos.CENTER);
        }
        return state;
    }

    static void installTerminalFocusHandler(StackPane terminalHost, Runnable requestFocus) {
        Objects.requireNonNull(terminalHost, "terminalHost");
        Objects.requireNonNull(requestFocus, "requestFocus");
        terminalHost.setOnMouseClicked(event -> {
            // A secondary click opens the terminal's ContextMenu before bubbling here.
            // The handle may defer its focus request, which would then race the popup.
            if (event.getButton() == MouseButton.PRIMARY) requestFocus.run();
        });
    }

    private void installLocalPathDrop(StackPane terminalHost, LocalTerminalHandle terminal) {
        terminalHost.setOnDragOver(event -> {
            if (shouldBubbleTerminalPathDrop(event.getDragboard().getContentTypes())) return;
            if (!TerminalPathDragSupport.localPaths(event.getDragboard()).isEmpty()) {
                event.acceptTransferModes(TransferMode.COPY);
                event.consume();
            }
        });
        terminalHost.setOnDragDropped(event -> {
            if (shouldBubbleTerminalPathDrop(event.getDragboard().getContentTypes())) return;
            List<String> paths = TerminalPathDragSupport.localPaths(event.getDragboard());
            boolean completed = sendLocalTerminalPaths(terminal, paths);
            event.setDropCompleted(completed);
            event.consume();
        });
    }

    private void installRemotePathDrop(StackPane terminalHost, OpenSession session) {
        terminalHost.setOnDragOver(event -> {
            if (shouldBubbleTerminalPathDrop(event.getDragboard().getContentTypes())) return;
            if (!TerminalPathDragSupport.remotePaths(
                    event.getDragboard(), session.pairingId).isEmpty()) {
                event.acceptTransferModes(TransferMode.COPY);
                event.consume();
            }
        });
        terminalHost.setOnDragDropped(event -> {
            if (shouldBubbleTerminalPathDrop(event.getDragboard().getContentTypes())) return;
            List<String> paths = TerminalPathDragSupport.remotePaths(
                    event.getDragboard(), session.pairingId);
            boolean completed = sendRemoteTerminalPaths(session, paths);
            event.setDropCompleted(completed);
            event.consume();
        });
    }

    static boolean shouldBubbleTerminalPathDrop(Set<javafx.scene.input.DataFormat> contentTypes) {
        return WorkspacePaneCoordinator.isWorkspaceDragPayload(contentTypes);
    }

    private boolean sendLocalTerminalPaths(LocalTerminalHandle terminal, List<String> paths) {
        if (paths.isEmpty()) return false;
        final String arguments;
        try {
            arguments = TerminalDirectoryCommand.localArguments(paths, terminal.shellFamily());
        } catch (IllegalArgumentException failure) {
            setStatus(localizer.text("workspace.goTo.failed"), StatusTone.ERROR);
            return false;
        }
        terminal.sendInput(arguments).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (error != null) {
                setStatus(messageFor(error, localizer.text("workspace.goTo.failed")), StatusTone.ERROR);
            } else terminal.requestFocus();
        }));
        return true;
    }

    private boolean sendRemoteTerminalPaths(OpenSession session, List<String> paths) {
        if (paths.isEmpty() || session.terminal == null || session.state != ConnectionState.CONNECTED) {
            return false;
        }
        ServerOverview.SystemFamily family = session.serverOverview == null
                ? ServerOverview.SystemFamily.UNKNOWN : session.serverOverview.systemFamily();
        final String arguments;
        try {
            arguments = TerminalDirectoryCommand.remoteArguments(paths, family,
                    commandShellFamily(family, session.terminal.shellFamily()));
        } catch (IllegalArgumentException failure) {
            setSessionStatus(StatusModule.TERMINAL, session,
                    localizer.text("workspace.goTo.failed"), StatusTone.ERROR);
            return false;
        }
        session.terminal.sendInput(arguments).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (error != null) {
                setSessionStatus(StatusModule.TERMINAL, session,
                        messageFor(error, localizer.text("workspace.goTo.failed")), StatusTone.ERROR);
            } else session.terminal.requestFocus();
        }));
        return true;
    }

    private Node sftpPrimary(OpenSession session) {
        if (session.state == ConnectionState.CONNECTED && session.sftp != null) {
            return createSftpWorkspace(session);
        }
        VBox state = new VBox(10.0);
        state.setAlignment(Pos.CENTER);
        state.getStyleClass().add("workspace-connection-state");
        Label title = new Label(session.state == ConnectionState.CONNECTING ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.c46ca6dd9f84d9a7") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.0eac54f0e99a6cc9"));
        title.getStyleClass().add("workspace-connection-state-title");
        Label detail = new Label(session.state == ConnectionState.CONNECTING
                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.d1732be268ea4eb1")
                : session.failureMessage);
        detail.setWrapText(true);
        detail.getStyleClass().add("workspace-connection-state-detail");
        state.getChildren().addAll(title, detail);
        if (session.state == ConnectionState.DISCONNECTED) {
            Button retry = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.68891ab35cab9663"));
            retry.getStyleClass().add("primary-button");
            retry.setOnAction(ignored -> requestReconnect(session));
            Button close = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3fd47edce45b3603"));
            close.setOnAction(ignored -> closeSession(session.tabs.get(WorkspaceType.SFTP), com.ynpe.fastremote.i18n.Texts.text("legacy.java.a9504e63d452b238")));
            HBox actions = new HBox(8.0, retry, close);
            actions.setAlignment(Pos.CENTER);
            state.getChildren().add(actions);
        }
        return state;
    }

    private RemoteFilesHandle remoteFiles(OpenSession session) {
        RemoteFilesHandle source = session.sftp != null ? session.sftp.remoteFiles()
                : session.terminal != null ? session.terminal.remoteFiles() : null;
        if (source == null) throw new IllegalStateException("SFTP connection is unavailable");
        if (source != session.observationSource) {
            session.observationSource = source;
            session.observationGeneration = java.util.UUID.randomUUID().toString();
            String generation = session.observationGeneration;
            session.observedRemoteFiles = new ObservedRemoteFiles(source, (from, to, result) -> {
                Runnable record = () -> {
                    Tab tab = session.tabs.get(WorkspaceType.SFTP);
                    if (tab == null) tab = session.tabs.get(WorkspaceType.TERMINAL);
                    if (tab != null) observations.mutation(automationWorkspaceId(tab), generation, from, to, result);
                };
                if (Platform.isFxApplicationThread()) record.run(); else Platform.runLater(record);
            });
        }
        return session.observedRemoteFiles;
    }

    private void requestReconnect(OpenSession session) {
        if (session.closed.get() || session.state == ConnectionState.CONNECTING) return;
        if (session.state == ConnectionState.CONNECTED && !confirmConnectedReconnect(session)) return;
        Optional<SavedHost> saved = catalog.hosts().stream()
                .filter(candidate -> candidate.name().equals(session.name)
                        && candidate.profile().equals(session.profile))
                .findFirst();
        if (saved.map(item -> item.jumpHostId().isPresent()).orElse(false)) {
            List<SavedHost> route;
            try {
                route = savedConnectionRoute(saved.orElseThrow());
            } catch (IllegalArgumentException invalid) {
                setSessionStatus(statusModule(session), session,
                        com.ynpe.fastremote.i18n.Texts.translateExternalText(invalid.getMessage()), StatusTone.ERROR);
                return;
            }
            if (route.stream().anyMatch(item -> item.credentialSecretId().isPresent())
                    && catalog.vaultStatus() != VaultStatus.UNLOCKED) {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b70cd0bc9761df7f"), StatusTone.ERROR);
                manageVault();
                return;
            }
            stopCurrentConnectionForReconnect(session);
            session.state = ConnectionState.CONNECTING;
            session.failureMessage = com.ynpe.fastremote.i18n.Texts.text("legacy.java.360f969c99aaa1c4");
            logConnection(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.400af44264f97c73"), StatusTone.WORKING);
            if (session.connectedOnce) showRetainedWorkspace(session, true);
            else refreshSessionTabs(session);
            resolveConnectionRoute(route, 0, new ArrayList<>(), session);
            return;
        }
        if (saved.isPresent() && saved.orElseThrow().credentialSecretId().isPresent()) {
            if (catalog.vaultStatus() != VaultStatus.UNLOCKED) {
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b70cd0bc9761df7f"), StatusTone.ERROR);
                manageVault();
                return;
            }
            setSessionStatus(statusModule(session), session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d137a9b6de875b8e"), StatusTone.WORKING);
            catalogWorkflow.loadCredential(saved.orElseThrow()).whenComplete((credential, error) ->
                    Platform.runLater(() -> {
                        if (error != null) {
                            setSessionStatus(statusModule(session), session,
                                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.5062f0a04675e0ac"), StatusTone.ERROR);
                        } else {
                            beginReconnect(session, credential);
                        }
                    }));
            return;
        }
        if (session.temporaryCredential != null) {
            TemporaryConnectionCredential copy = session.temporaryCredential.copy();
            beginReconnect(session, copy.copySecret(), copy.copyPrivateKey());
            copy.close();
            return;
        }
        promptForReconnectCredential(session);
    }

    private boolean confirmConnectedReconnect(OpenSession session) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(rootPane.getScene().getWindow());
        alert.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.68891ab35cab9663"));
        alert.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ee97f54402afa871"));
        alert.setContentText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b66e4e603a84ea85") + (session.initialType == WorkspaceType.TERMINAL
                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.e2a76ef1f12e147f") : " SFTP") + com.ynpe.fastremote.i18n.Texts.text("legacy.java.abe3d732dbfd0d1b"));
        ButtonType reconnect = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c21030183110f1c4"), ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(ButtonType.CANCEL, reconnect);
        return alert.showAndWait().filter(reconnect::equals).isPresent();
    }

    private void promptForReconnectCredential(OpenSession session) {
        boolean password = session.profile.authenticationType() == AuthenticationType.PASSWORD;
        Dialog<char[]> dialog = new Dialog<>();
        dialog.initOwner(rootPane.getScene().getWindow());
        dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.68891ab35cab9663"));
        dialog.setHeaderText(session.name.isBlank() ? connectionDetail(session.name, session.profile) : session.name);
        DialogSpacing.prepare(dialog);
        RevealablePasswordEditor secret = new RevealablePasswordEditor(password ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.7be1419614f3508d") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.d2ec1829b67bbe98"));
        secret.setPromptText(password ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.5fe8ca081bd504e3") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.e1ad47762150065a"));
        Label validation = new Label();
        validation.getStyleClass().add("vault-password-validation-error");
        validation.setManaged(false);
        validation.setVisible(false);
        dialog.getDialogPane().setContent(new VBox(8.0,
                new Label(password ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.94a36a5efcb1d9f0") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.31f5d63536390b45")), secret, validation));
        ButtonType reconnect = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.68891ab35cab9663"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, reconnect);
        dialog.setResultConverter(button -> button == reconnect ? secret.getText().toCharArray() : null);
        Button action = (Button) dialog.getDialogPane().lookupButton(reconnect);
        action.addEventFilter(ActionEvent.ACTION, event -> {
            if (password && secret.getText().isEmpty()) {
                event.consume();
                validation.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f79c2da2bb2c6440"));
                validation.setManaged(true);
                validation.setVisible(true);
                secret.focusInput();
            }
        });
        dialog.showAndWait().ifPresent(value -> {
            if (session.temporaryCredential != null) session.temporaryCredential.close();
            session.temporaryCredential = new TemporaryConnectionCredential(value, null);
            beginReconnect(session, value, null);
        });
        secret.clear();
    }

    private void beginReconnect(OpenSession session, ResolvedCredential credential) {
        try {
            char[] secret = credential.copySecret();
            byte[] privateKey = credential.copyPrivateKey();
            beginReconnect(session, secret, privateKey);
        } finally {
            credential.close();
        }
    }

    private void beginReconnect(OpenSession session, char[] secret, byte[] privateKey) {
        stopCurrentConnectionForReconnect(session);
        session.state = ConnectionState.CONNECTING;
        session.failureMessage = com.ynpe.fastremote.i18n.Texts.text("legacy.java.360f969c99aaa1c4");
        logConnection(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.a56a437661b4b53b"), StatusTone.WORKING);
        if (session.connectedOnce) showRetainedWorkspace(session, true);
        else refreshSessionTabs(session);
        try {
            startConnection(session, secret, privateKey);
        } catch (RuntimeException failure) {
            failBeforeConnection(session, connectionFailureMessage(failure));
        }
    }

    private void stopCurrentConnectionForReconnect(OpenSession session) {
        if (session.cancelAttempt != null) session.cancelAttempt.run();
        session.cancelAttempt = null;
        session.attempt = null;
        TerminalHandle terminal = session.terminal;
        session.terminal = null;
        if (terminal != null) {
            if (session.connectedOnce && session.state == ConnectionState.DISCONNECTED) {
                session.retainedTerminal = terminal;
                terminal.setInputEnabled(false);
            } else terminal.close();
        }
        SftpHandle sftp = session.sftp;
        session.sftp = null;
        if (sftp != null) {
            if (session.connectedOnce && session.state == ConnectionState.DISCONNECTED) session.retainedSftp = sftp;
            else sftp.close();
        }
        if (session.transferQueue != null) session.transferQueue.connectionLost();
        session.serverOverview = null;
        session.serverOverviewFailure = null;
        session.serverOverviewView = null;
        session.serverOverviewLoading = false;
    }

    private void openPairedWorkspace(OpenSession source, WorkspaceType target) {
        OpenSession paired = findPairedSession(source, target, openSessions.values());
        if (paired != null) {
            Tab pairedTab = paired.tabs.get(target);
            if (pairedTab != null) selectWorkspaceTab(pairedTab);
            return;
        }
        String initialRemotePath = pairedSftpInitialPath(source, target).orElse(null);
        Tab insertionAnchor = relatedPanelInsertionAnchor(
                openPairedPanelInCurrentWorkspace(
                        persistedWorkspaceSettings.windowSettings(), target),
                source.tabs.get(source.initialType));
        if (insertionAnchor != null) {
            pendingWorkspaceInsertionAnchors.put(source.pairingId, insertionAnchor);
        }
        catalog.hosts().stream()
                .filter(saved -> saved.name().equals(source.name) && saved.profile().equals(source.profile))
                .findFirst()
                .ifPresentOrElse(
                        saved -> connectHost(saved, target, source.pairingId, initialRemotePath),
                        () -> {
                            TemporaryConnectionCredential credential = copyTemporaryCredential(source);
                            if (credential != null) {
                                openTemporaryWorkspace(
                                        source.name, source.profile, target, credential, source.pairingId,
                                        initialRemotePath);
                            } else {
                                pendingWorkspaceInsertionAnchors.remove(source.pairingId);
                            }
                        });
    }

    private void openIndependentWorkspace(OpenSession source, WorkspaceType target) {
        openIndependentWorkspace(source, target, null);
    }

    private void openIndependentWorkspace(
            OpenSession source, WorkspaceType target, String initialRemotePath) {
        openIndependentWorkspace(source, target, initialRemotePath, java.util.UUID.randomUUID());
    }

    private boolean openIndependentWorkspace(
            OpenSession source, WorkspaceType target, String initialRemotePath,
            java.util.UUID pairingId) {
        Optional<SavedHost> saved = catalog.hosts().stream()
                .filter(candidate -> candidate.name().equals(source.name)
                        && candidate.profile().equals(source.profile))
                .findFirst();
        if (saved.isPresent()) {
            connectHost(saved.orElseThrow(), target, pairingId, initialRemotePath);
            return true;
        }
        TemporaryConnectionCredential credential = copyTemporaryCredential(source);
        if (credential == null) return false;
        openTemporaryWorkspace(source.name, source.profile, target, credential,
                pairingId, initialRemotePath);
        return true;
    }

    private TemporaryConnectionCredential copyTemporaryCredential(OpenSession source) {
        if (source.temporaryCredential != null) {
            try {
                return source.temporaryCredential.copy();
            } catch (IllegalStateException cleared) {
                // The source panel already released its in-memory quick-connect credential.
            }
        }
        if (!source.closed.get()) {
            setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8e1a5e148b478208"), StatusTone.ERROR);
        }
        return null;
    }

    private void openTemporaryWorkspace(
            String name,
            ConnectionProfile profile,
            WorkspaceType target,
            TemporaryConnectionCredential credential) {
        openTemporaryWorkspace(name, profile, target, credential, java.util.UUID.randomUUID());
    }

    private void openTemporaryWorkspace(
            String name,
            ConnectionProfile profile,
            WorkspaceType target,
            TemporaryConnectionCredential credential,
            java.util.UUID pairingId) {
        openTemporaryWorkspace(name, profile, target, credential, pairingId, null);
    }

    private void openTemporaryWorkspace(
            String name,
            ConnectionProfile profile,
            WorkspaceType target,
            TemporaryConnectionCredential credential,
            java.util.UUID pairingId,
            String initialRemotePath) {
        OpenSession pending = null;
        try {
            pending = createPendingSession(name, profile, target, pairingId);
            applyInitialRemotePath(pending, initialRemotePath);
            pending.temporaryCredential = credential;
            startConnection(pending, credential.copySecret(), credential.copyPrivateKey());
        } catch (RuntimeException failure) {
            credential.close();
            if (pending != null) pending.temporaryCredential = null;
            throw failure;
        }
    }

    static OpenSession findPairedSession(
            OpenSession source, WorkspaceType target, java.util.Collection<OpenSession> sessions) {
        if (source == null || target == null || sessions == null) return null;
        return sessions.stream().filter(candidate -> candidate != null && candidate != source
                        && !candidate.closed.get() && candidate.pairingId.equals(source.pairingId)
                        && candidate.initialType == target && candidate.tabs.containsKey(target))
                .findFirst().orElse(null);
    }

    static Optional<String> pairedSftpInitialPath(OpenSession source, WorkspaceType target) {
        if (source == null || source.initialType != WorkspaceType.TERMINAL
                || target != WorkspaceType.SFTP) {
            return Optional.empty();
        }
        return Optional.ofNullable(source.latestInferredWorkingDirectory)
                .filter(path -> !path.isBlank());
    }

    static String expandRemoteHomePath(String path, String remoteHome) {
        if (path == null || (!path.equals("~") && !path.startsWith("~/"))) return path;
        if (remoteHome == null || remoteHome.isBlank() || ".".equals(remoteHome)) return path;
        String normalizedHome = remoteHome;
        while (normalizedHome.length() > 1 && normalizedHome.endsWith("/")) {
            normalizedHome = normalizedHome.substring(0, normalizedHome.length() - 1);
        }
        if (path.equals("~")) return normalizedHome;
        return normalizedHome.equals("/")
                ? "/" + path.substring(2)
                : normalizedHome + path.substring(1);
    }

    private void resolveRemoteHome(OpenSession session, TerminalHandle terminal) {
        terminal.remoteFiles().canonicalPath(".").whenComplete((remoteHome, error) -> {
            if (error != null || remoteHome == null || remoteHome.isBlank()) return;
            Platform.runLater(() -> {
                if (session.closed.get() || session.terminal != terminal) return;
                session.remoteHome = remoteHome;
                session.remoteHomeCaptured = true;
                session.latestInferredWorkingDirectory = expandRemoteHomePath(
                        session.latestInferredWorkingDirectory, remoteHome);
            });
        });
    }

    static void applyInitialRemotePath(OpenSession session, String initialRemotePath) {
        if (initialRemotePath != null && !initialRemotePath.isBlank()) {
            if (session.initialType == WorkspaceType.TERMINAL) {
                session.pendingTerminalDirectory = initialRemotePath;
            } else {
                session.currentRemotePath = initialRemotePath;
                session.initialRemotePathFallbackPending = true;
            }
        }
    }

    private void refreshSessionTabs(OpenSession session) {
        for (Map.Entry<WorkspaceType, Tab> entry : session.tabs.entrySet()) {
            WorkspaceType type = entry.getKey();
            Tab tab = entry.getValue();
            Node primary = type == WorkspaceType.TERMINAL ? terminalPrimary(session) : sftpPrimary(session);
            workspacePanes.updatePaneContent(tab, createWorkspaceShell(session, type, primary));
            updateWorkspaceTabGraphic(tab, session, type);
        }
        if (openSessions.get(selectedWorkspaceTab()) == session) {
            refreshWorkspaceQuickTools();
        }
    }

    private void showRetainedWorkspace(OpenSession session, boolean reconnecting) {
        if (!WorkspaceViewModel.shouldRetainWorkspace(session.connectedOnce, session.state)) return;
        if (session.terminal != null) session.terminal.setInputEnabled(false);
        if (session.retainedTerminal != null) session.retainedTerminal.setInputEnabled(false);
        for (Map.Entry<WorkspaceType, Tab> entry : session.tabs.entrySet()) {
            if (!(entry.getValue().getContent() instanceof VBox shell)) continue;
            Node primary = (Node) shell.getProperties().get("fastremote.workspace.primary");
            if (primary == null) continue;
            if (!primary.getStyleClass().contains("workspace-retained-content")) {
                primary.getStyleClass().add("workspace-retained-content");
            }
            if (entry.getKey() == WorkspaceType.SFTP) primary.setDisable(true);
            updateWorkspaceTabGraphic(entry.getValue(), session, entry.getKey());
            Label message = new Label(com.ynpe.fastremote.i18n.Texts.text(reconnecting
                    ? "workspace.disconnect.reconnecting" : "workspace.disconnect.message"));
            message.getStyleClass().add("workspace-disconnect-message");
            message.setMinWidth(0.0);
            message.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(message, Priority.ALWAYS);
            Button reconnect = new Button();
            reconnect.setMinWidth(Region.USE_PREF_SIZE);
            reconnect.getStyleClass().addAll("primary-button", "workspace-disconnect-action");
            configureWorkspaceReconnectAction(reconnect, reconnecting,
                    () -> requestReconnect(session), () -> cancelConnection(session));
            Button close = new Button(com.ynpe.fastremote.i18n.Texts.text("workspace.disconnect.close"));
            close.setMinWidth(Region.USE_PREF_SIZE);
            close.getStyleClass().add("workspace-disconnect-action");
            close.setOnAction(ignored -> closeSession(entry.getValue(),
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.a9504e63d452b238")));
            HBox banner = new HBox(8.0, message, reconnect, close);
            banner.setAlignment(Pos.CENTER_LEFT);
            banner.getStyleClass().add("workspace-disconnect-banner");
            replaceWorkspaceDisconnectBanner(shell, banner);
        }
    }

    static void replaceWorkspaceDisconnectBanner(VBox shell, HBox replacement) {
        Objects.requireNonNull(shell, "shell");
        Objects.requireNonNull(replacement, "replacement");
        shell.getChildren().removeIf(node -> node instanceof HBox
                && node.getStyleClass().contains("workspace-disconnect-banner"));
        shell.getChildren().addFirst(replacement);
    }

    static void configureWorkspaceReconnectAction(
            Button action, boolean reconnecting, Runnable reconnect, Runnable cancel) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(reconnect, "reconnect");
        Objects.requireNonNull(cancel, "cancel");
        action.setText(com.ynpe.fastremote.i18n.Texts.text(reconnecting
                ? "workspace.disconnect.cancelReconnect" : "workspace.disconnect.reconnect"));
        action.setDisable(false);
        action.setOnAction(ignored -> {
            if (reconnecting) cancel.run();
            else reconnect.run();
        });
    }

    private static void releaseRetainedConnections(OpenSession session) {
        TerminalHandle terminal = session.retainedTerminal;
        session.retainedTerminal = null;
        if (terminal != null) terminal.close();
        SftpHandle sftp = session.retainedSftp;
        session.retainedSftp = null;
        if (sftp != null) sftp.close();
    }

    private VBox createWorkspaceShell(OpenSession session, WorkspaceType type, Node primary) {
        Node extension = type == WorkspaceType.TERMINAL
                ? createTerminalActivity(session)
                : createTransferActivity(session);
        SplitPane embeddedContent = type == WorkspaceType.SFTP ? new SplitPane() : null;
        StackPane floatingContent = type == WorkspaceType.TERMINAL ? new StackPane(primary) : null;
        Node content;
        if (embeddedContent != null) {
            embeddedContent.setOrientation(javafx.geometry.Orientation.VERTICAL);
            embeddedContent.getItems().add(primary);
            if (workspaceActivityInitiallyVisible(type, bottomDockVisible)) {
                embeddedContent.getItems().add(extension);
                initializeWorkspaceActivityDivider(embeddedContent, type, extension);
            }
            embeddedContent.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
                if (splitPaneDividerTarget(event.getTarget())) {
                    saveWorkspaceActivityHeight(type, renderedHeight(extension));
                }
            });
            embeddedContent.getStyleClass().add("workspace-content-split");
            content = embeddedContent;
        } else {
            content = floatingContent;
        }
        DisconnectedWorkspaceReconnect.install(content,
                () -> canReconnectDisconnectedWorkspace(session), () -> requestReconnect(session));

        Button reconnect = new Button();
        reconnect.setGraphic(workspaceReconnectIcon());
        reconnect.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.68891ab35cab9663"));
        reconnect.setTooltip(new Tooltip(session.state == ConnectionState.CONNECTED
                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.6f31e1e2b8d8a19f") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.fc64ee2e90a8d88f")));
        reconnect.getStyleClass().add("workspace-rail-action");
        reconnect.getStyleClass().add("workspace-icon-button");
        reconnect.setDisable(session.state == ConnectionState.CONNECTING);
        reconnect.setOnAction(event -> requestReconnect(session));
        Separator reconnectSeparator = new Separator(javafx.geometry.Orientation.VERTICAL);
        Button create = workspaceNewSessionButton(
                type, target -> openIndependentWorkspace(session, target));
        Button counterpart = new Button(workspaceCounterpartLabel(type));
        counterpart.getStyleClass().add("workspace-rail-action");
        counterpart.getStyleClass().add("workspace-counterpart-button");
        counterpart.setGraphic(workspaceCounterpartIcon(type));
        counterpart.setOnAction(event -> {
            WorkspaceType target = type == WorkspaceType.TERMINAL ? WorkspaceType.SFTP : WorkspaceType.TERMINAL;
            openPairedWorkspace(session, target);
        });
        counterpart.setDisable(session.state != ConnectionState.CONNECTED);
        Separator separator = new Separator(javafx.geometry.Orientation.VERTICAL);
        Button toggle = type == WorkspaceType.TERMINAL
                ? floatingTerminalActivityButton(floatingContent, extension)
                : embeddedWorkspaceActivityButton(type, embeddedContent, extension);
        Button goTo = remoteFavoritePathButton(session, type);
        Button duplicate = type == WorkspaceType.TERMINAL
                ? workspaceIconButton(Icon.COPY, localizer.text("terminal.context.duplicate"),
                        ignored -> duplicateRemoteTerminal(session))
                : null;
        VBox shell = new VBox(content);
        VBox.setVgrow(content, Priority.ALWAYS);
        shell.getStyleClass().add("session-workspace");
        shell.getProperties().put("fastremote.workspace.primary", primary);
        shell.getProperties().put(WORKSPACE_STATUS_TOOLS_KEY,
                orderedWorkspaceStatusTools(type, reconnect, reconnectSeparator, counterpart,
                        goTo, duplicate, create, separator, toggle));
        return shell;
    }

    static List<Node> orderedWorkspaceStatusTools(
            WorkspaceType type, Node refresh, Node firstSeparator, Node counterpart, Node goTo,
            Node duplicate, Node create, Node secondSeparator, Node toggle) {
        Objects.requireNonNull(type, "type");
        return type == WorkspaceType.TERMINAL
                ? List.of(refresh, firstSeparator, goTo,
                        Objects.requireNonNull(duplicate, "duplicate"), create, counterpart, secondSeparator, toggle)
                : List.of(refresh, firstSeparator, goTo, create, counterpart, secondSeparator, toggle);
    }

    private Button remoteFavoritePathButton(OpenSession session, WorkspaceType type) {
        Button button = favoritePathButton(
                () -> mergedFavoritePaths(
                        persistedWorkspaceSettings.sftpSettings().globalBookmarks().remotePaths(),
                        persistedWorkspaceSettings.sftpSettings()
                                .bookmarksFor(session.bookmarkKey).remotePaths()),
                path -> {
                    if (type == WorkspaceType.TERMINAL) sendRemoteTerminalDirectory(session, path);
                    else changeSftpRemoteDirectory(session, path);
                });
        button.setDisable(session.state != ConnectionState.CONNECTED);
        return button;
    }

    private Button embeddedWorkspaceActivityButton(
            WorkspaceType type, SplitPane content, Node extension) {
        Button toggle = new Button();
        LucideIconView menu = new LucideIconView(Icon.MENU);
        menu.getStyleClass().add("workspace-action-icon");
        toggle.setGraphic(menu);
        toggle.setAccessibleText(type == WorkspaceType.TERMINAL
                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.0f1f51efda0240d8") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.6b16a762fd27c4c6"));
        toggle.setTooltip(new Tooltip(toggle.getAccessibleText()));
        toggle.getStyleClass().add("workspace-activity-button");
        toggle.getStyleClass().add("workspace-icon-button");
        toggle.setOnAction(event -> {
            boolean visible = !content.getItems().contains(extension);
            if (visible) {
                content.getItems().add(extension);
                initializeWorkspaceActivityDivider(content, type, extension);
            } else {
                saveWorkspaceActivityHeight(type, renderedHeight(extension));
                content.getItems().remove(extension);
            }
            setToolWindowButtonSelected(toggle, visible);
        });
        setToolWindowButtonSelected(toggle, content.getItems().contains(extension));
        return toggle;
    }

    private Button floatingTerminalActivityButton(StackPane content, Node extension) {
        Region resizeHandle = new Region();
        resizeHandle.getStyleClass().add("workspace-terminal-resize-handle");
        resizeHandle.setMinHeight(6.0);
        resizeHandle.setPrefHeight(6.0);
        resizeHandle.setMaxHeight(6.0);
        resizeHandle.setCursor(javafx.scene.Cursor.V_RESIZE);
        VBox floating = new VBox(resizeHandle, extension);
        VBox.setVgrow(extension, Priority.ALWAYS);
        floating.setMinHeight(TERMINAL_ACTIVITY_MIN_HEIGHT);
        setFixedFloatingHeight(floating, Math.max(
                TERMINAL_ACTIVITY_MIN_HEIGHT, persistedWorkspaceSettings.terminalActivityHeight()));
        floating.setMaxWidth(Double.MAX_VALUE);
        floating.getStyleClass().add("workspace-terminal-floating");
        StackPane.setAlignment(floating, Pos.BOTTOM_CENTER);
        setManagedVisible(floating, false);
        content.getChildren().add(floating);

        double[] drag = new double[2];
        resizeHandle.setOnMousePressed(event -> {
            drag[0] = event.getSceneY();
            drag[1] = floating.getHeight();
            event.consume();
        });
        resizeHandle.setOnMouseDragged(event -> {
            double maximum = Math.max(TERMINAL_ACTIVITY_MIN_HEIGHT, content.getHeight() * 0.75);
            setFixedFloatingHeight(floating, clamp(drag[1] + drag[0] - event.getSceneY(),
                    TERMINAL_ACTIVITY_MIN_HEIGHT, maximum));
            event.consume();
        });
        resizeHandle.setOnMouseReleased(event -> {
            saveWorkspaceActivityHeight(WorkspaceType.TERMINAL, floating.getHeight());
            event.consume();
        });

        Button toggle = new Button();
        LucideIconView menu = new LucideIconView(Icon.MENU);
        menu.getStyleClass().add("workspace-action-icon");
        toggle.setGraphic(menu);
        toggle.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0f1f51efda0240d8"));
        toggle.setTooltip(new Tooltip(toggle.getAccessibleText()));
        toggle.getStyleClass().add("workspace-activity-button");
        toggle.getStyleClass().add("workspace-icon-button");
        floating.getProperties().put("activityToggle", toggle);
        toggle.setOnAction(event -> {
            boolean visible = !floating.isManaged();
            setManagedVisible(floating, visible);
            if (visible) floating.toFront();
            setToolWindowButtonSelected(toggle, visible);
        });
        setToolWindowButtonSelected(toggle, false);
        content.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            Node target = event.getTarget() instanceof Node node ? node : null;
            if (floating.isManaged() && !isDescendant(target, floating)) {
                setManagedVisible(floating, false);
                setToolWindowButtonSelected(toggle, false);
            }
        });
        return toggle;
    }

    private void initializeWorkspaceActivityDivider(
            SplitPane content, WorkspaceType type, Node extension) {
        SplitPane.setResizableWithParent(extension, false);
        double minimum = type == WorkspaceType.SFTP ? SFTP_ACTIVITY_MIN_HEIGHT : TERMINAL_ACTIVITY_MIN_HEIGHT;
        double savedHeight = type == WorkspaceType.SFTP
                ? persistedWorkspaceSettings.sftpActivityHeight()
                : persistedWorkspaceSettings.terminalActivityHeight();
        double activityHeight = Math.max(minimum, savedHeight);
        if (setInitialWorkspaceActivityDivider(content, activityHeight)) return;
        content.heightProperty().addListener(new javafx.beans.value.ChangeListener<>() {
            @Override
            public void changed(
                    javafx.beans.value.ObservableValue<? extends Number> observable,
                    Number oldHeight,
                    Number newHeight) {
                if (!setInitialWorkspaceActivityDivider(content, activityHeight)) return;
                observable.removeListener(this);
            }
        });
    }

    private static boolean setInitialWorkspaceActivityDivider(SplitPane content, double activityHeight) {
        if (content.getHeight() <= activityHeight) return false;
        content.setDividerPositions(workspaceActivityDivider(
                WorkspaceType.SFTP, content.getHeight(), activityHeight));
        return true;
    }

    static void setFixedFloatingHeight(Region floating, double height) {
        floating.setPrefHeight(height);
        floating.setMaxHeight(height);
    }

    private static double renderedHeight(Node node) {
        double height = node.getBoundsInParent().getHeight();
        return Double.isFinite(height) && height > 0.0 ? height : 0.0;
    }

    private void saveWorkspaceActivityHeight(WorkspaceType type, double renderedHeight) {
        if (!workspaceSettingsLoaded || !Double.isFinite(renderedHeight) || renderedHeight <= 0.0) return;
        double minimum = type == WorkspaceType.TERMINAL
                ? TERMINAL_ACTIVITY_MIN_HEIGHT : SFTP_ACTIVITY_MIN_HEIGHT;
        double height = clamp(renderedHeight, minimum, GlobalSettings.MAX_ACTIVITY_HEIGHT);
        double terminalHeight = type == WorkspaceType.TERMINAL
                ? height : persistedWorkspaceSettings.terminalActivityHeight();
        double sftpHeight = type == WorkspaceType.SFTP
                ? height : persistedWorkspaceSettings.sftpActivityHeight();
        if (Math.abs(terminalHeight - persistedWorkspaceSettings.terminalActivityHeight()) < 0.5
                && Math.abs(sftpHeight - persistedWorkspaceSettings.sftpActivityHeight()) < 0.5) return;
        GlobalSettings settings = persistedWorkspaceSettings.withActivityHeights(terminalHeight, sftpHeight);
        persistedWorkspaceSettings = settings;
        settingsWorkflow.save(settings).whenComplete((ignored, error) -> {
            if (error != null) Platform.runLater(() -> setStatus(
                    messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.08e9f533dc0aad66")), StatusTone.ERROR));
        });
    }

    private HBox createTerminalActivity(OpenSession session) {
        Label title = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e33a98cd3def061f"));
        title.getStyleClass().add("workspace-server-title");
        title.setWrapText(true);
        title.setAlignment(Pos.CENTER);
        title.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        Button refresh = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.aee88743413144a2"));
        refresh.getStyleClass().add("compact-button");
        refresh.setMaxWidth(Double.MAX_VALUE);
        refresh.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5128c3a0f850fd42"));
        VBox actions = new VBox(7.0, title, refresh);
        actions.setAlignment(Pos.TOP_CENTER);
        actions.getStyleClass().add("workspace-server-actions");

        TextArea overview = new TextArea();
        overview.setEditable(false);
        overview.setWrapText(true);
        overview.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0adca20dc7136267"));
        overview.getStyleClass().add("workspace-server-overview");
        session.serverOverviewView = overview;
        refresh.setOnAction(event -> loadServerOverview(session, true));
        boolean probeEnabled = persistedWorkspaceSettings.terminalPreferences()
                .remoteProbeSettings().probeServerInformation();
        refresh.setDisable(!probeEnabled);
        HBox activity = new HBox(7.0, overview, actions);
        HBox.setHgrow(overview, Priority.ALWAYS);
        activity.setMinHeight(TERMINAL_ACTIVITY_MIN_HEIGHT);
        activity.setPrefHeight(persistedWorkspaceSettings.terminalActivityHeight());
        activity.getStyleClass().add("workspace-terminal-activity");
        if (!probeEnabled) overview.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4a3e3512976e2483"));
        else if (session.serverOverview != null) overview.setText(session.serverOverview.text());
        else if (session.serverOverviewLoading) overview.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.14ebae4af34d11b5"));
        else if (session.serverOverviewFailure != null) overview.setText(session.serverOverviewFailure);
        else loadServerOverview(session, false);
        return activity;
    }

    private void loadServerOverview(OpenSession session, boolean force) {
        if (!persistedWorkspaceSettings.terminalPreferences().remoteProbeSettings().probeServerInformation()) {
            if (session.serverOverviewView != null) {
                session.serverOverviewView.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4a3e3512976e2483"));
            }
            return;
        }
        if (session.terminal == null || session.state != ConnectionState.CONNECTED) {
            if (session.serverOverviewView != null) session.serverOverviewView.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a809684825fae26c"));
            return;
        }
        if (session.serverOverviewLoading) return;
        if (!force && session.serverOverview != null) {
            if (session.serverOverviewView != null) session.serverOverviewView.setText(session.serverOverview.text());
            return;
        }
        if (!force && session.serverOverviewFailure != null) {
            if (session.serverOverviewView != null) session.serverOverviewView.setText(session.serverOverviewFailure);
            return;
        }
        session.serverOverviewLoading = true;
        if (force) session.serverOverviewFailure = null;
        TextArea target = session.serverOverviewView;
        if (target != null) target.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.14ebae4af34d11b5"));
        session.terminal.loadServerOverview().whenComplete((overview, error) -> Platform.runLater(() -> {
            session.serverOverviewLoading = false;
            if (session.closed.get()) return;
            if (error == null) {
                session.serverOverview = overview;
                session.serverOverviewFailure = null;
                if (session.serverOverviewView != null) session.serverOverviewView.setText(overview.text());
            } else {
                session.serverOverviewFailure = com.ynpe.fastremote.i18n.Texts.text("legacy.java.77a637561e96eda4") + messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.fab2edc0fdfbaa37"));
                if (session.serverOverviewView != null) {
                    session.serverOverviewView.setText(session.serverOverviewFailure);
                }
            }
        }));
    }

    private SplitPane createTransferActivity(OpenSession session) {
        TableView<SftpTransferJob> transfers = new PersistentHorizontalScrollTableView<>(
                transferItems(session.transferQueue));
        transfers.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        transfers.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        List<Double> widths = defaultTransferColumnWidths();
        transfers.getColumns().setAll(List.of(
                transferColumn(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1121471a0ff440f8"), job -> job.direction() == SftpTransferJob.Direction.UPLOAD ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.9e07e3c0532d4976") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.4673a23061656125"), widths.get(0)),
                transferColumn(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f4e2ebc162b202b1"), SftpTransferJob::source, widths.get(1)),
                transferColumn(com.ynpe.fastremote.i18n.Texts.text("legacy.java.57060c88a36bf3d0"), SftpTransferJob::target, widths.get(2)),
                transferColumn(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ffff18d1c2d15426"), job -> humanSize(job.totalBytesProperty().get()), widths.get(3)),
                transferColumn(com.ynpe.fastremote.i18n.Texts.text("legacy.java.aca85fb30a91ef74"), job -> humanSize(job.transferredBytesProperty().get()), widths.get(4)),
                transferColumn(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8d6d315ea308f36c"), job -> elapsedTime(job.elapsedNanosProperty().get()), widths.get(5)),
                transferColumn(com.ynpe.fastremote.i18n.Texts.text("legacy.java.68af1ac4b8a03627"), job -> speed(job.currentBytesPerSecondProperty().get()), widths.get(6)),
                transferColumn(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f6633bf0a1d35372"), job -> speed(job.averageBytesPerSecond()), widths.get(7)),
                transferColumn(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6320b4a8722a851f"), job -> job.state().label(), widths.get(8))));
        session.transferTable = transfers;
        if (session.transferQueue != null) installTransferContextMenu(session, transfers);
        transfers.setPlaceholder(new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f06b0c2400c2089d")));
        transfers.getStyleClass().add("workspace-transfer-list");
        keepHorizontalScrollBarVisible(transfers);

        TextArea fileLog = new TextArea();
        fileLog.setEditable(false);
        fileLog.setWrapText(false);
        fileLog.getStyleClass().add("workspace-operation-log");
        session.fileLogView = fileLog;
        session.fileLog.attach(fileLog);

        fileLog.setContextMenu(SftpLogContextMenu.create(fileLog, session.fileLog::clear));

        SplitPane activity = new SplitPane(transfers, fileLog);
        activity.setDividerPositions(0.58);
        activity.setMinHeight(SFTP_ACTIVITY_MIN_HEIGHT);
        activity.getStyleClass().add("workspace-transfer-split");
        return activity;
    }

    static ObservableList<SftpTransferJob> transferItems(SftpTransferQueue queue) {
        return queue == null ? FXCollections.observableArrayList() : queue.jobs();
    }

    private static void keepHorizontalScrollBarVisible(TableView<?> table) {
        Runnable reveal = () -> table.lookupAll(".scroll-bar").stream()
                .filter(ScrollBar.class::isInstance)
                .map(ScrollBar.class::cast)
                .filter(scroll -> scroll.getOrientation() == javafx.geometry.Orientation.HORIZONTAL)
                .forEach(scroll -> {
                    scroll.setManaged(true);
                    scroll.setVisible(true);
                    scroll.setOpacity(1.0);
                });
        table.skinProperty().addListener((ignored, previous, current) -> Platform.runLater(reveal));
        table.widthProperty().addListener((ignored, previous, current) -> Platform.runLater(reveal));
        table.getItems().addListener((javafx.beans.InvalidationListener) ignored -> Platform.runLater(reveal));
        Platform.runLater(reveal);
    }

    private static TableColumn<SftpTransferJob, String> transferColumn(
            String title, java.util.function.Function<SftpTransferJob, String> value, double width) {
        TableColumn<SftpTransferJob, String> column = new TableColumn<>(title);
        configureTransferColumnWidth(column, width);
        column.setCellValueFactory(row -> {
            SftpTransferJob job = row.getValue();
            return javafx.beans.binding.Bindings.createStringBinding(
                    () -> value.apply(job),
                    job.stateProperty(), job.totalBytesProperty(), job.transferredBytesProperty(),
                    job.elapsedNanosProperty(), job.currentBytesPerSecondProperty());
        });
        return column;
    }

    private void installTransferContextMenu(OpenSession session, TableView<SftpTransferJob> table) {
        ContextMenu menu = ApplicationContextMenus.create();
        MenuItem retrySelected = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("sftp.transfer.retrySelected"));
        MenuItem cancelSelected = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.02d1152b3568b75a"));
        MenuItem cancelAll = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4db833187d8bac8f"));
        MenuItem pauseSelected = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.90bcd95b81c4e944"));
        MenuItem pauseAll = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6f27376da0507344"));
        MenuItem resumeSelected = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2bda51a00d29665b"));
        MenuItem resumeAll = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.9afe078dcfebfd98"));
        MenuItem deleteSelected = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.469f67cf665e1add"));
        MenuItem deleteAll = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e59f8cdf489a5bfb"));
        retrySelected.setOnAction(event -> session.transferQueue.retry(
                List.copyOf(table.getSelectionModel().getSelectedItems())));
        cancelSelected.setOnAction(event -> session.transferQueue.cancel(List.copyOf(table.getSelectionModel().getSelectedItems())));
        cancelAll.setOnAction(event -> session.transferQueue.cancel(List.copyOf(table.getItems())));
        pauseSelected.setOnAction(event -> session.transferQueue.pause(List.copyOf(table.getSelectionModel().getSelectedItems())));
        pauseAll.setOnAction(event -> session.transferQueue.pause(List.copyOf(table.getItems())));
        resumeSelected.setOnAction(event -> session.transferQueue.resume(List.copyOf(table.getSelectionModel().getSelectedItems())));
        resumeAll.setOnAction(event -> session.transferQueue.resume(List.copyOf(table.getItems())));
        deleteSelected.setOnAction(event -> session.transferQueue.delete(List.copyOf(table.getSelectionModel().getSelectedItems())));
        deleteAll.setOnAction(event -> session.transferQueue.delete(List.copyOf(table.getItems())));
        menu.getItems().setAll(retrySelected, new SeparatorMenuItem(), cancelSelected, cancelAll,
                new SeparatorMenuItem(), pauseSelected, pauseAll,
                resumeSelected, resumeAll, new SeparatorMenuItem(), deleteSelected, deleteAll);
        ApplicationContextMenus.prepareBeforeShow(menu, () -> {
            List<SftpTransferJob> selected = List.copyOf(table.getSelectionModel().getSelectedItems());
            retrySelected.setDisable(selected.stream().noneMatch(job -> job.state().canRetry()));
            cancelSelected.setDisable(selected.stream().noneMatch(job -> job.state().canCancel()));
            cancelAll.setDisable(table.getItems().stream().noneMatch(job -> job.state().canCancel()));
            pauseSelected.setDisable(selected.stream().noneMatch(job -> job.state().canPause()));
            pauseAll.setDisable(table.getItems().stream().noneMatch(job -> job.state().canPause()));
            resumeSelected.setDisable(selected.stream().noneMatch(job -> job.state().canResume()));
            resumeAll.setDisable(table.getItems().stream().noneMatch(job -> job.state().canResume()));
            deleteSelected.setDisable(selected.stream().noneMatch(job -> job.state().canDelete()));
            deleteAll.setDisable(table.getItems().stream().noneMatch(job -> job.state().canDelete()));
        });
        table.setContextMenu(menu);
    }

    private static String elapsedTime(long nanos) {
        return com.ynpe.fastremote.i18n.Texts.localizer().formatClockDuration(
                java.time.Duration.ofNanos(Math.max(0L, nanos)));
    }

    private static String speed(long bytesPerSecond) {
        return bytesPerSecond <= 0L ? "—" : humanSize(bytesPerSecond) + "/s";
    }

    private SftpConflictDecision resolveAutomationTransferConflict(TransferConflict conflict) {
        return resolveTransferConflict(conflict, true);
    }

    private SftpConflictDecision resolveTransferConflict(TransferConflict conflict, boolean automated) {
        if (Platform.isFxApplicationThread()) {
            throw new IllegalStateException("SFTP conflict resolution must not block the JavaFX thread");
        }
        CompletableFuture<SftpConflictDecision> decision = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                if (controllerClosed.get()) {
                    decision.complete(cancelledTransferConflict());
                    return;
                }
                if (transferConflictDialogs == null) {
                    transferConflictDialogs = new TransferConflictDialogQueue(
                            rootPane.getScene().getWindow());
                }
                transferConflictDialogs.request(conflict, automated).whenComplete((answer, failure) -> {
                    if (failure == null) decision.complete(answer);
                    else decision.completeExceptionally(failure);
                });
            } catch (RuntimeException failure) {
                decision.completeExceptionally(failure);
            }
        });
        try {
            return decision.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return cancelledTransferConflict();
        } catch (ExecutionException failure) {
            return cancelledTransferConflict();
        }
    }

    private static SftpConflictDecision cancelledTransferConflict() {
        return new SftpConflictDecision(TransferConflictChoice.CANCEL, false);
    }

    static void configureTransferColumnWidth(TableColumn<?, ?> column, double width) {
        column.setMinWidth(Math.min(52.0, width));
        column.setPrefWidth(width);
        column.setResizable(true);
    }

    static List<Double> defaultTransferColumnWidths() {
        return List.of(65.0, 175.0, 150.0, 100.0, 100.0, 90.0, 80.0, 85.0, 85.0);
    }

    private SplitPane createSftpWorkspace(OpenSession session) {
        if (session.transferQueue == null) {
            session.transferQueue = new SftpTransferQueue(remoteFiles(session),
                    () -> persistedWorkspaceSettings.sftpSettings().concurrentTransfers(),
                    conflict -> resolveTransferConflict(conflict, false),
                    activity -> appendFileActivity(session, activity));
            session.transferQueue.jobs().addListener((javafx.collections.ListChangeListener<SftpTransferJob>) change -> {
                while (change.next()) {
                    for (SftpTransferJob job : change.getAddedSubList()) {
                        Tab observedTab = session.tabs.get(WorkspaceType.SFTP);
                        if (observedTab != null) observations.transfers(automationWorkspaceId(observedTab),
                                session.observationGeneration, List.of(job));
                        job.stateProperty().addListener((ignored, previous, current) -> {
                            if (current != SftpTransferState.COMPLETED
                                    && current != SftpTransferState.FAILED) return;
                            if (current == SftpTransferState.COMPLETED) {
                                appendFileLog(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.0e9f49e642de06fe") + job.source() + " → " + job.target());
                            } else if (current == SftpTransferState.FAILED) {
                                String fallback = com.ynpe.fastremote.i18n.Texts.text("legacy.java.aa1002e88855c067")
                                        + job.source() + " → " + job.target();
                                appendFileLog(session, sshSftpFailureMessage(job.failure(), fallback),
                                        StatusTone.ERROR);
                            }
                            if (current == SftpTransferState.COMPLETED || current == SftpTransferState.FAILED) {
                                if (job.direction() == SftpTransferJob.Direction.UPLOAD) {
                                    refreshRemoteDirectoryViews(session, session.currentRemotePath);
                                } else {
                                    refreshLocalDirectoryViews(session, session.currentLocalPath);
                                }
                            }
                        });
                    }
                }
            });
        } else if (session.transferQueue.closed()) {
            session.transferQueue.reconnect(remoteFiles(session));
        }
        SplitPane files = new SplitPane(createLocalFileBrowser(session), createRemoteFileBrowser(session));
        files.setDividerPositions(0.5);
        files.getStyleClass().add("sftp-file-split");
        return files;
    }

    private VBox createRemoteFileBrowser(OpenSession session) {
        String initialPath = remoteBrowserInitialPath(session.currentRemotePath);
        TextField path = new TextField(initialPath);
        path.setId("remote-path");
        Button up = sftpActionButton(com.ynpe.fastremote.i18n.Texts.text("legacy.java.780cced2c214a5e8"), Icon.FOLDER_UP);
        Button refresh = sftpActionButton(com.ynpe.fastremote.i18n.Texts.text("legacy.java.aee88743413144a2"), Icon.REFRESH_CW);
        Button download = sftpActionButton(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4673a23061656125"), Icon.IMPORT);
        Button edit = sftpActionButton(com.ynpe.fastremote.i18n.Texts.text("editor.edit"), Icon.PENCIL);
        Button favorites = favoriteButton(session, false);
        TableView<RemoteFileEntry> table = new TableView<>();
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        session.remotePathField = path;
        session.remoteTable = table;
        session.currentRemotePath = initialPath;
        table.setId("remote-table");
        List<Double> widths = defaultSftpFileColumnWidths();
        TableColumn<RemoteFileEntry, String> name = new TableColumn<>(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d44e9b3d3b31d37b"));
        name.setPrefWidth(widths.get(0));
        name.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().name()));
        name.setCellFactory(ignored -> fileNameCell(
                RemoteFileEntry::directory, RemoteFileEntry::symbolicLink));
        TableColumn<RemoteFileEntry, String> size = new TableColumn<>(com.ynpe.fastremote.i18n.Texts.text("legacy.java.50db7447b966f5ef"));
        size.setPrefWidth(widths.get(1));
        size.setCellValueFactory(value -> new SimpleStringProperty(
                blankFileEntry(value.getValue().name()) ? ""
                        : value.getValue().directory() ? "—" : humanSize(value.getValue().size())));
        TableColumn<RemoteFileEntry, String> modified = new TableColumn<>(com.ynpe.fastremote.i18n.Texts.text("legacy.java.257bbcc44c839db4"));
        modified.setPrefWidth(widths.get(2));
        modified.setCellValueFactory(value -> new SimpleStringProperty(
                blankFileEntry(value.getValue().name()) ? ""
                        : value.getValue().modifiedAt()
                                .map(MainWindowController::formatSftpFileTime).orElse("—")));
        table.getColumns().setAll(List.of(name, size, modified));
        keepFileHelperRowsAtEdges(table, RemoteFileEntry::name);
        table.setPlaceholder(new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a62ff14fb72adfe6")));
        path.setOnAction(event -> loadRemoteDirectory(session, path.getText(), table, path));
        table.setOnMouseClicked(event -> {
            RemoteFileEntry selected = table.getSelectionModel().getSelectedItem();
            if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 2 || selected == null) return;
            if (selected.directory()) loadRemoteDirectory(session, selected.path(), table, path);
            else if (persistedWorkspaceSettings.sftpSettings().doubleClickFileAction()
                    == SftpDoubleClickAction.OPEN) editRemote(session, selected);
            else downloadEntries(session, List.of(selected), Path.of(session.currentLocalPath));
        });
        up.setOnAction(event -> loadRemoteDirectory(session, parentRemotePath(path.getText()), table, path));
        refresh.setOnAction(event -> {
            refreshRemoteDirectoryViews(session, path.getText());
        });
        download.setOnAction(event -> downloadSelected(session, session.currentLocalPath));
        edit.setOnAction(event -> editSelectedRemote(session));
        installRemoteTableInteractions(session, table);
        Label paneLabel = sftpPaneLabel(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7ba00927dde9122f"));
        Separator paneSeparator = new Separator(javafx.geometry.Orientation.VERTICAL);
        HBox bar = new HBox(8.0, paneLabel, paneSeparator, up, path, refresh, download, edit, favorites);
        HBox.setHgrow(path, Priority.ALWAYS);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("session-action-bar");
        Node content = createRemoteDirectoryContent(session, table, path);
        Label statistics = directoryStatisticsLabel();
        session.remoteStatisticsLabel = statistics;
        VBox browser = fileBrowser(bar, content, statistics);
        session.suppressRemoteHistory = true;
        Platform.runLater(() -> loadRemoteDirectory(session, initialPath, table, path));
        return browser;
    }

    static String remoteBrowserInitialPath(String currentPath) {
        return currentPath == null || currentPath.isBlank() ? "." : currentPath;
    }

    private Node createRemoteDirectoryContent(
            OpenSession session, TableView<RemoteFileEntry> table, TextField path) {
        Node tableSurface = fileTableSurface(table);
        if (!persistedWorkspaceSettings.showSftpDirectoryTree()) return tableSurface;
        CenteredTreeView<RemoteFileEntry> tree = new CenteredTreeView<>();
        session.remoteTree = tree;
        tree.setShowRoot(true);
        tree.setCellFactory(ignored -> {
            TreeCell<RemoteFileEntry> cell = remoteFileTreeCell();
            cell.setOnContextMenuRequested(event -> {
                if (cell.getItem() == null) return;
                tree.getSelectionModel().select(cell.getTreeItem());
                remoteContextMenu(session, cell.getItem(), cell.getTreeItem(), true)
                        .show(cell, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            cell.setOnDragDetected(event -> {
                if (cell.getItem() == null) return;
                beginSftpDrag(session);
                session.draggedRemoteEntries = List.of(cell.getItem());
                session.draggedLocalEntries = List.of();
                ClipboardContent content = new ClipboardContent();
                content.putString("fastremote-remote-tree");
                TerminalPathDragSupport.putRemotePaths(content, session.pairingId,
                        session.draggedRemoteEntries.stream().map(RemoteFileEntry::path).toList());
                cell.startDragAndDrop(TransferMode.COPY_OR_MOVE).setContent(content);
                event.consume();
            });
            cell.setOnDragOver(event -> {
                if (WorkspacePaneCoordinator.isWorkspaceDragPayload(event.getDragboard())) return;
                if (cell.getItem() != null && cell.getItem().directory()
                        && !session.draggedLocalEntries.isEmpty()) event.acceptTransferModes(TransferMode.COPY);
                event.consume();
            });
            cell.setOnDragDropped(event -> {
                if (WorkspacePaneCoordinator.isWorkspaceDragPayload(event.getDragboard())) return;
                List<LocalFileEntry> dragged = transferableLocalEntries(session.draggedLocalEntries);
                String target = cell.getItem() == null ? null : cell.getItem().path();
                if (target != null && !dragged.isEmpty()) {
                    deferSftpDropUntilDragDone(session, () -> uploadEntries(session, dragged, target));
                }
                clearSftpDrag(session);
                event.setDropCompleted(target != null && !dragged.isEmpty());
                event.consume();
            });
            cell.setOnDragDone(event -> finishSftpDrag(session));
            session.remoteMoves.installTarget(cell, () -> cell.getItem() != null
                    && cell.getItem().directory() ? cell.getItem().path() : null);
            return cell;
        });
        tree.setOnContextMenuRequested(event -> {
            remoteContextMenu(session, null, null, true).show(tree, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        tree.getSelectionModel().selectedItemProperty().addListener((ignored, previous, selected) -> {
            if (!session.synchronizingRemoteTree && selected != null
                    && selected.getValue() != null && selected.getValue().directory()) {
                loadRemoteDirectory(session, selected.getValue().path(), table, path);
            }
        });
        remoteFiles(session).canonicalPath("/").whenComplete((canonical, error) -> Platform.runLater(() -> {
            if (error != null) {
                appendFileLog(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.20865bf76789c4b4")
                                + remoteDirectoryFailureMessage(error),
                        StatusTone.ERROR);
                return;
            }
            TreeItem<RemoteFileEntry> root = remoteTreeRoot(session, canonical);
            tree.setRoot(root);
            root.setExpanded(true);
            synchronizeRemoteTree(session, path.getText());
        }));
        SplitPane split = new SplitPane(tree, tableSurface);
        split.setDividerPositions(0.36);
        return split;
    }

    private VBox createLocalFileBrowser(OpenSession session) {
        TextField path = new TextField(localBrowserInitialPath(
                session.currentLocalPath, System.getProperty("user.home", ".")));
        Button up = sftpActionButton(com.ynpe.fastremote.i18n.Texts.text("legacy.java.780cced2c214a5e8"), Icon.FOLDER_UP);
        Button refresh = sftpActionButton(com.ynpe.fastremote.i18n.Texts.text("legacy.java.aee88743413144a2"), Icon.REFRESH_CW);
        Button upload = sftpActionButton(com.ynpe.fastremote.i18n.Texts.text("legacy.java.9e07e3c0532d4976"), Icon.EXPORT);
        Button edit = sftpActionButton(com.ynpe.fastremote.i18n.Texts.text("editor.edit"), Icon.PENCIL);
        Button favorites = favoriteButton(session, true);
        TableView<LocalFileEntry> table = localFileTable();
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        session.localPathField = path;
        session.localTable = table;
        session.currentLocalPath = path.getText();
        path.setOnAction(event -> requestLocalDirectory(session, path.getText(), table, path));
        table.setOnMouseClicked(event -> {
            LocalFileEntry selected = table.getSelectionModel().getSelectedItem();
            if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 2 || selected == null) return;
            if (selected.directory()) loadLocalDirectory(session, selected.path(), table, path);
            else if (persistedWorkspaceSettings.sftpSettings().doubleClickFileAction()
                    == SftpDoubleClickAction.OPEN) editLocal(session, selected);
            else uploadEntries(session, List.of(selected), session.currentRemotePath);
        });
        up.setOnAction(event -> {
            try {
                Path current = Path.of(path.getText()).toAbsolutePath().normalize();
                loadLocalDirectory(session, current.getParent() == null ? current : current.getParent(), table, path);
            } catch (java.nio.file.InvalidPathException invalid) {
                showInvalidLocalPath(session, path.getText());
            }
        });
        refresh.setOnAction(event -> {
            refreshLocalDirectoryViews(session, path.getText());
        });
        upload.setOnAction(event -> uploadSelected(session, session.currentRemotePath));
        edit.setOnAction(event -> editSelectedLocal(session));
        installLocalTableInteractions(session, table);
        Label paneLabel = sftpPaneLabel(com.ynpe.fastremote.i18n.Texts.text("legacy.java.bf4ad761840fcd0a"));
        Separator paneSeparator = new Separator(javafx.geometry.Orientation.VERTICAL);
        HBox bar = new HBox(8.0, paneLabel, paneSeparator, up, path, refresh, upload, edit, favorites);
        HBox.setHgrow(path, Priority.ALWAYS);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("session-action-bar");

        Node content = createLocalDirectoryContent(session, table, path);
        Label statistics = directoryStatisticsLabel();
        session.localStatisticsLabel = statistics;
        VBox browser = fileBrowser(bar, content, statistics);
        session.localBrowser = browser;
        Platform.runLater(() -> requestLocalDirectory(session, path.getText(), table, path));
        return browser;
    }

    static String localBrowserInitialPath(String currentPath, String homePath) {
        if (currentPath == null || currentPath.isBlank() || ".".equals(currentPath)) {
            return homePath == null || homePath.isBlank() ? "." : homePath;
        }
        return currentPath;
    }

    private static Label sftpPaneLabel(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("sftp-pane-label");
        return label;
    }

    private static VBox fileBrowser(Node bar, Node content, Label statistics) {
        VBox browser = new VBox(bar, content, statistics);
        VBox.setVgrow(content, Priority.ALWAYS);
        browser.getStyleClass().add("sftp-file-browser");
        return browser;
    }

    private Button favoriteButton(OpenSession session, boolean local) {
        String label = com.ynpe.fastremote.i18n.Texts.text("legacy.java.60a53514eb9228a2");
        Button button = sftpFavoriteButton(label);
        ContextMenu menu = ApplicationContextMenus.create();
        button.setOnAction(event -> {
            if (ApplicationContextMenus.isShowing(menu)) {
                menu.hide();
                return;
            }
            rebuildFavoriteMenu(menu, session, local);
            menu.show(button, javafx.geometry.Side.BOTTOM, 0.0, 4.0);
        });
        return button;
    }

    static Button sftpActionButton(String label, Icon iconType) {
        Button button = new Button();
        LucideIconView icon = new LucideIconView(iconType);
        icon.getStyleClass().add("sftp-toolbar-icon");
        button.setGraphic(icon);
        button.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        button.setAccessibleText(label);
        button.setTooltip(new Tooltip(label));
        button.getStyleClass().add("sftp-toolbar-icon-button");
        return button;
    }

    static Button sftpFavoriteButton(String label) {
        return sftpActionButton(label, Icon.STAR);
    }

    private void rebuildFavoriteMenu(ContextMenu menu, OpenSession session, boolean local) {
        MenuItem add = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2e8a4ae9c22dca06"));
        MenuItem manage = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f074def5de38a40a"));
        MenuItem root = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.42ee2863b6d776ff"));
        MenuItem home = new MenuItem("home");
        add.setOnAction(event -> addCurrentBookmark(session, local));
        manage.setOnAction(event -> manageHostBookmarks(session));
        root.setOnAction(event -> navigateToRoot(session, local));
        home.setOnAction(event -> navigateToHome(session, local));
        menu.getItems().setAll(add, manage, new SeparatorMenuItem(), root, home, new SeparatorMenuItem());
        FavoritePathSections favorites = favoritePathSections(session, local, 30);
        favorites.global().forEach(path -> menu.getItems().add(bookmarkItem(session, local, path)));
        menu.getItems().add(new SeparatorMenuItem());
        favorites.host().forEach(path -> menu.getItems().add(bookmarkItem(session, local, path)));
    }

    private MenuItem bookmarkItem(OpenSession session, boolean local, String path) {
        MenuItem item = new MenuItem(path);
        item.setMnemonicParsing(false);
        item.setOnAction(event -> {
            if (local) requestLocalDirectory(session, path, session.localTable, session.localPathField);
            else loadRemoteDirectory(session, path, session.remoteTable, session.remotePathField);
        });
        return item;
    }

    private void addCurrentBookmark(OpenSession session, boolean local) {
        String value = local ? session.currentLocalPath : session.currentRemotePath;
        SftpSettings settings = persistedWorkspaceSettings.sftpSettings();
        SftpBookmarks current = settings.bookmarksFor(session.bookmarkKey);
        List<String> localPaths = new ArrayList<>(current.localPaths());
        List<String> remotePaths = new ArrayList<>(current.remotePaths());
        List<String> target = local ? localPaths : remotePaths;
        if (!target.contains(value)) target.add(value);
        saveSftpSettings(settings.withHostBookmarks(
                session.bookmarkKey, new SftpBookmarks(localPaths, remotePaths)),
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.be096737a8e4c8f7") + value);
    }

    private void manageHostBookmarks(OpenSession session) {
        SftpSettings initial = persistedWorkspaceSettings.sftpSettings();
        SftpBookmarkDialog.show(rootPane.getScene().getWindow(),
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.cce88ff30132b3b9"),
                initial.bookmarksFor(session.bookmarkKey), bookmarks -> saveSftpSettings(
                        persistedWorkspaceSettings.sftpSettings()
                                .withHostBookmarks(session.bookmarkKey, bookmarks),
                        com.ynpe.fastremote.i18n.Texts.text("legacy.java.efe5c734ce49625e")));
    }

    @FXML
    private void manageGlobalBookmarks() {
        SftpSettings settings = persistedWorkspaceSettings.sftpSettings();
        SftpBookmarkDialog.show(rootPane.getScene().getWindow(),
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.e8242ab8929830ab"),
                settings.globalBookmarks(), bookmarks -> saveGlobalBookmarks(
                        bookmarks, com.ynpe.fastremote.i18n.Texts.text("legacy.java.94b9a6fc6012d316")));
    }

    private void editSelectedLocal(OpenSession session) {
        List<LocalFileEntry> selected = session.localTable == null ? List.of()
                : transferableLocalEntries(List.copyOf(session.localTable.getSelectionModel().getSelectedItems()));
        editLocal(session, selected.size() == 1 ? selected.getFirst() : null);
    }

    private void editSelectedRemote(OpenSession session) {
        List<RemoteFileEntry> selected = session.remoteTable == null ? List.of()
                : transferableRemoteEntries(List.copyOf(session.remoteTable.getSelectionModel().getSelectedItems()));
        editRemote(session, selected.size() == 1 ? selected.getFirst() : null);
    }

    private void editLocal(OpenSession session, LocalFileEntry entry) {
        if (entry == null) {
            editorSftpFailure(session, com.ynpe.fastremote.i18n.Texts.text("editor.error.selectOne"));
            return;
        }
        textEditors.openLocal(entry, message -> editorSftpFailure(session, message),
                localEditorInsertionAnchor(persistedWorkspaceSettings.windowSettings(),
                        session.tabs.get(WorkspaceType.SFTP)));
    }

    private void editRemote(OpenSession session, RemoteFileEntry entry) {
        editRemote(session, entry, RemoteEditorPlacement.CONFIGURED);
    }

    private void editRemote(
            OpenSession session, RemoteFileEntry entry, RemoteEditorPlacement placement) {
        if (entry == null) {
            editorSftpFailure(session, com.ynpe.fastremote.i18n.Texts.text("editor.error.selectOne"));
            return;
        }
        String hostName = connectionLogName(session.name, session.profile);
        textEditors.openRemote(entry, hostName, remoteFiles(session),
                message -> editorSftpFailure(session, message),
                message -> logConnection(session, message, StatusTone.ERROR),
                () -> refreshRemoteDirectoryViews(session, session.currentRemotePath),
                remoteEditorInsertionAnchor(persistedWorkspaceSettings.windowSettings(),
                        session.tabs.get(WorkspaceType.SFTP), placement),
                path -> revealRemoteEditorFile(session, path));
    }

    enum RemoteEditorPlacement {
        CONFIGURED,
        NEW_WORKSPACE,
        CURRENT_WORKSPACE
    }

    static Tab remoteEditorInsertionAnchor(
            WindowSettings settings, Tab sourcePane, RemoteEditorPlacement placement) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(placement, "placement");
        boolean useCurrentWorkspace = switch (placement) {
            case CONFIGURED -> settings.openRemoteEditorInCurrentWorkspace();
            case NEW_WORKSPACE -> false;
            case CURRENT_WORKSPACE -> true;
        };
        return relatedPanelInsertionAnchor(useCurrentWorkspace, sourcePane);
    }

    static Tab localEditorInsertionAnchor(WindowSettings settings, Tab sourcePane) {
        Objects.requireNonNull(settings, "settings");
        return relatedPanelInsertionAnchor(
                settings.openLocalEditorInCurrentWorkspace(), sourcePane);
    }

    static boolean openPairedPanelInCurrentWorkspace(
            WindowSettings settings, WorkspaceType target) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(target, "target");
        return target == WorkspaceType.SFTP
                ? settings.openSftpFromTerminalInCurrentWorkspace()
                : settings.openTerminalFromSftpInCurrentWorkspace();
    }

    static Tab relatedPanelInsertionAnchor(boolean useCurrentWorkspace, Tab sourcePane) {
        return useCurrentWorkspace ? sourcePane : null;
    }

    private void revealRemoteEditorFile(OpenSession source, String requestedPath) {
        String path = normalizeRemotePath(requestedPath);
        Tab existing = source.tabs.get(WorkspaceType.SFTP);
        if (existing != null && openSessions.get(existing) == source) {
            selectWorkspaceTab(existing);
            locateRemoteFile(source, path);
            return;
        }
        java.util.UUID pairingId = java.util.UUID.randomUUID();
        if (pendingRemoteFileSelections.size() >= 16) {
            pendingRemoteFileSelections.remove(pendingRemoteFileSelections.keySet().iterator().next());
        }
        pendingRemoteFileSelections.put(pairingId, path);
        if (!openIndependentWorkspace(source, WorkspaceType.SFTP,
                parentRemotePath(path), pairingId)) {
            RecentConnection recent = new RecentConnection(
                    RecentConnection.Source.QUICK_CONNECTION,
                    "editor-reopen:" + pairingId,
                    Optional.empty(),
                    source.name.isBlank() ? source.profile.host().value() : source.name,
                    source.profile.host(), source.profile.port(), source.profile.username(),
                    source.profile.authenticationType(), Instant.now(), HostIcon.SERVER);
            showQuickConnectDialog(recent, WorkspaceType.SFTP, pairingId, parentRemotePath(path));
        }
    }

    private void locateRemoteFile(OpenSession session, String path) {
        session.pendingRemoteSelectionPath = path;
        if (session.state == ConnectionState.CONNECTED
                && session.remoteTable != null && session.remotePathField != null) {
            loadRemoteDirectory(session, parentRemotePath(path),
                    session.remoteTable, session.remotePathField);
        }
    }

    private void editorSftpFailure(OpenSession session, String message) {
        session.fileLog.append(operationLogLine(message));
        setSessionStatus(StatusModule.SFTP, session, message, StatusTone.ERROR);
    }

    private CompletionStage<Void> saveGlobalBookmarks(
            SftpBookmarks bookmarks, String successMessage) {
        SftpSettings settings = persistedWorkspaceSettings.sftpSettings();
        return saveSftpSettings(new SftpSettings(
                settings.showHiddenFiles(), settings.concurrentTransfers(), bookmarks,
                settings.hostBookmarks(), settings.maxEditableFileBytes(),
                settings.editorFontFamily(), settings.editorFontSize(), settings.editorTheme(),
                settings.doubleClickFileAction(), settings.editorTextAppearance()), successMessage);
    }

    private CompletionStage<Void> saveSftpSettings(SftpSettings settings, String successMessage) {
        GlobalSettings previous = persistedWorkspaceSettings;
        GlobalSettings updated = previous.withSftpSettings(settings);
        // Publish before writing so other views and queued layout saves use the same snapshot.
        persistedWorkspaceSettings = updated;
        CompletableFuture<Void> completion = new CompletableFuture<>();
        settingsWorkflow.save(updated).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (error != null) {
                if (persistedWorkspaceSettings == updated) persistedWorkspaceSettings = previous;
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3abd9672dab81cf7"), StatusTone.ERROR);
                completion.completeExceptionally(error);
                return;
            }
            setStatus(successMessage, StatusTone.IDLE);
            completion.complete(null);
        }));
        return completion;
    }

    private void navigateToRoot(OpenSession session, boolean local) {
        if (local) {
            try {
                Path current = Path.of(session.currentLocalPath).toAbsolutePath().normalize();
                loadLocalDirectory(session, current.getRoot() == null ? current : current.getRoot(),
                        session.localTable, session.localPathField);
            } catch (java.nio.file.InvalidPathException invalid) {
                showInvalidLocalPath(session, session.currentLocalPath);
            }
        } else {
            loadRemoteDirectory(session, "/", session.remoteTable, session.remotePathField);
        }
    }

    private void navigateToHome(OpenSession session, boolean local) {
        if (local) requestLocalDirectory(session, System.getProperty("user.home", "."),
                session.localTable, session.localPathField);
        else loadRemoteDirectory(session, session.remoteHome, session.remoteTable, session.remotePathField);
    }

    private void installLocalTableInteractions(OpenSession session, TableView<LocalFileEntry> table) {
        if (session.localMoves == null) session.localMoves = new FilePanelMoveSupport<>(() -> session.draggedLocalEntries,
                FilePanelMoves::localAllowed,
                (entry, directory) -> workflow.moveLocal(
                        entry.path(), directory.resolve(entry.path().getFileName()), false),
                action -> deferSftpDropUntilDragDone(session, action), () -> !session.closed.get(),
                (entry, error) -> appendFileLog(session,
                        FilePanelMoves.failureMessage(entry.name(), error), StatusTone.ERROR),
                (entry, directory) -> session.currentLocalPath = FilePanelMoves.localLocationAfterMove(
                        Path.of(session.currentLocalPath), entry, directory).toString(),
                () -> refreshLocalDirectoryViews(session, session.currentLocalPath));
        preventBlankRowSelection(table);
        table.setOnDragOver(event -> {
            if (WorkspacePaneCoordinator.isWorkspaceDragPayload(event.getDragboard())) return;
            if (!session.draggedRemoteEntries.isEmpty()) event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
        table.setOnDragDropped(event -> {
            if (WorkspacePaneCoordinator.isWorkspaceDragPayload(event.getDragboard())) return;
            List<RemoteFileEntry> dragged = transferableRemoteEntries(session.draggedRemoteEntries);
            Path target = Path.of(session.currentLocalPath);
            if (!dragged.isEmpty()) {
                deferSftpDropUntilDragDone(session, () -> downloadEntries(session, dragged, target));
            }
            clearSftpDrag(session);
            event.setDropCompleted(!dragged.isEmpty());
            event.consume();
        });
        session.localMoves.installTarget(table, () -> Path.of(session.currentLocalPath));
        table.setRowFactory(ignored -> {
            TableRow<LocalFileEntry> row = new TableRow<>();
            guardBlankFileRow(table, row);
            row.setOnContextMenuRequested(event -> {
                selectContextRow(table, row);
                localContextMenu(session, row.getItem(), null, false)
                        .show(row, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            row.setOnDragDetected(event -> {
                LocalFileEntry item = row.getItem();
                if (item == null) return;
                selectDragRow(table, row);
                beginSftpDrag(session);
                session.draggedLocalEntries = transferableLocalEntries(
                        List.copyOf(table.getSelectionModel().getSelectedItems()));
                if (session.draggedLocalEntries.isEmpty()) return;
                session.draggedRemoteEntries = List.of();
                ClipboardContent content = new ClipboardContent();
                content.putString("fastremote-local");
                List<Path> paths = session.draggedLocalEntries.stream()
                        .map(LocalFileEntry::path).toList();
                content.putFiles(paths.stream().map(Path::toFile).toList());
                TerminalPathDragSupport.putLocalPaths(content,
                        paths.stream().map(Path::toString).toList());
                row.startDragAndDrop(TransferMode.COPY_OR_MOVE).setContent(content);
                event.consume();
            });
            row.setOnDragOver(event -> {
                if (WorkspacePaneCoordinator.isWorkspaceDragPayload(event.getDragboard())) return;
                if (!session.draggedRemoteEntries.isEmpty()) event.acceptTransferModes(TransferMode.COPY);
                event.consume();
            });
            row.setOnDragDropped(event -> {
                if (WorkspacePaneCoordinator.isWorkspaceDragPayload(event.getDragboard())) return;
                Path target = row.getItem() != null && row.getItem().directory()
                        ? row.getItem().path() : Path.of(session.currentLocalPath);
                List<RemoteFileEntry> dragged = transferableRemoteEntries(session.draggedRemoteEntries);
                if (!dragged.isEmpty()) {
                    deferSftpDropUntilDragDone(session, () -> downloadEntries(session, dragged, target));
                }
                clearSftpDrag(session);
                event.setDropCompleted(!dragged.isEmpty());
                event.consume();
            });
            row.setOnDragDone(event -> finishSftpDrag(session));
            session.localMoves.installTarget(row,
                    () -> FilePanelMoves.localTarget(row.getItem(), Path.of(session.currentLocalPath)));
            return row;
        });
    }

    private void installRemoteTableInteractions(OpenSession session, TableView<RemoteFileEntry> table) {
        if (session.remoteMoves == null) session.remoteMoves = new FilePanelMoveSupport<>(() -> session.draggedRemoteEntries,
                FilePanelMoves::remoteAllowed,
                (entry, directory) -> FilePanelMoves.moveRemote(remoteFiles(session), entry, directory),
                action -> deferSftpDropUntilDragDone(session, action), () -> !session.closed.get(),
                (entry, error) -> appendFileLog(session,
                        FilePanelMoves.failureMessage(entry.name(), error), StatusTone.ERROR),
                (entry, directory) -> session.currentRemotePath = FilePanelMoves.remoteLocationAfterMove(
                        session.currentRemotePath, entry, directory),
                () -> refreshRemoteDirectoryViews(session, session.currentRemotePath));
        preventBlankRowSelection(table);
        table.setOnDragOver(event -> {
            if (WorkspacePaneCoordinator.isWorkspaceDragPayload(event.getDragboard())) return;
            if (!session.draggedLocalEntries.isEmpty()) event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
        table.setOnDragDropped(event -> {
            if (WorkspacePaneCoordinator.isWorkspaceDragPayload(event.getDragboard())) return;
            List<LocalFileEntry> dragged = transferableLocalEntries(session.draggedLocalEntries);
            String target = session.currentRemotePath;
            if (!dragged.isEmpty()) {
                deferSftpDropUntilDragDone(session, () -> uploadEntries(session, dragged, target));
            }
            clearSftpDrag(session);
            event.setDropCompleted(!dragged.isEmpty());
            event.consume();
        });
        session.remoteMoves.installTarget(table, () -> session.currentRemotePath);
        table.setRowFactory(ignored -> {
            TableRow<RemoteFileEntry> row = new TableRow<>();
            guardBlankFileRow(table, row);
            row.setOnContextMenuRequested(event -> {
                selectContextRow(table, row);
                remoteContextMenu(session, row.getItem(), null, false)
                        .show(row, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            row.setOnDragDetected(event -> {
                RemoteFileEntry item = row.getItem();
                if (item == null) return;
                selectDragRow(table, row);
                beginSftpDrag(session);
                session.draggedRemoteEntries = transferableRemoteEntries(
                        List.copyOf(table.getSelectionModel().getSelectedItems()));
                if (session.draggedRemoteEntries.isEmpty()) return;
                session.draggedLocalEntries = List.of();
                ClipboardContent content = new ClipboardContent();
                content.putString("fastremote-remote");
                TerminalPathDragSupport.putRemotePaths(content, session.pairingId,
                        session.draggedRemoteEntries.stream().map(RemoteFileEntry::path).toList());
                row.startDragAndDrop(TransferMode.COPY_OR_MOVE).setContent(content);
                event.consume();
            });
            row.setOnDragOver(event -> {
                if (WorkspacePaneCoordinator.isWorkspaceDragPayload(event.getDragboard())) return;
                if (!session.draggedLocalEntries.isEmpty()) event.acceptTransferModes(TransferMode.COPY);
                event.consume();
            });
            row.setOnDragDropped(event -> {
                if (WorkspacePaneCoordinator.isWorkspaceDragPayload(event.getDragboard())) return;
                String target = row.getItem() != null && row.getItem().directory()
                        ? row.getItem().path() : session.currentRemotePath;
                List<LocalFileEntry> dragged = transferableLocalEntries(session.draggedLocalEntries);
                if (!dragged.isEmpty()) {
                    deferSftpDropUntilDragDone(session, () -> uploadEntries(session, dragged, target));
                }
                clearSftpDrag(session);
                event.setDropCompleted(!dragged.isEmpty());
                event.consume();
            });
            row.setOnDragDone(event -> finishSftpDrag(session));
            session.remoteMoves.installTarget(row,
                    () -> FilePanelMoves.remoteTarget(row.getItem(), session.currentRemotePath));
            return row;
        });
    }

    private static <T> void selectContextRow(TableView<T> table, TableRow<T> row) {
        if (row.getItem() == null || blankTableItem(row.getItem())) {
            table.getSelectionModel().clearSelection();
        } else if (!table.getSelectionModel().isSelected(row.getIndex())) {
            table.getSelectionModel().clearAndSelect(row.getIndex());
        }
    }

    static <T> void guardBlankFileRow(TableView<T> table, TableRow<T> row) {
        row.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (!blankTableItem(row.getItem())) return;
            // The final two visual spacer rows are model items so that they remain present even
            // when the viewport is full. Never let TableViewBehavior select those sentinels on a
            // mouse press: its deferred selection correction used to run while a context menu was
            // being mapped on GTK, forcing a second CSS/layout pulse and occasionally retaining
            // the popup mouse grab. ContextMenuEvent is separate and still reaches the row.
            table.getSelectionModel().clearSelection();
            if (event.getButton() == MouseButton.PRIMARY
                    || event.getButton() == MouseButton.SECONDARY) {
                event.consume();
            }
        });
    }

    private static <T> void selectDragRow(TableView<T> table, TableRow<T> row) {
        if (!table.getSelectionModel().isSelected(row.getIndex())) {
            table.getSelectionModel().clearAndSelect(row.getIndex());
        }
    }

    private static void clearSftpDrag(OpenSession session) {
        session.draggedLocalEntries = List.of();
        session.draggedRemoteEntries = List.of();
    }

    static void beginSftpDrag(OpenSession session) {
        Objects.requireNonNull(session, "session");
        clearSftpDrag(session);
        session.pendingSftpDropAction = null;
    }

    static void deferSftpDropUntilDragDone(OpenSession session, Runnable action) {
        Objects.requireNonNull(session, "session");
        session.pendingSftpDropAction = Objects.requireNonNull(action, "action");
    }

    static void finishSftpDrag(OpenSession session) {
        Objects.requireNonNull(session, "session");
        clearSftpDrag(session);
        if (session.localMoves != null) session.localMoves.clearHighlight();
        if (session.remoteMoves != null) session.remoteMoves.clearHighlight();
        Runnable action = session.pendingSftpDropAction;
        session.pendingSftpDropAction = null;
        if (action != null) {
            // Commit the immutable drop intent only after the source drag lifecycle has ended.
            // Linux conflict UI stays in the same native window, so correctness no longer
            // depends on an arbitrary GTK drag-grab release delay.
            Platform.runLater(action);
        }
    }

    static <T> void preventBlankRowSelection(TableView<T> table) {
        boolean[] clearingScheduled = new boolean[1];
        table.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<T>) change -> {
                    if (clearingScheduled[0]) return;
                    List<Integer> blankIndices = table.getSelectionModel().getSelectedIndices().stream()
                            .filter(index -> index >= 0 && index < table.getItems().size())
                            .filter(index -> blankTableItem(table.getItems().get(index)))
                            .toList();
                    if (blankIndices.isEmpty()) return;
                    clearingScheduled[0] = true;
                    Platform.runLater(() -> {
                        blankIndices.forEach(table.getSelectionModel()::clearSelection);
                        clearingScheduled[0] = false;
                    });
                });
    }

    static <T> Node fileTableSurface(TableView<T> table) {
        Rectangle marquee = new Rectangle();
        marquee.getStyleClass().add("sftp-selection-marquee");
        marquee.setManaged(false);
        marquee.setMouseTransparent(true);
        marquee.setVisible(false);
        StackPane surface = new StackPane(table, marquee);
        double[] start = new double[2];
        boolean[] active = new boolean[1];
        table.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || !blankTableArea(table, event)) return;
            Point2D point = surface.sceneToLocal(event.getSceneX(), event.getSceneY());
            start[0] = point.getX();
            start[1] = point.getY();
            active[0] = true;
            marquee.setX(point.getX());
            marquee.setY(point.getY());
            marquee.setWidth(0.0);
            marquee.setHeight(0.0);
            if (!event.isShortcutDown()) table.getSelectionModel().clearSelection();
        });
        table.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!active[0] || !event.isPrimaryButtonDown()) return;
            Point2D point = surface.sceneToLocal(event.getSceneX(), event.getSceneY());
            double x = Math.min(start[0], point.getX());
            double y = Math.min(start[1], point.getY());
            marquee.setX(x);
            marquee.setY(y);
            marquee.setWidth(Math.abs(point.getX() - start[0]));
            marquee.setHeight(Math.abs(point.getY() - start[1]));
            marquee.setVisible(marquee.getWidth() > 3.0 || marquee.getHeight() > 3.0);
            if (marquee.isVisible()) {
                Bounds selection = marquee.getBoundsInParent();
                table.lookupAll(".table-row-cell").stream()
                        .filter(TableRow.class::isInstance)
                        .map(TableRow.class::cast)
                        .filter(row -> row.getItem() != null && !blankTableItem(row.getItem())
                                && row.getIndex() >= 0)
                        .forEach(row -> {
                            Bounds bounds = surface.sceneToLocal(row.localToScene(row.getBoundsInLocal()));
                            if (selection.intersects(bounds)) table.getSelectionModel().select(row.getIndex());
                            else if (!event.isShortcutDown()) table.getSelectionModel().clearSelection(row.getIndex());
                        });
            }
            event.consume();
        });
        table.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (!active[0]) return;
            active[0] = false;
            marquee.setVisible(false);
            event.consume();
        });
        table.addEventFilter(javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            // A context request terminates any incomplete blank-area marquee gesture before the
            // popup takes the native GTK mouse grab.
            active[0] = false;
            marquee.setVisible(false);
        });
        return surface;
    }

    private static boolean blankTableArea(TableView<?> table, MouseEvent event) {
        if (!(event.getTarget() instanceof Node node)) return false;
        for (Node current = node; current != null && current != table; current = current.getParent()) {
            if (blocksFileTableMarquee(current)) return false;
            if (current instanceof TableRow<?> row) {
                Object item = row.getItem();
                return item == null || blankTableItem(item);
            }
        }
        return true;
    }

    static boolean blocksFileTableMarquee(Node node) {
        if (node instanceof ScrollBar) return true;
        List<String> styles = node.getStyleClass();
        return styles.contains("scroll-bar") || styles.contains("corner") || styles.contains("filler")
                || styles.contains("column-header") || styles.contains("column-header-background")
                || styles.contains("show-hide-columns-button");
    }

    private ContextMenu localContextMenu(
            OpenSession session, LocalFileEntry clicked, TreeItem<LocalFileEntry> treeItem, boolean tree) {
        List<LocalFileEntry> selected = transferableLocalEntries(tree && clicked != null ? List.of(clicked)
                : session.localTable == null ? List.of()
                : List.copyOf(session.localTable.getSelectionModel().getSelectedItems()));
        ContextMenu menu = ApplicationContextMenus.create();
        Menu go = navigationMenu(session, true);
        MenuItem goToTerminal = new MenuItem(localizer.text("sftp.goToTerminal"));
        MenuItem openInTerminal = new MenuItem(localizer.text("sftp.openInTerminal"));
        MenuItem openNewSftp = new MenuItem(localizer.text("sftp.openNewSession"));
        Optional<Path> terminalDirectory = localTerminalDirectory(clicked, session.currentLocalPath);
        MenuItem upload = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.9e07e3c0532d4976"));
        MenuItem edit = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("editor.edit"));
        MenuItem addFavorite = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2e8a4ae9c22dca06"));
        MenuItem manage = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f074def5de38a40a"));
        Menu more = sftpMoreMenu(manage, openInTerminal, openNewSftp, List.of());
        MenuItem refresh = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.aee88743413144a2"));
        MenuItem createDirectory = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d5f6f645ff5e4bd9"));
        MenuItem createFile = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6ddd1eaccab127de"));
        MenuItem delete = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2f9daa828907b93f"));
        MenuItem rename = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0d0cbac2eee54113"));
        MenuItem copyPath = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b97c49acb93028ec"));
        MenuItem copyName = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.886775196a71d984"));
        MenuItem reveal = new MenuItem(PlatformFileManagerLabel.reveal());
        CheckMenuItem hidden = hiddenFilesMenuItem(true);
        CheckMenuItem directoryTree = directoryTreeMenuItem(true);
        upload.setAccelerator(shortcutCombination(ShortcutAction.SFTP_UPLOAD));
        edit.setAccelerator(shortcutCombination(ShortcutAction.SFTP_EDIT));
        refresh.setAccelerator(shortcutCombination(ShortcutAction.SFTP_REFRESH));
        createDirectory.setAccelerator(shortcutCombination(ShortcutAction.SFTP_NEW_FOLDER));
        delete.setAccelerator(shortcutCombination(ShortcutAction.SFTP_DELETE));
        rename.setAccelerator(shortcutCombination(ShortcutAction.SFTP_RENAME));
        hidden.setAccelerator(shortcutCombination(ShortcutAction.SFTP_TOGGLE_HIDDEN));
        upload.setDisable(selected.isEmpty());
        edit.setDisable(selected.size() != 1);
        delete.setDisable(selected.isEmpty());
        rename.setDisable(selected.size() != 1);
        copyPath.setDisable(selected.isEmpty());
        copyName.setDisable(selected.isEmpty());
        reveal.setDisable(selected.size() != 1);
        openInTerminal.setDisable(terminalDirectory.isEmpty());
        goToTerminal.setOnAction(event -> goToLocalTerminal());
        openInTerminal.setOnAction(event -> terminalDirectory.ifPresent(
                path -> openLocalTerminal(false, path, null)));
        openNewSftp.setOnAction(event -> openIndependentWorkspace(session, WorkspaceType.SFTP));
        upload.setOnAction(event -> uploadEntries(session, selected, session.currentRemotePath));
        edit.setOnAction(event -> editLocal(session, selected.size() == 1 ? selected.getFirst() : null));
        addFavorite.setOnAction(event -> addBookmarkPath(session, true,
                clicked != null && clicked.directory() ? clicked.path().toString() : session.currentLocalPath));
        manage.setOnAction(event -> manageHostBookmarks(session));
        refresh.setOnAction(event -> {
            if (treeItem != null) refreshLocalTreeNode(session, treeItem);
            else refreshLocalTreeRoots(session);
            requestLocalDirectory(session, session.currentLocalPath,
                    session.localTable, session.localPathField);
        });
        Path creationParent = tree && clicked != null ? clicked.path() : Path.of(session.currentLocalPath);
        createDirectory.setOnAction(event -> createLocalObject(session, creationParent, true));
        createFile.setOnAction(event -> createLocalObject(session, creationParent, false));
        delete.setOnAction(event -> deleteLocalObjects(session, selected));
        rename.setOnAction(event -> renameLocalObject(session, selected.getFirst()));
        copyPath.setOnAction(event -> copyText(selected.stream().map(value -> value.path().toString()).toList()));
        copyName.setOnAction(event -> copyText(selected.stream().map(LocalFileEntry::name).toList()));
        reveal.setOnAction(event -> revealLocal(selected.getFirst()));
        hidden.setOnAction(event -> saveLocalFileDisplaySettings(hidden.isSelected(),
                persistedWorkspaceSettings.windowSettings().showLocalFileDirectoryTree()));
        directoryTree.setOnAction(event -> saveLocalFileDisplaySettings(
                persistedWorkspaceSettings.windowSettings().showLocalFileHiddenFiles(),
                directoryTree.isSelected()));
        menu.getItems().setAll(sftpContextMenuTopItems(go, addFavorite, goToTerminal, more));
        menu.getItems().addAll(new SeparatorMenuItem(), upload, edit, refresh, new SeparatorMenuItem(),
                createDirectory, createFile, delete, rename, new SeparatorMenuItem(), copyPath, copyName,
                reveal, hidden, directoryTree);
        return menu;
    }

    private ContextMenu remoteContextMenu(
            OpenSession session, RemoteFileEntry clicked, TreeItem<RemoteFileEntry> treeItem, boolean tree) {
        List<RemoteFileEntry> selected = transferableRemoteEntries(tree && clicked != null ? List.of(clicked)
                : session.remoteTable == null ? List.of()
                : List.copyOf(session.remoteTable.getSelectionModel().getSelectedItems()));
        ContextMenu menu = ApplicationContextMenus.create();
        Menu go = navigationMenu(session, false);
        MenuItem goToTerminal = new MenuItem(localizer.text("sftp.goToTerminal"));
        MenuItem openInTerminal = new MenuItem(localizer.text("sftp.openInTerminal"));
        MenuItem openNewSftp = new MenuItem(localizer.text("sftp.openNewSession"));
        String terminalDirectory = remoteTerminalDirectory(clicked, session.currentRemotePath);
        MenuItem download = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4673a23061656125"));
        MenuItem edit = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("editor.edit"));
        MenuItem editNewWorkspace = new MenuItem(
                com.ynpe.fastremote.i18n.Texts.text("editor.editNewWorkspace"));
        MenuItem editCurrentWorkspace = new MenuItem(
                com.ynpe.fastremote.i18n.Texts.text("editor.editCurrentWorkspace"));
        MenuItem addFavorite = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2e8a4ae9c22dca06"));
        MenuItem manage = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f074def5de38a40a"));
        Menu more = sftpMoreMenu(manage, openInTerminal, openNewSftp,
                List.of(editNewWorkspace, editCurrentWorkspace));
        MenuItem refresh = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.aee88743413144a2"));
        MenuItem createDirectory = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d5f6f645ff5e4bd9"));
        MenuItem createFile = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6ddd1eaccab127de"));
        MenuItem delete = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2f9daa828907b93f"));
        MenuItem rename = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0d0cbac2eee54113"));
        MenuItem properties = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.86de52d178203799"));
        MenuItem copyPath = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b97c49acb93028ec"));
        MenuItem copyName = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.886775196a71d984"));
        CheckMenuItem hidden = hiddenFilesMenuItem(false);
        CheckMenuItem directoryTree = directoryTreeMenuItem(false);
        download.setAccelerator(shortcutCombination(ShortcutAction.SFTP_DOWNLOAD));
        edit.setAccelerator(shortcutCombination(ShortcutAction.SFTP_EDIT));
        refresh.setAccelerator(shortcutCombination(ShortcutAction.SFTP_REFRESH));
        createDirectory.setAccelerator(shortcutCombination(ShortcutAction.SFTP_NEW_FOLDER));
        delete.setAccelerator(shortcutCombination(ShortcutAction.SFTP_DELETE));
        rename.setAccelerator(shortcutCombination(ShortcutAction.SFTP_RENAME));
        hidden.setAccelerator(shortcutCombination(ShortcutAction.SFTP_TOGGLE_HIDDEN));
        download.setDisable(selected.isEmpty());
        edit.setDisable(selected.size() != 1);
        editNewWorkspace.setDisable(selected.size() != 1);
        editCurrentWorkspace.setDisable(selected.size() != 1);
        delete.setDisable(selected.isEmpty());
        rename.setDisable(selected.size() != 1);
        properties.setDisable(selected.size() != 1);
        copyPath.setDisable(selected.isEmpty());
        copyName.setDisable(selected.isEmpty());
        goToTerminal.setOnAction(event -> openPairedWorkspace(session, WorkspaceType.TERMINAL));
        openInTerminal.setOnAction(event -> openIndependentWorkspace(
                session, WorkspaceType.TERMINAL, terminalDirectory));
        openNewSftp.setOnAction(event -> openIndependentWorkspace(session, WorkspaceType.SFTP));
        download.setOnAction(event -> downloadEntries(session, selected, Path.of(session.currentLocalPath)));
        edit.setOnAction(event -> editRemote(session, selected.size() == 1 ? selected.getFirst() : null));
        editNewWorkspace.setOnAction(event -> editRemote(session,
                selected.size() == 1 ? selected.getFirst() : null,
                RemoteEditorPlacement.NEW_WORKSPACE));
        editCurrentWorkspace.setOnAction(event -> editRemote(session,
                selected.size() == 1 ? selected.getFirst() : null,
                RemoteEditorPlacement.CURRENT_WORKSPACE));
        addFavorite.setOnAction(event -> addBookmarkPath(session, false,
                clicked != null && clicked.directory() ? clicked.path() : session.currentRemotePath));
        manage.setOnAction(event -> manageHostBookmarks(session));
        refresh.setOnAction(event -> {
            if (treeItem != null) refreshRemoteTreeNode(session, treeItem);
            else refreshRemoteTreeRoot(session);
            loadRemoteDirectory(session, session.currentRemotePath,
                    session.remoteTable, session.remotePathField);
        });
        createDirectory.setOnAction(event -> createRemoteObject(session, true));
        createFile.setOnAction(event -> createRemoteObject(session, false));
        delete.setOnAction(event -> deleteRemoteObjects(session, selected));
        rename.setOnAction(event -> renameRemoteObject(session, selected.getFirst()));
        properties.setOnAction(event -> editRemoteProperties(session, selected.getFirst()));
        copyPath.setOnAction(event -> copyText(selected.stream().map(RemoteFileEntry::path).toList()));
        copyName.setOnAction(event -> copyText(selected.stream().map(RemoteFileEntry::name).toList()));
        hidden.setOnAction(event -> setHiddenFiles(session, hidden.isSelected()));
        directoryTree.setOnAction(event -> setSftpDirectoryTreeVisible(directoryTree.isSelected()));
        menu.getItems().setAll(sftpContextMenuTopItems(go, addFavorite, goToTerminal, more));
        menu.getItems().addAll(new SeparatorMenuItem(), download, edit, refresh,
                new SeparatorMenuItem(),
                createDirectory, createFile, delete, rename, properties,
                new SeparatorMenuItem(), copyPath, copyName, hidden, directoryTree);
        return menu;
    }

    static List<MenuItem> sftpContextMenuTopItems(
            Menu go, MenuItem addFavorite, MenuItem goToTerminal, Menu more) {
        return List.of(go, addFavorite, goToTerminal, more);
    }

    static Menu sftpMoreMenu(
            MenuItem manageBookmarks,
            MenuItem openInTerminal,
            MenuItem openNewSftp,
            List<MenuItem> editorPlacements) {
        Menu more = new Menu(com.ynpe.fastremote.i18n.Texts.text("sftp.context.more"));
        more.setId("sftp-context-more");
        more.getItems().add(manageBookmarks);
        more.getItems().add(new SeparatorMenuItem());
        more.getItems().addAll(openInTerminal, openNewSftp);
        if (!editorPlacements.isEmpty()) {
            more.getItems().add(new SeparatorMenuItem());
            more.getItems().addAll(editorPlacements);
        }
        return ApplicationContextMenus.styleSubmenu(more);
    }

    static Optional<Path> localTerminalDirectory(LocalFileEntry selected, String currentDirectory) {
        try {
            boolean blankArea = selected == null || blankFileEntry(selected.name());
            Path path = blankArea ? Path.of(currentDirectory) : selected.path();
            if (!blankArea && !selected.directory() && path.getParent() != null) {
                path = path.getParent();
            }
            return Optional.of(path.toAbsolutePath().normalize());
        } catch (InvalidPathException | NullPointerException invalid) {
            return Optional.empty();
        }
    }

    static String remoteTerminalDirectory(RemoteFileEntry selected, String currentDirectory) {
        String path = selected == null || blankFileEntry(selected.name()) ? currentDirectory
                : selected.directory() ? selected.path() : parentRemotePath(selected.path());
        return normalizeRemotePath(path);
    }

    private void goToLocalTerminal() {
        List<Tab> tabs = workspacePanes.logicalTabsInDisplayOrder();
        for (int index = tabs.size() - 1; index >= 0; index--) {
            Tab tab = tabs.get(index);
            LocalTerminalHandle terminal = localTerminals.get(tab);
            if (terminal == null) continue;
            selectWorkspaceTab(tab);
            terminal.requestFocus();
            return;
        }
        openLocalTerminal();
    }

    private Menu navigationMenu(OpenSession session, boolean local) {
        Menu go = new Menu(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d69b5ab453f7d2d6"));
        MenuItem back = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.572cf45ba43634b3"));
        MenuItem next = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d681c6e2947ae79b"));
        MenuItem up = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.da88ef9c3ec580d2"));
        MenuItem root = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.42ee2863b6d776ff"));
        MenuItem home = new MenuItem("home");
        back.setDisable((local ? session.localBack : session.remoteBack).isEmpty());
        next.setDisable((local ? session.localHistoryNext : session.remoteHistoryNext).isEmpty());
        back.setOnAction(event -> navigateHistory(session, local, true));
        next.setOnAction(event -> navigateHistory(session, local, false));
        up.setOnAction(event -> {
            if (local) {
                Path current = Path.of(session.currentLocalPath);
                loadLocalDirectory(session, current.getParent() == null ? current : current.getParent(),
                        session.localTable, session.localPathField);
            } else loadRemoteDirectory(session, parentRemotePath(session.currentRemotePath),
                    session.remoteTable, session.remotePathField);
        });
        root.setOnAction(event -> navigateToRoot(session, local));
        home.setOnAction(event -> navigateToHome(session, local));
        go.getItems().setAll(back, next, up, root, home);
        FavoritePathSections favorites = favoritePathSections(session, local, 20);
        if (!favorites.global().isEmpty() || !favorites.host().isEmpty()) {
            go.getItems().add(new SeparatorMenuItem());
            favorites.global().forEach(path -> go.getItems().add(bookmarkItem(session, local, path)));
            if (!favorites.global().isEmpty() && !favorites.host().isEmpty()) {
                go.getItems().add(new SeparatorMenuItem());
            }
            favorites.host().forEach(path -> go.getItems().add(bookmarkItem(session, local, path)));
        }
        return ApplicationContextMenus.styleSubmenu(go);
    }

    private FavoritePathSections favoritePathSections(
            OpenSession session, boolean local, int maximumPaths) {
        SftpSettings settings = persistedWorkspaceSettings.sftpSettings();
        SftpBookmarks global = settings.globalBookmarks();
        SftpBookmarks host = settings.bookmarksFor(session.bookmarkKey);
        return limitedFavoritePathSections(
                local ? global.localPaths() : global.remotePaths(),
                local ? host.localPaths() : host.remotePaths(),
                maximumPaths);
    }

    static FavoritePathSections limitedFavoritePathSections(
            List<String> global, List<String> host, int maximumPaths) {
        Objects.requireNonNull(global, "global");
        Objects.requireNonNull(host, "host");
        if (maximumPaths < 0) throw new IllegalArgumentException("maximumPaths must not be negative");
        int globalCount = Math.min(Math.min(10, global.size()), maximumPaths);
        List<String> limitedGlobal = List.copyOf(global.subList(0, globalCount));
        int hostCount = Math.min(host.size(), maximumPaths - globalCount);
        return new FavoritePathSections(
                limitedGlobal, List.copyOf(host.subList(0, hostCount)));
    }

    record FavoritePathSections(List<String> global, List<String> host) {
        FavoritePathSections {
            global = List.copyOf(global);
            host = List.copyOf(host);
        }

        int size() {
            return global.size() + host.size();
        }
    }

    private void navigateHistory(OpenSession session, boolean local, boolean back) {
        java.util.ArrayDeque<String> source = local
                ? (back ? session.localBack : session.localHistoryNext)
                : (back ? session.remoteBack : session.remoteHistoryNext);
        java.util.ArrayDeque<String> destination = local
                ? (back ? session.localHistoryNext : session.localBack)
                : (back ? session.remoteHistoryNext : session.remoteBack);
        if (source.isEmpty()) return;
        destination.addLast(local ? session.currentLocalPath : session.currentRemotePath);
        String target = source.removeLast();
        if (local) {
            session.suppressLocalHistory = true;
            requestLocalDirectory(session, target, session.localTable, session.localPathField);
        } else {
            session.suppressRemoteHistory = true;
            loadRemoteDirectory(session, target, session.remoteTable, session.remotePathField);
        }
    }

    private void addBookmarkPath(OpenSession session, boolean local, String value) {
        SftpSettings settings = persistedWorkspaceSettings.sftpSettings();
        SftpBookmarks current = settings.bookmarksFor(session.bookmarkKey);
        List<String> localPaths = new ArrayList<>(current.localPaths());
        List<String> remotePaths = new ArrayList<>(current.remotePaths());
        List<String> target = local ? localPaths : remotePaths;
        if (!target.contains(value)) target.add(value);
        saveSftpSettings(settings.withHostBookmarks(session.bookmarkKey,
                new SftpBookmarks(localPaths, remotePaths)), com.ynpe.fastremote.i18n.Texts.text("legacy.java.be096737a8e4c8f7") + value);
    }

    private void createLocalObject(OpenSession session, boolean directory) {
        createLocalObject(session, Path.of(session.currentLocalPath), directory);
    }

    private void createLocalObject(OpenSession session, Path parent, boolean directory) {
        promptObjectName(directory ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.d5f6f645ff5e4bd9") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.6ddd1eaccab127de"), directory ? "NewFolder" : "NewFile.txt")
                .ifPresent(name -> {
                    Path target = parent.resolve(name);
                    CompletionStage<Void> operation = directory
                            ? workflow.createLocalDirectory(target) : workflow.createLocalFile(target);
                    operation.whenComplete((ignored, error) -> Platform.runLater(() -> finishFileMutation(
                            session, error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.7e6a71efbf63fd6f"),
                            () -> refreshLocalDirectoryViews(session, session.currentLocalPath))));
                });
    }

    private void createRemoteObject(OpenSession session, boolean directory) {
        createRemoteObject(session, session.currentRemotePath, directory);
    }

    private void createRemoteObject(OpenSession session, String parent, boolean directory) {
        promptObjectName(directory ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.d5f6f645ff5e4bd9") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.6ddd1eaccab127de"), directory ? "NewFolder" : "NewFile.txt")
                .ifPresent(name -> {
                    String target = joinRemotePath(parent, name);
                    CompletionStage<Void> operation = directory
                            ? remoteFiles(session).createDirectory(target) : remoteFiles(session).createFile(target);
                    operation.whenComplete((ignored, error) -> Platform.runLater(() -> finishFileMutation(
                            session, error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.7e6a71efbf63fd6f"),
                            () -> refreshRemoteDirectoryViews(session, session.currentRemotePath))));
                });
    }

    private Optional<String> promptObjectName(String title, String defaultName) {
        TextInputDialog dialog = new TextInputDialog(defaultName);
        dialog.setOnShown(event -> Platform.runLater(dialog.getEditor()::selectAll));
        dialog.initOwner(rootPane.getScene().getWindow());
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.df4950a17982ca45"));
        return dialog.showAndWait().map(String::strip)
                .filter(name -> !name.isEmpty() && !name.equals(".") && !name.equals("..")
                        && name.indexOf('/') < 0 && name.indexOf('\\') < 0);
    }

    private void deleteLocalObjects(OpenSession session, List<LocalFileEntry> selected) {
        if (!confirmDelete(selected.size())) return;
        deleteContinuing(selected, entry -> workflow.deleteLocal(entry.path(),
                activity -> appendFileActivity(session, activity)))
                .whenComplete((failures, unexpected) -> Platform.runLater(() -> {
                    if (session.closed.get()) return;
                    List<String> messages = new ArrayList<>();
                    if (unexpected != null) {
                        messages.add(deleteFailureMessage(null, unexpected));
                    } else {
                        for (DeletionFailure<LocalFileEntry> failure : failures) {
                            messages.addAll(deleteFailureMessages(failure.item().path().toString(), failure.error()));
                        }
                    }
                    if (!messages.isEmpty()) {
                        appendFileLog(session, messages.size()
                                + com.ynpe.fastremote.i18n.Texts.text("legacy.java.5f6cb87491f93dc1"), StatusTone.ERROR);
                        for (String message : messages) appendFileLog(session, message, StatusTone.ERROR);
                    }
                    Set<Path> deleted = selected.stream()
                            .filter(entry -> unexpected == null && failures.stream()
                                    .noneMatch(failure -> failure.item().equals(entry)))
                            .map(LocalFileEntry::path).collect(java.util.stream.Collectors.toSet());
                    refreshLocalAfterDeletion(session, deleted);
                }));
    }

    void refreshLocalAfterDeletion(OpenSession session, Set<Path> deleted) {
        session.currentLocalPath = SftpBrowserModel.localDirectoryAfterDeletion(
                Path.of(session.currentLocalPath), deleted).toString();
        session.synchronizingLocalTree = true;
        try {
            pruneLocalTreeEntries(session.localTree == null ? null : session.localTree.getRoot(), deleted);
        } finally {
            session.synchronizingLocalTree = false;
        }
        // Reloading the list merges the current folder's tree children from that listing; rebuilding the
        // tree roots would collapse every expanded folder.
        if (session.localTable != null) {
            requestLocalDirectory(session, session.currentLocalPath, session.localTable, session.localPathField);
        }
    }

    private void deleteRemoteObjects(OpenSession session, List<RemoteFileEntry> selected) {
        if (!confirmDelete(selected.size())) return;
        deleteContinuing(selected, entry -> remoteFiles(session).delete(entry.path(),
                activity -> appendFileActivity(session, activity)))
                .whenComplete((failures, unexpected) -> Platform.runLater(() -> {
                    List<String> messages = new ArrayList<>();
                    if (unexpected != null) {
                        messages.add(deleteFailureMessage(null, unexpected));
                    } else {
                        for (DeletionFailure<RemoteFileEntry> failure : failures) {
                            messages.addAll(deleteFailureMessages(failure.item().path(), failure.error()));
                        }
                    }
                    if (!messages.isEmpty()) {
                        appendFileLog(session, messages.size()
                                + com.ynpe.fastremote.i18n.Texts.text("legacy.java.5f6cb87491f93dc1"), StatusTone.ERROR);
                        for (String message : messages) appendFileLog(session, message, StatusTone.ERROR);
                    }
                    pruneRemoteTreeEntries(session.remoteTree == null ? null : session.remoteTree.getRoot(),
                            selected.stream().map(RemoteFileEntry::path)
                                    .map(SftpBrowserModel::normalizeRemotePath)
                                    .collect(java.util.stream.Collectors.toSet()));
                    // Partial deletion changes the directory too, so always refresh after the batch. Reloading the
                    // list merges the current folder's tree children; rebuilding the root collapses the tree.
                    if (session.remoteTable != null) loadRemoteDirectory(session, session.currentRemotePath,
                            session.remoteTable, session.remotePathField);
                }));
    }

    static <T> CompletableFuture<List<DeletionFailure<T>>> deleteContinuing(
            List<T> items, Function<T, CompletionStage<Void>> operation) {
        List<DeletionFailure<T>> failures = new ArrayList<>();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (T item : List.copyOf(items)) {
            chain = chain.thenCompose(ignored -> {
                CompletionStage<Void> current;
                try {
                    current = Objects.requireNonNull(operation.apply(item), "delete operation");
                } catch (RuntimeException failure) {
                    failures.add(new DeletionFailure<>(item, failure));
                    return CompletableFuture.completedFuture(null);
                }
                return current.<Void>handle((result, failure) -> {
                    if (failure != null) failures.add(new DeletionFailure<>(item, failure));
                    return null;
                }).toCompletableFuture();
            });
        }
        return chain.thenApply(ignored -> List.copyOf(failures));
    }

    private static List<String> deleteFailureMessages(String selectedPath, Throwable error) {
        Throwable cause = unwrap(error);
        Throwable[] details = cause.getSuppressed();
        if (details.length == 0) return List.of(deleteFailureMessage(selectedPath, cause));
        List<String> messages = new ArrayList<>(details.length);
        for (Throwable detail : details) messages.add(deleteFailureMessage(null, detail));
        return List.copyOf(messages);
    }

    private static String deleteFailureMessage(String path, Throwable error) {
        String detail = sshSftpFailureMessage(
                error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.c228558cf257fc49"));
        String message = path == null || path.isBlank() ? detail : path + ": " + detail;
        return StatusMessagePresentation.diagnostic(message);
    }

    record DeletionFailure<T>(T item, Throwable error) { }

    private boolean confirmDelete(int count) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(rootPane.getScene().getWindow());
        alert.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a3ea3c17b401bd2f"));
        alert.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b5e1bb61b32e8e52") + count + com.ynpe.fastremote.i18n.Texts.text("legacy.java.c4dd98c6e2efecdb"));
        alert.setContentText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.72effbe1bfc721af"));
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void renameLocalObject(OpenSession session, LocalFileEntry entry) {
        if (entry.path().getParent() == null) return;
        promptObjectName(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0d0cbac2eee54113"), entry.name()).ifPresent(name -> {
            Path target = entry.path().resolveSibling(name);
            if (target.equals(entry.path()) || session.closed.get()) return;
            workflow.renameLocal(entry.path(), target).whenComplete((ignored, error) -> Platform.runLater(() -> {
                if (session.closed.get()) return;
                if (error == null) {
                    session.currentLocalPath = SftpBrowserModel.localDirectoryAfterRename(
                            Path.of(session.currentLocalPath), entry.path(), target).toString();
                }
                finishFileMutation(session, error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.e367f53aeadf33cb"),
                        () -> refreshLocalDirectoryViews(session, session.currentLocalPath));
            }));
        });
    }

    private void renameRemoteObject(OpenSession session, RemoteFileEntry entry) {
        promptObjectName(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0d0cbac2eee54113"), entry.name()).ifPresent(name ->
                remoteFiles(session).rename(entry.path(), joinRemotePath(parentRemotePath(entry.path()), name))
                        .whenComplete((ignored, error) -> Platform.runLater(() -> finishFileMutation(session,
                                error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.e367f53aeadf33cb"),
                                () -> refreshRemoteDirectoryViews(session, session.currentRemotePath)))));
    }

    private void editRemoteProperties(OpenSession session, RemoteFileEntry entry) {
        remoteFiles(session).readAttributes(entry.path()).whenComplete((attributes, readError) ->
                Platform.runLater(() -> {
                    if (readError != null) {
                        appendFileLog(session, sshSftpFailureMessage(readError,
                                com.ynpe.fastremote.i18n.Texts.text("legacy.java.29cdb1476f217ab3")), StatusTone.ERROR);
                        return;
                    }
                    RemoteFilePropertiesDialog.show(rootPane.getScene().getWindow(), attributes)
                            .filter(permissions -> permissions != attributes.permissions())
                            .ifPresent(permissions -> remoteFiles(session).setPermissions(entry.path(), permissions)
                                    .whenComplete((ignored, writeError) -> Platform.runLater(() ->
                                            finishFileMutation(session, writeError, com.ynpe.fastremote.i18n.Texts.text("legacy.java.74f2a1d76f73f062"),
                                                    () -> refreshRemoteDirectoryViews(
                                                            session, session.currentRemotePath)))));
                }));
    }

    private void finishFileMutation(OpenSession session, Throwable error, String fallback, Runnable refresh) {
        if (error != null) appendFileLog(session, sshSftpFailureMessage(error, fallback), StatusTone.ERROR);
        else refresh.run();
    }

    private static void copyText(List<String> values) {
        if (values.isEmpty()) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(String.join(System.lineSeparator(), values));
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void revealLocal(LocalFileEntry entry) {
        workflow.revealLocal(entry.path(), !entry.directory()).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (error != null) setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.9924447f0b807036"), StatusTone.ERROR);
        }));
    }

    private CheckMenuItem hiddenFilesMenuItem(boolean local) {
        CheckMenuItem item = new CheckMenuItem(localizer.text("sftp.context.hiddenFiles"));
        item.setSelected(local
                ? persistedWorkspaceSettings.windowSettings().showLocalFileHiddenFiles()
                : persistedWorkspaceSettings.sftpSettings().showHiddenFiles());
        return item;
    }

    private CheckMenuItem directoryTreeMenuItem(boolean local) {
        CheckMenuItem item = new CheckMenuItem(localizer.text("sftp.context.directoryTree"));
        item.setSelected(local
                ? persistedWorkspaceSettings.windowSettings().showLocalFileDirectoryTree()
                : persistedWorkspaceSettings.showSftpDirectoryTree());
        return item;
    }

    private void toggleHiddenFiles(OpenSession session) {
        setHiddenFiles(session, !persistedWorkspaceSettings.sftpSettings().showHiddenFiles());
    }

    private void setHiddenFiles(OpenSession session, boolean show) {
        SftpSettings sftp = persistedWorkspaceSettings.sftpSettings();
        if (sftp.showHiddenFiles() == show) return;
        GlobalSettings previous = persistedWorkspaceSettings;
        GlobalSettings updated = previous.withSftpSettings(
                sftp.withShowHiddenFiles(show));
        persistedWorkspaceSettings = updated;
        settingsWorkflow.save(updated).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (error != null) {
                if (persistedWorkspaceSettings == updated) persistedWorkspaceSettings = previous;
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b72f7f29e4b0299f"), StatusTone.ERROR);
                return;
            }
            new HashSet<>(openSessions.values()).stream()
                    .filter(open -> open.state == ConnectionState.CONNECTED)
                    .filter(open -> open.sftp != null || open.terminal != null)
                    .forEach(this::refreshCurrentSftpPanel);
        }));
    }

    private void setSftpDirectoryTreeVisible(boolean show) {
        if (persistedWorkspaceSettings.showSftpDirectoryTree() == show) return;
        GlobalSettings previous = persistedWorkspaceSettings;
        GlobalSettings updated = previous.withShowSftpDirectoryTree(show);
        persistedWorkspaceSettings = updated;
        settingsWorkflow.save(updated).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (error != null) {
                if (persistedWorkspaceSettings == updated) persistedWorkspaceSettings = previous;
                setStatus(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3abd9672dab81cf7"),
                        StatusTone.ERROR);
                return;
            }
            refreshSftpWorkspaceLayouts();
        }));
    }

    private void refreshCurrentSftpPanel(OpenSession session) {
        if (session.localTable != null) requestLocalDirectory(session, session.currentLocalPath,
                session.localTable, session.localPathField);
        if (session.remoteTable != null) loadRemoteDirectory(session, session.currentRemotePath,
                session.remoteTable, session.remotePathField);
        refreshLocalTreeRoots(session);
        refreshRemoteTreeRoot(session);
    }

    private void refreshLocalDirectoryViews(OpenSession session, String path) {
        refreshLocalTreeRoots(session);
        if (session.localTable != null) {
            requestLocalDirectory(session, path, session.localTable, session.localPathField);
        }
    }

    private void refreshRemoteDirectoryViews(OpenSession session, String path) {
        refreshRemoteTreeRoot(session);
        if (session.remoteTable != null) {
            loadRemoteDirectory(session, path, session.remoteTable, session.remotePathField);
        }
    }

    void refreshLocalTreeRoots(OpenSession session) {
        long generation = ++session.localTreeGeneration;
        if (session.localTree == null || session.localTree.getRoot() == null) return;
        TreeView<LocalFileEntry> tree = session.localTree;
        workflow.localRoots().whenComplete((roots, error) -> Platform.runLater(() -> {
            if (error != null || session.closed.get() || tree != session.localTree
                    || generation != session.localTreeGeneration) return;
            session.synchronizingLocalTree = true;
            try {
                tree.getRoot().getChildren().setAll(roots.stream()
                        .filter(entry -> persistedWorkspaceSettings.windowSettings()
                                .showLocalFileHiddenFiles() || !entry.hidden())
                        .map(MainWindowController::localTreeItem).toList());
            } finally {
                session.synchronizingLocalTree = false;
            }
            try {
                synchronizeLocalTree(session, Path.of(session.currentLocalPath));
            } catch (java.nio.file.InvalidPathException ignored) {
                // The current field is restored by the list navigation path.
            }
        }));
    }

    private void refreshRemoteTreeRoot(OpenSession session) {
        if (session.remoteTree == null) return;
        remoteFiles(session).canonicalPath("/").whenComplete((canonical, error) -> Platform.runLater(() -> {
            if (error != null || session.remoteTree == null) return;
            TreeItem<RemoteFileEntry> root = remoteTreeRoot(session, canonical);
            session.remoteTree.setRoot(root);
            root.setExpanded(true);
            synchronizeRemoteTree(session, session.currentRemotePath);
        }));
    }

    private void refreshCurrentLocalTreeNode(OpenSession session) {
        if (session.localTree == null || session.localTree.getRoot() == null) return;
        try {
            Path target = Path.of(session.currentLocalPath).toAbsolutePath().normalize();
            TreeItem<LocalFileEntry> item = findLocalTreeItem(session.localTree.getRoot(), target);
            if (item == null) refreshLocalTreeRoots(session);
            else refreshLocalTreeNode(session, item);
        } catch (java.nio.file.InvalidPathException ignored) {
            refreshLocalTreeRoots(session);
        }
    }

    private void refreshCurrentRemoteTreeNode(OpenSession session) {
        if (session.remoteTree == null || session.remoteTree.getRoot() == null) return;
        TreeItem<RemoteFileEntry> item = findRemoteTreeItem(
                session.remoteTree.getRoot(), normalizeRemotePath(session.currentRemotePath));
        if (item == null) refreshRemoteTreeRoot(session);
        else refreshRemoteTreeNode(session, item);
    }

    private void refreshLocalTreeNode(OpenSession session, TreeItem<LocalFileEntry> item) {
        if (item == null || item.getValue() == null) {
            refreshLocalTreeRoots(session);
            return;
        }
        boolean expanded = item.isExpanded();
        item.getChildren().setAll(new TreeItem<>());
        loadLocalTreeChildrenAsync(session, item).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (error == null && expanded) item.setExpanded(true);
        }));
    }

    private void refreshRemoteTreeNode(OpenSession session, TreeItem<RemoteFileEntry> item) {
        if (item == null || item.getValue() == null) {
            refreshRemoteTreeRoot(session);
            return;
        }
        boolean expanded = item.isExpanded();
        item.getChildren().setAll(new TreeItem<>());
        loadRemoteTreeChildrenAsync(session, item).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (error == null && expanded) item.setExpanded(true);
        }));
    }

    private static Label directoryStatisticsLabel() {
        Label label = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b43095e8e10a6536"));
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add("sftp-directory-statistics");
        return label;
    }

    static TableView<LocalFileEntry> localFileTable() {
        TableView<LocalFileEntry> table = new TableView<>();
        List<Double> widths = defaultLocalFileColumnWidths();
        TableColumn<LocalFileEntry, String> name = new TableColumn<>(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d44e9b3d3b31d37b"));
        name.setPrefWidth(widths.get(0));
        name.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().name()));
        name.setCellFactory(ignored -> fileNameCell(
                LocalFileEntry::directory, LocalFileEntry::symbolicLink));
        TableColumn<LocalFileEntry, String> size = new TableColumn<>(com.ynpe.fastremote.i18n.Texts.text("legacy.java.50db7447b966f5ef"));
        size.setPrefWidth(widths.get(1));
        size.setCellValueFactory(row -> new SimpleStringProperty(
                blankFileEntry(row.getValue().name()) ? ""
                        : row.getValue().directory() ? "—" : humanSize(row.getValue().size())));
        TableColumn<LocalFileEntry, String> type = new TableColumn<>(
                com.ynpe.fastremote.i18n.Texts.text("localFiles.column.type"));
        type.setPrefWidth(widths.get(2));
        type.setCellValueFactory(row -> new SimpleStringProperty(localFileType(row.getValue())));
        TableColumn<LocalFileEntry, String> modified = new TableColumn<>(com.ynpe.fastremote.i18n.Texts.text("legacy.java.257bbcc44c839db4"));
        modified.setPrefWidth(widths.get(3));
        modified.setCellValueFactory(row -> new SimpleStringProperty(
                blankFileEntry(row.getValue().name()) ? ""
                        : row.getValue().modifiedAt()
                                .map(MainWindowController::formatSftpFileTime).orElse("—")));
        TableColumn<LocalFileEntry, String> created = new TableColumn<>(
                com.ynpe.fastremote.i18n.Texts.text("localFiles.column.created"));
        created.setPrefWidth(widths.get(4));
        created.setCellValueFactory(row -> new SimpleStringProperty(
                blankFileEntry(row.getValue().name()) ? ""
                        : row.getValue().createdAt()
                                .map(MainWindowController::formatSftpFileTime).orElse("—")));
        table.getColumns().setAll(List.of(name, size, type, modified, created));
        keepFileHelperRowsAtEdges(table, LocalFileEntry::name);
        table.setPlaceholder(new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a62ff14fb72adfe6")));
        return table;
    }

    static List<Double> defaultLocalFileColumnWidths() {
        return List.of(220.0, 95.0, 125.0, 170.0, 170.0);
    }

    static String localFileType(LocalFileEntry entry) {
        if (blankFileEntry(entry.name())) return "";
        if (entry.symbolicLink()) return com.ynpe.fastremote.i18n.Texts.text("localFiles.type.symbolicLink");
        if (entry.directory()) return com.ynpe.fastremote.i18n.Texts.text("localFiles.type.folder");
        return com.ynpe.fastremote.i18n.Texts.text("localFiles.type.file");
    }

    static List<Double> defaultSftpFileColumnWidths() {
        return List.of(195.0, 95.0, 170.0);
    }

    private static <T> void keepFileHelperRowsAtEdges(
            TableView<T> table, java.util.function.Function<T, String> name) {
        table.setSortPolicy(view -> {
            Comparator<T> comparator = view.getComparator();
            if (comparator == null) return true;
            FXCollections.sort(view.getItems(), (left, right) -> {
                int leftRank = fileRowRank(name.apply(left));
                int rightRank = fileRowRank(name.apply(right));
                return leftRank == rightRank ? comparator.compare(left, right)
                        : Integer.compare(leftRank, rightRank);
            });
            return true;
        });
    }

    private static int fileRowRank(String name) {
        if ("..".equals(name)) return 0;
        if (blankFileEntry(name)) return 2;
        return 1;
    }

    static String formatSftpFileTime(Instant value) {
        return formatSftpFileTime(value, ZoneId.systemDefault());
    }

    static String formatSftpFileTime(Instant value, ZoneId zone) {
        return com.ynpe.fastremote.i18n.Texts.localizer().formatDateTime(value, zone);
    }

    private Node createLocalDirectoryContent(
            OpenSession session, TableView<LocalFileEntry> table, TextField path) {
        Node tableSurface = fileTableSurface(table);
        if (!persistedWorkspaceSettings.windowSettings().showLocalFileDirectoryTree()) {
            return tableSurface;
        }
        CenteredTreeView<LocalFileEntry> tree = new CenteredTreeView<>();
        session.localTree = tree;
        tree.setShowRoot(false);
        tree.setCellFactory(ignored -> {
            TreeCell<LocalFileEntry> cell = localFileTreeCell();
            cell.setOnContextMenuRequested(event -> {
                if (cell.getItem() == null) return;
                tree.getSelectionModel().select(cell.getTreeItem());
                localContextMenu(session, cell.getItem(), cell.getTreeItem(), true)
                        .show(cell, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            cell.setOnDragDetected(event -> {
                if (cell.getItem() == null) return;
                beginSftpDrag(session);
                session.draggedLocalEntries = List.of(cell.getItem());
                session.draggedRemoteEntries = List.of();
                ClipboardContent content = new ClipboardContent();
                content.putString("fastremote-local-tree");
                content.putFiles(List.of(cell.getItem().path().toFile()));
                TerminalPathDragSupport.putLocalPaths(content,
                        List.of(cell.getItem().path().toString()));
                cell.startDragAndDrop(TransferMode.COPY_OR_MOVE).setContent(content);
                event.consume();
            });
            cell.setOnDragOver(event -> {
                if (WorkspacePaneCoordinator.isWorkspaceDragPayload(event.getDragboard())) return;
                if (cell.getItem() != null && cell.getItem().directory()
                        && !session.draggedRemoteEntries.isEmpty()) event.acceptTransferModes(TransferMode.COPY);
                event.consume();
            });
            cell.setOnDragDropped(event -> {
                if (WorkspacePaneCoordinator.isWorkspaceDragPayload(event.getDragboard())) return;
                List<RemoteFileEntry> dragged = transferableRemoteEntries(session.draggedRemoteEntries);
                Path target = cell.getItem() == null ? null : cell.getItem().path();
                if (target != null && !dragged.isEmpty()) {
                    deferSftpDropUntilDragDone(session, () -> downloadEntries(session, dragged, target));
                }
                clearSftpDrag(session);
                event.setDropCompleted(target != null && !dragged.isEmpty());
                event.consume();
            });
            cell.setOnDragDone(event -> finishSftpDrag(session));
            session.localMoves.installTarget(cell, () -> cell.getItem() != null
                    && cell.getItem().directory() ? cell.getItem().path() : null);
            return cell;
        });
        tree.setOnContextMenuRequested(event -> {
            localContextMenu(session, null, null, true).show(tree, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        TreeItem<LocalFileEntry> hiddenRoot = new TreeItem<>();
        tree.setRoot(hiddenRoot);
        installTreeBranchLoader(hiddenRoot, item -> loadLocalTreeChildren(session, item));
        tree.getSelectionModel().selectedItemProperty().addListener((ignored, previous, selected) -> {
            if (!session.synchronizingLocalTree && selected != null
                    && selected.getValue() != null && selected.getValue().directory()) {
                loadLocalDirectory(session, selected.getValue().path(), table, path);
            }
        });
        long generation = session.localTreeGeneration;
        workflow.localRoots().whenComplete((roots, error) -> Platform.runLater(() -> {
            if (session.closed.get() || session.localTree != tree
                    || generation != session.localTreeGeneration) return;
            if (error != null) {
                appendFileLog(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.866801c0d5e084f6")
                        + sshSftpFailureMessage(error,
                                com.ynpe.fastremote.i18n.Texts.text("legacy.java.13a46616e16fe5e6")), StatusTone.ERROR);
            } else {
                hiddenRoot.getChildren().setAll(roots.stream().map(MainWindowController::localTreeItem).toList());
                try {
                    synchronizeLocalTree(session, Path.of(path.getText()).toAbsolutePath().normalize());
                } catch (java.nio.file.InvalidPathException ignored) {
                    // The path field validation reports this through the normal navigation flow.
                }
            }
        }));
        SplitPane split = new SplitPane(tree, tableSurface);
        split.setDividerPositions(0.36);
        return split;
    }

    static TreeCell<LocalFileEntry> localFileTreeCell() {
        return new TreeCell<>() {
            private final KeyedGraphicCache<SftpGraphicKey, Node> graphicCache = new KeyedGraphicCache<>();

            @Override
            protected void updateItem(LocalFileEntry item, boolean empty) {
                super.updateItem(item, empty);
                boolean loading = !empty && item == null && getTreeItem() != null;
                getStyleClass().remove("sftp-tree-loading");
                if (loading) getStyleClass().add("sftp-tree-loading");
                setText(loading ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.86b6d0d63062ba81") : empty || item == null ? null : item.name());
                setGraphic(empty || item == null ? null : graphicCache.valueFor(
                        sftpGraphicKey(item.name(), item.directory(), item.symbolicLink()),
                        () -> fileEntryGraphic(item.name(), item.directory(), item.symbolicLink())));
            }
        };
    }

    private static TreeCell<RemoteFileEntry> remoteFileTreeCell() {
        return new TreeCell<>() {
            private final KeyedGraphicCache<SftpGraphicKey, Node> graphicCache = new KeyedGraphicCache<>();

            @Override
            protected void updateItem(RemoteFileEntry item, boolean empty) {
                super.updateItem(item, empty);
                boolean loading = !empty && item == null && getTreeItem() != null;
                getStyleClass().remove("sftp-tree-loading");
                if (loading) getStyleClass().add("sftp-tree-loading");
                setText(loading ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.86b6d0d63062ba81") : empty || item == null ? null : item.name());
                setGraphic(empty || item == null ? null : graphicCache.valueFor(
                        sftpGraphicKey(item.name(), item.directory(), item.symbolicLink()),
                        () -> fileEntryGraphic(item.name(), item.directory(), item.symbolicLink())));
            }
        };
    }

    private static <T> TableCell<T, String> fileNameCell(
            java.util.function.Function<T, Boolean> directory,
            java.util.function.Function<T, Boolean> symbolicLink) {
        return new TableCell<>() {
            private final KeyedGraphicCache<SftpGraphicKey, Node> graphicCache = new KeyedGraphicCache<>();

            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                T entry = empty || getTableRow() == null ? null : getTableRow().getItem();
                boolean blank = entry != null && blankFileEntry(name);
                setText(entry == null || blank ? null : name);
                boolean isDirectory = entry != null && directory.apply(entry);
                boolean isSymbolicLink = entry != null && symbolicLink.apply(entry);
                setGraphic(entry == null || blank ? null : graphicCache.valueFor(
                        sftpGraphicKey(name, isDirectory, isSymbolicLink),
                        () -> fileEntryGraphic(name, isDirectory, isSymbolicLink)));
                setGraphicTextGap(7.0);
            }
        };
    }

    private static Node fileEntryGraphic(String name, boolean directory, boolean symbolicLink) {
        StackPane graphic = new StackPane();
        graphic.setMinSize(16.0, 15.0);
        graphic.setPrefSize(16.0, 15.0);
        graphic.setMaxSize(16.0, 15.0);
        SftpFileIconResolver.Type type = SftpFileIconResolver.resolve(name, directory);
        LucideIconView base = new LucideIconView(type.icon());
        base.getStyleClass().setAll("sftp-entry-icon", type.styleClass());
        graphic.getChildren().add(base);
        if (symbolicLink) {
            LucideIconView link = new LucideIconView(Icon.EXTERNAL_LINK);
            link.getStyleClass().setAll("sftp-symbolic-link-icon");
            link.setScaleX(0.34);
            link.setScaleY(0.34);
            link.setTranslateX(4.5);
            link.setTranslateY(4.0);
            graphic.getChildren().add(link);
        }
        return graphic;
    }

    private static SftpGraphicKey sftpGraphicKey(String name, boolean directory, boolean symbolicLink) {
        return new SftpGraphicKey(SftpFileIconResolver.resolve(name, directory), symbolicLink);
    }

    static TreeItem<LocalFileEntry> localTreeItem(LocalFileEntry entry) {
        TreeItem<LocalFileEntry> item = new TreeItem<>(entry);
        if (entry.directory()) item.getChildren().add(new TreeItem<>());
        return item;
    }

    private static TreeItem<RemoteFileEntry> remoteTreeItem(RemoteFileEntry entry) {
        TreeItem<RemoteFileEntry> item = new TreeItem<>(entry);
        if (entry.directory()) item.getChildren().add(new TreeItem<>());
        return item;
    }

    private TreeItem<RemoteFileEntry> remoteTreeRoot(OpenSession session, String canonical) {
        TreeItem<RemoteFileEntry> root = remoteTreeItem(new RemoteFileEntry(
                remoteBaseName(canonical), canonical, true, false, 0L, Optional.empty()));
        installTreeBranchLoader(root, item -> loadRemoteTreeChildren(session, item));
        return root;
    }

    private void loadLocalTreeChildren(OpenSession session, TreeItem<LocalFileEntry> item) {
        loadLocalTreeChildrenAsync(session, item);
    }

    private CompletionStage<Void> loadLocalTreeChildrenAsync(
            OpenSession session, TreeItem<LocalFileEntry> item) {
        LocalFileEntry directory = item.getValue();
        if (directory == null || !directory.directory()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> inProgress = session.localTreeLoads.get(item);
        if (inProgress != null) return inProgress;
        if (!hasTreePlaceholder(item)) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> loaded = new CompletableFuture<>();
        long generation = session.localTreeGeneration;
        session.localTreeLoads.put(item, loaded);
        workflow.listLocal(directory.path()).whenComplete((entries, error) -> Platform.runLater(() -> {
            session.localTreeLoads.remove(item);
            if (session.closed.get() || generation != session.localTreeGeneration) {
                loaded.completeExceptionally(new CancellationException("SFTP local tree was replaced or closed"));
                return;
            }
            if (error != null) {
                appendFileLog(session, sshSftpFailureMessage(error,
                        com.ynpe.fastremote.i18n.Texts.text("legacy.java.c0c379c78eefb7cd")), StatusTone.ERROR);
                restoreTreePlaceholderAndCollapse(item);
                loaded.completeExceptionally(error);
            } else {
                replaceTreeChildren(item, visibleTreeDirectories(
                        entries, LocalFileEntry::directory, LocalFileEntry::hidden,
                        persistedWorkspaceSettings.windowSettings().showLocalFileHiddenFiles()).stream()
                        .map(MainWindowController::localTreeItem).toList());
                loaded.complete(null);
            }
        }));
        return loaded;
    }

    private void loadRemoteTreeChildren(OpenSession session, TreeItem<RemoteFileEntry> item) {
        loadRemoteTreeChildrenAsync(session, item);
    }

    private CompletionStage<Void> loadRemoteTreeChildrenAsync(
            OpenSession session, TreeItem<RemoteFileEntry> item) {
        RemoteFileEntry directory = item.getValue();
        if (directory == null || !directory.directory()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> inProgress = session.remoteTreeLoads.get(item);
        if (inProgress != null) return inProgress;
        if (!hasTreePlaceholder(item)) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> loaded = new CompletableFuture<>();
        session.remoteTreeLoads.put(item, loaded);
        remoteFiles(session).list(directory.path()).whenComplete((entries, error) -> Platform.runLater(() -> {
            session.remoteTreeLoads.remove(item);
            if (session.closed.get()) {
                loaded.completeExceptionally(new CancellationException("SFTP workspace closed"));
                return;
            }
            if (error != null) {
                appendFileLog(session, remoteDirectoryFailureMessage(error), StatusTone.ERROR);
                restoreTreePlaceholderAndCollapse(item);
                loaded.completeExceptionally(error);
            } else {
                replaceTreeChildren(item, visibleTreeDirectories(
                        entries, RemoteFileEntry::directory, entry -> entry.name().startsWith("."),
                        persistedWorkspaceSettings.sftpSettings().showHiddenFiles()).stream()
                        .map(MainWindowController::remoteTreeItem).toList());
                loaded.complete(null);
            }
        }));
        return loaded;
    }

    private void synchronizeLocalTree(OpenSession session, Path target) {
        synchronizeLocalTree(session, target, null);
    }

    /**
     * Selects {@code target} in the local tree. When the file list has just loaded it, {@code listing} fills and
     * expands the node directly instead of leaving it collapsed or listing the directory a second time.
     */
    private void synchronizeLocalTree(OpenSession session, Path target, List<LocalFileEntry> listing) {
        long generation = session.localTreeGeneration;
        long directoryRequest = session.localDirectoryRequest;
        TreeView<LocalFileEntry> tree = session.localTree;
        if (tree == null || tree.getRoot() == null) return;
        TreeItem<LocalFileEntry> root = tree.getRoot().getChildren().stream()
                .filter(item -> target.startsWith(item.getValue().path()))
                .findFirst().orElse(null);
        if (root == null) return;
        ensureLocalTreeNode(session, root, target).whenComplete((item, error) -> Platform.runLater(() -> {
            if (error != null || item == null || session.closed.get() || tree != session.localTree
                    || generation != session.localTreeGeneration
                    || directoryRequest != session.localDirectoryRequest
                    || !target.toString().equals(session.currentLocalPath)) return;
            session.synchronizingLocalTree = true;
            try {
                if (listing != null && !session.localTreeLoads.containsKey(item)) {
                    populateTreeChildren(item, visibleTreeDirectories(
                            listing, LocalFileEntry::directory, LocalFileEntry::hidden,
                            persistedWorkspaceSettings.windowSettings().showLocalFileHiddenFiles()),
                            LocalFileEntry::path, MainWindowController::localTreeItem);
                    item.setExpanded(true);
                }
                expandParents(item);
                tree.getSelectionModel().select(item);
                centerTreeSelection(tree, item);
            } finally {
                session.synchronizingLocalTree = false;
            }
        }));
    }

    private CompletionStage<TreeItem<LocalFileEntry>> ensureLocalTreeNode(
            OpenSession session, TreeItem<LocalFileEntry> current, Path target) {
        if (current.getValue().path().equals(target)) return CompletableFuture.completedFuture(current);
        return loadLocalTreeChildrenAsync(session, current).thenCompose(ignored -> {
            TreeItem<LocalFileEntry> child = current.getChildren().stream()
                    .filter(item -> target.startsWith(item.getValue().path()))
                    .max(Comparator.comparingInt(item -> item.getValue().path().getNameCount()))
                    .orElse(null);
            if (child != null) return ensureLocalTreeNode(session, child, target);
            TreeItem<LocalFileEntry> lazyChild = addLazyLocalTreeChild(current, target);
            return lazyChild == null ? CompletableFuture.completedFuture(current)
                    : ensureLocalTreeNode(session, lazyChild, target);
        });
    }

    private void synchronizeRemoteTree(OpenSession session, String target) {
        synchronizeRemoteTree(session, target, null);
    }

    /**
     * Selects {@code target} in the remote tree. When the file list has just loaded it, {@code listing} fills and
     * expands the node directly instead of leaving it collapsed or listing the directory a second time.
     */
    private void synchronizeRemoteTree(OpenSession session, String target, List<RemoteFileEntry> listing) {
        TreeView<RemoteFileEntry> tree = session.remoteTree;
        if (tree == null || tree.getRoot() == null) return;
        String normalizedTarget = normalizeRemotePath(target);
        long directoryRequest = session.remoteDirectoryRequest;
        ensureRemoteTreeNode(session, tree.getRoot(), normalizedTarget)
                .whenComplete((item, error) -> Platform.runLater(() -> {
                    if (error != null || item == null || session.closed.get()
                            || !remoteTreeSynchronizationStillCurrent(
                                    session, normalizedTarget, directoryRequest)) return;
                    session.synchronizingRemoteTree = true;
                    try {
                        if (listing != null && !session.remoteTreeLoads.containsKey(item)) {
                            populateTreeChildren(item, visibleTreeDirectories(
                                    listing, RemoteFileEntry::directory, entry -> entry.name().startsWith("."),
                                    persistedWorkspaceSettings.sftpSettings().showHiddenFiles()),
                                    entry -> normalizeRemotePath(entry.path()), MainWindowController::remoteTreeItem);
                            item.setExpanded(true);
                        }
                        expandParents(item);
                        tree.getSelectionModel().select(item);
                        centerTreeSelection(tree, item);
                    } finally {
                        session.synchronizingRemoteTree = false;
                    }
                }));
    }

    static boolean remoteTreeSynchronizationStillCurrent(
            OpenSession session, String target, long directoryRequest) {
        return session.remoteDirectoryRequest == directoryRequest
                && normalizeRemotePath(session.currentRemotePath).equals(normalizeRemotePath(target));
    }

    private static <T> void centerTreeSelection(TreeView<T> tree, TreeItem<T> item) {
        if (tree instanceof CenteredTreeView<?> centeredTree) centeredTree.centerSelection();
        else tree.scrollTo(tree.getRow(item));
    }

    private CompletionStage<TreeItem<RemoteFileEntry>> ensureRemoteTreeNode(
            OpenSession session, TreeItem<RemoteFileEntry> current, String target) {
        String currentPath = normalizeRemotePath(current.getValue().path());
        if (currentPath.equals(target)) return CompletableFuture.completedFuture(current);
        return loadRemoteTreeChildrenAsync(session, current).thenCompose(ignored -> {
            TreeItem<RemoteFileEntry> child = current.getChildren().stream()
                    .filter(item -> remotePathContains(item.getValue().path(), target))
                    .max(Comparator.comparingInt(item -> normalizeRemotePath(item.getValue().path()).length()))
                    .orElse(null);
            if (child != null) return ensureRemoteTreeNode(session, child, target);
            TreeItem<RemoteFileEntry> lazyChild = addLazyRemoteTreeChild(current, target);
            return lazyChild == null ? CompletableFuture.completedFuture(current)
                    : ensureRemoteTreeNode(session, lazyChild, target);
        });
    }

    static TreeItem<LocalFileEntry> addLazyLocalTreeChild(
            TreeItem<LocalFileEntry> parent, Path target) {
        Path parentPath = parent.getValue().path();
        if (!target.startsWith(parentPath) || parentPath.equals(target)) return null;
        Path relative = parentPath.relativize(target);
        if (relative.getNameCount() == 0) return null;
        Path childPath = parentPath.resolve(relative.getName(0)).normalize();
        TreeItem<LocalFileEntry> child = localTreeItem(new LocalFileEntry(
                childPath.getFileName().toString(), childPath, true, false, 0L, Optional.empty()));
        parent.getChildren().add(child);
        return child;
    }

    static TreeItem<RemoteFileEntry> addLazyRemoteTreeChild(
            TreeItem<RemoteFileEntry> parent, String target) {
        String parentPath = normalizeRemotePath(parent.getValue().path());
        String normalizedTarget = normalizeRemotePath(target);
        if (!normalizedTarget.startsWith("/") || !remotePathContains(parentPath, normalizedTarget)
                || parentPath.equals(normalizedTarget)) return null;
        String remainder = parentPath.equals("/")
                ? normalizedTarget.substring(1) : normalizedTarget.substring(parentPath.length() + 1);
        int separator = remainder.indexOf('/');
        String name = separator < 0 ? remainder : remainder.substring(0, separator);
        if (name.isBlank()) return null;
        String childPath = parentPath.equals("/") ? "/" + name : parentPath + "/" + name;
        TreeItem<RemoteFileEntry> child = remoteTreeItem(new RemoteFileEntry(
                name, childPath, true, false, 0L, Optional.empty()));
        parent.getChildren().add(child);
        return child;
    }

    private boolean showSftpEntry(boolean hidden) {
        return persistedWorkspaceSettings.sftpSettings().showHiddenFiles() || !hidden;
    }

    private static void updateDirectoryStatistics(Label label, DirectoryStatistics statistics) {
        if (label != null) label.setText(statistics.summary());
    }

    private void loadLocalDirectory(
            OpenSession session,
            Path requestedPath,
            TableView<LocalFileEntry> table,
            TextField pathField) {
        loadLocalDirectory(session, requestedPath, table, pathField, null);
    }

    private void loadLocalDirectory(
            OpenSession session,
            Path requestedPath,
            TableView<LocalFileEntry> table,
            TextField pathField,
            CompletableFuture<Boolean> completion) {
        Path normalized = requestedPath.toAbsolutePath().normalize();
        long request = ++session.localDirectoryRequest;
        table.setDisable(true);
        workflow.listLocal(normalized).whenComplete((entries, error) -> Platform.runLater(() -> {
            if (request != session.localDirectoryRequest || session.closed.get()) {
                if (completion != null) completion.completeExceptionally(
                        new CancellationException("SFTP local directory request was superseded"));
                return;
            }
            table.setDisable(false);
            if (error != null) {
                session.suppressLocalHistory = false;
                String message = sshSftpFailureMessage(
                        error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.f4b7513a137fc5ee"));
                pathField.setText(session.currentLocalPath);
                appendFileLog(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.5df848377af967dc") + normalized + " · " + message, StatusTone.ERROR);
                if (completion != null) completion.completeExceptionally(unwrap(error));
            } else {
                if (!normalized.toString().equals(session.currentLocalPath)) {
                    if (session.suppressLocalHistory) session.suppressLocalHistory = false;
                    else {
                        session.localBack.addLast(session.currentLocalPath);
                        session.localHistoryNext.clear();
                    }
                }
                pathField.setText(normalized.toString());
                session.currentLocalPath = normalized.toString();
                List<LocalFileEntry> visible = entries.stream()
                        .filter(entry -> persistedWorkspaceSettings.windowSettings()
                                .showLocalFileHiddenFiles() || !entry.hidden()).toList();
                table.getItems().setAll(withLocalNavigation(normalized, visible));
                updateDirectoryStatistics(session.localStatisticsLabel, localDirectoryStatistics(visible));
                synchronizeLocalTree(session, normalized, entries);
                appendFileLog(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.424132bb5c0e92f2") + normalized + "（" + visible.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.bb3e164d5f076623"));
                if (completion != null) completion.complete(true);
            }
        }));
    }

    private void requestLocalDirectory(
            OpenSession session,
            String requestedPath,
            TableView<LocalFileEntry> table,
            TextField pathField) {
        try {
            loadLocalDirectory(session, Path.of(requestedPath), table, pathField);
        } catch (java.nio.file.InvalidPathException invalid) {
            showInvalidLocalPath(session, requestedPath);
        }
    }

    private void showInvalidLocalPath(OpenSession session, String path) {
        appendFileLog(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.f357d40755aab939") + path, StatusTone.ERROR);
        if (session.localPathField != null) session.localPathField.setText(session.currentLocalPath);
    }

    private void loadRemoteDirectory(
            OpenSession session,
            String requestedPath,
            TableView<RemoteFileEntry> table,
            TextField pathField) {
        loadRemoteDirectory(session, requestedPath, table, pathField, null);
    }

    private void loadRemoteDirectory(
            OpenSession session,
            String requestedPath,
            TableView<RemoteFileEntry> table,
            TextField pathField,
            CompletableFuture<Boolean> completion) {
        table.setDisable(true);
        long request = ++session.remoteDirectoryRequest;
        appendFileLog(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.10180991f64f11b2") + requestedPath);
        RemoteFilesHandle files = remoteFiles(session);
        files.canonicalPath(requestedPath).thenCompose(canonical ->
                files.list(canonical).thenApply(entries -> new RemoteListing(canonical, entries)))
                .whenComplete((listing, error) -> Platform.runLater(() -> {
                    if (request != session.remoteDirectoryRequest || session.closed.get()) {
                        if (completion != null) completion.completeExceptionally(
                                new CancellationException("SFTP remote directory request was superseded"));
                        return;
                    }
                    table.setDisable(false);
                    if (error != null) {
                        if (completion == null && consumeInitialRemotePathFallback(session, requestedPath)) {
                            pathField.setText(".");
                            appendFileLog(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.782ce9da7e9c3c4a") + requestedPath);
                            loadRemoteDirectory(session, ".", table, pathField);
                            return;
                        }
                        session.suppressRemoteHistory = false;
                        String message = remoteDirectoryFailureMessage(error);
                        pathField.setText(session.currentRemotePath);
                        appendFileLog(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.206ccbf997ff77f7") + requestedPath + " · " + message,
                                StatusTone.ERROR);
                        if (completion != null) completion.completeExceptionally(unwrap(error));
                    } else {
                        session.initialRemotePathFallbackPending = false;
                        if (!session.remoteHomeCaptured && ".".equals(requestedPath)) {
                            session.remoteHome = listing.path();
                            session.remoteHomeCaptured = true;
                            session.latestInferredWorkingDirectory = expandRemoteHomePath(
                                    session.latestInferredWorkingDirectory, listing.path());
                        }
                        if (!listing.path().equals(session.currentRemotePath)) {
                            if (session.suppressRemoteHistory) session.suppressRemoteHistory = false;
                            else {
                                session.remoteBack.addLast(session.currentRemotePath);
                                session.remoteHistoryNext.clear();
                            }
                        }
                        pathField.setText(listing.path());
                        session.currentRemotePath = listing.path();
                        String pendingSelection = session.pendingRemoteSelectionPath;
                        List<RemoteFileEntry> visible = listing.entries().stream()
                                .filter(entry -> showSftpEntry(entry.name().startsWith("."))
                                        || pendingSelection != null && normalizeRemotePath(entry.path())
                                                .equals(normalizeRemotePath(pendingSelection)))
                                .toList();
                        table.getItems().setAll(withRemoteNavigation(listing.path(), visible));
                        selectPendingRemoteFile(session, listing.path(), table);
                        updateDirectoryStatistics(
                                session.remoteStatisticsLabel, remoteDirectoryStatistics(visible));
                        synchronizeRemoteTree(session, listing.path(), listing.entries());
                        appendFileLog(session,
                                com.ynpe.fastremote.i18n.Texts.text("legacy.java.10a7b1cf56bde3d3") + listing.path() + "（" + visible.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.bb3e164d5f076623"));
                        if (completion != null) completion.complete(true);
                    }
                }));
    }

    private static void selectPendingRemoteFile(
            OpenSession session, String directory, TableView<RemoteFileEntry> table) {
        String target = session.pendingRemoteSelectionPath;
        if (target == null || !normalizeRemotePath(parentRemotePath(target))
                .equals(normalizeRemotePath(directory))) return;
        table.getItems().stream().filter(entry -> normalizeRemotePath(entry.path()).equals(target))
                .findFirst().ifPresent(entry -> {
                    session.pendingRemoteSelectionPath = null;
                    table.getSelectionModel().clearSelection();
                    table.getSelectionModel().select(entry);
                    table.scrollTo(entry);
                });
    }

    static boolean consumeInitialRemotePathFallback(OpenSession session, String failedPath) {
        if (!session.initialRemotePathFallbackPending
                || !Objects.equals(session.currentRemotePath, failedPath)) return false;
        session.initialRemotePathFallbackPending = false;
        session.currentRemotePath = ".";
        return true;
    }

    static String remoteDirectoryFailureMessage(Throwable error) {
        return sshSftpFailureMessage(error,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.004d60d5f9c9c72b"));
    }

    static String sshSftpFailureMessage(Throwable error, String fallback) {
        String detail = null;
        Throwable current = unwrap(error);
        for (int depth = 0; current != null && depth < 5; depth++, current = current.getCause()) {
            String message = sanitizeSshSftpFailureDetail(current.getMessage());
            if (isMeaningfulSshSftpFailureDetail(message)) {
                detail = message;
            } else if (detail == null && isSpecificFailureType(current.getClass())) {
                detail = current.getClass().getSimpleName();
            }
        }
        return detail == null ? Objects.requireNonNull(fallback, "fallback") : detail;
    }

    private static String sanitizeSshSftpFailureDetail(String message) {
        if (message == null) return "";
        String safe = message.replaceAll("[\\r\\n\\t\\u0000-\\u001f]+", " ")
                .replaceAll("\\s{2,}", " ").strip();
        String lowered = safe.toLowerCase(Locale.ROOT);
        if (lowered.contains("password") || lowered.contains("passphrase")
                || lowered.contains("private key") || safe.contains("\u5bc6\u7801")
                || safe.contains("\u53e3\u4ee4") || safe.contains("\u79c1\u94a5")) {
            return "";
        }
        return safe.length() > 500 ? safe.substring(0, 500) + "…" : safe;
    }

    private static boolean isMeaningfulSshSftpFailureDetail(String detail) {
        if (detail.isBlank()) return false;
        return !switch (detail.toLowerCase(Locale.ROOT)) {
            case "unknown", "unknown error", "error", "failure", "failed" -> true;
            default -> false;
        };
    }

    private static boolean isSpecificFailureType(Class<?> type) {
        return type != Throwable.class && type != Exception.class && type != RuntimeException.class
                && type != IOException.class && type != CompletionException.class
                && type != ExecutionException.class;
    }

    private void uploadSelected(OpenSession session, String remoteDirectory) {
        requireLicense(com.ynpe.fastremote.domain.license.LicenseCapability.TRANSFER,
                () -> uploadAuthorizedSelected(session, remoteDirectory), null);
    }

    private void uploadAuthorizedSelected(OpenSession session, String remoteDirectory) {
        if (session.localTable == null || session.transferQueue == null) return;
        List<LocalFileEntry> selected = transferableLocalEntries(
                List.copyOf(session.localTable.getSelectionModel().getSelectedItems()));
        if (selected.isEmpty()) return;
        session.transferQueue.upload(selected, remoteDirectory);
        appendFileLog(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.4f90bef493b3f25e") + selected.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.4c54e5cb422f358e") + remoteDirectory);
    }

    private void uploadEntries(OpenSession session, List<LocalFileEntry> entries, String remoteDirectory) {
        requireLicense(com.ynpe.fastremote.domain.license.LicenseCapability.TRANSFER,
                () -> uploadAuthorizedEntries(session, entries, remoteDirectory), null);
    }

    private void uploadAuthorizedEntries(OpenSession session, List<LocalFileEntry> entries, String remoteDirectory) {
        List<LocalFileEntry> transferable = transferableLocalEntries(entries);
        if (transferable.isEmpty() || session.transferQueue == null) return;
        session.transferQueue.upload(transferable, remoteDirectory);
        appendFileLog(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.506273db2012eae0") + transferable.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.4c54e5cb422f358e") + remoteDirectory);
    }

    private void downloadSelected(OpenSession session, String localDirectory) {
        requireLicense(com.ynpe.fastremote.domain.license.LicenseCapability.TRANSFER,
                () -> downloadAuthorizedSelected(session, localDirectory), null);
    }

    private void downloadAuthorizedSelected(OpenSession session, String localDirectory) {
        if (session.remoteTable == null || session.transferQueue == null) return;
        List<RemoteFileEntry> selected = transferableRemoteEntries(
                List.copyOf(session.remoteTable.getSelectionModel().getSelectedItems()));
        if (selected.isEmpty()) return;
        try {
            session.transferQueue.download(selected, Path.of(localDirectory));
            appendFileLog(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.da4987ea444ce358") + selected.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.4c54e5cb422f358e") + localDirectory);
        } catch (java.nio.file.InvalidPathException invalid) {
            appendFileLog(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.8ce92366337a4c5e") + localDirectory, StatusTone.ERROR);
        }
    }

    private void downloadEntries(OpenSession session, List<RemoteFileEntry> entries, Path localDirectory) {
        requireLicense(com.ynpe.fastremote.domain.license.LicenseCapability.TRANSFER,
                () -> downloadAuthorizedEntries(session, entries, localDirectory), null);
    }

    private void downloadAuthorizedEntries(OpenSession session, List<RemoteFileEntry> entries, Path localDirectory) {
        List<RemoteFileEntry> transferable = transferableRemoteEntries(entries);
        if (transferable.isEmpty() || session.transferQueue == null) return;
        session.transferQueue.download(transferable, localDirectory);
        appendFileLog(session, com.ynpe.fastremote.i18n.Texts.text("legacy.java.b73cc8e95cdbcb29") + transferable.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.4c54e5cb422f358e") + localDirectory);
    }

    private void requireLicense(com.ynpe.fastremote.domain.license.LicenseCapability capability, Runnable authorized,
            CompletableFuture<?> automationResult) {
        setStatus(localizer.text("license.working"), StatusTone.WORKING);
        licenseAccess.permits(capability).whenComplete((permitted, error) -> Platform.runLater(() -> {
            if (controllerClosed.get()) return;
            if (error != null || !Boolean.TRUE.equals(permitted)) {
                IllegalStateException denied = new IllegalStateException(
                        localizer.text("license.actionRequired", capability.wireName()));
                setStatus(denied.getMessage(), StatusTone.ERROR);
                if (automationResult != null) automationResult.completeExceptionally(denied);
                showLicenseRequiredPrompt(capability);
                return;
            }
            authorized.run();
        }));
    }

    @FXML
    private void openLicenseActivation() {
        openLicenseActivation.run();
    }

    @FXML
    private void activateFromStatusKey(KeyEvent event) {
        if (event.getCode() != KeyCode.ENTER && event.getCode() != KeyCode.SPACE) return;
        event.consume();
        openLicenseActivation();
    }

    public void refreshLicenseControls() {
        licenseStatus.get().whenComplete((snapshot, error) -> Platform.runLater(() -> {
            if (controllerClosed.get()) return;
            applyLicenseControlState(licenseControlState(snapshot, error));
        }));
    }

    public void updateLicenseControls(com.ynpe.fastremote.domain.license.LicenseSnapshot snapshot) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> updateLicenseControls(snapshot));
            return;
        }
        if (!controllerClosed.get()) applyLicenseControlState(licenseControlState(snapshot, null));
    }

    private void applyLicenseControlState(LicenseControlState state) {
        boolean statusVisible = state.activationStatus() != LicenseActivationStatus.HIDDEN;
        toolbarActivationStatusLabel.setManaged(statusVisible);
        toolbarActivationStatusLabel.setVisible(statusVisible);
        toolbarActivationStatusLabel.getStyleClass().removeAll(
                "license-activation-inactive", "license-activation-trial");
        if (state.activationStatus() == LicenseActivationStatus.INACTIVE) {
            toolbarActivationStatusLabel.setText(localizer.text("license.toolbar.notActivated"));
            toolbarActivationStatusLabel.getStyleClass().add("license-activation-inactive");
        } else if (state.activationStatus() == LicenseActivationStatus.TRIAL) {
            toolbarActivationStatusLabel.setText(localizer.text("license.toolbar.trial"));
            toolbarActivationStatusLabel.getStyleClass().add("license-activation-trial");
        }
        activateLicenseMenuItem.setDisable(state.activateDisabled());
        deactivateLicenseMenuItem.setDisable(state.deactivateDisabled());
    }

    static LicenseControlState licenseControlState(
            com.ynpe.fastremote.domain.license.LicenseSnapshot snapshot, Throwable error) {
        boolean authorized = error == null && snapshot != null
                && LicenseSettingsPage.canDeactivateLicense(snapshot);
        return new LicenseControlState(licenseActivationStatus(snapshot, error), authorized, !authorized);
    }

    static LicenseActivationStatus licenseActivationStatus(
            com.ynpe.fastremote.domain.license.LicenseSnapshot snapshot, Throwable error) {
        if (error != null || snapshot == null) return LicenseActivationStatus.INACTIVE;
        return switch (snapshot.status()) {
            case TRIAL_ACTIVE -> LicenseActivationStatus.TRIAL;
            case LICENSED_ONLINE, LICENSED_OFFLINE, OFFLINE_GRACE -> LicenseActivationStatus.HIDDEN;
            default -> LicenseActivationStatus.INACTIVE;
        };
    }

    static Icon vaultToolbarIcon(VaultStatus status) {
        return status == VaultStatus.UNLOCKED ? Icon.KEY_ROUND : Icon.LOCK_KEYHOLE;
    }

    enum LicenseActivationStatus { INACTIVE, TRIAL, HIDDEN }

    record LicenseControlState(LicenseActivationStatus activationStatus, boolean activateDisabled,
                               boolean deactivateDisabled) { }

    @FXML
    private void deactivateCurrentLicense() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                localizer.text("license.deactivate.confirm"), ButtonType.CANCEL, ButtonType.OK);
        if (mainWindow != null) confirmation.initOwner(mainWindow);
        confirmation.setTitle(localizer.text("license.title"));
        confirmation.setHeaderText(localizer.text("license.deactivate"));
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) return;
        setStatus(localizer.text("license.working"), StatusTone.WORKING);
        deactivateLicense.get().whenComplete((snapshot, error) -> Platform.runLater(() -> {
            if (error != null) {
                setStatus(localizer.text("license.error", messageFor(unwrap(error), "DEACTIVATION_FAILED")), StatusTone.ERROR);
                return;
            }
            updateLicenseControls(snapshot);
            setStatus(localizer.text("license.deactivate.done"), StatusTone.IDLE);
        }));
    }

    private void showLicenseRequiredPrompt(com.ynpe.fastremote.domain.license.LicenseCapability capability) {
        if (licensePromptVisible || controllerClosed.get()) return;
        licensePromptVisible = true;
        ButtonType activate = new ButtonType(localizer.text("license.activateNow"),
                ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(localizer.text("license.later"),
                ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.WARNING, localizer.text("license.featureUnavailable.body"),
                activate, cancel);
        DialogSpacing.configureInitialWindowWidth(alert, 520.0, 440.0);
        if (mainWindow != null) alert.initOwner(mainWindow);
        alert.setTitle(localizer.text("license.title"));
        alert.setHeaderText(localizer.text("license.featureUnavailable.header", capability.wireName()));
        alert.setOnHidden(event -> {
            licensePromptVisible = false;
            if (alert.getResult() == activate) openLicenseActivation.run();
        });
        alert.show();
    }

    private void logConnection(OpenSession session, String message, StatusTone tone) {
        StatusModule module = statusModule(session);
        if (module == StatusModule.SFTP) appendFileLog(session, message, tone);
        setSessionStatus(module, session, message, tone);
    }

    private void appendFileLog(OpenSession session, String message) {
        appendFileLog(session, message, StatusTone.IDLE);
    }

    private void appendFileLog(OpenSession session, String message, StatusTone tone) {
        session.fileLog.append(operationLogLine(message));
        appendDiagnosticLog("[SFTP][" + connectionLogName(session.name, session.profile) + "] " + message,
                tone);
    }

    private void appendFileActivity(OpenSession session, TransferActivity activity) {
        if (session.closed.get()) return;
        appendFileLog(session, fileActivityMessage(activity));
    }

    private static String fileActivityMessage(TransferActivity activity) {
        String operation = switch (activity.operation()) {
            case UPLOAD -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.9e07e3c0532d4976");
            case DOWNLOAD -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.4673a23061656125");
            case DELETE -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.2f9daa828907b93f");
        };
        String type = switch (activity.objectType()) {
            case FILE -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.39932f24fe11a6ba");
            case DIRECTORY -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.52daa71ebc310581");
            case SYMBOLIC_LINK -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.35d313a2d6423b1d");
            case OTHER -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.39932f24fe11a6ba");
        };
        String route = activity.operation() == TransferActivity.Operation.DELETE
                ? activity.source() : activity.source() + " → " + activity.target();
        return StatusMessagePresentation.diagnostic(operation + ' ' + type + ": " + route);
    }

    private static String operationLogLine(String message) {
        return '[' + timestamp() + "] " + message + System.lineSeparator();
    }

    private static String timestamp() {
        return com.ynpe.fastremote.i18n.Texts.localizer().formatDateTime(LocalDateTime.now());
    }

    private static String humanSize(long size) {
        return com.ynpe.fastremote.i18n.Texts.localizer().formatBytes(size);
    }

    private void closeSession(Tab tab, String message) {
        OpenSession session = openSessions.get(tab);
        if (session == null) return;
        for (Tab related : List.copyOf(session.tabs.values())) {
            related.setOnCloseRequest(null);
            openSessions.remove(related);
            workspacePanes.removePane(related);
        }
        session.tabs.clear();
        session.closed.set(true);
        if (textEditors != null && session.sftp != null) textEditors.cleanupRemoteTemporaryFiles(remoteFiles(session));
        if (session.attempt != null) session.attempt.cancel();
        if (session.pendingCredential != null) session.pendingCredential.close();
        if (session.temporaryCredential != null) session.temporaryCredential.close();
        session.fileLog.detach(session.fileLogView);
        session.fileLogView = null;
        session.fileLog.close();
        if (session.transferQueue != null) session.transferQueue.close();
        if (session.terminal != null) session.terminal.close();
        if (session.sftp != null) session.sftp.close();
        releaseRetainedConnections(session);
        updateWorkspaceVisibility();
        setSessionStatus(statusModule(session), session, message, StatusTone.IDLE);
        updateActions();
    }

    private void closeWorkspaceTab(Tab tab, String message) {
        OpenSession session = openSessions.remove(tab);
        if (session == null) return;
        WorkspaceType closedType = session.tabs.entrySet().stream()
                .filter(entry -> entry.getValue() == tab)
                .map(Map.Entry::getKey).findFirst().orElse(null);
        session.tabs.values().removeIf(tab::equals);
        if (closedType == WorkspaceType.SFTP) {
            if (textEditors != null && session.sftp != null) textEditors.cleanupRemoteTemporaryFiles(remoteFiles(session));
            if (session.transferQueue != null) {
                session.transferQueue.close();
                session.transferQueue = null;
            }
            if (session.sftp != null) {
                session.sftp.close();
                session.sftp = null;
            }
            session.fileLog.detach(session.fileLogView);
            session.fileLogView = null;
            session.localDirectoryRequest++;
            session.remoteDirectoryRequest++;
            session.localTree = null;
            session.remoteTree = null;
            session.localTable = null;
            session.remoteTable = null;
            session.localPathField = null;
            session.remotePathField = null;
            session.localStatisticsLabel = null;
            session.remoteStatisticsLabel = null;
        }
        tab.setOnCloseRequest(null);
        workspacePanes.removePane(tab);
        tab.getProperties().remove(AUTOMATION_OWNED_TAB_KEY);
        if (session.tabs.isEmpty()) {
            session.closed.set(true);
            session.fileLog.close();
            if (session.cancelAttempt != null) session.cancelAttempt.run();
            if (session.pendingCredential != null) session.pendingCredential.close();
            if (session.temporaryCredential != null) session.temporaryCredential.close();
            if (session.terminal != null) session.terminal.close();
            if (session.sftp != null) session.sftp.close();
            releaseRetainedConnections(session);
        }
        updateWorkspaceVisibility();
        setSessionStatus(statusModule(session), session, message, StatusTone.IDLE);
        updateActions();
    }

    private void refreshWorkspaceTabTitles() {
        for (Map.Entry<Tab, OpenSession> entry : openSessions.entrySet()) {
            WorkspaceType type = entry.getValue().tabs.entrySet().stream()
                    .filter(value -> value.getValue() == entry.getKey())
                    .map(Map.Entry::getKey).findFirst().orElse(WorkspaceType.TERMINAL);
            updateWorkspaceTabGraphic(entry.getKey(), entry.getValue(), type);
        }
    }

    private void refreshSftpWorkspaceLayouts() {
        for (OpenSession session : new HashSet<>(openSessions.values())) {
            Tab sftp = session.tabs.get(WorkspaceType.SFTP);
            if (sftp != null && session.state == ConnectionState.CONNECTED
                    && (session.sftp != null || session.terminal != null)) {
                sftp.setContent(createWorkspaceShell(
                        session, WorkspaceType.SFTP, createSftpWorkspace(session)));
                workspacePanes.updatePaneContent(sftp, sftp.getContent());
            }
        }
    }

    private void updateWorkspaceTabGraphic(Tab tab, OpenSession session, WorkspaceType type) {
        Region state = new Region();
        state.setMinSize(8.0, 8.0);
        state.setPrefSize(8.0, 8.0);
        state.setMaxSize(8.0, 8.0);
        state.getStyleClass().addAll("workspace-tab-status", workspaceStatusStyle(session.state));
        String originalTitle = workspaceTabTitle(
                session.name, session.profile, persistedWorkspaceSettings.hostTabTitleParts(), type);
        String displayedTitle = automationWorkspaceTabTitle(originalTitle, session.automationOwned);
        Node graphic = type == WorkspaceType.TERMINAL
                ? WorkspaceTabGraphics.remoteTerminal(state)
                : WorkspaceTabGraphics.remoteFiles(state);
        configureWorkspaceTabHeader(tab, displayedTitle, graphic);
        if (workspacePanes != null) workspacePanes.refreshPaneHeader(tab);
    }

    static void configureWorkspaceTabHeader(Tab tab, String title, Node graphic) {
        // The normal header and JavaFX overflow menu both read Tab.text.
        tab.setText(title);
        tab.setGraphic(graphic);
    }

    static int insertionIndexAfter(List<Tab> tabs, Tab anchor) {
        int anchorIndex = anchor == null ? -1 : tabs.indexOf(anchor);
        return anchorIndex < 0 ? tabs.size() : anchorIndex + 1;
    }

    private static boolean isAutomationOwnedTab(Tab tab) {
        return tab != null && Boolean.TRUE.equals(tab.getProperties().get(AUTOMATION_OWNED_TAB_KEY));
    }

    static String automationWorkspaceTabTitle(String originalTitle, boolean automationOwned) {
        return automationOwned ? "AI-> " + originalTitle : originalTitle;
    }

    static boolean shouldActivateAutomationWorkspace(boolean automationOwned, boolean activateTab) {
        return !automationOwned || activateTab;
    }

    static LucideIconView workspaceReconnectIcon() {
        LucideIconView icon = new LucideIconView(Icon.PLUG_ZAP);
        icon.getStyleClass().add("workspace-action-icon");
        return icon;
    }

    private void updateAuthenticationFields() {
        boolean privateKey = authenticationField.getValue() == AuthenticationType.PRIVATE_KEY;
        boolean fromLibrary = manualQuickFieldsDisabled(credentialSourceField.getValue());
        usernameField.setDisable(fromLibrary);
        authenticationField.setDisable(fromLibrary);
        privateKeyField.setDisable(!privateKey || fromLibrary);
        chooseKeyButton.setDisable(!privateKey || fromLibrary);
        hostPasswordField.setDisable(privateKey || fromLibrary);
        privateKeyPassphraseField.setDisable(!privateKey || fromLibrary);
    }

    private void updateQuickCredentialSource() {
        boolean fromLibrary = manualQuickFieldsDisabled(credentialSourceField.getValue());
        credentialProfileField.setDisable(!fromLibrary);
        if (fromLibrary && quickCredentialSelector.selectedCredential().isEmpty()
                && !quickCredentialSelector.isEmpty()) {
            quickCredentialSelector.selectFirst();
        }
        if (fromLibrary) applyCredentialChoice(quickCredentialSelector.selectedCredential().orElse(null));
        else updateAuthenticationFields();
    }

    private GlobalCredential selectedQuickCredential() {
        return credentialSourceField.getValue() == QuickCredentialSource.LIBRARY
                ? quickCredentialSelector.selectedCredential().orElse(null) : null;
    }

    private void rebuildCredentialChoices(List<GlobalCredential> credentials) {
        rebuildCredentialTree();
        Optional<SecretId> selected = quickCredentialSelector.selectedCredential().map(GlobalCredential::id);
        quickCredentialSelector.rebuild(credentials, catalog.credentialGroups(), selected);
        if (quickCredentialSelector.selectedCredential().isEmpty() && !quickCredentialSelector.isEmpty()) {
            quickCredentialSelector.selectFirst();
        }
        updateQuickCredentialSource();
    }

    private void rebuildQuickJumpChoices(List<SavedHost> hosts, List<HostGroup> groups) {
        Optional<com.ynpe.fastremote.domain.catalog.HostId> selected = quickJumpHostId();
        quickJumpSelector.rebuild(hosts, groups, selected, Optional.empty());
    }

    private Optional<com.ynpe.fastremote.domain.catalog.HostId> quickJumpHostId() {
        return quickJumpSelector == null ? Optional.empty() : quickJumpSelector.selectedHostId();
    }

    private void applyCredentialChoice(GlobalCredential credential) {
        if (credentialSourceField.getValue() == QuickCredentialSource.LIBRARY
                && credential != null) {
            usernameField.setText(credential.username());
            authenticationField.setValue(credential.authenticationType());
            privateKeyField.clear();
            hostPasswordField.clear();
            privateKeyPassphraseField.clear();
        }
        updateAuthenticationFields();
    }

    private void updateActions() {
        OpenSession selected = openSessions.get(selectedWorkspaceTab());
        boolean selectedConnecting = selected != null && selected.state == ConnectionState.CONNECTING;
        boolean disconnectDisabled = selected == null;
        connectButton.setDisable(false);
        reconnectMenuItem.setDisable(selected == null || selectedConnecting);
        disconnectButton.setDisable(disconnectDisabled);
        disconnectMenuItem.setDisable(disconnectDisabled);
        disconnectButton.setText(selectedConnecting ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.2cd0f3be8738a86c") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.f33ac04eece6a6a6"));
        disconnectMenuItem.setText(selectedConnecting ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.3687f953cfe85fb8") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.caa56a75825e335e"));
        disconnectButton.setOnAction(selectedConnecting ? event -> cancelConnection() : event -> disconnect());
        boolean anyConnecting = new HashSet<>(openSessions.values()).stream()
                .anyMatch(session -> session.state == ConnectionState.CONNECTING);
        progressIndicator.setVisible(anyConnecting);
        progressIndicator.setManaged(anyConnecting);
        refreshWorkspaceQuickTools();
    }

    private void setStatus(String message, StatusTone tone) {
        setStatusMessage(statusMessage(StatusModule.GLOBAL, "", message), tone);
    }

    /** Reports a startup data backup in both the status bar and the diagnostic log. */
    public void showStartupDataBackupNotice(String message) {
        setStatus(message, StatusTone.ERROR);
    }

    private void setSessionStatus(StatusModule module, OpenSession session, String message, StatusTone tone) {
        setStatusMessage(statusMessage(module, connectionLogName(session.name, session.profile), message), tone);
    }

    private void setStatusMessage(String message, StatusTone tone) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(tone, "tone");
        primaryStatusLabel.setText(StatusMessagePresentation.summary(message));
        statusIndicator.getStyleClass().removeAll(
                "status-dot-idle", "status-dot-working", "status-dot-connected", "status-dot-error");
        statusIndicator.getStyleClass().add(tone.styleClass);
        appendDiagnosticLog(message, tone);
    }

    private static StatusModule statusModule(OpenSession session) {
        return session.initialType == WorkspaceType.SFTP ? StatusModule.SFTP : StatusModule.TERMINAL;
    }

    static String statusMessage(StatusModule module, String connection, String message) {
        Objects.requireNonNull(module, "module");
        String context = connection == null || connection.isBlank() ? "" : '[' + connection.strip() + ']';
        return '[' + module.label() + ']' + context + ' ' + Objects.requireNonNull(message, "message");
    }

    private void appendDiagnosticLog(String message, StatusTone tone) {
        LocalDateTime timestamp = LocalDateTime.now();
        String sanitized = StatusMessagePresentation.diagnostic(message);
        synchronized (diagnosticLogLock) {
            if (sanitized.equals(previousDiagnosticMessage) && tone.styleClass.equals(previousDiagnosticTone)) {
                return;
            }
            previousDiagnosticMessage = sanitized;
            previousDiagnosticTone = tone.styleClass;
            diagnosticLogOutput.append(timestamp, sanitized);
            String rendered = '[' + com.ynpe.fastremote.i18n.Texts.localizer()
                    .formatTime(timestamp.toLocalTime()) + "]  " + sanitized;
            diagnosticTextLog.append(rendered + System.lineSeparator());
        }
        scheduleDiagnosticLogPathUpdate();
    }

    private void scheduleDiagnosticLogPathUpdate() {
        if (!diagnosticPathUpdatePending.compareAndSet(false, true)) return;
        Platform.runLater(() -> {
            try {
                if (!controllerClosed.get()) updateDiagnosticLogPath();
            } finally {
                diagnosticPathUpdatePending.set(false);
            }
        });
    }

    private void appendAutomationAudit(com.ynpe.fastremote.domain.automation.AutomationAuditEvent event) {
        String source = event.source() == com.ynpe.fastremote.domain.automation.AutomationSource.BUILT_IN_AI
                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.bdb3b266ba8a5a67") : "MCP";
        String phase = switch (event.phase()) {
            case REQUESTED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.393e1241552b1870");
            case APPROVAL_REQUIRED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.79209170acff4896");
            case APPROVED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.521e65ffc7d0ff2d");
            case DENIED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.f098218a3032a625");
            case STARTED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.d2bb025a2e51c410");
            case SUCCEEDED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.c0b3fbff51ccc40b");
            case FAILED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.28384d7afd2e4fa6");
            case CANCELED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.a37778f17c5f3ee5");
        };
        String elapsed = event.duration().map(value -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.ceecbbdea64f78ea") + automationDuration(value)).orElse("");
        String detail = event.detail().isBlank() ? "" : com.ynpe.fastremote.i18n.Texts.text("legacy.java.271998524281a4e7") + event.detail();
        appendDiagnosticLog(com.ynpe.fastremote.i18n.Texts.text("legacy.java.051f233a5a92109c") + source + "][" + phase + "] " + event.action()
                + com.ynpe.fastremote.i18n.Texts.text("legacy.java.632a3965d77000b3") + event.target() + detail + elapsed,
                switch (event.phase()) {
                    case DENIED, FAILED -> StatusTone.ERROR;
                    case REQUESTED, APPROVAL_REQUIRED, STARTED -> StatusTone.WORKING;
                    default -> StatusTone.CONNECTED;
                });
    }

    private static String automationDuration(java.time.Duration duration) {
        return com.ynpe.fastremote.i18n.Texts.localizer().formatDuration(duration);
    }

    private void sampleTraffic() {
        long sampledAt = System.nanoTime();
        long previousReceived = totalReceivedBytes;
        long previousSent = totalSentBytes;
        java.util.Set<OpenSession> sessions = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        sessions.addAll(openSessions.values());
        for (OpenSession session : sessions) {
            long received = 0L;
            long sent = 0L;
            if (session.terminal != null) {
                received += session.terminal.receivedBytes();
                sent += session.terminal.sentBytes();
            }
            if (session.transferQueue != null) {
                TrafficSample transfer = sftpTraffic(session.transferQueue);
                received += transfer.received();
                sent += transfer.sent();
            }
            appendTrafficDelta(connectionLogName(session.name, session.profile), session, received, sent);
        }
        // Local PTY counters measure text I/O, not network traffic.
        AiAssistantWorkflow.TrafficSnapshot aiTraffic = aiAssistantWorkflow.trafficSnapshot();
        appendTrafficDelta(com.ynpe.fastremote.i18n.Texts.text("traffic.aiMcp"), aiAssistantWorkflow,
                aiTraffic.receivedBytes(), aiTraffic.sentBytes());
        trafficSamples.keySet().removeIf(key -> !(key instanceof OpenSession && sessions.contains(key))
                && key != aiAssistantWorkflow);
        long elapsedNanos = Math.max(1L, sampledAt - previousTrafficSampleNanos);
        trafficStatisticsLabel.setText(trafficStatusText(
                totalReceivedBytes, totalSentBytes,
                trafficRate(totalReceivedBytes - previousReceived, elapsedNanos),
                trafficRate(totalSentBytes - previousSent, elapsedNanos)));
        previousTrafficSampleNanos = sampledAt;
    }

    private void appendTrafficDelta(String context, Object key, long received, long sent) {
        TrafficSample previous = trafficSamples.put(key, new TrafficSample(received, sent));
        if (previous == null) previous = new TrafficSample(0L, 0L);
        long receivedDelta = Math.max(0L, received - previous.received());
        long sentDelta = Math.max(0L, sent - previous.sent());
        if (receivedDelta == 0L && sentDelta == 0L) return;
        totalReceivedBytes += receivedDelta;
        totalSentBytes += sentDelta;
        appendDiagnosticLog(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b3f067d046ccfa63") + context + com.ynpe.fastremote.i18n.Texts.text("legacy.java.7e31d34cc106a1db") + byteCount(receivedDelta)
                + com.ynpe.fastremote.i18n.Texts.text("legacy.java.d85aa5e7f9851a90") + byteCount(received) + com.ynpe.fastremote.i18n.Texts.text("legacy.java.7a25e5a07a17a2e5") + byteCount(sentDelta)
                + com.ynpe.fastremote.i18n.Texts.text("legacy.java.d85aa5e7f9851a90") + byteCount(sent) + "）", StatusTone.IDLE);
    }

    private static String byteCount(long bytes) {
        return com.ynpe.fastremote.i18n.Texts.localizer().formatBytes(bytes);
    }

    static long trafficRate(long bytes, long elapsedNanos) {
        if (bytes <= 0L || elapsedNanos <= 0L) return 0L;
        return Math.max(0L, Math.round(bytes / (elapsedNanos / 1_000_000_000.0)));
    }

    static String trafficStatusText(long received, long sent, long receivedPerSecond, long sentPerSecond) {
        return com.ynpe.fastremote.i18n.Texts.text("status.traffic",
                byteCount(received) + " (" + byteCount(receivedPerSecond) + "/s)",
                byteCount(sent) + " (" + byteCount(sentPerSecond) + "/s)");
    }

    private static TrafficSample sftpTraffic(SftpTransferQueue queue) {
        long received = 0L;
        long sent = 0L;
        for (SftpTransferJob job : queue.jobs()) {
            if (job.direction() == SftpTransferJob.Direction.DOWNLOAD) {
                received += job.transferredBytesProperty().get();
            } else {
                sent += job.transferredBytesProperty().get();
            }
        }
        return new TrafficSample(received, sent);
    }

    private void refreshWorkspaceQuickTools() {
        if (workspaceQuickTools == null) return;
        workspaceQuickTools.getChildren().clear();
        Tab selected = selectedWorkspaceTab();
        if (selected == null) {
            setManagedVisible(workspaceQuickTools, false);
            return;
        }
        LocalTerminalHandle local = localTerminals.get(selected);
        if (local != null) {
            Button files = workspaceIconButton(
                    Icon.FOLDER,
                    localizer.text("localFiles.openPaired"),
                    ignored -> openPairedLocalFiles(selected, local));
            Button goTo = favoritePathButton(
                    () -> persistedWorkspaceSettings.sftpSettings().globalBookmarks().localPaths(),
                    path -> sendLocalTerminalDirectory(local, path));
            Button duplicate = workspaceIconButton(
                    Icon.COPY,
                    com.ynpe.fastremote.i18n.Texts.text("workspace.localTerminal.duplicate"),
                    action -> duplicateLocalTerminal(selected, local, action));
            Button create = workspaceIconButton(
                    Icon.PLUS,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.a9f4ff897bec7384"),
                    ignored -> openLocalTerminal());
            workspaceQuickTools.getChildren().addAll(goTo, duplicate, create, files);
        } else if (localFileWorkspaces.containsKey(selected)) {
            LocalWorkspaceSession localWorkspace = localWorkspaceSessions.get(selected);
            LocalFileWorkspaceController controller = localFileWorkspaces.get(selected);
            Button goTo = favoritePathButton(
                    () -> persistedWorkspaceSettings.sftpSettings().globalBookmarks().localPaths(),
                    path -> {
                        try { controller.navigateTo(Path.of(path)); }
                        catch (java.nio.file.InvalidPathException invalid) {
                            setStatus(localizer.text("localFiles.invalidPath"), StatusTone.ERROR);
                        }
                    });
            Button duplicate = workspaceIconButton(Icon.COPY,
                    com.ynpe.fastremote.i18n.Texts.text("terminal.context.duplicate"), ignored -> {
                        Path directory = Optional.ofNullable(controller.currentDirectory())
                                .orElseGet(() -> Path.of(System.getProperty("user.home", ".")));
                        createLocalFileWorkspace(new LocalWorkspaceSession(directory), directory, null);
                    });
            Button create = workspaceIconButton(Icon.PLUS,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.a9f4ff897bec7384"),
                    ignored -> openLocalFiles());
            Button terminal = workspaceIconButton(Icon.SQUARE_TERMINAL,
                    localizer.text("localFiles.terminal"), ignored -> openPairedLocalTerminal(
                            localWorkspace, controller.currentDirectory()));
            workspaceQuickTools.getChildren().addAll(goTo, duplicate, create, terminal);
        } else if (openSessions.containsKey(selected)
                && selected.getContent() instanceof VBox shell
                && shell.getProperties().get(WORKSPACE_STATUS_TOOLS_KEY) instanceof List<?> tools) {
            workspaceQuickTools.getChildren().addAll(tools.stream()
                    .filter(Node.class::isInstance).map(Node.class::cast).toList());
        } else {
            Label title = new Label();
            title.textProperty().bind(selected.textProperty());
            title.setMinWidth(0.0);
            title.setMaxWidth(220.0);
            title.getStyleClass().add("workspace-status-tab-title");
            workspaceQuickTools.getChildren().add(title);
        }
        setManagedVisible(workspaceQuickTools, true);
    }

    private void duplicateLocalTerminal(Tab sourceTab, LocalTerminalHandle source, Button action) {
        if (action != null) action.setDisable(true);
        source.resolveWorkingDirectory().whenComplete((resolved, error) -> Platform.runLater(() -> {
            if (localTerminals.get(sourceTab) != source) return;
            if (action != null) action.setDisable(false);
            Path directory = resolved.orElseGet(() ->
                    Path.of(System.getProperty("user.home")).toAbsolutePath().normalize());
            openLocalTerminal(false, directory, null);
        }));
    }

    private List<TerminalContextMenuAction> localTerminalContextMenuItems(
            Tab sourceTab, LocalTerminalHandle source) {
        TerminalContextMenuAction files = TerminalContextMenuAction.item(
                localizer.text("localFiles.openPaired"), () -> openPairedLocalFiles(sourceTab, source));
        TerminalContextMenuAction.Submenu goTo = terminalFavoriteMenu(
                () -> persistedWorkspaceSettings.sftpSettings().globalBookmarks().localPaths(),
                path -> sendLocalTerminalDirectory(source, path));
        TerminalContextMenuAction duplicate = terminalContextAction("terminal.context.duplicate",
                () -> duplicateLocalTerminal(sourceTab, source, null));
        TerminalContextMenuAction create = terminalContextAction(
                "terminal.context.new", this::openLocalTerminal);
        TerminalContextMenuAction reveal = TerminalContextMenuAction.item(
                PlatformFileManagerLabel.reveal(), () -> revealLocalTerminalDirectory(sourceTab, source));
        return List.of(files, goTo, duplicate, create, reveal);
    }

    private List<TerminalContextMenuAction> remoteTerminalContextMenuItems(OpenSession session) {
        TerminalContextMenuAction sftp = TerminalContextMenuAction.item(
                workspaceCounterpartLabel(WorkspaceType.TERMINAL),
                () -> openPairedWorkspace(session, WorkspaceType.SFTP),
                () -> session.state == ConnectionState.CONNECTED);
        TerminalContextMenuAction.Submenu goTo = terminalFavoriteMenu(
                () -> mergedFavoritePaths(
                        persistedWorkspaceSettings.sftpSettings().globalBookmarks().remotePaths(),
                        persistedWorkspaceSettings.sftpSettings()
                                .bookmarksFor(session.bookmarkKey).remotePaths()),
                path -> sendRemoteTerminalDirectory(session, path),
                () -> session.state == ConnectionState.CONNECTED);
        TerminalContextMenuAction duplicate = terminalContextAction(
                "terminal.context.duplicate", () -> duplicateRemoteTerminal(session));
        TerminalContextMenuAction create = terminalContextAction(
                "terminal.context.new",
                () -> openIndependentWorkspace(session, WorkspaceType.TERMINAL));
        return List.of(sftp, goTo, duplicate, create);
    }

    private TerminalContextMenuAction.Submenu terminalFavoriteMenu(
            Supplier<List<String>> paths, Consumer<String> action) {
        return terminalFavoriteMenu(paths, action, () -> true);
    }

    private TerminalContextMenuAction.Submenu terminalFavoriteMenu(
            Supplier<List<String>> paths, Consumer<String> action,
            java.util.function.BooleanSupplier enabled) {
        List<String> available = paths.get();
        List<TerminalContextMenuAction> children;
        if (available.isEmpty()) {
            children = List.of(TerminalContextMenuAction.item(
                    localizer.text("workspace.goTo.empty"), () -> {}, () -> false));
        } else {
            children = available.stream()
                    .<TerminalContextMenuAction>map(path ->
                            TerminalContextMenuAction.item(path, () -> action.accept(path)))
                    .toList();
        }
        return TerminalContextMenuAction.submenu(
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.d69b5ab453f7d2d6"),
                children, enabled);
    }

    private TerminalContextMenuAction terminalContextAction(String textKey, Runnable action) {
        return TerminalContextMenuAction.item(localizer.text(textKey), action);
    }

    private void duplicateRemoteTerminal(OpenSession session) {
        openIndependentWorkspace(session, WorkspaceType.TERMINAL,
                remoteDuplicateInitialPath(session));
    }

    static String remoteDuplicateInitialPath(OpenSession session) {
        return Optional.ofNullable(session.latestInferredWorkingDirectory)
                .filter(path -> !path.isBlank())
                .orElse(null);
    }

    private void revealLocalTerminalDirectory(Tab sourceTab, LocalTerminalHandle source) {
        source.resolveWorkingDirectory().whenComplete((resolved, error) -> Platform.runLater(() -> {
            if (localTerminals.get(sourceTab) != source || resolved.isEmpty()) return;
            workflow.revealLocal(resolved.orElseThrow(), false)
                    .whenComplete((ignored, revealError) -> Platform.runLater(() -> {
                        if (revealError != null) {
                            setStatus(com.ynpe.fastremote.i18n.Texts.text(
                                    "legacy.java.9924447f0b807036"), StatusTone.ERROR);
                        }
                    }));
        }));
    }

    static Button workspaceIconButton(Icon iconType, String label, Consumer<Button> action) {
        Objects.requireNonNull(iconType, "iconType");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(action, "action");
        Button button = new Button();
        LucideIconView icon = new LucideIconView(iconType);
        icon.getStyleClass().add("workspace-action-icon");
        button.setGraphic(icon);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setAccessibleText(label);
        button.setTooltip(new Tooltip(label));
        button.getStyleClass().addAll("workspace-rail-action", "workspace-icon-button");
        button.setOnAction(event -> action.accept(button));
        return button;
    }

    static Button workspaceNewSessionButton(
            WorkspaceType type, Consumer<WorkspaceType> action) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(action, "action");
        String label = com.ynpe.fastremote.i18n.Texts.text(type == WorkspaceType.TERMINAL
                ? "workspace.newTerminalSession" : "workspace.newSftpSession");
        return workspaceIconButton(Icon.PLUS, label, ignored -> action.accept(type));
    }

    private Button favoritePathButton(Supplier<List<String>> paths, Consumer<String> action) {
        Button button = new Button();
        LucideIconView icon = new LucideIconView(Icon.LOCATE_FIXED);
        icon.getStyleClass().add("workspace-action-icon");
        button.setGraphic(icon);
        String label = com.ynpe.fastremote.i18n.Texts.text("legacy.java.d69b5ab453f7d2d6");
        button.setAccessibleText(label);
        button.setTooltip(new Tooltip(label));
        button.getStyleClass().addAll("workspace-rail-action", "workspace-icon-button");
        ContextMenu menu = ApplicationContextMenus.create();
        button.setOnAction(event -> {
            if (ApplicationContextMenus.isShowing(menu)) {
                menu.hide();
                return;
            }
            List<String> available = paths.get();
            if (available.isEmpty()) {
                MenuItem empty = new MenuItem(localizer.text("workspace.goTo.empty"));
                empty.setDisable(true);
                menu.getItems().setAll(empty);
            } else {
                menu.getItems().setAll(available.stream().map(path -> {
                    MenuItem item = new MenuItem(path);
                    item.setMnemonicParsing(false);
                    item.setOnAction(ignored -> action.accept(path));
                    return item;
                }).toList());
            }
            menu.show(button, javafx.geometry.Side.TOP, 0.0, -4.0);
        });
        return button;
    }

    static List<String> mergedFavoritePaths(List<String> global, List<String> host) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(Objects.requireNonNull(global, "global"));
        merged.addAll(Objects.requireNonNull(host, "host"));
        return List.copyOf(merged);
    }

    private void sendLocalTerminalDirectory(LocalTerminalHandle terminal, String path) {
        final String command;
        try {
            command = TerminalDirectoryCommand.local(path, terminal.shellFamily());
        } catch (IllegalArgumentException failure) {
            setStatus(localizer.text("workspace.goTo.failed"), StatusTone.ERROR);
            return;
        }
        terminal.sendInput(command).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (error != null) {
                setStatus(messageFor(error, localizer.text("workspace.goTo.failed")), StatusTone.ERROR);
            } else {
                setStatus(localizer.text("workspace.goTo.localSent", path), StatusTone.CONNECTED);
                terminal.requestFocus();
            }
        }));
    }

    private void sendRemoteTerminalDirectory(OpenSession session, String path) {
        if (session.terminal == null || session.state != ConnectionState.CONNECTED) return;
        ServerOverview.SystemFamily family = session.serverOverview == null
                ? ServerOverview.SystemFamily.UNKNOWN : session.serverOverview.systemFamily();
        final String command;
        try {
            command = TerminalDirectoryCommand.remote(path, family,
                    commandShellFamily(family, session.terminal.shellFamily()));
        } catch (IllegalArgumentException failure) {
            setSessionStatus(StatusModule.TERMINAL, session,
                    localizer.text("workspace.goTo.failed"), StatusTone.ERROR);
            return;
        }
        session.terminal.sendInput(command).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (error != null) {
                setSessionStatus(StatusModule.TERMINAL, session,
                        messageFor(error, localizer.text("workspace.goTo.failed")), StatusTone.ERROR);
            } else {
                setSessionStatus(StatusModule.TERMINAL, session,
                        localizer.text("workspace.goTo.sshSent", path), StatusTone.CONNECTED);
                session.terminal.requestFocus();
            }
        }));
    }

    private void changeSftpRemoteDirectory(OpenSession session, String path) {
        if (session.remoteTable == null || session.remotePathField == null
                || session.state != ConnectionState.CONNECTED) return;
        CompletableFuture<Boolean> changed = new CompletableFuture<>();
        changed.whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (error != null) {
                setSessionStatus(StatusModule.SFTP, session,
                        messageFor(error, localizer.text("workspace.goTo.failed")), StatusTone.ERROR);
            } else {
                setSessionStatus(StatusModule.SFTP, session,
                        localizer.text("workspace.goTo.sftpChanged", path), StatusTone.CONNECTED);
            }
        }));
        loadRemoteDirectory(session, path, session.remoteTable, session.remotePathField, changed);
    }

    private int parsePort() {
        String text = requireText(portField, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d1192e27c3d45da3"));
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.49df3c993e2fc8a0"), invalid);
        }
    }

    private static String requireText(TextField field, String message) {
        String value = field.getText().strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static Throwable unwrap(Throwable error) {
        return UiFailureMessages.unwrap(error);
    }

    private void refreshMcpStatusBar() {
        aiAssistantWorkflow.loadMcpServerStatus().whenComplete((status, error) -> Platform.runLater(() -> {
            if (controllerClosed.get()) return;
            boolean running = error == null && status != null
                    && status.state() == AiAssistantWorkflow.McpServerState.RUNNING;
            updateMcpStatusBar(mcpStatusIndicator, mcpStatusLabel, running);
        }));
    }

    static void updateMcpStatusBar(Label indicator, Label text, boolean running) {
        McpStatusBarPresentation presentation = mcpStatusBarPresentation(running);
        indicator.getStyleClass().removeAll("mcp-status-running", "mcp-status-stopped");
        indicator.getStyleClass().add(presentation.styleClass());
        indicator.setAccessibleText(presentation.text());
        text.setText(presentation.text());
    }

    static McpStatusBarPresentation mcpStatusBarPresentation(boolean running) {
        return new McpStatusBarPresentation(
                com.ynpe.fastremote.i18n.Texts.text(running ? "status.mcp.started" : "status.mcp.stopped"),
                running ? "mcp-status-running" : "mcp-status-stopped");
    }

    record McpStatusBarPresentation(String text, String styleClass) { }

    @Override
    public void close() {
        if (!controllerClosed.compareAndSet(false, true)) return;
        observations.clear();
        expansionSaveDelay.stop();
        windowBoundsSaveDelay.stop();
        horizontalDockRestoreDelay.stop();
        ownedWindowFocusRestoreDelay.stop();
        Window.getWindows().removeListener(ownedWindowListListener);
        if (mainWindow != null && mainWindowCloseHandler != null) {
            mainWindow.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, mainWindowCloseHandler);
            mainWindowCloseHandler = null;
        }
        trafficLogPulse.stop();
        mcpStatusPulse.stop();
        if (transferConflictDialogs != null) {
            transferConflictDialogs.close();
            transferConflictDialogs = null;
        }
        if (sideQuickConnectionTestRunner != null) sideQuickConnectionTestRunner.close();
        persistLibraryGroupExpansion();
        persistWindowBounds();
        if (textEditors != null) textEditors.dispose();
        for (OpenSession session : new HashSet<>(openSessions.values())) {
            session.closed.set(true);
            if (textEditors != null && session.sftp != null) textEditors.cleanupRemoteTemporaryFiles(remoteFiles(session));
            session.fileLog.close();
            if (session.cancelAttempt != null) session.cancelAttempt.run();
            if (session.pendingCredential != null) session.pendingCredential.close();
            if (session.temporaryCredential != null) session.temporaryCredential.close();
            if (session.terminal != null) session.terminal.close();
            if (session.sftp != null) session.sftp.close();
            releaseRetainedConnections(session);
        }
        openSessions.clear();
        localTerminals.values().forEach(LocalTerminalHandle::close);
        localTerminals.clear();
        localFileWorkspaces.values().forEach(LocalFileWorkspaceController::close);
        localFileWorkspaces.clear();
        localWorkspaceSessions.clear();
        if (pendingConnectionCredential != null) {
            pendingConnectionCredential.close();
            pendingConnectionCredential = null;
        }
        aiRuntimes.values().forEach(runtime -> runtime.view.close());
        if (aiChatView != null) aiChatView.close();
        if (aiPlacement != null) aiPlacement.closeDialogForShutdown();
        if (aiPromptFocusRestorer != null) aiPromptFocusRestorer.close();
        if (aiAssistantPageController != null) aiAssistantPageController.close();
        if (snippetLibraryDialog != null) snippetLibraryDialog.close();
        diagnosticTextLog.close();
        if (automationGateway != null) automationGateway.clearWindowHandlers();
        mainWindow = null;
    }

    private void registerGroupExpansion(TreeItem<String> item, String expansionKey) {
        // Restored state remains authoritative outside active search results.
        item.setExpanded(!persistedWorkspaceSettings.collapsedHostGroupIds().contains(expansionKey));
        expansionGroupItems.put(item, expansionKey);
        item.expandedProperty().addListener((ignored, previous, expanded) -> {
            if (synchronizingGroupExpansion || Objects.equals(previous, expanded)) return;
            Set<String> collapsedIds = updatedCollapsedGroupKeys(
                    persistedWorkspaceSettings.collapsedHostGroupIds(), expansionKey, expanded);
            persistedWorkspaceSettings = persistedWorkspaceSettings.withCollapsedHostGroupIds(collapsedIds);
            expansionStateDirty = true;
            synchronizeGroupExpansion();
            expansionSaveDelay.playFromStart();
        });
    }

    private void sendInitialRemoteTerminalDirectory(
            OpenSession session, String path, int attempt) {
        if (session.closed.get() || session.state != ConnectionState.CONNECTED
                || session.terminal == null) return;
        boolean waitingForWindowsShell = TerminalDirectoryCommand.looksLikeWindowsRemotePath(path)
                && session.terminal.shellFamily() == LocalTerminalHandle.ShellFamily.UNKNOWN
                && attempt < 50;
        if (!waitingForWindowsShell) {
            sendRemoteTerminalDirectory(session, path);
            return;
        }
        PauseTransition delay = new PauseTransition(Duration.millis(100));
        delay.setOnFinished(ignored -> sendInitialRemoteTerminalDirectory(session, path, attempt + 1));
        delay.play();
    }

    private void prepareLibrarySearch(TreeItem<String> root, Map<TreeItem<String>, ?> groups, String query) {
        boolean previous = synchronizingGroupExpansion;
        synchronizingGroupExpansion = true;
        try {
            hideEmptyGroupsWhileSearching(root, groups, query);
        } finally {
            synchronizingGroupExpansion = previous;
        }
    }

    private void synchronizeGroupExpansion() {
        synchronizingGroupExpansion = true;
        try {
            Set<String> collapsedIds = persistedWorkspaceSettings.collapsedHostGroupIds();
            expansionGroupItems.forEach((item, expansionKey) ->
                    item.setExpanded(!collapsedIds.contains(expansionKey)));
        } finally {
            synchronizingGroupExpansion = false;
        }
    }

    private void persistLibraryGroupExpansion() {
        if (!expansionStateDirty) return;
        GlobalSettings settings = persistedWorkspaceSettings;
        expansionStateDirty = false;
        settingsWorkflow.save(settings).whenComplete((ignored, error) -> {
            if (error != null) {
                Platform.runLater(() -> {
                    expansionStateDirty = true;
                    if (rootPane.getScene() != null && rootPane.getScene().getWindow().isShowing()) {
                        setStatus(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.3712e11db4d56c08")), StatusTone.ERROR);
                    }
                });
            }
        });
    }

    static String hostGroupExpansionKey(GroupId groupId) {
        return Objects.requireNonNull(groupId, "groupId").toString();
    }

    static String credentialGroupExpansionKey(CredentialGroupId groupId) {
        return "credential:" + Objects.requireNonNull(groupId, "groupId");
    }

    static String snippetGroupExpansionKey(SnippetGroupId groupId) {
        return "snippet:" + Objects.requireNonNull(groupId, "groupId");
    }

    static String variableGroupExpansionKey(SnippetVariableGroupId groupId) {
        return "variable:" + Objects.requireNonNull(groupId, "groupId");
    }

    static Set<String> updatedCollapsedGroupKeys(Set<String> collapsedKeys, String expansionKey, boolean expanded) {
        Set<String> updated = new HashSet<>(Objects.requireNonNull(collapsedKeys, "collapsedKeys"));
        if (expanded) updated.remove(Objects.requireNonNull(expansionKey, "expansionKey"));
        else updated.add(Objects.requireNonNull(expansionKey, "expansionKey"));
        return Set.copyOf(updated);
    }

    private void updateSelectedConnectionStatus() {
        updateActions();
        Tab selected = selectedWorkspaceTab();
        LocalTerminalHandle local = localTerminals.get(selected);
        if (local != null) Platform.runLater(local::requestFocus);
        OpenSession session = openSessions.get(selected);
        if (session != null && session.state == ConnectionState.CONNECTED
                && session.terminal != null && session.tabs.get(WorkspaceType.TERMINAL) == selected) {
            Platform.runLater(session.terminal::requestFocus);
        }
    }

    private static String messageFor(Throwable error, String fallback) {
        return UiFailureMessages.messageFor(error, fallback);
    }

    private static String vaultUnlockFailureMessage(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof com.ynpe.fastremote.domain.vault.VaultException vaultFailure
                && vaultFailure.type() == com.ynpe.fastremote.domain.vault.VaultFailureType.INVALID_PASSWORD_OR_CORRUPTED) {
            return com.ynpe.fastremote.i18n.Texts.text("legacy.java.6a401c6c1416de43");
        }
        return messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.13a0e28518f4e5b4"));
    }

    static String exportVaultVerificationMessage(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof com.ynpe.fastremote.domain.vault.VaultException vaultFailure) {
            return switch (vaultFailure.type()) {
                case NOT_CREATED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.0777f517b55c80e8");
                case INVALID_PASSWORD_OR_CORRUPTED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.6a401c6c1416de43");
                case STORAGE_FAILED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.19e48fcd8cc15200");
                case CRYPTOGRAPHY_FAILED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.37dc131ed3c902ff");
                case LOCKED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.df8875d53670aaa8");
                case ALREADY_CREATED, SECRET_NOT_FOUND -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.502c1049caddc72d");
            };
        }
        return com.ynpe.fastremote.i18n.Texts.text("legacy.java.ba5fde0021ebcdcd");
    }

    private record RemoteListing(String path, List<RemoteFileEntry> entries) {}

    private record SftpGraphicKey(SftpFileIconResolver.Type type, boolean symbolicLink) { }

    private static final class PersistentHorizontalScrollTableView<T> extends TableView<T> {
        private PersistentHorizontalScrollTableView(ObservableList<T> items) {
            super(items);
        }

        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            lookupAll(".scroll-bar").stream()
                    .filter(ScrollBar.class::isInstance)
                    .map(ScrollBar.class::cast)
                    .filter(scroll -> scroll.getOrientation() == javafx.geometry.Orientation.HORIZONTAL)
                    .forEach(scroll -> {
                        scroll.setManaged(true);
                        scroll.setVisible(true);
                        scroll.setOpacity(1.0);
                    });
        }
    }

    private record GroupDrop(Optional<GroupId> parentId, int targetIndex) {}

    private static final class HostDragState {
        private List<TreeItem<String>> items = List.of();
        private List<TreeItem<String>> pressedSelection = List.of();
    }

    private record VaultPasswordRequest(char[] password, boolean rememberUnlock) {}

    private record HostExportRequest(
            ConfigurationSelection selection, char[] password) implements AutoCloseable {
        private HostExportRequest {
            Objects.requireNonNull(password, "password");
        }

        @Override
        public void close() {
            Arrays.fill(password, '\0');
        }
    }

    enum QuickCredentialSource {
        MANUAL("legacy.java.4752af2e0472c49e"),
        LIBRARY("legacy.java.8c7ab4e04a0d22ea");

        private final String labelKey;

        QuickCredentialSource(String labelKey) {
            this.labelKey = labelKey;
        }

        @Override
        public String toString() {
            return com.ynpe.fastremote.i18n.Texts.text(labelKey);
        }
    }

    enum ToolInitialFocus {
        HOST, SESSION_SEARCH, CREDENTIAL_SEARCH, SNIPPET_SEARCH, VARIABLE_SEARCH, AI_PROMPT, NONE
    }

    enum ToolWindow {
        QUICK_CONNECT("legacy.java.bec3d896410b5294", "legacy.java.129d40826f7f53f2", ToolInitialFocus.HOST),
        HOST_LIBRARY("legacy.java.278dda9e9d5dce65", "legacy.java.539a9fd19d72ed33", ToolInitialFocus.SESSION_SEARCH),
        CREDENTIAL_VAULT("legacy.java.bb2b5d546de68220", "legacy.java.d65bdb651314f91a", ToolInitialFocus.CREDENTIAL_SEARCH),
        SNIPPETS("legacy.java.4466bdad43dae482", "legacy.java.1cbb8f00f07db977", ToolInitialFocus.SNIPPET_SEARCH),
        VARIABLE_LIBRARY("legacy.java.0aefd44f7a90ca71", "legacy.java.e6ed3d985f574274", ToolInitialFocus.VARIABLE_SEARCH),
        AI_ASSISTANT("legacy.java.1b0049fcfd4163fe", "legacy.java.e4f723b039cbc5d9", ToolInitialFocus.AI_PROMPT),
        DIAGNOSTIC_LOG("legacy.java.bb1d19b85634606e", "legacy.java.d8d4849375c49390", ToolInitialFocus.NONE);

        private final String titleKey;
        private final String subtitleKey;
        final ToolInitialFocus initialFocus;

        ToolWindow(String titleKey, String subtitleKey, ToolInitialFocus initialFocus) {
            this.titleKey = titleKey;
            this.subtitleKey = subtitleKey;
            this.initialFocus = initialFocus;
        }

        private String title() {
            return com.ynpe.fastremote.i18n.Texts.text(titleKey);
        }

        private String subtitle() {
            return com.ynpe.fastremote.i18n.Texts.text(subtitleKey);
        }

        private boolean importable() {
            return this == SNIPPETS || this == VARIABLE_LIBRARY;
        }
    }

    enum StatusModule {
        GLOBAL("legacy.java.63d6b47116de363c"),
        TERMINAL("legacy.java.e2a76ef1f12e147f"),
        SFTP(null);

        private final String labelKey;

        StatusModule(String labelKey) {
            this.labelKey = labelKey;
        }

        private String label() {
            return labelKey == null ? "SFTP" : com.ynpe.fastremote.i18n.Texts.text(labelKey);
        }
    }

    private enum StatusTone {
        IDLE("status-dot-idle"),
        WORKING("status-dot-working"),
        CONNECTED("status-dot-connected"),
        ERROR("status-dot-error");

        private final String styleClass;

        StatusTone(String styleClass) {
            this.styleClass = styleClass;
        }
    }

    private record TrafficSample(long received, long sent) { }
}
