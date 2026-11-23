package com.example.data

/**
 * Immutable specification representing a single repository source code stage used to boot and open
 * the canonical locked Base Clients:
 * - **ProjectIgnis / EDOPro** (`https://github.com/ProjectIgnis/EDOPro`)
 * - **Card-Forge / Forge** (`https://github.com/Card-Forge/forge`)
 *
 * Strictly read-only and locked to the upstream GitHub source repositories unless synced directly
 * from upstream.
 */
data class BaseClientBootStage(
    val stageOrder: Int,
    val sourceFilePath: String,
    val symbolOrEntrypoint: String,
    val codeSnippet: String,
    val runtimeResult: String
)

/**
 * Complete repository-backed boot specification for a locked Base Client (**EDOPro** or **Forge**).
 */
data class BaseClientCodeBootSpec(
    val baseVariantId: String,
    val shortTitle: String,
    val exactGitHubRepoUrl: String,
    val rawBootstrapCodeUrl: String,
    val deepLinkSchemeUri: String,
    val primaryEntrypointFile: String,
    val primaryLaunchCommand: String,
    val languageRuntime: String,
    val bootstrapCodeBlock: String,
    val stages: List<BaseClientBootStage>
)

/**
 * Registry holding the locked, upstream-referenced client startup source code for the two Base Clients:
 * 1. [[EDOPro]] (`EDOPRO_BASE_MR5` -> `https://github.com/ProjectIgnis/EDOPro`)
 * 2. [[Forge]] (`FORGE_MTG_BASE` -> `https://github.com/Card-Forge/forge`)
 */
object BaseClientCodeRegistry {

    val edoproBaseBootSpec = BaseClientCodeBootSpec(
        baseVariantId = "EDOPRO_BASE_MR5",
        shortTitle = "EDOPro Base Client (ProjectIgnis/EDOPro)",
        exactGitHubRepoUrl = "https://github.com/ProjectIgnis/EDOPro",
        rawBootstrapCodeUrl = "https://github.com/ProjectIgnis/EDOPro/blob/master/gframe/gframe.cpp",
        deepLinkSchemeUri = "edopro://duel/mr5",
        primaryEntrypointFile = "gframe/gframe.cpp -> ygo::Game::Initialize()",
        primaryLaunchCommand = "cmake --build build && ./ygopro -f -C system.conf",
        languageRuntime = "C++20 • Irrlicht Engine • ProjectIgnis/ocgcore (Lua 5.3)",
        bootstrapCodeBlock = """
            |// Upstream: https://github.com/ProjectIgnis/EDOPro (gframe/gframe.cpp & gframe/game.cpp)
            |int main(int argc, char* argv[]) {
            |    ygo::Config::LoadConfig("system.conf");
            |    ygo::mainGame = new ygo::Game();
            |    if (!ygo::mainGame->Initialize()) return EXIT_FAILURE;
            |    ygo::dataManager.LoadDB("expansions/cards.cdb");
            |    ygo::mainGame->MainLoop(); // Boots Irrlicht Client Window + CTOS/STOC ocgcore
            |    return EXIT_SUCCESS;
            |}
        """.trimMargin(),
        stages = listOf(
            BaseClientBootStage(
                stageOrder = 1,
                sourceFilePath = "gframe/config.cpp",
                symbolOrEntrypoint = "ygo::Config::LoadConfig(\"system.conf\")",
                codeSnippet = "LoadDBPaths(); ReadServerList(\"servers.json\"); // Protocol v40.1.4 MR5",
                runtimeResult = "Loaded MR5 defaults (8000 LP, 5-card hand, 5 MMZ / 5 STZ / 2 EMZ)"
            ),
            BaseClientBootStage(
                stageOrder = 2,
                sourceFilePath = "gframe/data_manager.cpp",
                symbolOrEntrypoint = "ygo::dataManager.LoadDB(\"cards.cdb\")",
                codeSnippet = "sqlite3_open_v2(cdb_path, &db, SQLITE_OPEN_READONLY, nullptr);",
                runtimeResult = "Mounted SQLite BabelCDB card database & Lua 5.3 script hooks"
            ),
            BaseClientBootStage(
                stageOrder = 3,
                sourceFilePath = "ocgcore/ocgapi.cpp",
                symbolOrEntrypoint = "create_duel(seed) + set_player_info(0, 8000, 5, 1)",
                codeSnippet = "pduel = create_duel(seed); set_script_reader(ScriptReaderEx);",
                runtimeResult = "Initialized C++ ocgcore duel state machine (Master Rule 5)"
            ),
            BaseClientBootStage(
                stageOrder = 4,
                sourceFilePath = "gframe/game.cpp",
                symbolOrEntrypoint = "ygo::mainGame->Initialize() -> MainLoop()",
                codeSnippet = "device = irr::createDevice(video::EDT_OPENGL, dimension2d_u32(1024, 640));",
                runtimeResult = "Opened ProjectIgnis/EDOPro client window & CTOS/STOC duel loop"
            )
        )
    )

    val forgeBaseBootSpec = BaseClientCodeBootSpec(
        baseVariantId = "FORGE_MTG_BASE",
        shortTitle = "Card-Forge MTG Base Client (Card-Forge/forge)",
        exactGitHubRepoUrl = "https://github.com/Card-Forge/forge",
        rawBootstrapCodeUrl = "https://github.com/Card-Forge/forge/blob/master/forge-gui-desktop/src/main/java/forge/view/Main.java",
        deepLinkSchemeUri = "forge://mtg/match",
        primaryEntrypointFile = "forge-gui-desktop/src/main/java/forge/view/Main.java",
        primaryLaunchCommand = "mvn -pl forge-gui-desktop javafx:run / Singletons.initializeOnce()",
        languageRuntime = "Java 17 • LibGDX/Swing GUI • Card-Forge Rules Engine",
        bootstrapCodeBlock = """
            |// Upstream: https://github.com/Card-Forge/forge (forge/view/Main.java & forge-gui-mobile)
            |public static void main(final String[] args) {
            |    FThreads.assertExecutedByEdt(false);
            |    Singletons.initializeOnce(true); // Mounts res/cardsfolder/*.txt & FModel
            |    Forge.start(new ForgePreferences()); // Opens Standalone Card-Forge Window
            |    Singletons.getControl().setCurrentScreen(FScreen.HOME_SCREEN);
            |}
        """.trimMargin(),
        stages = listOf(
            BaseClientBootStage(
                stageOrder = 1,
                sourceFilePath = "forge-gui/src/main/java/forge/model/FModel.java",
                symbolOrEntrypoint = "FModel.initialize(progressBar, prefs)",
                codeSnippet = "magicDb = new CardStorageReader(\"res/cardsfolder\", null, true);",
                runtimeResult = "Parsed res/cardsfolder/*.txt scripts & oracle rules catalog"
            ),
            BaseClientBootStage(
                stageOrder = 2,
                sourceFilePath = "forge-gui/src/main/java/forge/Singletons.java",
                symbolOrEntrypoint = "Singletons.initializeOnce(true)",
                codeSnippet = "view = GuiBase.getInterface().getNewGuiGame(); control = new FControl();",
                runtimeResult = "Initialized Card-Forge MTG Singletons & GuiBase Mobile/Desktop adapter"
            ),
            BaseClientBootStage(
                stageOrder = 3,
                sourceFilePath = "forge-game/src/main/java/forge/game/Game.java",
                symbolOrEntrypoint = "new Game(registeredPlayers, rules, match) -> GameAction",
                codeSnippet = "phaseHandler = new PhaseHandler(this); stack = new MagicStack(this);",
                runtimeResult = "Started MTG Rules Engine (20 Life, 7-card hand, The Stack & Blocker Combat)"
            ),
            BaseClientBootStage(
                stageOrder = 4,
                sourceFilePath = "forge-gui-desktop/src/main/java/forge/view/Main.java",
                symbolOrEntrypoint = "Forge.start(...) -> FControl.initialize()",
                codeSnippet = "GuiBase.getInterface().invokeInEdtNow(() -> view.getFrame().setVisible(true));",
                runtimeResult = "Opened Card-Forge Standalone Client Window with MTG match lobby"
            )
        )
    )

    fun findForBaseVariant(baseVariantId: String): BaseClientCodeBootSpec? = when (baseVariantId) {
        "EDOPRO_BASE_MR5" -> edoproBaseBootSpec
        "FORGE_MTG_BASE" -> forgeBaseBootSpec
        else -> null
    }
}
