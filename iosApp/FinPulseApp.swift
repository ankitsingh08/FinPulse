import SwiftUI
import FinPulseShared

@main
struct FinPulseApp: App {
    init() {
        KoinInitializerKt.doInitKoin(platformModules: []) { _ in }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

final class PortfolioViewModel: ObservableObject {
    @Published var state: PortfolioStreamState = PortfolioStreamStateConnecting.shared
    @Published var isVaultUnlocked: Bool = true

    private let bridge = IosPortfolioBridge()
    private var subscription: CloseableSubscription?

    func startObserving() {
        guard subscription == nil else { return }
        isVaultUnlocked = bridge.isVaultUnlocked()
        subscription = bridge.observePortfolioState { [weak self] newState in
            guard let self = self else { return }
            self.state = newState
            self.isVaultUnlocked = self.bridge.isVaultUnlocked()
        }
    }

    func stopObserving() {
        subscription?.close()
        subscription = nil
    }

    func toggleVault() {
        bridge.toggleVault()
        isVaultUnlocked = bridge.isVaultUnlocked()
    }

    deinit {
        stopObserving()
    }
}

struct ContentView: View {
    @StateObject private var viewModel = PortfolioViewModel()

    var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 20) {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("FinPulse Live Ticker")
                            .font(.title2)
                            .fontWeight(.bold)
                        Text("Phase 2: KMP + SwiftUI on iOS")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                    Button(action: { viewModel.toggleVault() }) {
                        Text(viewModel.isVaultUnlocked ? "Lock Vault" : "Unlock Vault")
                            .fontWeight(.semibold)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(Color.accentColor)
                            .foregroundColor(.white)
                            .cornerRadius(20)
                    }
                }

                if viewModel.state is PortfolioStreamStateConnecting {
                    Spacer()
                    HStack {
                        Spacer()
                        VStack(spacing: 12) {
                            ProgressView()
                            Text("Connecting to WebSocket Ticker Engine...")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                    }
                    Spacer()
                } else if viewModel.state is PortfolioStreamStateLocked {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Hardware Vault Locked")
                            .font(.headline)
                            .foregroundColor(Color(red: 0.55, green: 0.08, blue: 0.08))
                        Text("flatMapLatest cancelled the upstream callbackFlow and awaitClose disconnected the WebSocket.")
                            .font(.subheadline)
                            .foregroundColor(Color(red: 0.55, green: 0.08, blue: 0.08))
                    }
                    .padding(20)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(red: 0.98, green: 0.86, blue: 0.86))
                    .cornerRadius(16)
                    Spacer()
                } else if let active = viewModel.state as? PortfolioStreamStateActive {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Total Portfolio Value")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        Text(String(format: "$%.2f", active.totalPortfolioUsd))
                            .font(.system(size: 32, weight: .heavy))
                    }
                    .padding(20)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.blue.opacity(0.12))
                    .cornerRadius(16)

                    ScrollView {
                        VStack(spacing: 12) {
                            ForEach(active.holdings, id: \.symbolCode) { holding in
                                HoldingRowView(holding: holding)
                            }
                        }
                    }
                }
            }
            .padding(20)
            .navigationBarHidden(true)
        }
        .onAppear { viewModel.startObserving() }
        .onDisappear { viewModel.stopObserving() }
    }
}

struct HoldingRowView: View {
    let holding: AssetHolding

    var body: some View {
        let isPositive = holding.changePercent24h >= 0.0
        let deltaColor = isPositive
            ? Color(red: 0.11, green: 0.53, blue: 0.25)
            : Color(red: 0.77, green: 0.13, blue: 0.12)

        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(holding.symbolCode)
                    .font(.headline)
                    .fontWeight(.bold)
                Text(String(format: "%@ • %.2f units", holding.name, holding.quantityUnits))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 4) {
                Text(String(format: "$%.2f", holding.livePriceUsd))
                    .font(.headline)
                    .fontWeight(.semibold)
                Text(String(format: "%@%.2f%%", isPositive ? "+" : "", holding.changePercent24h))
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(deltaColor)
            }
        }
        .padding(16)
        .background(Color(UIColor.secondarySystemBackground))
        .cornerRadius(14)
    }
}
