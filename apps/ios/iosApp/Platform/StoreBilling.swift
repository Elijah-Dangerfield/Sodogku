//
//  StoreBilling.swift
//  iosApp
//
//  StoreKit 2. No SDK to add — StoreKit ships with the OS — so unlike
//  `AdNetwork.swift` this is unguarded and real from the first build.
//
//  The one rule that matters is the same one the Kotlin side is built around:
//  a store we could not reach reports `.unknown`, never `.notOwned`. Only an
//  answer clears a paying customer's entitlement.
//
//  ── SETUP REQUIRED ───────────────────────────────────────────────────────
//  Local testing needs a StoreKit configuration file, because a product id
//  that App Store Connect has never heard of returns an empty product list and
//  the paywall shows no price:
//
//    File ▸ New ▸ File ▸ StoreKit Configuration File (Products.storekit)
//    Add a Non-Consumable with product id `sodogku_pro` (ProductIds.pro),
//    price 4.99, then Product ▸ Scheme ▸ Edit Scheme ▸ Run ▸ Options ▸
//    StoreKit Configuration ▸ Products.storekit.
//
//  For a sandbox test on a device, create the same non-consumable in App Store
//  Connect and sign in with a Sandbox Apple Account.
//
import ComposeApp
import Foundation
import StoreKit

class IOSStoreBilling: StoreBilling {

    func __ownership(productId: String) async throws -> StoreOwnership {
        await currentOwnership(of: productId)
    }

    /// Apple requires a visible restore control, and this is what it calls.
    /// `AppStore.sync()` forces a receipt refresh (it can prompt for the Apple
    /// Account password), which is the difference between "restore" and the
    /// ordinary entitlement check above.
    func __restore(productId: String) async throws -> StoreOwnership {
        do {
            try await AppStore.sync()
        } catch {
            // A cancelled password prompt or a network failure. We still ask
            // for the local entitlements below — they may already be there.
            NSLog("StoreKit sync failed during restore: \(error)")
        }
        return await currentOwnership(of: productId)
    }

    func __purchase(productId: String) async throws -> StorePurchaseOutcome {
        guard let product = await product(for: productId) else {
            return StorePurchaseOutcome(result: .unavailable, errorKind: "product_not_found")
        }

        do {
            switch try await product.purchase() {
            case .success(let verification):
                switch verification {
                case .verified(let transaction):
                    await transaction.finish()
                    return StorePurchaseOutcome(result: .purchased, errorKind: nil)
                case .unverified(_, let error):
                    // A failed signature check. Treat it as a failure rather
                    // than a grant: this is the one place where being generous
                    // would be giving the product away to a jailbroken device.
                    return StorePurchaseOutcome(result: .failed, errorKind: "unverified_\((error as NSError).code)")
                }
            case .userCancelled:
                return StorePurchaseOutcome(result: .cancelled, errorKind: nil)
            case .pending:
                // Ask to Buy, or a payment awaiting approval. Not owned yet;
                // the foreground refresh in `RealEntitlements` picks it up.
                return StorePurchaseOutcome(result: .failed, errorKind: "purchase_pending")
            @unknown default:
                return StorePurchaseOutcome(result: .failed, errorKind: "unknown_purchase_result")
            }
        } catch StoreKitError.userCancelled {
            return StorePurchaseOutcome(result: .cancelled, errorKind: nil)
        } catch {
            return StorePurchaseOutcome(result: .failed, errorKind: "storekit_\((error as NSError).code)")
        }
    }

    func __priceLabel(productId: String) async throws -> String? {
        await product(for: productId)?.displayPrice
    }

    // MARK: - Private

    private func currentOwnership(of productId: String) async -> StoreOwnership {
        // `currentEntitlements` is served from the on-device receipt, so a
        // device that has seen the purchase answers with no network at all.
        // That is what keeps an offline Pro player Pro.
        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result else { continue }
            if transaction.productID == productId && transaction.revocationDate == nil {
                return .owned
            }
        }

        // Nothing locally — and StoreKit gives no "could not reach the store"
        // signal here, so an empty result on a device signed out of the App
        // Store looks exactly like a genuine "never bought it". A catalogue
        // lookup is the cheapest available proof that we could talk to the
        // store at all; if that fails too then we do not know, and not knowing
        // must never clear a paying customer.
        let storeAnswered = await product(for: productId) != nil
        return storeAnswered ? .notOwned : .unknown
    }

    private func product(for productId: String) async -> Product? {
        do {
            return try await Product.products(for: [productId]).first
        } catch {
            NSLog("StoreKit product lookup failed: \(error)")
            return nil
        }
    }
}
