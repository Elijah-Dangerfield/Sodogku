//
//  AdNetwork.swift
//  iosApp
//
//  AdMob on iOS, plus the two consent prompts that must run in front of it.
//
//  ORDER IS A POLICY REQUIREMENT, NOT A PREFERENCE:
//    1. UMP consent (EEA / UK)
//    2. App Tracking Transparency
//    3. MobileAds.start()
//    4. the first ad request
//
//  Google's own guidance is that ATT comes after the UMP form, because UMP can
//  present the ATT pre-prompt. Apple rejects builds that request the IDFA
//  before the ATT dialog. Neither failure shows up as a crash; both show up as
//  a rejected release, which is why the order lives in one place.
//
//  Everything policy-shaped above this — frequency gates, the new-user grace,
//  the offline grace, and the rule that a failed ad still pays the player —
//  is in `RealAdGate` in shared Kotlin. This file only loads and shows.
//
//  ── SETUP REQUIRED ───────────────────────────────────────────────────────
//  The `#if canImport(GoogleMobileAds)` guards exist so the app still builds
//  before the SDK is added. Until it is, every ad "fails", and the shared
//  Kotlin grants the reward anyway — correct, but revenue-free.
//
//  In Xcode: File ▸ Add Package Dependencies ▸
//    https://github.com/googleads/swift-package-manager-google-mobile-ads
//  and add both the `GoogleMobileAds` and `UserMessagingPlatform` products to
//  the iOS target. Then add `GADApplicationIdentifier` to Info.plist (it is
//  already there with Google's sample id) and flip
//  `AdUnits.useTestUnits` in shared Kotlin when the real units exist.
//
import ComposeApp
import Foundation
import UIKit

#if canImport(AppTrackingTransparency)
import AppTrackingTransparency
#endif

#if canImport(GoogleMobileAds)
import GoogleMobileAds
#endif

#if canImport(UserMessagingPlatform)
import UserMessagingPlatform
#endif

class IOSAdNetwork: NSObject, AdNetwork {

    private var initialised = false

    // MARK: - AdNetwork

    func __prepare() async throws {
        guard !initialised else { return }
        #if canImport(GoogleMobileAds)
        await requestConsent()
        await requestTrackingAuthorization()
        guard canRequestAds() else { return }
        await MobileAds.shared.start()
        MobileAds.shared.requestConfiguration.tagForChildDirectedTreatment = false
        initialised = true
        #endif
    }

    func __show(format: AdFormat) async throws -> AdShowOutcome {
        try await __prepare()
        #if canImport(GoogleMobileAds)
        guard initialised else {
            return AdShowOutcome(result: .notShown, errorKind: "sdk_not_ready")
        }
        guard let root = await Self.rootViewController() else {
            return AdShowOutcome(result: .notShown, errorKind: "no_root_view_controller")
        }
        switch format {
        case .rewarded:
            return await showRewarded(from: root)
        case .interstitial:
            return await showInterstitial(from: root)
        case .appOpen:
            return await showAppOpen(from: root)
        default:
            // A banner is a view in a hierarchy, not something you show and
            // wait on. `ads.bannerOnLevelMap` is off by default.
            return AdShowOutcome(result: .notShown, errorKind: "banner_is_not_a_full_screen_format")
        }
        #else
        return AdShowOutcome(result: .notShown, errorKind: "google_mobile_ads_not_linked")
        #endif
    }

    func preload(format: AdFormat) {
        #if canImport(GoogleMobileAds)
        Task { [weak self] in
            try? await self?.__prepare()
            guard self?.initialised == true else { return }
            switch format {
            case .rewarded:
                self?.cachedRewarded = try? await RewardedAd.load(
                    with: AdUnits.shared.ios(format: .rewarded), request: Request())
            case .interstitial:
                self?.cachedInterstitial = try? await InterstitialAd.load(
                    with: AdUnits.shared.ios(format: .interstitial), request: Request())
            case .appOpen:
                self?.cachedAppOpen = try? await AppOpenAd.load(
                    with: AdUnits.shared.ios(format: .appOpen), request: Request())
            default:
                break
            }
        }
        #endif
    }

    #if canImport(GoogleMobileAds)

    private var cachedRewarded: RewardedAd?
    private var cachedInterstitial: InterstitialAd?
    private var cachedAppOpen: AppOpenAd?

    private func showRewarded(from root: UIViewController) async -> AdShowOutcome {
        let ad: RewardedAd
        do {
            if let cached = cachedRewarded {
                ad = cached
                cachedRewarded = nil
            } else {
                ad = try await RewardedAd.load(
                    with: AdUnits.shared.ios(format: .rewarded), request: Request())
            }
        } catch {
            return Self.loadFailure(error)
        }

        let delegate = DismissalDelegate()
        ad.fullScreenContentDelegate = delegate
        var earned = false
        let dismissal = await withCheckedContinuation { (continuation: CheckedContinuation<Dismissal, Never>) in
            delegate.onFinished = { continuation.resume(returning: $0) }
            Task { @MainActor in
                ad.present(from: root) { earned = true }
            }
        }
        preload(format: .rewarded)

        switch dismissal {
        case .failed(let kind): return AdShowOutcome(result: .failed, errorKind: kind)
        case .closed: return AdShowOutcome(result: earned ? .rewarded : .dismissed, errorKind: nil)
        }
    }

    private func showInterstitial(from root: UIViewController) async -> AdShowOutcome {
        let ad: InterstitialAd
        do {
            if let cached = cachedInterstitial {
                ad = cached
                cachedInterstitial = nil
            } else {
                ad = try await InterstitialAd.load(
                    with: AdUnits.shared.ios(format: .interstitial), request: Request())
            }
        } catch {
            return Self.loadFailure(error)
        }
        let outcome = await present(ad: ad, from: root)
        preload(format: .interstitial)
        return outcome
    }

    private func showAppOpen(from root: UIViewController) async -> AdShowOutcome {
        let ad: AppOpenAd
        do {
            if let cached = cachedAppOpen {
                ad = cached
                cachedAppOpen = nil
            } else {
                ad = try await AppOpenAd.load(
                    with: AdUnits.shared.ios(format: .appOpen), request: Request())
            }
        } catch {
            return Self.loadFailure(error)
        }
        return await present(ad: ad, from: root)
    }

    private func present(ad: FullScreenPresentingAd, from root: UIViewController) async -> AdShowOutcome {
        let delegate = DismissalDelegate()
        ad.fullScreenContentDelegate = delegate
        let dismissal = await withCheckedContinuation { (continuation: CheckedContinuation<Dismissal, Never>) in
            delegate.onFinished = { continuation.resume(returning: $0) }
            Task { @MainActor in
                if let interstitial = ad as? InterstitialAd {
                    interstitial.present(from: root)
                } else if let appOpen = ad as? AppOpenAd {
                    appOpen.present(from: root)
                } else {
                    delegate.onFinished?(.failed("unsupported_format"))
                }
            }
        }
        switch dismissal {
        case .failed(let kind): return AdShowOutcome(result: .failed, errorKind: kind)
        case .closed: return AdShowOutcome(result: .completed, errorKind: nil)
        }
    }

    private func requestConsent() async {
        let parameters = RequestParameters()
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            ConsentInformation.shared.requestConsentInfoUpdate(with: parameters) { _ in
                continuation.resume()
            }
        }
        guard let root = await Self.rootViewController() else { return }
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            Task { @MainActor in
                ConsentForm.loadAndPresentIfRequired(from: root) { _ in
                    continuation.resume()
                }
            }
        }
    }

    /// `canRequestAds` is the gate, not "did the form show". UMP answers true
    /// for a user outside the EEA who was never shown anything, and false for
    /// one who declined; reading the form's presence would block ads for most
    /// of the world.
    private func canRequestAds() -> Bool {
        ConsentInformation.shared.canRequestAds
    }

    private static func loadFailure(_ error: Error) -> AdShowOutcome {
        let code = (error as NSError).code
        // GADErrorCode.noFill == 1, .networkError == 2
        switch code {
        case 1: return AdShowOutcome(result: .noFill, errorKind: "load_\(code)")
        case 2: return AdShowOutcome(result: .offline, errorKind: "load_\(code)")
        default: return AdShowOutcome(result: .failed, errorKind: "load_\(code)")
        }
    }

    private enum Dismissal {
        case closed
        case failed(String)
    }

    private final class DismissalDelegate: NSObject, FullScreenContentDelegate {
        var onFinished: ((Dismissal) -> Void)?

        func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
            finish(.closed)
        }

        func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
            finish(.failed("show_\((error as NSError).code)"))
        }

        private func finish(_ dismissal: Dismissal) {
            let handler = onFinished
            onFinished = nil
            handler?(dismissal)
        }
    }

    #endif

    /// SPEC 7.2: ATT goes in front of the first ad request, and it is asked at
    /// a moment where the value is legible — which is why `prepare()` is called
    /// lazily by the first ad gate rather than at launch.
    private func requestTrackingAuthorization() async {
        #if canImport(AppTrackingTransparency)
        guard #available(iOS 14.5, *) else { return }
        guard ATTrackingManager.trackingAuthorizationStatus == .notDetermined else { return }
        _ = await ATTrackingManager.requestTrackingAuthorization()
        #endif
    }

    @MainActor
    private static func rootViewController() -> UIViewController? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }?
            .keyWindow?
            .rootViewController
    }
}
