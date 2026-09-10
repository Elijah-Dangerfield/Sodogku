import SwiftUI
import UIKit
import ComposeApp

@main
struct iOSApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    private let graph = AppGraph.shared

    var body: some Scene {
        WindowGroup {
            RootComposeView(
                appComponent: graph.component,
                nativeViewFactory: graph.nativeViewFactory
            )
            .onOpenURL { url in
                // Forward URLs from custom-scheme links and Universal Links
                // into the Kotlin DeepLinkBridge — App.kt collects from it
                // and calls navController.handleDeepLink.
                graph.component.deepLinkBridge.emit(url: url.absoluteString)
            }
        }
    }
}

/// The Kotlin object graph, built the first time anything asks for it.
///
/// It used to be built in `iOSApp.init`, back when that was the only way into
/// the app. A home-screen quick action is not: UIKit builds the app delegate
/// and connects the scene with the tapped item already in hand, and the order
/// of that against a SwiftUI `App` struct's initialiser is not something to
/// bet a feature on. Whichever side asks first builds it, and the other gets
/// the same instance.
final class AppGraph {

    static let shared = AppGraph()

    let permissionManager = IOSPermissionManager()
    let reviewLauncher = IOSReviewLauncher()
    let adNetwork = IOSAdNetwork()
    let storeBilling = IOSStoreBilling()
    let nativeViewFactory = IOSNativeViewFactory.shared
    let component: IosAppComponent

    private init() {
        component = create(
            permissionManager: permissionManager,
            reviewLauncher: reviewLauncher,
            adNetwork: adNetwork,
            storeBilling: storeBilling,
            nativeViewFactory: nativeViewFactory
        )
        component.telemetry.initialize()
        // Construct every @AutoInit singleton up front — resolving the set is
        // what forces construction (AppEventDispatcher's lifecycle attach etc.).
        _ = component.autoInits
    }

    /// Turns a tapped long-press entry into the deep link it stands for.
    ///
    /// Kotlin owns the mapping (`AppShortcuts`), so the plist carries an
    /// identifier and no URL, and there is one list of destinations rather
    /// than one per platform. An unknown type means the plist and that list
    /// have drifted, which `ShortcutEntriesAgreeTest` is there to prevent.
    ///
    /// The URL goes into `DeepLinkBridge`, which holds it. On a cold start this
    /// runs before Compose has composed anything, let alone built a nav graph;
    /// `App.kt` waits for the graph before opening whatever is queued.
    @discardableResult
    func open(shortcut: UIApplicationShortcutItem) -> Bool {
        guard let url = ShortcutsKt.deepLinkForShortcut(type: shortcut.type) else {
            NSLog("Ignoring unknown home-screen shortcut: %@", shortcut.type)
            return false
        }
        component.deepLinkBridge.emit(url: url)
        return true
    }
}

/// UIKit's half of the app, which exists for the long-press menu and nothing
/// else. The SwiftUI `App` protocol surfaces none of the quick-action
/// callbacks, so this is the way back to them.
final class AppDelegate: NSObject, UIApplicationDelegate {

    /// Where a **cold** launch from the menu is caught.
    ///
    /// This app is scene based, so a tapped entry does not arrive through
    /// `performActionFor` on a cold start — it rides in on the connection
    /// options of the scene being built, and this is the first place we are
    /// handed them. Reading it here rather than in `scene(_:willConnectTo:)`
    /// keeps the scene delegate below down to the one method SwiftUI does not
    /// already handle for us.
    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(
            name: nil,
            sessionRole: connectingSceneSession.role
        )
        if connectingSceneSession.role == .windowApplication {
            configuration.delegateClass = SceneDelegate.self
        }
        if let shortcut = options.shortcutItem {
            AppGraph.shared.open(shortcut: shortcut)
        }
        return configuration
    }

    /// The pre-scenes path, kept as a backstop.
    ///
    /// iOS only calls this when no connected scene delegate answers
    /// `windowScene(_:performActionFor:)`, and ours does, so on this app it
    /// should never run. It is here because "which of the two UIKit picked" is
    /// not a thing worth learning from a bug report.
    func application(
        _ application: UIApplication,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        completionHandler(AppGraph.shared.open(shortcut: shortcutItem))
    }
}

/// Catches a long-press entry tapped while the app is already alive, whether it
/// was on screen or in the background. Deliberately the only thing this
/// implements: SwiftUI builds and owns the window, and a scene delegate that
/// reached for that would be taking over something that already works.
final class SceneDelegate: NSObject, UIWindowSceneDelegate {

    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        completionHandler(AppGraph.shared.open(shortcut: shortcutItem))
    }
}

struct RootComposeView: View {
    @Environment(\.scenePhase) private var scenePhase
    let appComponent: IosAppComponent
    let nativeViewFactory: SodogkuNativeViewFactory

    var body: some View {
        ComposeView(
            appComponent: appComponent,
            nativeViewFactory: nativeViewFactory
        )
        .ignoresSafeArea()
    }
}

struct ComposeView: UIViewControllerRepresentable {
    let appComponent: IosAppComponent
    let nativeViewFactory: SodogkuNativeViewFactory

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(
            appComponent: appComponent
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
